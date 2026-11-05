package service.shop;

import dto.shop.ShopPaymentSetupDto;
import dto.shop.ShopRegistrationDto;
import entity.Province;
import entity.Shop;
import entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.ProvinceRepository;
import repository.ShopRepository;
import repository.UserRepository;
import service.customer.AddressLocationService;
import service.customer.NotificationService;

import dto.seller.SellerAccountActivationDto;
import dto.seller.SellerCreateShopDto;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ==============================================================================================
 * SERVICE: ShopRegistrationService
 * ==============================================================================================
 * Mục đích: Quản lý toàn bộ nghiệp vụ (Business Logic) trong quy trình Tạo và Đăng ký Gian hàng
 * dành cho Người bán (Seller / Shop Owner) trên nền tảng sàn thương mại điện tử FreshFruit.
 *
 * Chu trình hoạt động gồm 4 giai đoạn chính do Service này đảm nhận xử lý:
 * ----------------------------------------------------------------------------------------------
 * Giai đoạn 1 (Kích hoạt người bán):
 *   - Tiếp nhận định danh CCCD/Hộ chiếu và mã số thuế cá nhân / doanh nghiệp.
 *   - Lưu trữ tạm thời vào bảng `seller_activations` và đồng bộ số điện thoại vào bảng `users`.
 *
 * Giai đoạn 2 (Tạo hồ sơ gian hàng & Nộp giấy phép):
 *   - Tiếp nhận thông tin tên Shop, địa chỉ kho lấy hàng, các kênh vận chuyển hỗ trợ.
 *   - Hợp nhất thông tin CCCD (Giai đoạn 1) và thông tin Shop (Giai đoạn 2) kèm URL giấy phép
 *     kinh doanh (GPKD) & giấy chứng nhận ATTP từ `ShopCertificateStorage`.
 *   - Chèn mới bản ghi vào bảng `shops` với:
 *       + `approval_status = 'PENDING'` (Chờ Ban Quản lý duyệt)
 *       + `operating_status = 'CLOSED'` (Chưa mở bán cho tới khi duyệt thanh toán)
 *   - Bắn thông báo xác nhận tới Người bán và thông báo tới Ban Quản Trị (Operations).
 *
 * Giai đoạn 3 (Theo dõi trạng thái & Nộp lại hồ sơ):
 *   - Truy vấn chi tiết tiến trình xét duyệt hồ sơ (`PENDING`, `APPROVED`, `REJECTED`).
 *   - Nếu bị từ chối (`REJECTED`), hỗ trợ nộp lại hồ sơ (`resubmitShop`) để quay lại `PENDING`.
 *
 * Giai đoạn 4 (Thiết lập tài khoản ngân hàng thụ hưởng):
 *   - Nhập thông tin số tài khoản, tên ngân hàng để đối soát với CCCD trước khi kích hoạt `OPEN`.
 * ==============================================================================================
 */
@Service
public class ShopRegistrationService {

    private final ShopRepository shopRepository;
    private final ProvinceRepository provinceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AddressLocationService addressLocationService;
    private final JdbcTemplate jdbcTemplate;

    public ShopRegistrationService(
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

    /**
     * Tự động khởi tạo bảng `seller_activations` khi ứng dụng khởi chạy nếu bảng chưa tồn tại.
     * Bảng này dùng để lưu trữ dữ liệu định danh CCCD và thông tin thuế của Người bán trước khi nộp hồ sơ mở Shop.
     */
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

    /**
     * ==========================================================================================
     * BƯỚC 1: LƯU THÔNG TIN KÍCH HOẠT ĐỊNH DANH NGƯỜI BÁN (saveSellerActivation)
     * ==========================================================================================
     * - Lưu thông tin CCCD, nơi cấp, ngày cấp, loại hình kinh doanh, mã số thuế vào bảng `seller_activations`.
     * - Sử dụng cú pháp `ON DUPLICATE KEY UPDATE` để người bán có thể chỉnh sửa lại trước khi tạo shop.
     * - Đồng bộ cập nhật số điện thoại liên lạc vào bảng người dùng `users`.
     *
     * @param userId ID người dùng đang đăng nhập
     * @param form   Dữ liệu form định danh người bán
     */
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

        // Đồng bộ số điện thoại vào bảng users nếu có thay đổi
        if (form.getPhone() != null && !form.getPhone().isBlank()) {
            userRepository.findById(userId).ifPresent(u -> {
                u.setPhone(form.getPhone().trim());
                userRepository.save(u);
            });
        }
    }

