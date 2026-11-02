package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
<<<<<<< HEAD
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;
import service.customer.CurrentUserProvider;
=======
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.jdbc.core.JdbcTemplate;
>>>>>>> develop
import service.complaint.ComplaintService;
import service.customer.CurrentUserProvider;
import service.shop.ShopOrderExcelExporter;
import service.shop.ShopOrderService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Controller
public class ShopOrderController {

    private static final String SESSION_TOKEN_ATTRIBUTE = "shopOrderToken";

    private final CurrentUserProvider currentUser;
    private final ShopOrderService orders;
    private final ShopOrderExcelExporter excelExporter;
    private final ComplaintService complaintService;
<<<<<<< HEAD
    public ShopOrderController(CurrentUserProvider currentUser, ShopOrderService orders, ShopOrderExcelExporter excelExporter, ComplaintService complaintService) {
        this.currentUser = currentUser; this.orders = orders; this.excelExporter = excelExporter; this.complaintService = complaintService;
    }
=======
    private final JdbcTemplate jdbc;

    public ShopOrderController(CurrentUserProvider currentUser,
                               ShopOrderService orders,
                               ShopOrderExcelExporter excelExporter,
                               ComplaintService complaintService,
                               JdbcTemplate jdbc) {
        this.currentUser = currentUser;
        this.orders = orders;
        this.excelExporter = excelExporter;
        this.complaintService = complaintService;
        this.jdbc = jdbc;
    }

>>>>>>> develop
    @GetMapping("/shopowner/orders")
    public String orders(@RequestParam(value = "filter", required = false) String filter,
                         HttpServletRequest request,
                         Model model) {
        Long ownerId = currentUser.requireUserId(request);

        model.addAttribute("orders", orders.list(ownerId, filter));
        model.addAttribute("filter", filter != null ? filter : "all");

        Map<String, Object> complaintCounts = orders.countComplaints(ownerId);
        long pendingComplaintsCount = ((Number) complaintCounts
                .getOrDefault("pending_shop_complaints", 0)).longValue();
        long totalComplaintsCount = ((Number) complaintCounts
                .getOrDefault("total_complaints", 0)).longValue();

        model.addAttribute("pendingComplaintsCount", pendingComplaintsCount);
        model.addAttribute("totalComplaintsCount", totalComplaintsCount);

        HttpSession session = request.getSession(true);
        model.addAttribute("ownerName", session.getAttribute("fullName"));
        model.addAttribute("token", getOrCreateShopOrderToken(session));

        return "shopowner/orders";
    }

    @GetMapping("/shopowner/orders/export.xlsx")
    public ResponseEntity<byte[]> export(HttpServletRequest request) {
        Long ownerId = currentUser.requireUserId(request);
        byte[] workbook = excelExporter.export(orders.exportRows(ownerId));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=shop-orders.xlsx")
                .body(workbook);
    }

