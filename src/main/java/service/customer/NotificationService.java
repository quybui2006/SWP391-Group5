package service.customer;

import dto.customer.NotificationDto;
import entity.Notification;
import entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.NotificationRepository;
import repository.ShopRepository;
import repository.UserRepository;
import repository.UserRoleRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ShopRepository shopRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               UserRoleRepository userRoleRepository,
                               ShopRepository shopRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.shopRepository = shopRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getRecentNotifications(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        return notificationRepository.findTop15ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(limit > 0 ? limit : 10)
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getAllNotifications(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public boolean markAsRead(Long notificationId, Long userId) {
        if (notificationId == null || userId == null) {
            return false;
        }
        Optional<Notification> optional = notificationRepository.findByIdAndUserId(notificationId, userId);
        if (optional.isPresent()) {
            Notification notification = optional.get();
            if (!notification.isRead()) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
            return true;
        }
        return false;
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        if (userId == null) {
            return;
        }
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public Notification createNotification(Long userId, String type, String title, String content, String targetType, Long targetId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        String eventKey = "EVT-" + UUID.randomUUID().toString();
        Notification notification = new Notification(user, eventKey, type, title, content, targetType, targetId);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void ensureSampleNotificationsIfEmpty(Long userId) {
        if (userId == null) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        boolean isOperations = userRoleRepository.existsByUser_IdAndRole_Code(userId, "OPERATIONS_MANAGER");
        if (isOperations) {
            // Xóa các thông báo rác kiểu khách hàng (voucher/mua hàng) nếu trước đó từng bị gán nhầm
            List<Notification> currentNotifs = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
            boolean hasCustomerNoise = currentNotifs.stream().anyMatch(n ->
                    "PROMOTION".equalsIgnoreCase(n.getType()) ||
                    "ORDER".equalsIgnoreCase(n.getType()) ||
                    (n.getEventKey() != null && (
                            n.getEventKey().startsWith("WELCOME-") ||
                            n.getEventKey().startsWith("ORDER-DEMO-") ||
                            n.getEventKey().startsWith("PROD-ARRIVE-") ||
                            n.getEventKey().startsWith("SYSTEM-TIP-")
                    ))
            );
            if (hasCustomerNoise) {
                notificationRepository.deleteAll(currentNotifs);
                currentNotifs = List.of();
            }

            if (!currentNotifs.isEmpty()) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            // 1. Nhóm thông báo: Yêu cầu duyệt mở Shop
            List<dto.operations.PendingShopApprovalDto> pending = shopRepository.findPendingApprovals();
            if (pending != null && !pending.isEmpty()) {
                for (dto.operations.PendingShopApprovalDto p : pending) {
                    Notification n = new Notification(
                            user,
                            "OP-PENDING-" + p.getId() + "-" + userId,
                            "SHOP_APPROVAL",
                            "Yêu cầu duyệt mở Shop: " + p.getName(),
                            "Chủ cửa hàng " + (p.getOwnerName() != null ? p.getOwnerName() : "Đối tác") +
                                    " vừa nộp hồ sơ xin mở shop '" + p.getName() + "'. Vui lòng kiểm tra giấy tờ pháp lý và thẩm định phê duyệt.",
                            "SHOP_APPROVAL",
                            p.getId()
                    );
                    n.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt() : now.minusMinutes(20));
                    n.setRead(false);
                    notificationRepository.save(n);
                }
            } else {
                Notification n1 = new Notification(
                        user,
                        "OP-PENDING-DEMO1-" + userId,
                        "SHOP_APPROVAL",
                        "Yêu cầu duyệt mở Shop: Nông Trại Xanh Đà Lạt",
                        "Chủ cửa hàng Trần Văn Hưng vừa nộp hồ sơ xin cấp phép kinh doanh gian hàng hoa quả tươi sạch. Hồ sơ đang chờ thẩm định thông tin pháp lý.",
                        "SHOP_APPROVAL",
                        1L
                );
                n1.setCreatedAt(now.minusMinutes(35));
                n1.setRead(false);
                notificationRepository.save(n1);

                Notification n2 = new Notification(
                        user,
                        "OP-PENDING-DEMO2-" + userId,
                        "SHOP_APPROVAL",
                        "Hồ sơ mở Shop chờ duyệt: Hoa Quả Nhập Khẩu GreenLife",
                        "Hồ sơ mở shop mới đã chờ thẩm định hơn 16 giờ (SLA chuẩn < 24h). Vui lòng ưu tiên xử lý hồ sơ sớm.",
                        "SHOP_APPROVAL",
                        2L
                );
                n2.setCreatedAt(now.minusHours(4));
                n2.setRead(false);
                notificationRepository.save(n2);
            }

            // 2. Nhóm thông báo: Giải trình hậu kiểm Cửa hàng (Post-audit)
            Notification n3 = new Notification(
                    user,
                    "OP-POST-AUDIT-1-" + userId,
                    "POST_AUDIT",
                    "Tiếp nhận giải trình hậu kiểm: Siêu Thị Trái Cây Tươi",
                    "Cửa hàng 'Siêu Thị Trái Cây Tươi' đã nộp biên bản giải trình và tài liệu kiểm định chất lượng lô hàng cam sành theo thông báo hậu kiểm của sàn.",
                    "POST_AUDIT",
                    null
            );
            n3.setCreatedAt(now.minusHours(8));
            n3.setRead(false);
            notificationRepository.save(n3);

            Notification n4 = new Notification(
                    user,
                    "OP-POST-AUDIT-2-" + userId,
                    "POST_AUDIT",
                    "Cập nhật hồ sơ giải trình hậu kiểm định kỳ",
                    "Hệ thống ghi nhận đối tác Shop Vựa Hoa Quả Miền Tây đã cập nhật giấy chứng nhận kiểm dịch thực vật và hóa đơn nguồn gốc hoa quả nhập khẩu.",
                    "POST_AUDIT",
                    null
            );
            n4.setCreatedAt(now.minusDays(1));
            n4.setRead(true);
            notificationRepository.save(n4);

            return;
        }

        // Logic mặc định cho khách hàng thường / người mua
        long count = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).size();
        if (count > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. Voucher Chào mừng
        Notification n1 = new Notification(
                user,
                "WELCOME-" + userId,
                "PROMOTION",
                "Tặng bạn voucher 20.000đ cho đơn hàng đầu tiên!",
                "Chào mừng bạn gia nhập FreshFruit! Nhập mã TƯƠI20 khi thanh toán để được giảm 20.000đ cho giỏ hàng trái cây tươi bất kỳ.",
                "PRODUCT",
                1L
        );
        n1.setCreatedAt(now.minusMinutes(12));
        n1.setRead(false);
        notificationRepository.save(n1);

        // 2. Cập nhật Đơn hàng
        Notification n2 = new Notification(
                user,
                "ORDER-DEMO-" + userId,
                "ORDER",
                "Đơn hàng trái cây tươi sẵn sàng giao đến bạn",
                "Đơn hàng của bạn đang được cửa hàng đóng gói cẩn thận với đá gel bảo quản và chuẩn bị bàn giao cho shipper.",
                "ORDER",
                null
        );
        n2.setCreatedAt(now.minusHours(2));
        n2.setRead(false);
        notificationRepository.save(n2);

        // 3. Trái cây tươi mới về
        Notification n3 = new Notification(
                user,
                "PROD-ARRIVE-" + userId,
                "PRODUCT",
                "Cam Vàng Navel & Táo Fuji tươi mới về hôm nay!",
                "Trái cây nhập khẩu tươi giòn, mọng nước, chuẩn chất lượng đã sẵn sàng phục vụ gia đình bạn với giá ưu đãi.",
                "PRODUCT",
                3L
        );
        n3.setCreatedAt(now.minusDays(1));
        n3.setRead(true);
        notificationRepository.save(n3);

        // 4. Mẹo dinh dưỡng & Hệ thống
        Notification n4 = new Notification(
                user,
                "SYSTEM-TIP-" + userId,
                "SYSTEM",
                "Nạp năng lượng và Vitamin C mỗi ngày cùng FreshFruit",
                "Thói quen ăn 1-2 phần trái cây tươi mỗi ngày giúp nâng cao đề kháng và giữ vóc dáng khỏe đẹp. Khám phá thực đơn hoa quả ngay!",
                "URL",
                null
        );
        n4.setCreatedAt(now.minusDays(2));
        n4.setRead(true);
        notificationRepository.save(n4);
    }

    @Transactional
    public void notifyOperationsNewShopRegistration(Long shopId, String shopName, String ownerName) {
        List<User> operationsUsers = userRoleRepository.findUsersByRoleCode("OPERATIONS_MANAGER");
        if (operationsUsers.isEmpty()) {
            userRepository.findByEmail("operations.manager@freshfruit.local").ifPresent(operationsUsers::add);
        }
        for (User opUser : operationsUsers) {
            String eventKey = "OP-SHOP-REQ-" + shopId + "-" + System.currentTimeMillis();
            String title = "Yêu cầu duyệt mở Shop: " + (shopName != null ? shopName : "Shop mới");
            String content = "Chủ shop " + (ownerName != null && !ownerName.isBlank() ? ownerName : "Đối tác") +
                    " vừa gửi hồ sơ mở shop '" + shopName + "'. Vui lòng kiểm tra và thẩm định hồ sơ.";
            Notification notif = new Notification(opUser, eventKey, "SHOP_APPROVAL", title, content, "SHOP_APPROVAL", shopId);
            notificationRepository.save(notif);
        }
    }

    @Transactional
    public void notifyOperationsPostAuditExplanation(Long shopId, String shopName, String summary) {
        List<User> operationsUsers = userRoleRepository.findUsersByRoleCode("OPERATIONS_MANAGER");
        if (operationsUsers.isEmpty()) {
            userRepository.findByEmail("operations.manager@freshfruit.local").ifPresent(operationsUsers::add);
        }
        for (User opUser : operationsUsers) {
            String eventKey = "OP-POST-AUDIT-" + (shopId != null ? shopId : 0) + "-" + System.currentTimeMillis();
            String title = "Tiếp nhận giải trình hậu kiểm" + (shopName != null && !shopName.isBlank() ? ": " + shopName : "");
            String content = summary != null && !summary.isBlank() ? summary :
                    "Cửa hàng đã nộp văn bản giải trình và tài liệu kiểm định theo yêu cầu hậu kiểm của sàn.";
            Notification notif = new Notification(opUser, eventKey, "POST_AUDIT", title, content, "POST_AUDIT", shopId);
            notificationRepository.save(notif);
        }
    }

    @Transactional
    public void notifyShopRegistrationSubmitted(Long userId, Long shopId, String shopName) {
        createNotification(
                userId,
                "SHOP",
                "Hồ sơ mở Shop đã gửi thành công!",
                "Hồ sơ mở cửa hàng '" + shopName + "' của bạn đã được tiếp nhận và đang được Ban vận hành FreshFruit xét duyệt.",
                "SHOP",
                shopId
        );
    }

    @Transactional
    public void notifyShopApproved(Long userId, Long shopId, String shopName) {
        createNotification(
                userId,
                "SHOP",
                "Chúc mừng! Shop '" + shopName + "' đã được phê duyệt 🎉",
                "Cửa hàng của bạn đã được kiểm duyệt và kích hoạt thành công trên hệ thống. Bây giờ bạn có thể truy cập Kênh người bán để bắt đầu đăng sản phẩm!",
                "SHOP",
                shopId
        );
    }

    @Transactional
    public void notifyShopRejected(Long userId, Long shopId, String shopName, String reason) {
        createNotification(
                userId,
                "SHOP",
                "Yêu cầu mở Shop '" + shopName + "' chưa được phê duyệt",
                "Ban vận hành thông báo hồ sơ mở shop của bạn chưa đạt yêu cầu. Lý do: " + (reason != null ? reason : "Thông tin chưa đầy đủ.") + ". Bạn có thể chỉnh sửa và nộp lại hồ sơ.",
                "SHOP",
                shopId
        );
    }

    @Transactional
    public void notifyPaymentApproved(Long userId, Long shopId, String shopName) {
        createNotification(
                userId,
                "SHOP",
                "Thông tin thanh toán đã được duyệt thành công! 🎉",
                "Tài khoản ngân hàng của cửa hàng '" + shopName + "' đã được Ban vận hành đối chiếu khớp với CCCD và phê duyệt. Cửa hàng của bạn hiện đã MỞ BÁN chính thức!",
                "SHOP",
                shopId
        );
    }

    @Transactional
    public void notifyPaymentRejected(Long userId, Long shopId, String shopName, String reason) {
        createNotification(
                userId,
                "SHOP",
                "Thông tin thanh toán cần chỉnh sửa lại ⚠️",
                "Thông tin tài khoản ngân hàng của cửa hàng '" + shopName + "' chưa được phê duyệt. Lý do: " + (reason != null ? reason : "Tên chủ tài khoản không khớp với CCCD.") + ". Vui lòng kiểm tra và cập nhật lại.",
                "SHOP",
                shopId
        );
    }

    @Transactional
    public void notifyOrderStatusChanged(Long userId, Long orderId, String orderCode, String statusText) {
        createNotification(
                userId,
                "ORDER",
                "Cập nhật trạng thái đơn hàng " + orderCode,
                "Đơn hàng " + orderCode + " của bạn hiện đang ở trạng thái: " + statusText + ".",
                "ORDER",
                orderId
        );
    }
}
