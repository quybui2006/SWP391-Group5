package controller.admin;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import service.admin.AdminShopPaymentService;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/shop-payouts")
public class AdminShopPaymentController {

    private final AdminShopPaymentService payments;

    public AdminShopPaymentController(AdminShopPaymentService payments) {
        this.payments = payments;
    }

    private boolean checkAdmin(HttpSession session) {
        return session != null && "ADMIN".equals(session.getAttribute("role"));
    }

    @GetMapping
    public String payouts(@RequestParam(required = false) String month,
                           Model model, HttpSession session) {
        if (!checkAdmin(session)) return "redirect:/login";
        String effectiveMonth = month == null || month.isBlank()
                ? LocalDate.now().toString().substring(0, 7)
                : month;
        model.addAttribute("month", effectiveMonth);
        model.addAttribute("payouts", payments.calculateShopPayouts(effectiveMonth));
        model.addAttribute("summary", payments.getPayoutSummary(effectiveMonth));
        return "admin/shop-payouts";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(@RequestParam String month,
                                              HttpSession session) {
        if (!checkAdmin(session)) return ResponseEntity.status(401).build();
        byte[] excel = payments.exportPayoutsXlsx(month);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "shop-payouts-" + month + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(excel);
    }
}
