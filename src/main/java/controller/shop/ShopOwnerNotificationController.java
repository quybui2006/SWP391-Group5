package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import service.customer.CurrentUserProvider;
import service.customer.NotificationService;

@Controller
public class ShopOwnerNotificationController {
    private final CurrentUserProvider currentUser;
    private final NotificationService notifications;

    public ShopOwnerNotificationController(CurrentUserProvider currentUser, NotificationService notifications) {
        this.currentUser = currentUser;
        this.notifications = notifications;
    }

    @GetMapping("/shopowner/notifications")
    public String list(HttpServletRequest request, Model model) {
        model.addAttribute("notifications", notifications.getAllNotifications(currentUser.requireUserId(request)));
        return "shopowner/notifications";
    }
}
