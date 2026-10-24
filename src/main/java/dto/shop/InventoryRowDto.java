package dto.shop;

/** Một dòng tồn kho đã ghép thông tin sản phẩm, biến thể và lô hàng. */
public record InventoryRowDto(
        Long inventoryId,
        Long variantId,
        String productName,
        String variantName,
        String sku,
        String batchCode,
        Integer quantityOnHand,
        Integer availableQuantity) {
}
