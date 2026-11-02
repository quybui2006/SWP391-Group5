package repository;

import entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findTop15ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    int deleteByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query(value = "DELETE FROM notifications WHERE user_id = :userId AND (event_key LIKE 'WELCOME-%' OR event_key LIKE 'ORDER-DEMO-%' OR event_key LIKE 'PROD-ARRIVE-%' OR event_key LIKE 'SYSTEM-TIP-%')", nativeQuery = true)
    int deleteCustomerSampleNotificationsByUserId(@Param("userId") Long userId);

    Optional<Notification> findByEventKey(String eventKey);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.eventKey IN :eventKeys")
    int deleteByUserAndEventKeys(@Param("userId") Long userId, @Param("eventKeys") List<String> eventKeys);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :notificationId AND n.user.id = :userId")
    int deleteByIdAndUserId(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);
}
