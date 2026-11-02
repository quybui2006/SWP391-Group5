package controller.shop;

import dto.customer.ShopPaymentSetupDto;
import entity.Shop;
import entity.ShopBankAccount;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.ShopBankAccountRepository;
import repository.ShopRepository;
import service.customer.CurrentUserProvider;
import service.customer.NotificationService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class ShopOwnerPaymentVerificationController {

    private final ShopRepository shopRepository;
    private final ShopBankAccountRepository shopBankAccountRepository;
    private final CurrentUserProvider currentUser;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public ShopOwnerPaymentVerificationController(
            ShopRepository shopRepository,
            ShopBankAccountRepository shopBankAccountRepository,
            CurrentUserProvider currentUser,
            JdbcTemplate jdbc,
            NotificationService notifications) {
        this.shopRepository = shopRepository;
        this.shopBankAccountRepository = shopBankAccountRepository;
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

        // Fetch bank accounts or seed from shop default
        List<ShopBankAccount> bankAccounts = shopBankAccountRepository.findByShopIdOrderByIsDefaultDescIdDesc(shop.getId());
        if (bankAccounts.isEmpty() && shop.getBankAccountNumber() != null && !shop.getBankAccountNumber().isBlank()) {
            String seedStatus = "APPROVED".equals(shop.getPaymentApprovalStatus()) ? "APPROVED" : "PENDING";
            ShopBankAccount defaultAcc = new ShopBankAccount(
                    shop.getId(),
                    shop.getBankName(),
                    shop.getBankAccountNumber(),
                    shop.getBankAccountName() != null ? shop.getBankAccountName() : shop.getIdCardName(),
                    shop.getBankBranch(),
                    true,
                    seedStatus
            );
            shopBankAccountRepository.save(defaultAcc);
            bankAccounts = shopBankAccountRepository.findByShopIdOrderByIsDefaultDescIdDesc(shop.getId());
        }

        // BẢO ĐẢM NGUYÊN TẮC 1: MỖI SỐ TÀI KHOẢN CHỈ CÓ DUY NHẤT 1 THẺ (TỰ ĐỘNG DỌN DẸP DỮ LIỆU TRÙNG LẶP)
        if (!bankAccounts.isEmpty()) {
            Map<String, ShopBankAccount> uniqueByStk = new LinkedHashMap<>();
            List<ShopBankAccount> duplicates = new ArrayList<>();
            for (ShopBankAccount acc : bankAccounts) {
                if (acc.getApprovalStatus() == null) {
                    acc.setApprovalStatus(acc.isDefault() && "APPROVED".equals(shop.getPaymentApprovalStatus()) ? "APPROVED" : "PENDING");
                }
                String stk = acc.getBankAccountNumber() != null ? acc.getBankAccountNumber().trim() : "";
                if (!uniqueByStk.containsKey(stk)) {
                    uniqueByStk.put(stk, acc);
                } else {
                    // Trùng STK -> giữ lại tài khoản mặc định / đã duyệt, xóa tài khoản thừa
                    ShopBankAccount existing = uniqueByStk.get(stk);
                    if (acc.isDefault() && !existing.isDefault()) {
                        duplicates.add(existing);
                        uniqueByStk.put(stk, acc);
                    } else {
                        duplicates.add(acc);
                    }
                }
            }
            if (!duplicates.isEmpty()) {
                shopBankAccountRepository.deleteAll(duplicates);
                bankAccounts = new ArrayList<>(uniqueByStk.values());
            }
        }

        // BẢO ĐẢM NGUYÊN TẮC 2: CHỈ CÓ DUY NHẤT 1 TÀI KHOẢN MẶC ĐỊNH
        if (!bankAccounts.isEmpty()) {
            long defaultCount = bankAccounts.stream().filter(ShopBankAccount::isDefault).count();
            if (defaultCount != 1) {
                // Ưu tiên tài khoản đã được APPROVED, nếu chưa có thì chọn tài khoản đầu tiên
                ShopBankAccount chosenDefault = bankAccounts.stream()
                        .filter(a -> "APPROVED".equals(a.getApprovalStatus()))
                        .findFirst()
                        .orElse(bankAccounts.get(0));
                for (ShopBankAccount acc : bankAccounts) {
                    acc.setDefault(acc.getId().equals(chosenDefault.getId()));
                }
                shopBankAccountRepository.saveAll(bankAccounts);
                bankAccounts = shopBankAccountRepository.findByShopIdOrderByIsDefaultDescIdDesc(shop.getId());
            }

            // Nếu có bất kỳ tài khoản nào đang PENDING, hệ thống BÁO LẠI LÀ CHỜ DUYỆT
            boolean hasPending = bankAccounts.stream().anyMatch(a -> "PENDING".equals(a.getApprovalStatus()));
            if (hasPending) {
                shop.setPaymentApprovalStatus("PENDING");
                bankAccounts.stream()
                        .filter(a -> "PENDING".equals(a.getApprovalStatus()))
                        .findFirst()
                        .ifPresent(p -> {
                            shop.setBankName(p.getBankName());
                            shop.setBankAccountNumber(p.getBankAccountNumber());
                            shop.setBankAccountName(p.getBankAccountName());
                            shop.setBankBranch(p.getBankBranch());
                        });
                shopRepository.save(shop);
            } else {
                // Đồng bộ tài khoản mặc định duy nhất vào bảng shops
                ShopBankAccount defaultAcc = bankAccounts.stream()
                        .filter(ShopBankAccount::isDefault)
                        .findFirst()
                        .orElse(bankAccounts.get(0));
                shop.setBankName(defaultAcc.getBankName());
                shop.setBankAccountNumber(defaultAcc.getBankAccountNumber());
                shop.setBankAccountName(defaultAcc.getBankAccountName());
                shop.setBankBranch(defaultAcc.getBankBranch());
                shopRepository.save(shop);
            }
        }

        if (!model.containsAttribute("form")) {
            ShopPaymentSetupDto form = new ShopPaymentSetupDto();
            form.setBankAccountName(shop.getIdCardName());
            form.setDefault(false);
            model.addAttribute("form", form);
        }

        List<String> existingStks = bankAccounts.stream()
                .map(ShopBankAccount::getBankAccountNumber)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toList());
        model.addAttribute("existingStks", existingStks);

        model.addAttribute("shop", shop);
        model.addAttribute("bankAccounts", bankAccounts);
        model.addAttribute("hasAccounts", !bankAccounts.isEmpty());
        model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
        model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
        model.addAttribute("activePage", "payment-verification");

        return "shopowner/payment-verification";
    }

    @PostMapping({"/shopowner/payment-verification", "/shopowner/payment-verification/add"})
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

        // Validate STK: Chỉ gồm chữ số, độ dài 6-30 và không được trùng với các tài khoản đã có
        String cleanStk = form.getBankAccountNumber() != null ? form.getBankAccountNumber().trim() : "";
        if (!cleanStk.isBlank()) {
            if (!cleanStk.matches("^[0-9]{6,30}$")) {
                errors.rejectValue("bankAccountNumber", "pattern",
                        "Số tài khoản ngân hàng chỉ được bao gồm các chữ số (độ dài từ 6 đến 30 số).");
            } else if (shopBankAccountRepository.existsByShopIdAndBankAccountNumber(shop.getId(), cleanStk)) {
                errors.rejectValue("bankAccountNumber", "duplicate",
                        "Số tài khoản ngân hàng này (" + cleanStk + ") đã có trong danh sách của Shop. Mỗi số tài khoản chỉ tạo 1 thẻ, không được nhập thông tin chuyển khoản trùng nhau.");
            }
        }

        List<ShopBankAccount> bankAccounts = shopBankAccountRepository.findByShopIdOrderByIsDefaultDescIdDesc(shop.getId());
        List<String> existingStks = bankAccounts.stream()
                .map(ShopBankAccount::getBankAccountNumber)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toList());

        if (errors.hasErrors()) {
            HttpSession session = request.getSession(false);
            model.addAttribute("ownerName", session != null ? session.getAttribute("fullName") : null);
            model.addAttribute("shop", shop);
            model.addAttribute("bankAccounts", bankAccounts);
            model.addAttribute("hasAccounts", !bankAccounts.isEmpty());
            model.addAttribute("existingStks", existingStks);
            model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
            model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
            model.addAttribute("activePage", "payment-verification");
            return "shopowner/payment-verification";
        }

        try {
            // Khi thêm mới: tài khoản luôn ở trạng thái PENDING (chờ Ban Quản Lý duyệt)
            boolean shouldBeDefault = bankAccounts.isEmpty();

            ShopBankAccount newAccount = new ShopBankAccount(
                    shop.getId(),
                    form.getBankName().trim(),
                    cleanStk,
                    accName,
                    form.getBankBranch() != null ? form.getBankBranch().trim() : "",
                    shouldBeDefault,
                    "PENDING"
            );
            shopBankAccountRepository.save(newAccount);

            // BẤT KỂ LÀ TÀI KHOẢN ĐẦU TIÊN HAY TÀI KHOẢN BỔ SUNG:
            // Hệ thống ĐỀU BÁO LẠI LÀ CHỜ DUYỆT (PENDING) như lần gửi yêu cầu đầu tiên!
            // Đồng bộ thông tin tài khoản đang chờ duyệt này lên bảng shops để Ban Quản Lý thấy ngay tại trang /operations/payments
            shop.setBankName(form.getBankName().trim());
            shop.setBankAccountNumber(cleanStk);
            shop.setBankAccountName(accName);
            shop.setBankBranch(form.getBankBranch() != null ? form.getBankBranch().trim() : "");
            shop.setPaymentApprovalStatus("PENDING");
            shop.setBankRejectionReason(null);
            shopRepository.save(shop);

            // Bắn thông báo đến Operations để thẩm định duyệt tài khoản ngân hàng này
            try {
                HttpSession session = request.getSession(false);
                String ownerName = session != null ? (String) session.getAttribute("fullName") : null;
                notifications.notifyOperationsPaymentVerificationSubmitted(shop.getId(), shop.getName(), ownerName);
            } catch (Exception ignored) {}

            try {
                notifications.createNotification(
                        ownerId,
                        "PAYMENT",
                        "Đã gửi thông tin tài khoản ngân hàng chờ duyệt ⏳",
                        "Thông tin tài khoản ngân hàng (" + form.getBankName() + " - STK: " + cleanStk + ") đang ở trạng thái CHỜ DUYỆT để Ban Quản Lý thẩm định đối soát CCCD.",
                        "PAYMENT_VERIFICATION",
                        shop.getId()
                );
            } catch (Exception ignored) {}

            redirectAttributes.addFlashAttribute("successMessage",
                    "Thêm thông tin tài khoản ngân hàng " + form.getBankName() + " (STK: " + cleanStk + ") thành công! Trạng thái đang là CHỜ DUYỆT để Ban Quản Lý (Operations) thẩm định đối soát.");

            return "redirect:/shopowner/payment-verification";
        } catch (Exception ex) {
            HttpSession session = request.getSession(false);
            model.addAttribute("ownerName", session != null ? session.getAttribute("fullName") : null);
            model.addAttribute("shop", shop);
            model.addAttribute("bankAccounts", bankAccounts);
            model.addAttribute("hasAccounts", !bankAccounts.isEmpty());
            model.addAttribute("existingStks", existingStks);
            model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
            model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
            model.addAttribute("errorMessage", "Đã xảy ra lỗi: " + ex.getMessage());
            model.addAttribute("activePage", "payment-verification");
            return "shopowner/payment-verification";
        }
    }

    @PostMapping("/shopowner/payment-verification/set-default")
    public String setDefaultBankAccount(
            @RequestParam("accountId") Long accountId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        Long ownerId = currentUser.requireUserId(request);
        Shop shop = shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin cửa hàng."));

        List<ShopBankAccount> accounts = shopBankAccountRepository.findByShopIdOrderByIsDefaultDescIdDesc(shop.getId());
        ShopBankAccount target = accounts.stream()
                .filter(a -> a.getId().equals(accountId))
                .findFirst()
                .orElse(null);

        if (target == null) {
            throw new IllegalArgumentException("Tài khoản ngân hàng không tồn tại hoặc không thuộc quyền sở hữu của Shop.");
        }

        if (!target.isApproved()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Chỉ có thể chọn tài khoản ngân hàng ĐÃ ĐƯỢC DUYỆT (APPROVED) làm tài khoản mặc định. Tài khoản "
                            + target.getBankName() + " (" + target.getBankAccountNumber() + ") hiện đang ở trạng thái: "
                            + (target.isPending() ? "CHỜ DUYỆT" : "BỊ TỪ CHỐI") + ".");
            return "redirect:/shopowner/payment-verification";
        }

        for (ShopBankAccount acc : accounts) {
            acc.setDefault(acc.getId().equals(accountId));
        }

        shopBankAccountRepository.saveAll(accounts);

        // Đồng bộ tài khoản mặc định vào bảng shops
        shop.setBankName(target.getBankName());
        shop.setBankAccountNumber(target.getBankAccountNumber());
        shop.setBankAccountName(target.getBankAccountName());
        shop.setBankBranch(target.getBankBranch());
        shopRepository.save(shop);

        redirectAttributes.addFlashAttribute("successMessage",
                "Đã đổi tài khoản " + target.getBankName() + " (" + target.getBankAccountNumber() + ") thành tài khoản mặc định nhận tiền quyết toán!");

        return "redirect:/shopowner/payment-verification";
    }

    @PostMapping("/shopowner/payment-verification/delete")
    public String deleteBankAccount(
            @RequestParam("accountId") Long accountId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        Long ownerId = currentUser.requireUserId(request);
        Shop shop = shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin cửa hàng."));

        ShopBankAccount account = shopBankAccountRepository.findByIdAndShopId(accountId, shop.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản ngân hàng không tồn tại hoặc không thuộc quyền sở hữu của Shop."));

        if (shopBankAccountRepository.countByShopId(shop.getId()) <= 1 && !"REJECTED".equals(account.getApprovalStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa tài khoản ngân hàng duy nhất của cửa hàng.");
            return "redirect:/shopowner/payment-verification";
        }

        boolean wasDefault = account.isDefault();
        shopBankAccountRepository.delete(account);

        List<ShopBankAccount> remaining = shopBankAccountRepository.findByShopIdOrderByIsDefaultDescIdDesc(shop.getId());
        if (!remaining.isEmpty()) {
            if (wasDefault) {
                // Ưu tiên chọn tài khoản đã APPROVED làm mặc định kế tiếp
                ShopBankAccount newDef = remaining.stream()
                        .filter(a -> "APPROVED".equals(a.getApprovalStatus()))
                        .findFirst()
                        .orElse(remaining.get(0));
                for (ShopBankAccount r : remaining) {
                    r.setDefault(r.getId().equals(newDef.getId()));
                }
                shopBankAccountRepository.saveAll(remaining);

                shop.setBankName(newDef.getBankName());
                shop.setBankAccountNumber(newDef.getBankAccountNumber());
                shop.setBankAccountName(newDef.getBankAccountName());
                shop.setBankBranch(newDef.getBankBranch());
                shopRepository.save(shop);
            }
        } else {
            shop.setBankName(null);
            shop.setBankAccountNumber(null);
            shop.setBankAccountName(null);
            shop.setBankBranch(null);
            shop.setPaymentApprovalStatus("UNCONFIGURED");
            shop.setBankRejectionReason(null);
            shopRepository.save(shop);
        }

        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa tài khoản ngân hàng thành công.");
        return "redirect:/shopowner/payment-verification";
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
