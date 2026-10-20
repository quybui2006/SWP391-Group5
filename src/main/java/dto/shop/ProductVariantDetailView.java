package dto.shop;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductVariantDetailView(
        Long id,
        String productName,
        String variantName,
        String sku,
        String specification,
        BigDecimal price,
        String unitName,
        String imageUrl,
        String status,
        String approvalStatus,
        String rejectionReason,
        LocalDateTime submittedAt,
        Integer quantityOnHand,
        Integer reservedQuantity,
        Integer availableQuantity,
        BigDecimal lowStockThresholdPct
) { }
