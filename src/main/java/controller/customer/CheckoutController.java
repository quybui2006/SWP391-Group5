package controller.customer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.MultiValueMap;
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

    /** Nhận service checkout và cổng thanh toán để xử lý trang xác nhận đơn. */
    public CheckoutController(OrderCheckoutService checkout, VNPayService vnPayService, JdbcTemplate jdbc) {
        this.checkout = checkout;
        this.vnPayService = vnPayService;
        this.jdbc = jdbc;
    }

    /** Nhận giỏ hàng, tính giá khuyến mãi và xem trước voucher theo từng shop. */
    @PostMapping("/checkout")
    public String review(@RequestParam(required = false) List<Long> selectedVariantIds,
                         @RequestParam MultiValueMap<String, String> params,
                         HttpSession session, Model model, RedirectAttributes redirect) {
        Long userId = customerId(session);
        if (selectedVariantIds == null || selectedVariantIds.isEmpty()) {
            redirect.addFlashAttribute("error", "Hãy chọn ít nhất một sản phẩm.");
            return "redirect:/cart";
        }
        var items = userId == null
                ? checkout.guestItems(guestCart(session), selectedVariantIds)
                : checkout.items(userId, selectedVariantIds);
        if (items.size() != selectedVariantIds.stream().distinct().count()) {
            redirect.addFlashAttribute("error", "Một số sản phẩm trong giỏ đã bị ẩn hoặc ngừng bán. Hãy tải lại giỏ hàng.");
            return "redirect:/cart";
        }

        BigDecimal subtotal = items.stream()
                .map(item -> ((BigDecimal) item.get("price")).multiply(BigDecimal.valueOf(((Number) item.get("quantity")).longValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, String> codes = voucherCodes(params);
        Map<Long, Map<String, Object>> checkoutShops = new java.util.LinkedHashMap<>();
        items.forEach(item -> {
            Long shopId = ((Number) item.get("shop_id")).longValue();
            Map<String, Object> shop = checkoutShops.computeIfAbsent(shopId, ignored -> {
                Map<String, Object> value = new java.util.LinkedHashMap<>();
                value.put("shopId", shopId); value.put("shopName", item.get("shop_name"));
                value.put("subtotal", BigDecimal.ZERO); value.put("discount", BigDecimal.ZERO); value.put("code", codes.getOrDefault(shopId, ""));
                return value;
            });
            BigDecimal line = ((BigDecimal) item.get("price")).multiply(BigDecimal.valueOf(((Number) item.get("quantity")).longValue()));
            shop.put("subtotal", ((BigDecimal) shop.get("subtotal")).add(line));
        });
        BigDecimal voucherDiscount = BigDecimal.ZERO;
        for (Map.Entry<Long, Map<String, Object>> entry : checkoutShops.entrySet()) {
            Map<String, Object> shop = entry.getValue();
            var quote = checkout.previewVoucher(entry.getKey(), (String) shop.get("code"), (BigDecimal) shop.get("subtotal"));
            shop.put("discount", quote.discount()); shop.put("voucherError", quote.error());
            voucherDiscount = voucherDiscount.add(quote.discount());
        }

        model.addAttribute("checkoutItems", items);
        model.addAttribute("addresses", userId == null ? List.of() : checkout.addresses(userId));
        model.addAttribute("provinces", checkout.provinces());
        model.addAttribute("selectedVariantIds", selectedVariantIds);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("checkoutShops", checkoutShops.values());
        model.addAttribute("voucherDiscount", voucherDiscount);
        model.addAttribute("guest", userId == null);

        BigDecimal shippingFee = new BigDecimal("20000").multiply(BigDecimal.valueOf(items.stream()
                .map(item -> ((Number) item.get("shop_id")).longValue()).distinct().count()));
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("total", subtotal.add(shippingFee).subtract(voucherDiscount));
        return "customer/checkout";
    }

    /** Xác thực dữ liệu thanh toán rồi chuyển yêu cầu đặt hàng vào service checkout. */
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
            @RequestParam MultiValueMap<String, String> params,
            HttpSession session,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = customerId(session);
        try {
            OrderCheckoutService.CheckoutResult result;
            if (userId == null) {
                if ("VNPAY".equalsIgnoreCase(paymentMethod)) throw new IllegalArgumentException("Thanh toán online hiện yêu cầu đăng nhập. Guest có thể đặt COD.");
                Map<Long, Integer> guest = guestCart(session);
                var guestResult = checkout.placeGuestOrder(guest, selectedVariantIds,
                        params.getFirst("guestEmail"), recipientName, recipientPhone, provinceCode,
                        wardName, addressDetail, customerNote, voucherCodes(params));
                result = guestResult.checkout();
                selectedVariantIds.forEach(guest::remove);
                session.setAttribute("guestCart", guest);
                session.setAttribute("guestOrderAccessToken", guestResult.guestAccessToken());
            } else {
                result = checkout.placeOrder(userId, selectedVariantIds, addressId, recipientName,
                        recipientPhone, provinceCode, wardName, addressDetail, customerNote,
                        paymentMethod, voucherCodes(params));
            }

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
            model.addAttribute("guestAccessToken", session.getAttribute("guestOrderAccessToken"));
            model.addAttribute("orderCodes", String.join(", ", result.orderCodes()));
            model.addAttribute("suggestedProducts", loadSuggestedProducts());
            return "customer/payment-result";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart";
        }
    }

    /** Đọc các mã voucher gửi lên, ghép mã với shop tương ứng từ tên trường form. */
    private Map<Long, String> voucherCodes(MultiValueMap<String, String> params) {
        Map<Long, String> codes = new HashMap<>();
        if (params == null) return codes;
        params.forEach((key, values) -> {
            if (key.startsWith("voucherCode_")) {
                try {
                    String value = values == null || values.isEmpty() ? "" : values.get(0);
                    if (!value.isBlank()) codes.put(Long.parseLong(key.substring("voucherCode_".length())), value);
                } catch (NumberFormatException ignored) { }
            }
        });
        return codes;
    }

    @SuppressWarnings("unchecked")
    /** Đọc giỏ hàng khách vãng lai từ session để tạo đơn mà không cần tài khoản. */
    private Map<Long, Integer> guestCart(HttpSession session) {
        Object value = session.getAttribute("guestCart");
        return value instanceof Map<?, ?> map ? (Map<Long, Integer>) map : new java.util.LinkedHashMap<>();
    }

    /** Kiểm tra kết quả VNPay và cập nhật đơn; thanh toán lỗi sẽ hủy đơn và trả lượt voucher. */
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
                            "JOIN shops s ON s.id = pv.shop_id " +
                            "WHERE p.is_visible = 1 AND pv.status = 'ACTIVE' AND pv.approval_status IN ('APPROVED','PENDING') " +
                            "AND s.approval_status = 'APPROVED' AND s.operating_status = 'OPEN' " +
                            "ORDER BY pv.id DESC LIMIT 8"
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Trả về mã khách đang đăng nhập, hoặc null nếu đây là khách vãng lai. */
    private Long customerId(HttpSession session) {
        if (!"CUSTOMER".equals(session.getAttribute("role"))) return null;
        Object id = session.getAttribute("userId");
        return id instanceof Number number ? number.longValue() : null;
    }
}
