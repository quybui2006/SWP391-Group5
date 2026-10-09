package service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class CartService {
    private final JdbcTemplate jdbc;
    public CartService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void add(Long userId, Long variantId, int quantity) {
        if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("Số lượng phải từ 1 đến 99.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM product_variants pv JOIN products p ON p.id=pv.product_id WHERE pv.id=? AND pv.status='ACTIVE' AND p.selling_status='ACTIVE'", Integer.class, variantId) == 0)
            throw new IllegalArgumentException("Sản phẩm hiện không còn mở bán.");
        jdbc.update("INSERT INTO cart_items(cart_id,variant_id,quantity) VALUES(?,?,?) ON DUPLICATE KEY UPDATE quantity=LEAST(quantity+VALUES(quantity),99)", cartId(userId), variantId, quantity);
    }

    public List<Map<String, Object>> items(Long userId) {
        Long id = findCartId(userId);
        if (id == null) return List.of();
        return jdbc.queryForList("SELECT ci.id,ci.variant_id,ci.quantity,pv.name AS variant_name,p.name AS product_name,pv.image_url,pv.price,vi.available_quantity FROM cart_items ci JOIN product_variants pv ON pv.id=ci.variant_id JOIN products p ON p.id=pv.product_id JOIN variant_inventory vi ON vi.variant_id=pv.id WHERE ci.cart_id=? ORDER BY ci.id", id);
    }

    public int itemCount(Long userId) {
        Integer count = jdbc.queryForObject("SELECT COALESCE(SUM(ci.quantity),0) FROM carts c LEFT JOIN cart_items ci ON ci.cart_id=c.id WHERE c.user_id=?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    @Transactional
    public void update(Long userId, Long itemId, int quantity) {
        if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("Số lượng phải từ 1 đến 99.");
        Long id = findCartId(userId);
        if (id == null || jdbc.update("UPDATE cart_items SET quantity=? WHERE id=? AND cart_id=?", quantity, itemId, id) == 0)
            throw new IllegalArgumentException("Sản phẩm trong giỏ không tồn tại.");
    }

    @Transactional
    public void remove(Long userId, Long itemId) {
        Long id = findCartId(userId);
        if (id != null) jdbc.update("DELETE FROM cart_items WHERE id=? AND cart_id=?", itemId, id);
    }

    private Long cartId(Long userId) {
        Long id = findCartId(userId);
        if (id != null) return id;
        jdbc.update("INSERT INTO carts(user_id) VALUES(?)", userId);
        return findCartId(userId);
    }

    private Long findCartId(Long userId) {
        List<Long> ids = jdbc.query("SELECT id FROM carts WHERE user_id=?", (rs, row) -> rs.getLong(1), userId);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
