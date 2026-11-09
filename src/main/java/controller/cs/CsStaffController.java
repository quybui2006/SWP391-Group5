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
    // 1. CS DASHBOARD (Content Moderation & Customer Overview)
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
        model.addAttribute("moderationMetrics", complaintService.getContentModerationMetrics());

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
                        "ORDER BY r.created_at DESC LIMIT 8"
        );
        model.addAttribute("recentLowReviews", recentLowReviews);

        return "cs/dashboard";
    }

    // =========================================================================
    // 2. KIỂM DUYỆT ĐÁNH GIÁ & PHẢN HỒI (CONTENT MODERATION)
    // =========================================================================

    @GetMapping("/reviews")
    public String reviews(
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String modStatus,
            Model model) {
        model.addAttribute("activePage", "reviews");
        model.addAttribute("selectedRating", rating);
        model.addAttribute("selectedModStatus", modStatus);
        model.addAttribute("moderationMetrics", complaintService.getContentModerationMetrics());

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

    // =========================================================================
    // 3. TRA CỨU KHÁCH HÀNG (CUSTOMER SUPPORT LOOKUP)
    // =========================================================================

    @GetMapping("/customers")
    public String customers(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("activePage", "customers");
        model.addAttribute("keyword", keyword);

        String sql = "SELECT u.id, u.full_name, u.email, u.phone, u.status, u.points_balance, u.created_at, " +
                "(SELECT COUNT(*) FROM orders o JOIN checkouts c ON c.id = o.checkout_id WHERE c.user_id = u.id) AS order_count " +
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
