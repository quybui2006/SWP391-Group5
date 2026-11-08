package controller.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
public class OperationsMembershipConfigController {
    private final JdbcTemplate jdbc;

    public OperationsMembershipConfigController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/operations/membership-config")
    public String show(Model model) {
        List<Map<String, Object>> tiers = jdbc.queryForList(
                "SELECT id, code, name, rank_order, min_spending, cashback_percent FROM membership_tiers ORDER BY rank_order");
        model.addAttribute("tiers", tiers);
        return "operations/membership-config";
    }

    @PostMapping("/operations/membership-config")
    public String save(@RequestParam("tierId") List<Long> tierIds,
                       @RequestParam("minSpending") List<Long> minSpending,
                       @RequestParam("cashbackPercent") List<java.math.BigDecimal> cashbackPercents,
                       RedirectAttributes redirect) {
        if (tierIds.size() != minSpending.size() || tierIds.size() != cashbackPercents.size()) {
            redirect.addFlashAttribute("error", "Dữ liệu cấu hình không hợp lệ.");
            return "redirect:/operations/membership-config";
        }
        for (int i = 0; i < tierIds.size(); i++) {
            if (minSpending.get(i) < 0 || cashbackPercents.get(i).signum() < 0
                    || cashbackPercents.get(i).compareTo(new java.math.BigDecimal("100")) > 0
                    || (i == 0 && minSpending.get(i) != 0)
                    || (i > 0 && minSpending.get(i) <= minSpending.get(i - 1))) {
                redirect.addFlashAttribute("error", "Mức chi tiêu phải tăng dần; tỷ lệ hoàn xu từ 0 đến 100%.");
                return "redirect:/operations/membership-config";
            }
        }
        for (int i = 0; i < tierIds.size(); i++) {
            jdbc.update("UPDATE membership_tiers SET min_spending = ?, cashback_percent = ? WHERE id = ?",
                    minSpending.get(i), cashbackPercents.get(i), tierIds.get(i));
        }
        redirect.addFlashAttribute("success", "Đã lưu cấu hình membership.");
        return "redirect:/operations/membership-config";
    }
}
