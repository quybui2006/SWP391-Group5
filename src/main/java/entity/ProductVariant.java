package entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Transient
    private BigDecimal promotionalPrice;

    public BigDecimal getPromotionalPrice() { return promotionalPrice; }
    public void setPromotionalPrice(BigDecimal promotionalPrice) { this.promotionalPrice = promotionalPrice; }


    /** Trường tương thích dữ liệu cũ; sản phẩm mới không qua hàng đợi duyệt. */
    @Column(name = "approval_status", nullable = false)
    private String approvalStatus = "DRAFT";

    @Column(name = "submitted_at")
    private java.time.LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "operations_blocked", nullable = false)
    private boolean operationsBlocked;

    @Column(name = "operations_block_reason")
    private String operationsBlockReason;

    @Column(name = "operations_blocked_at")
    private LocalDateTime operationsBlockedAt;

    /** Trạng thái bán do shop quản lý; OP chặn bán bằng cờ riêng. */
    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @ManyToOne
    @JoinColumn(name = "base_unit_id", nullable = false)
    private Unit baseUnit;

    // ============================================================
    //  Các trường bổ sung cho màn hình Shop Owner (Sheet2 No.7, No.8)
    //  Bổ sung ngày 08/10/2026. Không sửa các trường phía trên.
    // ============================================================

    /** Shop sở hữu biến thể. Schema có UNIQUE (shop_id, sku). */
    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    /** Mã SKU. */
    @Column(name = "sku", nullable = false)
    private String sku;

    /** Quy cách, ví dụ "Khoảng 0.5kg, 5-6 quả". */
    @Column(name = "specification")
    private String specification;

    /** Số lượng tối thiểu phải mua. */
    @Column(name = "min_order_quantity", nullable = false)
    private Integer minOrderQuantity = 1;

    // Getter và Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public java.time.LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(java.time.LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public java.time.LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(java.time.LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public boolean isOperationsBlocked() { return operationsBlocked; }
    public void setOperationsBlocked(boolean operationsBlocked) { this.operationsBlocked = operationsBlocked; }
    public String getOperationsBlockReason() { return operationsBlockReason; }
    public void setOperationsBlockReason(String operationsBlockReason) { this.operationsBlockReason = operationsBlockReason; }
    public LocalDateTime getOperationsBlockedAt() { return operationsBlockedAt; }
    public void setOperationsBlockedAt(LocalDateTime operationsBlockedAt) { this.operationsBlockedAt = operationsBlockedAt; }
    public Unit getBaseUnit() { return baseUnit; }
    public void setBaseUnit(Unit baseUnit) { this.baseUnit = baseUnit; }

    // Getter và Setter cho các trường bổ sung
    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public Integer getMinOrderQuantity() { return minOrderQuantity; }
    public void setMinOrderQuantity(Integer minOrderQuantity) { this.minOrderQuantity = minOrderQuantity; }
}
