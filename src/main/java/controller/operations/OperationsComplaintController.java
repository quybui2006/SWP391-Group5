package controller.operations;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
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

    public OperationsComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
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

        Map<String, Long> statusCounts = complaintService.getComplaintStatusCounts();
        if (statusCounts != null) {
            model.addAllAttributes(statusCounts);
        }

        Map<String, Object> reports = complaintService.getAnalyticsAndReports();
        model.addAllAttributes(reports);

        model.addAttribute("reasonLabels", complaintService.getReasonLabels());

        return "operations/complaints";
    }

    @GetMapping("/{id}")
    public String complaintDetail(@PathVariable Long id, HttpSession session, Model model) {
        requireOperationsUserId(session);
        model.addAttribute("activeNav", "complaints");
        model.addAttribute("activePage", "complaints");
        Map<String, Object> complaint = complaintService.getComplaintDetail(id);
        model.addAttribute("complaint", complaint);
        return "operations/complaint-detail";
    }

    @PostMapping("/{id}/resolve")
    public String resolveComplaint(
            @PathVariable Long id,
            @RequestParam String resolutionAction,
            @RequestParam(required = false) String note,
            HttpSession session,
            RedirectAttributes redirect) {
        Long opsUserId = requireOperationsUserId(session);
        try {
            if ("RETURN_REFUND".equalsIgnoreCase(resolutionAction)) {
                complaintService.approveComplaintRefundByOperations(opsUserId, id, note);
                redirect.addFlashAttribute("message", "Đã duyệt khiếu nại và lập lệnh hoàn tiền 100% về tài khoản ngân hàng của khách hàng thành công!");
            } else if ("APPROVE_ONLY".equalsIgnoreCase(resolutionAction)) {
                complaintService.approveComplaintByOperations(opsUserId, id, note);
                redirect.addFlashAttribute("message", "Đã phê duyệt khiếu nại thành công! Hồ sơ đã được chuyển cho Cửa hàng để xử lý giao bù hoặc tặng voucher.");
            } else {
                throw new IllegalArgumentException("Hành động xử lý khiếu nại không hợp lệ: " + resolutionAction);
            }
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi xử lý khiếu nại: " + ex.getMessage());
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
            redirect.addFlashAttribute("message", "Đã ghi nhận cảnh báo vi phạm và đánh dấu hậu kiểm khẩn cấp cho Shop #" + shopId + ".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi gửi cảnh báo hậu kiểm Shop: " + ex.getMessage());
        }
        return "redirect:/operations/complaints";
    }
}
