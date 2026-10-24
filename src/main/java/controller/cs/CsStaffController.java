package controller.cs;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.complaint.ComplaintService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cs")
public class CsStaffController {

    private final JdbcTemplate jdbc;
    private final ComplaintService complaintService;

    public CsStaffController(JdbcTemplate jdbc, ComplaintService complaintService) {
        this.jdbc = jdbc;
        this.complaintService = complaintService;
    }

    private Long currentStaffId(HttpSession session) {
        if (session == null) return null;
        Object uid = session.getAttribute("userId");
        return uid instanceof Number n ? n.longValue() : null;
    }

    // =========================================================================
    // 1. OPERATIONS & CS DASHBOARD (Dispute Reports, CS SLA, Moderation, Alerts)
    // =========================================================================

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        model.addAttribute("activePage", "dashboard");

        long totalCustomers = queryCount("SELECT COUNT(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id WHERE r.code = 'CUSTOMER'");
        long totalOrders = queryCount("SELECT COUNT(*) FROM orders");
        long cancelledOrders = queryCount("SELECT COUNT(*) FROM orders WHERE status = 'CANCELLED'");
        long totalReviews = queryCount("SELECT COUNT(*) FROM reviews");
        long lowRatingReviews = queryCount("SELECT COUNT(*) FROM reviews WHERE rating <= 2");

        model.addAttribute("totalCustomers", totalCustomers);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("cancelledOrders", cancelledOrders);
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("lowRatingReviews", lowRatingReviews);

        // 5 nhóm chỉ số Báo cáo Tranh chấp, SLA CSKH, Kiểm duyệt nội dung & Cảnh báo vi phạm
        Map<String, Object> reports = complaintService.getAnalyticsAndReports();
        model.addAllAttributes(reports);

        // Danh sách yêu cầu khiếu nại mới nhất cần xử lý
        List<Map<String, Object>> recentComplaints = complaintService.listCsComplaints("ALL", "ALL", "ALL", false, null);
        if (recentComplaints.size() > 6) {
            recentComplaints = recentComplaints.subList(0, 6);
        }
        model.addAttribute("recentComplaints", recentComplaints);

        // Đơn hàng bị hủy hoặc giao thất bại gần đây
        List<Map<String, Object>> disputeOrders = safeQueryList(
                "SELECT o.id, o.order_code, o.recipient_name, o.recipient_phone, o.total_amount, o.status, o.delivery_status, " +
                        "o.cancellation_reason, o.delivery_failure_reason, o.created_at, s.name AS shop_name " +
                        "FROM orders o JOIN shops s ON s.id = o.shop_id " +
                        "WHERE o.status = 'CANCELLED' OR o.delivery_status = 'FAILED' " +
                        "ORDER BY o.created_at DESC LIMIT 6"
        );
        model.addAttribute("disputeOrders", disputeOrders);

        // Đánh giá kém cần CSKH liên hệ xử lý
        List<Map<String, Object>> recentLowReviews = safeQueryList(
                "SELECT r.id, r.rating, r.content, r.created_at, COALESCE(r.moderation_status, 'PENDING') AS moderation_status, " +
                        "u.full_name AS customer_name, u.phone AS customer_phone, " +
                        "p.name AS product_name, pv.name AS variant_name, s.name AS shop_name " +
                        "FROM reviews r " +
                        "JOIN users u ON u.id = r.user_id " +
                        "JOIN product_variants pv ON pv.id = r.variant_id " +
                        "JOIN products p ON p.id = pv.product_id " +
                        "JOIN shops s ON s.id = pv.shop_id " +
                        "WHERE r.rating <= 3 " +
                        "ORDER BY r.created_at DESC LIMIT 5"
        );
        model.addAttribute("recentLowReviews", recentLowReviews);
        model.addAttribute("reasonLabels", complaintService.getReasonLabels());

