package service;

import entity.ProductVariant;
import org.springframework.stereotype.Service;
import repository.ProductVariantRepository;
import repository.PromotionalPriceRepository;
import repository.ActivePromotionPrice;
import java.util.Map;
import java.util.stream.Collectors;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class HomeService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ProductVariantRepository productVariantRepository;
    private final PromotionalPriceRepository promotionalPriceRepository;

    public HomeService(ProductVariantRepository productVariantRepository, PromotionalPriceRepository promotionalPriceRepository) {
        this.productVariantRepository = productVariantRepository;
        this.promotionalPriceRepository = promotionalPriceRepository;
    }

    public List<ProductVariant> getActiveProductsForHome() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        List<ProductVariant> variants = productVariantRepository.findByStatusAndProduct_SellingStatusAndProduct_ApprovalStatus("ACTIVE", "ACTIVE", "APPROVED");
        if (variants.isEmpty()) return variants;
        List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<Long, java.math.BigDecimal> activePrices = promotionalPriceRepository
                .findActivePricesByVariantIds(variantIds, now).stream()
                .collect(Collectors.toMap(ActivePromotionPrice::getVariantId,
                        ActivePromotionPrice::getPromotionalPrice));
        variants.forEach(variant -> variant.setPromotionalPrice(activePrices.get(variant.getId())));
        return variants;
    }
}
