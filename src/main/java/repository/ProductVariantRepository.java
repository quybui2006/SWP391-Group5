package repository;

import entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    // Tìm các biến thể sản phẩm đang ACTIVE
    // và thuộc về Product cũng đang ACTIVE
    List<ProductVariant> findByStatusAndProduct_SellingStatus(
            String variantStatus,
            String productStatus
    );

    // Tìm kiếm sản phẩm theo tên (chứa từ khóa) và trạng thái ACTIVE
    List<ProductVariant> findByNameContainingIgnoreCaseAndStatusAndProduct_SellingStatus(
            String keyword,
            String variantStatus,
            String productStatus
    );

    // Câu Query kết hợp Tìm kiếm (keyword) + Lọc giá (min/max) + Lọc Danh mục
    @Query("SELECT DISTINCT pv FROM ProductVariant pv " +
            "JOIN pv.product p LEFT JOIN p.categories c " +
            "WHERE pv.status = 'ACTIVE' AND p.sellingStatus = 'ACTIVE' " +
            "AND (:keyword = '' OR LOWER(pv.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:minPrice IS NULL OR pv.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR pv.price <= :maxPrice) " +
            "AND (:filterByCategory = false OR c.id IN :categoryIds)")
    List<ProductVariant> searchAndFilter(
            @Param("keyword") String keyword,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("filterByCategory") boolean filterByCategory,
            @Param("categoryIds") List<Long> categoryIds);
}
