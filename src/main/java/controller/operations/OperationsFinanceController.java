package controller.operations;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import service.operations.OperationsFinanceService;

import java.io.IOException;
import java.time.LocalDate;

@Controller
@RequestMapping("/operations/finance")
public class OperationsFinanceController {
    private final OperationsFinanceService finance;
    public OperationsFinanceController(OperationsFinanceService finance) { this.finance = finance; }

    @GetMapping
    public String financeManagement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refundDate,
            @RequestParam(defaultValue = "ALL") String refundStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
            Model model,
            HttpSession session) {
        LocalDate day = refundDate == null ? LocalDate.now() : refundDate;
        model.addAttribute("refundDate", day);
        model.addAttribute("refundStatus", refundStatus);
        model.addAttribute("periodFrom", periodFrom == null ? day.withDayOfMonth(1) : periodFrom);
        model.addAttribute("periodTo", periodTo == null ? day : periodTo);
        model.addAttribute("refunds", finance.refunds(day, refundStatus));
        model.addAttribute("eligibleRefundCount", finance.eligibleRefundCount(day));
        model.addAttribute("payouts", finance.payouts());
        model.addAttribute("eligiblePayoutCount", finance.eligiblePayoutCount());
        model.addAttribute("commissionRate", finance.currentCommissionRate().multiply(new java.math.BigDecimal("100")).stripTrailingZeros().toPlainString());
        model.addAttribute("refundExportHistory", finance.exportHistory("CUSTOMER_REFUNDS"));
        model.addAttribute("payoutExportHistory", finance.exportHistory("SHOP_PAYOUTS"));
        model.addAttribute("activeNav", "finance");
        model.addAttribute("activePage", "finance");
        return "operations/finance";
    }

    @PostMapping("/payouts/create")
    public String createPayouts(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        try { redirect.addFlashAttribute("message", "Đã tạo " + finance.createPayouts(periodFrom, periodTo) + " bảng kê thanh toán Shop."); }
        catch (Exception e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/operations/finance?periodFrom="+periodFrom+"&periodTo="+periodTo;
    }

    @GetMapping("/refunds/export")
    public void exportRefunds(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, HttpSession session, HttpServletResponse response) throws IOException {
        Long userId = session != null && session.getAttribute("userId") instanceof Number n ? n.longValue() : null;
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=customer-refunds-"+date+".xlsx");
            response.getOutputStream().write(finance.exportRefunds(date, userId));
        } catch (IllegalArgumentException ex) {
            response.reset(); response.setStatus(400); response.setContentType("text/plain;charset=UTF-8"); response.getWriter().write(ex.getMessage());
        }
    }

    @GetMapping("/payouts/export")
    public void exportPayouts(HttpSession session, HttpServletResponse response) throws IOException {
        Long userId = session != null && session.getAttribute("userId") instanceof Number n ? n.longValue() : null;
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=shop-payouts.xlsx");
            response.getOutputStream().write(finance.exportPayouts(userId));
        } catch (IllegalArgumentException ex) {
            response.reset(); response.setStatus(400); response.setContentType("text/plain;charset=UTF-8"); response.getWriter().write(ex.getMessage());
        }
    }

    @GetMapping("/exports/{id}/download")
    public void downloadExport(@PathVariable Long id, HttpServletResponse response) throws IOException {
        var file = finance.exportFile(id);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.get("file_name") + "\"");
        response.getOutputStream().write((byte[]) file.get("file_content"));
    }
}
