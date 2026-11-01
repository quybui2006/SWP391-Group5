package controller.shop;

import dto.customer.ShopPaymentSetupDto;
import entity.Shop;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.ShopRepository;
import service.customer.CurrentUserProvider;
import service.customer.NotificationService;

import java.util.Optional;

@Controller
public class ShopOwnerPaymentVerificationController {

    private final ShopRepository shopRepository;
    private final CurrentUserProvider currentUser;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public ShopOwnerPaymentVerificationController(
            ShopRepository shopRepository,
            CurrentUserProvider currentUser,
            JdbcTemplate jdbc,
            NotificationService notifications) {
        this.shopRepository = shopRepository;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    @GetMapping("/shopowner/payment-verification")
    public String showPaymentVerificationForm(
            HttpServletRequest request,
            Model model) {
        Long ownerId = currentUser.requireUserId(request);
        Optional<Shop> shopOpt = shopRepository.findByOwnerId(ownerId);
        if (shopOpt.isEmpty()) {
            return "redirect:/seller/channel";
        }

        Shop shop = shopOpt.get();
        if (!"APPROVED".equals(shop.getApprovalStatus())) {
            return "redirect:/seller/status";
        }

        HttpSession session = request.getSession(false);
        model.addAttribute("ownerName", session != null ? session.getAttribute("fullName") : null);

        if (!model.containsAttribute("form")) {
            ShopPaymentSetupDto form = new ShopPaymentSetupDto();
            if (shop.getBankName() != null && !shop.getBankName().isBlank()) {
                form.setBankName(shop.getBankName());
                form.setBankAccountNumber(shop.getBankAccountNumber());
                form.setBankAccountName(shop.getBankAccountName());
                form.setBankBranch(shop.getBankBranch());
            } else {
                form.setBankAccountName(shop.getIdCardName());
            }
            model.addAttribute("form", form);
        }

        model.addAttribute("shop", shop);
        model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
        model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
        model.addAttribute("activePage", "payment-verification");

        return "shopowner/payment-verification";
    }

    @PostMapping("/shopowner/payment-verification")
    public String submitPaymentVerification(
            @Valid @ModelAttribute("form") ShopPaymentSetupDto form,
            BindingResult errors,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long ownerId = currentUser.requireUserId(request);
        Optional<Shop> shopOpt = shopRepository.findByOwnerId(ownerId);
        if (shopOpt.isEmpty()) {
            return "redirect:/seller/channel";
        }

        Shop shop = shopOpt.get();
        if (!"APPROVED".equals(shop.getApprovalStatus())) {
            return "redirect:/seller/status";
        }

        // Validate đối chiếu: Tên chủ TK phải trùng khớp với Họ tên trên CCCD
        String accName = form.getBankAccountName() != null ? form.getBankAccountName().trim().toUpperCase() : "";
        String cccdName = shop.getIdCardName() != null ? shop.getIdCardName().trim() : "";
        if (!accName.isEmpty() && !cccdName.isEmpty()) {
            String normAcc = removeAccents(accName);
            String normCccd = removeAccents(cccdName);
            if (!normAcc.equals(normCccd)) {
                errors.rejectValue("bankAccountName", "mismatch",
                        "Tên chủ tài khoản không trùng khớp với Họ tên trên CCCD (" + cccdName + "). Vui lòng nhập đúng họ tên chính chủ.");
            }
        }

        // Validate STK: Chỉ gồm chữ số, độ dài 6-30
        if (form.getBankAccountNumber() != null && !form.getBankAccountNumber().trim().isBlank()) {
            String cleanStk = form.getBankAccountNumber().trim();
            if (!cleanStk.matches("^[0-9]{6,30}$")) {
                errors.rejectValue("bankAccountNumber", "pattern",
                        "Số tài khoản ngân hàng chỉ được bao gồm các chữ số (độ dài từ 6 đến 30 số).");
            }
        }

        if (errors.hasErrors()) {
            HttpSession session = request.getSession(false);
            model.addAttribute("ownerName", session != null ? session.getAttribute("fullName") : null);
            model.addAttribute("shop", shop);
            model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
            model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
            model.addAttribute("activePage", "payment-verification");
            return "shopowner/payment-verification";
        }

        try {
            jdbc.update("""
                UPDATE shops
                SET bank_name = ?,
                    bank_account_number = ?,
                    bank_account_name = ?,
                    bank_branch = ?,
                    payment_approval_status = 'PENDING',
                    bank_rejection_reason = NULL,
                    payment_reviewed_at = NULL,
                    updated_at = NOW()
                WHERE id = ?
            """, form.getBankName(), form.getBankAccountNumber().trim(), accName, form.getBankBranch(), shop.getId());

            // Notify shop owner in-web
            try {
                notifications.createNotification(
                        ownerId,
                        "PAYMENT",
                        "Đã thêm thông tin tài khoản ngân hàng ⏳",
                        "Thông tin tài khoản ngân hàng của bạn (" + form.getBankName() + " - STK: " + form.getBankAccountNumber().trim() + ") đã được chuyển tới Ban Quản Lý. Trạng thái cửa hàng sẽ chuyển sang OPEN để mở bán trên sàn ngay khi được phê duyệt.",
                        "PAYMENT_VERIFICATION",
                        shop.getId()
                );
            } catch (Exception ignored) {}

            // Notify Operations managers
            try {
                HttpSession session = request.getSession(false);
                String ownerName = session != null ? (String) session.getAttribute("fullName") : null;
                notifications.notifyOperationsPaymentVerificationSubmitted(shop.getId(), shop.getName(), ownerName);
            } catch (Exception ignored) {}

            redirectAttributes.addFlashAttribute("successMessage",
                    "Thêm thông tin tài khoản ngân hàng thành công! Ban Quản Lý (Operations) sẽ đối chiếu với hồ sơ CCCD ("
                            + (shop.getIdCardName() != null ? shop.getIdCardName() : "") + ") và kích hoạt trạng thái mở bán (OPEN) trên sàn.");

            return "redirect:/shopowner/payment-verification";
        } catch (Exception ex) {
            HttpSession session = request.getSession(false);
            model.addAttribute("ownerName", session != null ? session.getAttribute("fullName") : null);
            model.addAttribute("shop", shop);
            model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
            model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
            model.addAttribute("errorMessage", "Đã xảy ra lỗi: " + ex.getMessage());
            model.addAttribute("activePage", "payment-verification");
            return "shopowner/payment-verification";
        }
    }

    private String removeAccents(String str) {
        if (str == null) return "";
        String normalized = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
                .replace("đ", "d").replace("Đ", "D")
                .replaceAll("\\s+", " ")
                .trim().toLowerCase();
    }
}
