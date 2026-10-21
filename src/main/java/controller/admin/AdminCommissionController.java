package controller.admin;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.admin.AdminCommissionService;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin/commission")
public class AdminCommissionController {

    private final AdminCommissionService commissionService;

    public AdminCommissionController(AdminCommissionService commissionService) {
        this.commissionService = commissionService;
    }

    private boolean checkAdmin(HttpSession session) {
        return session != null && "ADMIN".equals(session.getAttribute("role"));
    }

    @GetMapping
    public String commissionPage(Model model, HttpSession session) {
        if (!checkAdmin(session)) return "redirect:/login";
        model.addAttribute("stats", commissionService.getCommissionStats());
        model.addAttribute("rate", commissionService.getCurrentCommissionRate());
        return "admin/commission";
    }

    @PostMapping("/set")
    public String setCommissionRate(@RequestParam BigDecimal rate,
                                     HttpSession session,
                                     RedirectAttributes redirect) {
        if (!checkAdmin(session)) return "redirect:/login";
        try {
            Long adminId = session != null && session.getAttribute("userId") instanceof Number n ? n.longValue() : null;
            if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("15")) > 0) {
                throw new IllegalArgumentException("Tỷ lệ phí sàn phải từ 0 đến 15%.");
            }
            BigDecimal decimalRate = rate.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
            commissionService.setCommissionRate(decimalRate, adminId);
            redirect.addFlashAttribute("success", "Đã cập nhật tỷ lệ phí sàn thành " + rate + "%.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/commission";
    }
}
