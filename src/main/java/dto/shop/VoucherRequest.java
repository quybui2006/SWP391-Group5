package dto.shop;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;

public class VoucherRequest {
    @NotBlank(message="Vui lòng nhập mã voucher.")
    @Size(max=50, message="Mã voucher tối đa 50 ký tự.")
    @Pattern(regexp="^[A-Z0-9]+(?:[A-Z0-9_-]*[A-Z0-9])?$", message="Mã voucher chỉ gồm chữ in hoa không dấu, số, dấu gạch ngang hoặc gạch dưới.")
    private String code;
    @NotBlank(message="Vui lòng nhập tên voucher.") @Size(max=150,message="Tên voucher tối đa 150 ký tự.") private String name;
    @NotBlank(message="Vui lòng chọn loại giảm giá.") @Pattern(regexp="PERCENTAGE|FIXED_AMOUNT",message="Loại giảm giá không hợp lệ.") private String discountType="PERCENTAGE";
    @NotNull(message="Vui lòng nhập giá trị giảm.") @DecimalMin(value="1",message="Giá trị giảm phải lớn hơn 0.") @Digits(integer=19, fraction=19, message="Giá trị giảm vượt quá giới hạn lưu trữ.") private BigDecimal discountValue;
    @DecimalMin(value="1",message="Mức giảm tối đa phải lớn hơn 0.") @Digits(integer=19, fraction=19, message="Mức giảm tối đa vượt quá giới hạn lưu trữ.") private BigDecimal maxDiscountAmount;
    @NotNull(message="Vui lòng nhập giá trị đơn hàng tối thiểu.") @DecimalMin(value="0",message="Đơn tối thiểu không được âm.") @Digits(integer=19, fraction=19, message="Giá trị đơn tối thiểu vượt quá giới hạn lưu trữ.") private BigDecimal minOrderValue=BigDecimal.ZERO;
    @NotNull(message="Vui lòng nhập số lượt sử dụng tối đa.") @Min(value=1,message="Số lượt sử dụng tối đa phải ít nhất là 1.") private Integer maxUsages;
    private Boolean isPublic=true;
    @NotNull(message="Vui lòng chọn thời gian bắt đầu.") private LocalDateTime startsAt;
    @NotNull(message="Vui lòng chọn thời gian kết thúc.") private LocalDateTime endsAt;
    private String status="ACTIVE";
    @AssertTrue(message="Ngày kết thúc phải sau ngày bắt đầu.")
    public boolean isDateRangeValid() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }

    @AssertTrue(message="Ngày bắt đầu không được trước ngày hôm nay theo giờ Việt Nam.")
    public boolean isStartDateNotBeforeToday() {
        return startsAt == null || !startsAt.toLocalDate().isBefore(
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @AssertTrue(message="Giảm theo phần trăm phải từ 1 đến 100.")
    public boolean isPercentageValid() {
        // @DecimalMin handles values below 1; this cross-field rule handles only the percentage ceiling.
        return discountType == null || !"PERCENTAGE".equals(discountType) || discountValue == null
                || discountValue.compareTo(new BigDecimal("100")) <= 0;
    }

    @AssertTrue(message="Chỉ áp dụng giảm tối đa cho voucher giảm theo phần trăm.")
    public boolean isMaxDiscountValid() {
        return !"FIXED_AMOUNT".equals(discountType) || maxDiscountAmount == null;
    }

    @AssertTrue(message="Các giá trị tiền phải là số nguyên VNĐ (không có phần lẻ).")
    public boolean isVndAmountsWholeNumbers() {
        return isWholeVnd(discountValue) && isWholeVnd(maxDiscountAmount) && isWholeVnd(minOrderValue);
    }

    private boolean isWholeVnd(BigDecimal value) {
        if (value == null) return true;
        BigDecimal normalized = value.stripTrailingZeros();
        int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
        return normalized.scale() <= 0 && integerDigits <= 19;
    }

    public String getCode(){return code;} public void setCode(String v){code=v==null?null:v.trim().toUpperCase(java.util.Locale.ROOT);}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDiscountType(){return discountType;} public void setDiscountType(String v){discountType=v;}
    public BigDecimal getDiscountValue(){return discountValue;} public void setDiscountValue(BigDecimal v){discountValue=v;}
    public BigDecimal getMaxDiscountAmount(){return maxDiscountAmount;} public void setMaxDiscountAmount(BigDecimal v){maxDiscountAmount=v;}
    public BigDecimal getMinOrderValue(){return minOrderValue;} public void setMinOrderValue(BigDecimal v){minOrderValue=v;}
    public Integer getMaxUsages(){return maxUsages;} public void setMaxUsages(Integer v){maxUsages=v;}
    public Boolean getIsPublic(){return isPublic;} public void setIsPublic(Boolean v){isPublic=v;}
    public LocalDateTime getStartsAt(){return startsAt;} public void setStartsAt(LocalDateTime v){startsAt=v;}
    public LocalDateTime getEndsAt(){return endsAt;} public void setEndsAt(LocalDateTime v){endsAt=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
}
