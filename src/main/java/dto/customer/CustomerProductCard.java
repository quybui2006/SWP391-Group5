package dto.customer;

import entity.ProductVariant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Một sản phẩm phía khách; giá lấy từ biến thể có giá bán thấp nhất. */
public class CustomerProductCard {
    private final Long variantId;
    private final String name;
    private final String imageUrl;
    private final BigDecimal price;
    private final BigDecimal promotionalPrice;

    private CustomerProductCard(ProductVariant variant, String imageUrl) {
        this.variantId = variant.getId();
        this.name = variant.getProduct() == null ? variant.getName() : variant.getProduct().getName();
        this.imageUrl = imageUrl;
        this.price = variant.getPrice();
        this.promotionalPrice = variant.getPromotionalPrice();
    }

    public static List<CustomerProductCard> fromVariants(List<ProductVariant> variants) {
        Map<Long, ProductVariant> cheapestByProduct = new LinkedHashMap<>();
        Map<Long, String> imageByProduct = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            Long productId = variant.getProduct() == null ? -variant.getId() : variant.getProduct().getId();
            if (variant.getImageUrl() != null && !variant.getImageUrl().isBlank()) {
                imageByProduct.putIfAbsent(productId, variant.getImageUrl());
            }
            ProductVariant current = cheapestByProduct.get(productId);
            if (current == null || compareSalePrice(variant, current) < 0) {
                cheapestByProduct.put(productId, variant);
            }
        }

        List<CustomerProductCard> cards = new ArrayList<>();
        cheapestByProduct.forEach((productId, variant) ->
                cards.add(new CustomerProductCard(variant, imageByProduct.get(productId))));
        return cards;
    }

    private static int compareSalePrice(ProductVariant left, ProductVariant right) {
        BigDecimal leftPrice = salePrice(left);
        BigDecimal rightPrice = salePrice(right);
        if (leftPrice == null) return rightPrice == null ? 0 : 1;
        if (rightPrice == null) return -1;
        int priceOrder = leftPrice.compareTo(rightPrice);
        return priceOrder != 0 ? priceOrder : left.getId().compareTo(right.getId());
    }

    private static BigDecimal salePrice(ProductVariant variant) {
        return variant.getPromotionalPrice() == null ? variant.getPrice() : variant.getPromotionalPrice();
    }

    public Long getVariantId() { return variantId; }
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getPromotionalPrice() { return promotionalPrice; }
    public BigDecimal getSalePrice() { return promotionalPrice == null ? price : promotionalPrice; }
}
