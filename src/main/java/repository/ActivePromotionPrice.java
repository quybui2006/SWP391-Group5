package repository;

import java.math.BigDecimal;

/** Projection dùng để lấy giá khuyến mãi cho nhiều biến thể trong một truy vấn. */
public interface ActivePromotionPrice {
    Long getVariantId();
    BigDecimal getPromotionalPrice();
}
