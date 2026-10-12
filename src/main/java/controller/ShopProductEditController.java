package controller;

import dto.ProductEditView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.CategoryRepository;
import service.CurrentUserProvider;
import service.ProductValidationException;
import service.ShopProductService;

import java.util.Map;

/**
 * Màn hình Shop Owner - Edit Product (Sheet2 No.8).
 */
@Controller
public class ShopProductEditController {

    @Autowired
    private ShopProductService shopProductService;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping("/shop/product/{id}/edit")
    public String editProduct(@PathVariable("id") Long id, HttpServletRequest request, Model model) {
        model.addAttribute("form",
                shopProductService.buildEditForm(id, currentUserProvider.requireUserId(request)));
        model.addAttribute("errors", Map.of());
        return "shopproductedit";
    }

    @PostMapping("/shop/product/{id}/edit")
    public String saveProduct(@PathVariable("id") Long id,
                              @ModelAttribute("form") ProductEditView form,
                              BindingResult binding,
                              HttpServletRequest request,
                              Model model,
                              RedirectAttributes redirect) {
        try {
            shopProductService.update(id, currentUserProvider.requireUserId(request), form);
            redirect.addFlashAttribute("success", "Cập nhật sản phẩm thành công.");
            return "redirect:/shop/product/" + id;
        } catch (ProductValidationException ex) {
            // Trả lại form, dữ liệu người gõ không mất
            model.addAttribute("errors", ex.getFieldErrors());
        }
        return "shopproductedit";
    }
}
