package repository;

import entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** Thứ tự ảnh trong bảng product_images có UNIQUE (product_id, sort_order). */
    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);
}
