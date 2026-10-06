package controller;

import entity.ProductVariant;
import service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class CustomerProductDetailController {

    @Autowired
    private ProductService productService;

    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable("id") Long id, Model model) {
        ProductVariant variant = productService.getProductVariantById(id);

        // Nếu không tìm thấy sản phẩm hoặc sản phẩm đã bị ẩn -> Quay về trang chủ
        if (variant == null) {
            return "redirect:/";
        }

        // Đẩy dữ liệu chi tiết sản phẩm sang giao diện
        model.addAttribute("variant", variant);
        return "customerproductdetail";
    }
}