    /**
     * Lấy lại thông tin kích hoạt định danh đã lưu trong database của Người bán (nếu có).
     *
     * @param userId ID người dùng
     * @return Optional chứa SellerAccountActivationDto hoặc rỗng
     */
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

    /**
     * Tìm thông tin Shop theo ID tài khoản chủ sở hữu (ownerId).
     *
     * @param userId ID người dùng sở hữu shop
     * @return Optional chứa thực thể Shop
     */
    @Transactional(readOnly = true)
    public Optional<Shop> getShopByOwnerId(Long userId) {
        if (userId == null) return Optional.empty();
        return shopRepository.findByOwnerId(userId);
    }

    /**
     * Lấy danh sách Tỉnh/Thành phố đang hoạt động để người bán chọn làm địa chỉ kho lấy hàng.
     * Tự động khởi tạo (seed) dữ liệu địa chỉ mẫu nếu bảng chưa có dữ liệu.
     *
     * @return Danh sách các Tỉnh/Thành phố hợp lệ
     */
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

    /**
     * ==========================================================================================
     * BƯỚC 2A: ĐĂNG KÝ MỞ GIAN HÀNG MỚI LẦN ĐẦU (registerShop)
     * ==========================================================================================
     * - Kiểm tra tính hợp lệ của tài khoản: Đã đăng nhập, chưa có gian hàng hoặc chưa có hồ sơ chờ duyệt.
     * - Validate tỉnh/thành phố kho hàng tồn tại trong hệ thống.
     * - Khởi tạo thực thể `Shop` với:
     *     + `approvalStatus = 'PENDING'` (Chờ Ban Quản Trị duyệt)
     *     + `operatingStatus = 'CLOSED'` (Chưa được phép hiển thị mở bán)
     * - Lưu đầy đủ thông tin pháp lý: CCCD, MST, Giấy phép kinh doanh, Chứng nhận ATTP.
     * - Lưu các kênh vận chuyển hỗ trợ: FAST, EXPRESS, STANDARD.
     * - Bắn 2 thông báo: Xác nhận đã nộp hồ sơ tới Người bán và báo tới Ban Vận Hành (Operations).
     *
     * @param userId ID người dùng đăng ký
     * @param dto    DTO tổng hợp chứa toàn bộ thông tin đăng ký shop
     * @return Thực thể Shop đã được lưu vào database
     */
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
            // Nếu hồ sơ trước đó bị REJECTED -> chuyển hướng sang luồng nộp lại (resubmit)
            return resubmitShop(userId, dto);
        }

        // Validate tính hợp lệ của Tỉnh/Thành phố
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

        // Gắn thông tin định danh CCCD & Thuế
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

        // Gắn thông tin Giấy phép kinh doanh (GPKD)
        shop.setBusinessLicenseNumber(dto.getBusinessLicenseNumber() != null ? dto.getBusinessLicenseNumber().trim() : null);
        if (dto.getBusinessLicenseDate() != null && !dto.getBusinessLicenseDate().isBlank()) {
            try {
                shop.setBusinessLicenseDate(java.time.LocalDate.parse(dto.getBusinessLicenseDate().trim()));
            } catch (Exception ignored) {}
        }
        shop.setBusinessLicensePlace(dto.getBusinessLicensePlace() != null ? dto.getBusinessLicensePlace().trim() : null);
        shop.setBusinessLicenseUrl(dto.getBusinessLicenseUrl() != null ? dto.getBusinessLicenseUrl().trim() : null);

        // Gắn thông tin Giấy chứng nhận An toàn thực phẩm (ATTP)
        shop.setFoodSafetyCertNumber(dto.getFoodSafetyCertNumber() != null ? dto.getFoodSafetyCertNumber().trim() : null);
        if (dto.getFoodSafetyCertDate() != null && !dto.getFoodSafetyCertDate().isBlank()) {
            try {
                shop.setFoodSafetyCertDate(java.time.LocalDate.parse(dto.getFoodSafetyCertDate().trim()));
            } catch (Exception ignored) {}
        }
        if (dto.getFoodSafetyCertExpiry() != null && !dto.getFoodSafetyCertExpiry().isBlank()) {
            try {
                shop.setFoodSafetyCertExpiry(java.time.LocalDate.parse(dto.getFoodSafetyCertExpiry().trim()));
            } catch (Exception ignored) {}
        }
        shop.setFoodSafetyCertPlace(dto.getFoodSafetyCertPlace() != null ? dto.getFoodSafetyCertPlace().trim() : null);
        shop.setFoodSafetyCertUrl(dto.getFoodSafetyCertUrl() != null ? dto.getFoodSafetyCertUrl().trim() : null);

        // Tổng hợp các phương thức vận chuyển được chọn
        java.util.List<String> shipping = new java.util.ArrayList<>();
        if (dto.isShippingFast()) shipping.add("FAST");
        if (dto.isShippingExpress()) shipping.add("EXPRESS");
        if (dto.isShippingStandard()) shipping.add("STANDARD");
        shop.setShippingMethods(String.join(",", shipping));

        Shop savedShop = shopRepository.save(shop);

        // Bắn thông báo xác nhận nộp hồ sơ tới Người bán và thông báo tới Ban Vận Hành
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

    /**
     * ==========================================================================================
     * BƯỚC 2B: NỘP LẠI HỒ SƠ SAU KHI BỊ BAN VẬN HÀNH TỪ CHỐI (resubmitShop)
     * ==========================================================================================
     * - Dành cho trường hợp hồ sơ trước đó có `approval_status = 'REJECTED'`.
     * - Cập nhật lại toàn bộ thông tin mới do người bán sửa đổi.
     * - Xóa lý do từ chối cũ (`rejectionReason = null`), xóa thời gian duyệt cũ (`reviewedAt = null`).
     * - Đưa trạng thái trở lại `approval_status = 'PENDING'` và `operating_status = 'CLOSED'` để Ban Quản Lý thẩm định lại.
     * - Gửi thông báo tới Ban Vận Hành để xem xét hồ sơ mới nộp lại.
     *
     * @param userId ID người dùng
     * @param dto    DTO chứa thông tin hồ sơ mới cập nhật
     * @return Thực thể Shop sau khi cập nhật
     */
    @Transactional
    public Shop resubmitShop(Long userId, ShopRegistrationDto dto) {
        if (userId == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập.");
        }

        Shop shop = shopRepository.findByOwnerId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ mở Shop trước đó để nộp lại."));

        validateProvince(dto.getProvinceCode());

        // Cập nhật thông tin gian hàng & địa chỉ kho
        shop.setName(dto.getName().trim());
        shop.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : "");
        shop.setPhone(dto.getPhone().trim());
        shop.setProvinceCode(dto.getProvinceCode());
        shop.setWardName(dto.getWardName().trim());
        shop.setAddressDetail(dto.getAddressDetail().trim());

        // Reset trạng thái về PENDING và xóa lý do từ chối cũ
        shop.setApprovalStatus("PENDING");
        shop.setOperatingStatus("CLOSED");
        shop.setRejectionReason(null);
        shop.setReviewedAt(null);

        // Cập nhật thông tin CCCD & Thuế
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

        // Cập nhật thông tin Giấy phép kinh doanh
        shop.setBusinessLicenseNumber(dto.getBusinessLicenseNumber() != null ? dto.getBusinessLicenseNumber().trim() : null);
        if (dto.getBusinessLicenseDate() != null && !dto.getBusinessLicenseDate().isBlank()) {
            try {
                shop.setBusinessLicenseDate(java.time.LocalDate.parse(dto.getBusinessLicenseDate().trim()));
            } catch (Exception ignored) {}
        }
        shop.setBusinessLicensePlace(dto.getBusinessLicensePlace() != null ? dto.getBusinessLicensePlace().trim() : null);
        shop.setBusinessLicenseUrl(dto.getBusinessLicenseUrl() != null ? dto.getBusinessLicenseUrl().trim() : null);

        // Cập nhật thông tin Giấy chứng nhận ATTP
        shop.setFoodSafetyCertNumber(dto.getFoodSafetyCertNumber() != null ? dto.getFoodSafetyCertNumber().trim() : null);
        if (dto.getFoodSafetyCertDate() != null && !dto.getFoodSafetyCertDate().isBlank()) {
            try {
                shop.setFoodSafetyCertDate(java.time.LocalDate.parse(dto.getFoodSafetyCertDate().trim()));
            } catch (Exception ignored) {}
        }
        if (dto.getFoodSafetyCertExpiry() != null && !dto.getFoodSafetyCertExpiry().isBlank()) {
            try {
                shop.setFoodSafetyCertExpiry(java.time.LocalDate.parse(dto.getFoodSafetyCertExpiry().trim()));
            } catch (Exception ignored) {}
        }
        shop.setFoodSafetyCertPlace(dto.getFoodSafetyCertPlace() != null ? dto.getFoodSafetyCertPlace().trim() : null);
        shop.setFoodSafetyCertUrl(dto.getFoodSafetyCertUrl() != null ? dto.getFoodSafetyCertUrl().trim() : null);

        // Cập nhật các phương thức vận chuyển
        java.util.List<String> resubmitShipping = new java.util.ArrayList<>();
        if (dto.isShippingFast()) resubmitShipping.add("FAST");
        if (dto.isShippingExpress()) resubmitShipping.add("EXPRESS");
        if (dto.isShippingStandard()) resubmitShipping.add("STANDARD");
        shop.setShippingMethods(String.join(",", resubmitShipping));

        Shop saved = shopRepository.save(shop);

        // Bắn thông báo nộp lại hồ sơ tới người bán và ban vận hành
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

    /**
     * ==========================================================================================
     * BƯỚC 3: TRUY VẤN TIẾN TRÌNH XÉT DUYỆT HỒ SƠ SHOP (getShopRegistrationStatusDetails)
     * ==========================================================================================
     * - Dùng để hiển thị trạng thái động trên trang `/seller/status`.
     * - Tính toán tiêu đề trạng thái (`statusTitle`), mô tả (`statusDescription`), màu badge CSS,
     *   và bước tiến trình hiện tại (`stepIndex` từ 1 đến 4).
     * - Xử lý đầy đủ các kịch bản:
     *     + `PENDING`: Hồ sơ Shop đang chờ duyệt.
     *     + `REJECTED`: Hồ sơ Shop bị từ chối kèm lý do từ Operations.
     *     + `APPROVED`: Hồ sơ Shop đã duyệt, kiểm tra tiếp trạng thái đối soát tài khoản thanh toán
     *       (`paymentApprovalStatus`: `UNCONFIGURED`, `PENDING`, `REJECTED`, `APPROVED`).
     *
     * @param userId ID người dùng đang kiểm tra
     * @return Map chứa các thuộc tính model để Thymeleaf render giao diện
     */
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

    /**
     * ==========================================================================================
     * BƯỚC 4: THIẾT LẬP THÔNG TIN TÀI KHOẢN NGÂN HÀNG THỤ HƯỞNG (submitPaymentInfo)
     * ==========================================================================================
     * - Chỉ cho phép thực hiện khi hồ sơ Shop đã được duyệt bước 1 (`approvalStatus == 'APPROVED'`).
     * - Chuẩn hóa tên chủ tài khoản thành CHỮ IN HOA để phục vụ đối chiếu không dấu với CCCD.
     * - Cập nhật thông tin ngân hàng và chuyển `payment_approval_status = 'PENDING'`.
     * - Gửi thông báo đến chuông thông báo của Người bán xác nhận đã gửi tài khoản đối soát.
     *
     * @param userId ID người dùng
     * @param dto    DTO chứa thông tin ngân hàng (Tên ngân hàng, STK, Tên chủ TK, Chi nhánh)
     */
    @Transactional
    public void submitPaymentInfo(Long userId, ShopPaymentSetupDto dto) {
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

    /**
     * ==========================================================================================
     * CẦU NỐI HỢP NHẤT DỮ LIỆU ONBOARDING (registerShopFromSellerOnboarding)
     * ==========================================================================================
     * - Hợp nhất dữ liệu đã lưu ở Bước 1 (CCCD & Thuế từ `SellerAccountActivationDto`)
     *   và dữ liệu ở Bước 2 (Tên shop, kho hàng, GPKD, ATTP từ `SellerCreateShopDto`).
     * - Tạo thành đối tượng `ShopRegistrationDto` hoàn chỉnh và gọi hàm `registerShop`.
     *
     * @param userId     ID người dùng
     * @param activation Dữ liệu định danh người bán ở Bước 1
     * @param shopDto    Dữ liệu thông tin shop & giấy phép ở Bước 2
     * @return Thực thể Shop đã được đăng ký thành công
     */
    @Transactional
    public Shop registerShopFromSellerOnboarding(Long userId, SellerAccountActivationDto activation, SellerCreateShopDto shopDto) {
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

        // Kế thừa thông tin định danh CCCD & Thuế từ Bước 1
        fullDto.setIdCardNumber(activation.getIdCardNumber());
        fullDto.setIdCardName(activation.getIdCardName());
        fullDto.setIdCardDate(activation.getIdCardDate());
        fullDto.setIdCardPlace(activation.getIdCardPlace());
        fullDto.setBusinessType(activation.getBusinessType());
        fullDto.setTaxCode(activation.getTaxCode());
        fullDto.setTaxName(activation.getTaxName());
        fullDto.setTaxEmail(activation.getTaxEmail());

        // Kế thừa thông tin giấy phép kinh doanh & an toàn thực phẩm từ Bước 2
        fullDto.setBusinessLicenseNumber(shopDto.getBusinessLicenseNumber());
        fullDto.setBusinessLicenseDate(shopDto.getBusinessLicenseDate());
        fullDto.setBusinessLicensePlace(shopDto.getBusinessLicensePlace());
        fullDto.setBusinessLicenseUrl(shopDto.getBusinessLicenseUrl());
        fullDto.setFoodSafetyCertNumber(shopDto.getFoodSafetyCertNumber());
        fullDto.setFoodSafetyCertDate(shopDto.getFoodSafetyCertDate());
        fullDto.setFoodSafetyCertExpiry(shopDto.getFoodSafetyCertExpiry());
        fullDto.setFoodSafetyCertPlace(shopDto.getFoodSafetyCertPlace());
        fullDto.setFoodSafetyCertUrl(shopDto.getFoodSafetyCertUrl());

        return registerShop(userId, fullDto);
    }

    /**
     * Xác thực mã tỉnh/thành phố kho hàng có tồn tại trong hệ thống hay không.
     *
     * @param provinceCode Mã tỉnh/thành phố
     */
    private void validateProvince(String provinceCode) {
        if (provinceCode == null || !provinceRepository.existsById(provinceCode)) {
            throw new IllegalArgumentException("Tỉnh/Thành phố được chọn không hợp lệ.");
        }
    }
}
