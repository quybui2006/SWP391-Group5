package repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Bảng product_categories là bảng nối nhiều-nhiều giữa products và categories.
 *
 * Vì entity.Category chưa khai báo quan hệ ngược (List products) nên không
 * thể thao tác qua JPA. Dùng JdbcTemplate cho gọn, không phải sửa file
 * của người khác.
 */
@Repository
public class ProductCategoryLinkRepository {

    private final JdbcTemplate jdbc;

    public ProductCategoryLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> findCategoryIds(Long productId) {
        return jdbc.query(
                "SELECT category_id FROM product_categories WHERE product_id = ?",
                (rs, i) -> rs.getLong(1), productId);
    }

    public List<String> findCategoryNames(Long productId) {
        return jdbc.query(
                "SELECT c.name FROM categories c "
                        + "JOIN product_categories pc ON pc.category_id = c.id "
                        + "WHERE pc.product_id = ? ORDER BY c.name",
                (rs, i) -> rs.getString(1), productId);
    }

    /**
     * Mỗi sản phẩm giữ đúng một danh mục: xoá hết liên kết cũ rồi gắn mới.
     */
    public void replaceCategory(Long productId, Long categoryId) {
        jdbc.update("DELETE FROM product_categories WHERE product_id = ?", productId);
        jdbc.update("INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)",
                productId, categoryId);
    }
}
