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

    @Transactional(readOnly = true)
    public List<PendingShopApprovalDto> findPendingShops() {
        return shops.findPendingApprovals();
    }

    @Transactional(readOnly = true)
    public List<ShopApprovalItemDto> findShops(String status, String keyword) {
        List<ShopApprovalItemDto> list;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            list = shops.findAllWithOwners();
        } else {
            list = shops.findByApprovalStatusWithOwners(status.toUpperCase().trim());
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

        String provinceName = provinces.findById(shop.getProvinceCode())
                .map(Province::getName)
                .orElse(shop.getProvinceCode());
        dto.setProvinceName(provinceName);
        dto.setFullAddress(shop.getAddressDetail() + ", " + shop.getWardName() + ", " + provinceName);

        if (owner != null) {
            dto.setOwnerId(owner.getId());
            dto.setOwnerName(owner.getFullName());
            dto.setOwnerEmail(owner.getEmail());
            dto.setOwnerPhone(owner.getPhone());
            dto.setOwnerCreatedAt(owner.getCreatedAt());
            dto.setOwnerStatus(owner.getStatus());
        }

        // Query product count & orders count
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

    @Transactional
    public void approveShop(Long shopId) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu mở Shop."));

        if (shops.approvePending(shopId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Yêu cầu mở Shop không còn ở trạng thái chờ duyệt.");
        }

        // Assign SHOP_OWNER role in user_roles
        try {
            jdbc.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT ?, r.id FROM roles r WHERE r.code = 'SHOP_OWNER'
                ON DUPLICATE KEY UPDATE user_id = user_id
            """, shop.getOwnerId());
        } catch (Exception ex) {
            // Role may already exist
        }

        // Send Notification
        try {
            notifications.notifyShopApproved(shop.getOwnerId(), shop.getId(), shop.getName());
        } catch (Exception ignored) {}
    }

    @Transactional
    public void rejectShop(Long shopId, String rejectionReason) {
        String reason = normalizeReason(rejectionReason);
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu mở Shop."));

        if (shops.rejectPending(shopId, reason) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Yêu cầu mở Shop không còn ở trạng thái chờ duyệt.");
        }

        // Send Notification
        try {
            notifications.notifyShopRejected(shop.getOwnerId(), shop.getId(), shop.getName(), reason);
        } catch (Exception ignored) {}
    }

    @Transactional
    public void updateOperatingStatus(Long shopId, String operatingStatus, String reason) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Shop."));

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
}
