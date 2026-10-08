package controller;

import dto.ShopOwnerProductForm;
import dto.ShopOwnerProductView;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.ShopOwnerProductScreenService;

import java.util.Map;

/** Hai màn hình Product Detail/Edit của Shop Owner. */
@Controller
public class ShopOwnerProductScreenController {
    private final ShopOwnerProductScreenService service;

    public ShopOwnerProductScreenController(ShopOwnerProductScreenService service) {
        this.service = service;
    }

    @GetMapping("/shopowner/products")
    public String products(HttpSession session, Model model) {
        model.addAttribute("products", service.getProducts(userId(session)));
        return "shopowner/owner-product-list";
    }

    @GetMapping("/shopowner/products/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        try {
            model.addAttribute("product", service.getDetail(id, userId(session)));
            return "shopowner/owner-product-detail";
        } catch (RuntimeException ex) {
            return "redirect:/shopowner/products?missing";
        }
    }

    @GetMapping("/shopowner/products/{id}/edit")
    public String edit(@PathVariable Long id, HttpSession session, Model model) {
        try {
            ShopOwnerProductView product = service.getDetail(id, userId(session));
            model.addAttribute("product", product);
            model.addAttribute("form", ShopOwnerProductForm.from(product));
            model.addAttribute("categories", service.getCategories());
            model.addAttribute("errors", Map.of());
            return "shopowner/owner-product-edit";
        } catch (RuntimeException ex) {
            return "redirect:/shopowner/products?missing";
        }
    }

    @PostMapping("/shopowner/products/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("form") ShopOwnerProductForm form,
                         HttpSession session, Model model, RedirectAttributes redirect) {
        try {
            service.update(id, userId(session), form);
            redirect.addFlashAttribute("success", "Đã cập nhật thông tin sản phẩm.");
            return "redirect:/shopowner/products/" + id;
        } catch (ShopOwnerProductScreenService.ShopOwnerProductValidationException ex) {
            model.addAttribute("product", service.getDetail(id, userId(session)));
            model.addAttribute("categories", service.getCategories());
            model.addAttribute("errors", ex.getErrors());
            return "shopowner/owner-product-edit";
        } catch (RuntimeException ex) {
            return "redirect:/shopowner/products?missing";
        }
    }

    private Long userId(HttpSession session) {
        Object value = session.getAttribute("userId");
        return value instanceof Long id ? id : null;
    }
}
