package dto.shop;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public class InventoryBatchForm {
    @NotNull private Long inventoryId;
    @NotNull @Positive private Integer quantity;
    @NotNull private LocalDateTime receivedAt;
    private String note;

    public Long getInventoryId() { return inventoryId; }
    public void setInventoryId(Long inventoryId) { this.inventoryId = inventoryId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
