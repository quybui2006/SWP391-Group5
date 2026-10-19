package service.customer;

import dto.customer.ShopRegistrationDto;
import entity.Province;
import entity.Shop;
import entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.ProvinceRepository;
import repository.ShopRepository;
import repository.UserRepository;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CustomerShopRegistrationService {

    private final ShopRepository shopRepository;
    private final ProvinceRepository provinceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public CustomerShopRegistrationService(
            ShopRepository shopRepository,
            ProvinceRepository provinceRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.shopRepository = shopRepository;
        this.provinceRepository = provinceRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Optional<Shop> getShopByOwnerId(Long userId) {
        if (userId == null) return Optional.empty();
        return shopRepository.findByOwnerId(userId);
    }

    @Transactional(readOnly = true)
    public List<Province> getAllActiveProvinces() {
        return provinceRepository.findAll().stream()
                .filter(Province::isActive)
                .sorted(Comparator.comparing(Province::getName))
                .toList();
    }

    @Transactional
    public Shop registerShop(Long userId, ShopRegistrationDto dto) {
        if (userId == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập trước khi đăng ký mở shop.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin tài khoản người dùng."));

        Optional<Shop> existingOpt = shopRepository.findByOwnerId(userId);
        if (existingOpt.isPresent()) {
            Shop existing = existingOpt.get();
            if ("PENDING".equals(existing.getApprovalStatus())) {
                throw new IllegalStateException("Hồ sơ mở Shop của bạn hiện đang chờ Ban quản trị duyệt.");
            }
            if ("APPROVED".equals(existing.getApprovalStatus())) {
                throw new IllegalStateException("Bạn đã có cửa hàng đang hoạt động.");
            }
            // If REJECTED, route to resubmit
            return resubmitShop(userId, dto);
        }

        // Validate province
        validateProvince(dto.getProvinceCode());

        Shop shop = new Shop();
        shop.setOwnerId(userId);
        shop.setName(dto.getName().trim());
        shop.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : "");
        shop.setPhone(dto.getPhone().trim());
        shop.setProvinceCode(dto.getProvinceCode());
        shop.setWardName(dto.getWardName().trim());
        shop.setAddressDetail(dto.getAddressDetail().trim());
        shop.setApprovalStatus("PENDING");
        shop.setOperatingStatus("CLOSED");

        shop.setIdCardNumber(dto.getIdCardNumber() != null ? dto.getIdCardNumber().trim() : null);
        shop.setIdCardName(dto.getIdCardName() != null ? dto.getIdCardName().trim() : null);
        if (dto.getIdCardDate() != null && !dto.getIdCardDate().isBlank()) {
            try {
                shop.setIdCardDate(java.time.LocalDate.parse(dto.getIdCardDate().trim()));
            } catch (Exception ignored) {}
        }
        shop.setIdCardPlace(dto.getIdCardPlace() != null ? dto.getIdCardPlace().trim() : null);
        shop.setBusinessType(dto.getBusinessType() != null ? dto.getBusinessType() : "INDIVIDUAL");
        shop.setTaxCode(dto.getTaxCode() != null ? dto.getTaxCode().trim() : null);
        shop.setTaxName(dto.getTaxName() != null ? dto.getTaxName().trim() : null);
        shop.setTaxEmail(dto.getTaxEmail() != null ? dto.getTaxEmail().trim() : null);

        java.util.List<String> shipping = new java.util.ArrayList<>();
        if (dto.isShippingFast()) shipping.add("FAST");
        if (dto.isShippingExpress()) shipping.add("EXPRESS");
        if (dto.isShippingStandard()) shipping.add("STANDARD");
        shop.setShippingMethods(String.join(",", shipping));

        Shop savedShop = shopRepository.save(shop);

        // Send in-web notification
        try {
            notificationService.notifyShopRegistrationSubmitted(userId, savedShop.getId(), savedShop.getName());
        } catch (Exception ignored) {
        }

        return savedShop;
    }

    @Transactional
    public Shop resubmitShop(Long userId, ShopRegistrationDto dto) {
        if (userId == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập.");
        }

        Shop shop = shopRepository.findByOwnerId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ mở Shop trước đó để nộp lại."));

        validateProvince(dto.getProvinceCode());

        shop.setName(dto.getName().trim());
        shop.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : "");
        shop.setPhone(dto.getPhone().trim());
        shop.setProvinceCode(dto.getProvinceCode());
        shop.setWardName(dto.getWardName().trim());
        shop.setAddressDetail(dto.getAddressDetail().trim());
        shop.setApprovalStatus("PENDING");
        shop.setOperatingStatus("CLOSED");
        shop.setRejectionReason(null);
        shop.setReviewedAt(null);

        shop.setIdCardNumber(dto.getIdCardNumber() != null ? dto.getIdCardNumber().trim() : null);
        shop.setIdCardName(dto.getIdCardName() != null ? dto.getIdCardName().trim() : null);
        if (dto.getIdCardDate() != null && !dto.getIdCardDate().isBlank()) {
            try {
                shop.setIdCardDate(java.time.LocalDate.parse(dto.getIdCardDate().trim()));
            } catch (Exception ignored) {}
        }
        shop.setIdCardPlace(dto.getIdCardPlace() != null ? dto.getIdCardPlace().trim() : null);
        shop.setBusinessType(dto.getBusinessType() != null ? dto.getBusinessType() : "INDIVIDUAL");
        shop.setTaxCode(dto.getTaxCode() != null ? dto.getTaxCode().trim() : null);
        shop.setTaxName(dto.getTaxName() != null ? dto.getTaxName().trim() : null);
        shop.setTaxEmail(dto.getTaxEmail() != null ? dto.getTaxEmail().trim() : null);

        java.util.List<String> resubmitShipping = new java.util.ArrayList<>();
        if (dto.isShippingFast()) resubmitShipping.add("FAST");
        if (dto.isShippingExpress()) resubmitShipping.add("EXPRESS");
        if (dto.isShippingStandard()) resubmitShipping.add("STANDARD");
        shop.setShippingMethods(String.join(",", resubmitShipping));

        Shop saved = shopRepository.save(shop);

        try {
            notificationService.notifyShopRegistrationSubmitted(userId, saved.getId(), saved.getName());
        } catch (Exception ignored) {
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getShopRegistrationStatusDetails(Long userId) {
        Map<String, Object> result = new HashMap<>();
        Optional<Shop> shopOpt = getShopByOwnerId(userId);

        if (shopOpt.isEmpty()) {
            result.put("hasShop", false);
            return result;
        }

        Shop shop = shopOpt.get();
        result.put("hasShop", true);
        result.put("shop", shop);

        String provinceName = provinceRepository.findById(shop.getProvinceCode())
                .map(Province::getName)
                .orElse(shop.getProvinceCode());
        result.put("provinceName", provinceName);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy");
        if (shop.getCreatedAt() != null) {
            result.put("createdAtFormatted", shop.getCreatedAt().format(dtf));
        }
        if (shop.getReviewedAt() != null) {
            result.put("reviewedAtFormatted", shop.getReviewedAt().format(dtf));
        }

        String status = shop.getApprovalStatus();
        result.put("status", status);
        switch (status) {
            case "PENDING" -> {
                result.put("statusTitle", "Đang chờ duyệt");
                result.put("statusDescription", "Hồ sơ của bạn đã được tiếp nhận. Đội ngũ FreshFruit sẽ thẩm định thông tin trong vòng 24 giờ làm việc.");
                result.put("statusBadgeClass", "badge-pending");
                result.put("stepIndex", 2);
            }
            case "APPROVED" -> {
                result.put("statusTitle", "Đã được phê duyệt");
                result.put("statusDescription", "Chúc mừng! Cửa hàng của bạn đã chính thức được kích hoạt trên FreshFruit.");
                result.put("statusBadgeClass", "badge-approved");
                result.put("stepIndex", 3);
            }
            case "REJECTED" -> {
                result.put("statusTitle", "Yêu cầu bị từ chối");
                result.put("statusDescription", "Hồ sơ mở Shop chưa đạt tiêu chuẩn. Vui lòng kiểm tra lý do và gửi lại thông tin cập nhật.");
                result.put("statusBadgeClass", "badge-rejected");
                result.put("stepIndex", 3);
            }
            default -> {
                result.put("statusTitle", status);
                result.put("statusDescription", "");
                result.put("statusBadgeClass", "badge-default");
                result.put("stepIndex", 1);
            }
        }

        return result;
    }

    private void validateProvince(String provinceCode) {
        if (provinceCode == null || !provinceRepository.existsById(provinceCode)) {
            throw new IllegalArgumentException("Tỉnh/Thành phố được chọn không hợp lệ.");
        }
    }
}
