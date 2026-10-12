package dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu cho màn hình Edit Product (Sheet2 No.8).
 *
 * Giai đoạn làm giao diện: controller điền dữ liệu mẫu, chưa truy vấn
 * database. Sau này thay bằng service thật.
 */
public class ProductEditView {

    private Long id;
    private String name;
    private String origin;
    private String description;

    /** Mã lô nhập kho - không cho sửa (UNIQUE shop_id + batch_code). */
    private String batchCode;

    /** Thông tin chỉ đọc để Shop Owner nắm trạng thái duyệt. */
    private String approvalStatus;
    private String sellingStatus;
    private String shopName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;

    private List<CategoryOption> categories;
    private Long categoryId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getSellingStatus() { return sellingStatus; }
    public void setSellingStatus(String sellingStatus) { this.sellingStatus = sellingStatus; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public List<CategoryOption> getCategories() { return categories; }
    public void setCategories(List<CategoryOption> categories) { this.categories = categories; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    /** Một lựa chọn trong ô chọn danh mục. */
    public record CategoryOption(Long id, String name) {
    }
}
