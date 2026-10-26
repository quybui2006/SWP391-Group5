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

/**
 * ==============================================================================================
 * REPOSITORY: ShopRepository
 * ==============================================================================================
 * Mục đích: Tầng giao tiếp trực tiếp với cơ sở dữ liệu MySQL (bảng `shops`).
 * 
 * Các nhóm truy vấn chính trong luồng Duyệt Shop:
 * 1. Truy vấn DTO chiếu (Projection Queries): Kết hợp bảng `shops` và `users` để hiển thị danh sách duyệt.
 * 2. Đếm số lượng theo trạng thái: Phục vụ các thẻ Badge thống kê (PENDING, APPROVED, REJECTED).
 * 3. Cập nhật trạng thái nguyên tử (@Modifying Native Query):
 *    - approvePending: Chỉ cập nhật khi `approval_status = 'PENDING'` để chống xung đột race condition.
 *    - rejectPending: Bắt buộc lưu kèm lý do từ chối.
 *    - approvePaymentInfo: Duyệt thanh toán và kích hoạt operating_status = 'OPEN'.
 * ==============================================================================================
 */
@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** Schema có ràng buộc UNIQUE (owner_id) nên mỗi User chỉ sở hữu tối đa một Shop. */
    Optional<Shop> findByOwnerId(Long ownerId);

    /**
     * Lấy danh sách shop đang chờ duyệt (PENDING) sắp xếp theo thời gian nộp cũ nhất trước.
     */
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

    // Các hàm đếm số lượng theo trạng thái phục vụ hiển thị số liệu thống kê
    long countByApprovalStatus(String approvalStatus);

    long countByOperatingStatus(String operatingStatus);

    long countBySellerApprovalStatus(String sellerApprovalStatus);

    long countByPaymentApprovalStatus(String paymentApprovalStatus);

    /**
     * Lấy toàn bộ danh sách shop kèm thông tin chủ shop (Họ tên, email, sđt) và thông tin tài khoản ngân hàng.
     */
    @Query("""
            SELECT new dto.operations.ShopApprovalItemDto(
                s.id, s.name, s.description, u.fullName, u.email, s.phone,
                s.provinceCode, s.wardName, s.addressDetail, s.approvalStatus,
                s.operatingStatus, s.createdAt, s.reviewedAt, s.rejectionReason,
                s.paymentApprovalStatus, s.sellerApprovalStatus,
                s.bankName, s.bankAccountNumber, s.bankAccountName, s.idCardName
            )
            FROM Shop s, User u
            WHERE u.id = s.ownerId
            ORDER BY s.createdAt DESC
            """)
    List<dto.operations.ShopApprovalItemDto> findAllWithOwners();

    /**
     * Lấy danh sách shop theo trạng thái phê duyệt mở Shop (PENDING / APPROVED / REJECTED).
     */
    @Query("""
            SELECT new dto.operations.ShopApprovalItemDto(
                s.id, s.name, s.description, u.fullName, u.email, s.phone,
                s.provinceCode, s.wardName, s.addressDetail, s.approvalStatus,
                s.operatingStatus, s.createdAt, s.reviewedAt, s.rejectionReason,
                s.paymentApprovalStatus, s.sellerApprovalStatus
            )
            FROM Shop s, User u
            WHERE u.id = s.ownerId AND s.approvalStatus = :status
            ORDER BY s.createdAt DESC
            """)
    List<dto.operations.ShopApprovalItemDto> findByApprovalStatusWithOwners(@Param("status") String status);

    /**
     * Lấy danh sách shop theo trạng thái định danh người bán.
     */
    @Query("""
            SELECT new dto.operations.ShopApprovalItemDto(
                s.id, s.name, s.description, u.fullName, u.email, s.phone,
                s.provinceCode, s.wardName, s.addressDetail, s.approvalStatus,
                s.operatingStatus, s.createdAt, s.reviewedAt, s.rejectionReason,
                s.paymentApprovalStatus, s.sellerApprovalStatus
            )
            FROM Shop s, User u
            WHERE u.id = s.ownerId AND s.sellerApprovalStatus = :status
            ORDER BY s.createdAt DESC
            """)
    List<dto.operations.ShopApprovalItemDto> findBySellerApprovalStatusWithOwners(@Param("status") String status);

    /**
     * Lấy danh sách shop theo trạng thái đối soát tài khoản thanh toán.
     */
    @Query("""
            SELECT new dto.operations.ShopApprovalItemDto(
                s.id, s.name, s.description, u.fullName, u.email, s.phone,
                s.provinceCode, s.wardName, s.addressDetail, s.approvalStatus,
                s.operatingStatus, s.createdAt, s.reviewedAt, s.rejectionReason,
                s.paymentApprovalStatus, s.sellerApprovalStatus,
                s.bankName, s.bankAccountNumber, s.bankAccountName, s.idCardName
            )
            FROM Shop s, User u
            WHERE u.id = s.ownerId AND s.paymentApprovalStatus = :status
            ORDER BY s.createdAt DESC
            """)
    List<dto.operations.ShopApprovalItemDto> findByPaymentApprovalStatusWithOwners(@Param("status") String status);

    /**
     * Phê duyệt định danh người bán (CCCD, thuế).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET seller_approval_status = 'APPROVED', seller_reviewed_at = NOW(), seller_rejection_reason = NULL WHERE id = :shopId", nativeQuery = true)
    int approveSellerAccount(@Param("shopId") Long shopId);

    /**
     * Từ chối định danh người bán kèm lý do.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET seller_approval_status = 'REJECTED', seller_reviewed_at = NOW(), seller_rejection_reason = :reason WHERE id = :shopId", nativeQuery = true)
    int rejectSellerAccount(@Param("shopId") Long shopId, @Param("reason") String reason);

    /**
     * PHÊ DUYỆT HỒ SƠ MỞ SHOP:
     * - Cập nhật approval_status = 'APPROVED', operating_status = 'CLOSED', ghi nhận reviewed_at.
     * - Điều kiện chặn xung đột: Chỉ cập nhật khi `approval_status = 'PENDING'`.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET approval_status = 'APPROVED', operating_status = 'CLOSED', reviewed_at = NOW(), rejection_reason = NULL WHERE id = :shopId AND approval_status = 'PENDING'", nativeQuery = true)
    int approvePending(@Param("shopId") Long shopId);

    /**
     * TỪ CHỐI HỒ SƠ MỞ SHOP:
     * - Cập nhật approval_status = 'REJECTED', operating_status = 'CLOSED', ghi nhận lý do rejection_reason.
     * - Điều kiện chặn xung đột: Chỉ cập nhật khi `approval_status = 'PENDING'`.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET approval_status = 'REJECTED', operating_status = 'CLOSED', reviewed_at = NOW(), rejection_reason = :reason WHERE id = :shopId AND approval_status = 'PENDING'", nativeQuery = true)
    int rejectPending(@Param("shopId") Long shopId, @Param("reason") String reason);

    /**
     * Cập nhật trạng thái vận hành của shop (OPEN, CLOSED, SUSPENDED) và lý do đình chỉ (nếu có).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET operating_status = :status, suspension_reason = :reason, updated_at = NOW() WHERE id = :shopId", nativeQuery = true)
    int updateOperatingStatus(@Param("shopId") Long shopId, @Param("status") String status, @Param("reason") String reason);

    /**
     * PHÊ DUYỆT TÀI KHOẢN NGÂN HÀNG & MỞ BÁN CÔNG KHAI:
     * - Đổi payment_approval_status = 'APPROVED'
     * - Đổi operating_status = 'OPEN' để gian hàng chính thức được mua bán trên sàn.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET payment_approval_status = 'APPROVED', operating_status = 'OPEN', payment_reviewed_at = NOW(), bank_rejection_reason = NULL WHERE id = :shopId", nativeQuery = true)
    int approvePaymentInfo(@Param("shopId") Long shopId);

    /**
     * TỪ CHỐI TÀI KHOẢN NGÂN HÀNG:
     * - Đổi payment_approval_status = 'REJECTED' và giữ operating_status = 'CLOSED'.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET payment_approval_status = 'REJECTED', operating_status = 'CLOSED', payment_reviewed_at = NOW(), bank_rejection_reason = :reason WHERE id = :shopId", nativeQuery = true)
    int rejectPaymentInfo(@Param("shopId") Long shopId, @Param("reason") String reason);

    /**
     * Người bán nộp thông tin tài khoản ngân hàng đối soát.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE shops SET bank_name = :bankName, bank_account_number = :accountNumber, bank_account_name = :accountName, bank_branch = :branch, payment_approval_status = 'PENDING', bank_rejection_reason = NULL WHERE id = :shopId", nativeQuery = true)
    int submitPaymentInfo(@Param("shopId") Long shopId,
                          @Param("bankName") String bankName,
                          @Param("accountNumber") String accountNumber,
                          @Param("accountName") String accountName,
                          @Param("branch") String branch);
}
