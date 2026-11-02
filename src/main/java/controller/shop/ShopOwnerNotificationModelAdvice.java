package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import service.customer.NotificationService;

/** Shares the unread count with the Shop Owner sidebar on every shop page. */
@ControllerAdvice(basePackages = "controller.shop")
public class ShopOwnerNotificationModelAdvice {
    private final NotificationService notifications;

    public ShopOwnerNotificationModelAdvice(NotificationService notifications) {
        this.notifications = notifications;
    }

    @ModelAttribute
    public void addUnreadNotificationCount(HttpServletRequest request, Model model) {
        // The sidebar is shared by shop pages, so give its Thymeleaf condition a safe default.
        model.addAttribute("shopOwnerUnreadCount", 0L);
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) return;
        Object userId = session.getAttribute("userId");
        if (userId instanceof Number number) {
            model.addAttribute("shopOwnerUnreadCount", notifications.getUnreadCount(number.longValue()));
        }
    }
}
