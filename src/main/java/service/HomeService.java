package service;

import entity.ProductVariant;
import repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HomeService {

    @Autowired
    private ProductVariantRepository productVariantRepository;

    public List<ProductVariant> getActiveProductsForHome() {
        // Truyền trạng thái 'ACTIVE' để chỉ lấy hàng đang bán
        return productVariantRepository.findByStatusAndProduct_SellingStatus("ACTIVE", "ACTIVE");
    }
}
