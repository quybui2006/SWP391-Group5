package dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** View model riêng cho màn hình Shop Owner Product Detail. */
public class ShopOwnerProductView {
    private Long id;
    private String shopName;
    private String name;
    private String description;
    private String origin;
    private String batchCode;
    private LocalDate receivedDate;
    private LocalDate expiryDate;
    private String approvalStatus;
    private String sellingStatus;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
    private List<VariantRow> variants = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getSellingStatus() { return sellingStatus; }
    public void setSellingStatus(String sellingStatus) { this.sellingStatus = sellingStatus; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public List<VariantRow> getVariants() { return variants; }
    public void setVariants(List<VariantRow> variants) { this.variants = variants; }

    public record VariantRow(Long id, String name, String sku, String specification,
                             BigDecimal price, String unitName, String status,
                             int quantityOnHand, int reservedQuantity,
                             int availableQuantity, boolean lowStock) { }
}
