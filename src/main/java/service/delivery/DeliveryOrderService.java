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
            ORDER BY o.id DESC
        """;

        List<Map<String, Object>> rawOrders = jdbc.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rawOrders) {
            String rawStatus = firstNonNullStr(row, "status", "order_status", "delivery_status", "PENDING").toUpperCase();

            // QUAN TRỌNG: Khi khách mới đặt (PENDING - Chờ xác nhận), chưa hiện lên cho Delivery Manager.
            // Chỉ khi Shop Owner đã Confirm (CONFIRMED / PROCESSING / READY_TO_SHIP...) mới hiện lên.
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
     * Lấy danh sách từng sản phẩm trong đơn hàng (Phục vụ bảng Chi tiết đơn hàng như Ảnh 3)
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

            BigDecimal unitPrice = firstNonNullDecimal(row, "unit_price", "price", "sale_price");
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

        String rawStatus = firstNonNullStr(row, "status", "order_status", "delivery_status", "CONFIRMED").toUpperCase();
        // Quy đổi trạng thái chuẩn sang 4 trạng thái hiển thị cho Delivery Manager:
        // PROCESSING (Đang được xử lý), SHIPPING (Đang giao hàng), COMPLETED (Đã nhận hàng), CANCELLED (Đơn hàng đã bị hủy)
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
     * Cập nhật trạng thái giao hàng từ Dropdown của Delivery Manager
     */
    @Transactional
    public void updateDeliveryStatus(Long orderId, String newStatus, String cancelReason) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM orders WHERE id = ?", orderId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId);
        }

        Map<String, Object> order = rows.get(0);
        String currentStatus = firstNonNullStr(order, "status", "order_status", "");
        String paymentMethod = firstNonNullStr(order, "payment_method", "COD");

        if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalStateException("Đơn hàng này đã bị hủy trước đó, không thể cập nhật thêm.");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);

        switch (newStatus.toUpperCase()) {
            case "PROCESSING", "CONFIRMED" -> {
                jdbc.update("UPDATE orders SET status = 'CONFIRMED' WHERE id = ?", orderId);
            }

            case "SHIPPING" -> {
                jdbc.update("UPDATE orders SET status = 'SHIPPING' WHERE id = ?", orderId);
            }

            case "DELIVERED", "COMPLETED" -> {
                if ("COD".equalsIgnoreCase(paymentMethod)) {
                    jdbc.update("UPDATE orders SET status = 'COMPLETED', payment_status = 'PAID', paid_at = ? WHERE id = ?",
                            now, orderId);
                    jdbc.update("UPDATE payments SET status = 'SUCCEEDED', paid_at = ? WHERE order_id = ?", now, orderId);
                } else {
                    jdbc.update("UPDATE orders SET status = 'COMPLETED' WHERE id = ?", orderId);
                }

                // Trừ tồn kho gốc (quantity), KHÔNG đụng vào cột generated available_quantity
                List<Map<String, Object>> items = jdbc.queryForList(
                        "SELECT id, inventory_id, quantity FROM order_items WHERE order_id = ? AND allocation_status = 'RESERVED'",
                        orderId);
                for (Map<String, Object> item : items) {
                    Long invId = ((Number) item.get("inventory_id")).longValue();
                    int qty = ((Number) item.get("quantity")).intValue();
                    Long itemId = ((Number) item.get("id")).longValue();
                    jdbc.update("""
                        UPDATE variant_inventory 
                        SET quantity = GREATEST(0, quantity - ?),
                            reserved_quantity = GREATEST(0, reserved_quantity - ?)
                        WHERE id = ?
                    """, qty, qty, invId);
                    jdbc.update("UPDATE order_items SET allocation_status = 'COMMITTED' WHERE id = ?", itemId);
                }
            }

            case "CANCELLED" -> {
                String reason = (cancelReason != null && !cancelReason.isBlank())
                        ? cancelReason.trim()
                        : "Đơn hàng đã bị hủy khi giao";

                List<Map<String, Object>> items = jdbc.queryForList(
                        "SELECT id, inventory_id, quantity FROM order_items WHERE order_id = ? AND allocation_status = 'RESERVED'",
                        orderId);
                for (Map<String, Object> item : items) {
                    Long invId = ((Number) item.get("inventory_id")).longValue();
                    int qty = ((Number) item.get("quantity")).intValue();
                    Long itemId = ((Number) item.get("id")).longValue();
                    jdbc.update("UPDATE variant_inventory SET reserved_quantity = GREATEST(0, reserved_quantity - ?) WHERE id = ?",
                            qty, invId);
                    jdbc.update("UPDATE order_items SET allocation_status = 'RELEASED' WHERE id = ?", itemId);
                }

                jdbc.update("""
                    UPDATE orders 
                    SET status = 'CANCELLED', cancelled_at = ?, cancellation_reason = ? 
                    WHERE id = ?
                """, now, reason, orderId);
            }

            default -> {
                jdbc.update("UPDATE orders SET status = ? WHERE id = ?", newStatus.toUpperCase(), orderId);
            }
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