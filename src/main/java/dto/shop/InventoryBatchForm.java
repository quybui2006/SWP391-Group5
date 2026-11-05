package dto.shop;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.LocalDate;

public class InventoryBatchForm {
    @NotNull private Long inventoryId;
    @NotNull @Positive private Integer quantity;
    @NotNull private LocalDateTime receivedAt;
    @NotNull private LocalDate expiresAt;
    @Size(max = 1000) private String note;

    public Long getInventoryId() { return inventoryId; }
    public void setInventoryId(Long inventoryId) { this.inventoryId = inventoryId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
