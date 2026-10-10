package controller;

import entity.CartItem;
import service.CartService; // Nhúng thêm CartService
import repository.CartItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@Controller
public class CartController {

    @Autowired
    private CartItemRepository cartItemRepository;


    @Autowired
    private CartService cartService;

    @GetMapping("/cart")
    public String viewCart(Model model) {

        List<CartItem> cartItems = cartItemRepository.findByCartId(1L);
        model.addAttribute("cartItems", cartItems);
        return "cart";
    }

    @GetMapping("/cart/remove")
    public String removeCartItem(@RequestParam("id") Long id) {
        cartItemRepository.deleteById(id);
        return "redirect:/cart";
    }


    @PostMapping("/cart/add")
    public String addToCart(@RequestParam("variantId") Long variantId,
                            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity) {


        Long cartId = 1L;

        // Gọi Service để xử lý logic: có rồi thì cộng dồn, chưa có thì thêm mới
        cartService.addToCart(cartId, variantId, quantity);

        // Sau khi thêm thành công, load lại trang giỏ hàng
        return "redirect:/cart";
    }
}