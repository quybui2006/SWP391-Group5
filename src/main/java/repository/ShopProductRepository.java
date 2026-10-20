package repository;

import entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Truy vấn sản phẩm cho Shop Owner và danh sách duyệt của Operations Manager. */
@Repository
public interface ShopProductRepository extends JpaRepository<Product, Long> {

    /** Tìm sản phẩm thuộc đúng shop của chủ sở hữu. */
    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    List<Product> findByShopId(Long shopId);

    boolean existsByShopIdAndBatchCode(Long shopId, String batchCode);

    @Query("""
    SELECT DISTINCT p.origin
    FROM Product p JOIN p.variants pv JOIN Shop s ON s.id = pv.shopId
    WHERE p.origin IS NOT NULL AND TRIM(p.origin) <> ''
      AND pv.status = 'ACTIVE'
      AND pv.approvalStatus IN ('APPROVED', 'PENDING')
      AND s.approvalStatus = 'APPROVED'
      AND s.operatingStatus = 'OPEN'
    ORDER BY p.origin
    """)
    List<String> findDistinctActiveOrigins();

}