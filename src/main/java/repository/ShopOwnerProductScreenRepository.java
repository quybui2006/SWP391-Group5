package repository;

import dto.ShopOwnerCategoryOption;
import dto.ShopOwnerProductForm;
import dto.ShopOwnerProductView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import dto.ShopOwnerProductListItem;

/**
 * JDBC repository riêng cho hai màn hình Shop Owner Product.
 * Không phụ thuộc entity Product/Variant dùng chung của các module khác.
 */
@Repository
public class ShopOwnerProductScreenRepository {
    private final JdbcTemplate jdbc;

    public ShopOwnerProductScreenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ShopOwnerProductView> findOwnedProduct(Long productId, Long userId) {
        String sql = """
                SELECT p.id, p.name, p.description, p.origin, p.batch_code,
                       p.received_date, p.expiry_date, p.approval_status, p.selling_status,
                       s.name AS shop_name,
                       (SELECT pi.image_url FROM product_images pi
                        WHERE pi.product_id = p.id ORDER BY pi.sort_order LIMIT 1) AS image_url,
                       (SELECT pc.category_id FROM product_categories pc
                        WHERE pc.product_id = p.id ORDER BY pc.category_id LIMIT 1) AS category_id,
                       (SELECT c.name FROM product_categories pc JOIN categories c ON c.id = pc.category_id
                        WHERE pc.product_id = p.id ORDER BY c.name LIMIT 1) AS category_name
                FROM products p
                JOIN shops s ON s.id = p.shop_id
                WHERE p.id = ? AND s.owner_id = ?
                """;
        List<ShopOwnerProductView> result = jdbc.query(sql, (rs, rowNum) -> {
            ShopOwnerProductView view = new ShopOwnerProductView();
            view.setId(rs.getLong("id"));
            view.setName(rs.getString("name"));
            view.setDescription(rs.getString("description"));
            view.setOrigin(rs.getString("origin"));
            view.setBatchCode(rs.getString("batch_code"));
            Date received = rs.getDate("received_date");
            Date expiry = rs.getDate("expiry_date");
            view.setReceivedDate(received == null ? null : received.toLocalDate());
            view.setExpiryDate(expiry == null ? null : expiry.toLocalDate());
            view.setApprovalStatus(rs.getString("approval_status"));
            view.setSellingStatus(rs.getString("selling_status"));
            view.setShopName(rs.getString("shop_name"));
            view.setImageUrl(rs.getString("image_url"));
            long categoryId = rs.getLong("category_id");
            view.setCategoryId(rs.wasNull() ? null : categoryId);
            view.setCategoryName(rs.getString("category_name"));
            return view;
        }, productId, userId);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        ShopOwnerProductView product = result.get(0);
        product.setVariants(findVariants(productId, userId));
        return Optional.of(product);
    }

    public List<ShopOwnerProductListItem> findOwnedProducts(Long userId) {
        String sql = """
                SELECT p.id, p.name, p.approval_status, p.selling_status,
                       COALESCE((SELECT GROUP_CONCAT(c.name ORDER BY c.name SEPARATOR ', ')
                                 FROM product_categories pc JOIN categories c ON c.id = pc.category_id
                                 WHERE pc.product_id = p.id), 'Chưa phân loại') AS category_name,
                       (SELECT pi.image_url FROM product_images pi
                        WHERE pi.product_id = p.id ORDER BY pi.sort_order LIMIT 1) AS image_url,
                       (SELECT COUNT(*) FROM product_variants v WHERE v.product_id = p.id) AS variant_count,
                       COALESCE((SELECT SUM(i.available_quantity) FROM product_variants v
                                 LEFT JOIN variant_inventory i ON i.variant_id = v.id
                                 WHERE v.product_id = p.id), 0) AS available_stock,
                       (SELECT MIN(v.price) FROM product_variants v WHERE v.product_id = p.id) AS from_price
                FROM products p JOIN shops s ON s.id = p.shop_id
                WHERE s.owner_id = ? ORDER BY p.updated_at DESC, p.id DESC
                """;
        return jdbc.query(sql, (rs, rowNum) -> new ShopOwnerProductListItem(
                rs.getLong("id"), rs.getString("name"), rs.getString("category_name"),
                rs.getString("image_url"), rs.getString("approval_status"),
                rs.getString("selling_status"), rs.getInt("variant_count"),
                rs.getInt("available_stock"), rs.getBigDecimal("from_price")
        ), userId);
    }

