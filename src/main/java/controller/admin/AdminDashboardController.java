package controller.admin;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import service.admin.AdminDashboardService;

@Controller
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    private boolean checkAdmin(HttpSession session) {
        return session != null && "ADMIN".equals(session.getAttribute("role"));
    }

    @GetMapping
    public String dashboard(Model model, HttpSession session) {
        if (!checkAdmin(session)) return "redirect:/login";
        model.addAttribute("stats", dashboardService.getOverviewStats());
        model.addAttribute("recent", dashboardService.getRecentOrdersSummary());
        return "admin/dashboard";
    }
}
