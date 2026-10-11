package controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import service.CurrentUserProvider;
import service.ShopOrderService;

@Controller
public class ShopOrderController {
    private final CurrentUserProvider currentUser;
    private final ShopOrderService orders;
    public ShopOrderController(CurrentUserProvider currentUser, ShopOrderService orders) {
        this.currentUser = currentUser; this.orders = orders;
    }
    @GetMapping("/shopowner/orders")
    public String orders(HttpServletRequest request, Model model) {
        Long ownerId = currentUser.requireUserId(request);
        model.addAttribute("orders", orders.list(ownerId));
        model.addAttribute("ownerName", request.getSession(false).getAttribute("fullName"));
        return "shopowner/orders";
    }

    @GetMapping("/shopowner/orders/{id}")
    public String detail(@org.springframework.web.bind.annotation.PathVariable Long id, HttpServletRequest request, Model model) {
        Long owner = currentUser.requireUserId(request);
        model.addAttribute("order", orders.detail(owner, id));
        model.addAttribute("items", orders.items(owner, id));
        var session = request.getSession(false);
        if (session.getAttribute("shopOrderToken") == null) session.setAttribute("shopOrderToken", java.util.UUID.randomUUID().toString());
        model.addAttribute("token", session.getAttribute("shopOrderToken"));
        return "shopowner/order-detail";
    }

    @org.springframework.web.bind.annotation.PostMapping("/shopowner/orders/{id}/advance")
    public String advance(@org.springframework.web.bind.annotation.PathVariable Long id,
                          @org.springframework.web.bind.annotation.RequestParam(required=false) String token,
                          HttpServletRequest request, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        Long owner = currentUser.requireUserId(request);
        if (token == null || !token.equals(request.getSession(false).getAttribute("shopOrderToken")))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        try { orders.advance(owner, id); redirect.addFlashAttribute("message", "Đã cập nhật đơn hàng."); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("message", ex.getMessage()); }
        return "redirect:/shopowner/orders/" + id;
    }
}
