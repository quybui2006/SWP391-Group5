package controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import service.ShopProductService;
import service.CurrentUserProvider;
import service.ShopOrderService;

@Controller
public class ShopOwnerController {
    private final ShopProductService products;
    private final CurrentUserProvider currentUser;
    private final ShopOrderService orders;

    public ShopOwnerController(ShopProductService products, CurrentUserProvider currentUser, ShopOrderService orders) {
        this.products = products; this.currentUser = currentUser; this.orders = orders;
    }

    @GetMapping("/shopowner/home")
    public String shopOwnerHome(jakarta.servlet.http.HttpServletRequest request, Model model) {
        Long ownerId = currentUser.requireUserId(request);
        model.addAttribute("ownerName", request.getSession(false).getAttribute("fullName"));
        model.addAttribute("shop", products.shopDashboard(ownerId));
        model.addAttribute("stats", products.shopDashboardStats(ownerId));
        model.addAttribute("recentVariants", products.recentShopVariants(ownerId));
        model.addAttribute("orderSummary", orders.summary(ownerId));
        return "shopowner/home";
    }

    // Tan PTH integration: restore the variants screen with rows scoped to the signed-in shop.
    @GetMapping("/shopowner/product-variants")
    public String productVariants(@RequestParam(defaultValue = "") String keyword,
                                  HttpServletRequest request, Model model) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        model.addAttribute("variants", products.searchShopVariants(ownerId, keyword));
        model.addAttribute("variantSummary", products.shopVariantSummary(ownerId));
        model.addAttribute("keyword", keyword.trim());
        return "shopowner/product-variants";
    }

    private Long ownerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) return null;
        Object id = session.getAttribute("userId");
        return id instanceof Number number ? number.longValue() : null;
    }
}
