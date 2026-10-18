package repository;

import dto.operations.PendingShopApprovalDto;
import entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** Schema có UNIQUE (owner_id) nên tối đa một kết quả. */
    Optional<Shop> findByOwnerId(Long ownerId);

    @Query("""
            SELECT new dto.operations.PendingShopApprovalDto(
                s.id, s.name, u.fullName, u.email, s.phone, s.provinceCode,
                s.wardName, s.addressDetail, s.description, s.createdAt
            )
            FROM Shop s, User u
            WHERE u.id = s.ownerId AND s.approvalStatus = 'PENDING'
            ORDER BY s.createdAt ASC
            """)
    List<PendingShopApprovalDto> findPendingApprovals();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET approval_status = 'APPROVED', reviewed_at = NOW(), rejection_reason = NULL WHERE id = :shopId AND approval_status = 'PENDING'", nativeQuery = true)
    int approvePending(@Param("shopId") Long shopId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET approval_status = 'REJECTED', reviewed_at = NOW(), rejection_reason = :reason WHERE id = :shopId AND approval_status = 'PENDING'", nativeQuery = true)
    int rejectPending(@Param("shopId") Long shopId, @Param("reason") String reason);
}
