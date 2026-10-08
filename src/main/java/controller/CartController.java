package controller;

import entity.CartItem;
import org.springframework.web.bind.annotation.RequestParam;
import repository.CartItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@Controller
public class CartController {

    @Autowired
    private CartItemRepository cartItemRepository;

    @GetMapping("/cart")
    public String viewCart(Model model) {
        // Lấy giỏ hàng của cart_id = 1 (đã được tạo mẫu trong database SQL)
        List<CartItem> cartItems = cartItemRepository.findByCartId(1L);
        model.addAttribute("cartItems", cartItems);
        return "cart";
    }
    @GetMapping("/cart/remove")
    public String removeCartItem(@RequestParam("id") Long id) {

        cartItemRepository.deleteById(id);

        return "redirect:/cart";
    }
}