    public Long findFirstShopOwnerId() {
        List<Long> users = jdbc.query("SELECT owner_id FROM shops ORDER BY id LIMIT 1",
                (rs, rowNum) -> rs.getLong("owner_id"));
        return users.isEmpty() ? null : users.get(0);
    }

    private List<ShopOwnerProductView.VariantRow> findVariants(Long productId, Long userId) {
        String sql = """
                SELECT v.id, v.name, v.sku, v.specification, v.price, v.status,
                       u.name AS unit_name,
                       COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand,
                       COALESCE(i.reserved_quantity, 0) AS reserved_quantity,
                       COALESCE(i.available_quantity, 0) AS available_quantity,
                       CASE WHEN i.id IS NOT NULL AND i.available_quantity <= i.low_stock_threshold_pct
                            THEN 1 ELSE 0 END AS low_stock
                FROM product_variants v
                JOIN products p ON p.id = v.product_id
                JOIN shops s ON s.id = p.shop_id
                JOIN units u ON u.id = v.base_unit_id
                LEFT JOIN variant_inventory i ON i.variant_id = v.id
                WHERE v.product_id = ? AND s.owner_id = ?
                ORDER BY v.price, v.id
                """;
        return jdbc.query(sql, (rs, rowNum) -> new ShopOwnerProductView.VariantRow(
                rs.getLong("id"), rs.getString("name"), rs.getString("sku"),
                rs.getString("specification"), rs.getBigDecimal("price"),
                rs.getString("unit_name"), rs.getString("status"),
                rs.getInt("quantity_on_hand"), rs.getInt("reserved_quantity"),
                rs.getInt("available_quantity"), rs.getBoolean("low_stock")
        ), productId, userId);
    }

    public List<ShopOwnerCategoryOption> findActiveCategories() {
        return jdbc.query("SELECT id, name FROM categories WHERE is_active = 1 ORDER BY name",
                (rs, rowNum) -> new ShopOwnerCategoryOption(rs.getLong("id"), rs.getString("name")));
    }

    public boolean categoryExists(Long categoryId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE id = ? AND is_active = 1", Integer.class, categoryId);
        return count != null && count > 0;
    }

    public void updateOwnedProduct(Long productId, Long userId, ShopOwnerProductForm form) {
        String sql = """
                UPDATE products p
                JOIN shops s ON s.id = p.shop_id
                SET p.name = ?, p.description = ?, p.origin = ?,
                    p.received_date = ?, p.expiry_date = ?,
                    p.approval_status = ?, p.selling_status = ?,
                    p.submitted_at = CASE WHEN ? = 'PENDING' THEN UTC_TIMESTAMP(3) ELSE p.submitted_at END,
                    p.reviewed_at = CASE WHEN ? = 'APPROVED' THEN UTC_TIMESTAMP(3) ELSE p.reviewed_at END
                WHERE p.id = ? AND s.owner_id = ?
                """;
        int affected = jdbc.update(sql,
                form.getName().trim(), emptyToNull(form.getDescription()), emptyToNull(form.getOrigin()),
                form.getReceivedDate(), form.getExpiryDate(),
                form.getApprovalStatus().trim().toUpperCase(), form.getSellingStatus().trim().toUpperCase(),
                form.getApprovalStatus().trim().toUpperCase(), form.getApprovalStatus().trim().toUpperCase(),
                productId, userId);
        if (affected == 0) {
            throw new ShopOwnerProductNotFoundException();
        }
        jdbc.update("DELETE pc FROM product_categories pc JOIN products p ON p.id = pc.product_id " +
                "JOIN shops s ON s.id = p.shop_id WHERE p.id = ? AND s.owner_id = ?", productId, userId);
        jdbc.update("INSERT INTO product_categories(product_id, category_id) VALUES (?, ?)",
                productId, form.getCategoryId());
    }

    private String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public static class ShopOwnerProductNotFoundException extends RuntimeException { }
}
