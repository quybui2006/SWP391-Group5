package entity;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "selling_status", nullable = false)
    private String sellingStatus;

    // Quan hệ 1-Nhiều với ProductVariant
    @OneToMany(mappedBy = "product")
    private List<ProductVariant> variants;

    // Quan hệ Nhiều-Nhiều với Category
    @ManyToMany
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories;

    // ============================================================
    //  Các trường bổ sung cho màn hình Shop Owner (Sheet2 No.7, No.8)
    //  Bổ sung ngày 08/10/2026. Không sửa các trường phía trên.
    //
    //  shopId quan trọng nhất: dùng để chặn Shop Owner này xem
    //  sản phẩm của Shop Owner khác.
    // ============================================================

    /** Shop sở hữu sản phẩm. UNIQUE (id, shop_id) trong schema. */
    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "origin")
    private String origin;

    /**
     * Mã lô nhập kho. Schema có UNIQUE (shop_id, batch_code) và mỗi lô nhập
     * là một sản phẩm riêng, nên không cho sửa sau khi tạo.
     */
    @Column(name = "batch_code", nullable = false)
    private String batchCode;

    @Column(name = "received_date", nullable = false)
    private java.time.LocalDate receivedDate;

    @Column(name = "expiry_date", nullable = false)
    private java.time.LocalDate expiryDate;

    /** DRAFT / PENDING / APPROVED / REJECTED */
    @Column(name = "approval_status", nullable = false)
    private String approvalStatus = "DRAFT";

    @Column(name = "submitted_at")
    private java.time.LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private java.time.LocalDateTime updatedAt;

    // Getter và Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSellingStatus() {
        return sellingStatus;
    }

    public void setSellingStatus(String sellingStatus) {
        this.sellingStatus = sellingStatus;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public void setVariants(List<ProductVariant> variants) {
        this.variants = variants;
    }

    public List<Category> getCategories() { return categories; }

    public void setCategories(List<Category> categories) { this.categories = categories; }

    // Getter và Setter cho các trường bổ sung
    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public java.time.LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(java.time.LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public java.time.LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(java.time.LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public java.time.LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(java.time.LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public java.time.LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(java.time.LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}