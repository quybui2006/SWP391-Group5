package repository;

import entity.VariantInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariantInventoryRepository extends JpaRepository<VariantInventory, Long> {

    Optional<VariantInventory> findByVariantId(Long variantId);

    /** Lấy tồn kho nhiều biến thể trong 1 lần truy vấn, tránh N+1. */
    List<VariantInventory> findByVariantIdIn(List<Long> variantIds);
}
