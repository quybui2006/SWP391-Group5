package service.operations;

import dto.operations.PendingShopApprovalDto;
import dto.operations.ShopApprovalDetailDto;
import dto.operations.ShopApprovalItemDto;
import entity.Province;
import entity.Shop;
import entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import repository.ProvinceRepository;
import repository.ShopRepository;
import repository.UserRepository;
import service.customer.NotificationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ==============================================================================================
 * SERVICE: OperationsShopApprovalService
 * ==============================================================================================
 * Mục đích: Xử lý toàn bộ logic nghiệp vụ (Business Logic) liên quan đến chu trình duyệt mở Shop:
 * 
 * Các chức năng chính:
 * 1. Truy vấn & thống kê danh sách hồ sơ shop, người bán và tài khoản thanh toán.
 * 2. Lấy chi tiết hồ sơ thẩm định và TỰ ĐỘNG ĐỐI SOÁT Họ tên CCCD vs Tên chủ TK ngân hàng (bỏ dấu tiếng Việt).
 * 3. Phê duyệt hồ sơ mở Shop (approveShop):
 *    - Đổi trạng thái: approval_status = 'APPROVED'.
 *    - Phân quyền: Thăng cấp User thành 'SHOP_OWNER' trong bảng `user_roles`.
 *    - Bắn Notification: Yêu cầu chủ shop tiếp tục cài đặt tài khoản nhận tiền.
 * 4. Từ chối hồ sơ mở Shop (rejectShop):
 *    - Đổi trạng thái: approval_status = 'REJECTED' và lưu lý do từ chối.
 *    - Bắn Notification kèm lý do chi tiết tới Người bán.
 * 5. Quản lý trạng thái vận hành (updateOperatingStatus): OPEN / CLOSED / SUSPENDED.
 * 6. Đối soát thanh toán (approvePayment): Phê duyệt TK ngân hàng và tự động mở bán (operating_status='OPEN').
 * ==============================================================================================
 */
@Service
public class OperationsShopApprovalService {
    private final ShopRepository shops;
    private final ProvinceRepository provinces;
    private final UserRepository users;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;

    public OperationsShopApprovalService(
            ShopRepository shops,
            ProvinceRepository provinces,
            UserRepository users,
            NotificationService notifications,
            JdbcTemplate jdbc) {
        this.shops = shops;
        this.provinces = provinces;
        this.users = users;
        this.notifications = notifications;
        this.jdbc = jdbc;
    }

    /**
     * Tự động kiểm tra và thêm các cột cần thiết cho việc xét duyệt hồ sơ người bán nếu chưa có trong DB.
     */
    @jakarta.annotation.PostConstruct
    public void initDb() {
        try {
            jdbc.execute("ALTER TABLE shops ADD COLUMN seller_approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING'");
        } catch (Exception ignored) {}
        try {
            jdbc.execute("ALTER TABLE shops ADD COLUMN seller_rejection_reason VARCHAR(1000) NULL");
        } catch (Exception ignored) {}
        try {
            jdbc.execute("ALTER TABLE shops ADD COLUMN seller_reviewed_at DATETIME NULL");
        } catch (Exception ignored) {}
    }

    /**
     * Tìm kiếm và lọc danh sách tài khoản định danh người bán theo trạng thái và từ khóa.
     */
    @Transactional(readOnly = true)
    public List<ShopApprovalItemDto> findSellers(String status, String keyword) {
        List<ShopApprovalItemDto> list;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            list = shops.findAllWithOwners();
        } else {
            list = shops.findBySellerApprovalStatusWithOwners(status.toUpperCase().trim());
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase().trim();
            list = list.stream().filter(s ->
                    (s.getName() != null && s.getName().toLowerCase().contains(kw)) ||
                    (s.getOwnerName() != null && s.getOwnerName().toLowerCase().contains(kw)) ||
                    (s.getOwnerEmail() != null && s.getOwnerEmail().toLowerCase().contains(kw)) ||
                    (s.getPhone() != null && s.getPhone().contains(kw))
            ).collect(Collectors.toList());
        }

