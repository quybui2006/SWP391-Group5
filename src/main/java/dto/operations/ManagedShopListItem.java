package dto.operations;

import java.time.LocalDateTime;

public record ManagedShopListItem(
        Long id,
        String name,
        String ownerName,
        String ownerEmail,
        String phone,
        String address,
        String approvalStatus,
        String operatingStatus,
        String suspensionReason,
        Long productCount,
        Long variantCount,
        LocalDateTime createdAt) { }
