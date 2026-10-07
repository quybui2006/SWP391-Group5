package dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO dùng để nhận dữ liệu khi Shop Owner tạo sản phẩm mới.
 *
 * Không dùng Lombok ở class này để tránh lỗi getter/setter không được generate
 * khi IDE hoặc Maven chưa bật annotation processing.
 */
public class ProductCreateRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(
            min = 5,
            max = 100,
            message = "Tên sản phẩm phải từ 5-100 ký tự"
    )
    private String name;

    private String description;

    @Size(
            max = 150,
            message = "Xuất xứ không được vượt quá 150 ký tự"
    )
    private String origin;

    @NotBlank(message = "Mã lô không được để trống")
    @Size(
            max = 60,
            message = "Mã lô không được vượt quá 60 ký tự"
    )
    private String batchCode;

    @NotNull(message = "Vui lòng nhập ngày nhập")
    private LocalDate receivedDate;

    @NotNull(message = "Vui lòng nhập ngày hết hạn")
    private LocalDate expiryDate;

    @NotNull(message = "Giá sản phẩm không được để trống")
    @DecimalMin(
            value = "1000",
            inclusive = true,
            message = "Giá tối thiểu là 1000 VNĐ"
    )
    private BigDecimal price;

    @NotNull(message = "Vui lòng chọn danh mục")
    private Long categoryId;

    @NotBlank(message = "SKU không được để trống")
    @Size(
            max = 80,
            message = "SKU không được vượt quá 80 ký tự"
    )
    private String sku;

    @NotBlank(message = "Tên biến thể không được để trống")
    @Size(
            max = 150,
            message = "Tên biến thể không được vượt quá 150 ký tự"
    )
    private String variantName;

    @NotNull(message = "Vui lòng chọn đơn vị")
    private Long unitId;

    @NotNull(message = "Vui lòng nhập số lượng tồn kho")
    @Min(
            value = 1,
            message = "Số lượng tồn kho phải lớn hơn 0"
    )
    private Integer stockQuantity;

    @NotNull(message = "Vui lòng nhập ngưỡng tồn kho")
    @DecimalMin(
            value = "0.0",
            inclusive = true,
            message = "Ngưỡng tồn kho không được âm"
    )
    @DecimalMax(
            value = "100.0",
            inclusive = true,
            message = "Ngưỡng tồn kho không được lớn hơn 100%"
    )
    private BigDecimal lowStockThresholdPct;

    /**
     * File ảnh sản phẩm. ServiceImpl sẽ kiểm tra null/rỗng, dung lượng và định dạng.
     */
    private MultipartFile imageFile;

    public ProductCreateRequest() {
    }

    public ProductCreateRequest(
            String name,
            String description,
            String origin,
            String batchCode,
            LocalDate receivedDate,
            LocalDate expiryDate,
            BigDecimal price,
            Long categoryId,
            String sku,
            String variantName,
            Long unitId,
            Integer stockQuantity,
            BigDecimal lowStockThresholdPct,
            MultipartFile imageFile
    ) {
        this.name = name;
        this.description = description;
        this.origin = origin;
        this.batchCode = batchCode;
        this.receivedDate = receivedDate;
        this.expiryDate = expiryDate;
        this.price = price;
        this.categoryId = categoryId;
        this.sku = sku;
        this.variantName = variantName;
        this.unitId = unitId;
        this.stockQuantity = stockQuantity;
        this.lowStockThresholdPct = lowStockThresholdPct;
        this.imageFile = imageFile;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDate receivedDate) {
        this.receivedDate = receivedDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public BigDecimal getLowStockThresholdPct() {
        return lowStockThresholdPct;
    }

    public void setLowStockThresholdPct(BigDecimal lowStockThresholdPct) {
        this.lowStockThresholdPct = lowStockThresholdPct;
    }

    public MultipartFile getImageFile() {
        return imageFile;
    }

    public void setImageFile(MultipartFile imageFile) {
        this.imageFile = imageFile;
    }
}
