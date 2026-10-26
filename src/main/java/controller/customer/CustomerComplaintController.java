package controller.customer;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.complaint.ComplaintService;
import service.customer.CustomerAccountService;

@Controller
@RequestMapping("/customer/account/complaints")
public class CustomerComplaintController {

    private final ComplaintService complaintService;
    private final CustomerAccountService accountService;

    public CustomerComplaintController(ComplaintService complaintService, CustomerAccountService accountService) {
        this.complaintService = complaintService;
        this.accountService = accountService;
    }

    private Long currentCustomerId(HttpSession session) {
        if (session == null) return null;
        Object role = session.getAttribute("role");
        if (!"CUSTOMER".equals(role) && !"ADMIN".equals(role)) return null;
        Object id = session.getAttribute("userId");
        return id instanceof Number n ? n.longValue() : null;
    }

    @GetMapping
    public String listComplaints(
            @RequestParam(value = "status", defaultValue = "all") String status,
            Model model,
            HttpSession session) {
        Long customerId = currentCustomerId(session);
        if (customerId == null) {
            return "redirect:/login?continueTo=/customer/account/complaints";
        }
        try {
            model.addAttribute("profile", accountService.profile(customerId));
        } catch (Exception ignored) {
        }
        model.addAttribute("complaints", complaintService.listCustomerComplaints(customerId, status));
        model.addAttribute("statusCounts", complaintService.countCustomerComplaintsByStatus(customerId));
        model.addAttribute("currentStatus", status);
        model.addAttribute("activePage", "complaints");
        return "customer/complaints";
    }

    @GetMapping("/{id}")
    public String complaintDetail(@PathVariable Long id, Model model, HttpSession session) {
        Long customerId = currentCustomerId(session);
        if (customerId == null) {
            return "redirect:/login?continueTo=/customer/account/complaints/" + id;
        }
        try {
            model.addAttribute("profile", accountService.profile(customerId));
        } catch (Exception ignored) {
        }
        model.addAttribute("complaint", complaintService.getCustomerComplaintDetail(customerId, id));
        model.addAttribute("activePage", "complaints");
        return "customer/complaint-detail";
    }

    @PostMapping
    public String submitComplaint(
            @RequestParam Long orderId,
            @RequestParam(required = false) Long orderItemId,
            @RequestParam String reasonType,
            @RequestParam(required = false) String title,
            @RequestParam String description,
            @RequestParam(required = false) MultipartFile evidenceImage,
            @RequestParam(required = false, defaultValue = "0") Long requestedRefundAmount,
            HttpSession session,
            RedirectAttributes redirect) {
        Long customerId = currentCustomerId(session);
        if (customerId == null) {
            return "redirect:/login?continueTo=/customer/account/orders/" + orderId;
        }
        try {
            Long complaintId = complaintService.submitCustomerComplaint(
                    customerId, orderId, orderItemId, reasonType, title, description, evidenceImage, requestedRefundAmount);
            redirect.addFlashAttribute("message", "Đã gửi yêu cầu khiếu nại thành công tới Shop! Shop có tối đa 6 giờ để phản hồi.");
            return "redirect:/customer/account/complaints/" + complaintId;
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customer/account/orders/" + orderId;
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Không thể gửi yêu cầu khiếu nại lúc này. Vui lòng kiểm tra lại thông tin và thử lại.");
            return "redirect:/customer/account/orders/" + orderId;
        }
    }

    @PostMapping("/{id}/cancel")
    public String cancelComplaint(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirect) {
        Long customerId = currentCustomerId(session);
        if (customerId == null) {
            return "redirect:/login?continueTo=/customer/account/complaints/" + id;
        }
        try {
            complaintService.cancelCustomerComplaint(customerId, id);
            redirect.addFlashAttribute("message", "Đã rút lại yêu cầu khiếu nại.");
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/account/complaints/" + id;
    }
}
