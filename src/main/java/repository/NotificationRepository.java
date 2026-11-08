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

    @Query(value = "SELECT n.* FROM notifications n WHERE n.user_id = :userId AND ("
            + "(n.target_type = 'ORDER' AND n.target_id IN (SELECT o.id FROM orders o JOIN shops s ON s.id = o.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('SHOP','PAYMENT_VERIFICATION') AND n.target_id IN (SELECT s.id FROM shops s WHERE s.owner_id = :userId)) "
            + "OR (n.target_type = 'PRODUCT' AND n.target_id IN (SELECT p.id FROM products p JOIN shops s ON s.id = p.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('VARIANT','PRODUCT_VARIANT') AND n.target_id IN (SELECT pv.id FROM product_variants pv JOIN shops s ON s.id = pv.shop_id WHERE s.owner_id = :userId))"
            + ") ORDER BY n.created_at DESC", nativeQuery = true)
    List<Notification> findShopOwnerNotifications(@Param("userId") Long userId);

    @Query(value = "SELECT COUNT(*) FROM notifications n WHERE n.user_id = :userId AND n.is_read = 0 AND ("
            + "(n.target_type = 'ORDER' AND n.target_id IN (SELECT o.id FROM orders o JOIN shops s ON s.id = o.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('SHOP','PAYMENT_VERIFICATION') AND n.target_id IN (SELECT s.id FROM shops s WHERE s.owner_id = :userId)) "
            + "OR (n.target_type = 'PRODUCT' AND n.target_id IN (SELECT p.id FROM products p JOIN shops s ON s.id = p.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('VARIANT','PRODUCT_VARIANT') AND n.target_id IN (SELECT pv.id FROM product_variants pv JOIN shops s ON s.id = pv.shop_id WHERE s.owner_id = :userId))"
            + ")", nativeQuery = true)
    long countUnreadShopOwnerNotifications(@Param("userId") Long userId);

    @Modifying
    @Query(value = "UPDATE notifications n SET n.is_read = 1 WHERE n.user_id = :userId AND n.is_read = 0 AND ("
            + "(n.target_type = 'ORDER' AND n.target_id IN (SELECT o.id FROM orders o JOIN shops s ON s.id = o.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('SHOP','PAYMENT_VERIFICATION') AND n.target_id IN (SELECT s.id FROM shops s WHERE s.owner_id = :userId)) "
            + "OR (n.target_type = 'PRODUCT' AND n.target_id IN (SELECT p.id FROM products p JOIN shops s ON s.id = p.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('VARIANT','PRODUCT_VARIANT') AND n.target_id IN (SELECT pv.id FROM product_variants pv JOIN shops s ON s.id = pv.shop_id WHERE s.owner_id = :userId))"
            + ")", nativeQuery = true)
    int markShopOwnerNotificationsRead(@Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE n FROM notifications n WHERE n.id = :notificationId AND n.user_id = :userId AND ("
            + "(n.target_type = 'ORDER' AND n.target_id IN (SELECT o.id FROM orders o JOIN shops s ON s.id = o.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('SHOP','PAYMENT_VERIFICATION') AND n.target_id IN (SELECT s.id FROM shops s WHERE s.owner_id = :userId)) "
            + "OR (n.target_type = 'PRODUCT' AND n.target_id IN (SELECT p.id FROM products p JOIN shops s ON s.id = p.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('VARIANT','PRODUCT_VARIANT') AND n.target_id IN (SELECT pv.id FROM product_variants pv JOIN shops s ON s.id = pv.shop_id WHERE s.owner_id = :userId))"
            + ")", nativeQuery = true)
    int deleteShopOwnerNotification(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    @Modifying
    @Query(value = "UPDATE notifications n SET n.is_read = 1 WHERE n.id = :notificationId AND n.user_id = :userId AND ("
            + "(n.target_type = 'ORDER' AND n.target_id IN (SELECT o.id FROM orders o JOIN shops s ON s.id = o.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('SHOP','PAYMENT_VERIFICATION') AND n.target_id IN (SELECT s.id FROM shops s WHERE s.owner_id = :userId)) "
            + "OR (n.target_type = 'PRODUCT' AND n.target_id IN (SELECT p.id FROM products p JOIN shops s ON s.id = p.shop_id WHERE s.owner_id = :userId)) "
            + "OR (n.target_type IN ('VARIANT','PRODUCT_VARIANT') AND n.target_id IN (SELECT pv.id FROM product_variants pv JOIN shops s ON s.id = pv.shop_id WHERE s.owner_id = :userId))"
            + ")", nativeQuery = true)
    int markShopOwnerNotificationRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    Optional<Notification> findByEventKey(String eventKey);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :notificationId AND n.user.id = :userId")
    int deleteByIdAndUserId(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.eventKey IN :eventKeys")
    int deleteByUserAndEventKeys(@Param("userId") Long userId, @Param("eventKeys") List<String> eventKeys);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);
}
