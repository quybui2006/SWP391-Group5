package dto.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/** Dữ liệu cho màn hình riêng Biến thể và tồn kho. */
public class ProductVariantCreateRequest {
    @NotNull private Long productId;
    @NotBlank @Size(max = 150) private String variantName;
    @NotNull private Long unitId;
    @NotNull @jakarta.validation.constraints.DecimalMin("1") private BigDecimal price;
    @NotNull @Positive private Integer stockQuantity;
    @jakarta.validation.constraints.PositiveOrZero private Integer lowStockThresholdQuantity;
    @NotNull private MultipartFile imageFile;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }
    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
    public Integer getLowStockThresholdQuantity() { return lowStockThresholdQuantity; }
    public void setLowStockThresholdQuantity(Integer value) { this.lowStockThresholdQuantity = value; }
    public MultipartFile getImageFile() { return imageFile; }
    public void setImageFile(MultipartFile imageFile) { this.imageFile = imageFile; }
}
