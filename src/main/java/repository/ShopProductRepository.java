package repository;

import entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn sản phẩm theo góc nhìn Shop Owner.
 *
 * Không dùng cho khách hàng - trang khách dùng ProductVariantRepository
 * (của Nguyễn Hồng Hà).
 */
@Repository
public interface ShopProductRepository extends JpaRepository<Product, Long> {

    /**
     * Tìm sản phẩm thuộc đúng shop của chủ sở hữu.
     * Đây là hàm quan trọng nhất: chặn Shop Owner này xem sản phẩm
     * của Shop Owner khác.
     */
    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    List<Product> findByShopId(Long shopId);

    boolean existsByShopIdAndBatchCode(Long shopId, String batchCode);
}
