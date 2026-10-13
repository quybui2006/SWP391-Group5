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

    boolean existsByShopIdAndSku(Long shopId, String sku);

    /** Chỉ trả biến thể đang bán thuộc sản phẩm được duyệt để tạo khuyến mãi. */
    @Query("SELECT pv FROM ProductVariant pv JOIN FETCH pv.product p " +
            "WHERE pv.shopId = :shopId AND pv.status = 'ACTIVE' " +
            "AND p.approvalStatus = 'APPROVED' " +
            "ORDER BY pv.name ASC")
    List<ProductVariant> findPromotionEligibleVariants(@Param("shopId") Long shopId);

    // Tìm các biến thể sản phẩm đang ACTIVE
    // và thuộc về Product cũng đang ACTIVE
    List<ProductVariant> findByStatusAndProduct_SellingStatusAndProduct_ApprovalStatus(
            String variantStatus,
            String productStatus,
            String approvalStatus
    );

    // Tìm kiếm sản phẩm theo tên (chứa từ khóa) và trạng thái ACTIVE
    List findByNameContainingIgnoreCaseAndStatusAndProduct_SellingStatusAndProduct_ApprovalStatus(
            String keyword,
            String variantStatus,
            String productStatus,
            String approvalStatus
    );

    // Câu Query kết hợp Tìm kiếm (keyword) + Lọc giá (min/max) + Lọc Danh mục
    @Query("SELECT DISTINCT pv FROM ProductVariant pv " +
            "JOIN pv.product p LEFT JOIN p.categories c " +
            "WHERE pv.status = 'ACTIVE' AND p.sellingStatus = 'ACTIVE' AND p.approvalStatus = 'APPROVED' " +
            "AND (:keyword = '' OR LOWER(pv.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:minPrice IS NULL OR pv.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR pv.price <= :maxPrice) " +
            "AND (:filterByCategory = false OR c.id IN :categoryIds)")
    List searchAndFilter(
            @Param("keyword") String keyword,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("filterByCategory") boolean filterByCategory,
            @Param("categoryIds") List categoryIds);
}
