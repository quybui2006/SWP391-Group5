package service.admin;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Tính toán và xuất file chi phí trả cho Shop Owner.
 * Fee thu từ shop đã có sẵn trong shop_monthly_fees.
 */
@Service
public class AdminShopPaymentService {

    private final JdbcTemplate jdbc;

    public AdminShopPaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> calculateShopPayouts(String month) {
        // month format: YYYY-MM
        LocalDate monthDate = LocalDate.parse(month + "-01");
        LocalDate nextMonth = monthDate.plusMonths(1);

        return jdbc.queryForList("""
                SELECT s.id AS shop_id, s.name AS shop_name, s.owner_id,
                       u.full_name AS owner_name, u.email AS owner_email,
                       COALESCE(SUM(o.shop_revenue_amount), 0) AS revenue,
                       COALESCE(f.fee_amount, ROUND(COALESCE(SUM(o.shop_revenue_amount), 0) *
                           COALESCE(CAST(pc.config_value AS DECIMAL(19,4)), 0.05), 0)) AS fee_amount,
                       COALESCE(f.fee_amount, ROUND(COALESCE(SUM(o.shop_revenue_amount), 0) *
                           COALESCE(CAST(pc.config_value AS DECIMAL(19,4)), 0.05), 0)) AS payout_amount
                FROM shops s
                JOIN users u ON u.id = s.owner_id
                LEFT JOIN orders o ON o.shop_id = s.id
                    AND o.status = 'COMPLETED' AND o.payment_status = 'PAID'
                    AND o.completed_at >= ? AND o.completed_at < ?
                LEFT JOIN shop_monthly_fees f ON f.shop_id = s.id AND f.revenue_month = ?
                LEFT JOIN platform_config pc ON pc.config_key = 'commission_rate'
                GROUP BY s.id, s.name, s.owner_id, u.full_name, u.email, f.fee_amount, pc.config_value
                ORDER BY payout_amount DESC
                """, monthDate, nextMonth, monthDate);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPayoutSummary(String month) {
        List<Map<String, Object>> rows = calculateShopPayouts(month);
        BigDecimal totalRevenue = rows.stream().map(r -> (BigDecimal) r.get("revenue")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFee = rows.stream().map(r -> (BigDecimal) r.get("fee_amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPayout = rows.stream().map(r -> (BigDecimal) r.get("payout_amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("totalRevenue", totalRevenue, "totalFee", totalFee, "totalPayout", totalPayout, "shopCount", rows.size());
    }

    public String exportPayoutsCsv(String month) {
        List<Map<String, Object>> rows = calculateShopPayouts(month);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal totalPayout = BigDecimal.ZERO;

        // UTF-8 BOM so Excel opens Vietnamese correctly.
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("BÁO CÁO ĐỐI SOÁT PHÍ SÀN SHOP OWNER\r\n");
        sb.append("Kỳ: ").append(month).append("\r\n\r\n");

        sb.append("STT;Tên Shop;Chủ Shop;Email chủ shop;Tiền khách trả (VNĐ);Phí sàn phải trả (VNĐ);Trạng thái\r\n");

        int stt = 1;
        for (Map<String, Object> r : rows) {
            BigDecimal revenue = toDecimal(r.get("revenue"));
            BigDecimal fee = toDecimal(r.get("fee_amount"));
            BigDecimal payout = toDecimal(r.get("payout_amount"));

            totalRevenue = totalRevenue.add(revenue);
            totalFee = totalFee.add(fee);
            totalPayout = totalPayout.add(payout);

            sb.append(stt++).append(";")
              .append('"').append(escapeCsv(r.get("shop_name"))).append('"').append(";")
              .append('"').append(escapeCsv(r.get("owner_name"))).append('"').append(";")
              .append('"').append(escapeCsv(r.get("owner_email"))).append('"').append(";")
              .append(formatMoney(revenue)).append(";")
              .append(formatMoney(fee)).append(";")
              .append("Chờ thanh toán").append("\r\n");
        }

        sb.append("\r\n");
        sb.append("TỔNG KẾT;;;;")
          .append(formatMoney(totalRevenue)).append(";")
          .append(formatMoney(totalFee)).append(";")
          .append(formatMoney(totalPayout)).append("\r\n");
        sb.append("Số shop:").append(rows.size()).append("\r\n");

        return sb.toString();
    }

    public byte[] exportPayoutsXlsx(String month) {
        List<Map<String, Object>> rows = calculateShopPayouts(month);
        LocalDate from = LocalDate.parse(month + "-01");
        LocalDate to = from.plusMonths(1).minusDays(1);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Transaction Report");
            sheet.setDisplayGridlines(false);

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            Font sectionFont = workbook.createFont();
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 12);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);

            CellStyle sectionStyle = workbook.createCellStyle();
            sectionStyle.setFont(sectionFont);
            sectionStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            sectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(sectionStyle);

            CellStyle labelStyle = workbook.createCellStyle();
            labelStyle.setFont(sectionFont);
            setBorder(labelStyle);

            CellStyle valueStyle = workbook.createCellStyle();
            setBorder(valueStyle);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setWrapText(true);
            setBorder(headerStyle);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);
            setBorder(moneyStyle);

            CellStyle totalStyle = workbook.createCellStyle();
            totalStyle.setFont(sectionFont);
            totalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            totalStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(totalStyle);

            CellStyle totalLabelStyle = workbook.createCellStyle();
            totalLabelStyle.setFont(sectionFont);
            totalLabelStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(totalLabelStyle);

            int rowIndex = 0;
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue("Báo cáo");
            row.getCell(0).setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 3, 0, 7));

