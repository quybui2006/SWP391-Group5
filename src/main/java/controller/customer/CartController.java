package controller.customer;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.customer.CartService;
import java.util.List;

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
        java.util.List<java.util.Map<String, Object>> items;
        if (userId != null) items = cart.items(userId);
        else {
            var quantities = guestCart(session);
            items = cart.guestItems(quantities);
            items.forEach(item -> item.put("id", item.get("variant_id")));
        }
        model.addAttribute("cartItems", items);
        model.addAttribute("cartShops", groupByShop(items));
        return "customer/cart";
    }

    // Gom các sản phẩm trong giỏ theo từng shop.
    // Mỗi nhóm shop sẽ tự sắp xếp item mua được lên trên, item hết hàng/ngừng bán xuống cuối.
    private java.util.List<java.util.Map<String, Object>> groupByShop(java.util.List<java.util.Map<String, Object>> items) {
        java.util.Map<Object, java.util.Map<String, Object>> shops = new java.util.LinkedHashMap<>();
        for (var item : items) {
            Object key = item.get("shop_id");
            var group = shops.computeIfAbsent(key, k -> {
                var g = new java.util.LinkedHashMap<String, Object>();
                g.put("shop_id", item.get("shop_id"));
                g.put("shop_name", item.get("shop_name"));
                g.put("items", new java.util.ArrayList<java.util.Map<String, Object>>());
                return g;
            });
            ((java.util.List<java.util.Map<String, Object>>) group.get("items")).add(item);
        }
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>(shops.values());
        for (var shop : result) {
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> shopItems =
                    (java.util.List<java.util.Map<String, Object>>) shop.get("items");
            shopItems.sort((a, b) -> {
                int sa = sortOrder(a);
                int sb = sortOrder(b);
                if (sa != sb) return Integer.compare(sa, sb);
                int ca = idOrder(a);
                int cb = idOrder(b);
                return Integer.compare(ca, cb);
            });
            boolean allUnavailable = shopItems.stream()
                    .allMatch(i -> sortOrder(i) == 2);
            shop.put("all_unavailable", allUnavailable);
        }
        result.sort((a, b) -> {
            boolean ua = Boolean.TRUE.equals(a.get("all_unavailable"));
            boolean ub = Boolean.TRUE.equals(b.get("all_unavailable"));
            if (ua != ub) return Boolean.compare(ua, ub);
            int sa = idOrder(a);
            int sb = idOrder(b);
            return Integer.compare(sa, sb);
        });
        return result;
    }

    private int sortOrder(java.util.Map<String, Object> item) {
        Object value = item.get("availability_sort");
        return value instanceof Number n ? n.intValue() : 1;
    }

    private int idOrder(java.util.Map<String, Object> item) {
        Object value = item.get("id");
        return value instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
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
            if (userId != null) {
                cart.add(userId, variantId, quantity);
            } else {
                var guest = guestCart(session);
                int totalQuantity = Math.min(99, guest.getOrDefault(variantId, 0) + quantity);
                cart.validateVariant(variantId, totalQuantity);
                guest.put(variantId, totalQuantity);
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
            if (userId != null) {
                cart.add(userId, variantId, quantity);
            } else {
                var guest = guestCart(session);
                int totalQuantity = Math.min(99, guest.getOrDefault(variantId, 0) + quantity);
                cart.validateVariant(variantId, totalQuantity);
                guest.put(variantId, totalQuantity);
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
