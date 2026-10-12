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
        if (userId != null) model.addAttribute("cartItems", cart.items(userId));
        else {
            var quantities = guestCart(session);
            var items = cart.guestItems(quantities);
            items.forEach(item -> item.put("id", item.get("variant_id")));
            model.addAttribute("cartItems", items);
        }
        return "cart";
    }

    // Đếm số lượng sản phẩm trong giỏ
    @GetMapping("/cart/count")
    @ResponseBody
    public int count(HttpSession session) {
        Long userId = customerId(session);

        return userId == null ? guestCart(session).values().stream().mapToInt(Integer::intValue).sum() : cart.itemCount(userId);
    }

    // Thêm sản phẩm bằng AJAX
    @PostMapping("/cart/items")
    @ResponseBody
    public ResponseEntity<?> add(
            @RequestParam Long variantId,
            @RequestParam(defaultValue = "1") int quantity,
            HttpSession session) {

        Long userId = customerId(session);

        try {
            if (userId != null) cart.add(userId, variantId, quantity);
            else {
                cart.validateVariant(variantId, quantity);
                var guest = guestCart(session);
                guest.put(variantId, Math.min(99, guest.getOrDefault(variantId, 0) + quantity));
                session.setAttribute("guestCart", guest);
            }
            return ResponseEntity.ok(userId == null ? guestCart(session).values().stream().mapToInt(Integer::intValue).sum() : cart.itemCount(userId));
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

        try {
            if (userId != null) cart.add(userId, variantId, quantity);
            else {
                cart.validateVariant(variantId, quantity);
                var guest = guestCart(session);
                guest.put(variantId, Math.min(99, guest.getOrDefault(variantId, 0) + quantity));
                session.setAttribute("guestCart", guest);
            }
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

        try {
            if (userId != null) cart.update(userId, id, quantity);
            else {
                cart.validateVariant(id, quantity);
                var guest = guestCart(session);
                if (!guest.containsKey(id)) throw new IllegalArgumentException("Sản phẩm trong giỏ không tồn tại.");
                guest.put(id, quantity);
                session.setAttribute("guestCart", guest);
            }
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

        if (userId != null) cart.remove(userId, id);
        else { var guest = guestCart(session); guest.remove(id); session.setAttribute("guestCart", guest); }
        return "redirect:/cart";
    }

    // Giữ tương thích với đường dẫn xóa cũ
    @GetMapping("/cart/remove")
    public String removeLegacy(
            @RequestParam("id") Long id,
            HttpSession session) {

        Long userId = customerId(session);

        if (userId != null) cart.remove(userId, id);
        else { var guest = guestCart(session); guest.remove(id); session.setAttribute("guestCart", guest); }
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

    @SuppressWarnings("unchecked")
    private java.util.Map<Long, Integer> guestCart(HttpSession session) {
        Object value = session.getAttribute("guestCart");
        return value instanceof java.util.Map<?, ?> map ? (java.util.Map<Long, Integer>) map : new java.util.LinkedHashMap<>();
    }
}
