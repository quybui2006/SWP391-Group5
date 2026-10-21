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
import java.util.Locale;
import java.util.Map;

@Service
public class DeliveryOrderService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;

    public DeliveryOrderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lấy danh sách các đơn hàng mà Shop Owner ĐÃ XÁC NHẬN (không lấy đơn PENDING chưa duyệt)
     */
    public List<Map<String, Object>> getDeliveryOrders() {
        String sql = """
            SELECT o.*,
                   s.name AS joined_shop_name
            FROM orders o
            LEFT JOIN shops s ON s.id = o.shop_id
            WHERE o.status IN ('CONFIRMED', 'PROCESSING', 'READY_TO_SHIP', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED')
            ORDER BY o.id DESC
        """;

        List<Map<String, Object>> rawOrders = jdbc.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rawOrders) {
            String rawStatus = firstNonNullStr(row, "status", "order_status", "delivery_status", "PENDING").toUpperCase(Locale.ROOT);

            // Khi khách mới đặt (PENDING - Chờ xác nhận), chưa hiện lên cho Delivery Manager.
            if ("PENDING".equals(rawStatus) || "UNCONFIRMED".equals(rawStatus) || "DRAFT".equals(rawStatus)) {
                continue;
            }

            Map<String, Object> ord = enrichOrderRow(row);
            result.add(ord);
        }

        return result;
    }

    /**
     * Lấy thông tin 1 đơn hàng cho trang Chi tiết
     */
    public Map<String, Object> getOrderDetail(Long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT o.*, s.name AS joined_shop_name
            FROM orders o
            LEFT JOIN shops s ON s.id = o.shop_id
            WHERE o.id = ?
        """, orderId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId);
        }
        return enrichOrderRow(rows.get(0));
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
     * Lấy danh sách từng sản phẩm trong đơn hàng (Phục vụ bảng Chi tiết đơn hàng)
     */
    public List<Map<String, Object>> getOrderItems(Long orderId) {
        List<Map<String, Object>> rawItems = jdbc.queryForList("""
            SELECT oi.*,
                   pv.name AS pv_name,
                   pv.image_url AS pv_image_url,
                   p.name AS p_name
            FROM order_items oi
            LEFT JOIN product_variants pv ON pv.id = oi.variant_id
            LEFT JOIN products p ON p.id = pv.product_id
            WHERE oi.order_id = ?
            ORDER BY oi.id ASC
        """, orderId);

        List<Map<String, Object>> items = new ArrayList<>();
        int stt = 1;
        for (Map<String, Object> row : rawItems) {
            Map<String, Object> item = new HashMap<>(row);
            item.put("stt", stt++);

            String productName = firstNonNullStr(row, "product_name", "p_name", "variant_name", "pv_name", "Sản phẩm");
            String variantName = firstNonNullStr(row, "variant_name", "pv_name", "");
            if (!variantName.isEmpty() && !productName.equalsIgnoreCase(variantName)) {
                productName = productName + " - " + variantName;
            }
            item.put("display_name", productName);

            String imageUrl = firstNonNullStr(row, "image_url", "pv_image_url", "");
            item.put("display_image", imageUrl);

            int qty = row.get("quantity") instanceof Number n ? n.intValue() : 1;
            item.put("quantity", qty);

            BigDecimal unitPrice = firstNonNullDecimal(row, "sale_unit_price", "unit_price", "price", "sale_price");
            BigDecimal lineTotal = firstNonNullDecimal(row, "line_total", "subtotal", "total_price");
            if (lineTotal.compareTo(BigDecimal.ZERO) == 0 && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            }

            item.put("unit_price", unitPrice);
            item.put("line_total", lineTotal);
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> enrichOrderRow(Map<String, Object> row) {
        Map<String, Object> ord = new HashMap<>(row);
        Long orderId = ((Number) row.get("id")).longValue();

        String rawStatus = firstNonNullStr(row, "status", "order_status", "delivery_status", "CONFIRMED").toUpperCase(Locale.ROOT);
        String uiStatus = switch (rawStatus) {
            case "CONFIRMED", "PROCESSING", "READY_TO_SHIP", "PACKING" -> "PROCESSING";
            case "SHIPPING", "DELIVERING", "IN_TRANSIT" -> "SHIPPING";
            case "COMPLETED", "DELIVERED", "SUCCESS" -> "COMPLETED";
            case "CANCELLED", "CANCELED", "FAILED", "RETURNED" -> "CANCELLED";
            default -> "PROCESSING";
        };
        ord.put("raw_status", rawStatus);
        ord.put("ui_status", uiStatus);

        Map<String, Object> checkoutRow = Map.of();
        Object checkoutIdObj = row.get("checkout_id");
        if (checkoutIdObj instanceof Number cid) {
            try {
                List<Map<String, Object>> cList = jdbc.queryForList("SELECT * FROM checkouts WHERE id = ?", cid.longValue());
                if (!cList.isEmpty()) checkoutRow = cList.get(0);
            } catch (Exception ignored) {}
        }

        String recipientName = firstNonNullStr(row, "recipient_name", "receiver_name", "customer_name", "shipping_name",
                firstNonNullStr(checkoutRow, "recipient_name", "receiver_name", "customer_name", "Khách hàng"));

        String recipientPhone = firstNonNullStr(row, "recipient_phone", "receiver_phone", "customer_phone", "shipping_phone", "phone",
                firstNonNullStr(checkoutRow, "recipient_phone", "receiver_phone", "phone", ""));

        String addressDetail = firstNonNullStr(row, "delivery_address", "address_detail", "shipping_address_detail", "shipping_address", "address",
                firstNonNullStr(checkoutRow, "address_detail", "shipping_address_detail", "shipping_address", "address", ""));

        String wardName = firstNonNullStr(row, "delivery_ward_name", "ward_name", "shipping_ward_name", "shipping_ward",
                firstNonNullStr(checkoutRow, "ward_name", "shipping_ward_name", ""));

        String provinceCode = firstNonNullStr(row, "delivery_province_code", "province_code", "shipping_province_code",
                firstNonNullStr(checkoutRow, "province_code", "shipping_province_code", ""));

        String provinceName = firstNonNullStr(row, "delivery_province_name", "province_name", "shipping_province_name", "");
        if (provinceName.isEmpty() && !provinceCode.isEmpty()) {
            try {
                List<String> pNames = jdbc.queryForList("SELECT name FROM provinces WHERE code = ?", String.class, provinceCode);
                if (!pNames.isEmpty()) provinceName = pNames.get(0);
            } catch (Exception ignored) {}
        }

        List<String> addrParts = new ArrayList<>();
        if (!addressDetail.isBlank()) addrParts.add(addressDetail);
        if (!wardName.isBlank()) addrParts.add(wardName);
        if (!provinceName.isBlank()) addrParts.add(provinceName);
        String fullAddress = addrParts.isEmpty() ? "Chưa cập nhật" : String.join(", ", addrParts);

        ord.put("order_code", firstNonNullStr(row, "order_code", "code", String.valueOf(orderId)));
        ord.put("recipient_name", recipientName);
        ord.put("recipient_phone", recipientPhone);
        ord.put("full_address", fullAddress);
        ord.put("shop_name", firstNonNullStr(row, "joined_shop_name", "shop_name", "FreshFruit"));
        ord.put("payment_method", firstNonNullStr(row, "payment_method", "method",
                firstNonNullStr(checkoutRow, "payment_method", "COD")));
        ord.put("payment_status", firstNonNullStr(row, "payment_status", "UNPAID"));
        ord.put("total_amount", firstNonNullDecimal(row, "total_amount", "grand_total", "final_amount", "total"));

        Object createdObj = row.get("created_at");
        if (createdObj instanceof Timestamp ts) {
            ord.put("created_at", ts.toLocalDateTime());
        } else if (!(createdObj instanceof LocalDateTime)) {
            ord.put("created_at", LocalDateTime.now(BUSINESS_ZONE));
        }

        return ord;
    }

    /**
     * Cập nhật trạng thái giao hàng của Delivery Manager
     */
    @Transactional
    public void updateDeliveryStatus(Long orderId, String newStatus, String cancelReason) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM orders WHERE id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId);
        }

        Map<String, Object> order = rows.get(0);
        String currentStatus = firstNonNullStr(order, "status", "order_status", "").toUpperCase(Locale.ROOT);
        String paymentMethod = firstNonNullStr(order, "payment_method", "COD").toUpperCase(Locale.ROOT);
        String paymentStatus = firstNonNullStr(order, "payment_status", "UNPAID").toUpperCase(Locale.ROOT);

        if ("COMPLETED".equals(currentStatus) || "DELIVERED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
            throw new IllegalStateException("Đơn hàng đã kết thúc, không thể cập nhật thêm.");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String target = newStatus == null ? "" : newStatus.trim().toUpperCase(Locale.ROOT);

        if ("SHIPPING".equals(target)) {
            if (!("CONFIRMED".equals(currentStatus) || "PROCESSING".equals(currentStatus) || "READY_TO_SHIP".equals(currentStatus))) {
                throw new IllegalStateException("Chỉ nhận đơn Shop Owner đã xác nhận để bắt đầu giao.");
            }
            jdbc.update("""
                UPDATE orders 
                SET status = 'SHIPPING', 
                    delivery_status = 'IN_TRANSIT', 
                    shipped_at = ?, 
                    delivery_updated_at = ? 
                WHERE id = ?
            """, now, now, orderId);
            return;
        }

        if ("COMPLETED".equals(target) || "DELIVERED".equals(target)) {
            if (!"SHIPPING".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ hoàn tất đơn đang được giao.");
            }
            consumeReservedItems(orderId);
            if ("COD".equals(paymentMethod) && "UNPAID".equals(paymentStatus)) {
                jdbc.update("UPDATE payments SET status = 'SUCCEEDED', paid_at = ? WHERE order_id = ? AND status = 'PENDING'", now, orderId);
                jdbc.update("""
                    UPDATE orders 
                    SET status = 'COMPLETED', 
                        payment_status = 'PAID', 
                        paid_at = ?, 
                        delivery_status = 'DELIVERED', 
                        delivered_at = ?, 
                        auto_complete_at = DATE_ADD(?, INTERVAL 3 DAY), 
                        completed_at = ?, 
                        delivery_updated_at = ? 
                    WHERE id = ?
                """, now, now, now, now, now, orderId);
            } else {
                jdbc.update("""
                    UPDATE orders 
                    SET status = 'COMPLETED', 
                        delivery_status = 'DELIVERED', 
                        delivered_at = ?, 
                        auto_complete_at = DATE_ADD(?, INTERVAL 3 DAY), 
                        completed_at = ?, 
                        delivery_updated_at = ? 
                    WHERE id = ?
                """, now, now, now, now, orderId);
            }
            return;
        }

        if ("CANCELLED".equals(target)) {
            if ("PAID".equals(paymentStatus)) {
                throw new IllegalStateException("Đơn online đã thanh toán phải được giao; không thể hủy tại luồng giao hàng.");
            }
            String reason = (cancelReason == null || cancelReason.isBlank())
                    ? "Giao hàng không thành công / Khách từ chối nhận"
                    : cancelReason.trim();
            if (reason.length() > 1000) {
                throw new IllegalArgumentException("Lý do không được vượt quá 1000 ký tự.");
            }
            releaseReservedItems(orderId);
            jdbc.update("""
                UPDATE orders 
                SET status = 'CANCELLED', 
                    delivery_status = 'FAILED', 
                    delivery_failure_reason = ?, 
                    delivery_updated_at = ?, 
                    cancelled_at = ?, 
                    cancellation_reason = ? 
                WHERE id = ?
            """, reason, now, now, reason, orderId);
            jdbc.update("UPDATE payments SET status = 'CANCELLED', failure_reason = ? WHERE order_id = ? AND status = 'PENDING'", reason, orderId);
            return;
        }

        throw new IllegalArgumentException("Trạng thái giao hàng không hợp lệ.");
    }

    private void consumeReservedItems(Long orderId) {
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT oi.id, oi.inventory_id, oi.quantity FROM order_items oi WHERE oi.order_id = ? AND oi.allocation_status = 'RESERVED' ORDER BY oi.inventory_id FOR UPDATE",
                orderId);
        for (Map<String, Object> item : items) {
            long inventoryId = ((Number) item.get("inventory_id")).longValue();
            long itemId = ((Number) item.get("id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();

            if (jdbc.update("UPDATE variant_inventory SET quantity_on_hand = quantity_on_hand - ?, reserved_quantity = reserved_quantity - ? WHERE id = ? AND quantity_on_hand >= ? AND reserved_quantity >= ?",
                    quantity, quantity, inventoryId, quantity, quantity) != 1) {
                throw new IllegalStateException("Tồn kho đã giữ không còn nhất quán cho mặt hàng " + itemId + ".");
            }
            jdbc.update("UPDATE order_items SET allocation_status = 'CONSUMED' WHERE id = ? AND allocation_status = 'RESERVED'", itemId);

            int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id = ?", Integer.class, inventoryId);
            int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id = ?", Integer.class, inventoryId);
            jdbc.update("INSERT INTO inventory_transactions(inventory_id, order_item_id, type, on_hand_delta, reserved_delta, on_hand_after, reserved_after, event_key, created_by) VALUES(?, ?, 'SALE', ?, ?, ?, ?, ?, NULL)",
                    inventoryId, itemId, -quantity, -quantity, onHand, reserved, "sale-delivery-" + itemId);
        }
    }

    private void releaseReservedItems(Long orderId) {
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT oi.id, oi.inventory_id, oi.quantity FROM order_items oi WHERE oi.order_id = ? AND oi.allocation_status = 'RESERVED' ORDER BY oi.inventory_id FOR UPDATE",
                orderId);
        for (Map<String, Object> item : items) {
            long inventoryId = ((Number) item.get("inventory_id")).longValue();
            long itemId = ((Number) item.get("id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();

            if (jdbc.update("UPDATE variant_inventory SET reserved_quantity = reserved_quantity - ? WHERE id = ? AND reserved_quantity >= ?",
                    quantity, inventoryId, quantity) != 1) {
                throw new IllegalStateException("Không thể trả tồn kho đã giữ cho mặt hàng " + itemId + ".");
            }
            jdbc.update("UPDATE order_items SET allocation_status = 'RELEASED' WHERE id = ? AND allocation_status = 'RESERVED'", itemId);

            int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id = ?", Integer.class, inventoryId);
            int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id = ?", Integer.class, inventoryId);
            jdbc.update("INSERT INTO inventory_transactions(inventory_id, order_item_id, type, on_hand_delta, reserved_delta, on_hand_after, reserved_after, event_key, created_by) VALUES(?, ?, 'RELEASE', 0, ?, ?, ?, ?, NULL)",
                    inventoryId, itemId, -quantity, onHand, reserved, "release-delivery-" + itemId);
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
}