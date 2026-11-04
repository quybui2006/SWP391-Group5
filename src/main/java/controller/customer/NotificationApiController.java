package controller.customer;

import dto.customer.NotificationDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import service.customer.NotificationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationService notificationService;

    public NotificationApiController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private Long getUserIdFromSession(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        Map<String, Object> response = new HashMap<>();

        if (userId == null) {
            response.put("loggedIn", false);
            response.put("unreadCount", 0);
            response.put("notifications", List.of());
            return ResponseEntity.ok(response);
        }

        boolean shopOwner = "SHOP_OWNER".equals(session.getAttribute("role"));
        if (!shopOwner) notificationService.ensureSampleNotificationsIfEmpty(userId);
        long unreadCount = shopOwner ? notificationService.getShopOwnerUnreadCount(userId) : notificationService.getUnreadCount(userId);
        List<NotificationDto> notifications = shopOwner
                ? notificationService.getRecentShopOwnerNotifications(userId, 10)
                : notificationService.getRecentNotifications(userId, 10);
        if (shopOwner) {
            notifications.forEach(notification -> {
                if ("ORDER".equalsIgnoreCase(notification.getTargetType()) && notification.getTargetId() != null) {
                    notification.setTargetUrl("/shopowner/orders/" + notification.getTargetId());
                } else if ("PRODUCT".equalsIgnoreCase(notification.getTargetType()) && notification.getTargetId() != null) {
                    notification.setTargetUrl("/shop/product/" + notification.getTargetId());
                } else if (("VARIANT".equalsIgnoreCase(notification.getTargetType())
                        || "PRODUCT_VARIANT".equalsIgnoreCase(notification.getTargetType())) && notification.getTargetId() != null) {
                    notification.setTargetUrl("/shopowner/product-variants/" + notification.getTargetId());
                } else if ("SHOP".equalsIgnoreCase(notification.getTargetType())
                        || "PAYMENT_VERIFICATION".equalsIgnoreCase(notification.getTargetType())) {
                    notification.setTargetUrl("/shopowner/payment-verification");
                }
            });
        }

        response.put("loggedIn", true);
        response.put("unreadCount", unreadCount);
        response.put("notifications", notifications);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long id, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        Map<String, Object> response = new HashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(401).body(response);
        }

        boolean shopOwner = "SHOP_OWNER".equals(session.getAttribute("role"));
        boolean success = shopOwner ? notificationService.markShopOwnerNotificationRead(id, userId)
                : notificationService.markAsRead(id, userId);
        long unreadCount = shopOwner ? notificationService.getShopOwnerUnreadCount(userId) : notificationService.getUnreadCount(userId);

        response.put("success", success);
        response.put("unreadCount", unreadCount);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteNotification(@PathVariable Long id, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("success", false, "unreadCount", 0));
        boolean shopOwner = "SHOP_OWNER".equals(session.getAttribute("role"));
        boolean deleted = shopOwner ? notificationService.deleteShopOwnerNotification(id, userId)
                : notificationService.deleteNotification(id, userId);
        long unreadCount = shopOwner ? notificationService.getShopOwnerUnreadCount(userId) : notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("success", deleted, "unreadCount", unreadCount));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        Map<String, Object> response = new HashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "Chưa đăng nhập");
            return ResponseEntity.status(401).body(response);
        }

        boolean shopOwner = "SHOP_OWNER".equals(session.getAttribute("role"));
        if (shopOwner) notificationService.markAllShopOwnerNotificationsRead(userId);
        else notificationService.markAllAsRead(userId);
        response.put("success", true);
        response.put("unreadCount", 0);
        return ResponseEntity.ok(response);
    }
}
