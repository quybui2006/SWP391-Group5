package entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Ledger bất biến của tồn kho. Mỗi thay đổi tồn kho phải có một bản ghi giao dịch.
 * Không ánh xạ/update cột available_quantity tại đây.
 */
@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "on_hand_delta", nullable = false)
    private Integer onHandDelta;

    @Column(name = "reserved_delta", nullable = false)
    private Integer reservedDelta;

    @Column(name = "on_hand_after", nullable = false)
    private Integer onHandAfter;

    @Column(name = "reserved_after", nullable = false)
    private Integer reservedAfter;

    @Column(name = "event_key", nullable = false, unique = true, length = 150)
    private String eventKey;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInventoryId() { return inventoryId; }
    public void setInventoryId(Long inventoryId) { this.inventoryId = inventoryId; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getOnHandDelta() { return onHandDelta; }
    public void setOnHandDelta(Integer onHandDelta) { this.onHandDelta = onHandDelta; }
    public Integer getReservedDelta() { return reservedDelta; }
    public void setReservedDelta(Integer reservedDelta) { this.reservedDelta = reservedDelta; }
    public Integer getOnHandAfter() { return onHandAfter; }
    public void setOnHandAfter(Integer onHandAfter) { this.onHandAfter = onHandAfter; }
    public Integer getReservedAfter() { return reservedAfter; }
    public void setReservedAfter(Integer reservedAfter) { this.reservedAfter = reservedAfter; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
