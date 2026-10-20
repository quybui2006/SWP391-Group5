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

    /** Tin đang bán thuộc shop: hậu kiểm PENDING và đã duyệt APPROVED đều đủ điều kiện. */
    @Query("SELECT pv FROM ProductVariant pv JOIN FETCH pv.product p JOIN Shop s ON s.id = pv.shopId " +
            "WHERE pv.shopId = :shopId AND s.approvalStatus = 'APPROVED' AND s.operatingStatus = 'OPEN' " +
            "AND pv.status = 'ACTIVE' AND pv.approvalStatus IN ('APPROVED','PENDING') " +
            "ORDER BY pv.name ASC")
    List<ProductVariant> findPromotionEligibleVariants(@Param("shopId") Long shopId);

    /** Catalog bán ngay tin PENDING trong lúc Operations hậu kiểm. */
    @Query("SELECT DISTINCT pv FROM ProductVariant pv JOIN FETCH pv.product p JOIN Shop s ON s.id = pv.shopId " +
            "WHERE s.approvalStatus = 'APPROVED' AND s.operatingStatus = 'OPEN' " +
            "AND pv.status = 'ACTIVE' AND pv.approvalStatus IN ('APPROVED','PENDING')")
    List<ProductVariant> findApprovedActiveForCatalog();

    @Query("SELECT pv FROM ProductVariant pv JOIN FETCH pv.product p JOIN Shop s ON s.id = pv.shopId " +
            "WHERE pv.id = :variantId AND s.approvalStatus = 'APPROVED' AND s.operatingStatus = 'OPEN' " +
            "AND pv.status = 'ACTIVE' AND pv.approvalStatus IN ('APPROVED','PENDING')")
    java.util.Optional<ProductVariant> findSellableById(@Param("variantId") Long variantId);

    // Câu Query kết hợp Tìm kiếm đa trường (Tên biến thể, Tên sản phẩm, Xuất xứ, Mô tả, SKU, Lô hàng) + Lọc giá + Danh mục + Xuất xứ
    @Query("SELECT DISTINCT pv FROM ProductVariant pv " +
            "JOIN pv.product p JOIN Shop s ON s.id = pv.shopId LEFT JOIN p.categories c " +
            "WHERE s.approvalStatus = 'APPROVED' AND s.operatingStatus = 'OPEN' " +
            "AND pv.status = 'ACTIVE' AND pv.approvalStatus IN ('APPROVED','PENDING') " +
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

    @Query("SELECT DISTINCT pv FROM ProductVariant pv " +
            "JOIN pv.product p JOIN Shop s ON s.id = pv.shopId LEFT JOIN p.categories c " +
            "WHERE s.approvalStatus = 'APPROVED' AND s.operatingStatus = 'OPEN' " +
            "AND pv.status = 'ACTIVE' AND pv.approvalStatus IN ('APPROVED','PENDING') " +
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

    /** Lấy biến thể đang bán của một shop, gồm tin đang chờ hậu kiểm. */
    @Query("SELECT DISTINCT pv FROM ProductVariant pv " +
            "JOIN pv.product p JOIN Shop s ON s.id = pv.shopId LEFT JOIN p.categories c " +
            "WHERE (pv.shopId = :shopId OR p.shopId = :shopId) " +
            "AND s.approvalStatus = 'APPROVED' AND s.operatingStatus = 'OPEN' " +
            "AND pv.status = 'ACTIVE' AND pv.approvalStatus IN ('APPROVED','PENDING') " +
            "AND (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:keyword = '' OR LOWER(pv.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<ProductVariant> findByShopAndCategoryAndKeyword(
            @Param("shopId") Long shopId,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword);
}
