package dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponseDTO {

    private Long id;

    private String name;

    private String sku;

    private Long categoryId;

    private BigDecimal price;

    private String unit;

    private Integer stockQuantity;

    private String imageUrl;

    private String status;

    private LocalDateTime createdAt;
}