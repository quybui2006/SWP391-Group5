package dto.operations;

import java.math.BigDecimal;

public record ManagedShopVariant(
        Long variantId,
        String productName,
        String variantName,
        String sku,
        String imageUrl,
        BigDecimal price,
        String unitName,
        String variantStatus,
        String approvalStatus,
        Integer quantityOnHand,
        Integer availableQuantity,
        String batchCode) { }
