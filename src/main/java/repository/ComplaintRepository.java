package repository;

import entity.Complaint;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Complaint c WHERE c.id = :id")
    Optional<Complaint> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Complaint c WHERE c.id = :id AND c.userId = :userId")
    Optional<Complaint> findLockedByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Complaint c, Shop s WHERE c.shopId = s.id AND c.id = :id AND s.ownerId = :ownerId")
    Optional<Complaint> findLockedByIdAndShopOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);

    long countByOrderIdAndStatusNot(Long orderId, String excludedStatus);

    long countByOrderIdAndStatusIn(Long orderId, Collection<String> statuses);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, String status);

    long countByUserIdAndStatusIn(Long userId, Collection<String> statuses);

    long countByShopId(Long shopId);

    long countByShopIdAndIsShopFault(Long shopId, Integer isShopFault);

    long countByStatus(String status);

    long countByStatusNot(String excludedStatus);

    long countByStatusIn(Collection<String> statuses);

    List<Complaint> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<Complaint> findByUserIdAndStatusInOrderByCreatedAtDescIdDesc(Long userId, Collection<String> statuses);

    List<Complaint> findByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);

    @Query("""
            SELECT c.id FROM Complaint c
            WHERE c.status IN ('PENDING_SHOP', 'PENDING') AND c.slaDueAt <= :now
            ORDER BY c.id ASC
            """)
    List<Long> findOverduePendingShopIds(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Complaint c SET c.escalatedToOps = 1 WHERE c.shopId = :shopId AND c.isShopFault = 1")
    int markEscalatedToOpsByShopId(@Param("shopId") Long shopId);

    @Query("""
            SELECT c FROM Complaint c, Order o, User u, Shop s
            WHERE o.id = c.orderId AND u.id = c.userId AND s.id = c.shopId
              AND (:filterByStatus = false OR c.status IN :statuses)
              AND (:priority = '' OR c.priority = :priority)
              AND (:reasonType = '' OR c.reasonType = :reasonType)
              AND (:keyword = '' OR (
                   LOWER(c.complaintCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR (u.phone IS NOT NULL AND LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))
                OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
              ))
            ORDER BY
              CASE WHEN c.status IN ('PENDING_OPERATIONS', 'PENDING_OPS') THEN 0
                   WHEN c.status = 'PENDING_SHOP' THEN 1
                   WHEN c.status IN ('ESCALATED', 'IN_PROGRESS') THEN 2
                   ELSE 3 END ASC,
              CASE WHEN c.priority = 'HIGH' THEN 0 ELSE 1 END ASC,
              c.slaDueAt ASC,
              c.createdAt DESC
            """)
    List<Complaint> searchCsComplaints(
            @Param("filterByStatus") boolean filterByStatus,
            @Param("statuses") Collection<String> statuses,
            @Param("priority") String priority,
            @Param("reasonType") String reasonType,
            @Param("keyword") String keyword
    );
}
