package controller;

import dto.ProductDetailView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import service.CurrentUserProvider;
import service.ShopProductService;

/**
 * Màn hình Shop Owner - Xem chi tiết sản phẩm (Sheet2 No.7).
 */
@Controller
public class ShopProductDetailController {

    @Autowired
    private ShopProductService shopProductService;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @GetMapping("/shop/product/{id}")
    public String productDetail(@PathVariable("id") Long id, HttpServletRequest request, Model model) {
        model.addAttribute("product",
                shopProductService.getDetail(id, currentUserProvider.requireUserId(request)));
        return "shopproductdetail";
    }
}
