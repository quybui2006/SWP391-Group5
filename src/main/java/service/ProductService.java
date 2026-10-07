package service;

import dto.ProductCreateRequest;
import dto.ProductResponseDTO;
import entity.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Các nghiệp vụ dùng chung cho catalog khách hàng và shop owner.
 */
public interface ProductService {

    Page<ProductResponseDTO> getProductList(Long shopId, String keyword, Pageable pageable);

    ProductResponseDTO addProduct(Long shopId, Long userId, ProductCreateRequest request);

    List<ProductVariant> searchAndFilterProducts(String keyword, List<Long> categoryIds, String priceRange);

    ProductVariant getProductVariantById(Long id);
}
