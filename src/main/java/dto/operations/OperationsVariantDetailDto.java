package dto.operations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Chi tiết đầy đủ để Operations Manager kiểm tra một biến thể trước khi duyệt. */
public record OperationsVariantDetailDto(
        Long id,
        Long productId,
        String variantName,
        String sku,
        String specification,
        String imageUrl,
        BigDecimal price,
        Integer minOrderQuantity,
        String unitName,
        String variantStatus,
        String approvalStatus,
        String rejectionReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String productName,
        String productDescription,
        String categoryName,
        String origin,
        String batchCode,
        LocalDate receivedDate,
        LocalDate expiryDate,
        String shopName,
        String shopPhone,
        String shopAddress,
        Integer initialQuantity,
        Integer quantityOnHand,
        Integer reservedQuantity,
        Integer availableQuantity,
        BigDecimal lowStockThresholdPct,
        String inventoryStatus
) { }
