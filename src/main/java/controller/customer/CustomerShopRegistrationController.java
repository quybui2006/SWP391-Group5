package controller.customer;

import dto.customer.ShopPaymentSetupDto;
import dto.customer.ShopRegistrationDto;
import entity.Shop;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.customer.CustomerAccountService;
import service.customer.CustomerShopRegistrationService;

import java.util.Map;
import java.util.Optional;

@Controller
public class CustomerShopRegistrationController {

    private final CustomerShopRegistrationService shopRegistrationService;
    private final CustomerAccountService customerAccountService;

    public CustomerShopRegistrationController(
            CustomerShopRegistrationService shopRegistrationService,
            CustomerAccountService customerAccountService) {
        this.shopRegistrationService = shopRegistrationService;
        this.customerAccountService = customerAccountService;
    }

    private Long getUserIdFromSession(HttpSession session) {
        if (session == null) return null;
        Object idObj = session.getAttribute("userId");
        if (idObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    @GetMapping({"/seller/channel", "/seller"})
    public String sellerChannel(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/seller/channel";
        }

        Optional<Shop> existingShopOpt = shopRegistrationService.getShopByOwnerId(userId);
        if (existingShopOpt.isPresent()) {
            Shop shop = existingShopOpt.get();
            if ("APPROVED".equals(shop.getApprovalStatus())) {
                if (!"APPROVED".equals(shop.getPaymentApprovalStatus())) {
                    return "redirect:/customer/shop-registration/payment";
                }
                session.setAttribute("role", "SHOP_OWNER");
                return "redirect:/shopowner/home";
            }
            return "redirect:/customer/shop-registration/status";
        }

        Object role = session.getAttribute("role");
        if ("SHOP_OWNER".equals(role)) {
            return "redirect:/shopowner/home";
        }

        return "redirect:/customer/shop-registration";
    }

    @GetMapping("/customer/shop-registration")
    public String showRegistrationForm(
            @RequestParam(value = "mode", required = false) String mode,
            Model model,
            HttpSession session) {

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/customer/shop-registration";
        }

        Optional<Shop> existingShopOpt = shopRegistrationService.getShopByOwnerId(userId);
        if (existingShopOpt.isPresent() && !"edit".equalsIgnoreCase(mode)) {
            Shop shop = existingShopOpt.get();
            if ("PENDING".equals(shop.getApprovalStatus()) || "APPROVED".equals(shop.getApprovalStatus())) {
                return "redirect:/customer/shop-registration/status";
            }
        }

        if (!model.containsAttribute("form")) {
            ShopRegistrationDto dto = new ShopRegistrationDto();
            if (existingShopOpt.isPresent()) {
                Shop s = existingShopOpt.get();
                dto.setName(s.getName());
                dto.setDescription(s.getDescription());
                dto.setPhone(s.getPhone());
                dto.setProvinceCode(s.getProvinceCode());
                dto.setWardName(s.getWardName());
                dto.setAddressDetail(s.getAddressDetail());
                dto.setIdCardNumber(s.getIdCardNumber());
                dto.setIdCardName(s.getIdCardName());
                dto.setIdCardDate(s.getIdCardDate() != null ? s.getIdCardDate().toString() : null);
                dto.setIdCardPlace(s.getIdCardPlace());
                dto.setBusinessType(s.getBusinessType() != null ? s.getBusinessType() : "INDIVIDUAL");
                dto.setTaxCode(s.getTaxCode());
                dto.setTaxName(s.getTaxName());
                dto.setTaxEmail(s.getTaxEmail());
            } else {
                try {
                    var prof = customerAccountService.profile(userId);
                    if (prof != null) {
                        if (prof.get("phone") != null && !prof.get("phone").toString().isBlank()) {
                            dto.setPhone(prof.get("phone").toString());
                        }
                        if (prof.get("fullName") != null) {
                            dto.setIdCardName(prof.get("fullName").toString());
                        }
                        if (prof.get("email") != null) {
                            dto.setTaxEmail(prof.get("email").toString());
                        }
                    }
                } catch (Exception ignored) {}
            }
            model.addAttribute("form", dto);
        }

        model.addAttribute("provinces", shopRegistrationService.getAllActiveProvinces());
        model.addAttribute("isResubmit", existingShopOpt.isPresent() && "REJECTED".equals(existingShopOpt.get().getApprovalStatus()));
        if (existingShopOpt.isPresent()) {
            model.addAttribute("existingShop", existingShopOpt.get());
        }

        try {
            model.addAttribute("profile", customerAccountService.profile(userId));
        } catch (Exception ignored) {}

        model.addAttribute("activePage", "shopRegistration");
        return "customer/shop-registration";
    }

