package dto.shop;

import java.util.List;

public record InventoryVariantView(Long inventoryId, Long id, String name, String sku, String imageUrl, String unitName,
                                   int onHand, int reserved, int available, int sold, int lowStockThresholdQuantity,
                                   List<InventoryBatchView> batches) { }
