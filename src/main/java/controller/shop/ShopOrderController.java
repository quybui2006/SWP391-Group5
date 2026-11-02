package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Objects;
import service.customer.CurrentUserProvider;
import service.complaint.ComplaintService;
import service.shop.ShopOrderExcelExporter;
import service.shop.ShopOrderService;

@Controller
public class ShopOrderController {
    private final CurrentUserProvider currentUser;
    private final ShopOrderService orders;
    private final ShopOrderExcelExporter excelExporter;
    private final ComplaintService complaintService;
    public ShopOrderController(CurrentUserProvider currentUser, ShopOrderService orders, ShopOrderExcelExporter excelExporter, ComplaintService complaintService) {
        this.currentUser = currentUser; this.orders = orders; this.excelExporter = excelExporter; this.complaintService = complaintService;
    }

    private Map<String, Object> verifyComplaintBelongsToOrderAndShop(Long ownerId, Long orderId, Long complaintId) {
        Map<String, Object> order = orders.detail(ownerId, orderId);
        Long shopId = ((Number) order.get("shop_id")).longValue();
        Map<String, Object> complaint = complaintService.getComplaintDetail(complaintId);
        if (complaint == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khiếu nại.");
        }
        Long complaintOrderId = complaint.get("order_id") instanceof Number n ? n.longValue() : null;
        Long complaintShopId = complaint.get("shop_id") instanceof Number n ? n.longValue() : null;
        if (!Objects.equals(orderId, complaintOrderId) || !Objects.equals(shopId, complaintShopId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Khiếu nại không thuộc đơn hàng hoặc cửa hàng của bạn.");
        }
        return complaint;
    }

    @GetMapping("/shopowner/orders")
    public String orders(@RequestParam(value = "filter", required = false) String filter,
                         HttpServletRequest request, Model model) {
        Long ownerId = currentUser.requireUserId(request);
        model.addAttribute("orders", orders.list(ownerId, filter));
        model.addAttribute("filter", filter != null ? filter : "all");

        Map<String, Object> complaintCounts = orders.countComplaints(ownerId);
        long pendingComplaintsCount = ((Number) complaintCounts.getOrDefault("pending_shop_complaints", 0)).longValue();
        long totalComplaintsCount = ((Number) complaintCounts.getOrDefault("total_complaints", 0)).longValue();

        model.addAttribute("pendingComplaintsCount", pendingComplaintsCount);
        model.addAttribute("totalComplaintsCount", totalComplaintsCount);

        model.addAttribute("ownerName", request.getSession(false).getAttribute("fullName"));
        var session = request.getSession(false);
        if (session.getAttribute("shopOrderToken") == null) session.setAttribute("shopOrderToken", java.util.UUID.randomUUID().toString());
        model.addAttribute("token", session.getAttribute("shopOrderToken"));
        return "shopowner/orders";
    }

    @GetMapping("/shopowner/orders/export.xlsx")
    public ResponseEntity<byte[]> export(HttpServletRequest request) {
        Long ownerId = currentUser.requireUserId(request);
        byte[] workbook = excelExporter.export(orders.exportRows(ownerId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=shop-orders.xlsx")
                .body(workbook);
    }

    @GetMapping("/shopowner/orders/{id}")
    public String detail(@org.springframework.web.bind.annotation.PathVariable Long id, HttpServletRequest request, Model model) {
        Long owner = currentUser.requireUserId(request);
        model.addAttribute("order", orders.detail(owner, id));
        model.addAttribute("items", orders.items(owner, id));
        model.addAttribute("orderComplaints", complaintService.listComplaintsByOrder(id));
        var session = request.getSession(false);
        if (session.getAttribute("shopOrderToken") == null) session.setAttribute("shopOrderToken", java.util.UUID.randomUUID().toString());
        model.addAttribute("token", session.getAttribute("shopOrderToken"));
        return "shopowner/order-detail";
    }

    @GetMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/resend")
    public String resendOrderPage(@PathVariable Long orderId,
                                  @PathVariable Long complaintId,
                                  HttpServletRequest request,
                                  Model model) {
        Long owner = currentUser.requireUserId(request);
        Map<String, Object> order = orders.detail(owner, orderId);
        Long shopId = ((Number) order.get("shop_id")).longValue();
        Map<String, Object> complaint = verifyComplaintBelongsToOrderAndShop(owner, orderId, complaintId);

        model.addAttribute("order", order);
        model.addAttribute("orderItems", orders.items(owner, orderId));
        model.addAttribute("complaint", complaint);
        model.addAttribute("shopVariants", complaintService.getAvailableShopVariants(shopId));
        return "shopowner/complaint-resend-order";
    }

    @PostMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/resend")
    public String processResendOrder(@PathVariable Long orderId,
                                     @PathVariable Long complaintId,
                                     @RequestParam(name = "variantIds", required = false) java.util.List<Long> variantIds,
                                     @RequestParam(name = "quantities", required = false) java.util.List<Integer> quantities,
                                     @RequestParam(required = false) String shopResponse,
                                     HttpServletRequest request,
                                     org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        Long owner = currentUser.requireUserId(request);
        verifyComplaintBelongsToOrderAndShop(owner, orderId, complaintId);
        try {
            Long replacementOrderId = complaintService.submitShopDecisionResendCustom(owner, complaintId, variantIds, quantities, shopResponse);
            redirect.addFlashAttribute("message", "Đã tạo đơn giao bù 0đ thành công và chuyển sang quy trình đóng gói!");
            return "redirect:/shopowner/orders/" + replacementOrderId;
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/shopowner/orders/" + orderId + "/complaints/" + complaintId + "/resend";
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi tạo đơn giao bù: " + ex.getMessage());
            return "redirect:/shopowner/orders/" + orderId;
        }
    }

    @GetMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/voucher")
    public String issueVoucherPage(@PathVariable Long orderId,
                                   @PathVariable Long complaintId,
                                   HttpServletRequest request,
                                   Model model) {
        Long owner = currentUser.requireUserId(request);
        Map<String, Object> order = orders.detail(owner, orderId);
        Map<String, Object> complaint = verifyComplaintBelongsToOrderAndShop(owner, orderId, complaintId);

        model.addAttribute("order", order);
        model.addAttribute("complaint", complaint);

        String randomSuffix = java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase(java.util.Locale.ROOT);
        String suggestedCode = "VC-KN-" + randomSuffix;
        long suggestedDiscount = 50000L;
        if (complaint.get("actual_paid_amount") instanceof Number num && num.longValue() > 0) {
            suggestedDiscount = num.longValue();
        }

        model.addAttribute("suggestedCode", suggestedCode);
        model.addAttribute("suggestedDiscount", suggestedDiscount);
        return "shopowner/complaint-voucher-form";
    }

    @PostMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/voucher")
    public String processIssueVoucher(@PathVariable Long orderId,
                                      @PathVariable Long complaintId,
                                      @RequestParam(required = false) String voucherCode,
                                      @RequestParam(required = false) String voucherName,
                                      @RequestParam(defaultValue = "FIXED_AMOUNT") String discountType,
                                      @RequestParam(required = false) java.math.BigDecimal discountValue,
                                      @RequestParam(required = false) java.math.BigDecimal maxDiscountAmount,
                                      @RequestParam(required = false) java.math.BigDecimal minOrderValue,
                                      @RequestParam(defaultValue = "30") Integer validDays,
                                      @RequestParam(required = false) String shopResponse,
                                      HttpServletRequest request,
                                      org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        Long owner = currentUser.requireUserId(request);
        verifyComplaintBelongsToOrderAndShop(owner, orderId, complaintId);
        try {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime end = now.plusDays(validDays != null && validDays > 0 ? validDays : 30);
            complaintService.submitShopDecisionIssueVoucherCustom(
                    owner, complaintId, voucherCode, voucherName, discountType,
                    discountValue, maxDiscountAmount, minOrderValue,
                    now, end, shopResponse
            );
            redirect.addFlashAttribute("message", "Đã phát hành voucher đền bù thành công và lưu trực tiếp vào ví của khách hàng!");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/shopowner/orders/" + orderId + "/complaints/" + complaintId + "/voucher";
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi phát hành voucher: " + ex.getMessage());
        }
        return "redirect:/shopowner/orders/" + orderId;
    }

    @org.springframework.web.bind.annotation.PostMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/respond")
    public String respondComplaint(@org.springframework.web.bind.annotation.PathVariable Long orderId,
                                   @org.springframework.web.bind.annotation.PathVariable Long complaintId,
                                   @RequestParam(defaultValue = "RESEND_ITEMS") String decision,
                                   @RequestParam(required = false) String shopResponse,
                                   @RequestParam(required = false) org.springframework.web.multipart.MultipartFile shopEvidenceImage,
                                   @RequestParam(required = false) Long voucherDiscount,
                                   HttpServletRequest request,
                                   org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        Long owner = currentUser.requireUserId(request);
        verifyComplaintBelongsToOrderAndShop(owner, orderId, complaintId);
        try {
            complaintService.submitShopDecision(owner, complaintId, decision, shopResponse, shopEvidenceImage, voucherDiscount);
            if ("RESEND_ITEMS".equalsIgnoreCase(decision)) {
                redirect.addFlashAttribute("message", "Đã chấp thuận khiếu nại và tự động tạo đơn hàng giao bù 0đ cho khách hàng!");
            } else if ("ISSUE_VOUCHER".equalsIgnoreCase(decision)) {
                redirect.addFlashAttribute("message", "Đã chấp thuận khiếu nại và tặng voucher đền bù vào ví của khách hàng!");
            } else {
                redirect.addFlashAttribute("message", "Đã cập nhật quyết định xử lý khiếu nại.");
            }
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Đã xảy ra lỗi khi xử lý khiếu nại: " + (ex.getMessage() != null ? ex.getMessage() : "Vui lòng kiểm tra lại."));
        }
        return "redirect:/shopowner/orders/" + orderId;
    }

    @org.springframework.web.bind.annotation.PostMapping("/shopowner/orders/{id}/confirm")
    public String confirm(@org.springframework.web.bind.annotation.PathVariable Long id,
                          @RequestParam(required=false) String token,
                          HttpServletRequest request, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        Long owner = currentUser.requireUserId(request);
        if (token == null || !token.equals(request.getSession(false).getAttribute("shopOrderToken")))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        try { orders.confirmCod(owner, id); redirect.addFlashAttribute("message", "Đã xác nhận đơn hàng và ghi nhận số lượng đã bán."); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("message", ex.getMessage()); }
        return "redirect:/shopowner/orders/" + id;
    }

    @org.springframework.web.bind.annotation.PostMapping("/shopowner/orders/{id}/reject")
    public String reject(@org.springframework.web.bind.annotation.PathVariable Long id,
                         @RequestParam(required=false) String token,
                         @RequestParam String reason,
                         HttpServletRequest request, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        Long owner = currentUser.requireUserId(request);
        if (token == null || !token.equals(request.getSession(false).getAttribute("shopOrderToken")))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        try { orders.rejectCod(owner, id, reason); redirect.addFlashAttribute("message", "Đã từ chối đơn và thông báo cho khách."); }
        catch (IllegalArgumentException ex) { redirect.addFlashAttribute("message", ex.getMessage()); }
        return "redirect:/shopowner/orders/" + id;
    }
}
