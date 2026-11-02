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

        notificationService.ensureSampleNotificationsIfEmpty(userId);
        long unreadCount = notificationService.getUnreadCount(userId);
        List<NotificationDto> notifications = notificationService.getRecentNotifications(userId, 10);

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

        boolean success = notificationService.markAsRead(id, userId);
        long unreadCount = notificationService.getUnreadCount(userId);

        response.put("success", success);
        response.put("unreadCount", unreadCount);
        return ResponseEntity.ok(response);
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

        notificationService.markAllAsRead(userId);
        response.put("success", true);
        response.put("unreadCount", 0);
        return ResponseEntity.ok(response);
    }
}
