package controller.customer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.customer.OrderCheckoutService;
import service.vnpay.VNPayService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CheckoutController {

    private final OrderCheckoutService checkout;
    private final VNPayService vnPayService;
    private final JdbcTemplate jdbc;

    public CheckoutController(OrderCheckoutService checkout, VNPayService vnPayService, JdbcTemplate jdbc) {
        this.checkout = checkout;
        this.vnPayService = vnPayService;
        this.jdbc = jdbc;
    }

    @PostMapping("/checkout")
    public String review(@RequestParam(required = false) List<Long> selectedVariantIds, HttpSession session, Model model, RedirectAttributes redirect) {
        Long userId = customerId(session);
        if (userId == null) return "redirect:/login";
        if (selectedVariantIds == null || selectedVariantIds.isEmpty()) {
            redirect.addFlashAttribute("error", "Hãy chọn ít nhất một sản phẩm.");
            return "redirect:/cart";
        }
        var items = checkout.items(userId, selectedVariantIds);
        if (items.isEmpty()) return "redirect:/cart";

        BigDecimal subtotal = items.stream()
                .map(item -> ((BigDecimal) item.get("price")).multiply(BigDecimal.valueOf(((Number) item.get("quantity")).longValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("checkoutItems", items);
        model.addAttribute("addresses", checkout.addresses(userId));
        model.addAttribute("provinces", checkout.provinces());
        model.addAttribute("selectedVariantIds", selectedVariantIds);
        model.addAttribute("subtotal", subtotal);

        BigDecimal shippingFee = new BigDecimal("20000").multiply(BigDecimal.valueOf(items.stream()
                .map(item -> ((Number) item.get("shop_id")).longValue()).distinct().count()));
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("total", subtotal.add(shippingFee));
        return "customer/checkout";
    }

    @PostMapping("/order/submit")
    public String submitOrder(
            @RequestParam("selectedVariantIds") List<Long> selectedVariantIds,
            @RequestParam(value = "addressId", required = false) Long addressId,
            @RequestParam(value = "recipientName", required = false) String recipientName,
            @RequestParam(value = "recipientPhone", required = false) String recipientPhone,
            @RequestParam(value = "provinceCode", required = false) String provinceCode,
            @RequestParam(value = "wardName", required = false) String wardName,
            @RequestParam(value = "addressDetail", required = false) String addressDetail,
            @RequestParam(value = "customerNote", required = false) String customerNote,
            @RequestParam(value = "paymentMethod", defaultValue = "COD") String paymentMethod,
            HttpSession session,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = customerId(session);
        if (userId == null) return "redirect:/login";

        try {
            OrderCheckoutService.CheckoutResult result = checkout.placeOrder(
                    userId, selectedVariantIds, addressId, recipientName,
                    recipientPhone, provinceCode, wardName, addressDetail, customerNote, paymentMethod
            );

            // Nếu chọn VNPay -> Chuyển hướng sang cổng VNPay Sandbox
            if ("VNPAY".equalsIgnoreCase(result.paymentMethod())) {
                String vnpayUrl = vnPayService.createPaymentUrl(result.checkoutCode(), result.totalAmount(), request);
                return "redirect:" + vnpayUrl;
            }

            // Nếu chọn COD -> Chuyển sang trang payment-result với trạng thái Đang chờ thanh toán
            model.addAttribute("isSuccess", true);
            model.addAttribute("paymentMethod", "COD");
            model.addAttribute("paymentMethodLabel", "Thanh toán khi nhận hàng (COD)");
            model.addAttribute("totalAmount", result.totalAmount());
            model.addAttribute("checkoutCode", result.checkoutCode());
            model.addAttribute("orderCodes", String.join(", ", result.orderCodes()));
            model.addAttribute("suggestedProducts", loadSuggestedProducts());
            return "customer/payment-result";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart";
        }
    }

    @GetMapping("/payment/vnpay-return")
    public String vnpayReturn(@RequestParam Map<String, String> allParams, Model model) {
        boolean isValidSignature = vnPayService.verifyCallback(new HashMap<>(allParams));
        String responseCode = allParams.get("vnp_ResponseCode");
        String checkoutCode = allParams.get("vnp_TxnRef");

        BigDecimal totalAmount = BigDecimal.ZERO;
        try {
            String rawAmount = allParams.get("vnp_Amount");
            if (rawAmount != null) {
                totalAmount = new BigDecimal(rawAmount).divide(BigDecimal.valueOf(100));
            }
        } catch (Exception ignored) {}

        boolean isSuccess = isValidSignature && "00".equals(responseCode);
        String orderCodesStr = "";

        if (checkoutCode != null && !checkoutCode.isEmpty()) {
            try {
                List<String> orderCodes = checkout.handleVnPayCallback(checkoutCode, isSuccess);
                orderCodesStr = String.join(", ", orderCodes);
            } catch (Exception e) {
                e.printStackTrace();
                isSuccess = false;
            }
        } else {
            isSuccess = false;
        }

        model.addAttribute("isSuccess", isSuccess);
        model.addAttribute("paymentMethod", "VNPAY");
        model.addAttribute("paymentMethodLabel", "Cổng thanh toán VNPay Sandbox");
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("checkoutCode", checkoutCode);
        model.addAttribute("orderCodes", orderCodesStr);
        if (isSuccess) {
            model.addAttribute("suggestedProducts", loadSuggestedProducts());
        }

        return "customer/payment-result";
    }

    private List<Map<String, Object>> loadSuggestedProducts() {
        try {
            return jdbc.queryForList(
                    "SELECT pv.id, pv.name, pv.price, pv.image_url, p.name AS product_name " +
                            "FROM product_variants pv " +
                            "JOIN products p ON p.id = pv.product_id " +
                            "WHERE pv.status = 'ACTIVE' AND p.selling_status = 'ACTIVE' AND p.approval_status = 'APPROVED' " +
                            "ORDER BY pv.id DESC LIMIT 8"
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private Long customerId(HttpSession session) {
        if (!"CUSTOMER".equals(session.getAttribute("role"))) return null;
        Object id = session.getAttribute("userId");
        return id instanceof Number number ? number.longValue() : null;
    }
}