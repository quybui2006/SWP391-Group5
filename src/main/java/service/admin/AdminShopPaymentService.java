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
        StringBuilder sb = new StringBuilder();
        sb.append("shop_id,shop_name,owner_id,revenue,fee_amount,payout_amount\r\n");
        for (Map<String, Object> r : rows) {
            sb.append(r.get("shop_id")).append(",")
              .append('"').append(escapeCsv(r.get("shop_name"))).append('"').append(",")
              .append(r.get("owner_id")).append(",")
              .append(r.get("revenue")).append(",")
              .append(r.get("fee_amount")).append(",")
              .append(r.get("payout_amount")).append("\r\n");
        }
        return sb.toString();
    }

    private String escapeCsv(Object v) {
        return v == null ? "" : v.toString().replace("\"", "\"\"");
    }
}
