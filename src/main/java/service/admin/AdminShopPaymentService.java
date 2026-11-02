package service.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
                       COALESCE(SUM(o.shop_revenue_amount), 0) AS revenue,
                       COALESCE(f.fee_amount, 0) AS fee_amount,
                       COALESCE(SUM(o.shop_revenue_amount) - COALESCE(f.fee_amount, 0), 0) AS payout_amount
                FROM shops s
                LEFT JOIN orders o ON o.shop_id = s.id
                    AND o.status = 'COMPLETED' AND o.payment_status = 'PAID'
                    AND o.completed_at >= ? AND o.completed_at < ?
                LEFT JOIN shop_monthly_fees f ON f.shop_id = s.id AND f.revenue_month = ?
                GROUP BY s.id, s.name, s.owner_id, f.fee_amount
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
        sb.append("BÁO CÁO CHI TRẢ SHOP OWNER\r\n");
        sb.append("Kỳ: ").append(month).append("\r\n\r\n");

        sb.append("STT;Tên Shop;Mã Shop;Mã Chủ Shop;Doanh thu (VNĐ);Phí sàn (VNĐ);Cần trả (VNĐ)\r\n");

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
              .append(r.get("shop_id")).append(";")
              .append(r.get("owner_id")).append(";")
              .append(formatMoney(revenue)).append(";")
              .append(formatMoney(fee)).append(";")
              .append(formatMoney(payout)).append("\r\n");
        }

        sb.append("\r\n");
        sb.append("TỔNG KẾT;;;;")
          .append(formatMoney(totalRevenue)).append(";")
          .append(formatMoney(totalFee)).append(";")
          .append(formatMoney(totalPayout)).append("\r\n");
        sb.append("Số shop:").append(rows.size()).append("\r\n");

        return sb.toString();
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
