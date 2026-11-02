package controller.customer;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import service.customer.CustomerAccountService;
import service.customer.CustomerDashboardService;

import java.util.Map;

@Controller
@RequestMapping("/customer/account/dashboard")
public class CustomerPersonalDashboardController {

    private final CustomerDashboardService dashboardService;
    private final CustomerAccountService accountService;

    public CustomerPersonalDashboardController(
            CustomerDashboardService dashboardService,
            CustomerAccountService accountService) {
        this.dashboardService = dashboardService;
        this.accountService = accountService;
    }

    private Long currentUserId(HttpSession session) {
        if (session == null) return null;
        Object id = session.getAttribute("userId");
        return id instanceof Number n ? n.longValue() : null;
    }

    @GetMapping
    public String showDashboard(Model model, HttpSession session) {
        Long userId = currentUserId(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/customer/account/dashboard";
        }

        Map<String, Object> data = dashboardService.getCustomerDashboard(userId);
        model.addAllAttributes(data);

        try {
            model.addAttribute("profile", accountService.profile(userId));
        } catch (Exception ignored) {}

        model.addAttribute("activePage", "dashboard");
        return "customer/dashboard";
    }
}
