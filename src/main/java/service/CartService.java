package service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class CartService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;

    public CartService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void validateVariant(Long variantId, int quantity) {
        if (variantId == null) throw new IllegalArgumentException("Sản phẩm không hợp lệ.");
        if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("Số lượng phải từ 1 đến 99.");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM product_variants pv JOIN products p ON p.id=pv.product_id WHERE pv.id=? AND pv.status='ACTIVE' AND p.selling_status='ACTIVE' AND p.approval_status='APPROVED'", Integer.class, variantId);
        if (count == null || count == 0) throw new IllegalArgumentException("Sản phẩm hiện không còn mở bán.");
    }

    /**
     * Thêm sản phẩm vào giỏ hàng của khách hàng.
     */
    @Transactional
    public void add(Long userId, Long variantId, int quantity) {
        if (userId == null || variantId == null) {
            throw new IllegalArgumentException(
                    "Thông tin khách hàng hoặc sản phẩm không hợp lệ."
            );
        }

        if (quantity < 1 || quantity > 99) {
            throw new IllegalArgumentException(
                    "Số lượng phải từ 1 đến 99."
            );
        }

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) " +
                        "FROM product_variants pv " +
                        "JOIN products p ON p.id = pv.product_id " +
                        "WHERE pv.id = ? " +
                        "AND pv.status = 'ACTIVE' " +
                        "AND p.selling_status = 'ACTIVE' " +
                        "AND p.approval_status = 'APPROVED'",
                Integer.class,
                variantId
        );

        if (count == null || count == 0) {
            throw new IllegalArgumentException(
                    "Sản phẩm hiện không còn mở bán."
            );
        }

        Long cartId = getOrCreateCartId(userId);

        jdbc.update(
                "INSERT INTO cart_items (cart_id, variant_id, quantity) " +
                        "VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "quantity = LEAST(cart_items.quantity + VALUES(quantity), 99)",
                cartId,
                variantId,
                quantity
        );
    }

    /**
     * Lấy danh sách sản phẩm trong giỏ hàng.
     */
    public List<Map<String, Object>> items(Long userId) {
        if (userId == null) {
            return List.of();
        }

        Long cartId = findCartId(userId);

        if (cartId == null) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        return jdbc.queryForList(
                "SELECT ci.id, " +
                        "ci.variant_id, " +
                        "ci.quantity, " +
                        "pv.name AS variant_name, " +
                        "p.name AS product_name, " +
                        "pv.image_url, pv.price AS original_price, " +
                        "COALESCE((SELECT pp.promotional_price FROM promotional_prices pp " +
                        "WHERE pp.variant_id = pv.id AND pp.is_enabled = 1 AND pp.starts_at <= ? AND pp.ends_at > ? " +
                        "ORDER BY pp.starts_at DESC, pp.id DESC LIMIT 1), pv.price) AS price, " +
                        "vi.available_quantity " +
                        "FROM cart_items ci " +
                        "JOIN product_variants pv ON pv.id = ci.variant_id " +
                        "JOIN products p ON p.id = pv.product_id " +
                        "JOIN variant_inventory vi ON vi.variant_id = pv.id " +
                        "WHERE ci.cart_id = ? " +
                        "ORDER BY ci.id",
                now, now, cartId
        );
    }

    /** Read catalog data for items held in a guest's HTTP session. */
    public List<Map<String, Object>> guestItems(java.util.Map<Long, Integer> quantities) {
        if (quantities == null || quantities.isEmpty()) return List.of();
        String marks = String.join(",", java.util.Collections.nCopies(quantities.size(), "?"));
        List<Object> args = new java.util.ArrayList<>();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        args.add(now); args.add(now); args.addAll(quantities.keySet());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT pv.id AS variant_id,pv.name AS variant_name,p.name AS product_name,pv.image_url,pv.price AS original_price, " +
                        "COALESCE((SELECT pp.promotional_price FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 " +
                        "AND pp.starts_at <= ? AND pp.ends_at > ? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1),pv.price) AS price,vi.available_quantity " +
                        "FROM product_variants pv JOIN products p ON p.id=pv.product_id JOIN variant_inventory vi ON vi.variant_id=pv.id " +
                        "WHERE pv.id IN (" + marks + ") AND pv.status='ACTIVE' AND p.selling_status='ACTIVE' AND p.approval_status='APPROVED'", args.toArray());
        for (Map<String, Object> row : rows) row.put("quantity", quantities.get(((Number) row.get("variant_id")).longValue()));
        return rows;
    }

    /**
     * Tổng số lượng sản phẩm trong giỏ hàng.
     */
    public int itemCount(Long userId) {
        if (userId == null) {
            return 0;
        }

        Integer count = jdbc.queryForObject(
                "SELECT COALESCE(SUM(ci.quantity), 0) " +
                        "FROM carts c " +
                        "LEFT JOIN cart_items ci ON ci.cart_id = c.id " +
                        "WHERE c.user_id = ?",
                Integer.class,
                userId
        );

        return count == null ? 0 : count;
    }

    /**
     * Cập nhật số lượng sản phẩm trong giỏ.
     */
    @Transactional
    public void update(Long userId, Long itemId, int quantity) {
        if (userId == null || itemId == null) {
            throw new IllegalArgumentException(
                    "Thông tin khách hàng hoặc sản phẩm không hợp lệ."
            );
        }

        if (quantity < 1 || quantity > 99) {
            throw new IllegalArgumentException(
                    "Số lượng phải từ 1 đến 99."
            );
        }

        Long cartId = findCartId(userId);

        if (cartId == null) {
            throw new IllegalArgumentException(
                    "Giỏ hàng không tồn tại."
            );
        }

        int updatedRows = jdbc.update(
                "UPDATE cart_items " +
                        "SET quantity = ? " +
                        "WHERE id = ? AND cart_id = ?",
                quantity,
                itemId,
                cartId
        );

        if (updatedRows == 0) {
            throw new IllegalArgumentException(
                    "Sản phẩm trong giỏ không tồn tại."
            );
        }
    }

    /**
     * Xóa một sản phẩm khỏi giỏ hàng của khách hàng.
     */
    @Transactional
    public void remove(Long userId, Long itemId) {
        if (userId == null || itemId == null) {
            throw new IllegalArgumentException(
                    "Thông tin khách hàng hoặc sản phẩm không hợp lệ."
            );
        }

        Long cartId = findCartId(userId);

        if (cartId != null) {
            jdbc.update(
                    "DELETE FROM cart_items " +
                            "WHERE id = ? AND cart_id = ?",
                    itemId,
                    cartId
            );
        }
    }

    /**
     * Lấy ID giỏ hàng; tạo mới nếu khách hàng chưa có.
     */
    private Long getOrCreateCartId(Long userId) {
        Long cartId = findCartId(userId);

        if (cartId != null) {
            return cartId;
        }

        jdbc.update(
                "INSERT INTO carts (user_id) VALUES (?)",
                userId
        );

        cartId = findCartId(userId);

        if (cartId == null) {
            throw new IllegalStateException(
                    "Không thể tạo giỏ hàng cho khách hàng."
            );
        }

        return cartId;
    }

    /**
     * Tìm giỏ hàng theo ID khách hàng.
     */
    private Long findCartId(Long userId) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM carts WHERE user_id = ?",
                (rs, rowNum) -> rs.getLong("id"),
                userId
        );

        return ids.isEmpty() ? null : ids.get(0);
    }
}
