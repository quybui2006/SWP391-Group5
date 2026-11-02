package repository;

import entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.user WHERE r.variantId = :variantId ORDER BY r.createdAt DESC")
    List<Review> findByVariantIdOrderByCreatedAtDesc(@Param("variantId") Long variantId);

    long countByVariantId(Long variantId);

    long countByVariantIdAndRating(Long variantId, Integer rating);

    java.util.Optional<Review> findByIdAndVariantIdAndUserId(Long id, Long variantId, Long userId);

    boolean existsByOrderItemId(Long orderItemId);

    @Query("SELECT AVG(CAST(r.rating as double)) FROM Review r WHERE r.variantId = :variantId")
    Double getAverageRatingByVariantId(@Param("variantId") Long variantId);
}
