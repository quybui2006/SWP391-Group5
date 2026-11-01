package service.complaint;

import java.util.Locale;

/**
 * Mô hình trạng thái khiếu nại 2 tầng (Shop xử lý trước -> CSKH phân xử sau).
 */
public enum ComplaintStatus {
    PENDING_SHOP("PENDING_SHOP", "Chờ Shop phản hồi"),
    ESCALATED("ESCALATED", "Chuyển CSKH phân xử"),
    RESOLVED("RESOLVED", "Đã giải quyết"),
    REJECTED("REJECTED", "Bác bỏ khiếu nại"),
    CANCELLED("CANCELLED", "Đã rút khiếu nại");

    private final String code;
    private final String label;

    ComplaintStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return this == PENDING_SHOP || this == ESCALATED;
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == REJECTED || this == CANCELLED;
    }

    public static ComplaintStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING_SHOP;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING_SHOP", "PENDING" -> PENDING_SHOP;
            case "ESCALATED", "IN_PROGRESS" -> ESCALATED;
            case "RESOLVED" -> RESOLVED;
            case "REJECTED" -> REJECTED;
            case "CANCELLED" -> CANCELLED;
            default -> throw new IllegalArgumentException("Trạng thái khiếu nại không hợp lệ: " + raw);
        };
    }
}
