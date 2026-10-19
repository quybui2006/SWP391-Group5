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

    @Autowired
    private repository.VariantInventoryRepository variantInventoryRepository;

    public List<ProductVariant> searchAndFilterProducts(String keyword, List<Long> categoryIds, String priceRange) {
        return searchAndFilterProducts(keyword, categoryIds, priceRange, null, null, null, false, "popular");
    }

    public List<ProductVariant> searchAndFilterProducts(
            String keyword,
            List<Long> categoryIds,
            String priceRange,
            BigDecimal customMinPrice,
            BigDecimal customMaxPrice,
            List<String> origins,
            Boolean inStockOnly,
            String sortKey) {

        BigDecimal minPrice = customMinPrice;
        BigDecimal maxPrice = customMaxPrice;

        // Xử lý khoảng giá nếu không nhập khoảng tùy chỉnh
        if (minPrice == null && maxPrice == null && priceRange != null) {
            if ("under50".equals(priceRange)) {
                maxPrice = new BigDecimal("50000");
            } else if ("50to100".equals(priceRange)) {
                minPrice = new BigDecimal("50000");
                maxPrice = new BigDecimal("100000");
            } else if ("100to200".equals(priceRange)) {
                minPrice = new BigDecimal("100000");
                maxPrice = new BigDecimal("200000");
            } else if ("over200".equals(priceRange)) {
                minPrice = new BigDecimal("200000");
            } else if ("over100".equals(priceRange)) {
                minPrice = new BigDecimal("100000");
            }
        }

        // Xử lý danh mục
        boolean filterByCategory = (categoryIds != null && !categoryIds.isEmpty());
        List<Long> safeCategoryIds = filterByCategory ? categoryIds : List.of(-1L);

        // Xử lý xuất xứ / vùng miền
        boolean filterByOrigin = (origins != null && !origins.isEmpty());
        List<String> safeOrigins = filterByOrigin ? origins : List.of("__NONE__");

        // Xử lý sắp xếp
        org.springframework.data.domain.Sort sort;
        if ("priceAsc".equalsIgnoreCase(sortKey)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "price");
        } else if ("priceDesc".equalsIgnoreCase(sortKey)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "price");
        } else if ("nameAsc".equalsIgnoreCase(sortKey)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "name");
        } else if ("newest".equalsIgnoreCase(sortKey)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id");
        } else {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id");
        }

        String safeKeyword = (keyword == null) ? "" : keyword.trim();

        List<ProductVariant> variants = productVariantRepository.advancedSearchAndFilter(
                safeKeyword, minPrice, maxPrice, filterByCategory, safeCategoryIds, filterByOrigin, safeOrigins, sort);

        // Lọc tồn kho nếu yêu cầu inStockOnly
        if (Boolean.TRUE.equals(inStockOnly) && !variants.isEmpty()) {
            List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
            var inventoryList = variantInventoryRepository.findByVariantIdIn(variantIds);
            var invMap = inventoryList.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entity.VariantInventory::getVariantId,
                            java.util.function.Function.identity(),
                            (a, b) -> a));

            variants = variants.stream()
                    .filter(v -> {
                        var inv = invMap.get(v.getId());
                        return inv != null
                                && "ACTIVE".equals(inv.getStatus())
                                && inv.getAvailableQuantity() != null
                                && inv.getAvailableQuantity() > 0;
                    })
                    .collect(java.util.stream.Collectors.toList());
        }

        // Gán khuyến mãi hiện hành (nếu có)
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        for (ProductVariant variant : variants) {
            promotionalPriceRepository.findActivePriceByVariantId(variant.getId(), now)
                    .ifPresent(variant::setPromotionalPrice);
        }

        return variants;
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