        return "cs/dashboard";
    }

    // =========================================================================
    // 2. DANH SÁCH YÊU CẦU KHIẾU NẠI & TẠO PHIẾU HỖ TRỢ
    // =========================================================================

    @GetMapping("/tickets")
    public String tickets(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "ALL") String priority,
            @RequestParam(defaultValue = "ALL") String reasonType,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) String keyword,
            Model model) {

        model.addAttribute("activePage", "tickets");
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("selectedReason", reasonType);
        model.addAttribute("overdueOnly", overdue);
        model.addAttribute("keyword", keyword);

        List<Map<String, Object>> tickets = complaintService.listCsComplaints(status, priority, reasonType, overdue, keyword);
        model.addAttribute("tickets", tickets);

        Map<String, Object> reports = complaintService.getAnalyticsAndReports();
        model.addAllAttributes(reports);
        model.addAttribute("reasonLabels", complaintService.getReasonLabels());
        model.addAttribute("resolutionLabels", complaintService.getResolutionLabels());

        return "cs/tickets";
    }

    @PostMapping("/tickets/create")
    public String createTicket(
            @RequestParam String orderCode,
            @RequestParam(defaultValue = "QUALITY_ISSUE") String reasonType,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "CS_MANUAL") String sourceType,
            HttpSession session,
            RedirectAttributes redirect) {
        try {
            Long ticketId = complaintService.createTicketByCs(
                    currentStaffId(session), orderCode, reasonType, title, description, sourceType);
            redirect.addFlashAttribute("message", "Đã khởi tạo phiếu khiếu nại / hỗ trợ thành công!");
            return "redirect:/cs/tickets/" + ticketId;
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/cs/tickets";
        }
    }

    // =========================================================================
    // 3. XEM CHI TIẾT VÀ XỬ LÝ KHIẾU NẠI
    // =========================================================================

    @GetMapping("/tickets/{id}")
    public String ticketDetail(@PathVariable Long id, Model model) {
        model.addAttribute("activePage", "tickets");
        model.addAttribute("complaint", complaintService.getComplaintDetail(id));
        model.addAttribute("reasonLabels", complaintService.getReasonLabels());
        model.addAttribute("resolutionLabels", complaintService.getResolutionLabels());
        return "cs/ticket-detail";
    }

    @PostMapping("/tickets/{id}/start")
    public String startTicketProcessing(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirect) {
        try {
            complaintService.startProcessing(id, currentStaffId(session));
            redirect.addFlashAttribute("message", "Đã tiếp nhận hồ sơ khiếu nại và chuyển sang trạng thái Đang xử lý.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cs/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/resolve")
    public String resolveTicket(
            @PathVariable Long id,
            @RequestParam String decisionStatus,
            @RequestParam(required = false) String resolutionType,
            @RequestParam String resolutionNote,
            @RequestParam(required = false) Long compensatedPoints,
            @RequestParam(defaultValue = "false") boolean isShopFault,
            @RequestParam(defaultValue = "false") boolean escalateToOps,
            HttpSession session,
            RedirectAttributes redirect) {
        try {
            complaintService.resolveComplaint(
                    id,
                    currentStaffId(session),
                    decisionStatus,
                    resolutionType,
                    resolutionNote,
                    compensatedPoints,
                    isShopFault,
                    escalateToOps);
            redirect.addFlashAttribute("message", "Đã lưu quyết định xử lý khiếu nại và gửi thông báo tới các bên liên quan!");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cs/tickets/" + id;
    }

    @PostMapping("/violations/{shopId}/escalate")
    public String escalateShopViolation(
            @PathVariable Long shopId,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirect) {
        try {
            complaintService.escalateShopToOperations(shopId, reason);
            redirect.addFlashAttribute("message", "Đã gửi Cảnh báo vi phạm sang Ban Vận hành (Operations Manager) để hậu kiểm Shop #" + shopId + ".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cs/dashboard";
    }

    // =========================================================================
    // 4. KIỂM DUYỆT ĐÁNH GIÁ & PHẢN HỒI (CONTENT MODERATION)
    // =========================================================================

    @GetMapping("/reviews")
    public String reviews(
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String modStatus,
            Model model) {
        model.addAttribute("activePage", "reviews");
        model.addAttribute("selectedRating", rating);
        model.addAttribute("selectedModStatus", modStatus);

        Map<String, Object> reports = complaintService.getAnalyticsAndReports();
        model.addAllAttributes(reports);

        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.rating, r.content, r.created_at, u.full_name AS customer_name, u.email AS customer_email, " +
                "p.name AS product_name, pv.name AS variant_name, pv.image_url, s.name AS shop_name, " +
                "COALESCE(r.moderation_status, 'PENDING') AS moderation_status, r.moderation_reason " +
                "FROM reviews r " +
                "JOIN users u ON u.id = r.user_id " +
                "JOIN product_variants pv ON pv.id = r.variant_id " +
                "JOIN products p ON p.id = pv.product_id " +
                "JOIN shops s ON s.id = pv.shop_id " +
                "WHERE 1=1 ");

        List<Object> args = new ArrayList<>();
        if (rating != null && rating >= 1 && rating <= 5) {
            sql.append("AND r.rating = ? ");
            args.add(rating);
        }
        if (modStatus != null && !modStatus.isBlank() && !"ALL".equalsIgnoreCase(modStatus)) {
            sql.append("AND COALESCE(r.moderation_status, 'PENDING') = ? ");
            args.add(modStatus.trim().toUpperCase());
        }
        sql.append("ORDER BY r.created_at DESC LIMIT 50");

        List<Map<String, Object>> reviews = safeQueryList(sql.toString(), args.toArray());
        model.addAttribute("reviews", reviews);
        return "cs/reviews";
    }

    @PostMapping("/reviews/{id}/approve")
    public String approveReview(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
        return updateReviewStatus(id, "APPROVED", session, redirect);
    }

    @PostMapping("/reviews/{id}/reject")
    public String rejectReview(@PathVariable Long id, @RequestParam(required = false) String reason,
                               HttpSession session, RedirectAttributes redirect) {
        return updateReviewStatus(id, "REJECTED", session, redirect,
                reason != null && !reason.isBlank() ? reason : "Vi phạm tiêu chuẩn cộng đồng");
    }

    @PostMapping("/reviews/{id}/remove")
    public String removeReview(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
        try {
            jdbc.update("UPDATE reviews SET moderation_status='REMOVED', moderated_by=?, moderated_at=CURRENT_TIMESTAMP WHERE id=?",
                    currentStaffId(session), id);
            jdbc.update("DELETE FROM reviews WHERE id=?", id);
            redirect.addFlashAttribute("message", "Đã xóa đánh giá #" + id + ".");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Không thể xóa đánh giá: " + e.getMessage());
        }
        return "redirect:/cs/reviews";
    }

    private String updateReviewStatus(Long id, String status, HttpSession session, RedirectAttributes redirect, String reason) {
        try {
            jdbc.update("UPDATE reviews SET moderation_status=?, moderation_reason=?, moderated_by=?, moderated_at=CURRENT_TIMESTAMP WHERE id=?",
                    status, reason, currentStaffId(session), id);
            String vnStatus = "APPROVED".equals(status) ? "duyệt hiển thị" : "từ chối / ẩn";
            redirect.addFlashAttribute("message", "Đã " + vnStatus + " đánh giá #" + id + ".");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Không thể cập nhật đánh giá: " + e.getMessage());
        }
        return "redirect:/cs/reviews";
    }

    private String updateReviewStatus(Long id, String status, HttpSession session, RedirectAttributes redirect) {
        return updateReviewStatus(id, status, session, redirect, null);
    }

    @GetMapping("/customers")
    public String customers(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("activePage", "customers");
        model.addAttribute("keyword", keyword);

        String sql = "SELECT u.id, u.full_name, u.email, u.phone, u.status, u.points_balance, u.created_at, " +
                "(SELECT COUNT(*) FROM orders o JOIN checkouts c ON c.id = o.checkout_id WHERE c.user_id = u.id) AS order_count, " +
                "(SELECT COUNT(*) FROM complaints cp WHERE cp.user_id = u.id) AS complaint_count " +
                "FROM users u " +
                "JOIN user_roles ur ON ur.user_id = u.id " +
                "JOIN roles r ON r.id = ur.role_id " +
                "WHERE r.code = 'CUSTOMER' ";

        List<Map<String, Object>> customers;
        if (keyword != null && !keyword.isBlank()) {
            sql += "AND (u.full_name LIKE ? OR u.email LIKE ? OR u.phone LIKE ?) ORDER BY u.id DESC LIMIT 50";
            String kw = "%" + keyword.trim() + "%";
            customers = safeQueryList(sql, kw, kw, kw);
        } else {
            sql += "ORDER BY u.id DESC LIMIT 50";
            customers = safeQueryList(sql);
        }

        model.addAttribute("customers", customers);
        return "cs/customers";
    }

    private long queryCount(String sql) {
        try {
            Long count = jdbc.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<Map<String, Object>> safeQueryList(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
