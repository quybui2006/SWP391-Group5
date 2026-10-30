package service.shop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import service.shop.VoucherUsageService;
import java.util.List;
import java.util.Map;

@Service
public class ShopOrderService {
    private final JdbcTemplate jdbc;
    private final VoucherUsageService voucherUsage;
    public ShopOrderService(JdbcTemplate jdbc, VoucherUsageService voucherUsage) {
        this.jdbc = jdbc;
        this.voucherUsage = voucherUsage;
    }

    public List<Map<String, Object>> list(Long ownerId) {
        return list(ownerId, null);
    }

    public List<Map<String, Object>> list(Long ownerId, String filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, o.order_code, o.recipient_name, o.total_amount, o.status,
                       o.recipient_phone, o.payment_method, o.payment_status, o.delivery_status,
                       o.cancellation_reason, o.created_at, o.shop_revenue_amount,
                       c.id AS complaint_id, c.complaint_code, c.status AS complaint_status,
                       c.reason_type AS complaint_reason_type, c.sla_due_at AS complaint_sla_due_at,
                       c.requested_refund_amount, c.final_refund_amount
                FROM orders o
                JOIN shops s ON s.id = o.shop_id
                LEFT JOIN (
                    SELECT c1.id, c1.order_id, c1.complaint_code, c1.status, c1.reason_type,
                           c1.sla_due_at, c1.requested_refund_amount, c1.final_refund_amount
                    FROM complaints c1
                    INNER JOIN (
                        SELECT order_id, MAX(id) AS max_id
                        FROM complaints
                        GROUP BY order_id
                    ) c2 ON c1.id = c2.max_id
                ) c ON c.order_id = o.id
                WHERE s.owner_id = ?
                """);
        if ("complaint".equalsIgnoreCase(filter)) {
            sql.append(" AND c.status IS NOT NULL AND c.status <> 'CANCELLED' ");
        }
        sql.append(" ORDER BY o.created_at DESC, o.id DESC ");
        return jdbc.queryForList(sql.toString(), ownerId);
    }

    public Map<String, Object> countComplaints(Long ownerId) {
        return jdbc.queryForMap("""
                SELECT
                    COUNT(c.id) AS total_complaints,
                    COALESCE(SUM(CASE WHEN c.status IN ('PENDING_SHOP', 'PENDING') THEN 1 ELSE 0 END), 0) AS pending_shop_complaints
                FROM complaints c
                JOIN shops s ON s.id = c.shop_id
                WHERE s.owner_id = ? AND c.status <> 'CANCELLED'
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

    public List<Map<String, Object>> exportRows(Long ownerId) {
        return jdbc.queryForList("""
                SELECT o.order_code,o.created_at,o.recipient_name,o.recipient_phone,
                       CONCAT_WS(', ',o.delivery_address,o.delivery_ward_name,o.delivery_province_name) AS delivery_address,
                       GROUP_CONCAT(CONCAT(oi.product_name,' - ',oi.variant_name,' x',oi.quantity) ORDER BY oi.id SEPARATOR '; ') AS order_items,
                       o.subtotal,o.shipping_fee,o.total_amount,o.payment_method,o.status,o.cancellation_reason
                FROM orders o JOIN shops s ON s.id=o.shop_id
                LEFT JOIN order_items oi ON oi.order_id=o.id
                WHERE s.owner_id=?
                GROUP BY o.id
                ORDER BY o.created_at DESC,o.id DESC
                """, ownerId);
    }

    public Map<String, Object> detail(Long ownerId, Long id) {
        var rows = jdbc.queryForList("SELECT o.* FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.id=? AND s.owner_id=?", id, ownerId);
        if (rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return rows.get(0);
    }

    public List<Map<String, Object>> items(Long ownerId, Long id) {
        detail(ownerId, id);
        return jdbc.queryForList("SELECT oi.*,vi.quantity_on_hand,vi.reserved_quantity,vi.available_quantity FROM order_items oi JOIN variant_inventory vi ON vi.id=oi.inventory_id WHERE oi.order_id=? ORDER BY oi.id", id);
    }

    @org.springframework.transaction.annotation.Transactional
    public void confirmCod(Long ownerId, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.* FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.id=? AND s.owner_id=? FOR UPDATE", id, ownerId);
        if (rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        Map<String, Object> order = rows.get(0);
        if (!"PENDING".equals(order.get("status")) || !"COD".equals(order.get("payment_method")) || !"UNPAID".equals(order.get("payment_status")))
            throw new IllegalArgumentException("Chỉ xác nhận được đơn COD đang chờ xử lý.");

        List<Map<String, Object>> items = jdbc.queryForList("SELECT oi.id,oi.inventory_id,oi.quantity,vi.quantity_on_hand,vi.reserved_quantity,vi.available_quantity FROM order_items oi JOIN variant_inventory vi ON vi.id=oi.inventory_id WHERE oi.order_id=? AND oi.allocation_status='UNRESERVED' ORDER BY oi.inventory_id FOR UPDATE", id);
        if (items.isEmpty()) throw new IllegalArgumentException("Đơn không có mặt hàng đang chờ giữ tồn kho.");
        for (Map<String, Object> item : items) {
            int quantity = ((Number) item.get("quantity")).intValue();
            int available = ((Number) item.get("available_quantity")).intValue();
            if (quantity > available) throw new IllegalArgumentException("Tồn kho có thể bán không đủ để xác nhận đơn này. Còn " + available + ", cần " + quantity + ".");
        }
        for (Map<String, Object> item : items) {
            long inventoryId = ((Number) item.get("inventory_id")).longValue();
            long itemId = ((Number) item.get("id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();
            if (jdbc.update("UPDATE variant_inventory SET quantity_on_hand=quantity_on_hand-? WHERE id=? AND quantity_on_hand-reserved_quantity>=?", quantity, inventoryId, quantity) != 1)
                throw new IllegalArgumentException("Không thể ghi nhận số lượng đã bán cho mặt hàng " + itemId + ".");
            jdbc.update("UPDATE order_items SET allocation_status='CONSUMED' WHERE id=? AND allocation_status='UNRESERVED'", itemId);
            int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            jdbc.update("INSERT INTO inventory_transactions(inventory_id,order_item_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,reason,created_by) VALUES(?,?,'SALE',?,0,?,?,?, ?,?)", inventoryId, itemId, -quantity, onHand, reserved, "sale-confirm-" + itemId, "Bán theo đơn " + order.get("order_code"), ownerId);
        }
        if (jdbc.update("UPDATE orders SET status='CONFIRMED' WHERE id=? AND status='PENDING' AND payment_method='COD'", id) != 1)
            throw new IllegalArgumentException("Đơn vừa được cập nhật ở nơi khác. Tải lại trang.");
        jdbc.update("INSERT IGNORE INTO notifications(user_id,event_key,type,title,content,target_type,target_id) SELECT c.user_id,CONCAT('ORDER_CONFIRMED_',o.id),'ORDER','Shop đã xác nhận đơn',CONCAT('Shop đã xác nhận đơn ',o.order_code),'ORDER',o.id FROM orders o JOIN checkouts c ON c.id=o.checkout_id WHERE o.id=? AND c.user_id IS NOT NULL", id);
    }

    /** Shop từ chối đơn COD đang chờ và hoàn lại lượt voucher nếu đơn được hủy thành công. */
    @org.springframework.transaction.annotation.Transactional
    public void rejectCod(Long ownerId, Long id, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000)
            throw new IllegalArgumentException("Nhập lý do từ chối (tối đa 1000 ký tự).");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.* FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.id=? AND s.owner_id=? FOR UPDATE", id, ownerId);
        if (rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        Map<String, Object> order = rows.get(0);
        if (!"PENDING".equals(order.get("status")) || !"COD".equals(order.get("payment_method")) || !"UNPAID".equals(order.get("payment_status")))
            throw new IllegalArgumentException("Chỉ từ chối được đơn COD đang chờ xử lý.");
        String cleanReason = reason.trim();
        if (jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP(3),cancellation_reason=? WHERE id=? AND status='PENDING'", cleanReason, id) != 1)
            throw new IllegalArgumentException("Đơn vừa được cập nhật ở nơi khác. Tải lại trang.");
        voucherUsage.returnDiscountUse(id);
        jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE order_id=? AND allocation_status='UNRESERVED'", id);
        jdbc.update("UPDATE payments SET status='CANCELLED',failure_reason=? WHERE order_id=? AND status='PENDING'", cleanReason, id);
        Long checkoutId = ((Number) order.get("checkout_id")).longValue();
        Integer activeOrders = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE checkout_id=? AND status<>'CANCELLED'", Integer.class, checkoutId);
        jdbc.update("UPDATE checkouts SET status=? WHERE id=? AND status IN ('PENDING','CONFIRMED','PARTIAL')", activeOrders != null && activeOrders == 0 ? "CANCELLED" : "PARTIAL", checkoutId);
        jdbc.update("INSERT IGNORE INTO notifications(user_id,event_key,type,title,content,target_type,target_id) SELECT c.user_id,CONCAT('ORDER_REJECTED_',o.id),'ORDER','Shop từ chối đơn hàng',CONCAT('Đơn ',o.order_code,' bị từ chối. Lý do: ',?),'ORDER',o.id FROM orders o JOIN checkouts c ON c.id=o.checkout_id WHERE o.id=? AND c.user_id IS NOT NULL", cleanReason, id);
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
