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

import dto.seller.SellerAccountActivationDto;
import dto.seller.SellerCreateShopDto;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CustomerShopRegistrationService {

    private final ShopRepository shopRepository;
    private final ProvinceRepository provinceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AddressLocationService addressLocationService;
    private final JdbcTemplate jdbcTemplate;

    public CustomerShopRegistrationService(
            ShopRepository shopRepository,
            ProvinceRepository provinceRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AddressLocationService addressLocationService,
            JdbcTemplate jdbcTemplate) {
        this.shopRepository = shopRepository;
        this.provinceRepository = provinceRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.addressLocationService = addressLocationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSellerActivationsTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS seller_activations (
                    user_id BIGINT PRIMARY KEY,
                    phone VARCHAR(20) NOT NULL,
                    email VARCHAR(254) NOT NULL,
                    id_card_name VARCHAR(150) NOT NULL,
                    id_card_number VARCHAR(50) NOT NULL,
                    id_card_date DATE NOT NULL,
                    id_card_place VARCHAR(150) NOT NULL,
                    business_type VARCHAR(50) NOT NULL DEFAULT 'INDIVIDUAL',
                    tax_code VARCHAR(50) NULL,
                    tax_name VARCHAR(150) NULL,
                    tax_email VARCHAR(254) NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
            """);
        } catch (Exception ignored) {}
    }

    @Transactional
    public void saveSellerActivation(Long userId, SellerAccountActivationDto form) {
        if (userId == null || form == null) return;

        LocalDate cardDate = null;
        if (form.getIdCardDate() != null && !form.getIdCardDate().isBlank()) {
            try {
                cardDate = LocalDate.parse(form.getIdCardDate().trim());
            } catch (Exception ignored) {}
        }
        if (cardDate == null) {
            cardDate = LocalDate.now();
        }

        jdbcTemplate.update("""
            INSERT INTO seller_activations 
            (user_id, phone, email, id_card_name, id_card_number, id_card_date, id_card_place, business_type, tax_code, tax_name, tax_email, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                phone = VALUES(phone),
                email = VALUES(email),
                id_card_name = VALUES(id_card_name),
                id_card_number = VALUES(id_card_number),
                id_card_date = VALUES(id_card_date),
                id_card_place = VALUES(id_card_place),
                business_type = VALUES(business_type),
                tax_code = VALUES(tax_code),
                tax_name = VALUES(tax_name),
                tax_email = VALUES(tax_email),
                updated_at = CURRENT_TIMESTAMP
        """,
        userId,
        form.getPhone() != null ? form.getPhone().trim() : "",
        form.getEmail() != null ? form.getEmail().trim() : "",
        form.getIdCardName() != null ? form.getIdCardName().trim() : "",
        form.getIdCardNumber() != null ? form.getIdCardNumber().trim() : "",
        cardDate,
        form.getIdCardPlace() != null ? form.getIdCardPlace().trim() : "",
        form.getBusinessType() != null ? form.getBusinessType() : "INDIVIDUAL",
        form.getTaxCode() != null ? form.getTaxCode().trim() : null,
        form.getTaxName() != null ? form.getTaxName().trim() : null,
        form.getTaxEmail() != null ? form.getTaxEmail().trim() : null
        );

        if (form.getPhone() != null && !form.getPhone().isBlank()) {
            userRepository.findById(userId).ifPresent(u -> {
                u.setPhone(form.getPhone().trim());
                userRepository.save(u);
            });
        }
    }

    @Transactional(readOnly = true)
    public Optional<SellerAccountActivationDto> getSellerActivation(Long userId) {
        if (userId == null) return Optional.empty();
        try {
            List<SellerAccountActivationDto> results = jdbcTemplate.query(
                "SELECT phone, email, id_card_name, id_card_number, id_card_date, id_card_place, business_type, tax_code, tax_name, tax_email FROM seller_activations WHERE user_id = ?",
                (rs, rowNum) -> {
                    SellerAccountActivationDto dto = new SellerAccountActivationDto();
                    dto.setPhone(rs.getString("phone"));
                    dto.setEmail(rs.getString("email"));
                    dto.setIdCardName(rs.getString("id_card_name"));
                    dto.setIdCardNumber(rs.getString("id_card_number"));
                    java.sql.Date d = rs.getDate("id_card_date");
                    dto.setIdCardDate(d != null ? d.toLocalDate().toString() : null);
                    dto.setIdCardPlace(rs.getString("id_card_place"));
                    dto.setBusinessType(rs.getString("business_type"));
                    dto.setTaxCode(rs.getString("tax_code"));
                    dto.setTaxName(rs.getString("tax_name"));
                    dto.setTaxEmail(rs.getString("tax_email"));
                    return dto;
                },
                userId
            );
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<Shop> getShopByOwnerId(Long userId) {
        if (userId == null) return Optional.empty();
        return shopRepository.findByOwnerId(userId);
    }

    @Transactional
    public List<Province> getAllActiveProvinces() {
        List<Province> list = provinceRepository.findAll().stream()
                .filter(p -> p.isActive() && !"DEMO".equalsIgnoreCase(p.getCode()))
                .sorted(Comparator.comparing(Province::getName))
                .toList();

        if (list.size() <= 1 && addressLocationService != null) {
            List<Province> seeded = addressLocationService.getLocations().stream()
                    .map(loc -> new Province(String.valueOf(loc.code()), loc.name()))
                    .filter(p -> !provinceRepository.existsById(p.getCode()))
                    .toList();
            if (!seeded.isEmpty()) {
                provinceRepository.saveAll(seeded);
                list = provinceRepository.findAll().stream()
                        .filter(p -> p.isActive() && !"DEMO".equalsIgnoreCase(p.getCode()))
                        .sorted(Comparator.comparing(Province::getName))
                        .toList();
            }
        }
        return list;
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
            String ownerName = userRepository.findById(userId).map(entity.User::getFullName).orElse(dto.getIdCardName());
            notificationService.notifyOperationsNewShopRegistration(
                    savedShop.getId(),
                    savedShop.getName(),
                    ownerName
            );
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
            String ownerName = userRepository.findById(userId).map(entity.User::getFullName).orElse(dto.getIdCardName());
            notificationService.notifyOperationsNewShopRegistration(
                    saved.getId(),
                    saved.getName(),
                    ownerName
            );
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
                String paymentStatus = shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED";
                result.put("paymentApprovalStatus", paymentStatus);
                if ("APPROVED".equals(paymentStatus)) {
                    result.put("statusTitle", "Đã hoàn tất duyệt hồ sơ & thanh toán");
                    result.put("statusDescription", "Chúc mừng! Cửa hàng của bạn đã chính thức được kích hoạt và mở bán trên FreshFruit.");
                    result.put("statusBadgeClass", "badge-approved");
                    result.put("stepIndex", 4);
                } else if ("PENDING".equals(paymentStatus)) {
                    result.put("statusTitle", "Đang đối chiếu thông tin thanh toán");
                    result.put("statusDescription", "Hồ sơ mở Shop đã duyệt. Ban vận hành đang đối chiếu thông tin tài khoản ngân hàng với CCCD của bạn.");
                    result.put("statusBadgeClass", "badge-pending");
                    result.put("stepIndex", 3);
                } else if ("REJECTED".equals(paymentStatus)) {
                    result.put("statusTitle", "Thông tin thanh toán bị từ chối");
                    result.put("statusDescription", "Thông tin tài khoản ngân hàng chưa khớp với CCCD hoặc không hợp lệ. Vui lòng cập nhật lại.");
                    result.put("statusBadgeClass", "badge-rejected");
                    result.put("stepIndex", 3);
                } else {
                    result.put("statusTitle", "Yêu cầu nhập thông tin thanh toán");
                    result.put("statusDescription", "Hồ sơ mở Shop đã duyệt bước 1. Vui lòng thiết lập tài khoản ngân hàng để hệ thống đối chiếu và mở bán.");
                    result.put("statusBadgeClass", "badge-pending");
                    result.put("stepIndex", 3);
                }
            }
            case "REJECTED" -> {
                result.put("statusTitle", "Yêu cầu bị từ chối");
                result.put("statusDescription", "Hồ sơ mở Shop chưa đạt tiêu chuẩn. Vui lòng kiểm tra lý do và gửi lại thông tin cập nhật.");
                result.put("statusBadgeClass", "badge-rejected");
                result.put("stepIndex", 2);
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

    @Transactional
    public void submitPaymentInfo(Long userId, dto.customer.ShopPaymentSetupDto dto) {
        if (userId == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập.");
        }
        Shop shop = shopRepository.findByOwnerId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin cửa hàng."));

        if (!"APPROVED".equals(shop.getApprovalStatus())) {
            throw new IllegalStateException("Hồ sơ cửa hàng chưa được phê duyệt bước 1.");
        }

        int updated = shopRepository.submitPaymentInfo(
                shop.getId(),
                dto.getBankName().trim(),
                dto.getBankAccountNumber().trim(),
                dto.getBankAccountName().trim().toUpperCase(),
                dto.getBankBranch() != null ? dto.getBankBranch().trim() : ""
        );

        if (updated == 0) {
            throw new IllegalStateException("Không thể cập nhật thông tin tài khoản ngân hàng.");
        }

        try {
            notificationService.createNotification(
                    userId,
                    "SHOP",
                    "Đã gửi thông tin thanh toán để duyệt 💳",
                    "Thông tin tài khoản ngân hàng của cửa hàng '" + shop.getName() + "' đã được gửi tới Ban vận hành để đối chiếu với CCCD (" + (shop.getIdCardName() != null ? shop.getIdCardName() : "") + ").",
                    "SHOP",
                    shop.getId()
            );
        } catch (Exception ignored) {}
    }

    @Transactional
    public Shop registerShopFromSellerOnboarding(Long userId, dto.seller.SellerAccountActivationDto activation, dto.seller.SellerCreateShopDto shopDto) {
        if (activation == null) {
            throw new IllegalArgumentException("Vui lòng hoàn thành bước Kích hoạt tài khoản Người bán trước.");
        }
        if (shopDto == null) {
            throw new IllegalArgumentException("Vui lòng nhập thông tin tạo cửa hàng.");
        }

        ShopRegistrationDto fullDto = new ShopRegistrationDto();
        fullDto.setName(shopDto.getName());
        fullDto.setDescription(shopDto.getDescription());
        fullDto.setPhone(shopDto.getPhone());
        fullDto.setProvinceCode(shopDto.getProvinceCode());
        fullDto.setWardName(shopDto.getWardName());
        fullDto.setAddressDetail(shopDto.getAddressDetail());
        fullDto.setShippingFast(shopDto.isShippingFast());
        fullDto.setShippingExpress(shopDto.isShippingExpress());
        fullDto.setShippingStandard(shopDto.isShippingStandard());
        fullDto.setAgreeTerms(shopDto.isAgreeTerms());

        fullDto.setIdCardNumber(activation.getIdCardNumber());
        fullDto.setIdCardName(activation.getIdCardName());
        fullDto.setIdCardDate(activation.getIdCardDate());
        fullDto.setIdCardPlace(activation.getIdCardPlace());
        fullDto.setBusinessType(activation.getBusinessType());
        fullDto.setTaxCode(activation.getTaxCode());
        fullDto.setTaxName(activation.getTaxName());
        fullDto.setTaxEmail(activation.getTaxEmail());

        return registerShop(userId, fullDto);
    }

    private void validateProvince(String provinceCode) {
        if (provinceCode == null || !provinceRepository.existsById(provinceCode)) {
            throw new IllegalArgumentException("Tỉnh/Thành phố được chọn không hợp lệ.");
        }
    }
}
