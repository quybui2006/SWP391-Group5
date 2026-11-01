package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;
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

    @org.springframework.web.bind.annotation.PostMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/respond")
    public String respondComplaint(@org.springframework.web.bind.annotation.PathVariable Long orderId,
                                   @org.springframework.web.bind.annotation.PathVariable Long complaintId,
                                   @RequestParam(defaultValue = "REJECT") String decision,
                                   @RequestParam(required = false) String shopResponse,
                                   @RequestParam(required = false) org.springframework.web.multipart.MultipartFile shopEvidenceImage,
                                   HttpServletRequest request,
                                   org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        Long owner = currentUser.requireUserId(request);
        try {
            complaintService.submitShopDecision(owner, complaintId, decision, shopResponse, shopEvidenceImage);
            if ("ACCEPT".equalsIgnoreCase(decision)) {
                redirect.addFlashAttribute("message", "Đã đồng ý giải quyết khiếu nại và tự động hoàn trả điểm cho khách hàng!");
            } else {
                redirect.addFlashAttribute("message", "Đã gửi giải trình từ chối khiếu nại và chuyển hồ sơ lên CSKH phân xử.");
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
