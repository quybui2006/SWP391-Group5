package dto.operations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Thông tin rút gọn của biến thể chờ Operations Manager kiểm duyệt. */
public record PendingVariantApprovalDto(
        Long id,
        String variantName,
        String sku,
        String imageUrl,
        String productName,
        String shopName,
        String categoryName,
        BigDecimal price,
        String unitName,
        Integer quantityOnHand,
        Integer availableQuantity,
        String batchCode,
        LocalDate expiryDate,
        LocalDateTime submittedAt
) { }
