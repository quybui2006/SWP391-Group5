package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import service.customer.CurrentUserProvider;
import service.customer.NotificationService;
import service.shop.InventoryService;

import java.util.Locale;

@Controller
public class ShopOwnerNotificationController {
    private final CurrentUserProvider currentUser;
    private final NotificationService notifications;
    private final InventoryService inventory;

    public ShopOwnerNotificationController(
            CurrentUserProvider currentUser,
            NotificationService notifications,
            InventoryService inventory) {
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.inventory = inventory;
    }

    @GetMapping("/shopowner/notifications")
    public String list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "all") String status,
            Model model) {
        Long userId = currentUser.requireUserId(request);
        var allNotifications = notifications.getShopOwnerNotifications(userId);

        allNotifications.stream()
                .filter(item -> "ORDER".equalsIgnoreCase(item.getTargetType())
                        && item.getTargetId() != null)
                .forEach(item ->
                        item.setTargetUrl("/shopowner/orders/" + item.getTargetId()));

        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedStatus = switch (
                status == null ? "all" : status.toLowerCase(Locale.ROOT)) {
            case "unread" -> "unread";
            case "read" -> "read";
            default -> "all";
        };

        var filteredNotifications = allNotifications.stream()
                .filter(item -> normalizedKeyword.isEmpty()
                        || (item.getTitle() != null
                        && item.getTitle().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                        || (item.getContent() != null
                        && item.getContent().toLowerCase(Locale.ROOT).contains(normalizedKeyword)))
                .filter(item -> "all".equals(normalizedStatus)
                        || ("read".equals(normalizedStatus) == item.isRead()))
                .toList();

        model.addAttribute("notifications", filteredNotifications);
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("selectedStatus", normalizedStatus);
        model.addAttribute("unreadCount",
                notifications.getShopOwnerUnreadCount(userId));
        model.addAttribute("lowStockAlerts",
                inventory.getLowStockAlerts(userId));

        return "shopowner/notifications";
    }
}