    @PostMapping("/customer/shop-registration")
    public String submitRegistration(
            @Valid @ModelAttribute("form") ShopRegistrationDto form,
            BindingResult errors,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/customer/shop-registration";
        }

        if (!form.isAgreeTerms()) {
            errors.rejectValue("agreeTerms", "required", "Bạn cần đồng ý với Quy chế hoạt động của FreshFruit.");
        }

        if (errors.hasErrors()) {
            model.addAttribute("provinces", shopRegistrationService.getAllActiveProvinces());
            model.addAttribute("activePage", "shopRegistration");
            try {
                model.addAttribute("profile", customerAccountService.profile(userId));
            } catch (Exception ignored) {}
            return "customer/shop-registration";
        }

        try {
            shopRegistrationService.registerShop(userId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Hồ sơ đăng ký mở Shop đã được gửi thành công! Ban Quản trị sẽ xét duyệt trong thời gian sớm nhất.");
            return "redirect:/customer/shop-registration/status";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("provinces", shopRegistrationService.getAllActiveProvinces());
            model.addAttribute("activePage", "shopRegistration");
            try {
                model.addAttribute("profile", customerAccountService.profile(userId));
            } catch (Exception ignored) {}
            return "customer/shop-registration";
        }
    }

    @GetMapping("/customer/shop-registration/status")
    public String showRegistrationStatus(Model model, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/customer/shop-registration/status";
        }

        Map<String, Object> details = shopRegistrationService.getShopRegistrationStatusDetails(userId);
        if (!(Boolean) details.getOrDefault("hasShop", false)) {
            return "redirect:/customer/shop-registration";
        }

        model.addAllAttributes(details);
        try {
            model.addAttribute("profile", customerAccountService.profile(userId));
        } catch (Exception ignored) {}

        model.addAttribute("activePage", "shopRegistrationStatus");
        return "customer/shop-registration-status";
    }

    @GetMapping("/customer/shop-registration/payment")
    public String showPaymentSetupForm(Model model, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/customer/shop-registration/payment";
        }

        Optional<Shop> shopOpt = shopRegistrationService.getShopByOwnerId(userId);
        if (shopOpt.isEmpty()) {
            return "redirect:/customer/shop-registration";
        }

        Shop shop = shopOpt.get();
        if (!"APPROVED".equals(shop.getApprovalStatus())) {
            return "redirect:/customer/shop-registration/status";
        }

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
        try {
            model.addAttribute("profile", customerAccountService.profile(userId));
        } catch (Exception ignored) {}
        model.addAttribute("activePage", "shopPaymentSetup");
        return "customer/shop-payment-setup";
    }

    @PostMapping("/customer/shop-registration/payment")
    public String submitPaymentSetup(
            @Valid @ModelAttribute("form") ShopPaymentSetupDto form,
            BindingResult errors,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/customer/shop-registration/payment";
        }

        Optional<Shop> shopOpt = shopRegistrationService.getShopByOwnerId(userId);
        if (shopOpt.isEmpty() || !"APPROVED".equals(shopOpt.get().getApprovalStatus())) {
            return "redirect:/customer/shop-registration/status";
        }

        Shop shop = shopOpt.get();

        if (errors.hasErrors()) {
            model.addAttribute("shop", shop);
            model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
            model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
            try {
                model.addAttribute("profile", customerAccountService.profile(userId));
            } catch (Exception ignored) {}
            model.addAttribute("activePage", "shopPaymentSetup");
            return "customer/shop-payment-setup";
        }

        try {
            shopRegistrationService.submitPaymentInfo(userId, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã lưu thông tin thanh toán thành công! Ban vận hành sẽ đối chiếu tên chủ tài khoản với CCCD (" + (shop.getIdCardName() != null ? shop.getIdCardName() : "") + ") và phê duyệt.");
            return "redirect:/customer/shop-registration/payment";
        } catch (Exception ex) {
            model.addAttribute("shop", shop);
            model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
            model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
            model.addAttribute("errorMessage", ex.getMessage());
            try {
                model.addAttribute("profile", customerAccountService.profile(userId));
            } catch (Exception ignored) {}
            model.addAttribute("activePage", "shopPaymentSetup");
            return "customer/shop-payment-setup";
        }
    }

    @GetMapping("/register/shop-owner")
    public String showShopOwnerRegistrationLanding(Model model) {
        model.addAttribute("provinces", shopRegistrationService.getAllActiveProvinces());
        return "login/register-shop-owner";
    }
}
