package service;

import entity.ProductVariant;
import repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductVariantRepository productVariantRepository;

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

        return productVariantRepository.searchAndFilter(safeKeyword, minPrice, maxPrice, filterByCategory, safeCategoryIds);
    }
}