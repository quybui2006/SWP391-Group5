package repository;

import dto.shop.InventoryRowDto;
import entity.VariantInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariantInventoryRepository extends JpaRepository<VariantInventory, Long> {

    Optional<VariantInventory> findByVariantId(Long variantId);

    /** Lấy tồn kho nhiều biến thể trong 1 lần truy vấn, tránh N+1. */
    List<VariantInventory> findByVariantIdIn(List<Long> variantIds);

    /** Khóa dòng tồn kho trong suốt transaction để tránh cập nhật đồng thời gây sai lệch. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM VariantInventory i WHERE i.variantId = :variantId")
    Optional<VariantInventory> findLockedByVariantId(@Param("variantId") Long variantId);

    @Query("""
            SELECT new dto.shop.InventoryRowDto(
                i.id, v.id, p.name, v.name, v.sku, p.batchCode,
                i.quantityOnHand,
                (i.quantityOnHand - i.reservedQuantity)
            )
            FROM VariantInventory i
            JOIN ProductVariant v ON v.id = i.variantId
            JOIN v.product p
            WHERE v.shopId = :shopId
            ORDER BY p.name ASC, v.name ASC
            """)
    List<InventoryRowDto> findInventoryRowsForShop(@Param("shopId") Long shopId);
}
