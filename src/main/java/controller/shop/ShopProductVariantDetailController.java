package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import service.customer.CurrentUserProvider;
import service.shop.ShopProductService;

@Controller
public class ShopProductVariantDetailController {
    private final ShopProductService products;
    private final CurrentUserProvider currentUser;

    public ShopProductVariantDetailController(ShopProductService products, CurrentUserProvider currentUser) {
        this.products = products;
        this.currentUser = currentUser;
    }

    @GetMapping("/shopowner/product-variants/{id}")
    public String detail(@PathVariable Long id, HttpServletRequest request, Model model) {
        model.addAttribute("variant", products.getVariantDetail(id, currentUser.requireUserId(request)));
        return "shopowner/product-variant-detail";
    }
}
