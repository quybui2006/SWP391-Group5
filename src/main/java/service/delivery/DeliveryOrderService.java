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
     * Lấy danh sách các đơn hàng phục vụ vận chuyển (Tự động khớp mọi tên cột của bảng orders/checkouts)
     */
    public List<Map<String, Object>> getDeliveryOrders(String statusFilter, String keyword) {
        // Lấy toàn bộ cột hiện có của bảng orders (o.*) và thông tin cửa hàng
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
                SUM(CASE WHEN status IN ('PENDING', 'CONFIRMED', 'PROCESSING', 'READY_TO_SHIP') THEN 1 ELSE 0 END) AS waiting_pickup,
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
            case "SHIPPING" -> {
                jdbc.update("UPDATE orders SET status = 'SHIPPING' WHERE id = ?", orderId);
            }

            case "DELIVERED", "COMPLETED" -> {
                if ("COD".equalsIgnoreCase(paymentMethod)) {
                    jdbc.update("UPDATE orders SET status = ?, payment_status = 'PAID', paid_at = ? WHERE id = ?",
                            newStatus.toUpperCase(), now, orderId);
                    jdbc.update("UPDATE payments SET status = 'SUCCEEDED', paid_at = ? WHERE order_id = ?", now, orderId);
                } else {
                    jdbc.update("UPDATE orders SET status = ? WHERE id = ?", newStatus.toUpperCase(), orderId);
                }

                // Trừ chính thức tồn kho đã giữ chỗ
                List<Map<String, Object>> items = jdbc.queryForList(
                        "SELECT id, inventory_id, quantity FROM order_items WHERE order_id = ? AND allocation_status = 'RESERVED'",
                        orderId);
                for (Map<String, Object> item : items) {
                    Long invId = ((Number) item.get("inventory_id")).longValue();
                    int qty = ((Number) item.get("quantity")).intValue();
                    Long itemId = ((Number) item.get("id")).longValue();
                    jdbc.update("""
                        UPDATE variant_inventory 
                        SET available_quantity = GREATEST(0, available_quantity - ?),
                            reserved_quantity = GREATEST(0, reserved_quantity - ?)
                        WHERE id = ?
                    """, qty, qty, invId);
                    jdbc.update("UPDATE order_items SET allocation_status = 'COMMITTED' WHERE id = ?", itemId);
                }
            }

            case "CANCELLED" -> {
                String reason = (cancelReason != null && !cancelReason.isBlank())
                        ? cancelReason.trim()
                        : "Giao hàng không thành công / Khách từ chối nhận";

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
}