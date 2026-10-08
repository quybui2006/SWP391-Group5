package dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ProductDetailView {

    private Long id;
    private String name;
    private String categoryName;
    private String description;
    private String origin;
    private String batchCode;
    private String shopName;

    private String imageUrl;
    private List<String> imageUrls;

    private BigDecimal price;
    private String unitName;

    private String approvalStatus;

    private String sellingStatus;

    private String rejectionReason;

    private LocalDate receivedDate;
    private LocalDate expiryDate;

    private long daysUntilExpiry;
    private boolean expiringSoon;

    private List<VariantRow> variants;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getSellingStatus() { return sellingStatus; }
    public void setSellingStatus(String sellingStatus) { this.sellingStatus = sellingStatus; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public long getDaysUntilExpiry() { return daysUntilExpiry; }
    public void setDaysUntilExpiry(long daysUntilExpiry) { this.daysUntilExpiry = daysUntilExpiry; }
    public boolean isExpiringSoon() { return expiringSoon; }
    public void setExpiringSoon(boolean expiringSoon) { this.expiringSoon = expiringSoon; }
    public List<VariantRow> getVariants() { return variants; }
    public void setVariants(List<VariantRow> variants) { this.variants = variants; }

    public record VariantRow(Long id, String sku, String name, String specification,
                             String status, BigDecimal price, String unitName,
                             int quantityOnHand, int reservedQuantity,
                             int availableQuantity, boolean lowStock) {
    }
}
