package dto.shop;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Form cập nhật tăng số lượng / điều chỉnh / báo hỏng tồn kho.
 * Số lượng cập nhật từ UI luôn là số nguyên dương; Service quyết định tăng hoặc giảm.
 */
public class InventoryActionRequest {

    @NotNull(message = "Vui lòng chọn biến thể sản phẩm.")
    private Long variantId;

    @NotNull(message = "Vui lòng chọn loại giao dịch.")
    private InventoryActionType actionType;

    @NotNull(message = "Vui lòng nhập số lượng.")
    @Positive(message = "Số lượng phải là số nguyên dương.")
    private Integer quantity;

    @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự.")
    private String reason;

    /** Chỉ áp dụng khi actionType = ADJUST; mặc định giảm để tránh cộng nhầm tồn. */
    private InventoryAdjustmentDirection adjustmentDirection = InventoryAdjustmentDirection.DECREASE;

    public Long getVariantId() { return variantId; }
    public void setVariantId(Long variantId) { this.variantId = variantId; }
    public InventoryActionType getActionType() { return actionType; }
    public void setActionType(InventoryActionType actionType) { this.actionType = actionType; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public InventoryAdjustmentDirection getAdjustmentDirection() { return adjustmentDirection; }
    public void setAdjustmentDirection(InventoryAdjustmentDirection adjustmentDirection) {
        this.adjustmentDirection = adjustmentDirection;
    }
}
