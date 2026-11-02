package service.complaint;

import java.util.Locale;

/**
 * Mô hình trạng thái khiếu nại (Ban Vận hành thẩm định trước, Cửa hàng xử lý bồi hoàn).
 */
public enum ComplaintStatus {
    PENDING_OPERATIONS("PENDING_OPERATIONS", "Chờ Vận hành duyệt"),
    PENDING_SHOP("PENDING_SHOP", "Chờ Shop xử lý"),
    RESOLVED("RESOLVED", "Đã giải quyết"),
    REJECTED("REJECTED", "Từ chối khiếu nại"),
    CANCELLED("CANCELLED", "Đã rút khiếu nại"),
    // Trạng thái cũ giữ tương thích hiển thị nếu có bản ghi lịch sử
    ESCALATED("ESCALATED", "Đang xử lý");

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
        return this == PENDING_OPERATIONS || this == PENDING_SHOP || this == ESCALATED;
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == REJECTED || this == CANCELLED;
    }

    public static ComplaintStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING_OPERATIONS;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING_OPERATIONS", "PENDING_OPS" -> PENDING_OPERATIONS;
            case "PENDING_SHOP", "PENDING" -> PENDING_SHOP;
            case "RESOLVED" -> RESOLVED;
            case "REJECTED" -> REJECTED;
            case "CANCELLED" -> CANCELLED;
            case "ESCALATED", "IN_PROGRESS" -> ESCALATED;
            default -> throw new IllegalArgumentException("Trạng thái khiếu nại không hợp lệ: " + raw);
        };
    }
}
