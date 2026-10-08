package controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.OrderCheckoutService;
import java.math.BigDecimal;
import java.util.List;

@Controller
public class CheckoutController {
    private final OrderCheckoutService checkout;
    public CheckoutController(OrderCheckoutService checkout) { this.checkout = checkout; }

    @PostMapping("/checkout")
    public String review(@RequestParam(required = false) List<Long> selectedVariantIds, HttpSession session, Model model, RedirectAttributes redirect) {
        Long userId = customerId(session);
        if (userId == null) return "redirect:/login";
        if (selectedVariantIds == null || selectedVariantIds.isEmpty()) { redirect.addFlashAttribute("error", "Hãy chọn ít nhất một sản phẩm."); return "redirect:/cart"; }
        var items = checkout.items(userId, selectedVariantIds);
        if (items.isEmpty()) return "redirect:/cart";
        BigDecimal subtotal = items.stream().map(item -> ((BigDecimal) item.get("price")).multiply(BigDecimal.valueOf(((Number) item.get("quantity")).longValue()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("checkoutItems", items);
        model.addAttribute("addresses", checkout.addresses(userId));
        model.addAttribute("provinces", checkout.provinces());
        model.addAttribute("selectedVariantIds", selectedVariantIds);
        model.addAttribute("subtotal", subtotal);
        BigDecimal shippingFee = new BigDecimal("20000").multiply(BigDecimal.valueOf(items.stream()
                .map(item -> ((Number) item.get("shop_id")).longValue()).distinct().count()));
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("total", subtotal.add(shippingFee));
        return "checkout";
    }

    @PostMapping("/order/submit")
    public String place(@RequestParam List<Long> selectedVariantIds, @RequestParam(required = false) Long addressId,
                        @RequestParam(required = false) String recipientName, @RequestParam(required = false) String recipientPhone,
                        @RequestParam(required = false) String provinceCode, @RequestParam(required = false) String wardName,
                        @RequestParam(required = false) String addressDetail, @RequestParam(required = false) String customerNote,
                        HttpSession session, RedirectAttributes redirect) {
        Long userId = customerId(session);
        if (userId == null) return "redirect:/login";
        try { redirect.addFlashAttribute("message", "Đặt hàng thành công. Mã đơn: " + checkout.placeOrder(userId, selectedVariantIds, addressId, recipientName, recipientPhone, provinceCode, wardName, addressDetail, customerNote)); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/cart";
    }

    private Long customerId(HttpSession session) {
        if (!"CUSTOMER".equals(session.getAttribute("role"))) return null;
        Object id = session.getAttribute("userId");
        return id instanceof Number number ? number.longValue() : null;
    }
}
