package controller.operations;

import dto.operations.ApprovalActionResponse;
import dto.operations.RejectApprovalRequest;
import dto.operations.ShopApprovalDetailDto;
import dto.operations.ShopApprovalItemDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.operations.OperationsShopApprovalService;

import java.util.List;
import java.util.Map;

@Controller
public class OperationsShopApprovalController {
    private final OperationsShopApprovalService approvals;

    public OperationsShopApprovalController(OperationsShopApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/operations/sellers")
    public String sellerApprovalList(
            @RequestParam(value = "status", required = false, defaultValue = "PENDING") String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model) {

        List<ShopApprovalItemDto> sellers = approvals.findSellers(status, keyword);
        Map<String, Long> stats = approvals.getSellerApprovalStats();

        model.addAttribute("sellers", sellers);
        model.addAttribute("stats", stats);
        model.addAttribute("currentStatus", status != null ? status.toUpperCase() : "PENDING");
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("activeNav", "sellers");

        return "operations/sellers";
    }

    @PostMapping("/operations/shops/{id:[0-9]+}/seller/approve")
    public String approveSellerForm(
            @PathVariable Long id,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            RedirectAttributes redirectAttributes) {
        try {
            approvals.approveSellerAccount(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt tài khoản bán hàng thành công! Hồ sơ định danh & thuế đã được xác thực.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectUrl != null && !redirectUrl.isBlank() ? "redirect:" + redirectUrl : "redirect:/operations/shops/" + id;
    }

    @PostMapping("/operations/shops/{id:[0-9]+}/seller/reject")
    public String rejectSellerForm(
            @PathVariable Long id,
            @RequestParam("rejectionReason") String rejectionReason,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            RedirectAttributes redirectAttributes) {
        try {
            approvals.rejectSellerAccount(id, rejectionReason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối tài khoản bán hàng và gửi phản hồi đến người dùng.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectUrl != null && !redirectUrl.isBlank() ? "redirect:" + redirectUrl : "redirect:/operations/shops/" + id;
    }

    @GetMapping("/operations/payments")
    public String paymentApprovalList(
            @RequestParam(value = "status", required = false, defaultValue = "PENDING") String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model) {

        List<ShopApprovalItemDto> payments = approvals.findPayments(status, keyword);
        Map<String, Long> stats = approvals.getPaymentApprovalStats();

        model.addAttribute("payments", payments);
        model.addAttribute("stats", stats);
        model.addAttribute("currentStatus", status != null ? status.toUpperCase() : "PENDING");
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("activeNav", "payments");

        return "operations/payments";
    }

    @GetMapping("/operations/shops")
    public String shopApprovalList(
            @RequestParam(value = "status", required = false, defaultValue = "PENDING") String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model) {

        List<ShopApprovalItemDto> shops = approvals.findShops(status, keyword);
        Map<String, Long> stats = approvals.getApprovalStats();

        model.addAttribute("shops", shops);
        model.addAttribute("stats", stats);
        model.addAttribute("currentStatus", status != null ? status.toUpperCase() : "PENDING");
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("activeNav", "shops");

        return "operations/shops";
    }

    @GetMapping("/operations/shops/{id:[0-9]+}")
    public String shopApprovalDetail(@PathVariable Long id, Model model) {
        ShopApprovalDetailDto shop = approvals.getShopDetail(id);
        model.addAttribute("shop", shop);
        model.addAttribute("activeNav", "shops");
        return "operations/shop-detail";
    }

    @PostMapping("/operations/shops/{id:[0-9]+}/approve")
    public String approveShopForm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            approvals.approveShop(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt yêu cầu mở Shop thành công! Cửa hàng đã được kích hoạt.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/operations/shops/" + id;
    }

    @PostMapping("/operations/shops/{id:[0-9]+}/reject")
    public String rejectShopForm(
            @PathVariable Long id,
            @RequestParam("rejectionReason") String rejectionReason,
            RedirectAttributes redirectAttributes) {
        try {
            approvals.rejectShop(id, rejectionReason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối yêu cầu mở Shop và gửi phản hồi đến người dùng.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/operations/shops/" + id;
    }

    @PostMapping("/operations/shops/{id:[0-9]+}/operating-status")
    public String updateOperatingStatusForm(
            @PathVariable Long id,
            @RequestParam("operatingStatus") String operatingStatus,
            @RequestParam(value = "suspensionReason", required = false) String suspensionReason,
            RedirectAttributes redirectAttributes) {
        try {
            approvals.updateOperatingStatus(id, operatingStatus, suspensionReason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái hoạt động của Shop thành: " + operatingStatus);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/operations/shops/" + id;
    }

    @PostMapping("/operations/shops/{id:[0-9]+}/payment/approve")
    public String approvePaymentForm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            approvals.approvePayment(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt thông tin tài khoản ngân hàng thành công! Cửa hàng đã được mở bán (OPEN).");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/operations/shops/" + id;
    }

    @PostMapping("/operations/shops/{id:[0-9]+}/payment/reject")
    public String rejectPaymentForm(
            @PathVariable Long id,
            @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {
        try {
            approvals.rejectPayment(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối thông tin thanh toán và gửi thông báo yêu cầu người bán điều chỉnh.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/operations/shops/" + id;
    }

    // JSON API for existing AJAX callers
    @PostMapping("/operations/api/shops/{shopId}/approve")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> approveShop(@PathVariable Long shopId) {
        approvals.approveShop(shopId);
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã duyệt yêu cầu mở Shop."));
    }

    @PostMapping("/operations/api/shops/{shopId}/reject")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> rejectShop(
            @PathVariable Long shopId,
            @Valid @RequestBody RejectApprovalRequest request) {
        approvals.rejectShop(shopId, request.getRejectionReason());
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã từ chối yêu cầu mở Shop."));
    }
}
