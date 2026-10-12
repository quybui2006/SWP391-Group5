package dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShopProductListItem(Long id, String name, String imageUrl, String sku,
                                  String categoryName, BigDecimal price, Integer stockQuantity,
                                  String status, LocalDateTime createdAt) { }
