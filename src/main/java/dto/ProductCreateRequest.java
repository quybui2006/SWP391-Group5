package dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductCreateRequest {
    @NotBlank @Size(max = 200) private String name;
    @Size(max = 150) private String origin;
    @Size(max = 2000) private String description;
    @NotBlank @Size(max = 60) private String batchCode;
    @NotNull @PastOrPresent private LocalDate receivedDate;
    @NotNull private LocalDate expiryDate;
    @NotNull private Long categoryId;
    @NotBlank @Size(max = 150) private String variantName;
    @NotBlank @Size(max = 80) private String sku;
    @NotNull private Long unitId;
    @NotNull @DecimalMin("1") private BigDecimal price;
    @NotNull @Positive private Integer stockQuantity;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal lowStockThresholdPct;
    @NotNull private MultipartFile imageFile;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
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
