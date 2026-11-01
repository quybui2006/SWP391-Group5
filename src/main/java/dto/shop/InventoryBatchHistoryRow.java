package dto.shop;

import java.time.LocalDateTime;

public record InventoryBatchHistoryRow(Long id, LocalDateTime createdAt, String type, int onHandDelta,
                                       int reservedDelta, int onHandAfter, int reservedAfter,
                                       String reason, String productName, String variantName,
                                       String sku, String unitName, String batchCode, String orderCode) {
    public String typeLabel() {
        return switch (type) {
            case "OPENING" -> "Tạo lô";
            case "RECEIVE" -> "Nhập hàng";
            case "ADJUST" -> "Điều chỉnh";
            case "RESERVE" -> "Giữ hàng";
            case "RELEASE" -> "Trả hàng giữ";
            case "SALE" -> "Đã bán";
            case "DISCARD" -> "Loại bỏ";
            default -> "Khác";
        };
    }
}
