package service.shop;

import entity.ProductVariant;
import repository.ProductVariantRepository;
import repository.PromotionalPriceRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import repository.ActivePromotionPrice;

@Service
public class ProductService {

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private PromotionalPriceRepository promotionalPriceRepository;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public List searchAndFilterProducts(String keyword, List categoryIds, String priceRange) {
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;

        // Xử lý khoảng giá
        if ("under50".equals(priceRange)) {
            maxPrice = new BigDecimal("50000");
        } else if ("50to100".equals(priceRange)) {
            minPrice = new BigDecimal("50000");
            maxPrice = new BigDecimal("100000");
        } else if ("over100".equals(priceRange)) {
            minPrice = new BigDecimal("100000");
        }

        // Xử lý danh mục (Nếu user không tích vào ô nào -> không lọc theo danh mục)
        boolean filterByCategory = (categoryIds != null && !categoryIds.isEmpty());
        List safeCategoryIds = filterByCategory ? categoryIds : List.of(-1L);

        String safeKeyword = (keyword == null) ? "" : keyword.trim();

        List<ProductVariant> variants = productVariantRepository.searchAndFilter(
                safeKeyword, minPrice, maxPrice, filterByCategory, safeCategoryIds);
        applyActivePromotionPrices(variants);
        return variants;
    }

    private void applyActivePromotionPrices(List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) return;
        List<Long> ids = variants.stream().map(ProductVariant::getId).toList();
        Map<Long, BigDecimal> prices = promotionalPriceRepository
                .findActivePricesByVariantIds(ids, java.time.LocalDateTime.now(BUSINESS_ZONE)).stream()
                .collect(Collectors.toMap(ActivePromotionPrice::getVariantId,
                        ActivePromotionPrice::getPromotionalPrice));
        variants.forEach(variant -> variant.setPromotionalPrice(prices.get(variant.getId())));
    }

    public ProductVariant getProductVariantById(Long id) {
        ProductVariant variant = productVariantRepository.findById(id)
                // Hậu kiểm: khách hàng chỉ cần sản phẩm đang ACTIVE; approval_status không chặn catalog.
                .filter(v -> "ACTIVE".equals(v.getStatus())
                        && "ACTIVE".equals(v.getProduct().getSellingStatus()))
                .orElse(null);
        if (variant != null) {
            variant.setPromotionalPrice(promotionalPriceRepository
                    .findActivePriceByVariantId(id, LocalDateTime.now(BUSINESS_ZONE)).orElse(null));
        }
        return variant;
    }
}
