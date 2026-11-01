package controller.customer;

import dto.customer.ShopPaymentSetupDto;
import dto.customer.ShopRegistrationDto;
import dto.seller.SellerAccountActivationDto;
import dto.seller.SellerCreateShopDto;
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

    public static final String SESSION_ACTIVATION_KEY = "sellerActivation";

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

    /**
     * Entry Point: Kênh Bán Hàng
     * Luồng phân phối theo Shopee:
     * 1. Chưa đăng nhập -> Login.
     * 2. Đã có Shop:
     *    - Đã duyệt Shop & Thanh toán -> Shop Owner Home
     *    - Đã duyệt Shop nhưng chưa duyệt Thanh toán -> /seller/payment
     *    - Chờ duyệt hoặc Bị từ chối -> /seller/status
     * 3. Chưa có Shop:
     *    - Đã xong bước kích hoạt (có trong session) -> /seller/create-shop
     *    - Chưa xong bước kích hoạt -> /seller/activate
     */
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
                    return "redirect:/seller/payment";
                }
                session.setAttribute("role", "SHOP_OWNER");
                return "redirect:/shopowner/home";
            }
            return "redirect:/seller/status";
        }

        if (session.getAttribute(SESSION_ACTIVATION_KEY) != null
                || shopRegistrationService.getSellerActivation(userId).isPresent()) {
            return "redirect:/seller/create-shop";
        }

        return "redirect:/seller/activate";
    }

    // =========================================================================
    // BƯỚC 1: KÍCH HOẠT TÀI KHOẢN NGƯỜI BÁN (CCCD & THUẾ) - KHÔNG NẰM TRONG CUSTOMER
    // =========================================================================

    @GetMapping("/seller/activate")
    public String showSellerActivation(
            @RequestParam(value = "mode", required = false) String mode,
            Model model,
            HttpSession session) {

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/seller/activate";
        }

        Optional<Shop> existingShopOpt = shopRegistrationService.getShopByOwnerId(userId);
        if (existingShopOpt.isPresent() && !"edit".equalsIgnoreCase(mode)) {
            Shop shop = existingShopOpt.get();
            if ("APPROVED".equals(shop.getApprovalStatus())) {
                return !"APPROVED".equals(shop.getPaymentApprovalStatus())
                        ? "redirect:/seller/payment"
                        : "redirect:/shopowner/home";
            }
            if ("PENDING".equals(shop.getApprovalStatus())) {
                return "redirect:/seller/status";
            }
        }

        if (!model.containsAttribute("form")) {
            SellerAccountActivationDto form = new SellerAccountActivationDto();
            SellerAccountActivationDto sessionActivation = (SellerAccountActivationDto) session.getAttribute(SESSION_ACTIVATION_KEY);

            if (sessionActivation != null) {
                form = sessionActivation;
            } else if (shopRegistrationService.getSellerActivation(userId).isPresent()) {
                form = shopRegistrationService.getSellerActivation(userId).get();
            } else if (existingShopOpt.isPresent()) {
                Shop s = existingShopOpt.get();
                form.setPhone(s.getPhone());
                form.setIdCardNumber(s.getIdCardNumber());
                form.setIdCardName(s.getIdCardName());
                form.setIdCardDate(s.getIdCardDate() != null ? s.getIdCardDate().toString() : null);
                form.setIdCardPlace(s.getIdCardPlace());
                form.setBusinessType(s.getBusinessType() != null ? s.getBusinessType() : "INDIVIDUAL");
                form.setTaxCode(s.getTaxCode());
                form.setTaxName(s.getTaxName());
                form.setTaxEmail(s.getTaxEmail());
            } else {
                try {
                    var prof = customerAccountService.profile(userId);
                    if (prof != null) {
                        if (prof.get("phone") != null && !prof.get("phone").toString().isBlank()) {
                            form.setPhone(prof.get("phone").toString());
                        }
                        if (prof.get("email") != null && !prof.get("email").toString().isBlank()) {
                            form.setEmail(prof.get("email").toString());
                            form.setTaxEmail(prof.get("email").toString());
                        }
                        if (prof.get("fullName") != null) {
                            form.setIdCardName(prof.get("fullName").toString());
                        }
                    }
                } catch (Exception ignored) {}
            }
            model.addAttribute("form", form);
        }

        model.addAttribute("activeStep", 1);
        return "seller/activate";
    }

    @PostMapping("/seller/activate")
    public String submitSellerActivation(
            @Valid @ModelAttribute("form") SellerAccountActivationDto form,
            BindingResult errors,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/seller/activate";
        }

        if (errors.hasErrors()) {
            model.addAttribute("activeStep", 1);
            return "seller/activate";
        }

        // Lưu thông tin kích hoạt vào database và session
        shopRegistrationService.saveSellerActivation(userId, form);
        session.setAttribute(SESSION_ACTIVATION_KEY, form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Kích hoạt tài khoản Người bán thành công! Mời bạn tiếp tục hoàn tất thông tin tạo Shop.");

        return "redirect:/seller/create-shop";
    }

    // =========================================================================
    // BƯỚC 2: TẠO GIAN HÀNG (TÊN SHOP, ĐỊA CHỈ KHO, VẬN CHUYỂN) - KHÔNG NẰM TRONG CUSTOMER
    // =========================================================================

    @GetMapping("/seller/create-shop")
    public String showCreateShopForm(
            @RequestParam(value = "mode", required = false) String mode,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/seller/create-shop";
        }

        Optional<Shop> existingShopOpt = shopRegistrationService.getShopByOwnerId(userId);
        if (existingShopOpt.isPresent() && !"edit".equalsIgnoreCase(mode)) {
            Shop shop = existingShopOpt.get();
            if ("APPROVED".equals(shop.getApprovalStatus())) {
                return !"APPROVED".equals(shop.getPaymentApprovalStatus())
                        ? "redirect:/seller/payment"
                        : "redirect:/shopowner/home";
            }
            if ("PENDING".equals(shop.getApprovalStatus())) {
                return "redirect:/seller/status";
            }
        }

        SellerAccountActivationDto activation = (SellerAccountActivationDto) session.getAttribute(SESSION_ACTIVATION_KEY);
        if (activation == null) {
            activation = shopRegistrationService.getSellerActivation(userId).orElse(null);
            if (activation != null) {
                session.setAttribute(SESSION_ACTIVATION_KEY, activation);
            }
        }

        if (activation == null && existingShopOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Vui lòng kích hoạt tài khoản Shop Owner (Định danh CCCD & Thuế) trước khi tạo Shop.");
            return "redirect:/seller/activate";
        }

        if (!model.containsAttribute("form")) {
            SellerCreateShopDto form = new SellerCreateShopDto();
            if (existingShopOpt.isPresent()) {
                Shop s = existingShopOpt.get();
                form.setName(s.getName());
                form.setDescription(s.getDescription());
                form.setPhone(s.getPhone());
                form.setProvinceCode(s.getProvinceCode());
                form.setWardName(s.getWardName());
                form.setAddressDetail(s.getAddressDetail());
            } else if (activation != null && activation.getPhone() != null) {
                form.setPhone(activation.getPhone());
            }
            model.addAttribute("form", form);
        }

        model.addAttribute("activation", activation);
        model.addAttribute("provinces", shopRegistrationService.getAllActiveProvinces());
        model.addAttribute("activeStep", 2);
        return "seller/create-shop";
    }

    /**
     * BƯỚC KHỞI ĐẦU LUỒNG DUYỆT: Người bán nộp hồ sơ tạo Shop.
     * - Nếu là đăng ký lần đầu: Tạo Shop mới với approval_status = 'PENDING', operating_status = 'CLOSED'.
     * - Nếu là nộp lại sau khi bị từ chối (REJECTED): Cập nhật thông tin mới và đưa approval_status trở lại 'PENDING'.
     * - Gửi thông báo xác nhận đã nộp hồ sơ tới Người bán và chuyển hướng sang trang /seller/status.
     */
    @PostMapping("/seller/create-shop")
    public String submitCreateShop(
            @Valid @ModelAttribute("form") SellerCreateShopDto form,
            BindingResult errors,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/seller/create-shop";
        }

        SellerAccountActivationDto activation = (SellerAccountActivationDto) session.getAttribute(SESSION_ACTIVATION_KEY);
        if (activation == null) {
            activation = shopRegistrationService.getSellerActivation(userId).orElse(null);
            if (activation != null) {
                session.setAttribute(SESSION_ACTIVATION_KEY, activation);
            }
        }
        Optional<Shop> existingShopOpt = shopRegistrationService.getShopByOwnerId(userId);

        if (activation == null && existingShopOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Phiên làm việc đã hết hạn hoặc chưa kích hoạt tài khoản. Vui lòng thực hiện Bước 1.");
            return "redirect:/seller/activate";
        }

        if (!form.isAgreeTerms()) {
            errors.rejectValue("agreeTerms", "required", "Bạn cần đồng ý với Quy chế hoạt động của FreshFruit.");
        }

        if (errors.hasErrors()) {
            model.addAttribute("activation", activation);
            model.addAttribute("provinces", shopRegistrationService.getAllActiveProvinces());
            model.addAttribute("activeStep", 2);
            return "seller/create-shop";
        }

        try {
            // Trường hợp 1: Hồ sơ đã tồn tại và từng bị REJECTED -> Nộp lại (resubmit) đưa trạng thái về PENDING
            if (existingShopOpt.isPresent() && "REJECTED".equals(existingShopOpt.get().getApprovalStatus())) {
                ShopRegistrationDto fullDto = new ShopRegistrationDto();
                fullDto.setName(form.getName());
                fullDto.setDescription(form.getDescription());
                fullDto.setPhone(form.getPhone());
                fullDto.setProvinceCode(form.getProvinceCode());
                fullDto.setWardName(form.getWardName());
                fullDto.setAddressDetail(form.getAddressDetail());
                fullDto.setShippingFast(form.isShippingFast());
                fullDto.setShippingExpress(form.isShippingExpress());
                fullDto.setShippingStandard(form.isShippingStandard());
                fullDto.setAgreeTerms(form.isAgreeTerms());

                if (activation != null) {
                    fullDto.setIdCardNumber(activation.getIdCardNumber());
                    fullDto.setIdCardName(activation.getIdCardName());
                    fullDto.setIdCardDate(activation.getIdCardDate());
                    fullDto.setIdCardPlace(activation.getIdCardPlace());
                    fullDto.setBusinessType(activation.getBusinessType());
                    fullDto.setTaxCode(activation.getTaxCode());
                    fullDto.setTaxName(activation.getTaxName());
                    fullDto.setTaxEmail(activation.getTaxEmail());
                } else {
                    Shop s = existingShopOpt.get();
                    fullDto.setIdCardNumber(s.getIdCardNumber());
                    fullDto.setIdCardName(s.getIdCardName());
                    fullDto.setIdCardDate(s.getIdCardDate() != null ? s.getIdCardDate().toString() : null);
                    fullDto.setIdCardPlace(s.getIdCardPlace());
                    fullDto.setBusinessType(s.getBusinessType());
                    fullDto.setTaxCode(s.getTaxCode());
                    fullDto.setTaxName(s.getTaxName());
                    fullDto.setTaxEmail(s.getTaxEmail());
                }
                shopRegistrationService.resubmitShop(userId, fullDto);
            } else {
                // Trường hợp 2: Đăng ký tạo Shop mới lần đầu -> Chèn mới vào bảng shops với status = 'PENDING'
                shopRegistrationService.registerShopFromSellerOnboarding(userId, activation, form);
            }

            // Xóa cache kích hoạt sau khi đã nộp thành công
            session.removeAttribute(SESSION_ACTIVATION_KEY);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Hồ sơ đăng ký mở Shop đã được gửi thành công! Ban Quản trị sẽ xét duyệt trong thời gian sớm nhất.");
            return "redirect:/seller/status";

        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("activation", activation);
            model.addAttribute("provinces", shopRegistrationService.getAllActiveProvinces());
            model.addAttribute("activeStep", 2);
            return "seller/create-shop";
        }
    }

    // =========================================================================
    // BƯỚC 3: TIẾN TRÌNH XÉT DUYỆT HỒ SƠ SHOP (CHỜ DUYỆT / TỪ CHỐI / ĐÃ DUYỆT)
    // =========================================================================

    @GetMapping("/seller/status")
    public String showSellerStatus(Model model, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/seller/status";
        }

        Map<String, Object> details = shopRegistrationService.getShopRegistrationStatusDetails(userId);
        if (!(Boolean) details.getOrDefault("hasShop", false)) {
            return "redirect:/seller/channel";
        }

        model.addAllAttributes(details);
        model.addAttribute("activeStep", 3);
        return "seller/status";
    }

    // =========================================================================
    // BƯỚC 4: THIẾT LẬP THÔNG TIN THANH TOÁN & ĐỐI CHIẾU CCCD
    // =========================================================================

    @GetMapping("/seller/payment")
    public String showSellerPaymentSetup(Model model, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/seller/payment";
        }

        Optional<Shop> shopOpt = shopRegistrationService.getShopByOwnerId(userId);
        if (shopOpt.isEmpty()) {
            return "redirect:/seller/channel";
        }

        Shop shop = shopOpt.get();
        if (!"APPROVED".equals(shop.getApprovalStatus())) {
            return "redirect:/seller/status";
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
        model.addAttribute("activeStep", 3);
        return "seller/payment";
    }

    @PostMapping("/seller/payment")
    public String submitSellerPaymentSetup(
            @Valid @ModelAttribute("form") ShopPaymentSetupDto form,
            BindingResult errors,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login?continueTo=/seller/payment";
        }

        Optional<Shop> shopOpt = shopRegistrationService.getShopByOwnerId(userId);
        if (shopOpt.isEmpty() || !"APPROVED".equals(shopOpt.get().getApprovalStatus())) {
            return "redirect:/seller/status";
        }

        Shop shop = shopOpt.get();

        if (errors.hasErrors()) {
            model.addAttribute("shop", shop);
            model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
            model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
            model.addAttribute("activeStep", 3);
            return "seller/payment";
        }

        try {
            shopRegistrationService.submitPaymentInfo(userId, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã lưu thông tin thanh toán thành công! Ban vận hành sẽ đối chiếu tên chủ tài khoản với CCCD ("
                            + (shop.getIdCardName() != null ? shop.getIdCardName() : "") + ") và phê duyệt.");
            return "redirect:/seller/payment";
        } catch (Exception ex) {
            model.addAttribute("shop", shop);
            model.addAttribute("paymentApprovalStatus", shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
            model.addAttribute("bankRejectionReason", shop.getBankRejectionReason());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("activeStep", 3);
            return "seller/payment";
        }
    }

    // =========================================================================
    // LEGACY REDIRECTS (Tách hoàn toàn khỏi Customer, chuyển hướng về Seller Channel)
    // =========================================================================

    @GetMapping("/customer/shop-registration")
    public String legacyCustomerShopRegistration() {
        return "redirect:/seller/channel";
    }

    @PostMapping("/customer/shop-registration")
    public String legacyPostCustomerShopRegistration() {
        return "redirect:/seller/channel";
    }

    @GetMapping("/customer/shop-registration/status")
    public String legacyCustomerShopRegistrationStatus() {
        return "redirect:/seller/status";
    }

    @GetMapping("/customer/shop-registration/payment")
    public String legacyCustomerShopRegistrationPayment() {
        return "redirect:/seller/payment";
    }

    @PostMapping("/customer/shop-registration/payment")
    public String legacyPostCustomerShopRegistrationPayment() {
        return "redirect:/seller/payment";
    }

    @GetMapping("/register/shop-owner")
    public String showShopOwnerRegistrationLanding() {
        return "redirect:/register?role=shop_owner";
    }
}

