package controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import service.ShopProductService;

@Controller
public class ShopOwnerController {
    private final ShopProductService products;

    public ShopOwnerController(ShopProductService products) {
        this.products = products;
    }

    @GetMapping("/shopowner/home")
    public String shopOwnerHome() {
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
