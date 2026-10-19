package controller.shop;

import dto.shop.ProductVariantEditForm;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.customer.CurrentUserProvider;
import service.shop.ProductValidationException;
import service.shop.ShopProductService;

import java.util.Map;

@Controller
public class ShopProductVariantEditController {
    private final ShopProductService products;
    private final CurrentUserProvider currentUser;

    public ShopProductVariantEditController(ShopProductService products, CurrentUserProvider currentUser) {
        this.products = products;
        this.currentUser = currentUser;
    }

    @GetMapping("/shopowner/product-variants/{id}/edit")
    public String form(@PathVariable Long id, HttpServletRequest request, Model model) {
        model.addAttribute("form", products.buildVariantEditForm(id, currentUser.requireUserId(request)));
        model.addAttribute("errors", Map.of());
        return "shopowner/product-variant-edit";
    }

    @PostMapping("/shopowner/product-variants/{id}/edit")
    public String save(@PathVariable Long id, @ModelAttribute("form") ProductVariantEditForm form,
                       HttpServletRequest request, Model model, RedirectAttributes redirect) {
        try {
            products.updateVariant(id, currentUser.requireUserId(request), form);
            redirect.addFlashAttribute("success", "Đã cập nhật biến thể và tồn kho.");
            return "redirect:/shopowner/product-variants";
        } catch (ProductValidationException ex) {
            ProductVariantEditForm savedState = products.buildVariantEditForm(
                    id, currentUser.requireUserId(request));
            form.setUnits(savedState.getUnits());
            form.setProductName(savedState.getProductName());
            form.setProductCanSell(savedState.isProductCanSell());
            if (!savedState.isProductCanSell() && !"DRAFT".equalsIgnoreCase(form.getStatus())) {
                form.setStatus("DRAFT");
            }
            model.addAttribute("errors", ex.getFieldErrors());
            return "shopowner/product-variant-edit";
        }
    }
}
