package repository;

import dto.operations.PendingProductApprovalDto;
import entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

/** Truy vấn sản phẩm cho Shop Owner và danh sách duyệt của Operations Manager. */
@Repository
public interface ShopProductRepository extends JpaRepository<Product, Long> {

    /** Tìm sản phẩm thuộc đúng shop của chủ sở hữu. */
    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    List<Product> findByShopId(Long shopId);

    boolean existsByShopIdAndBatchCode(Long shopId, String batchCode);

    /** Chuyển DRAFT -> PENDING có điều kiện, tránh submit lặp hoặc race condition. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products
               SET approval_status = 'PENDING', selling_status = 'DRAFT',
                   submitted_at = :submittedAt, reviewed_at = NULL, rejection_reason = NULL
             WHERE id = :productId AND shop_id = :shopId AND approval_status = 'DRAFT'
            """, nativeQuery = true)
    int submitDraftForApproval(@Param("productId") Long productId,
                               @Param("shopId") Long shopId,
                               @Param("submittedAt") LocalDateTime submittedAt);

    @Query("""
            SELECT new dto.operations.PendingProductApprovalDto(
                p.id, p.name, p.batchCode, s.name, p.origin,
                p.receivedDate, p.expiryDate, p.submittedAt
            )
            FROM Product p, Shop s
            WHERE s.id = p.shopId AND p.approvalStatus = 'PENDING'
            ORDER BY p.submittedAt ASC
            """)
    List<PendingProductApprovalDto> findPendingApprovals();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE products SET approval_status = 'APPROVED', reviewed_at = NOW(), rejection_reason = NULL WHERE id = :productId AND approval_status = 'PENDING'", nativeQuery = true)
    int approvePending(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE products SET approval_status = 'REJECTED', reviewed_at = NOW(), rejection_reason = :reason WHERE id = :productId AND approval_status = 'PENDING'", nativeQuery = true)
    int rejectPending(@Param("productId") Long productId, @Param("reason") String reason);

    @Query("SELECT DISTINCT p.origin FROM Product p " +
           "WHERE p.origin IS NOT NULL AND TRIM(p.origin) <> '' " +
           "AND p.sellingStatus = 'ACTIVE' AND p.approvalStatus = 'APPROVED' " +
           "ORDER BY p.origin ASC")
    List<String> findDistinctActiveOrigins();
}
