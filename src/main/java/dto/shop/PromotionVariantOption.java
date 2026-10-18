package dto.shop;

import java.math.BigDecimal;

public class PromotionVariantOption {
    private final Long id;
    private final String label;
    private final BigDecimal price;
    public PromotionVariantOption(Long id, String label, BigDecimal price) {
        this.id = id; this.label = label; this.price = price;
    }
    public Long getId() { return id; }
    public String getLabel() { return label; }
    public BigDecimal getPrice() { return price; }
}
