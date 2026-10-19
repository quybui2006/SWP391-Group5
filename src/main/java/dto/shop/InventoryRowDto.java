package dto.shop;

import java.time.LocalDate;

/** Một dòng tồn kho đã ghép thông tin sản phẩm, biến thể và lô hàng. */
public record InventoryRowDto(
        Long inventoryId,
        Long variantId,
        String productName,
        String variantName,
        String sku,
        String batchCode,
        LocalDate expiryDate,
        Integer quantityOnHand,
        Integer reservedQuantity,
        Integer availableQuantity) {
}
