package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import service.customer.NotificationService;

/** Makes the Shop Owner unread count available to the shared navigation. */
@ControllerAdvice(basePackages = "controller.shop")
public class ShopOwnerNotificationModelAdvice {
    private final NotificationService notifications;

    public ShopOwnerNotificationModelAdvice(NotificationService notifications) {
        this.notifications = notifications;
    }

    @ModelAttribute
    public void addUnreadNotificationCount(HttpServletRequest request, Model model) {
        model.addAttribute("shopOwnerUnreadCount", 0L);
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) return;
        Object userId = session.getAttribute("userId");
        if (userId instanceof Number number) {
            model.addAttribute("shopOwnerUnreadCount", notifications.getUnreadCount(number.longValue()));
        }
    }
}
