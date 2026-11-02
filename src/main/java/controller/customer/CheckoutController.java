package controller.customer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.customer.OrderCheckoutService;
import service.vnpay.VNPayService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CheckoutController {

    private static final BigDecimal INTRA_PROVINCE_SHIPPING_FEE = new BigDecimal("14000");
    private static final BigDecimal INTER_PROVINCE_SHIPPING_FEE = new BigDecimal("30000");

    private final OrderCheckoutService checkout;
    private final VNPayService vnPayService;
    private final JdbcTemplate jdbc;

    public CheckoutController(OrderCheckoutService checkout, VNPayService vnPayService, JdbcTemplate jdbc) {
        this.checkout = checkout;
        this.vnPayService = vnPayService;
        this.jdbc = jdbc;
    }

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
                .map(item -> ((BigDecimal) item.get("price"))
                        .multiply(BigDecimal.valueOf(((Number) item.get("quantity")).longValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, Long> submittedVoucherIds = voucherIds(params);
        Map<Long, Map<String, Object>> checkoutShops = new LinkedHashMap<>();

        // Gom sản phẩm theo shop và lấy mã tỉnh của từng shop.
        for (Map<String, Object> item : items) {
            Long shopId = ((Number) item.get("shop_id")).longValue();
            Map<String, Object> shop = checkoutShops.computeIfAbsent(shopId, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("shopId", shopId);
                value.put("shopName", item.get("shop_name"));

                String shopProvinceCode = "";
                try {
                    String dbCode = jdbc.queryForObject(
                            "SELECT province_code FROM shops WHERE id = ?",
                            String.class,
                            shopId
                    );
                    if (dbCode != null) {
                        shopProvinceCode = dbCode.trim();
                    }
                } catch (org.springframework.dao.EmptyResultDataAccessException ignoredException) {
                    // Giữ mã tỉnh rỗng; khi đó phí ship tạm tính theo mức ngoại tỉnh.
                }
                value.put("shopProvinceCode", shopProvinceCode);
                value.put("subtotal", BigDecimal.ZERO);
                value.put("discount", BigDecimal.ZERO);
                value.put("shippingFee", INTER_PROVINCE_SHIPPING_FEE);
                return value;
            });

            BigDecimal lineTotal = ((BigDecimal) item.get("price"))
                    .multiply(BigDecimal.valueOf(((Number) item.get("quantity")).longValue()));
            shop.put("subtotal", ((BigDecimal) shop.get("subtotal")).add(lineTotal));
        }

        // Lấy địa chỉ đã lưu. Service đang sắp xếp địa chỉ mặc định lên đầu.
        List<Map<String, Object>> addresses = userId == null
                ? List.of()
                : checkout.addresses(userId);

        // Dùng địa chỉ được gửi kèm request nếu có; nếu chưa có thì dùng địa chỉ mặc định/đầu tiên.
        // Đây chỉ là phí tạm tính ở giao diện; Service sẽ tự kiểm tra và tính lại khi đặt hàng.
        String customerProvinceCode = resolveCustomerProvinceCode(params, addresses);

        BigDecimal voucherDiscount = BigDecimal.ZERO;
        BigDecimal shippingFee = BigDecimal.ZERO;

        for (Map.Entry<Long, Map<String, Object>> entry : checkoutShops.entrySet()) {
            Long shopId = entry.getKey();
            Map<String, Object> shop = entry.getValue();
            BigDecimal shopSubtotal = (BigDecimal) shop.get("subtotal");

            Long selectedVoucherId = userId == null ? null : submittedVoucherIds.get(shopId);
            List<Map<String, Object>> savedVouchers = userId == null
                    ? List.of()
                    : checkout.savedVouchers(userId, shopId, shopSubtotal);

            shop.put("savedVouchers", savedVouchers);
            shop.put("selectedVoucherId", selectedVoucherId);

            if (selectedVoucherId != null) {
                OrderCheckoutService.VoucherQuote quote = checkout.previewSavedVoucher(
                        userId, shopId, selectedVoucherId, shopSubtotal
                );
                shop.put("discount", quote.discount());
                shop.put("voucherError", quote.error());
                voucherDiscount = voucherDiscount.add(quote.discount());
            }

            String shopProvinceCode = String.valueOf(shop.getOrDefault("shopProvinceCode", ""));
            BigDecimal shopShippingFee = calculateShippingFee(customerProvinceCode, shopProvinceCode);
            shop.put("shippingFee", shopShippingFee);
            shippingFee = shippingFee.add(shopShippingFee);
        }

        BigDecimal total = subtotal.add(shippingFee).subtract(voucherDiscount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        model.addAttribute("checkoutItems", items);
        model.addAttribute("addresses", addresses);
        model.addAttribute("provinces", checkout.provinces());
        model.addAttribute("selectedVariantIds", selectedVariantIds);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("checkoutShops", checkoutShops.values());
        model.addAttribute("voucherDiscount", voucherDiscount);
        model.addAttribute("guest", userId == null);
        model.addAttribute("customerProvinceCode", customerProvinceCode);
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("total", total);

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
            @RequestParam MultiValueMap<String, String> params,
            HttpSession session,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = customerId(session);
        try {
            OrderCheckoutService.CheckoutResult result;

            if (userId == null) {
                if ("VNPAY".equalsIgnoreCase(paymentMethod)) {
                    throw new IllegalArgumentException("Thanh toán online hiện yêu cầu đăng nhập. Guest có thể đặt COD.");
                }

                Map<Long, Integer> guest = guestCart(session);
                if (!voucherIds(params).isEmpty()) {
                    throw new IllegalArgumentException("Bạn cần đăng nhập để sử dụng voucher đã lưu.");
                }

                OrderCheckoutService.GuestCheckoutResult guestResult = checkout.placeGuestOrder(
                        guest,
                        selectedVariantIds,
                        params.getFirst("guestEmail"),
                        recipientName,
                        recipientPhone,
                        provinceCode,
                        wardName,
                        addressDetail,
                        customerNote,
                        Map.of()
                );

                result = guestResult.checkout();
                selectedVariantIds.forEach(guest::remove);
                session.setAttribute("guestCart", guest);
                session.setAttribute("guestOrderAccessToken", guestResult.guestAccessToken());

            } else {
                result = checkout.placeOrder(
                        userId,
                        selectedVariantIds,
                        addressId,
                        recipientName,
                        recipientPhone,
                        provinceCode,
                        wardName,
                        addressDetail,
                        customerNote,
                        paymentMethod,
                        voucherIds(params)
                );
            }

            // VNPay: chuyển hướng sang cổng thanh toán.
            if ("VNPAY".equalsIgnoreCase(result.paymentMethod())) {
                String vnpayUrl = vnPayService.createPaymentUrl(
                        result.checkoutCode(), result.totalAmount(), request
                );
                return "redirect:" + vnpayUrl;
            }

            // COD: hiển thị trang kết quả đặt hàng.
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
            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage() == null ? "Không thể đặt hàng. Vui lòng thử lại." : e.getMessage()
            );
            return "redirect:/cart";
        }
    }

    /**
     * Phí ship được tính riêng cho từng shop:
     * - Cùng tỉnh/thành: 14.000đ.
     * - Khác tỉnh/thành hoặc chưa xác định được tỉnh: 30.000đ.
     */
    private BigDecimal calculateShippingFee(String customerProvinceCode, String shopProvinceCode) {
        if (customerProvinceCode != null && !customerProvinceCode.isBlank()
                && shopProvinceCode != null && !shopProvinceCode.isBlank()
                && customerProvinceCode.trim().equals(shopProvinceCode.trim())) {
            return INTRA_PROVINCE_SHIPPING_FEE;
        }
        return INTER_PROVINCE_SHIPPING_FEE;
    }

    /**
     * Xác định tỉnh khách để tạm tính phí ship trên trang checkout.
     * Ưu tiên provinceCode được gửi lên; tiếp theo là addressId được chọn;
     * cuối cùng là địa chỉ đầu tiên (Service addresses() sắp xếp mặc định lên đầu).
     */
    private String resolveCustomerProvinceCode(MultiValueMap<String, String> params,
                                               List<Map<String, Object>> addresses) {
        if (params != null) {
            String submittedProvinceCode = params.getFirst("provinceCode");
            if (submittedProvinceCode != null && !submittedProvinceCode.isBlank()) {
                return submittedProvinceCode.trim();
            }

            String addressIdValue = params.getFirst("addressId");
            if (addressIdValue != null && !addressIdValue.isBlank()) {
                try {
                    long selectedAddressId = Long.parseLong(addressIdValue.trim());
                    for (Map<String, Object> address : addresses) {
                        Object id = address.get("id");
                        if (id instanceof Number number && number.longValue() == selectedAddressId) {
                            Object code = address.get("province_code");
                            return code == null ? "" : code.toString().trim();
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Bỏ qua addressId không hợp lệ khi chỉ tính tạm trên giao diện.
                }
            }
        }

        if (addresses != null && !addresses.isEmpty()) {
            Object code = addresses.get(0).get("province_code");
            return code == null ? "" : code.toString().trim();
        }
        return "";
    }

    private Map<Long, Long> voucherIds(MultiValueMap<String, String> params) {
        Map<Long, Long> ids = new HashMap<>();
        if (params == null) {
            return ids;
        }

        params.forEach((key, values) -> {
            if (key.startsWith("voucherId_")) {
                try {
                    String value = values == null || values.isEmpty() ? "" : values.get(0);
                    if (!value.isBlank()) {
                        Long shopId = Long.parseLong(key.substring("voucherId_".length()));
                        Long voucherId = Long.parseLong(value);
                        ids.put(shopId, voucherId);
                    }
                } catch (NumberFormatException ignored) {
                    // Bỏ qua tham số voucher sai định dạng; Service sẽ kiểm tra lại voucher hợp lệ.
                }
            }
        });
        return ids;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> guestCart(HttpSession session) {
        Object value = session.getAttribute("guestCart");
        return value instanceof Map<?, ?> map
                ? (Map<Long, Integer>) map
                : new LinkedHashMap<>();
    }

    @GetMapping("/api/wards")
    @ResponseBody
    public List<Map<String, Object>> getWards(@RequestParam("provinceCode") String provinceCode) {
        return checkout.getWardsByProvince(provinceCode);
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
        } catch (Exception ignored) {
            // Giữ tổng tiền bằng 0 nếu tham số callback không hợp lệ.
        }

        boolean isSuccess = isValidSignature && "00".equals(responseCode);
        String orderCodesStr = "";

        if (checkoutCode != null && !checkoutCode.isEmpty()) {
            try {
                List<String> orderCodes = checkout.handleVnPayCallback(
                        checkoutCode,
                        isSuccess,
                        allParams.get("vnp_TransactionNo"),
                        allParams.get("vnp_PayDate")
                );
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
                            "WHERE p.is_visible = 1 AND pv.status = 'ACTIVE' " +
                            "AND pv.approval_status IN ('APPROVED','PENDING') " +
                            "AND s.approval_status = 'APPROVED' AND s.operating_status = 'OPEN' " +
                            "ORDER BY pv.id DESC LIMIT 8"
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    @PostMapping("/customer/account/orders/{orderId}/cancel")
    public String cancelMyOrder(@PathVariable Long orderId,
                                @RequestParam(required = false) String cancelReason,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        Long userId = customerId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        try {
            checkout.cancelMyOrder(userId, orderId, cancelReason);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Hủy đơn hàng và hoàn tiền (nếu có) thành công!"
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Có lỗi hệ thống xảy ra khi xử lý hủy đơn."
            );
        }

        return "redirect:/customer/account/orders";
    }

    private Long customerId(HttpSession session) {
        if (!"CUSTOMER".equals(session.getAttribute("role"))) {
            return null;
        }
        Object id = session.getAttribute("userId");
        return id instanceof Number number ? number.longValue() : null;
    }
}
