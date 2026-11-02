package controller.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class OrderTrackingController {

    private final JdbcTemplate jdbc;

    public OrderTrackingController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/order/tracking")
    public String trackOrder(@RequestParam(value = "code", required = false) String code, Model model) {
        if (code == null || code.isBlank()) {
            model.addAttribute("searched", false);
            return "customer/order-tracking";
        }

        String orderCode = code.trim().toUpperCase();
        model.addAttribute("searched", true);
        model.addAttribute("searchCode", orderCode);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT o.*, s.name AS shop_name, s.phone AS shop_phone, c.contact_email
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            JOIN shops s ON s.id = o.shop_id
            WHERE o.order_code = ?
            """, orderCode);

        if (rows.isEmpty()) {
            model.addAttribute("found", false);
            model.addAttribute("errorMessage", "Không tìm thấy đơn hàng với mã \"" + orderCode + "\". Vui lòng kiểm tra lại mã đơn hàng.");
            return "customer/order-tracking";
        }

        Map<String, Object> order = new LinkedHashMap<>(rows.get(0));
        Long orderId = ((Number) order.get("id")).longValue();

        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT oi.id AS order_item_id, oi.variant_id, oi.product_name, oi.variant_name, oi.sku,
                   oi.quantity, oi.unit_price, oi.sale_unit_price, oi.line_total,
                   oi.image_url, oi.base_unit_name
            FROM order_items oi
            WHERE oi.order_id = ?
            ORDER BY oi.id
            """, orderId);
        order.put("items", items);

        List<Map<String, Object>> payments = jdbc.queryForList(
                "SELECT payment_code, method, amount, status, paid_at FROM payments WHERE order_id = ? ORDER BY id", orderId);
        order.put("payments", payments);

        for (String field : List.of("created_at", "shipped_at", "delivered_at", "completed_at", "cancelled_at", "paid_at")) {
            order.put(field + "_fmt", formatOrderDate(order.get(field)));
        }

        order.put("tracking", orderTracking(order));

        model.addAttribute("found", true);
        model.addAttribute("order", order);

        return "customer/order-tracking";
    }

    private String formatOrderDate(Object value) {
        if (value == null) return "—";
        LocalDateTime dateTime;
        if (value instanceof java.sql.Timestamp timestamp) dateTime = timestamp.toLocalDateTime();
        else if (value instanceof LocalDateTime localDateTime) dateTime = localDateTime;
        else if (value instanceof java.util.Date date)
            dateTime = LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        else return value.toString();
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private Map<String, Object> orderTracking(Map<String, Object> order) {
        String status = normalized(order.get("status"));
        String deliveryStatus = normalized(order.get("delivery_status"));

        int progress = switch (status) {
            case "CONFIRMED" -> 2;
            case "PREPARING" -> 2;
            case "SHIPPING" -> 3;
            case "DELIVERED" -> 4;
            case "COMPLETED" -> 5;
            default -> 0;
        };
        if ("READY".equals(deliveryStatus)) progress = Math.max(progress, 2);
        if ("IN_TRANSIT".equals(deliveryStatus)) progress = Math.max(progress, 3);
        if ("DELIVERED".equals(deliveryStatus) || order.get("delivered_at") != null)
            progress = Math.max(progress, 4);
        if (order.get("completed_at") != null || "COMPLETED".equals(status))
            progress = Math.max(progress, 5);

        String currentStep = switch (status) {
            case "PENDING" -> isPaid(order) ? "CONFIRMED" : "PLACED";
            case "CONFIRMED" -> "CONFIRMED";
            case "PREPARING" -> "PREPARING";
            case "SHIPPING" -> "SHIPPING";
            case "DELIVERED" -> "DELIVERED";
            case "COMPLETED" -> "COMPLETED";
            case "CANCELLED" -> "CANCELLED";
            default -> progress >= 5 ? "COMPLETED" : progress >= 4 ? "DELIVERED"
                    : progress >= 3 ? "SHIPPING" : progress >= 2 ? "PREPARING"
                    : progress >= 1 ? "CONFIRMED" : "PLACED";
        };
        if ("READY".equals(deliveryStatus) && "CONFIRMED".equals(status)) currentStep = "PREPARING";
        if ("IN_TRANSIT".equals(deliveryStatus) && progress >= 3) currentStep = "SHIPPING";
        if ("DELIVERED".equals(deliveryStatus) && progress >= 4) currentStep = "DELIVERED";
        if ("COMPLETED".equals(status)) currentStep = "COMPLETED";

        Map<String, Object> tracking = new LinkedHashMap<>();
        tracking.put("confirmedClass", trackingClass(progress >= 1, "CONFIRMED".equals(currentStep)));
        tracking.put("preparingClass", trackingClass(progress >= 2, "PREPARING".equals(currentStep)));
        tracking.put("shippingClass", trackingClass(progress >= 3, "SHIPPING".equals(currentStep)));
        tracking.put("deliveredClass", trackingClass(progress >= 4, "DELIVERED".equals(currentStep)));
        tracking.put("completedClass", trackingClass(progress >= 5, "COMPLETED".equals(currentStep)));
        tracking.put("currentStep", currentStep);
        return tracking;
    }

    private String trackingClass(boolean done, boolean active) {
        if (done && active) return "done active";
        if (done) return "done";
        return active ? "active" : "";
    }

    private boolean isPaid(Map<String, Object> order) {
        return "PAID".equals(normalized(order.get("payment_status")));
    }

    private String normalized(Object value) {
        return value == null ? "" : value.toString().trim().toUpperCase(java.util.Locale.ROOT);
    }
}