    @GetMapping("/shopowner/orders/{id}")
    public String detail(@PathVariable Long id,
                         HttpServletRequest request,
                         Model model) {
        Long ownerId = currentUser.requireUserId(request);

        model.addAttribute("order", orders.detail(ownerId, id));
        model.addAttribute("items", orders.items(ownerId, id));
        model.addAttribute("orderComplaints", complaintService.listComplaintsByOrder(id));

        HttpSession session = request.getSession(true);
        model.addAttribute("token", getOrCreateShopOrderToken(session));

        return "shopowner/order-detail";
    }

<<<<<<< HEAD
    @org.springframework.web.bind.annotation.PostMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/respond")
    public String respondComplaint(@org.springframework.web.bind.annotation.PathVariable Long orderId,
                                   @org.springframework.web.bind.annotation.PathVariable Long complaintId,
                                   @RequestParam(defaultValue = "REJECT") String decision,
=======
    /**
     * Đảm bảo khiếu nại đang thao tác thuộc đúng đơn hàng và shop của owner hiện tại.
     */
    private Map<String, Object> verifyComplaintBelongsToOrderAndShop(
            Long ownerId, Long orderId, Long complaintId) {

        Map<String, Object> order = orders.detail(ownerId, orderId);
        Object shopIdValue = order.get("shop_id");
        if (!(shopIdValue instanceof Number shopNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy cửa hàng của đơn hàng.");
        }
        Long shopId = shopNumber.longValue();

        Map<String, Object> complaint = complaintService.getComplaintDetail(complaintId);
        if (complaint == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khiếu nại.");
        }

        Long complaintOrderId = complaint.get("order_id") instanceof Number n
                ? n.longValue() : null;
        Long complaintShopId = complaint.get("shop_id") instanceof Number n
                ? n.longValue() : null;

        if (!Objects.equals(orderId, complaintOrderId)
                || !Objects.equals(shopId, complaintShopId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Khiếu nại không thuộc đơn hàng hoặc cửa hàng của bạn.");
        }

        return complaint;
    }

    @GetMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/resend")
    public String resendOrderPage(@PathVariable Long orderId,
                                  @PathVariable Long complaintId,
                                  HttpServletRequest request,
                                  Model model) {
        Long ownerId = currentUser.requireUserId(request);
        Map<String, Object> order = orders.detail(ownerId, orderId);
        Long shopId = ((Number) order.get("shop_id")).longValue();
        Map<String, Object> complaint = verifyComplaintBelongsToOrderAndShop(
                ownerId, orderId, complaintId);

        model.addAttribute("order", order);
        model.addAttribute("orderItems", orders.items(ownerId, orderId));
        model.addAttribute("complaint", complaint);
        model.addAttribute("shopVariants", complaintService.getAvailableShopVariants(shopId));

        return "shopowner/complaint-resend-order";
    }

    @PostMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/resend")
    public String processResendOrder(@PathVariable Long orderId,
                                     @PathVariable Long complaintId,
                                     @RequestParam(name = "variantIds", required = false) List<Long> variantIds,
                                     @RequestParam(name = "quantities", required = false) List<Integer> quantities,
                                     @RequestParam(required = false) String shopResponse,
                                     HttpServletRequest request,
                                     RedirectAttributes redirect) {
        Long ownerId = currentUser.requireUserId(request);
        verifyComplaintBelongsToOrderAndShop(ownerId, orderId, complaintId);

        try {
            Long replacementOrderId = complaintService.submitShopDecisionResendCustom(
                    ownerId, complaintId, variantIds, quantities, shopResponse);
            redirect.addFlashAttribute(
                    "message",
                    "Đã tạo đơn giao bù 0đ thành công và chuyển sang quy trình đóng gói!");
            return "redirect:/shopowner/orders/" + replacementOrderId;
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/shopowner/orders/" + orderId
                    + "/complaints/" + complaintId + "/resend";
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
        Long ownerId = currentUser.requireUserId(request);
        Map<String, Object> order = orders.detail(ownerId, orderId);
        Map<String, Object> complaint = verifyComplaintBelongsToOrderAndShop(
                ownerId, orderId, complaintId);

        model.addAttribute("order", order);
        model.addAttribute("complaint", complaint);

        String randomSuffix = UUID.randomUUID().toString()
                .substring(0, 6).toUpperCase(Locale.ROOT);
        String suggestedCode = "VC-KN-" + randomSuffix;
        long suggestedDiscount = 50_000L;

        if (complaint.get("actual_paid_amount") instanceof Number number
                && number.longValue() > 0) {
            suggestedDiscount = number.longValue();
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
                                      RedirectAttributes redirect) {
        Long ownerId = currentUser.requireUserId(request);
        verifyComplaintBelongsToOrderAndShop(ownerId, orderId, complaintId);

        try {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            int days = validDays != null && validDays > 0 ? validDays : 30;
            java.time.LocalDateTime end = now.plusDays(days);

            complaintService.submitShopDecisionIssueVoucherCustom(
                    ownerId, complaintId, voucherCode, voucherName, discountType,
                    discountValue, maxDiscountAmount, minOrderValue,
                    now, end, shopResponse);

            redirect.addFlashAttribute(
                    "message",
                    "Đã phát hành voucher đền bù thành công và lưu trực tiếp vào ví của khách hàng!");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/shopowner/orders/" + orderId
                    + "/complaints/" + complaintId + "/voucher";
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi phát hành voucher: " + ex.getMessage());
        }

        return "redirect:/shopowner/orders/" + orderId;
    }

    @PostMapping("/shopowner/orders/{orderId}/complaints/{complaintId}/respond")
    public String respondComplaint(@PathVariable Long orderId,
                                   @PathVariable Long complaintId,
                                   @RequestParam(defaultValue = "RESEND_ITEMS") String decision,
>>>>>>> develop
                                   @RequestParam(required = false) String shopResponse,
                                   @RequestParam(required = false) org.springframework.web.multipart.MultipartFile shopEvidenceImage,
                                   HttpServletRequest request,
<<<<<<< HEAD
                                   org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        Long owner = currentUser.requireUserId(request);
        try {
            complaintService.submitShopDecision(owner, complaintId, decision, shopResponse, shopEvidenceImage);
            if ("ACCEPT".equalsIgnoreCase(decision)) {
                redirect.addFlashAttribute("message", "Đã đồng ý giải quyết khiếu nại và tự động hoàn trả điểm cho khách hàng!");
=======
                                   RedirectAttributes redirect) {
        Long ownerId = currentUser.requireUserId(request);
        verifyComplaintBelongsToOrderAndShop(ownerId, orderId, complaintId);

        try {
            complaintService.submitShopDecision(
                    ownerId, complaintId, decision, shopResponse,
                    shopEvidenceImage, voucherDiscount);

            if ("RESEND_ITEMS".equalsIgnoreCase(decision)) {
                redirect.addFlashAttribute(
                        "message",
                        "Đã chấp thuận khiếu nại và tự động tạo đơn hàng giao bù 0đ cho khách hàng!");
            } else if ("ISSUE_VOUCHER".equalsIgnoreCase(decision)) {
                redirect.addFlashAttribute(
                        "message",
                        "Đã chấp thuận khiếu nại và tặng voucher đền bù vào ví của khách hàng!");
>>>>>>> develop
            } else {
                redirect.addFlashAttribute("message", "Đã gửi giải trình từ chối khiếu nại và chuyển hồ sơ lên CSKH phân xử.");
            }
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            String message = ex.getMessage() != null
                    ? ex.getMessage() : "Vui lòng kiểm tra lại.";
            redirect.addFlashAttribute("error", "Đã xảy ra lỗi khi xử lý khiếu nại: " + message);
        }

        return "redirect:/shopowner/orders/" + orderId;
    }

    @PostMapping("/shopowner/orders/{id}/confirm")
    public String confirm(@PathVariable Long id,
                          @RequestParam(required = false) String token,
                          HttpServletRequest request,
                          RedirectAttributes redirect) {
        Long ownerId = currentUser.requireUserId(request);
        verifyShopOrderToken(request, token);

        try {
            orders.confirmOrder(ownerId, id);
            redirect.addFlashAttribute("message", "Đã xác nhận đơn hàng và trừ tồn kho thành công.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/shopowner/orders/" + id;
    }

    @PostMapping("/shopowner/orders/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String token,
                         @RequestParam String reason,
                         HttpServletRequest request,
                         RedirectAttributes redirect) {
        Long ownerId = currentUser.requireUserId(request);
        verifyShopOrderToken(request, token);

        try {
            orders.rejectOrder(ownerId, id, reason);
            redirect.addFlashAttribute("message", "Đã từ chối đơn hàng thành công.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/shopowner/orders/" + id;
    }

    /**
     * Shop bàn giao đơn đã xác nhận cho bộ phận vận chuyển.
     * Giữ trạng thái PREPARING/READY theo check constraint mà code hiện tại của dự án đang dùng.
     */
    @PostMapping("/shopowner/orders/{id}/handover")
    public String handoverOrder(@PathVariable Long id,
                                @RequestParam(required = false) String token,
                                HttpServletRequest request,
                                RedirectAttributes redirect) {
        Long ownerId = currentUser.requireUserId(request);
        verifyShopOrderToken(request, token);

        try {
            Integer count = jdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM orders o
                    WHERE o.id = ?
                      AND EXISTS (
                          SELECT 1
                          FROM shops s
                          WHERE s.id = o.shop_id AND s.owner_id = ?
                      )
                    """,
                    Integer.class,
                    id,
                    ownerId
            );

            if (count == null || count == 0) {
                throw new IllegalArgumentException(
                        "Đơn hàng không tồn tại hoặc không thuộc quyền quản lý của bạn.");
            }

            int updatedRows = jdbc.update(
                    """
                    UPDATE orders
                    SET status = 'PREPARING',
                        delivery_status = 'READY',
                        delivery_updated_at = NOW(),
                        updated_at = NOW()
                    WHERE id = ?
                      AND status IN ('CONFIRMED', 'PENDING')
                    """,
                    id
            );

            if (updatedRows > 0) {
                redirect.addFlashAttribute(
                        "message",
                        "Đã đóng gói và bàn giao đơn hàng cho Shipper thành công!");
            } else {
                redirect.addFlashAttribute(
                        "error",
                        "Đơn hàng chưa được xác nhận hoặc đã được bàn giao trước đó.");
            }
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            redirect.addFlashAttribute(
                    "error", "Lỗi khi bàn giao đơn hàng: " + ex.getMessage());
        }

        return "redirect:/shopowner/orders/" + id;
    }

    private String getOrCreateShopOrderToken(HttpSession session) {
        Object existing = session.getAttribute(SESSION_TOKEN_ATTRIBUTE);
        if (existing instanceof String token && !token.isBlank()) {
            return token;
        }
        String token = UUID.randomUUID().toString();
        session.setAttribute(SESSION_TOKEN_ATTRIBUTE, token);
        return token;
    }

    private void verifyShopOrderToken(HttpServletRequest request, String token) {
        HttpSession session = request.getSession(false);
        Object expected = session == null ? null : session.getAttribute(SESSION_TOKEN_ATTRIBUTE);
        if (!(expected instanceof String expectedToken)
                || token == null
                || !expectedToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
