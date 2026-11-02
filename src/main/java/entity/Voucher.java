package entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers", uniqueConstraints = @UniqueConstraint(name = "uq_vouchers_shop_code", columnNames = {"shop_id", "code"}))
public class Voucher {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="shop_id", nullable=false) private Long shopId;
    @Column(nullable=false, length=50) private String code;
    @Column(nullable=false, length=150) private String name;
    @Column(name="discount_type", nullable=false, length=20) private String discountType;
    @Column(name="discount_value", nullable=false, precision=19, scale=0) private BigDecimal discountValue;
    @Column(name="max_discount_amount", precision=19, scale=0) private BigDecimal maxDiscountAmount;
    @Column(name="min_order_value", nullable=false, precision=19, scale=0) private BigDecimal minOrderValue = BigDecimal.ZERO;
    @Column(name="max_usages", nullable=false) private Integer maxUsages;
    @Column(name="used_count", nullable=false) private Integer usedCount = 0;
    @Column(name="is_public", nullable=false) private Boolean isPublic = true;
    @Column(name="voucher_type", nullable=false, length=20) private String voucherType = "DISCOUNT";
    @Column(nullable=false, length=20) private String status = "ACTIVE";
    @Column(name="starts_at", nullable=false) private LocalDateTime startsAt;
    @Column(name="ends_at", nullable=false) private LocalDateTime endsAt;
    @Column(name="created_at", nullable=false, insertable=false, updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false, insertable=false, updatable=false) private LocalDateTime updatedAt;

    @PrePersist @PreUpdate private void normalize() {
        if (code != null) code = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (name != null) name = name.trim();
        if (usedCount == null) usedCount = 0;
        if (minOrderValue == null) minOrderValue = BigDecimal.ZERO;
        if (isPublic == null) isPublic = true;
        if (status == null) status = "ACTIVE";
    }
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getShopId(){return shopId;} public void setShopId(Long v){shopId=v;}
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDiscountType(){return discountType;} public void setDiscountType(String v){discountType=v;}
    public BigDecimal getDiscountValue(){return discountValue;} public void setDiscountValue(BigDecimal v){discountValue=v;}
    public BigDecimal getMaxDiscountAmount(){return maxDiscountAmount;} public void setMaxDiscountAmount(BigDecimal v){maxDiscountAmount=v;}
    public BigDecimal getMinOrderValue(){return minOrderValue;} public void setMinOrderValue(BigDecimal v){minOrderValue=v;}
    public Integer getMaxUsages(){return maxUsages;} public void setMaxUsages(Integer v){maxUsages=v;}
    public Integer getUsedCount(){return usedCount;} public void setUsedCount(Integer v){usedCount=v;}
    public Boolean getIsPublic(){return isPublic;} public void setIsPublic(Boolean v){isPublic=v;}
    public String getVoucherType(){return voucherType;} public void setVoucherType(String v){voucherType=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDateTime getStartsAt(){return startsAt;} public void setStartsAt(LocalDateTime v){startsAt=v;}
    public LocalDateTime getEndsAt(){return endsAt;} public void setEndsAt(LocalDateTime v){endsAt=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
