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

    long countByApprovalStatus(String approvalStatus);

    long countByOperatingStatus(String operatingStatus);

    @Query("""
            SELECT new dto.operations.ShopApprovalItemDto(
                s.id, s.name, s.description, u.fullName, u.email, s.phone,
                s.provinceCode, s.wardName, s.addressDetail, s.approvalStatus,
                s.operatingStatus, s.createdAt, s.reviewedAt, s.rejectionReason,
                s.paymentApprovalStatus
            )
            FROM Shop s, User u
            WHERE u.id = s.ownerId
            ORDER BY s.createdAt DESC
            """)
    List<dto.operations.ShopApprovalItemDto> findAllWithOwners();

    @Query("""
            SELECT new dto.operations.ShopApprovalItemDto(
                s.id, s.name, s.description, u.fullName, u.email, s.phone,
                s.provinceCode, s.wardName, s.addressDetail, s.approvalStatus,
                s.operatingStatus, s.createdAt, s.reviewedAt, s.rejectionReason,
                s.paymentApprovalStatus
            )
            FROM Shop s, User u
            WHERE u.id = s.ownerId AND s.approvalStatus = :status
            ORDER BY s.createdAt DESC
            """)
    List<dto.operations.ShopApprovalItemDto> findByApprovalStatusWithOwners(@Param("status") String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET approval_status = 'APPROVED', operating_status = 'CLOSED', reviewed_at = NOW(), rejection_reason = NULL WHERE id = :shopId AND approval_status = 'PENDING'", nativeQuery = true)
    int approvePending(@Param("shopId") Long shopId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET approval_status = 'REJECTED', operating_status = 'CLOSED', reviewed_at = NOW(), rejection_reason = :reason WHERE id = :shopId AND approval_status = 'PENDING'", nativeQuery = true)
    int rejectPending(@Param("shopId") Long shopId, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET operating_status = :status, suspension_reason = :reason, updated_at = NOW() WHERE id = :shopId", nativeQuery = true)
    int updateOperatingStatus(@Param("shopId") Long shopId, @Param("status") String status, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET payment_approval_status = 'APPROVED', operating_status = 'OPEN', payment_reviewed_at = NOW(), bank_rejection_reason = NULL WHERE id = :shopId", nativeQuery = true)
    int approvePaymentInfo(@Param("shopId") Long shopId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET payment_approval_status = 'REJECTED', operating_status = 'CLOSED', payment_reviewed_at = NOW(), bank_rejection_reason = :reason WHERE id = :shopId", nativeQuery = true)
    int rejectPaymentInfo(@Param("shopId") Long shopId, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET bank_name = :bankName, bank_account_number = :accountNumber, bank_account_name = :accountName, bank_branch = :branch, payment_approval_status = 'PENDING', bank_rejection_reason = NULL WHERE id = :shopId", nativeQuery = true)
    int submitPaymentInfo(@Param("shopId") Long shopId,
                          @Param("bankName") String bankName,
                          @Param("accountNumber") String accountNumber,
                          @Param("accountName") String accountName,
                          @Param("branch") String branch);
}
