package service;

import entity.ProductVariant;
import repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductVariantRepository productVariantRepository;

    public List searchActiveProducts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            // Nếu không nhập gì, trả về tất cả sản phẩm đang bán
            return productVariantRepository.findByStatusAndProduct_SellingStatus("ACTIVE", "ACTIVE");
        }
        // Trả về danh sách lọc theo từ khóa
        return productVariantRepository.findByNameContainingIgnoreCaseAndStatusAndProduct_SellingStatus(
                keyword.trim(), "ACTIVE", "ACTIVE");
    }
}
