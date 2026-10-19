package controller.cs;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cs")
public class CsStaffController {

    private final JdbcTemplate jdbc;

    public CsStaffController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        model.addAttribute("activePage", "dashboard");

        // Thống kê hỗ trợ khách hàng an toàn
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

        // Đơn hàng bị hủy hoặc có sự cố gần đây cần CSKH can thiệp
        List<Map<String, Object>> disputeOrders = safeQueryList(
                "SELECT o.id, o.order_code, o.recipient_name, o.recipient_phone, o.total_amount, o.status, " +
                        "o.cancellation_reason, o.created_at, s.name AS shop_name " +
                        "FROM orders o JOIN shops s ON s.id = o.shop_id " +
                        "WHERE o.status = 'CANCELLED' OR o.delivery_status = 'FAILED' " +
                        "ORDER BY o.created_at DESC LIMIT 6"
        );
        model.addAttribute("disputeOrders", disputeOrders);

        // Đánh giá kém cần CSKH liên hệ xử lý
        List<Map<String, Object>> recentLowReviews = safeQueryList(
                "SELECT r.id, r.rating, r.content, r.created_at, u.full_name AS customer_name, u.phone AS customer_phone, " +
                        "p.name AS product_name, pv.name AS variant_name " +
                        "FROM reviews r " +
                        "JOIN users u ON u.id = r.user_id " +
                        "JOIN product_variants pv ON pv.id = r.variant_id " +
                        "JOIN products p ON p.id = pv.product_id " +
                        "WHERE r.rating <= 3 " +
                        "ORDER BY r.created_at DESC LIMIT 5"
        );
        model.addAttribute("recentLowReviews", recentLowReviews);

        return "cs/dashboard";
    }

    @GetMapping("/tickets")
    public String tickets(Model model) {
        model.addAttribute("activePage", "tickets");
        return "cs/tickets";
    }

    @GetMapping("/reviews")
    public String reviews(@RequestParam(required = false) Integer rating, Model model) {
        model.addAttribute("activePage", "reviews");
        model.addAttribute("selectedRating", rating);

        String sql = "SELECT r.id, r.rating, r.content, r.created_at, u.full_name AS customer_name, u.email AS customer_email, " +
                "p.name AS product_name, pv.name AS variant_name, pv.image_url " +
                "FROM reviews r " +
                "JOIN users u ON u.id = r.user_id " +
                "JOIN product_variants pv ON pv.id = r.variant_id " +
                "JOIN products p ON p.id = pv.product_id ";

        List<Map<String, Object>> reviews;
        if (rating != null && rating >= 1 && rating <= 5) {
            sql += "WHERE r.rating = ? ORDER BY r.created_at DESC LIMIT 50";
            reviews = safeQueryList(sql, rating);
        } else {
            sql += "ORDER BY r.created_at DESC LIMIT 50";
            reviews = safeQueryList(sql);
        }

        model.addAttribute("reviews", reviews);
        return "cs/reviews";
    }

    @GetMapping("/customers")
    public String customers(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("activePage", "customers");
        model.addAttribute("keyword", keyword);

        String sql = "SELECT u.id, u.full_name, u.email, u.phone, u.status, u.created_at, " +
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
