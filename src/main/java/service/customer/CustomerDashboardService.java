package service.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CustomerDashboardService {

    private final JdbcTemplate jdbc;

    public CustomerDashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerDashboard(Long userId) {
        Map<String, Object> data = new HashMap<>();

        // 1. User & Loyalty info
        Map<String, Object> user = jdbc.queryForMap(
                "SELECT u.id, u.full_name, u.email, u.phone, u.points_balance, u.total_qualifying_spending, " +
                "u.membership_tier_id, m.code AS tier_code, m.name AS tier_name, m.rank_order " +
                "FROM users u " +
                "LEFT JOIN membership_tiers m ON m.id = u.membership_tier_id " +
                "WHERE u.id = ?", userId);
        data.put("user", user);

        // Next Tier calculation
        Integer currentRank = user.get("rank_order") != null ? ((Number) user.get("rank_order")).intValue() : 1;
        BigDecimal currentSpend = user.get("total_qualifying_spending") != null
                ? new BigDecimal(user.get("total_qualifying_spending").toString()) : BigDecimal.ZERO;

        List<Map<String, Object>> nextTierList = jdbc.queryForList(
                "SELECT * FROM membership_tiers WHERE rank_order > ? ORDER BY rank_order ASC LIMIT 1", currentRank);
        if (!nextTierList.isEmpty()) {
            Map<String, Object> nextTier = nextTierList.get(0);
            BigDecimal minSpend = nextTier.get("min_spending") != null
                    ? new BigDecimal(nextTier.get("min_spending").toString()) : BigDecimal.ZERO;
            BigDecimal needed = minSpend.subtract(currentSpend);
            data.put("hasNextTier", true);
            data.put("nextTierName", nextTier.get("name"));
            data.put("nextTierMinSpend", minSpend);
            data.put("spendingNeeded", needed.max(BigDecimal.ZERO));
            double progress = minSpend.compareTo(BigDecimal.ZERO) > 0
                    ? Math.min(100.0, currentSpend.doubleValue() * 100.0 / minSpend.doubleValue()) : 100.0;
            data.put("tierProgress", Math.round(progress));
        } else {
            data.put("hasNextTier", false);
            data.put("tierProgress", 100);
        }

        // 2. Spending Stats
        BigDecimal totalSpent = jdbc.queryForObject(
                "SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o JOIN checkouts c ON c.id=o.checkout_id WHERE c.user_id=? AND o.status='COMPLETED'",
                BigDecimal.class, userId);
        data.put("totalSpent", totalSpent != null ? totalSpent : BigDecimal.ZERO);

        Long totalOrders = jdbc.queryForObject(
                "SELECT COUNT(o.id) FROM orders o JOIN checkouts c ON c.id=o.checkout_id WHERE c.user_id=?",
                Long.class, userId);
        Long completedOrders = jdbc.queryForObject(
                "SELECT COUNT(o.id) FROM orders o JOIN checkouts c ON c.id=o.checkout_id WHERE c.user_id=? AND o.status='COMPLETED'",
                Long.class, userId);
        Long cancelledOrders = jdbc.queryForObject(
                "SELECT COUNT(o.id) FROM orders o JOIN checkouts c ON c.id=o.checkout_id WHERE c.user_id=? AND o.status='CANCELLED'",
                Long.class, userId);

        data.put("totalOrders", totalOrders != null ? totalOrders : 0L);
        data.put("completedOrders", completedOrders != null ? completedOrders : 0L);
        data.put("cancelledOrders", cancelledOrders != null ? cancelledOrders : 0L);

        // Monthly spending breakdown (6 months)
        java.time.LocalDateTime sixMonthsAgo = java.time.LocalDate.now().minusMonths(5).withDayOfMonth(1).atStartOfDay();
        List<Map<String, Object>> userOrders = jdbc.queryForList("""
            SELECT o.created_at, o.total_amount
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            WHERE c.user_id = ? AND o.status = 'COMPLETED' AND o.created_at >= ?
            ORDER BY o.created_at ASC
        """, userId, java.sql.Timestamp.valueOf(sixMonthsAgo));

        java.time.format.DateTimeFormatter monthDtf = java.time.format.DateTimeFormatter.ofPattern("MM/yyyy");
        Map<String, Map<String, Object>> monthTrendMap = new java.util.LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            java.time.LocalDate d = java.time.LocalDate.now().minusMonths(i);
            String label = d.format(monthDtf);
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("month_label", label);
            item.put("total_amount", BigDecimal.ZERO);
            item.put("order_count", 0L);
            monthTrendMap.put(label, item);
        }

        for (Map<String, Object> o : userOrders) {
            Object catObj = o.get("created_at");
            if (catObj == null) continue;
            java.time.LocalDateTime ldt;
            if (catObj instanceof java.sql.Timestamp ts) {
                ldt = ts.toLocalDateTime();
            } else if (catObj instanceof java.time.LocalDateTime dt) {
                ldt = dt;
            } else {
                continue;
            }
            String label = ldt.format(monthDtf);
            if (monthTrendMap.containsKey(label)) {
                Map<String, Object> item = monthTrendMap.get(label);
                item.put("order_count", ((Number) item.get("order_count")).longValue() + 1);
                BigDecimal amt = (BigDecimal) item.get("total_amount");
                BigDecimal rowAmt = o.get("total_amount") != null ? new BigDecimal(o.get("total_amount").toString()) : BigDecimal.ZERO;
                item.put("total_amount", amt.add(rowAmt));
            }
        }
        List<Map<String, Object>> monthlySpending = new java.util.ArrayList<>(monthTrendMap.values());
        data.put("monthlySpending", monthlySpending);

        // 3. Shopping Behavior Insights
        // Top Categories
        List<Map<String, Object>> topCategories = jdbc.queryForList("""
            SELECT cat.name AS category_name,
                   COUNT(oi.id) AS purchase_count,
                   COALESCE(SUM(oi.line_total), 0) AS total_amount
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN checkouts c ON c.id = o.checkout_id
            JOIN product_variants pv ON pv.id = oi.variant_id
            JOIN product_categories pc ON pc.product_id = pv.product_id
            JOIN categories cat ON cat.id = pc.category_id
            WHERE c.user_id = ?
            GROUP BY cat.id, cat.name
            ORDER BY purchase_count DESC, total_amount DESC
            LIMIT 5
        """, userId);
        data.put("topCategories", topCategories);

        // Top favorite variants
        List<Map<String, Object>> topVariants = jdbc.queryForList("""
            SELECT oi.product_name, oi.variant_name, oi.image_url,
                   COUNT(oi.id) AS buy_count,
                   COALESCE(SUM(oi.quantity), 0) AS total_quantity,
                   COALESCE(SUM(oi.line_total), 0) AS total_spent
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN checkouts c ON c.id = o.checkout_id
            WHERE c.user_id = ?
            GROUP BY oi.variant_id, oi.product_name, oi.variant_name, oi.image_url
            ORDER BY buy_count DESC, total_spent DESC
            LIMIT 5
        """, userId);
        data.put("topVariants", topVariants);

        // Preferred Payment Method
        List<Map<String, Object>> paymentMethods = jdbc.queryForList("""
            SELECT o.payment_method, COUNT(o.id) AS use_count
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            WHERE c.user_id = ?
            GROUP BY o.payment_method
            ORDER BY use_count DESC
            LIMIT 1
        """, userId);
        data.put("preferredPayment", !paymentMethods.isEmpty() ? paymentMethods.get(0).get("payment_method") : "COD");

        // Favorite Shops
        List<Map<String, Object>> favoriteShops = jdbc.queryForList("""
            SELECT s.id AS shop_id, s.name AS shop_name,
                   COUNT(o.id) AS order_count,
                   COALESCE(SUM(o.total_amount), 0) AS total_spent
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            JOIN shops s ON s.id = o.shop_id
            WHERE c.user_id = ?
            GROUP BY s.id, s.name
            ORDER BY order_count DESC, total_spent DESC
            LIMIT 3
        """, userId);
        data.put("favoriteShops", favoriteShops);

        // Recent completed orders for "Buy Again"
        List<Map<String, Object>> recentOrders = jdbc.queryForList("""
            SELECT o.id, o.order_code, o.total_amount, o.status, o.created_at, s.name AS shop_name,
                   (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) AS item_count
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            JOIN shops s ON s.id = o.shop_id
            WHERE c.user_id = ?
            ORDER BY o.created_at DESC
            LIMIT 5
        """, userId);
        data.put("recentOrders", recentOrders);

        return data;
    }
}
