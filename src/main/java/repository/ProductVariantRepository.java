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

    /** Một sản phẩm không nên có hai biến thể cùng tên, ví dụ hai biến thể "Túi 1kg". */
    boolean existsByProduct_IdAndNameIgnoreCaseAndIdNot(Long productId, String name, Long id);

    /** Hậu kiểm: biến thể đang bán của sản phẩm ACTIVE có thể được chọn tạo khuyến mãi dù approval_status còn PENDING. */
    @Query("SELECT pv FROM ProductVariant pv JOIN FETCH pv.product p " +
            "WHERE pv.shopId = :shopId AND pv.status = 'ACTIVE' " +
            "AND p.sellingStatus = 'ACTIVE' " +
            "ORDER BY pv.name ASC")
    List<ProductVariant> findPromotionEligibleVariants(@Param("shopId") Long shopId);

    /** Danh mục khách hàng theo hậu kiểm: chỉ xét trạng thái bán của sản phẩm, không xét approval_status. */
    List<ProductVariant> findByStatusAndProduct_SellingStatus(String variantStatus, String productStatus);

    // Câu Query kết hợp Tìm kiếm (keyword) + Lọc giá (min/max) + Lọc Danh mục
    @Query("SELECT DISTINCT pv FROM ProductVariant pv " +
            "JOIN pv.product p LEFT JOIN p.categories c " +

            "WHERE pv.status = 'ACTIVE' AND p.sellingStatus = 'ACTIVE' " +
            "AND (:keyword = '' OR LOWER(pv.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +

            "WHERE pv.status = 'ACTIVE' AND p.sellingStatus = 'ACTIVE' AND p.approvalStatus = 'APPROVED' " +
            "AND (:keyword = '' OR (" +
            "   LOWER(pv.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "   LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "   (p.origin IS NOT NULL AND LOWER(p.origin) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
            "   (p.description IS NOT NULL AND LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
            "   LOWER(pv.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "   LOWER(p.batchCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "   (pv.specification IS NOT NULL AND LOWER(pv.specification) LIKE LOWER(CONCAT('%', :keyword, '%')))" +
            ")) " +
            "AND (:minPrice IS NULL OR pv.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR pv.price <= :maxPrice) " +
            "AND (:filterByCategory = false OR c.id IN :categoryIds) " +
            "AND (:filterByOrigin = false OR p.origin IN :origins)")
    List<ProductVariant> advancedSearchAndFilter(
            @Param("keyword") String keyword,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("filterByCategory") boolean filterByCategory,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("filterByOrigin") boolean filterByOrigin,
            @Param("origins") List<String> origins,
            org.springframework.data.domain.Sort sort);

    // Backward-compatible searchAndFilter method
    @Query("SELECT DISTINCT pv FROM ProductVariant pv " +
            "JOIN pv.product p LEFT JOIN p.categories c " +
            "WHERE pv.status = 'ACTIVE' AND p.sellingStatus = 'ACTIVE' AND p.approvalStatus = 'APPROVED' " +
            "AND (:keyword = '' OR LOWER(pv.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +

            "AND (:minPrice IS NULL OR pv.price >= :minPrice) " +
            "AND (:maxPrice IS NULL OR pv.price <= :maxPrice) " +
            "AND (:filterByCategory = false OR c.id IN :categoryIds)")
    List searchAndFilter(
            @Param("keyword") String keyword,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("filterByCategory") boolean filterByCategory,
            @Param("categoryIds") List categoryIds);

    // Lấy danh sách sản phẩm theo shop, có lọc theo category và keyword
    @Query("SELECT DISTINCT pv FROM ProductVariant pv " +
            "JOIN pv.product p LEFT JOIN p.categories c " +
            "WHERE (pv.shopId = :shopId OR p.shopId = :shopId) " +
            "AND pv.status = 'ACTIVE' AND p.sellingStatus = 'ACTIVE' " +
            "AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:keyword = '' OR LOWER(pv.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<ProductVariant> findByShopAndCategoryAndKeyword(
            @Param("shopId") Long shopId,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword);
}
