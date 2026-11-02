package dto.shop;

public record InventoryBatchOption(Long id, String code, String productName, String variantName,
                                   String sku, String unitName, int available) { }
