package dto.operations;

import java.util.List;

public record OperationsCatalogProduct(
        Long productId,
        String productName,
        Long shopId,
        String shopName,
        String productStatus,
        boolean visible,
        String imageUrl,
        List<ManagedShopVariant> variants) { }
