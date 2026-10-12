package service;

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
}
