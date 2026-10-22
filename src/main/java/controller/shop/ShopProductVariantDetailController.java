package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.customer.CurrentUserProvider;
import service.shop.ShopProductService;

@Controller
public class ShopProductVariantDetailController {
    private final ShopProductService products;
    private final CurrentUserProvider currentUser;

    public ShopProductVariantDetailController(
            ShopProductService products,
            CurrentUserProvider currentUser
    ) {
        this.products = products;
        this.currentUser = currentUser;
    }

    @GetMapping("/shopowner/product-variants/{id}")
    public String detail(
            @PathVariable Long id,
            HttpServletRequest request,
            Model model
    ) {
        model.addAttribute(
                "variant",
                products.getVariantDetail(id, currentUser.requireUserId(request))
        );
        return "shopowner/product-variant-detail";
    }

    @PostMapping("/shopowner/product-variants/{id}/hide")
    public String hide(
            @PathVariable Long id,
            HttpServletRequest request,
            RedirectAttributes redirect
    ) {
        products.hideVariantFromSale(id, currentUser.requireUserId(request));
        redirect.addFlashAttribute(
                "success",
                "Đã ẩn biến thể khỏi gian hàng. Đơn đã xác nhận và tồn kho đang giữ vẫn được giữ nguyên."
        );
        return "redirect:/shopowner/product-variants/" + id;
    }

    @PostMapping("/shopowner/product-variants/{id}/show")
    public String show(
            @PathVariable Long id,
            HttpServletRequest request,
            RedirectAttributes redirect
    ) {
        try {
            products.showVariantForSale(id, currentUser.requireUserId(request));
            redirect.addFlashAttribute(
                    "success",
                    "Đã hiển thị lại biến thể trên gian hàng."
            );
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/shopowner/product-variants/" + id;
    }
}