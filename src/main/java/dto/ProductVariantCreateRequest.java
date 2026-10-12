package dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    @NotBlank @Size(max = 80) private String sku;
    @NotNull private Long unitId;
    @NotNull @DecimalMin("1") private BigDecimal price;
    @NotNull @Positive private Integer stockQuantity;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal lowStockThresholdPct;
    @NotNull private MultipartFile imageFile;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
    public BigDecimal getLowStockThresholdPct() { return lowStockThresholdPct; }
    public void setLowStockThresholdPct(BigDecimal lowStockThresholdPct) { this.lowStockThresholdPct = lowStockThresholdPct; }
    public MultipartFile getImageFile() { return imageFile; }
    public void setImageFile(MultipartFile imageFile) { this.imageFile = imageFile; }
}
