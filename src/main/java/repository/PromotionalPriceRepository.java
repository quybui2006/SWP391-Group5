package repository;

import entity.PromotionalPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface PromotionalPriceRepository extends JpaRepository<PromotionalPrice, Long> {

    /** Giá khuyến mãi hợp lệ tại thời điểm được truyền theo múi giờ nghiệp vụ. */
    @Query(value = "SELECT pp.promotional_price FROM promotional_prices pp " +
            "WHERE pp.variant_id = :variantId AND pp.is_enabled = 1 " +
            "AND pp.starts_at <= :now AND pp.ends_at > :now " +
            "ORDER BY pp.starts_at DESC, pp.id DESC LIMIT 1", nativeQuery = true)
    Optional<BigDecimal> findActivePriceByVariantId(
            @Param("variantId") Long variantId, @Param("now") LocalDateTime now);

    @Query(value = "SELECT pp.variant_id AS variantId, pp.promotional_price AS promotionalPrice " +
            "FROM promotional_prices pp WHERE pp.variant_id IN (:variantIds) AND pp.is_enabled = 1 " +
            "AND pp.starts_at <= :now AND pp.ends_at > :now " +
            "AND NOT EXISTS (SELECT 1 FROM promotional_prices newer WHERE newer.variant_id = pp.variant_id " +
            "AND newer.is_enabled = 1 AND newer.starts_at <= :now AND newer.ends_at > :now " +
            "AND (newer.starts_at > pp.starts_at OR (newer.starts_at = pp.starts_at AND newer.id > pp.id)))", nativeQuery = true)
    List<ActivePromotionPrice> findActivePricesByVariantIds(
            @Param("variantIds") Collection<Long> variantIds, @Param("now") LocalDateTime now);
}
