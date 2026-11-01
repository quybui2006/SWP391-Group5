package controller.shop;

import dto.seller.SellerAccountActivationDto;
import entity.Province;
import entity.Shop;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import repository.ProvinceRepository;
import service.customer.CustomerShopRegistrationService;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Controller phục vụ hiển thị "Thông tin đăng ký" Shop trên Sidebar.
 * Cho phép Chủ cửa hàng (Shop Owner / Người bán) xem lại chi tiết toàn bộ hồ sơ
 * đã đăng ký trong luồng tạo shop (Thông tin gian hàng, kho bãi, định danh CCCD, thuế,
 * tài khoản thanh toán và tiến trình phê duyệt của Ban Quản trị).
 */
@Controller
public class ShopOwnerRegistrationInfoController {

    private final CustomerShopRegistrationService shopRegistrationService;
    private final ProvinceRepository provinceRepository;

    public ShopOwnerRegistrationInfoController(
            CustomerShopRegistrationService shopRegistrationService,
            ProvinceRepository provinceRepository) {
        this.shopRegistrationService = shopRegistrationService;
        this.provinceRepository = provinceRepository;
    }

    private Long getUserIdFromSession(HttpSession session) {
        if (session == null) return null;
        Object idObj = session.getAttribute("userId");
        if (idObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    @GetMapping({"/shopowner/registration-info", "/seller/registration-info"})
    public String showRegistrationInfo(HttpSession session, Model model) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return "redirect:/login";
        }

        Optional<Shop> shopOpt = shopRegistrationService.getShopByOwnerId(userId);
        if (shopOpt.isEmpty()) {
            return "redirect:/seller/channel";
        }

        Shop shop = shopOpt.get();

        // 1. Tên tỉnh/thành phố kho hàng
        String provinceName = "Chưa xác định";
        if (shop.getProvinceCode() != null) {
            provinceName = provinceRepository.findById(shop.getProvinceCode())
                    .map(Province::getName)
                    .orElse(shop.getProvinceCode());
        }

        // 2. Thông tin kích hoạt tài khoản định danh CCCD & Thuế
        Optional<SellerAccountActivationDto> activationOpt = shopRegistrationService.getSellerActivation(userId);
        SellerAccountActivationDto activation = activationOpt.orElseGet(() -> {
            SellerAccountActivationDto fallback = new SellerAccountActivationDto();
            fallback.setPhone(shop.getPhone());
            fallback.setIdCardName(shop.getIdCardName());
            fallback.setIdCardNumber(shop.getIdCardNumber());
            fallback.setIdCardDate(shop.getIdCardDate() != null ? shop.getIdCardDate().toString() : null);
            fallback.setIdCardPlace(shop.getIdCardPlace());
            fallback.setBusinessType(shop.getBusinessType() != null ? shop.getBusinessType() : "INDIVIDUAL");
            fallback.setTaxCode(shop.getTaxCode());
            fallback.setTaxName(shop.getTaxName());
            fallback.setTaxEmail(shop.getTaxEmail());
            return fallback;
        });

        // 3. Định dạng thời gian
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy");
        String createdAtFormatted = shop.getCreatedAt() != null ? shop.getCreatedAt().format(dtf) : "Vừa đăng ký";
        String reviewedAtFormatted = shop.getReviewedAt() != null ? shop.getReviewedAt().format(dtf) : null;
        String paymentReviewedAtFormatted = shop.getPaymentReviewedAt() != null ? shop.getPaymentReviewedAt().format(dtf) : null;

        // 4. Phương thức vận chuyển đã cấu hình
        String shippingMethods = shop.getShippingMethods() != null ? shop.getShippingMethods() : "";
        boolean shippingFast = shippingMethods.contains("FAST");
        boolean shippingExpress = shippingMethods.contains("EXPRESS");
        boolean shippingStandard = shippingMethods.contains("STANDARD") || shippingMethods.isBlank();

        // 5. Loại hình kinh doanh
        String businessType = shop.getBusinessType() != null ? shop.getBusinessType() : "INDIVIDUAL";
        String businessTypeLabel = switch (businessType) {
            case "HOUSEHOLD" -> "Hộ kinh doanh cá thể";
            case "COMPANY" -> "Doanh nghiệp / Hợp tác xã (HTX)";
            default -> "Cá nhân / Nhà vườn";
        };

        // 6. Tên chủ sở hữu
        String ownerName = (String) session.getAttribute("fullName");
        if (ownerName == null || ownerName.isBlank()) {
            ownerName = shop.getIdCardName() != null ? shop.getIdCardName() : "Chủ cửa hàng";
        }

        model.addAttribute("shop", shop);
        model.addAttribute("shopName", shop.getName());
        model.addAttribute("ownerName", ownerName);
        model.addAttribute("provinceName", provinceName);
        model.addAttribute("activation", activation);
        model.addAttribute("createdAtFormatted", createdAtFormatted);
        model.addAttribute("reviewedAtFormatted", reviewedAtFormatted);
        model.addAttribute("paymentReviewedAtFormatted", paymentReviewedAtFormatted);
        model.addAttribute("shippingFast", shippingFast);
        model.addAttribute("shippingExpress", shippingExpress);
        model.addAttribute("shippingStandard", shippingStandard);
        model.addAttribute("businessTypeLabel", businessTypeLabel);
        model.addAttribute("activePage", "registration-info");

        return "shopowner/registration-info";
    }
}
