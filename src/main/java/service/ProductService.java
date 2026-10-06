package service;

import dto.ProductCreateRequest;
import dto.ProductResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    Page<ProductResponseDTO> getProductList(
            Long shopId,
            String keyword,
            Pageable pageable
    );

    ProductResponseDTO addProduct(
            Long shopId,
            Long userId,
            ProductCreateRequest request
    );
}