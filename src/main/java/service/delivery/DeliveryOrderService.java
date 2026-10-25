package service.delivery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import service.customer.OrderCheckoutService;

@Service
public class DeliveryOrderService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final OrderCheckoutService checkout;

    public DeliveryOrderService(JdbcTemplate jdbc, OrderCheckoutService checkout) {
        this.jdbc = jdbc;
        this.checkout = checkout;
    }

    /**
     * Lấy danh sách các đơn hàng phục vụ vận chuyển (Tự động khớp mọi tên cột của bảng orders/checkouts)
     */
    public List<Map<String, Object>> getDeliveryOrders(String statusFilter, String keyword) {
        // Lấy toàn bộ cột hiện có của bảng orders (o.*) và thông tin cửa hàng
        String sql = """
            SELECT o.*,
                   s.name AS joined_shop_name
            FROM orders o
            LEFT JOIN shops s ON s.id = o.shop_id
            WHERE o.status IN ('CONFIRMED','SHIPPING','COMPLETED','CANCELLED')
            ORDER BY o.id DESC
        """;

        List<Map<String, Object>> rawOrders = jdbc.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rawOrders) {
            Map<String, Object> ord = new HashMap<>(row);
            Long orderId = ((Number) row.get("id")).longValue();

            // 1. Chuẩn hóa trạng thái đơn hàng
            String status = firstNonNullStr(row, "status", "order_status", "delivery_status", "PENDING");
            if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
                if (!status.equalsIgnoreCase(statusFilter.trim())) {
                    continue;
                }
            }
            ord.put("status", status);

            // 2. Nếu thông tin người nhận/địa chỉ nằm ở bảng checkouts thì lấy bổ sung từ checkouts
            Map<String, Object> checkoutRow = Map.of();
            Object checkoutIdObj = row.get("checkout_id");
            if (checkoutIdObj instanceof Number cid) {
                try {
                    List<Map<String, Object>> cList = jdbc.queryForList("SELECT * FROM checkouts WHERE id = ?", cid.longValue());
                    if (!cList.isEmpty()) checkoutRow = cList.get(0);
                } catch (Exception ignored) {}
            }

            // 3. Chuẩn hóa Tên người nhận, SĐT, Địa chỉ, Tỉnh/Thành
            String recipientName = firstNonNullStr(row, "recipient_name", "receiver_name", "customer_name", "shipping_name",
                    firstNonNullStr(checkoutRow, "recipient_name", "receiver_name", "customer_name", "Khách hàng"));

            String recipientPhone = firstNonNullStr(row, "recipient_phone", "receiver_phone", "customer_phone", "shipping_phone", "phone",
                    firstNonNullStr(checkoutRow, "recipient_phone", "receiver_phone", "phone", ""));

            String addressDetail = firstNonNullStr(row, "address_detail", "shipping_address_detail", "shipping_address", "delivery_address", "address",
                    firstNonNullStr(checkoutRow, "address_detail", "shipping_address_detail", "shipping_address", "address", ""));

            String wardName = firstNonNullStr(row, "ward_name", "shipping_ward_name", "shipping_ward",
                    firstNonNullStr(checkoutRow, "ward_name", "shipping_ward_name", ""));

            String provinceCode = firstNonNullStr(row, "province_code", "shipping_province_code",
                    firstNonNullStr(checkoutRow, "province_code", "shipping_province_code", ""));

            String provinceName = firstNonNullStr(row, "province_name", "shipping_province_name", "");
            if (provinceName.isEmpty() && !provinceCode.isEmpty()) {
                try {
                    List<String> pNames = jdbc.queryForList("SELECT name FROM provinces WHERE code = ?", String.class, provinceCode);
                    if (!pNames.isEmpty()) provinceName = pNames.get(0);
                } catch (Exception ignored) {}
            }

            // 4. Kiểm tra từ khóa tìm kiếm (keyword)
            String orderCode = firstNonNullStr(row, "order_code", "code", "#" + orderId);
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim().toLowerCase();
                boolean matched = orderCode.toLowerCase().contains(kw)
                        || recipientName.toLowerCase().contains(kw)
                        || recipientPhone.toLowerCase().contains(kw);
                if (!matched) continue;
            }

            ord.put("order_code", orderCode);
            ord.put("recipient_name", recipientName);
            ord.put("recipient_phone", recipientPhone);
            ord.put("address_detail", addressDetail);
            ord.put("ward_name", wardName);
            ord.put("province_name", provinceName);
            ord.put("shop_name", firstNonNullStr(row, "joined_shop_name", "shop_name", "FreshFruit"));
            ord.put("customer_note", firstNonNullStr(row, "customer_note", "note",
                    firstNonNullStr(checkoutRow, "customer_note", "note", "")));

            // 5. Chuẩn hóa tiền & thanh toán
            ord.put("payment_method", firstNonNullStr(row, "payment_method", "method",
                    firstNonNullStr(checkoutRow, "payment_method", "COD")));
            ord.put("payment_status", firstNonNullStr(row, "payment_status", "UNPAID"));
            ord.put("total_amount", firstNonNullDecimal(row, "total_amount", "grand_total", "final_amount", "total"));

            // 6. Chuẩn hóa thời gian tạo đơn (chuyển Timestamp sang LocalDateTime cho Thymeleaf #temporals)
            Object createdObj = row.get("created_at");
            if (createdObj instanceof Timestamp ts) {
                ord.put("created_at", ts.toLocalDateTime());
            } else if (!(createdObj instanceof LocalDateTime)) {
                ord.put("created_at", LocalDateTime.now(BUSINESS_ZONE));
            }

            // 7. Lấy danh sách tên món hàng trong đơn
            ord.put("items_summary", loadOrderItemsSummary(orderId));

            result.add(ord);
        }

        return result;
    }

    private String loadOrderItemsSummary(Long orderId) {
        try {
            List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT oi.*, pv.name AS pv_name
                FROM order_items oi
                LEFT JOIN product_variants pv ON pv.id = oi.variant_id
                WHERE oi.order_id = ?
            """, orderId);

            if (items.isEmpty()) return "Chưa có chi tiết sản phẩm";

            List<String> parts = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String name = firstNonNullStr(item, "variant_name", "product_name", "pv_name", "Sản phẩm");
                Object qty = item.get("quantity") != null ? item.get("quantity") : 1;
                parts.add(name + " (x" + qty + ")");
            }
            return String.join(", ", parts);
        } catch (Exception e) {
            return "Đơn hàng #" + orderId;
        }
    }

    private String firstNonNullStr(Map<String, Object> map, String... keysAndDefault) {
        if (map == null || keysAndDefault.length == 0) return "";
        for (int i = 0; i < keysAndDefault.length - 1; i++) {
            Object val = map.get(keysAndDefault[i]);
            if (val != null && !val.toString().isBlank()) {
                return val.toString();
            }
        }
        return keysAndDefault[keysAndDefault.length - 1];
    }

    private BigDecimal firstNonNullDecimal(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val instanceof BigDecimal bd) return bd;
            if (val instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    /**
     * Thống kê nhanh số lượng đơn theo trạng thái
     */
    public Map<String, Object> getOrderCounts() {
        String sql = """
            SELECT 
                COUNT(*) AS total_all,
                SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS waiting_pickup,
                SUM(CASE WHEN status = 'SHIPPING' THEN 1 ELSE 0 END) AS shipping_count,
                SUM(CASE WHEN status IN ('DELIVERED', 'COMPLETED') THEN 1 ELSE 0 END) AS delivered_count,
                SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count
            FROM orders
        """;
        return jdbc.queryForMap(sql);
    }

    /**
     * Cập nhật trạng thái giao hàng của đơn hàng
     */
    @Transactional
    public void updateDeliveryStatus(Long orderId, String newStatus, String cancelReason) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM orders WHERE id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId);
        }

        Map<String, Object> order = rows.get(0);
        String currentStatus = firstNonNullStr(order, "status", "order_status", "");
        String paymentMethod = firstNonNullStr(order, "payment_method", "COD");
        String paymentStatus = firstNonNullStr(order, "payment_status", "UNPAID");
        if ("COMPLETED".equals(currentStatus) || "CANCELLED".equals(currentStatus))
            throw new IllegalStateException("Đơn hàng đã kết thúc, không thể cập nhật thêm.");
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String target = newStatus == null ? "" : newStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if ("SHIPPING".equals(target)) {
            if (!"CONFIRMED".equals(currentStatus)) throw new IllegalStateException("Chỉ nhận đơn Shop Owner đã xác nhận để bắt đầu giao.");
            jdbc.update("UPDATE orders SET status='SHIPPING',delivery_status='IN_TRANSIT',shipped_at=?,delivery_updated_at=? WHERE id=? AND status='CONFIRMED'", now, now, orderId);
            return;
        }
        if ("COMPLETED".equals(target)) {
            if (!"SHIPPING".equals(currentStatus)) throw new IllegalStateException("Chỉ hoàn tất đơn đang được giao.");
            consumeReservedItems(orderId);
            if ("COD".equals(paymentMethod) && "UNPAID".equals(paymentStatus)) {
                jdbc.update("UPDATE payments SET status='SUCCEEDED',paid_at=? WHERE order_id=? AND status='PENDING'", now, orderId);
                jdbc.update("UPDATE orders SET status='COMPLETED',payment_status='PAID',paid_at=?,delivery_status='DELIVERED',delivered_at=?,auto_complete_at=DATE_ADD(?,INTERVAL 3 DAY),completed_at=?,delivery_updated_at=? WHERE id=? AND status='SHIPPING'", now, now, now, now, now, orderId);
            } else {
                jdbc.update("UPDATE orders SET status='COMPLETED',delivery_status='DELIVERED',delivered_at=?,auto_complete_at=DATE_ADD(?,INTERVAL 3 DAY),completed_at=?,delivery_updated_at=? WHERE id=? AND status='SHIPPING'", now, now, now, now, orderId);
            }
            checkout.creditCashbackPoints(orderId);
            return;
        }
        if ("CANCELLED".equals(target)) {
            if ("PAID".equals(paymentStatus)) throw new IllegalStateException("Đơn online đã thanh toán phải được giao; không thể hủy tại luồng giao hàng.");
            String reason = cancelReason == null || cancelReason.isBlank()
                    ? "Giao hàng không thành công / Khách từ chối nhận" : cancelReason.trim();
            if (reason.length() > 1000) throw new IllegalArgumentException("Lý do không được vượt quá 1000 ký tự.");
            releaseReservedItems(orderId);
            jdbc.update("UPDATE orders SET status='CANCELLED',delivery_status='FAILED',delivery_failure_reason=?,delivery_updated_at=?,cancelled_at=?,cancellation_reason=? WHERE id=? AND status IN ('CONFIRMED','SHIPPING')", reason, now, now, reason, orderId);
            jdbc.update("UPDATE payments SET status='CANCELLED',failure_reason=? WHERE order_id=? AND status='PENDING'", reason, orderId);
            return;
        }
        throw new IllegalArgumentException("Trạng thái giao hàng không hợp lệ.");
    }

    private void consumeReservedItems(Long orderId) {
        List<Map<String, Object>> items = jdbc.queryForList("SELECT oi.id,oi.inventory_id,oi.quantity FROM order_items oi WHERE oi.order_id=? AND oi.allocation_status='RESERVED' ORDER BY oi.inventory_id FOR UPDATE", orderId);
        if (items.isEmpty()) {
            Integer soldItems = jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE order_id=? AND allocation_status='CONSUMED'", Integer.class, orderId);
            if (soldItems != null && soldItems > 0) return;
            throw new IllegalStateException("Không tìm thấy tồn kho đã giữ cho đơn hàng.");
        }
        for (Map<String, Object> item : items) {
            long inventoryId = ((Number) item.get("inventory_id")).longValue();
            long itemId = ((Number) item.get("id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();
            if (jdbc.update("UPDATE variant_inventory SET quantity_on_hand=quantity_on_hand-?,reserved_quantity=reserved_quantity-? WHERE id=? AND quantity_on_hand>=? AND reserved_quantity>=?", quantity, quantity, inventoryId, quantity, quantity) != 1)
                throw new IllegalStateException("Tồn kho đã giữ không còn nhất quán cho mặt hàng " + itemId + ".");
            jdbc.update("UPDATE order_items SET allocation_status='CONSUMED' WHERE id=? AND allocation_status='RESERVED'", itemId);
            int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            jdbc.update("INSERT INTO inventory_transactions(inventory_id,order_item_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,created_by) VALUES(?,?,'SALE',?,?,?,?,?,NULL)", inventoryId, itemId, -quantity, -quantity, onHand, reserved, "sale-delivery-" + itemId);
        }
    }

    private void releaseReservedItems(Long orderId) {
        List<Map<String, Object>> items = jdbc.queryForList("SELECT oi.id,oi.inventory_id,oi.quantity,oi.allocation_status FROM order_items oi WHERE oi.order_id=? AND oi.allocation_status IN ('RESERVED','CONSUMED') ORDER BY oi.inventory_id FOR UPDATE", orderId);
        for (Map<String, Object> item : items) {
            long inventoryId = ((Number) item.get("inventory_id")).longValue();
            long itemId = ((Number) item.get("id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();
            boolean alreadySold = "CONSUMED".equals(item.get("allocation_status"));
            if (alreadySold) {
                if (jdbc.update("UPDATE variant_inventory SET quantity_on_hand=quantity_on_hand+? WHERE id=?", quantity, inventoryId) != 1)
                    throw new IllegalStateException("Không thể hoàn kho cho mặt hàng " + itemId + ".");
                jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE id=? AND allocation_status='CONSUMED'", itemId);
                int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
                int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
                jdbc.update("INSERT INTO inventory_transactions(inventory_id,order_item_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,reason,created_by) VALUES(?,?,'ADJUST',?,0,?,?,? ,?,NULL)", inventoryId, itemId, quantity, onHand, reserved, "return-delivery-" + itemId, "Hoàn kho do giao hàng thất bại - đơn " + orderId);
                continue;
            }
            if (jdbc.update("UPDATE variant_inventory SET reserved_quantity=reserved_quantity-? WHERE id=? AND reserved_quantity>=?", quantity, inventoryId, quantity) != 1)
                throw new IllegalStateException("Không thể trả tồn kho đã giữ cho mặt hàng " + itemId + ".");
            jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE id=? AND allocation_status='RESERVED'", itemId);
            int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            jdbc.update("INSERT INTO inventory_transactions(inventory_id,order_item_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,created_by) VALUES(?,?,'RELEASE',0,?,?,?,?,NULL)", inventoryId, itemId, -quantity, onHand, reserved, "release-delivery-" + itemId);
        }
    }
}
