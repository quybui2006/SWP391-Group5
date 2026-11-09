package service.complaint;

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
}
