package service;

import dto.NotificationDto;
import entity.Notification;
import entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.NotificationRepository;
import repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
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
        long count = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).size();
        if (count > 0) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
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
}
