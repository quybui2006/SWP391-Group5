package service.operations;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class OperationsFinanceService {
    private final JdbcTemplate jdbc;
    private final service.admin.AdminCommissionService commissionService;

    public OperationsFinanceService(JdbcTemplate jdbc, service.admin.AdminCommissionService commissionService) {
        this.jdbc = jdbc;
        this.commissionService = commissionService;
    }

    private void syncRefundLedger() {
        jdbc.update("""
            INSERT IGNORE INTO customer_refunds(order_id,customer_id,refund_type,reason,refund_amount,bank_name,bank_account_no,bank_account_holder)
            SELECT o.id,c.user_id,'CANCEL_ORDER',COALESCE(o.cancellation_reason,'Đơn hàng đã hủy'),o.total_amount,
                   b.bank_name,b.bank_account_no,b.bank_account_holder
            FROM orders o JOIN checkouts c ON c.id=o.checkout_id
            LEFT JOIN customer_bank_accounts b ON b.user_id=c.user_id
            WHERE o.status='CANCELLED' AND o.payment_status='PAID'
        """);
        jdbc.update("""
            INSERT IGNORE INTO customer_refunds(order_id,customer_id,refund_type,reason,refund_amount,bank_name,bank_account_no,bank_account_holder)
            SELECT c.order_id,c.user_id,'RETURN_REFUND',COALESCE(c.resolution_note,c.title,'Khiếu nại/ trả hàng'),
                   COALESCE(NULLIF(c.final_refund_amount,0),c.requested_refund_amount),
                   b.bank_name,b.bank_account_no,b.bank_account_holder
            FROM complaints c JOIN orders o ON o.id=c.order_id
            LEFT JOIN customer_bank_accounts b ON b.user_id=c.user_id
            WHERE c.status='RESOLVED' AND c.resolution_type='RETURN_REFUND'
              AND COALESCE(NULLIF(c.final_refund_amount,0),c.requested_refund_amount)>0
        """);
        jdbc.update("""
            UPDATE customer_refunds r JOIN customer_bank_accounts b ON b.user_id=r.customer_id
            SET r.bank_name=b.bank_name,r.bank_account_no=b.bank_account_no,r.bank_account_holder=b.bank_account_holder
            WHERE r.status='PENDING' AND (r.bank_account_no IS NULL OR r.bank_account_no='')
        """);
    }

    @Transactional
    public Long recordExport(String type, String fileName, LocalDate from, LocalDate to, Long userId, byte[] content) {
        jdbc.update("INSERT INTO finance_export_history(export_type,file_name,filter_from,filter_to,exported_by,file_content) VALUES(?,?,?,?,?,?)",
                type, fileName, from == null ? null : Date.valueOf(from), to == null ? null : Date.valueOf(to), userId, content);
        return jdbc.queryForObject("SELECT MAX(id) FROM finance_export_history", Long.class);
    }

    public BigDecimal currentCommissionRate() { return commissionService.getCurrentCommissionRate(); }

    public List<Map<String,Object>> exportHistory(String type) {
        return jdbc.queryForList("""
            SELECT id,export_type,file_name,filter_from,filter_to,exported_at
            FROM finance_export_history WHERE export_type=? ORDER BY exported_at DESC,id DESC LIMIT 100
        """, type);
    }

    public Map<String,Object> exportFile(Long id) {
        return jdbc.queryForMap("SELECT file_name,file_content FROM finance_export_history WHERE id=?", id);
    }

    public long eligibleRefundCount(LocalDate day) {
        syncRefundLedger();
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM customer_refunds WHERE DATE(created_at)=? AND status='PENDING' AND COALESCE(bank_name,'')<>'' AND COALESCE(bank_account_no,'')<>'' AND COALESCE(bank_account_holder,'')<>''", Long.class, Date.valueOf(day));
        return count == null ? 0L : count;
    }

    public long eligiblePayoutCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM shop_payouts WHERE status='PENDING' AND COALESCE(bank_name,'')<>'' AND COALESCE(bank_account_no,'')<>'' AND COALESCE(bank_account_holder,'')<>''", Long.class);
        return count == null ? 0L : count;
    }

    public List<Map<String,Object>> refunds(LocalDate day, String status) {
        syncRefundLedger();
        String state = "PENDING".equalsIgnoreCase(status) || "EXPORTED".equalsIgnoreCase(status) ? status.toUpperCase() : "ALL";
        return jdbc.queryForList("""
            SELECT r.*, o.order_code, u.full_name AS customer_name,
                   CASE WHEN COALESCE(r.bank_name,'')<>'' AND COALESCE(r.bank_account_no,'')<>'' AND COALESCE(r.bank_account_holder,'')<>'' THEN 'READY' ELSE 'MISSING_BANK' END AS bank_readiness
            FROM customer_refunds r JOIN orders o ON o.id=r.order_id JOIN users u ON u.id=r.customer_id
            WHERE DATE(r.created_at)=? AND (?='ALL' OR r.status=?) ORDER BY r.created_at, r.id
        """, Date.valueOf(day), state, state);
    }

    public List<Map<String,Object>> payouts() {
        jdbc.update("""
            UPDATE shop_payouts p JOIN shops s ON s.id=p.shop_id
            SET p.bank_name=s.bank_name,p.bank_account_no=s.bank_account_number,p.bank_account_holder=s.bank_account_name
            WHERE p.status='PENDING' AND (p.bank_account_no IS NULL OR p.bank_account_no='')
        """);
        return jdbc.queryForList("""
            SELECT p.*, s.name AS shop_name,
                   CASE WHEN COALESCE(p.bank_name,'')<>'' AND COALESCE(p.bank_account_no,'')<>'' AND COALESCE(p.bank_account_holder,'')<>'' THEN 'READY' ELSE 'MISSING_BANK' END AS bank_readiness
            FROM shop_payouts p JOIN shops s ON s.id=p.shop_id
            ORDER BY p.created_at DESC,p.id DESC LIMIT 500
        """);
    }

    @Transactional
    public int createPayouts(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("Khoảng ngày thanh toán không hợp lệ.");
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT o.shop_id, COUNT(*) AS total_orders,
                   COALESCE(SUM(o.shop_revenue_amount),0) AS gross_amount,
                   s.bank_name,s.bank_account_number,s.bank_account_name
            FROM orders o JOIN shops s ON s.id=o.shop_id
            WHERE o.status='COMPLETED' AND o.payment_status='PAID' AND o.is_settled=0
              AND DATE(o.completed_at) BETWEEN ? AND ?
            GROUP BY o.shop_id,s.bank_name,s.bank_account_number,s.bank_account_name
            ORDER BY o.shop_id FOR UPDATE
        """, Date.valueOf(from), Date.valueOf(to));
        int created = 0;
        for (Map<String,Object> row : rows) {
            long shopId = ((Number)row.get("shop_id")).longValue();
            BigDecimal gross = (BigDecimal) row.get("gross_amount");
            BigDecimal feeRate = commissionService.getCurrentCommissionRate();
            BigDecimal fee = gross.multiply(feeRate).setScale(0, java.math.RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(fee).max(BigDecimal.ZERO);
            int inserted = jdbc.update("""
                INSERT IGNORE INTO shop_payouts(shop_id,period_from,period_to,total_orders,gross_amount,platform_fee,platform_fee_rate,net_payout,bank_name,bank_account_no,bank_account_holder)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """, shopId, Date.valueOf(from), Date.valueOf(to), ((Number)row.get("total_orders")).intValue(), gross, fee, feeRate, net,
                    row.get("bank_name"), row.get("bank_account_number"), row.get("bank_account_name"));
            if (inserted == 1) {
                jdbc.update("UPDATE orders SET is_settled=1 WHERE shop_id=? AND status='COMPLETED' AND payment_status='PAID' AND is_settled=0 AND DATE(completed_at) BETWEEN ? AND ?",
                        shopId, Date.valueOf(from), Date.valueOf(to));
                created++;
            }
        }
        return created;
    }

    @Transactional
    public byte[] exportRefunds(LocalDate day, Long userId) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT r.*,o.order_code,u.full_name AS customer_name FROM customer_refunds r
            JOIN orders o ON o.id=r.order_id JOIN users u ON u.id=r.customer_id
            WHERE DATE(r.created_at)=? AND r.status='PENDING'
              AND COALESCE(r.bank_name,'')<>'' AND COALESCE(r.bank_account_no,'')<>'' AND COALESCE(r.bank_account_holder,'')<>''
            ORDER BY r.created_at,r.id FOR UPDATE
        """, Date.valueOf(day));
        if (rows.isEmpty()) throw new IllegalArgumentException("Không có khoản hoàn tiền đủ thông tin ngân hàng để xuất file.");
        byte[] file = refundWorkbook(day, rows);
        String fileName = "customer-refunds-" + day + ".xlsx";
        jdbc.update("INSERT INTO finance_export_history(export_type,file_name,filter_from,filter_to,exported_by,file_content) VALUES('CUSTOMER_REFUNDS',?,?,?,?,?)",
                fileName, Date.valueOf(day), Date.valueOf(day), userId, file);
        jdbc.update("UPDATE customer_refunds SET status='EXPORTED',exported_at=CURRENT_TIMESTAMP(3) WHERE id IN (" + String.join(",", java.util.Collections.nCopies(rows.size(), "?")) + ")",
                rows.stream().map(r -> ((Number) r.get("id")).longValue()).toArray());
        return file;
    }

    @Transactional
    public byte[] exportPayouts(Long userId) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT p.*,s.name AS shop_name FROM shop_payouts p JOIN shops s ON s.id=p.shop_id
            WHERE p.status='PENDING'
              AND COALESCE(p.bank_name,'')<>'' AND COALESCE(p.bank_account_no,'')<>'' AND COALESCE(p.bank_account_holder,'')<>''
            ORDER BY p.id FOR UPDATE
        """);
        if (rows.isEmpty()) throw new IllegalArgumentException("Không có payout đủ thông tin ngân hàng để xuất file.");
        byte[] file = payoutWorkbook(rows);
        LocalDate from = rows.stream().map(r -> ((Date) r.get("period_from")).toLocalDate()).min(LocalDate::compareTo).orElse(null);
        LocalDate to = rows.stream().map(r -> ((Date) r.get("period_to")).toLocalDate()).max(LocalDate::compareTo).orElse(null);
        String fileName = "shop-payouts-" + LocalDate.now() + ".xlsx";
        jdbc.update("INSERT INTO finance_export_history(export_type,file_name,filter_from,filter_to,exported_by,file_content) VALUES('SHOP_PAYOUTS',?,?,?,?,?)",
                fileName, from == null ? null : Date.valueOf(from), to == null ? null : Date.valueOf(to), userId, file);
        if (!rows.isEmpty()) {
            List<Long> ids = rows.stream().map(r -> ((Number)r.get("id")).longValue()).toList();
            String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            jdbc.update("UPDATE shop_payouts SET status='EXPORTED',exported_at=CURRENT_TIMESTAMP(3) WHERE id IN ("+marks+")", ids.toArray());
        }
        return file;
    }

    private byte[] refundWorkbook(LocalDate day, List<Map<String,Object>> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s=wb.createSheet("Hoan tien khach hang");
            Styles st=new Styles(wb); widths(s,new int[]{18,22,24,20,42,20,20,26,34,18});
            title(s,st,"BÁO CÁO QUYẾT TOÁN HOÀN TIỀN KHÁCH HÀNG CUỐI NGÀY (DAILY CUSTOMER REFUND SHEET)",10);
            text(s,1,0,"Trích xuất giao dịch hoàn tiền theo ngày: " + day,st.subtitle);
            section(s,3,"1. THÔNG TIN CHI TIẾT PHIÊN QUYẾT TOÁN NGÀY (DAILY BATCH INFO)",10,st.blue);
            info(s,4,"Ngày báo cáo",day.toString(),"Mã lô giải ngân","CUS-REF-"+day.toString().replace("-",""),st);
            info(s,5,"Thời gian","00:00:00 - 23:59:59","Đối tượng","Khách hàng (Customer)",st);
            section(s,7,"2. TÓM TẮT YÊU CẦU HOÀN TIỀN KHÁCH HÀNG TRONG NGÀY (DAILY REFUND SUMMARY)",10,st.blue);
            header(s,8,list("Phân loại yêu cầu hoàn tiền","Tổng tiền cần hoàn","Đơn vị","Số giao dịch","Ghi chú nghiệp vụ"),st.dark);
            BigDecimal total=BigDecimal.ZERO;
            int row=9;
            for(String type:List.of("CANCEL_ORDER","RETURN_REFUND")) {
                BigDecimal sum=rows.stream().filter(r->type.equals(r.get("refund_type"))).map(r->(BigDecimal)r.get("refund_amount")).reduce(BigDecimal.ZERO,BigDecimal::add);
                long n=rows.stream().filter(r->type.equals(r.get("refund_type"))).count(); total=total.add(sum);
                values(s,row++,list(type.equals("CANCEL_ORDER")?"Hoàn tiền do khách hủy đơn":"Hoàn tiền do trả hàng / khiếu nại",sum,"VND",n,"Chờ ngân hàng giải ngân"),st.normal);
            }
            values(s,row,list("TỔNG TIỀN HOÀN",total,"VND",rows.size(),"Danh sách giao dịch chi tiết bên dưới"),st.total);
            section(s,11,"3. CHI TIẾT GIAO DỊCH YÊU CẦU HOÀN TIỀN CHO KHÁCH HÀNG (CUSTOMER REFUND LIST)",10,st.blue);
            header(s,12,list("Mã hoàn tiền","Mã đơn hàng","Khách hàng","Loại giao dịch","Lý do chi tiết","Số tiền hoàn (VND)","Ngân hàng","Số tài khoản","Chủ tài khoản","Trạng thái"),st.dark);
            int i=13; for(Map<String,Object> r:rows) values(s,i++,list("CRF-"+r.get("id"),r.get("order_code"),r.get("customer_name"),r.get("refund_type"),r.get("reason"),r.get("refund_amount"),r.get("bank_name"),r.get("bank_account_no"),r.get("bank_account_holder"),"Chờ NH giải ngân"),i%2==0?st.alt:st.normal);
            finish(wb); return bytes(wb);
        } catch(Exception ex){throw new IllegalStateException("Không thể tạo Excel hoàn tiền.",ex);}
    }

    private byte[] payoutWorkbook(List<Map<String,Object>> rows) {
        try(XSSFWorkbook wb=new XSSFWorkbook()) {
            Sheet s=wb.createSheet("Quyet toan Shop"); Sheet bank=wb.createSheet("Format Upload Ngan Hang");
            widths(s,new int[]{22,15,30,18,18,18,18,18,18,18,24,22,26});
            title(s, new Styles(wb),"BÁO CÁO QUYẾT TOÁN DOANH THU & THANH TOÁN ĐỊNH KỲ CHO SHOP (SHOP SETTLEMENT & PAYOUT)",13);
            Styles st=new Styles(wb);
            section(s,3,"1. THÔNG TIN CHI TIẾT KỲ QUYẾT TOÁN (SETTLEMENT CYCLE METADATA)",13,st.green);
            info(s,4,"Kỳ thanh toán","Theo khoảng ngày đã chọn","Chu kỳ","Định kỳ theo yêu cầu kế toán",st);
            section(s,6,"2. TÓM TẮT DÒNG TIỀN QUYẾT TOÁN TOÀN SÀN (PLATFORM SETTLEMENT SUMMARY)",13,st.green);
            BigDecimal gross=sum(rows,"gross_amount"), fee=sum(rows,"platform_fee"), net=sum(rows,"net_payout");
            header(s,7,list("Chỉ tiêu tài chính","Tổng số tiền","Đơn vị","Số giao dịch / Shop","Công thức & Quy tắc"),st.dark);
            values(s,8,list("Tổng doanh thu gộp Shop",gross,"VND",rows.size(),"Tổng doanh thu các đơn COMPLETED, PAID"),st.normal);
            values(s,9,list("Tổng phí nền tảng",fee,"VND",rows.size(),"Tỷ lệ được lấy từ cấu hình Admin tại thời điểm tạo payout"),st.normal);
            values(s,10,list("TỔNG TIỀN NGÂN HÀNG GIẢI NGÂN CHO SHOP",net,"VND",rows.size(),"Doanh thu gộp - phí nền tảng"),st.total);
            section(s,12,"3. BẢNG KÊ CHI TIẾT QUYẾT TOÁN & LỆNH CHI ĐỊNH KỲ TỪNG SHOP (SHOP PAYOUT LEDGER)",13,st.green);
            header(s,13,list("Mã lệnh chi","Mã Shop","Tên Shop","Số đơn thành công","Doanh thu gộp","Tỷ lệ phí Admin","Phí nền tảng","Thực nhận (Net Payout)","Ngân hàng nhận","Số tài khoản Shop","Chủ tài khoản","Trạng thái"),st.dark);
            int rnum=14; for(Map<String,Object> r:rows) values(s,rnum++,list("PAY-"+r.get("id"),r.get("shop_id"),r.get("shop_name"),r.get("total_orders"),r.get("gross_amount"),((BigDecimal)r.get("platform_fee_rate")).multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString()+"%",r.get("platform_fee"),r.get("net_payout"),r.get("bank_name"),r.get("bank_account_no"),r.get("bank_account_holder"),"Chờ ngân hàng"),rnum%2==0?st.alt:st.normal);
            widths(bank,new int[]{10,24,36,22,24,24,34});
            title(bank,st,"THANH TOÁN CHO SHOP GỬI NGÂN HÀNG",7);
            header(bank,3,list("STT","Mã lệnh chi (Batch Ref)","Mã & Tên Shop","Số tiền giải ngân (Net Payout)","Ngân hàng nhận","Số tài khoản nhận","Tên chủ tài khoản","Nội dung chuyển khoản"),st.dark);
            int br=4; for(Map<String,Object> r:rows) values(bank,br++,list(br-4,"PAY-"+r.get("id"),r.get("shop_id")+" - "+r.get("shop_name"),r.get("net_payout"),r.get("bank_name"),r.get("bank_account_no"),r.get("bank_account_holder"),"QUYET TOAN SHOP PAY-"+r.get("id")),br%2==0?st.alt:st.normal);
            finish(wb); return bytes(wb);
        } catch(Exception ex){throw new IllegalStateException("Không thể tạo Excel thanh toán Shop.",ex);}
    }

    private List<Object> list(Object... values){ return java.util.Arrays.asList(values); }
    private BigDecimal sum(List<Map<String,Object>> rows,String key){return rows.stream().map(r->(BigDecimal)r.get(key)).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private void title(Sheet s,Styles st,String text,int columns){Row r=s.createRow(0);r.setHeightInPoints(28);Cell c=r.createCell(0);c.setCellValue(text);c.setCellStyle(st.title);s.addMergedRegion(new CellRangeAddress(0,0,0,columns-1));}
    private void section(Sheet s,int row,String text,int cols,CellStyle style){Row r=s.createRow(row);Cell c=r.createCell(0);c.setCellValue(text);c.setCellStyle(style);s.addMergedRegion(new CellRangeAddress(row,row,0,cols-1));}
    private void header(Sheet s,int row,List<?> vals,CellStyle style){values(s,row,vals,style);}
    private void text(Sheet s,int row,int col,String value,CellStyle style){Cell c=s.createRow(row).createCell(col);c.setCellValue(value);c.setCellStyle(style);}
    private void info(Sheet s,int row,String a,Object b,String c,Object d,Styles st){Row r=s.createRow(row);cell(r,0,a,st.label);cell(r,1,b,st.normal);cell(r,4,c,st.label);cell(r,5,d,st.normal);}
    private void values(Sheet s,int row,List<?> vals,CellStyle style){Row r=s.createRow(row);for(int i=0;i<vals.size();i++)cell(r,i,vals.get(i),style);}
    private void cell(Row r,int col,Object value,CellStyle style){Cell c=r.createCell(col);if(value instanceof Number n)c.setCellValue(n.doubleValue());else c.setCellValue(value==null?"":String.valueOf(value));c.setCellStyle(style);}
    private void widths(Sheet s,int[] widths){for(int i=0;i<widths.length;i++)s.setColumnWidth(i,widths[i]*256);s.createFreezePane(0,1);}
    private void finish(XSSFWorkbook wb) throws IOException {for(Sheet s:wb) {s.setDisplayGridlines(false);s.setAutoFilter(new CellRangeAddress(Math.max(0,s.getPhysicalNumberOfRows()-Math.max(1,s.getLastRowNum()-12)),s.getLastRowNum(),0,Math.max(0,s.getRow(Math.max(0,s.getLastRowNum()-1)).getLastCellNum()-1)));} }
    private byte[] bytes(XSSFWorkbook wb)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();wb.write(out);return out.toByteArray();}

    private static class Styles {
        final CellStyle title,subtitle,blue,green,dark,normal,label,total,alt;
        Styles(Workbook w){
            title=style(w,"FFFFFF",IndexedColors.WHITE.getIndex(),true,16);
            Font tf=w.createFont();tf.setBold(true);tf.setFontHeightInPoints((short)15);tf.setColor(IndexedColors.DARK_BLUE.getIndex());title.setFont(tf);title.setAlignment(HorizontalAlignment.LEFT);
            subtitle=style(w,"FFFFFF",IndexedColors.GREY_50_PERCENT.getIndex(),false,10);
            blue=fill(w,"1F3C88",true,IndexedColors.WHITE.getIndex()); green=fill(w,"00634F",true,IndexedColors.WHITE.getIndex()); dark=fill(w,"26364A",true,IndexedColors.WHITE.getIndex());
            normal=style(w,"FFFFFF",IndexedColors.DARK_BLUE.getIndex(),false,10);label=fill(w,"EAF0F7",true,IndexedColors.DARK_BLUE.getIndex());total=fill(w,"D1FAE5",true,IndexedColors.DARK_BLUE.getIndex());alt=fill(w,"EFFBF4",false,IndexedColors.DARK_BLUE.getIndex());
        }
        private CellStyle style(Workbook w,String bg,short fg,boolean bold,int size){CellStyle s=w.createCellStyle();Font f=w.createFont();f.setBold(bold);f.setFontHeightInPoints((short)size);f.setColor(fg);s.setFont(f);s.setVerticalAlignment(VerticalAlignment.CENTER);s.setWrapText(true);border(s);return s;}
        private CellStyle fill(Workbook w,String color,boolean bold,short fg){CellStyle s=style(w,color,fg,bold,10);byte[] rgb=java.util.HexFormat.of().parseHex(color);((org.apache.poi.xssf.usermodel.XSSFCellStyle)s).setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(rgb,null));s.setFillPattern(FillPatternType.SOLID_FOREGROUND);if(bold)s.setAlignment(HorizontalAlignment.CENTER);return s;}
        private void border(CellStyle s){s.setBorderBottom(BorderStyle.THIN);s.setBorderTop(BorderStyle.THIN);s.setBorderLeft(BorderStyle.THIN);s.setBorderRight(BorderStyle.THIN);s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());}
    }
}
