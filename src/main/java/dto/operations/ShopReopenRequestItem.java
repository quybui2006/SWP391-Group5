package dto.operations;

import java.time.LocalDateTime;

public record ShopReopenRequestItem(
        Long id,
        Long shopId,
        String shopName,
        String ownerName,
        String ownerEmail,
        String explanation,
        String status,
        String operationReason,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt) { }
