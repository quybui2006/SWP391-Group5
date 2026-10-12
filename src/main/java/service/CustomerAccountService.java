package service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class CustomerAccountService {
    private final JdbcTemplate jdbc;
    public CustomerAccountService(JdbcTemplate jdbc) { this.jdbc = jdbc; ensureFavoritesTable(); }

    private void ensureFavoritesTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS customer_favorites (user_id BIGINT NOT NULL, variant_id BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY(user_id, variant_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(variant_id) REFERENCES product_variants(id))");
    }

    public Map<String,Object> profile(Long userId) {
        return jdbc.queryForMap("SELECT id,full_name,email,phone,status FROM users WHERE id=? AND status='ACTIVE'", userId);
    }
    public List<Map<String,Object>> orders(Long userId) {
        return jdbc.queryForList("""
            SELECT o.id,o.order_code,o.total_amount,o.status,o.payment_status,o.delivery_status,o.created_at,
                   s.name AS shop_name
            FROM orders o JOIN checkouts c ON c.id=o.checkout_id
            JOIN shops s ON s.id=o.shop_id
            WHERE c.user_id=? ORDER BY o.created_at DESC,o.id DESC
            """, userId);
    }
    public List<Map<String,Object>> favorites(Long userId) {
        return jdbc.queryForList("""
            SELECT pv.id AS variant_id,pv.name AS variant_name,pv.sku,pv.price,pv.image_url,
                   p.id AS product_id,p.name AS product_name,u.name AS unit_name
            FROM customer_favorites f JOIN product_variants pv ON pv.id=f.variant_id
            JOIN products p ON p.id=pv.product_id JOIN units u ON u.id=pv.base_unit_id
            WHERE f.user_id=? ORDER BY f.created_at DESC
            """, userId);
    }
    @Transactional
    public void updateProfile(Long userId, String fullName, String phone) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 150) throw new IllegalArgumentException("Họ tên không hợp lệ.");
        if (phone != null && phone.trim().length() > 20) throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
        jdbc.update("UPDATE users SET full_name=?, phone=? WHERE id=? AND status='ACTIVE'", fullName.trim(), phone == null || phone.isBlank() ? null : phone.trim(), userId);
    }
    @Transactional
    public void toggleFavorite(Long userId, Long variantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM customer_favorites WHERE user_id=? AND variant_id=?", Integer.class, userId, variantId);
        if (count != null && count > 0) jdbc.update("DELETE FROM customer_favorites WHERE user_id=? AND variant_id=?", userId, variantId);
        else jdbc.update("INSERT INTO customer_favorites(user_id,variant_id) SELECT ?,id FROM product_variants WHERE id=? AND status='ACTIVE'", userId, variantId);
    }
    public boolean isFavorite(Long userId, Long variantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM customer_favorites WHERE user_id=? AND variant_id=?", Integer.class, userId, variantId) > 0;
    }
}
