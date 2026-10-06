package repository;

import entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    // Tìm các biến thể sản phẩm đang ACTIVE
    // và thuộc về Product cũng đang ACTIVE
    List<ProductVariant> findByStatusAndProduct_SellingStatus(
            String variantStatus,
            String productStatus
    );
}