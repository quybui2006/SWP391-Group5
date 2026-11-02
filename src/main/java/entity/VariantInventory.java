package entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Bảng variant_inventory - tồn kho của từng biến thể.
 * Schema có UNIQUE (variant_id): mỗi biến thể đúng một dòng tồn kho.
 */
@Entity
@Table(name = "variant_inventory")
public class VariantInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false, unique = true)
    private Long variantId;

    @Column(name = "initial_quantity", nullable = false)
    private Integer initialQuantity;

    @Column(name = "quantity_on_hand", nullable = false)
    private Integer quantityOnHand;

    /** Số hàng khách đang giữ trong giỏ, chưa trừ khỏi tồn kho. */
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    /**
     * Cột GENERATED ALWAYS AS (quantity_on_hand - reserved_quantity) STORED.
     * MySQL tự tính, Hibernate không được ghi vào.
     */
    @Column(name = "available_quantity", insertable = false, updatable = false)
    private Integer availableQuantity;

    @Column(name = "low_stock_threshold_quantity", nullable = false)
    private Integer lowStockThresholdQuantity = 0;


    /** ACTIVE / BLOCKED / CLOSED */
    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVariantId() { return variantId; }
    public void setVariantId(Long variantId) { this.variantId = variantId; }
    public Integer getInitialQuantity() { return initialQuantity; }
    public void setInitialQuantity(Integer initialQuantity) { this.initialQuantity = initialQuantity; }
    public Integer getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(Integer quantityOnHand) { this.quantityOnHand = quantityOnHand; }
    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
    public Integer getLowStockThresholdQuantity() { return lowStockThresholdQuantity; }
    public void setLowStockThresholdQuantity(Integer lowStockThresholdQuantity) { this.lowStockThresholdQuantity = lowStockThresholdQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
