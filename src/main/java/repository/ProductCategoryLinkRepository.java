package repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Bảng product_categories là bảng nối nhiều-nhiều giữa products và categories.
 * Dùng JdbcTemplate để thao tác trực tiếp với bảng nối, đồng thời giữ tương
 * thích với các luồng hiện có chưa dùng quan hệ JPA hai chiều.
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
     * Thêm nhiều liên kết danh mục cho sản phẩm mới.
     * Phương thức được gọi trong transaction của tầng service.
     */
    public void addCategories(Long productId, List<Long> categoryIds) {
        if (productId == null || categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (Long categoryId : categoryIds.stream().distinct().toList()) {
            if (categoryId != null) {
                jdbc.update("INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)",
                        productId, categoryId);
            }
        }
    }

    /**
     * Thay thế danh mục đang có. Giữ lại để tương thích với màn hình chỉnh sửa
     * hiện đang cho chọn một danh mục.
     */
    public void replaceCategory(Long productId, Long categoryId) {
        jdbc.update("DELETE FROM product_categories WHERE product_id = ?", productId);
        jdbc.update("INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)",
                productId, categoryId);
    }
}
