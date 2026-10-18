package repository;

import dto.shop.InventoryTransactionRowDto;
import entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @Query("""
            SELECT new dto.shop.InventoryTransactionRowDto(
                t.id, t.createdAt, t.type, t.onHandDelta, t.onHandAfter, t.reason,
                p.name, v.name, v.sku, p.batchCode
            )
            FROM InventoryTransaction t
            JOIN VariantInventory i ON i.id = t.inventoryId
            JOIN ProductVariant v ON v.id = i.variantId
            JOIN v.product p
            WHERE v.shopId = :shopId
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<InventoryTransactionRowDto> findHistoryForShop(@Param("shopId") Long shopId);
}
