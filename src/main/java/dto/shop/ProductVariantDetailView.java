package dto.shop;

import java.math.BigDecimal;

public record ProductVariantDetailView(Long id, String productName, String variantName, String sku,
                                       String specification, BigDecimal price, String unitName, String status,
                                       Integer quantityOnHand, Integer reservedQuantity, Integer availableQuantity,
                                       BigDecimal lowStockThresholdPct) { }
