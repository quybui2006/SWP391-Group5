package controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ShopOwnerController {

    @GetMapping("/shopowner/home")
    public String shopOwnerHome() {
        return "shopowner/home";
    }

    @GetMapping("/shopowner/product-variants")
    public String productVariants(Model model) {
        model.addAttribute("pageTitle", "Quản lý Product Variants");
        return "shopowner/product-variants";
    }

    @GetMapping({"/shopowner/product-variants/new", "/shopowner/product-variants/edit/{id}"})
    public String productVariantForm() {
        return "shopowner/product-variant-form";
    }
}
