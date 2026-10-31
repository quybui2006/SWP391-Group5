package service.delivery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import service.customer.OrderCheckoutService;
import service.vnpay.VNPayService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DeliveryOrderService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("20000");

    private final JdbcTemplate jdbc;
    private final OrderCheckoutService checkout;
    private final VNPayService vnPayService;

    public DeliveryOrderService(JdbcTemplate jdbc, OrderCheckoutService checkout, VNPayService vnPayService) {
        this.jdbc = jdbc;
        this.checkout = checkout;
        this.vnPayService = vnPayService;
    }

    /**
     * Chỉ lấy các đơn hàng mà Shop Owner ĐÃ XÁC NHẬN hoặc ĐÃ BÀN GIAO (không lấy đơn PENDING chưa duyệt)
     */
    public List<Map<String, Object>> getDeliveryOrders() {
        String sql = """
            SELECT o.*,
                   s.name AS joined_shop_name
            FROM orders o
            LEFT JOIN shops s ON s.id = o.shop_id
            WHERE o.status IN ('CONFIRMED', 'PROCESSING', 'READY_TO_SHIP', 'SHIPPING', 'DELIVERED', 'COMPLETED')
               OR (o.status = 'CANCELLED' AND o.delivery_status IN ('IN_TRANSIT', 'FAILED'))
            ORDER BY o.id DESC
        """;

        var rawOrders = jdbc.queryForList(sql);
        var result = new ArrayList<Map<String, Object>>();

        for (var row : rawOrders) {
            String rawStatus = firstNonNullStr(row, "status", "order_status", "PENDING").toUpperCase(Locale.ROOT);
            if ("PENDING".equals(rawStatus) || "UNCONFIRMED".equals(rawStatus) || "DRAFT".equals(rawStatus)) {
                continue;
            }
            result.add(enrichOrderRow(row));
        }

        return result;
    }

    public Map<String, Object> getOrderDetail(Long orderId) {
        var rows = jdbc.queryForList("""
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
                SUM(CASE WHEN status IN ('CONFIRMED', 'PROCESSING', 'READY_TO_SHIP') THEN 1 ELSE 0 END) AS waiting_pickup,
                SUM(CASE WHEN status = 'SHIPPING' THEN 1 ELSE 0 END) AS shipping_count,
                SUM(CASE WHEN status IN ('DELIVERED', 'COMPLETED') THEN 1 ELSE 0 END) AS delivered_count,
                SUM(CASE WHEN status = 'CANCELLED' AND delivery_status IN ('IN_TRANSIT', 'FAILED') THEN 1 ELSE 0 END) AS cancelled_count
            FROM orders
            WHERE status IN ('CONFIRMED', 'PROCESSING', 'READY_TO_SHIP', 'SHIPPING', 'DELIVERED', 'COMPLETED')
               OR (status = 'CANCELLED' AND delivery_status IN ('IN_TRANSIT', 'FAILED'))
        """;
        return jdbc.queryForMap(sql);
    }

    public List<Map<String, Object>> getOrderItems(Long orderId) {
        var rawItems = jdbc.queryForList("""
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

        var items = new ArrayList<Map<String, Object>>();
        int stt = 1;
        for (var row : rawItems) {
            var item = new HashMap<String, Object>(row);
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
        var ord = new HashMap<String, Object>(row);
        Long orderId = ((Number) row.get("id")).longValue();

        String rawStatus = firstNonNullStr(row, "status", "order_status", "CONFIRMED").toUpperCase(Locale.ROOT);
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
                var cList = jdbc.queryForList("SELECT * FROM checkouts WHERE id = ?", cid.longValue());
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
                var pNames = jdbc.queryForList("SELECT name FROM provinces WHERE code = ?", String.class, provinceCode);
                if (!pNames.isEmpty()) provinceName = pNames.get(0);
            } catch (Exception ignored) {}
        }

        var addrParts = new ArrayList<String>();
        if (!addressDetail.isBlank()) addrParts.add(addressDetail);
        if (!wardName.isBlank()) addrParts.add(wardName);
        if (!provinceName.isBlank()) addrParts.add(provinceName);
        String fullAddress = addrParts.isEmpty() ? "Chưa cập nhật" : String.join(", ", addrParts);

        String paymentMethod = firstNonNullStr(row, "payment_method", "method",
                firstNonNullStr(checkoutRow, "payment_method", "COD")).toUpperCase(Locale.ROOT);

        BigDecimal subtotal = firstNonNullDecimal(row, "subtotal", "subtotal_amount");
        BigDecimal shippingFee = firstNonNullDecimal(row, "shipping_fee");
        if (shippingFee.compareTo(BigDecimal.ZERO) <= 0) {
            shippingFee = DEFAULT_SHIPPING_FEE;
        }
        BigDecimal fullOrderTotal = firstNonNullDecimal(row, "total_amount", "grand_total", "final_amount", "total");

        boolean isVnpay = "VNPAY".equalsIgnoreCase(paymentMethod);
        BigDecimal displayTotal = isVnpay ? BigDecimal.ZERO : fullOrderTotal;

        String podImageUrl = firstNonNullStr(row, "pod_image_url", "pod_url", "");
        String deliveryNote = firstNonNullStr(row, "delivery_note", "shipping_note", "");

        ord.put("order_code", firstNonNullStr(row, "order_code", "code", String.valueOf(orderId)));
        ord.put("tracking_code", firstNonNullStr(row, "delivery_tracking_code", "tracking_code", "DL-" + orderId));
        ord.put("recipient_name", recipientName);
        ord.put("recipient_phone", recipientPhone);
        ord.put("full_address", fullAddress);
        ord.put("shop_name", firstNonNullStr(row, "joined_shop_name", "shop_name", "FreshFruit"));
        ord.put("payment_method", paymentMethod);
        ord.put("is_vnpay", isVnpay);
        ord.put("payment_status", firstNonNullStr(row, "payment_status", "UNPAID"));
        ord.put("subtotal", subtotal);
        ord.put("shipping_fee", shippingFee);
        ord.put("full_order_total", fullOrderTotal);
        ord.put("total_amount", displayTotal);
        ord.put("pod_image_url", podImageUrl);
        ord.put("delivery_note", deliveryNote);

        Object createdObj = row.get("created_at");
        if (createdObj instanceof Timestamp ts) {
            ord.put("created_at", ts.toLocalDateTime());
        } else if (!(createdObj instanceof LocalDateTime)) {
            ord.put("created_at", LocalDateTime.now(BUSINESS_ZONE));
        }

        return ord;
    }

    /**
     * Hàm 3 tham số giữ nguyên để tương thích với code hiện tại
     */
    @Transactional
    public void updateDeliveryStatus(Long orderId, String newStatus, String cancelReason) {
        updateDeliveryStatus(orderId, newStatus, cancelReason, null, null);
    }

    /**
     * Cập nhật trạng thái giao hàng: Bàn giao -> Lấy hàng -> Giao thành công kèm POD -> Hoàn tất / Hủy
     */
    @Transactional
    public void updateDeliveryStatus(Long orderId, String newStatus, String cancelReason, String podImageUrl, String deliveryNote) {
        var rows = jdbc.queryForList("SELECT * FROM orders WHERE id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId);
        }

        var order = rows.get(0);
        String currentStatus = firstNonNullStr(order, "status", "order_status", "").toUpperCase(Locale.ROOT);
        String paymentMethod = firstNonNullStr(order, "payment_method", "COD").toUpperCase(Locale.ROOT);
        String paymentStatus = firstNonNullStr(order, "payment_status", "UNPAID").toUpperCase(Locale.ROOT);

        if ("COMPLETED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
            throw new IllegalStateException("Đơn hàng đã kết thúc, không thể cập nhật thêm.");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String target = newStatus == null ? "" : newStatus.trim().toUpperCase(Locale.ROOT);

        // 1. SHOP BÀN GIAO CHO VẬN CHUYỂN (READY_TO_SHIP)
        if ("READY_TO_SHIP".equals(target)) {
            if (!"CONFIRMED".equals(currentStatus) && !"PROCESSING".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ bàn giao đơn hàng đã được Shop xác nhận.");
            }
            String trackingCode = "DL-" + now.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + orderId;
            try {
                jdbc.update("""
                    UPDATE orders 
                    SET status = 'READY_TO_SHIP', 
                        delivery_status = 'WAITING_PICKUP', 
                        delivery_tracking_code = COALESCE(delivery_tracking_code, ?),
                        delivery_updated_at = ? 
                    WHERE id = ?
                """, trackingCode, now, orderId);
            } catch (Exception e) {
                jdbc.update("UPDATE orders SET status = 'READY_TO_SHIP', delivery_status = 'WAITING_PICKUP', delivery_updated_at = ? WHERE id = ?", now, orderId);
            }
            return;
        }

        // 2. SHIPPER NHẬN HÀNG & BẮT ĐẦU GIAO (SHIPPING)
        if ("SHIPPING".equals(target)) {
            if (!"CONFIRMED".equals(currentStatus) && !"PROCESSING".equals(currentStatus) && !"READY_TO_SHIP".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ nhận đơn Shop Owner đã xác nhận để bắt đầu giao.");
            }
            jdbc.update("""
                UPDATE orders 
                SET status = 'SHIPPING',
                    delivery_status = 'IN_TRANSIT',
                    shipped_at = ?,
                    delivery_updated_at = ? 
                WHERE id = ? AND status IN ('CONFIRMED', 'PROCESSING', 'READY_TO_SHIP')
            """, now, now, orderId);
            return;
        }

        // 3. ĐÃ GIAO ĐẾN NƠI + LƯU MINH CHỨNG POD (DELIVERED)
        if ("DELIVERED".equals(target)) {
            if (!"SHIPPING".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ xác nhận đã giao đối với đơn đang vận chuyển.");
            }
            if ("COD".equals(paymentMethod) && "UNPAID".equals(paymentStatus)) {
                jdbc.update("UPDATE payments SET status='SUCCEEDED',paid_at=? WHERE order_id=? AND status='PENDING'", now, orderId);
                jdbc.update("UPDATE orders SET status='DELIVERED',payment_status='PAID',paid_at=?,delivery_status='DELIVERED',delivered_at=?,auto_complete_at=DATE_ADD(?,INTERVAL 3 DAY),delivery_updated_at=? WHERE id=? AND status='SHIPPING'", now, now, now, now, orderId);
            } else {
                jdbc.update("UPDATE orders SET status='DELIVERED',delivery_status='DELIVERED',delivered_at=?,auto_complete_at=DATE_ADD(?,INTERVAL 3 DAY),delivery_updated_at=? WHERE id=? AND status='SHIPPING'", now, now, now, orderId);
            }
            savePodEvidence(orderId, podImageUrl, deliveryNote);
            return;
        }

        // 4. HOÀN TẤT ĐƠN HÀNG (COMPLETED)
        if ("COMPLETED".equals(target)) {
            if (!"SHIPPING".equals(currentStatus) && !"DELIVERED".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ hoàn tất đơn đang được giao.");
            }
            if ("COD".equals(paymentMethod) && "UNPAID".equals(paymentStatus)) {
                jdbc.update("UPDATE payments SET status='SUCCEEDED',paid_at=? WHERE order_id=? AND status='PENDING'", now, orderId);
                jdbc.update("UPDATE orders SET status='COMPLETED',payment_status='PAID',paid_at=?,delivery_status='DELIVERED',delivered_at=COALESCE(delivered_at,?),auto_complete_at=COALESCE(auto_complete_at,DATE_ADD(?,INTERVAL 3 DAY)),completed_at=?,delivery_updated_at=? WHERE id=? AND status IN ('SHIPPING','DELIVERED')", now, now, now, now, now, orderId);
            } else {
                jdbc.update("UPDATE orders SET status='COMPLETED',delivery_status='DELIVERED',delivered_at=COALESCE(delivered_at,?),auto_complete_at=COALESCE(auto_complete_at,DATE_ADD(?,INTERVAL 3 DAY)),completed_at=?,delivery_updated_at=? WHERE id=? AND status IN ('SHIPPING','DELIVERED')", now, now, now, now, orderId);
            }
            savePodEvidence(orderId, podImageUrl, deliveryNote);
            checkout.creditCashbackPoints(orderId);
            return;
        }

        // 5. GIAO HÀNG THẤT BẠI / HỦY ĐƠN (KHÔNG DÙNG REFUND & INVENTORY)
        if ("CANCELLED".equals(target)) {
            String reason = (cancelReason == null || cancelReason.isBlank())
                    ? "Giao hàng không thành công / Khách từ chối nhận"
                    : cancelReason.trim();
            if (reason.length() > 1000) {
                throw new IllegalArgumentException("Lý do không được vượt quá 1000 ký tự.");
            }

            jdbc.update("""
                UPDATE orders 
                SET status = 'CANCELLED',
                    delivery_status = 'FAILED',
                    delivery_failure_reason = ?,
                    delivery_updated_at = ?,
                    cancelled_at = ?,
                    cancellation_reason = ? 
                WHERE id = ? AND status IN ('CONFIRMED', 'SHIPPING', 'PROCESSING', 'READY_TO_SHIP')
            """, reason, now, now, reason, orderId);

            jdbc.update("UPDATE payments SET status='CANCELLED',failure_reason=? WHERE order_id=? AND status='PENDING'", reason, orderId);
            return;
        }

        throw new IllegalArgumentException("Trạng thái giao hàng không hợp lệ.");
    }

    private void savePodEvidence(Long orderId, String podImageUrl, String deliveryNote) {
        if ((podImageUrl == null || podImageUrl.isBlank()) && (deliveryNote == null || deliveryNote.isBlank())) {
            return;
        }
        try {
            jdbc.update("""
                UPDATE orders 
                SET pod_image_url = COALESCE(?, pod_image_url),
                    delivery_note = COALESCE(?, delivery_note)
                WHERE id = ?
            """, podImageUrl, deliveryNote, orderId);
        } catch (Exception ignored) {
            String podInfo = "POD_IMAGE:" + (podImageUrl != null ? podImageUrl : "")
                    + (deliveryNote != null && !deliveryNote.isBlank() ? " | Ghi chú: " + deliveryNote : "");
            try {
                jdbc.update("UPDATE orders SET delivery_failure_reason = ? WHERE id = ?", podInfo, orderId);
            } catch (Exception ignored2) {}
        }
    }

    private String firstNonNullStr(Map<String, Object> map, String... keysAndDefault) {
        if (map == null || keysAndDefault.length == 0) return "";
        int limit = keysAndDefault.length - 1;
        for (int i = 0; i < limit; i++) {
            Object val = map.get(keysAndDefault[i]);
            if (val != null && !val.toString().isBlank()) {
                return val.toString();
            }
        }
        return keysAndDefault[limit];
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