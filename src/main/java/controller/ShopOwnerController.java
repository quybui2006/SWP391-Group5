package controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ShopOwnerController {

    @GetMapping("/shopowner/home")
    public String shopOwnerHome() {
        return "shopowner/home";
    }

    // Tan PTH: the former variants screens were static mockups with no persistence routes.
    // Product and its first variant are created together; send this entry point to the DB-backed manager.
    @GetMapping({"/shopowner/product-variants", "/shopowner/product-variants/new", "/shopowner/product-variants/edit/{id}"})
    public String productVariants() {
        return "redirect:/shop/products";
    }
}
