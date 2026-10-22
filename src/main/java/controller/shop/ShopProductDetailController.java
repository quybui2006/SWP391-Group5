package controller.shop;

import dto.shop.ProductDetailView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.shop.ProductValidationException;
import org.springframework.web.bind.annotation.PathVariable;

import service.customer.CurrentUserProvider;
import service.shop.ShopProductService;

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
        return "shopowner/shopproductdetail";
    }

    @PostMapping("/shop/product/{id}/hide")
    public String hideProduct(@PathVariable("id") Long id, HttpServletRequest request,
                              RedirectAttributes redirect) {
        try {
            shopProductService.hideProductFromSale(id, currentUserProvider.requireUserId(request));
            redirect.addFlashAttribute("success", "Đã ẩn sản phẩm và các biến thể con khỏi khách hàng.");
        } catch (ProductValidationException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/shop/product/" + id;
    }

    @PostMapping("/shop/product/{id}/show")
    public String showProduct(@PathVariable("id") Long id, HttpServletRequest request,
                              RedirectAttributes redirect) {
        try {
            shopProductService.showProductForSale(id, currentUserProvider.requireUserId(request));
            redirect.addFlashAttribute("success", "Đã hiển thị sản phẩm lại cho khách hàng.");
        } catch (ProductValidationException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/shop/product/" + id;
    }

    @PostMapping("/shop/product/{id}/archive")
    public String archiveProduct(@PathVariable("id") Long id, HttpServletRequest request,
                                 RedirectAttributes redirect) {
        try {
            shopProductService.archiveProduct(id, currentUserProvider.requireUserId(request));
            redirect.addFlashAttribute("success", "Đã lưu trữ sản phẩm. Dữ liệu và lịch sử liên quan vẫn được giữ an toàn.");
            return "redirect:/shop/products";
        } catch (ProductValidationException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/shop/product/" + id;
        }
    }

    @PostMapping("/shop/product/{id}/delete")
    public String deleteProduct(@PathVariable("id") Long id, HttpServletRequest request,
                                RedirectAttributes redirect) {
        try {
            shopProductService.deleteProduct(id, currentUserProvider.requireUserId(request));
            redirect.addFlashAttribute("success", "Đã xóa sản phẩm.");
            return "redirect:/shop/products";
        } catch (ProductValidationException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/shop/product/" + id;
        }
    }

}
