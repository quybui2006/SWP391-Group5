package dto.shop;

public record InventoryVariantOption(Long inventoryId, Long variantId, String productName,
                                     String variantName, String sku, String unitName) { }
