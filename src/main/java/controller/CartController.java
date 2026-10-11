package controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.CartService;

@Controller
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    // Hiển thị giỏ hàng
    @GetMapping("/cart")
    public String viewCart(HttpSession session, Model model) {
        Long userId = customerId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        model.addAttribute("cartItems", cart.items(userId));
        return "cart";
    }

    // Đếm số lượng sản phẩm trong giỏ
    @GetMapping("/cart/count")
    @ResponseBody
    public int count(HttpSession session) {
        Long userId = customerId(session);

        return userId == null ? 0 : cart.itemCount(userId);
    }

    // Thêm sản phẩm bằng AJAX
    @PostMapping("/cart/items")
    @ResponseBody
    public ResponseEntity<?> add(
            @RequestParam Long variantId,
            @RequestParam(defaultValue = "1") int quantity,
            HttpSession session) {

        Long userId = customerId(session);

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            cart.add(userId, variantId, quantity);
            return ResponseEntity.ok(cart.itemCount(userId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    // Thêm sản phẩm từ form cũ
    @PostMapping("/cart/add")
    public String addToCart(
            @RequestParam("variantId") Long variantId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity,
            HttpSession session,
            RedirectAttributes redirect) {

        Long userId = customerId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        try {
            cart.add(userId, variantId, quantity);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/cart";
    }

    // Cập nhật số lượng sản phẩm
    @PostMapping("/cart/items/{id}/quantity")
    public String update(
            @PathVariable Long id,
            @RequestParam int quantity,
            HttpSession session,
            RedirectAttributes redirect) {

        Long userId = customerId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        try {
            cart.update(userId, id, quantity);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/cart";
    }

    // Xóa sản phẩm theo đường dẫn mới
    @PostMapping("/cart/items/{id}/delete")
    public String remove(
            @PathVariable Long id,
            HttpSession session) {

        Long userId = customerId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        cart.remove(userId, id);
        return "redirect:/cart";
    }

    // Giữ tương thích với đường dẫn xóa cũ
    @GetMapping("/cart/remove")
    public String removeLegacy(
            @RequestParam("id") Long id,
            HttpSession session) {

        Long userId = customerId(session);

        if (userId == null) {
            return "redirect:/login";
        }

        cart.remove(userId, id);
        return "redirect:/cart";
    }

    // Lấy ID khách hàng từ session
    private Long customerId(HttpSession session) {
        if (!"CUSTOMER".equals(session.getAttribute("role"))) {
            return null;
        }

        Object id = session.getAttribute("userId");

        return id instanceof Number number
                ? number.longValue()
                : null;
    }
}