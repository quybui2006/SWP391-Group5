package dto.customer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class NotificationDto {
    private Long id;
    private String eventKey;
    private String type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private String targetUrl;
    private boolean read;
    private String createdAtFormatted;
    private String timeAgo;
    private String icon;
    private String badgeClass;

    public NotificationDto() {
    }

    public static NotificationDto fromEntity(entity.Notification n) {
        NotificationDto dto = new NotificationDto();
        dto.setId(n.getId());
        dto.setEventKey(n.getEventKey());
        dto.setType(n.getType());
        dto.setTitle(n.getTitle());
        dto.setContent(n.getContent());
        dto.setTargetType(n.getTargetType());
        dto.setTargetId(n.getTargetId());
        dto.setRead(n.isRead());

        // Target URL
        if ("POST_AUDIT".equalsIgnoreCase(n.getTargetType()) || "POST_AUDIT".equalsIgnoreCase(n.getType())) {
            dto.setTargetUrl("/operations/post-audit");
        } else if ("SHOP_APPROVAL".equalsIgnoreCase(n.getTargetType()) || "SHOP_APPROVAL".equalsIgnoreCase(n.getType())) {
            dto.setTargetUrl(n.getTargetId() != null ? "/operations/shops/" + n.getTargetId() : "/operations/shops");
        } else if ("PRODUCT".equalsIgnoreCase(n.getTargetType()) && n.getTargetId() != null) {
            dto.setTargetUrl("/product/" + n.getTargetId());
        } else if ("ORDER".equalsIgnoreCase(n.getTargetType())) {
            dto.setTargetUrl("/customer/account/orders");
        } else if ("ACCOUNT".equalsIgnoreCase(n.getTargetType())) {
            dto.setTargetUrl("/customer/account");
        } else if ("SHOP".equalsIgnoreCase(n.getTargetType())) {
            if ("SHOP_APPROVAL".equalsIgnoreCase(n.getType())) {
                dto.setTargetUrl(n.getTargetId() != null ? "/operations/shops/" + n.getTargetId() : "/operations/shops");
            } else {
                dto.setTargetUrl("/seller/status");
            }
        } else {
            dto.setTargetUrl("/customer/account/notifications");
        }

        // Formatted time and relative time
        LocalDateTime created = n.getCreatedAt() != null ? n.getCreatedAt() : LocalDateTime.now();
        dto.setCreatedAtFormatted(created.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")));
        dto.setTimeAgo(formatTimeAgo(created));

        // Icon & badge class by type
        String typeUpper = n.getType() != null ? n.getType().toUpperCase() : "SYSTEM";
        switch (typeUpper) {
            case "POST_AUDIT":
                dto.setIcon("🔍");
                dto.setBadgeClass("notif-badge-account");
                break;
            case "SHOP_APPROVAL":
                dto.setIcon("🏪");
                dto.setBadgeClass("notif-badge-shop");
                break;
            case "PROMOTION":
            case "PROMO":
                dto.setIcon("🎁");
                dto.setBadgeClass("notif-badge-promo");
                break;
            case "ORDER":
                dto.setIcon("📦");
                dto.setBadgeClass("notif-badge-order");
                break;
            case "ACCOUNT":
                dto.setIcon("👤");
                dto.setBadgeClass("notif-badge-account");
                break;
            case "PRODUCT":
                dto.setIcon("🍊");
                dto.setBadgeClass("notif-badge-product");
                break;
            case "SHOP":
            case "APPROVAL":
                dto.setIcon("🏪");
                dto.setBadgeClass("notif-badge-shop");
                break;
            default:
                dto.setIcon("🔔");
                dto.setBadgeClass("notif-badge-system");
                break;
        }

        return dto;
    }

    private static String formatTimeAgo(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(dateTime, now);
        if (minutes < 1) {
            return "Vừa xong";
        }
        if (minutes < 60) {
            return minutes + " phút trước";
        }
        long hours = ChronoUnit.HOURS.between(dateTime, now);
        if (hours < 24) {
            return hours + " giờ trước";
        }
        long days = ChronoUnit.DAYS.between(dateTime, now);
        if (days < 7) {
            return days + " ngày trước";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getCreatedAtFormatted() {
        return createdAtFormatted;
    }

    public void setCreatedAtFormatted(String createdAtFormatted) {
        this.createdAtFormatted = createdAtFormatted;
    }

    public String getTimeAgo() {
        return timeAgo;
    }

    public void setTimeAgo(String timeAgo) {
        this.timeAgo = timeAgo;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public void setBadgeClass(String badgeClass) {
        this.badgeClass = badgeClass;
    }
}
