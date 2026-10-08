package dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Dữ liệu được phép sửa của Product; variant được quản lý ở module Product Variants. */
public class ShopOwnerProductForm {
    private String name;
    private String description;
    private String origin;
    private Long categoryId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;
    private String approvalStatus;
    private String sellingStatus;

    public static ShopOwnerProductForm from(ShopOwnerProductView source) {
        ShopOwnerProductForm form = new ShopOwnerProductForm();
        form.setName(source.getName());
        form.setDescription(source.getDescription());
        form.setOrigin(source.getOrigin());
        form.setCategoryId(source.getCategoryId());
        form.setReceivedDate(source.getReceivedDate());
        form.setExpiryDate(source.getExpiryDate());
        form.setApprovalStatus(source.getApprovalStatus());
        form.setSellingStatus(source.getSellingStatus());
        return form;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getSellingStatus() { return sellingStatus; }
    public void setSellingStatus(String sellingStatus) { this.sellingStatus = sellingStatus; }
}
