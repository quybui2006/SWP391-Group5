package controller.customer;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.customer.CustomerAccountService;
import service.customer.NotificationService;
import service.customer.OrderCheckoutService;
import service.complaint.ComplaintService;
import java.util.Map;
import dto.customer.ChangePasswordRequest;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;


@Controller
@RequestMapping("/customer/account")
public class CustomerAccountHomeController {
    private final CustomerAccountService account;
    private final NotificationService notificationService;
    private final ComplaintService complaintService;
    private final OrderCheckoutService checkout;

    public CustomerAccountHomeController(CustomerAccountService account, NotificationService notificationService, ComplaintService complaintService, OrderCheckoutService checkout) {
        this.account = account;
        this.notificationService = notificationService;
        this.complaintService = complaintService;
        this.checkout = checkout;
    }

    private Long current(HttpSession s) {
        if (!"CUSTOMER".equals(s.getAttribute("role"))) return null;
        Object id = s.getAttribute("userId");
        return id instanceof Number n ? n.longValue() : null;
    }

    private String login(Long id, String path) {
        return id == null ? "redirect:/login?continueTo=" + path : null;
    }

    private Long requireCustomer(HttpSession session) {
        Long id = current(session);
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        return id;
    }

    @GetMapping({"", "/"})
    public String account(Model model, HttpSession session) {
        Long id = current(session);
        String redirect = login(id, "/customer/account");
        if (redirect != null) return redirect;
        model.addAttribute("profile", account.profile(id));
        model.addAttribute("activePage", "profile");
        return "customer/account";
    }

