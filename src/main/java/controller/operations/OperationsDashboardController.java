package controller.operations;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import service.complaint.ComplaintService;
import service.operations.OperationsDashboardService;

import java.util.Map;

@Controller
public class OperationsDashboardController {

    private final OperationsDashboardService dashboardService;
    private final ComplaintService complaintService;

    public OperationsDashboardController(
            OperationsDashboardService dashboardService,
            ComplaintService complaintService) {
        this.dashboardService = dashboardService;
        this.complaintService = complaintService;
    }

    @GetMapping({"/operations", "/operations/dashboard"})
    public String showDashboard(Model model) {
        Map<String, Object> data = dashboardService.getDashboardData();
        model.addAllAttributes(data);

        Map<String, Object> complaintReports = complaintService.getAnalyticsAndReports();
        if (complaintReports != null) {
            model.addAllAttributes(complaintReports);
        }

        Map<String, Long> complaintStatusCounts = complaintService.getComplaintStatusCounts();
        if (complaintStatusCounts != null) {
            model.addAllAttributes(complaintStatusCounts);
        }

        model.addAttribute("activeNav", "dashboard");
        return "operations/dashboard";
    }
}
