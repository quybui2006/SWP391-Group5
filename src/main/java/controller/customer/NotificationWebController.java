package controller.customer;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import service.customer.CustomerAccountService;
import service.customer.NotificationService;

@Controller
public class NotificationWebController {

    private final NotificationService notificationService;
    private final CustomerAccountService accountService;

    public NotificationWebController(NotificationService notificationService, CustomerAccountService accountService) {
        this.notificationService = notificationService;
        this.accountService = accountService;
    }

    @GetMapping("/notifications")
    public String showNotificationCenter(Model model, HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        if (!(userIdObj instanceof Number number)) {
            return "redirect:/login?continueTo=/notifications";
        }
        Long userId = number.longValue();

        Object roleObj = session.getAttribute("role");
        if ("OPERATIONS_MANAGER".equals(roleObj)) {
            return "redirect:/operations/notifications";
        }

        notificationService.ensureSampleNotificationsIfEmpty(userId);
        model.addAttribute("notifications", notificationService.getAllNotifications(userId));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(userId));
        model.addAttribute("activePage", "notifications");

        // Try load profile if available
        try {
            model.addAttribute("profile", accountService.profile(userId));
        } catch (Exception ignored) {
        }

        return "customer/notifications";
    }

    @GetMapping("/operations/notifications")
    public String showOperationsNotifications(Model model, HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        if (!(userIdObj instanceof Number number)) {
            return "redirect:/login?continueTo=/operations/notifications";
        }
        Long userId = number.longValue();

        notificationService.ensureSampleNotificationsIfEmpty(userId);
        model.addAttribute("notifications", notificationService.getAllNotifications(userId));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(userId));
        model.addAttribute("activePage", "notifications");

        return "operations/notifications";
    }
}
