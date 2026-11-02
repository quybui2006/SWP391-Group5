package dto.shop;

import java.time.LocalDateTime;

/** Dòng lịch sử giao dịch tồn kho cho giao diện Shop Owner. */
public record InventoryTransactionRowDto(
        Long id,
        LocalDateTime createdAt,
        String type,
        Integer onHandDelta,
        Integer onHandAfter,
        String reason,
        String productName,
        String variantName,
        String sku,
        String batchCode) {
}
