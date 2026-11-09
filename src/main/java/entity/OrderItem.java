package entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "variant_name", nullable = false, length = 150)
    private String variantName;

    @Column(name = "sku", nullable = false, length = 80)
    private String sku;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "base_unit_name", nullable = false, length = 50)
    private String baseUnitName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "sale_unit_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal saleUnitPrice = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 0)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "allocation_status", nullable = false, length = 20)
    private String allocationStatus = "CONSUMED";

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public OrderItem() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public Long getVariantId() { return variantId; }
    public void setVariantId(Long variantId) { this.variantId = variantId; }

    public Long getInventoryId() { return inventoryId; }
    public void setInventoryId(Long inventoryId) { this.inventoryId = inventoryId; }

    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getBaseUnitName() { return baseUnitName; }
    public void setBaseUnitName(String baseUnitName) { this.baseUnitName = baseUnitName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getSaleUnitPrice() { return saleUnitPrice; }
    public void setSaleUnitPrice(BigDecimal saleUnitPrice) { this.saleUnitPrice = saleUnitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public String getAllocationStatus() { return allocationStatus; }
    public void setAllocationStatus(String allocationStatus) { this.allocationStatus = allocationStatus; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
