package service.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Tổng quan thống kê của sàn FreshFruit cho Admin.
 * Dùng dữ liệu thật từ orders và shop_monthly_fees.
 */
@Service
public class AdminDashboardService {

    private final JdbcTemplate jdbc;

    public AdminDashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> getOverviewStats() {
        Long totalOrders = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        Long completedOrders = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE status='COMPLETED'", Long.class);
        Long pendingOrders = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE status='PENDING'", Long.class);
        Long cancelledOrders = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE status='CANCELLED'", Long.class);

        BigDecimal gmv = jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_amount),0) FROM orders WHERE status='COMPLETED'",
                BigDecimal.class);

        Long totalShops = jdbc.queryForObject("SELECT COUNT(*) FROM shops", Long.class);
        Long activeShops = jdbc.queryForObject("SELECT COUNT(*) FROM shops WHERE operating_status='OPEN'", Long.class);
        Long totalUsers = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        Long customers = jdbc.queryForObject("""
                SELECT COUNT(*) FROM users u JOIN user_roles ur ON ur.user_id=u.id
                JOIN roles r ON r.id=ur.role_id WHERE r.code='CUSTOMER'
                """, Long.class);

        BigDecimal totalFees = jdbc.queryForObject("SELECT COALESCE(SUM(fee_amount),0) FROM shop_monthly_fees", BigDecimal.class);
        BigDecimal paidFees = jdbc.queryForObject("SELECT COALESCE(SUM(fee_amount),0) FROM shop_monthly_fees WHERE status='PAID'", BigDecimal.class);
        BigDecimal unpaidFees = jdbc.queryForObject("SELECT COALESCE(SUM(fee_amount),0) FROM shop_monthly_fees WHERE status='UNPAID'", BigDecimal.class);

        return Map.ofEntries(
                Map.entry("totalOrders", totalOrders),
                Map.entry("completedOrders", completedOrders),
                Map.entry("pendingOrders", pendingOrders),
                Map.entry("cancelledOrders", cancelledOrders),
                Map.entry("gmv", gmv),
                Map.entry("totalShops", totalShops),
                Map.entry("activeShops", activeShops),
                Map.entry("totalUsers", totalUsers),
                Map.entry("customers", customers),
                Map.entry("totalFees", totalFees),
                Map.entry("paidFees", paidFees),
                Map.entry("unpaidFees", unpaidFees)
        );
    }

    public Map<String, Object> getRecentOrdersSummary() {
        return jdbc.queryForMap("""
                SELECT COALESCE(COUNT(*),0) AS total_7d,
                       COALESCE(COUNT(CASE WHEN status='COMPLETED' THEN 1 END),0) AS completed_7d,
                       COALESCE(COUNT(CASE WHEN status='PENDING' THEN 1 END),0) AS pending_7d
                FROM orders WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                """);
    }
}
