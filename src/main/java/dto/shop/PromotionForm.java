package dto.shop;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PromotionForm {
    /** ID biến thể chủ shop chọn cho chương trình. */
    @NotNull(message = "Vui lòng chọn biến thể sản phẩm.")
    private Long variantId;

    /** Giá mới hiển thị cho khách; service còn kiểm tra thấp hơn giá bán hiện tại. */
    @NotNull(message = "Vui lòng nhập giá khuyến mãi.")
    @DecimalMin(value = "1", message = "Giá khuyến mãi phải lớn hơn 0.")
    @Digits(integer = 19, fraction = 0, message = "Giá khuyến mãi phải là số nguyên, tối đa 19 chữ số.")
    private BigDecimal promotionalPrice;

    /** Thời điểm bắt đầu áp giá khuyến mãi theo giờ Việt Nam. */
    @NotNull(message = "Vui lòng chọn thời điểm bắt đầu.")
    private LocalDateTime startsAt;

    /** Thời điểm kết thúc; service yêu cầu thời điểm này sau thời điểm bắt đầu. */
    @NotNull(message = "Vui lòng chọn thời điểm kết thúc.")
    private LocalDateTime endsAt;

    private Boolean enabled = true;

    public Long getVariantId() { return variantId; }
    public void setVariantId(Long variantId) { this.variantId = variantId; }
    public BigDecimal getPromotionalPrice() { return promotionalPrice; }
    public void setPromotionalPrice(BigDecimal promotionalPrice) { this.promotionalPrice = promotionalPrice; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
