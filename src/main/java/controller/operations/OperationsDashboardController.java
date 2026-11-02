package controller.operations;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import service.operations.OperationsDashboardService;

import java.util.Map;

@Controller
public class OperationsDashboardController {

    private final OperationsDashboardService dashboardService;

    public OperationsDashboardController(OperationsDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/operations", "/operations/dashboard"})
    public String showDashboard(Model model) {
        Map<String, Object> data = dashboardService.getDashboardData();
        model.addAllAttributes(data);
        model.addAttribute("activeNav", "dashboard");
        return "operations/dashboard";
    }
}