        return list;
    }

    /**
     * Thống kê số lượng hồ sơ định danh người bán (Chờ duyệt, Đã duyệt, Bị từ chối).
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getSellerApprovalStats() {
        Map<String, Long> stats = new HashMap<>();
        long pending = shops.countBySellerApprovalStatus("PENDING");
        long approved = shops.countBySellerApprovalStatus("APPROVED");
        long rejected = shops.countBySellerApprovalStatus("REJECTED");
        long total = pending + approved + rejected;

        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        return stats;
    }

    /**
     * Lấy danh sách tài khoản ngân hàng chờ đối soát (Bỏ qua các shop chưa cấu hình UNCONFIGURED).
     */
    @Transactional(readOnly = true)
    public List<ShopApprovalItemDto> findPayments(String status, String keyword) {
        List<ShopApprovalItemDto> list;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            list = shops.findAllWithOwners().stream()
                    .filter(s -> !"UNCONFIGURED".equalsIgnoreCase(s.getPaymentApprovalStatus()))
                    .collect(Collectors.toList());
        } else {
            list = shops.findByPaymentApprovalStatusWithOwners(status.toUpperCase().trim());
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase().trim();
            list = list.stream().filter(s ->
                    (s.getName() != null && s.getName().toLowerCase().contains(kw)) ||
                    (s.getOwnerName() != null && s.getOwnerName().toLowerCase().contains(kw)) ||
                    (s.getOwnerEmail() != null && s.getOwnerEmail().toLowerCase().contains(kw)) ||
                    (s.getPhone() != null && s.getPhone().contains(kw))
            ).collect(Collectors.toList());
        }

        return list;
    }

    /**
     * Thống kê số lượng tài khoản ngân hàng theo trạng thái đối soát.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getPaymentApprovalStats() {
        Map<String, Long> stats = new HashMap<>();
        long pending = shops.countByPaymentApprovalStatus("PENDING");
        long approved = shops.countByPaymentApprovalStatus("APPROVED");
        long rejected = shops.countByPaymentApprovalStatus("REJECTED");
        long unconfigured = shops.countByPaymentApprovalStatus("UNCONFIGURED");
        long total = pending + approved + rejected;

        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        stats.put("unconfigured", unconfigured);
        return stats;
    }

    /**
     * Truy vấn danh sách shop đang PENDING (dành cho các tác vụ cần thông tin tóm tắt).
     */
    @Transactional(readOnly = true)
    public List<PendingShopApprovalDto> findPendingShops() {
        return shops.findPendingApprovals();
    }

    /**
     * Truy vấn danh sách Shop theo trạng thái duyệt (PENDING, APPROVED, REJECTED, ALL) và từ khóa tìm kiếm.
     */
    @Transactional(readOnly = true)
    public List<ShopApprovalItemDto> findShops(String status, String keyword) {
        List<ShopApprovalItemDto> list;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            list = shops.findAllWithOwners();
        } else {
            list = shops.findByApprovalStatusWithOwners(status.toUpperCase().trim());
        }

        // Lọc theo từ khóa: Tên shop, Tên chủ shop, Email, SĐT
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase().trim();
            list = list.stream().filter(s ->
                    (s.getName() != null && s.getName().toLowerCase().contains(kw)) ||
                    (s.getOwnerName() != null && s.getOwnerName().toLowerCase().contains(kw)) ||
                    (s.getOwnerEmail() != null && s.getOwnerEmail().toLowerCase().contains(kw)) ||
                    (s.getPhone() != null && s.getPhone().contains(kw))
            ).collect(Collectors.toList());
        }

        return list;
    }

    /**
     * Đếm số lượng Shop theo từng nhóm trạng thái để hiển thị lên 4 Badge thống kê ở giao diện.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getApprovalStats() {
        Map<String, Long> stats = new HashMap<>();
        long pending = shops.countByApprovalStatus("PENDING");
        long approved = shops.countByApprovalStatus("APPROVED");
        long rejected = shops.countByApprovalStatus("REJECTED");
        long total = pending + approved + rejected;

        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        return stats;
    }

    /**
     * ==========================================================================================
     * LẤY CHI TIẾT HỒ SƠ THẨM ĐỊNH & ĐỐI SOÁT TỰ ĐỘNG
     * ==========================================================================================
     * - Tổng hợp: Thông tin shop, chủ sở hữu, địa chỉ kho, pháp lý CCCD, MST.
     * - TỰ ĐỘNG ĐỐI SOÁT: Chuẩn hóa họ tên trên CCCD và tên chủ tài khoản ngân hàng để so sánh.
     * - Tích hợp số liệu kinh doanh: Đếm số sản phẩm cha, số đơn hàng và tổng doanh thu đã giao dịch.
     */
    @Transactional(readOnly = true)
    public ShopApprovalDetailDto getShopDetail(Long shopId) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin cửa hàng."));

        User owner = users.findById(shop.getOwnerId()).orElse(null);

        ShopApprovalDetailDto dto = new ShopApprovalDetailDto();
        dto.setId(shop.getId());
        dto.setName(shop.getName());
        dto.setDescription(shop.getDescription());
        dto.setPhone(shop.getPhone());
        dto.setProvinceCode(shop.getProvinceCode());
        dto.setWardName(shop.getWardName());
        dto.setAddressDetail(shop.getAddressDetail());
        dto.setApprovalStatus(shop.getApprovalStatus());
        dto.setOperatingStatus(shop.getOperatingStatus());
        dto.setCreatedAt(shop.getCreatedAt());
        dto.setReviewedAt(shop.getReviewedAt());
        dto.setRejectionReason(shop.getRejectionReason());
        dto.setIdCardNumber(shop.getIdCardNumber());
        dto.setIdCardName(shop.getIdCardName());
        dto.setIdCardDate(shop.getIdCardDate() != null ? shop.getIdCardDate().toString() : null);
        dto.setIdCardPlace(shop.getIdCardPlace());
        dto.setBusinessType(shop.getBusinessType());
        dto.setTaxCode(shop.getTaxCode());
        dto.setTaxName(shop.getTaxName());
        dto.setTaxEmail(shop.getTaxEmail());
        dto.setShippingMethods(shop.getShippingMethods());
        dto.setBankName(shop.getBankName());
        dto.setBankAccountNumber(shop.getBankAccountNumber());
        dto.setBankAccountName(shop.getBankAccountName());
        dto.setBankBranch(shop.getBankBranch());
        dto.setPaymentApprovalStatus(shop.getPaymentApprovalStatus() != null ? shop.getPaymentApprovalStatus() : "UNCONFIGURED");
        dto.setBankRejectionReason(shop.getBankRejectionReason());
        dto.setPaymentReviewedAt(shop.getPaymentReviewedAt());
        dto.setSellerApprovalStatus(shop.getSellerApprovalStatus() != null ? shop.getSellerApprovalStatus() : "PENDING");
        dto.setSellerRejectionReason(shop.getSellerRejectionReason());
        dto.setSellerReviewedAt(shop.getSellerReviewedAt());

        // LOGIC ĐỐI SOÁT: So sánh Tên chủ TK ngân hàng với Họ tên CCCD (bỏ dấu tiếng Việt và chữ hoa/thường)
        boolean matches = false;
        if (shop.getIdCardName() != null && shop.getBankAccountName() != null) {
            String cccdClean = removeAccents(shop.getIdCardName().trim().toLowerCase());
            String bankClean = removeAccents(shop.getBankAccountName().trim().toLowerCase());
            matches = cccdClean.equals(bankClean);
        }
        dto.setBankAccountMatchesIdCard(matches);

        // Nối tên địa chỉ đầy đủ (Xã/Phường, Tỉnh/Thành phố)
        String provinceName = provinces.findById(shop.getProvinceCode())
                .map(Province::getName)
                .orElse(shop.getProvinceCode());
        dto.setProvinceName(provinceName);
        dto.setFullAddress(shop.getAddressDetail() + ", " + shop.getWardName() + ", " + provinceName);

        // Gắn thông tin tài khoản Người bán (User)
        if (owner != null) {
            dto.setOwnerId(owner.getId());
            dto.setOwnerName(owner.getFullName());
            dto.setOwnerEmail(owner.getEmail());
            dto.setOwnerPhone(owner.getPhone());
            dto.setOwnerCreatedAt(owner.getCreatedAt());
            dto.setOwnerStatus(owner.getStatus());
        }

        // Truy vấn số lượng sản phẩm, đơn hàng và doanh thu tích lũy của Shop
        try {
            Integer prodCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM products WHERE shop_id = ?", Integer.class, shopId);
            dto.setProductCount(prodCount != null ? prodCount : 0);

            Integer orderCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE shop_id = ?", Integer.class, shopId);
            dto.setOrderCount(orderCount != null ? orderCount : 0);

            Long revenue = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(shop_revenue_amount), 0) FROM orders WHERE shop_id = ? AND status = 'COMPLETED'", Long.class, shopId);
            dto.setTotalRevenue(revenue != null ? revenue : 0L);
        } catch (Exception ignored) {}

        return dto;
    }

    /**
     * Phê duyệt hồ sơ định danh người bán.
     */
    @Transactional
    public void approveSellerAccount(Long shopId) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ người bán."));
        int updated = shops.approveSellerAccount(shopId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể phê duyệt tài khoản bán hàng.");
        }
        try {
            notifications.createNotification(
                    shop.getOwnerId(),
                    "SHOP",
                    "Tài khoản bán hàng đã được phê duyệt",
                    "Hồ sơ định danh và thông tin thuế của bạn đã được phê duyệt thành công. Ban quản lý đang tiến hành duyệt mở shop.",
                    "SELLER_ACCOUNT",
                    shop.getId()
            );
        } catch (Exception ignored) {}
    }

    /**
     * Từ chối hồ sơ định danh người bán (kèm lý do).
     */
    @Transactional
    public void rejectSellerAccount(Long shopId, String rejectionReason) {
        String reason = normalizeReason(rejectionReason);
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ người bán."));
        int updated = shops.rejectSellerAccount(shopId, reason);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể từ chối tài khoản bán hàng.");
        }
        try {
            notifications.createNotification(
                    shop.getOwnerId(),
                    "SHOP",
                    "Yêu cầu xác thực tài khoản bán hàng bị từ chối",
                    "Hồ sơ người bán của bạn chưa đạt yêu cầu. Lý do: " + reason,
                    "SELLER_ACCOUNT",
                    shop.getId()
            );
        } catch (Exception ignored) {}
    }

    /**
     * ==========================================================================================
     * NGHIỆP VỤ CỐT LÕI: PHÊ DUYỆT MỞ SHOP (approveShop)
     * ==========================================================================================
     * Các bước thực thi nguyên tử (Atomic Transaction):
     * 1. Cập nhật bảng `shops`: approval_status='APPROVED', operating_status='CLOSED' (chờ đối soát TK ngân hàng).
     * 2. Phân quyền: Cấp quyền 'SHOP_OWNER' vào bảng `user_roles` cho User sở hữu shop.
     * 3. Gửi 2 Notification:
     *    - Thông báo chúc mừng hồ sơ mở Shop đã được duyệt.
     *    - Yêu cầu người bán hoàn tất khai báo STK ngân hàng để được mở bán (OPEN).
     */
    @Transactional
    public void approveShop(Long shopId) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu mở Shop."));

        // 1. Cập nhật trạng thái mở Shop trong DB (chỉ cập nhật nếu shop đang ở trạng thái PENDING)
        if (shops.approvePending(shopId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Yêu cầu mở Shop không còn ở trạng thái chờ duyệt.");
        }

        // Tự động đồng bộ trạng thái tài khoản định danh sang APPROVED
        try {
            jdbc.update("UPDATE shops SET seller_approval_status = 'APPROVED' WHERE id = ?", shopId);
        } catch (Exception ignored) {}

        // 2. Thăng cấp quyền người dùng thành SHOP_OWNER trong bảng liên kết user_roles
        try {
            jdbc.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, r.id FROM roles r WHERE r.code = 'SHOP_OWNER'
                ON DUPLICATE KEY UPDATE user_id = user_id
            """, shop.getOwnerId());
        } catch (Exception ex) {
            // Quyền có thể đã tồn tại từ trước
        }

        // 3. Gửi thông báo hệ thống đến chuông thông báo của Người bán
        try {
            notifications.notifyShopApproved(shop.getOwnerId(), shop.getId(), shop.getName());
            notifications.notifyPaymentVerificationRequired(shop.getOwnerId(), shop.getId(), shop.getName());
        } catch (Exception ignored) {}
    }

    /**
     * ==========================================================================================
     * NGHIỆP VỤ CỐT LÕI: TỪ CHỐI MỞ SHOP (rejectShop)
     * ==========================================================================================
     * Các bước thực thi:
     * 1. Validate lý do từ chối: Bắt buộc không để trống, tối đa 1000 ký tự.
     * 2. Cập nhật bảng `shops`: approval_status='REJECTED', lưu `rejection_reason`.
     * 3. Gửi Notification đến Người bán kèm lý do chi tiết để họ chỉnh sửa và nộp lại hồ sơ.
     */
    @Transactional
    public void rejectShop(Long shopId, String rejectionReason) {
        String reason = normalizeReason(rejectionReason);
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu mở Shop."));

        if (shops.rejectPending(shopId, reason) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Yêu cầu mở Shop không còn ở trạng thái chờ duyệt.");
        }

        // Gửi thông báo giải thích lý do từ chối tới chủ shop
        try {
            notifications.notifyShopRejected(shop.getOwnerId(), shop.getId(), shop.getName(), reason);
        } catch (Exception ignored) {}
    }

    /**
     * Điều chỉnh trạng thái vận hành của Shop: OPEN (Mở bán), CLOSED (Đóng cửa), SUSPENDED (Đình chỉ).
     * - Chỉ áp dụng cho Shop ĐÃ ĐƯỢC DUYỆT (approval_status == 'APPROVED').
     * - Nếu SUSPENDED: Bắt buộc có lý do và gửi thông báo kỷ luật tới người bán.
     */
    @Transactional
    public void updateOperatingStatus(Long shopId, String operatingStatus, String reason) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Shop."));

        // Ràng buộc nghiệp vụ: Chưa duyệt thì tuyệt đối không được điều chỉnh trạng thái hoạt động
        if (!"APPROVED".equals(shop.getApprovalStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể điều chỉnh trạng thái hoạt động của Shop đã được duyệt.");
        }

        String status = operatingStatus != null ? operatingStatus.toUpperCase().trim() : "OPEN";
        if (!List.of("OPEN", "CLOSED", "SUSPENDED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái hoạt động không hợp lệ.");
        }

        if ("SUSPENDED".equals(status) && (reason == null || reason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do tạm dừng hoạt động Shop.");
        }

        shops.updateOperatingStatus(shopId, status, reason != null ? reason.trim() : null);

        // Bắn thông báo khi shop bị đình chỉ hoạt động
        if ("SUSPENDED".equals(status)) {
            try {
                notifications.createNotification(
                        shop.getOwnerId(),
                        "SHOP",
                        "Cửa hàng của bạn đã bị tạm dừng hoạt động",
                        "Ban quản lý FreshFruit đã tạm dừng hoạt động cửa hàng '" + shop.getName() + "'. Lý do: " + reason,
                        "SHOP",
                        shop.getId()
                );
            } catch (Exception ignored) {}
        }
    }

    /**
     * ==========================================================================================
     * ĐỐI SOÁT TÀI CHÍNH: PHÊ DUYỆT TÀI KHOẢN NGÂN HÀNG (approvePayment)
     * ==========================================================================================
     * - Cập nhật payment_approval_status = 'APPROVED'.
     * - TỰ ĐỘNG KÍCH HOẠT operating_status = 'OPEN' (Cho phép nông sản của gian hàng xuất hiện trên sàn).
     * - Gửi thông báo chúc mừng tới Shop Owner.
     */
    @Transactional
    public void approvePayment(Long shopId) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Shop."));

        if (!"APPROVED".equals(shop.getApprovalStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop chưa được phê duyệt mở shop.");
        }

        if (!"PENDING".equals(shop.getPaymentApprovalStatus()) && !"REJECTED".equals(shop.getPaymentApprovalStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thông tin thanh toán không ở trạng thái chờ duyệt.");
        }

        int updated = shops.approvePaymentInfo(shopId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể phê duyệt thông tin thanh toán.");
        }

        // Gửi thông báo gian hàng đã chính thức mở bán
        try {
            notifications.notifyPaymentApproved(shop.getOwnerId(), shop.getId(), shop.getName());
        } catch (Exception ignored) {}
    }

    /**
     * Từ chối tài khoản ngân hàng thụ hưởng (kèm lý do không trùng khớp CCCD).
     */
    @Transactional
    public void rejectPayment(Long shopId, String reason) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Shop."));

        if (!"PENDING".equals(shop.getPaymentApprovalStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thông tin thanh toán không ở trạng thái chờ duyệt.");
        }

        String validReason = normalizeReason(reason);
        int updated = shops.rejectPaymentInfo(shopId, validReason);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể từ chối thông tin thanh toán.");
        }

        // Gửi thông báo yêu cầu người bán sửa lại tài khoản chính chủ
        try {
            notifications.notifyPaymentRejected(shop.getOwnerId(), shop.getId(), shop.getName(), validReason);
        } catch (Exception ignored) {}
    }

    /**
     * Hàm tiện ích kiểm tra và chuẩn hóa lý do từ chối (cắt khoảng trắng, kiểm tra rỗng, giới hạn độ dài).
     */
    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lý do từ chối không được vượt quá 1000 ký tự.");
        }
        return trimmed;
    }

    /**
     * Hàm tiện ích loại bỏ dấu tiếng Việt (Normalizer NFD) để đối soát tên chính xác:
     * Ví dụ: "Nguyễn Văn Ánh" -> "nguyen van anh" so khớp với "NGUYEN VAN ANH" của ngân hàng.
     */
    private String removeAccents(String str) {
        if (str == null) return "";
        String normalized = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
                .replace("đ", "d").replace("Đ", "D")
                .trim().toLowerCase();
    }
}