            rowIndex = 4;
            row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue("Thông tin tài khoản");
            row.getCell(0).setCellStyle(sectionStyle);
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, 7));

            writeLabelValue(sheet, rowIndex++, "Tên đăng nhập (Người bán)", "FreshFruit Admin", labelStyle, valueStyle);
            writeLabelValue(sheet, rowIndex++, "Từ", from.format(dateFormat), labelStyle, valueStyle);
            writeLabelValue(sheet, rowIndex++, "Đến", to.format(dateFormat), labelStyle, valueStyle);

            row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue("** Người bán sẽ chịu trách nhiệm nếu tự ý chỉnh sửa các thông tin trong báo cáo.");
            row.getCell(0).setCellStyle(valueStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 7));

            rowIndex++;
            row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue("Tóm tắt");
            row.getCell(0).setCellStyle(sectionStyle);
            row.createCell(4).setCellValue("VNĐ");
            row.createCell(4).setCellStyle(sectionStyle);
            row.createCell(5).setCellValue("Đơn vị");
            row.createCell(5).setCellStyle(sectionStyle);
            row.createCell(6).setCellValue("Số giao dịch");
            row.createCell(6).setCellStyle(sectionStyle);

            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal totalFee = BigDecimal.ZERO;
            BigDecimal totalPayout = BigDecimal.ZERO;
            for (Map<String, Object> payout : rows) {
                totalRevenue = totalRevenue.add(toDecimal(payout.get("revenue")));
                totalFee = totalFee.add(toDecimal(payout.get("fee_amount")));
                totalPayout = totalPayout.add(toDecimal(payout.get("payout_amount")));
            }

            writeSummaryRow(sheet, rowIndex++, "Tổng tiền khách trả", totalRevenue, rows.size(), labelStyle, moneyStyle, valueStyle);
            writeSummaryRow(sheet, rowIndex++, "Tổng phí sàn Shop Owner phải trả", totalPayout, rows.size(), labelStyle, moneyStyle, valueStyle);

            rowIndex += 2;
            row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue("Chi tiết giao dịch");
            row.getCell(0).setCellStyle(sectionStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 7));

            String[] headers = {"Ngày", "Loại giao dịch", "Chi tiết", "Mã tham chiếu", "Dòng tiền", "Số tiền", "Trạng thái", "Tổng phí sàn lũy kế"};
            row = sheet.createRow(rowIndex++);
            for (int i = 0; i < headers.length; i++) {
                row.createCell(i).setCellValue(headers[i]);
                row.getCell(i).setCellStyle(headerStyle);
            }

            BigDecimal balance = BigDecimal.ZERO;
            for (Map<String, Object> payout : rows) {
                BigDecimal payoutAmount = toDecimal(payout.get("payout_amount"));
                balance = balance.add(payoutAmount);
                row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(to.format(dateFormat));
                row.createCell(1).setCellValue("PHÍ SÀN");
                row.createCell(2).setCellValue("Phí sàn Shop Owner phải trả - " + toStr(payout.get("shop_name")));
                row.createCell(3).setCellValue("SHOP-" + toStr(payout.get("shop_id")));
                row.createCell(4).setCellValue("Tiền phí sàn");
                row.createCell(5).setCellValue(payoutAmount.doubleValue());
                row.createCell(6).setCellValue("Chờ thanh toán");
                row.createCell(7).setCellValue(balance.doubleValue());
                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(i >= 5 ? moneyStyle : valueStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 600, 256 * 256));
            }
            sheet.setColumnWidth(2, 55 * 256);
            sheet.setColumnWidth(7, 24 * 256);
            sheet.createFreezePane(0, rowIndex - rows.size());

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo file Excel chi trả: " + e.getMessage(), e);
        }
    }

    private void writeLabelValue(Sheet sheet, int rowIndex, String label, String value,
                                 CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.getCell(0).setCellStyle(labelStyle);
        row.createCell(1).setCellValue(value);
        row.getCell(1).setCellStyle(valueStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 1, 3));
    }

    private void writeSummaryRow(Sheet sheet, int rowIndex, String label, BigDecimal amount, int count,
                                 CellStyle labelStyle, CellStyle moneyStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.getCell(0).setCellStyle(labelStyle);
        row.createCell(4).setCellValue(amount.doubleValue());
        row.getCell(4).setCellStyle(moneyStyle);
        row.createCell(5).setCellValue("VND");
        row.getCell(5).setCellStyle(valueStyle);
        row.createCell(6).setCellValue(count);
        row.getCell(6).setCellStyle(valueStyle);
    }

    private void setBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String toStr(Object v) {
        return v == null ? "" : v.toString();
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    private String formatMoney(BigDecimal v) {
        return String.format("%,d", v == null ? 0 : v.longValue());
    }

    private String escapeCsv(Object v) {
        return v == null ? "" : v.toString().replace("\"", "\"\"");
    }
}
