package controller.operations;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.complaint.ComplaintService;
import service.login.CustomerDetailsService;

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

    private Long requireOperationsUserId(HttpSession session) {
        if (session != null) {
            Object role = session.getAttribute("role");
            Object uid = session.getAttribute("userId");
            if (uid instanceof Number n) {
                if ("OPERATIONS_MANAGER".equals(role) || "ADMIN".equals(role)) {
                    return n.longValue();
                }
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không có quyền Quản trị Vận hành.");
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomerDetailsService details) {
            String role = details.getPrimaryRole();
            if ("OPERATIONS_MANAGER".equals(role) || "ADMIN".equals(role)) {
                return details.getId();
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không có quyền Quản trị Vận hành.");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vui lòng đăng nhập bằng tài khoản Quản trị Vận hành (OPERATIONS_MANAGER).");
    }

    @GetMapping
    public String listComplaints(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String priority,
            @RequestParam(defaultValue = "ALL") String reasonType,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) String keyword,
            HttpSession session,
            Model model) {

        requireOperationsUserId(session);

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
        long countAll = queryCount("SELECT COUNT(*) FROM complaints WHERE status <> 'CANCELLED'");

        model.addAttribute("countPendingOps", countPendingOps);
        model.addAttribute("countPendingShop", countPendingShop);
        model.addAttribute("countResolved", countResolved);
        model.addAttribute("countRejected", countRejected);
        model.addAttribute("countAll", countAll);

        Map<String, Object> reports = complaintService.getAnalyticsAndReports();
        model.addAllAttributes(reports);

        model.addAttribute("reasonLabels", complaintService.getReasonLabels());
        model.addAttribute("resolutionLabels", complaintService.getResolutionLabels());

        return "operations/complaints";
    }

    @PostMapping("/create")
    public String createComplaintTicket(
            @RequestParam String orderCode,
            @RequestParam(defaultValue = "QUALITY_ISSUE") String reasonType,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "CS_MANUAL") String sourceType,
            HttpSession session,
            RedirectAttributes redirect) {
        Long opsUserId = requireOperationsUserId(session);
        try {
            Long ticketId = complaintService.createTicketByCs(
                    opsUserId, orderCode, reasonType, title, description, sourceType);
            redirect.addFlashAttribute("message", "Đã khởi tạo phiếu khiếu nại / hỗ trợ thành công!");
            return "redirect:/operations/complaints/" + ticketId;
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/operations/complaints";
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi khởi tạo phiếu khiếu nại: " + ex.getMessage());
            return "redirect:/operations/complaints";
        }
    }

    @GetMapping("/{id}")
    public String complaintDetail(@PathVariable Long id, HttpSession session, Model model) {
        requireOperationsUserId(session);
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
            @RequestParam(required = false) String resolutionAction,
            @RequestParam(required = false) Long refundAmount,
            HttpSession session,
            RedirectAttributes redirect) {
        Long opsUserId = requireOperationsUserId(session);
        try {
            if ("RETURN_REFUND".equalsIgnoreCase(resolutionAction)) {
                complaintService.approveComplaintRefundByOperations(opsUserId, id, refundAmount, note);
                redirect.addFlashAttribute("message", "Đã duyệt khiếu nại và ghi nhận lệnh hoàn tiền về tài khoản ngân hàng của khách hàng!");
            } else {
                complaintService.approveComplaintByOperations(opsUserId, id, note);
                redirect.addFlashAttribute("message", "Đã phê duyệt khiếu nại thành công! Hồ sơ đã được chuyển cho Cửa hàng để xử lý giao bù hoặc tặng voucher.");
            }
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi xử lý duyệt khiếu nại: " + ex.getMessage());
        }
        return "redirect:/operations/complaints/" + id;
    }

    @PostMapping("/{id}/approve-refund")
    public String approveComplaintRefund(
            @PathVariable Long id,
            @RequestParam(required = false) Long refundAmount,
            @RequestParam(required = false) String note,
            HttpSession session,
            RedirectAttributes redirect) {
        Long opsUserId = requireOperationsUserId(session);
        try {
            complaintService.approveComplaintRefundByOperations(opsUserId, id, refundAmount, note);
            redirect.addFlashAttribute("message", "Đã phê duyệt khiếu nại và lập lệnh hoàn tiền về tài khoản ngân hàng của khách hàng thành công!");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi xử lý duyệt hoàn tiền khiếu nại: " + ex.getMessage());
        }
        return "redirect:/operations/complaints/" + id;
    }

    @PostMapping("/{id}/reject")
    public String rejectComplaint(
            @PathVariable Long id,
            @RequestParam String reason,
            HttpSession session,
            RedirectAttributes redirect) {
        Long opsUserId = requireOperationsUserId(session);
        try {
            complaintService.rejectComplaintByOperations(opsUserId, id, reason);
            redirect.addFlashAttribute("message", "Đã từ chối khiếu nại thành công. Kết quả và lý do đã được thông báo tới khách hàng.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi xử lý từ chối khiếu nại: " + ex.getMessage());
        }
        return "redirect:/operations/complaints/" + id;
    }

    @PostMapping("/{id}/remind-shop")
    public String remindShopResponse(
            @PathVariable Long id,
            @RequestParam(required = false) String requestNote,
            HttpSession session,
            RedirectAttributes redirect) {
        Long opsUserId = requireOperationsUserId(session);
        try {
            complaintService.requestShopResponse(id, opsUserId, requestNote);
            redirect.addFlashAttribute("message", "Đã gửi thông báo đôn đốc Chủ Shop xử lý khiếu nại!");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi gửi thông báo đôn đốc Shop: " + ex.getMessage());
        }
        return "redirect:/operations/complaints/" + id;
    }

    @PostMapping("/violations/{shopId}/escalate")
    public String escalateShopViolation(
            @PathVariable Long shopId,
            @RequestParam(required = false) String reason,
            HttpSession session,
            RedirectAttributes redirect) {
        requireOperationsUserId(session);
        try {
            complaintService.escalateShopToOperations(shopId, reason);
            redirect.addFlashAttribute("message", "Đã ghi nhận cảnh báo vi phạm hậu kiểm cho Shop #" + shopId + ".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/operations/complaints";
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
