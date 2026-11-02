package controller.operations;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.complaint.ComplaintService;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/operations/complaints")
public class OperationsComplaintController {

    private final ComplaintService complaintService;
    private final JdbcTemplate jdbc;

    public OperationsComplaintController(ComplaintService complaintService, JdbcTemplate jdbc) {
        this.complaintService = complaintService;
        this.jdbc = jdbc;
    }

    private Long currentOperationsUserId(HttpSession session) {
        if (session != null) {
            Object uid = session.getAttribute("userId");
            if (uid instanceof Number n) return n.longValue();
        }
        // Fallback: tìm ID của tài khoản có role OPERATIONS_MANAGER hoặc ADMIN
        try {
            List<Long> ids = jdbc.queryForList("""
                SELECT u.id FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE r.code IN ('OPERATIONS_MANAGER', 'ADMIN')
                ORDER BY u.id ASC LIMIT 1
            """, Long.class);
            if (!ids.isEmpty()) return ids.get(0);
        } catch (Exception ignored) {}
        return 1L;
    }

    @GetMapping
    public String listComplaints(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String priority,
            @RequestParam(defaultValue = "ALL") String reasonType,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) String keyword,
            Model model) {

        model.addAttribute("activeNav", "complaints");
        model.addAttribute("activePage", "complaints");
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("selectedReason", reasonType);
        model.addAttribute("overdueOnly", overdue);
        model.addAttribute("keyword", keyword);

        List<Map<String, Object>> tickets = complaintService.listCsComplaints(status, priority, reasonType, overdue, keyword);
        model.addAttribute("tickets", tickets);

        // Thống kê nhanh theo trạng thái cho thanh tab
        long countPendingOps = queryCount("SELECT COUNT(*) FROM complaints WHERE status IN ('PENDING_OPERATIONS', 'PENDING_OPS')");
        long countPendingShop = queryCount("SELECT COUNT(*) FROM complaints WHERE status = 'PENDING_SHOP'");
        long countResolved = queryCount("SELECT COUNT(*) FROM complaints WHERE status = 'RESOLVED'");
        long countRejected = queryCount("SELECT COUNT(*) FROM complaints WHERE status = 'REJECTED'");
        long countAll = queryCount("SELECT COUNT(*) FROM complaints");

        model.addAttribute("countPendingOps", countPendingOps);
        model.addAttribute("countPendingShop", countPendingShop);
        model.addAttribute("countResolved", countResolved);
        model.addAttribute("countRejected", countRejected);
        model.addAttribute("countAll", countAll);

        model.addAttribute("reasonLabels", complaintService.getReasonLabels());
        model.addAttribute("resolutionLabels", complaintService.getResolutionLabels());

        return "operations/complaints";
    }

    @GetMapping("/{id}")
    public String complaintDetail(@PathVariable Long id, Model model) {
        model.addAttribute("activeNav", "complaints");
        model.addAttribute("activePage", "complaints");
        Map<String, Object> complaint = complaintService.getComplaintDetail(id);
        model.addAttribute("complaint", complaint);
        model.addAttribute("reasonLabels", complaintService.getReasonLabels());
        model.addAttribute("resolutionLabels", complaintService.getResolutionLabels());
        return "operations/complaint-detail";
    }

    @PostMapping("/{id}/approve")
    public String approveComplaint(
            @PathVariable Long id,
            @RequestParam(required = false) String note,
            HttpSession session,
            RedirectAttributes redirect) {
        try {
            Long opsUserId = currentOperationsUserId(session);
            complaintService.approveComplaintByOperations(opsUserId, id, note);
            redirect.addFlashAttribute("message", "Đã phê duyệt khiếu nại thành công! Hồ sơ đã được chuyển cho Cửa hàng để xử lý giao bù hoặc tặng voucher.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi xử lý duyệt khiếu nại: " + ex.getMessage());
        }
        return "redirect:/operations/complaints/" + id;
    }

    @PostMapping("/{id}/reject")
    public String rejectComplaint(
            @PathVariable Long id,
            @RequestParam String reason,
            HttpSession session,
            RedirectAttributes redirect) {
        try {
            Long opsUserId = currentOperationsUserId(session);
            complaintService.rejectComplaintByOperations(opsUserId, id, reason);
            redirect.addFlashAttribute("message", "Đã từ chối khiếu nại thành công. Kết quả và lý do đã được thông báo tới khách hàng.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi xử lý từ chối khiếu nại: " + ex.getMessage());
        }
        return "redirect:/operations/complaints/" + id;
    }

    private long queryCount(String sql, Object... args) {
        try {
            Long c = jdbc.queryForObject(sql, Long.class, args);
            return c != null ? c : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
