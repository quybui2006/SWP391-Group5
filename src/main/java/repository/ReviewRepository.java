package repository;

import entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query(value = "SELECT r.* FROM reviews r " +
            "LEFT JOIN users u ON u.id = r.user_id " +
            "WHERE r.variant_id = :variantId " +
            "AND COALESCE(r.moderation_status, 'APPROVED') NOT IN ('REJECTED', 'REMOVED') " +
            "ORDER BY r.created_at DESC", nativeQuery = true)
    List<Review> findByVariantIdOrderByCreatedAtDesc(@Param("variantId") Long variantId);

    @Query(value = "SELECT COUNT(*) FROM reviews r WHERE r.variant_id = :variantId " +
            "AND COALESCE(r.moderation_status, 'APPROVED') NOT IN ('REJECTED', 'REMOVED')", nativeQuery = true)
    long countByVariantId(@Param("variantId") Long variantId);

    @Query(value = "SELECT COUNT(*) FROM reviews r WHERE r.variant_id = :variantId " +
            "AND r.rating = :rating " +
            "AND COALESCE(r.moderation_status, 'APPROVED') NOT IN ('REJECTED', 'REMOVED')", nativeQuery = true)
    long countByVariantIdAndRating(@Param("variantId") Long variantId, @Param("rating") Integer rating);

    java.util.Optional<Review> findByIdAndVariantIdAndUserId(Long id, Long variantId, Long userId);

    boolean existsByOrderItemId(Long orderItemId);

    @Query(value = "SELECT AVG(r.rating) FROM reviews r WHERE r.variant_id = :variantId " +
            "AND COALESCE(r.moderation_status, 'APPROVED') NOT IN ('REJECTED', 'REMOVED')", nativeQuery = true)
    Double getAverageRatingByVariantId(@Param("variantId") Long variantId);
}
