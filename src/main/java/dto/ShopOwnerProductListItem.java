package dto;

import java.math.BigDecimal;

public record ShopOwnerProductListItem(Long id, String name, String categoryName,
                                       String imageUrl, String approvalStatus,
                                       String sellingStatus, int variantCount,
                                       int availableStock, BigDecimal fromPrice) { }
