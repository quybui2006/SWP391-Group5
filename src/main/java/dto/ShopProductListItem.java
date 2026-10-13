package dto;

import java.time.LocalDateTime;

public record ShopProductListItem(Long id, String name, String imageUrl, Integer variantCount,
                                  String categoryName, Integer stockQuantity, String status,
                                  String approvalStatus, String rejectionReason, LocalDateTime createdAt) { }
