package dto.operations;

import java.util.List;

public record ManagedShopProduct(
        Long productId,
        String productName,
        String imageUrl,
        List<ManagedShopVariant> variants) { }
