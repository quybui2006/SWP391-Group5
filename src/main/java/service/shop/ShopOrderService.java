package service.shop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class ShopOrderService {
    private final JdbcTemplate jdbc;
    public ShopOrderService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> list(Long ownerId) {
        return jdbc.queryForList("""
                SELECT o.id, o.order_code, o.recipient_name, o.total_amount, o.status,
                       o.payment_status, o.delivery_status, o.created_at
                FROM orders o JOIN shops s ON s.id=o.shop_id
                WHERE s.owner_id=? ORDER BY o.created_at DESC, o.id DESC
                """, ownerId);
    }

    public Map<String, Object> summary(Long ownerId) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN o.status='PENDING' THEN 1 ELSE 0 END),0) AS pending,
                COALESCE(SUM(CASE WHEN o.status='COMPLETED' AND o.payment_status='PAID'
                    THEN o.shop_revenue_amount ELSE 0 END),0) AS revenue
                FROM orders o JOIN shops s ON s.id=o.shop_id WHERE s.owner_id=?
                """, ownerId);
    }

    public Map<String, Object> detail(Long ownerId, Long id) {
        var rows = jdbc.queryForList("SELECT o.* FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.id=? AND s.owner_id=?", id, ownerId);
        if (rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return rows.get(0);
    }

    public List<Map<String, Object>> items(Long ownerId, Long id) {
        detail(ownerId, id);
        return jdbc.queryForList("SELECT * FROM order_items WHERE order_id=? ORDER BY id", id);
    }

    @org.springframework.transaction.annotation.Transactional
    public void advance(Long ownerId, Long id) {
        var order = detail(ownerId, id);
        String status = (String) order.get("status");
        String next = switch (status) {
            case "PENDING" -> "CONFIRMED";
            case "CONFIRMED" -> "PREPARING";
            default -> throw new IllegalArgumentException("Chỉ có thể xác nhận hoặc chuẩn bị đơn hàng.");
        };
        if (jdbc.update("UPDATE orders SET status=? WHERE id=? AND status=? AND shop_id IN (SELECT id FROM shops WHERE owner_id=?)", next, id, status, ownerId) != 1)
            throw new IllegalArgumentException("Đơn hàng vừa thay đổi. Vui lòng tải lại trang.");
    }

    public List<Map<String, Object>> frequentCustomers(Long ownerId) {
        return jdbc.queryForList("""
                SELECT o.recipient_name,
                       o.recipient_phone,
                       COUNT(o.id) AS order_count,
                       COALESCE(SUM(o.shop_revenue_amount), 0) AS total_spent,
                       MAX(o.created_at) AS last_order_date,
                       CASE
                           WHEN COUNT(o.id) >= 3 OR SUM(o.shop_revenue_amount) >= 1000000 THEN 'VIP'
                           WHEN COUNT(o.id) >= 2 THEN 'Khách quen'
                           ELSE 'Khách mới'
                       END AS customer_tier
                FROM orders o
                JOIN shops s ON s.id = o.shop_id
                WHERE s.owner_id = ? AND o.status != 'CANCELLED'
                GROUP BY o.recipient_name, o.recipient_phone
                ORDER BY total_spent DESC, order_count DESC
                LIMIT 10
                """, ownerId);
    }

    public List<Map<String, Object>> inventoryStatusList(Long ownerId) {
        return jdbc.queryForList("""
                SELECT pv.id AS variant_id,
                       pv.sku,
                       pv.name AS variant_name,
                       p.name AS product_name,
                       pv.image_url,
                       u.name AS unit_name,
                       vi.quantity_on_hand,
                       vi.reserved_quantity,
                       vi.available_quantity,
                       ROUND(vi.initial_quantity * (vi.low_stock_threshold_pct / 100.0), 0) AS threshold_qty,
                       CASE
                           WHEN vi.quantity_on_hand = 0 THEN 'Hết hàng'
                           WHEN vi.quantity_on_hand <= (vi.initial_quantity * (vi.low_stock_threshold_pct / 100.0)) THEN 'Sắp hết'
                           ELSE 'Đủ hàng'
                       END AS stock_status
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id
                JOIN shops s ON s.id = pv.shop_id
                JOIN units u ON u.id = pv.base_unit_id
                JOIN variant_inventory vi ON vi.variant_id = pv.id
                WHERE s.owner_id = ?
                ORDER BY CASE WHEN vi.quantity_on_hand = 0 THEN 1 ELSE 0 END DESC,
                         CASE WHEN vi.quantity_on_hand <= (vi.initial_quantity * (vi.low_stock_threshold_pct / 100.0)) THEN 1 ELSE 0 END DESC,
                         vi.quantity_on_hand ASC
                LIMIT 10
                """, ownerId);
    }

    public Map<String, Object> revenueAnalytics(Long ownerId) {
        java.time.LocalDateTime sevenDaysAgo = java.time.LocalDate.now().minusDays(6).atStartOfDay();
        List<Map<String, Object>> ordersInWeek = jdbc.queryForList("""
                SELECT o.created_at, o.status, o.shop_revenue_amount
                FROM orders o
                JOIN shops s ON s.id = o.shop_id
                WHERE s.owner_id = ? AND o.created_at >= ?
                ORDER BY o.created_at ASC
                """, ownerId, java.sql.Timestamp.valueOf(sevenDaysAgo));

        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        Map<String, Map<String, Object>> trendMap = new java.util.LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate d = java.time.LocalDate.now().minusDays(i);
            String label = d.format(dtf);
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("day_label", label);
            item.put("revenue", java.math.BigDecimal.ZERO);
            item.put("order_count", 0L);
            trendMap.put(label, item);
        }

        for (Map<String, Object> o : ordersInWeek) {
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
            String label = ldt.toLocalDate().format(dtf);
            if (trendMap.containsKey(label)) {
                Map<String, Object> item = trendMap.get(label);
                item.put("order_count", ((Number) item.get("order_count")).longValue() + 1);
                String status = String.valueOf(o.get("status"));
                if ("COMPLETED".equalsIgnoreCase(status)) {
                    java.math.BigDecimal rev = (java.math.BigDecimal) item.get("revenue");
                    java.math.BigDecimal rowRev = o.get("shop_revenue_amount") != null ? new java.math.BigDecimal(o.get("shop_revenue_amount").toString()) : java.math.BigDecimal.ZERO;
                    item.put("revenue", rev.add(rowRev));
                }
            }
        }
        List<Map<String, Object>> dailyTrend = new java.util.ArrayList<>(trendMap.values());

        Map<String, Object> statusCounts = jdbc.queryForMap("""
                SELECT
                    COALESCE(SUM(CASE WHEN o.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending,
                    COALESCE(SUM(CASE WHEN o.status = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS confirmed,
                    COALESCE(SUM(CASE WHEN o.status = 'PREPARING' THEN 1 ELSE 0 END), 0) AS preparing,
                    COALESCE(SUM(CASE WHEN o.status = 'SHIPPING' THEN 1 ELSE 0 END), 0) AS shipping,
                    COALESCE(SUM(CASE WHEN o.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed,
                    COALESCE(SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled
                FROM orders o
                JOIN shops s ON s.id = o.shop_id
                WHERE s.owner_id = ?
                """, ownerId);

        Map<String, Object> inventoryCounts = jdbc.queryForMap("""
                SELECT
                    COALESCE(SUM(vi.quantity_on_hand), 0) AS total_quantity,
                    COALESCE(SUM(CASE WHEN vi.quantity_on_hand = 0 THEN 1 ELSE 0 END), 0) AS out_of_stock_count,
                    COALESCE(SUM(CASE WHEN vi.quantity_on_hand > 0 AND vi.quantity_on_hand <= (vi.initial_quantity * (vi.low_stock_threshold_pct / 100.0)) THEN 1 ELSE 0 END), 0) AS low_stock_count
                FROM variant_inventory vi
                JOIN product_variants pv ON pv.id = vi.variant_id
                JOIN shops s ON s.id = pv.shop_id
                WHERE s.owner_id = ?
                """, ownerId);

        return Map.of(
                "dailyTrend", dailyTrend,
                "statusCounts", statusCounts,
                "inventoryCounts", inventoryCounts
        );
    }
}
