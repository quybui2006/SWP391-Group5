package dto.shop;

import java.time.LocalDateTime;

public record ShopReopenRequestHistory(
        Long id,
        String explanation,
        String status,
        String operationReason,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt) { }
