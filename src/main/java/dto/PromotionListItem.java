package dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PromotionListItem {
    private Long id;
    private Long variantId;
    private String productName;
    private String variantName;
    private String sku;
    private BigDecimal originalPrice;
    private BigDecimal promotionalPrice;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Boolean enabled;
    public PromotionListItem(Long id, Long variantId, String productName, String variantName, String sku,
            BigDecimal originalPrice, BigDecimal promotionalPrice, LocalDateTime startsAt,
            LocalDateTime endsAt, Boolean enabled) {
        this.id=id; this.variantId=variantId; this.productName=productName; this.variantName=variantName;
        this.sku=sku; this.originalPrice=originalPrice; this.promotionalPrice=promotionalPrice;
        this.startsAt=startsAt; this.endsAt=endsAt; this.enabled=enabled;
    }
    public Long getId() { return id; }
    public Long getVariantId() { return variantId; }
    public String getProductName() { return productName; }
    public String getVariantName() { return variantName; }
    public String getSku() { return sku; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public BigDecimal getPromotionalPrice() { return promotionalPrice; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public Boolean getEnabled() { return enabled; }
}
