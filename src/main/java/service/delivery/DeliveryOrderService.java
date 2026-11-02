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
<<<<<<< HEAD
import service.shop.VoucherUsageService;
import service.shop.InventoryBatchService;
=======
>>>>>>> develop

@Service
public class DeliveryOrderService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("20000");

    private final JdbcTemplate jdbc;
<<<<<<< HEAD
    private final VoucherUsageService voucherUsage;
    private final InventoryBatchService inventoryBatches;

    public DeliveryOrderService(JdbcTemplate jdbc, VoucherUsageService voucherUsage, InventoryBatchService inventoryBatches) {
        this.jdbc = jdbc;
        this.voucherUsage = voucherUsage;
        this.inventoryBatches = inventoryBatches;
=======
    private final OrderCheckoutService checkout;
    @SuppressWarnings("unused")
    private final VNPayService vnPayService;

    public DeliveryOrderService(JdbcTemplate jdbc, OrderCheckoutService checkout, VNPayService vnPayService) {
        this.jdbc = jdbc;
        this.checkout = checkout;
        this.vnPayService = vnPayService;
>>>>>>> develop
    }

    /**
     * Chỉ lấy các đơn hàng mà Shop Owner đã bàn giao (không lấy đơn PENDING chưa duyệt).
     */
    public List<Map<String, Object>> getDeliveryOrders() {
        String sql = """
            SELECT o.*,
                   s.name AS joined_shop_name
            FROM orders o
            LEFT JOIN shops s ON s.id = o.shop_id
            WHERE o.status IN ('PREPARING', 'SHIPPING', 'DELIVERED', 'COMPLETED')
               OR (o.status = 'CANCELLED' AND o.delivery_status IN ('IN_TRANSIT', 'FAILED'))
            ORDER BY o.id DESC
        """;

        var rawOrders = jdbc.queryForList(sql);
        var result = new ArrayList<Map<String, Object>>();

        for (var row : rawOrders) {
            String rawStatus = firstNonNullStr(row, "status", "order_status", "PENDING")
                    .toUpperCase(Locale.ROOT);
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

    /** Thống kê nhanh số lượng đơn theo trạng thái đang hiển thị ở màn giao hàng. */
    public Map<String, Object> getOrderCounts() {
        String sql = """
            SELECT
                COUNT(*) AS total_all,
                SUM(CASE WHEN status = 'PREPARING' THEN 1 ELSE 0 END) AS waiting_pickup,
                SUM(CASE WHEN status = 'SHIPPING' THEN 1 ELSE 0 END) AS shipping_count,
                SUM(CASE WHEN status IN ('DELIVERED', 'COMPLETED') THEN 1 ELSE 0 END) AS delivered_count,
                SUM(CASE WHEN status = 'CANCELLED' AND delivery_status IN ('IN_TRANSIT', 'FAILED') THEN 1 ELSE 0 END) AS cancelled_count
            FROM orders
            WHERE status IN ('PREPARING', 'SHIPPING', 'DELIVERED', 'COMPLETED')
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
            case "CONFIRMED", "PROCESSING", "PREPARING", "PACKING" -> "PROCESSING";
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
                if (!cList.isEmpty()) {
                    checkoutRow = cList.get(0);
                }
            } catch (Exception ignored) {
                // Một số phiên bản schema không còn lưu bảng checkouts.
            }
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
                if (!pNames.isEmpty()) {
                    provinceName = pNames.get(0);
                }
            } catch (Exception ignored) {
                // Không có bảng provinces hoặc mã tỉnh không tồn tại.
            }
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
        ord.put("tracking_code", firstNonNullStr(row, "delivery_code", "tracking_code", "DL-" + orderId));
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

    /** Hàm 3 tham số giữ nguyên để tương thích với code hiện tại. */
    @Transactional
    public void updateDeliveryStatus(Long orderId, String newStatus, String cancelReason) {
        updateDeliveryStatus(orderId, newStatus, cancelReason, null, null);
    }

    /**
     * Cập nhật trạng thái giao hàng: Shop bàn giao -> Shipper nhận hàng -> Giao thành công
     * kèm POD -> Hoàn tất / Hủy. Đồng thời cập nhật tồn kho theo allocation_status.
     */
    @Transactional
    public void updateDeliveryStatus(Long orderId, String newStatus, String cancelReason,
                                     String podImageUrl, String deliveryNote) {
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

        // 1. SHOP BÀN GIAO CHO VẬN CHUYỂN (PREPARING)
        if ("PREPARING".equals(target)) {
            if (!"CONFIRMED".equals(currentStatus) && !"PROCESSING".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ bàn giao đơn hàng đã được Shop xác nhận.");
            }
            String trackingCode = "DL-" + now.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + orderId;
            try {
                jdbc.update("""
                    UPDATE orders
                    SET status = 'PREPARING',
                        delivery_status = 'READY',
                        delivery_code = COALESCE(delivery_code, ?),
                        delivery_updated_at = ?
                    WHERE id = ?
                """, trackingCode, now, orderId);
            } catch (Exception e) {
                // Tương thích schema cũ chưa có cột delivery_code.
                jdbc.update("UPDATE orders SET status = 'PREPARING', delivery_status = 'READY', delivery_updated_at = ? WHERE id = ?",
                        now, orderId);
            }
            return;
        }

        // 2. SHIPPER NHẬN HÀNG & BẮT ĐẦU GIAO (SHIPPING)
        if ("SHIPPING".equals(target)) {
            if (!"CONFIRMED".equals(currentStatus) && !"PREPARING".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ nhận đơn Shop Owner đã bàn giao để bắt đầu giao.");
            }
            jdbc.update("""
                UPDATE orders
                SET status = 'SHIPPING',
                    delivery_status = 'IN_TRANSIT',
                    shipped_at = ?,
                    delivery_updated_at = ?
                WHERE id = ? AND status IN ('CONFIRMED', 'PREPARING')
            """, now, now, orderId);
            return;
        }

        // 3. ĐÃ GIAO ĐẾN NƠI + LƯU MINH CHỨNG POD (DELIVERED)
        if ("DELIVERED".equals(target)) {
            if (!"SHIPPING".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ xác nhận đã giao đối với đơn đang vận chuyển.");
            }

            // Hàng đã giao cho khách: trừ số lượng đã giữ khỏi tồn kho thực tế.
            consumeReservedItems(orderId);

            if ("COD".equals(paymentMethod) && "UNPAID".equals(paymentStatus)) {
                jdbc.update("UPDATE payments SET status='SUCCEEDED', paid_at=? WHERE order_id=? AND status='PENDING'", now, orderId);
                jdbc.update("""
                    UPDATE orders
                    SET status='DELIVERED', payment_status='PAID', paid_at=?,
                        delivery_status='DELIVERED', delivered_at=?,
                        auto_complete_at=DATE_ADD(?, INTERVAL 3 DAY), delivery_updated_at=?
                    WHERE id=? AND status='SHIPPING'
                """, now, now, now, now, orderId);
            } else {
                jdbc.update("""
                    UPDATE orders
                    SET status='DELIVERED', delivery_status='DELIVERED', delivered_at=?,
                        auto_complete_at=DATE_ADD(?, INTERVAL 3 DAY), delivery_updated_at=?
                    WHERE id=? AND status='SHIPPING'
                """, now, now, now, orderId);
            }
            savePodEvidence(orderId, podImageUrl, deliveryNote);
            return;
        }

        // 4. HOÀN TẤT ĐƠN HÀNG (COMPLETED)
        if ("COMPLETED".equals(target)) {
            if (!"SHIPPING".equals(currentStatus) && !"DELIVERED".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ hoàn tất đơn đang được giao.");
            }

            // Hàm này có kiểm tra idempotent: nếu đã CONSUMED khi DELIVERED thì không trừ lần nữa.
            consumeReservedItems(orderId);

            if ("COD".equals(paymentMethod) && "UNPAID".equals(paymentStatus)) {
                jdbc.update("UPDATE payments SET status='SUCCEEDED', paid_at=? WHERE order_id=? AND status='PENDING'", now, orderId);
                jdbc.update("""
                    UPDATE orders
                    SET status='COMPLETED', payment_status='PAID', paid_at=?,
                        delivery_status='DELIVERED', delivered_at=COALESCE(delivered_at, ?),
                        auto_complete_at=COALESCE(auto_complete_at, DATE_ADD(?, INTERVAL 3 DAY)),
                        completed_at=?, delivery_updated_at=?
                    WHERE id=? AND status IN ('SHIPPING', 'DELIVERED')
                """, now, now, now, now, now, orderId);
            } else {
                jdbc.update("""
                    UPDATE orders
                    SET status='COMPLETED', delivery_status='DELIVERED',
                        delivered_at=COALESCE(delivered_at, ?),
                        auto_complete_at=COALESCE(auto_complete_at, DATE_ADD(?, INTERVAL 3 DAY)),
                        completed_at=?, delivery_updated_at=?
                    WHERE id=? AND status IN ('SHIPPING', 'DELIVERED')
                """, now, now, now, now, orderId);
            }
            savePodEvidence(orderId, podImageUrl, deliveryNote);
            checkout.creditCashbackPoints(orderId);
            return;
        }

        // 5. GIAO HÀNG THẤT BẠI / KHÁCH TỪ CHỐI NHẬN: hoàn lại tồn kho.
        if ("CANCELLED".equals(target)) {
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
                WHERE id = ? AND status IN ('CONFIRMED', 'PROCESSING', 'SHIPPING', 'PREPARING')
            """, reason, now, now, reason, orderId);

            jdbc.update("UPDATE payments SET status='CANCELLED', failure_reason=? WHERE order_id=? AND status='PENDING'",
                    reason, orderId);
            return;
        }

        throw new IllegalArgumentException("Trạng thái giao hàng không hợp lệ.");
    }

    private void savePodEvidence(Long orderId, String podImageUrl, String deliveryNote) {
        if ((podImageUrl == null || podImageUrl.isBlank())
                && (deliveryNote == null || deliveryNote.isBlank())) {
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
            // Fallback cho schema chưa có cột POD.
            String podInfo = "POD_IMAGE:" + (podImageUrl != null ? podImageUrl : "")
                    + (deliveryNote != null && !deliveryNote.isBlank() ? " | Ghi chú: " + deliveryNote : "");
            try {
                jdbc.update("UPDATE orders SET delivery_failure_reason = ? WHERE id = ?", podInfo, orderId);
            } catch (Exception ignored2) {
                // Không làm hỏng thao tác cập nhật trạng thái chỉ vì schema thiếu cột lưu POD.
            }
        }
    }

    /** Chuyển lượng tồn kho đã giữ (RESERVED) thành lượng đã bán (CONSUMED). */
    private void consumeReservedItems(Long orderId) {
        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT oi.id, oi.inventory_id, oi.quantity
            FROM order_items oi
            WHERE oi.order_id = ? AND oi.allocation_status = 'RESERVED'
            ORDER BY oi.inventory_id
            FOR UPDATE
        """, orderId);

        if (items.isEmpty()) {
            Integer soldItems = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM order_items WHERE order_id=? AND allocation_status='CONSUMED'",
                    Integer.class, orderId);
            if (soldItems != null && soldItems > 0) {
                return;
            }
            throw new IllegalStateException("Không tìm thấy tồn kho đã giữ cho đơn hàng.");
        }

        for (Map<String, Object> item : items) {
            long inventoryId = ((Number) item.get("inventory_id")).longValue();
            long itemId = ((Number) item.get("id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();

            if (jdbc.update("""
                UPDATE variant_inventory
                SET quantity_on_hand = quantity_on_hand - ?,
                    reserved_quantity = reserved_quantity - ?
                WHERE id = ? AND quantity_on_hand >= ? AND reserved_quantity >= ?
            """, quantity, quantity, inventoryId, quantity, quantity) != 1) {
                throw new IllegalStateException("Tồn kho đã giữ không còn nhất quán cho mặt hàng " + itemId + ".");
            }

            jdbc.update("UPDATE order_items SET allocation_status='CONSUMED' WHERE id=? AND allocation_status='RESERVED'",
                    itemId);

            int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            jdbc.update("""
                INSERT INTO inventory_transactions(
                    inventory_id, order_item_id, type, on_hand_delta, reserved_delta,
                    on_hand_after, reserved_after, event_key, created_by
                ) VALUES (?, ?, 'SALE', ?, ?, ?, ?, ?, NULL)
            """, inventoryId, itemId, -quantity, -quantity, onHand, reserved, "sale-delivery-" + itemId);
        }
    }

    /** Hoàn lại tồn kho khi giao hàng thất bại, kể cả trường hợp hàng đã được đánh dấu CONSUMED. */
    private void releaseReservedItems(Long orderId) {
        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT oi.id, oi.inventory_id, oi.quantity, oi.allocation_status
            FROM order_items oi
            WHERE oi.order_id = ? AND oi.allocation_status IN ('RESERVED', 'CONSUMED')
            ORDER BY oi.inventory_id
            FOR UPDATE
        """, orderId);

        for (Map<String, Object> item : items) {
            long inventoryId = ((Number) item.get("inventory_id")).longValue();
            long itemId = ((Number) item.get("id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();
            boolean alreadySold = "CONSUMED".equals(item.get("allocation_status"));

            List<Map<String, Object>> invCheck = jdbc.queryForList(
                    "SELECT id FROM variant_inventory WHERE id=? FOR UPDATE", inventoryId);
            if (invCheck.isEmpty()) {
                jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE id=?", itemId);
                continue;
            }

            if (alreadySold) {
                if (jdbc.update("UPDATE variant_inventory SET quantity_on_hand=quantity_on_hand+? WHERE id=?",
                        quantity, inventoryId) != 1) {
                    throw new IllegalStateException("Không thể hoàn kho cho mặt hàng " + itemId + ".");
                }
                jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE id=? AND allocation_status='CONSUMED'",
                        itemId);

                int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
                int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
                jdbc.update("""
                    INSERT INTO inventory_transactions(
                        inventory_id, order_item_id, type, on_hand_delta, reserved_delta,
                        on_hand_after, reserved_after, event_key, reason, created_by
                    ) VALUES (?, ?, 'ADJUST', ?, 0, ?, ?, ?, ?, NULL)
                """, inventoryId, itemId, quantity, onHand, reserved,
                        "return-delivery-" + itemId,
                        "Hoàn kho do giao hàng thất bại - đơn " + orderId);
                continue;
            }

            if (jdbc.update("""
                UPDATE variant_inventory
                SET reserved_quantity = reserved_quantity - ?
                WHERE id = ? AND reserved_quantity >= ?
            """, quantity, inventoryId, quantity) != 1) {
                throw new IllegalStateException("Không thể trả tồn kho đã giữ cho mặt hàng " + itemId + ".");
            }
            jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE id=? AND allocation_status='RESERVED'",
                    itemId);

            int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            jdbc.update("""
                INSERT INTO inventory_transactions(
                    inventory_id, order_item_id, type, on_hand_delta, reserved_delta,
                    on_hand_after, reserved_after, event_key, created_by
                ) VALUES (?, ?, 'RELEASE', 0, ?, ?, ?, ?, NULL)
            """, inventoryId, itemId, -quantity, onHand, reserved, "release-delivery-" + itemId);
        }
    }

    private String firstNonNullStr(Map<String, Object> map, String... keysAndDefault) {
        if (map == null || keysAndDefault.length == 0) {
            return "";
        }
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
<<<<<<< HEAD

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
    /** Cập nhật giao hàng; đơn giao thất bại được hủy và trả lượt voucher giảm tiền. */
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
        if ("DELIVERED".equals(target)) {
            if (!"SHIPPING".equals(currentStatus)) throw new IllegalStateException("Chỉ xác nhận đã giao đối với đơn đang vận chuyển.");
            // Giao thành công vẫn giữ lô cho đến khi khách xác nhận đã nhận hàng.
            if ("COD".equals(paymentMethod) && "UNPAID".equals(paymentStatus)) {
                jdbc.update("UPDATE payments SET status='SUCCEEDED',paid_at=? WHERE order_id=? AND status='PENDING'", now, orderId);
                jdbc.update("UPDATE orders SET status='DELIVERED',payment_status='PAID',paid_at=?,delivery_status='DELIVERED',delivered_at=?,auto_complete_at=DATE_ADD(?,INTERVAL 3 DAY),delivery_updated_at=? WHERE id=? AND status='SHIPPING'", now, now, now, now, orderId);
            } else {
                jdbc.update("UPDATE orders SET status='DELIVERED',delivery_status='DELIVERED',delivered_at=?,auto_complete_at=DATE_ADD(?,INTERVAL 3 DAY),delivery_updated_at=? WHERE id=? AND status='SHIPPING'", now, now, now, orderId);
            }
            return;
        }
        if ("COMPLETED".equals(target)) {
            throw new IllegalStateException("Đơn chỉ được hoàn tất sau khi khách hàng xác nhận đã nhận hàng.");
        }
        if ("CANCELLED".equals(target)) {
            String reason = cancelReason == null || cancelReason.isBlank()
                    ? "Giao hàng không thành công / Khách từ chối nhận" : cancelReason.trim();
            if (reason.length() > 1000) throw new IllegalArgumentException("Lý do không được vượt quá 1000 ký tự.");
            releaseReservedItems(orderId);
            int cancelled = jdbc.update("UPDATE orders SET status='CANCELLED',delivery_status='FAILED',delivery_failure_reason=?,delivery_updated_at=?,cancelled_at=?,cancellation_reason=? WHERE id=? AND status IN ('CONFIRMED','SHIPPING')", reason, now, now, reason, orderId);
            if (cancelled == 1) voucherUsage.returnDiscountUse(orderId);
            jdbc.update("UPDATE payments SET status='CANCELLED',failure_reason=? WHERE order_id=? AND status='PENDING'", reason, orderId);
            return;
        }
        throw new IllegalArgumentException("Trạng thái giao hàng không hợp lệ.");
    }

    private void releaseReservedItems(Long orderId) {
        // A failed delivery releases the existing hold so the quantity returns to sellable stock.
        List<Map<String, Object>> items = jdbc.queryForList("SELECT oi.id,oi.allocation_status FROM order_items oi WHERE oi.order_id=? AND oi.allocation_status='RESERVED' ORDER BY oi.id FOR UPDATE", orderId);
        for (Map<String, Object> item : items) {
            long itemId = ((Number) item.get("id")).longValue();
            inventoryBatches.releaseOrderItem(itemId, "delivery-failed");
            jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE id=? AND allocation_status='RESERVED'", itemId);
        }
    }
=======
>>>>>>> develop
}