    @PostMapping("/profile")
    public String update(@RequestParam String fullName, @RequestParam(required = false) String phone,
                         @RequestParam(required = false) String gender, @RequestParam(required = false) String birthDate,
                         @RequestParam(required = false) org.springframework.web.multipart.MultipartFile avatar,
                         HttpSession session, RedirectAttributes ra) {
        Long id = current(session);
        String redirect = login(id, "/customer/account");
        if (redirect != null) return redirect;
        try {
            account.saveProfile(id, fullName, phone, gender, birthDate, avatar);
            session.setAttribute("fullName", fullName.trim());
            ra.addFlashAttribute("message", "Đã cập nhật thông tin cá nhân.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/account";
    }

    @GetMapping("/avatar")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> avatar(HttpSession session) {
        Long id = current(session);
        if (id == null) return org.springframework.http.ResponseEntity.status(401).build();
        var image = account.avatar(id);
        if (image == null) return org.springframework.http.ResponseEntity.notFound().build();
        return org.springframework.http.ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .contentType(org.springframework.http.MediaType.parseMediaType((String) image.get("avatar_type")))
            .body((byte[]) image.get("avatar"));
    }

    @GetMapping("/orders")
    public String orders(@RequestParam(value = "status", defaultValue = "all") String status,
                         Model model, HttpSession session) {
        Long id = current(session);
        String r = login(id, "/customer/account/orders");
        if (r != null) return r;
        if (!java.util.Set.of("all", "pending-payment", "shipping", "completed", "cancelled").contains(status))
            status = "all";
        model.addAttribute("profile", account.profile(id));
        model.addAttribute("orders", account.orders(id, status));
        model.addAttribute("statusCounts", account.orderStatusCounts(id));
        model.addAttribute("currentStatus", status);
        model.addAttribute("activePage", "orders");
        return "customer/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable Long id, Model model, HttpSession session) {
        Long customerId = current(session);
        String r = login(customerId, "/customer/account/orders/" + id);
        if (r != null) return r;
        model.addAttribute("profile", account.profile(customerId));
        model.addAttribute("order", account.orderDetail(customerId, id));
        var orderComplaints = complaintService.listComplaintsByOrder(id);
        boolean hasActiveComplaint = orderComplaints.stream()
                .anyMatch(c -> "PENDING_SHOP".equals(c.get("status")) || "ESCALATED".equals(c.get("status")) || "PENDING".equals(c.get("status")) || "IN_PROGRESS".equals(c.get("status")));
        model.addAttribute("orderComplaints", orderComplaints);
        model.addAttribute("hasActiveComplaint", hasActiveComplaint);
        Map<String, Object> eligibility = complaintService.getOrderComplaintEligibility(customerId, id);
        model.addAttribute("eligibility", eligibility);
        model.addAttribute("reasonLabels", eligibility.get("allowedReasons") != null ? eligibility.get("allowedReasons") : complaintService.getReasonLabels());
        model.addAttribute("activePage", "orders");
        return "customer/order-detail";
    }

    @PostMapping("/orders/{id}/complete")
    public String completeOrder(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        Long customerId = current(session);
        String redirect = login(customerId, "/customer/account/orders/" + id);
        if (redirect != null) return redirect;
        try {
            checkout.completeOrderByCustomer(customerId, id);
            ra.addFlashAttribute("message", "Đã xác nhận hoàn tất đơn hàng thành công!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/account/orders/" + id;
    }

    @GetMapping("/favorites")
    public String favorites(Model model, HttpSession session) {
        Long id = current(session);
        String r = login(id, "/customer/account/favorites");
        if (r != null) return r;
        model.addAttribute("profile", account.profile(id));
        model.addAttribute("favorites", account.favorites(id));
        model.addAttribute("activePage", "favorites");
        return "customer/favorites";
    }

    @PostMapping("/favorites/toggle")
    public String toggle(@RequestParam Long variantId, @RequestParam(defaultValue = "/customer/account/favorites") String redirect, HttpSession session) {
        Long id = current(session);
        if (id == null) return "redirect:/login";
        account.toggleFavorite(id, variantId);
        return "redirect:" + (redirect.startsWith("/") ? redirect : "/customer/account/favorites");
    }

    @GetMapping("/favorites/ids")
    @ResponseBody
    public java.util.List<Long> favoriteIds(HttpSession session) {
        return account.favoriteIds(requireCustomer(session));
    }

    @PostMapping("/favorites/save")
    @ResponseBody
    public Map<String, Boolean> saveFavorite(@RequestParam Long variantId, @RequestParam boolean favorite, HttpSession session) {
        return Map.of("favorite", account.setFavorite(requireCustomer(session), variantId, favorite));
    }

    @GetMapping("/wallet/coins")
    public String coins(@RequestParam(defaultValue="all") String filter, Model model, HttpSession session) {
        Long id = requireCustomer(session);
        if (!java.util.Set.of("all","received","used").contains(filter)) filter = "all";
        model.addAttribute("profile", account.profile(id)); model.addAttribute("coinBalance", account.coinBalance(id));
        model.addAttribute("coinHistory", account.coinHistory(id, filter)); model.addAttribute("coinFilter", filter); model.addAttribute("activePage", "wallet-coins");
        return "customer/wallet-coins";
    }

    @GetMapping("/wallet/money")
    public String money(Model model, HttpSession session) {
        Long id = requireCustomer(session); model.addAttribute("profile", account.profile(id)); model.addAttribute("wallet", account.wallet(id)); model.addAttribute("activePage", "wallet-money"); return "customer/wallet-money";
    }

    @GetMapping("/wallet/vouchers")
    public String vouchers(Model model, HttpSession session) {
        Long id = requireCustomer(session); model.addAttribute("profile", account.profile(id)); model.addAttribute("savedVouchers", account.savedVouchers(id)); model.addAttribute("activePage", "wallet-vouchers"); return "customer/wallet-vouchers";
    }

    @PostMapping("/wallet/vouchers/save")
    @ResponseBody
    public ResponseEntity<Map<String,Object>> saveVoucher(@RequestParam Long voucherId, HttpSession session) {
        try {
            account.saveVoucher(requireCustomer(session), voucherId);
            return ResponseEntity.ok(Map.of("saved", true));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("saved", false, "message", "Vui lòng đăng nhập để lưu voucher."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("saved", false, "message", ex.getMessage()));
        }
    }


    @GetMapping("/notifications")
    public String notifications(Model model, HttpSession session) {
        Long id = current(session);
        String r = login(id, "/customer/account/notifications");
        if (r != null) return r;
        notificationService.ensureSampleNotificationsIfEmpty(id);
        model.addAttribute("profile", account.profile(id));
        model.addAttribute("notifications", notificationService.getAllNotifications(id));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(id));
        model.addAttribute("activePage", "notifications");
        return "customer/notifications";
    }

    @GetMapping("/change-password")
    public String changePasswordPage(Model model, HttpSession session) {
        Long id = current(session);
        String redirect = login(id, "/customer/account/change-password");
        if (redirect != null) return redirect;

        model.addAttribute("profile", account.profile(id));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ChangePasswordRequest());
        }
        model.addAttribute("activePage", "changePassword");
        return "customer/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute("form") ChangePasswordRequest form,
                                 BindingResult errors,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes ra) {
        Long id = current(session);
        String redirect = login(id, "/customer/account/change-password");
        if (redirect != null) return redirect;

        if (form.getNewPassword() != null && !form.getNewPassword().equals(form.getConfirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "Mật khẩu xác nhận không khớp.");
        }

        if (errors.hasErrors()) {
            model.addAttribute("profile", account.profile(id));
            model.addAttribute("activePage", "changePassword");
            return "customer/change-password";
        }

        try {
            account.changePassword(id, form.getOldPassword(), form.getNewPassword());
            ra.addFlashAttribute("message", "Đổi mật khẩu thành công!");
            return "redirect:/customer/account/change-password";
        } catch (IllegalArgumentException e) {
            model.addAttribute("profile", account.profile(id));
            model.addAttribute("activePage", "changePassword");
            model.addAttribute("error", e.getMessage());
            return "customer/change-password";
        }
    }
}
