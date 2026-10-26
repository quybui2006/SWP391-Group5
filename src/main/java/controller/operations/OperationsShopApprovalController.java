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

/**
 * ==============================================================================================
 * CONTROLLER: OperationsShopApprovalController
 * ==============================================================================================
 * Mục đích: Điều phối toàn bộ luồng duyệt hồ sơ mở Shop và đối soát thanh toán của Ban vận hành (Operations).
 * 
 * Luồng hoạt động chính:
 * 1. GET  /operations/shops                -> Xem danh sách shop theo trạng thái (PENDING, APPROVED, REJECTED, ALL).
 * 2. GET  /operations/shops/{id}           -> Xem chi tiết hồ sơ thẩm định pháp lý (CCCD, thuế, tài khoản ngân hàng).
 * 3. POST /operations/shops/{id}/approve   -> Duyệt mở Shop: Chuyển approvalStatus='APPROVED', cấp role 'SHOP_OWNER'.
 * 4. POST /operations/shops/{id}/reject    -> Từ chối mở Shop: Lưu lý do từ chối, gửi thông báo cho Seller sửa lại.
 * 5. POST /operations/shops/{id}/operating-status -> Cập nhật trạng thái mở bán (OPEN / CLOSED / SUSPENDED).
 * 6. POST /operations/shops/{id}/payment/approve  -> Duyệt tài khoản đối soát thanh toán và tự động kích hoạt OPEN.
 * 7. POST /operations/shops/{id}/payment/reject   -> Từ chối tài khoản đối soát và gửi phản hồi cho Seller.
 * ==============================================================================================
 */
@Controller
public class OperationsShopApprovalController {
    private final OperationsShopApprovalService approvals;
    private final service.operations.OperationsShopManagementService shops;

    public OperationsShopApprovalController(
            OperationsShopApprovalService approvals,
            service.operations.OperationsShopManagementService shops) {
        this.approvals = approvals;
        this.shops = shops;
    }

    /**
     * BƯỚC 1A: Danh sách tài khoản người bán cần xác thực định danh (CCCD/MST).
     */
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

    /**
     * BƯỚC 1B: Duyệt hồ sơ định danh người bán.
     */
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

    /**
     * BƯỚC 1C: Từ chối hồ sơ định danh người bán (kèm lý do).
     */
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

    /**
     * BƯỚC 2A: Danh sách tài khoản ngân hàng chờ đối soát thanh toán.
     */
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

    /**
     * BƯỚC 3: Hiển thị danh sách hồ sơ mở Shop cần duyệt (Lọc theo tab trạng thái: PENDING, APPROVED, REJECTED, ALL).
     */
    @GetMapping("/operations/shops")
    public String shopApprovalList(
            @RequestParam(value = "status", required = false, defaultValue = "PENDING") String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model) {

        // Lấy danh sách hồ sơ shop và thống kê số lượng từng trạng thái
        List<ShopApprovalItemDto> shops = approvals.findShops(status, keyword);
        Map<String, Long> stats = approvals.getApprovalStats();

        model.addAttribute("shops", shops);
        model.addAttribute("stats", stats);
        model.addAttribute("currentStatus", status != null ? status.toUpperCase() : "PENDING");
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("activeNav", "shops");

        return "operations/shops";
    }

    /**
     * BƯỚC 4: Hiển thị trang chi tiết thẩm định hồ sơ Shop (CCCD, thuế, đối soát tên ngân hàng, danh mục sản phẩm).
     */
    @GetMapping("/operations/shops/{id:[0-9]+}")
    public String shopApprovalDetail(@PathVariable Long id, Model model) {
        // Lấy thông tin chi tiết hồ sơ shop và kết quả đối soát tự động (Họ tên CCCD vs Tên chủ TK ngân hàng)
        ShopApprovalDetailDto shop = approvals.getShopDetail(id);
        model.addAttribute("shop", shop);
        // Lấy danh sách sản phẩm / biến thể của shop để Operations hậu kiểm chất lượng nông sản
        model.addAttribute("variants", shops.listShopVariants(id));
        model.addAttribute("activeNav", "shops");
        return "operations/shop-detail";
    }

    /**
     * BƯỚC 5: Xử lý Form phê duyệt hồ sơ mở Shop.
     * - Cập nhật database: approval_status = 'APPROVED'.
     * - Phân quyền tự động: Cấp role 'SHOP_OWNER' vào bảng user_roles.
     * - Bắn thông báo: Gửi notification nhắc chủ shop khai báo thông tin thanh toán.
     */
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

    /**
     * BƯỚC 6: Xử lý Form từ chối hồ sơ mở Shop.
     * - Yêu cầu nhập lý do từ chối (rejectionReason).
     * - Cập nhật database: approval_status = 'REJECTED'.
     * - Bắn thông báo giải thích lý do tới Người bán để họ chỉnh sửa và nộp lại hồ sơ.
     */
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

    /**
     * BƯỚC 7: Cập nhật trạng thái vận hành của gian hàng (OPEN / CLOSED / SUSPENDED).
     * - Nếu SUSPENDED (đình chỉ do vi phạm quy chế), yêu cầu nhập lý do và gửi thông báo cảnh cáo.
     */
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

    /**
     * BƯỚC 8: Phê duyệt thông tin tài khoản ngân hàng thụ hưởng.
     * - Tự động kích hoạt trạng thái gian hàng sang 'OPEN' (mở bán công khai trên sàn).
     * - Gửi thông báo chúc mừng tới Shop Owner.
     */
    @PostMapping("/operations/shops/{id:[0-9]+}/payment/approve")
    public String approvePaymentForm(
            @PathVariable Long id,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            RedirectAttributes redirectAttributes) {
        try {
            approvals.approvePayment(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt thông tin tài khoản ngân hàng thành công! Cửa hàng đã được mở bán (OPEN).");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/operations/payments";
    }

    /**
     * BƯỚC 9: Từ chối thông tin tài khoản ngân hàng (do sai lệch tên chủ tài khoản so với CCCD).
     * - Giữ nguyên trạng thái shop là 'CLOSED'.
     * - Gửi thông báo yêu cầu người bán cập nhật lại tài khoản chính chủ.
     */
    @PostMapping("/operations/shops/{id:[0-9]+}/payment/reject")
    public String rejectPaymentForm(
            @PathVariable Long id,
            @RequestParam("reason") String reason,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            RedirectAttributes redirectAttributes) {
        try {
            approvals.rejectPayment(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối thông tin thanh toán và gửi thông báo yêu cầu người bán điều chỉnh.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/operations/payments";
    }

    // ==========================================================================================
    // JSON REST API: Dành cho các nút bấm gọi ngầm bằng Javascript / AJAX
    // ==========================================================================================

    /**
     * REST API duyệt Shop (trả về JSON).
     */
    @PostMapping("/operations/api/shops/{shopId}/approve")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> approveShop(@PathVariable Long shopId) {
        approvals.approveShop(shopId);
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã duyệt yêu cầu mở Shop."));
    }

    /**
     * REST API từ chối Shop (trả về JSON).
     */
    @PostMapping("/operations/api/shops/{shopId}/reject")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> rejectShop(
            @PathVariable Long shopId,
            @Valid @RequestBody RejectApprovalRequest request) {
        approvals.rejectShop(shopId, request.getRejectionReason());
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã từ chối yêu cầu mở Shop."));
    }
}
