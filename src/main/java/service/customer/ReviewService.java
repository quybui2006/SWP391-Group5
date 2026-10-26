package service.customer;

import dto.customer.ReviewDto;
import dto.customer.ReviewStatsDto;
import entity.Review;
import entity.User;
import repository.ReviewRepository;
import repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ReviewService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Optional<Long> findPurchasableOrderItem(Long variantId, Long userId) {
        List<Long> ids = jdbc.query("""
                SELECT oi.id FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE oi.variant_id = ? AND o.checkout_id IN (SELECT id FROM checkouts WHERE user_id = ?)
                  AND o.status = 'COMPLETED'
                  AND oi.reviewed_at IS NULL AND NOT EXISTS (SELECT 1 FROM reviews r WHERE r.order_item_id = oi.id)
                ORDER BY o.completed_at DESC, oi.id DESC LIMIT 1
                """, (rs, row) -> rs.getLong(1), variantId, userId);
        return ids.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public boolean canReview(Long variantId, Long userId) {
        return findPurchasableOrderItem(variantId, userId).isPresent();
    }

    @Transactional
    public Review addReview(Long variantId, Long userId, Long orderItemId, Integer rating, String content) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Số sao phải từ 1 đến 5.");
        }
        if (orderItemId == null || userId == null || variantId == null) {
            throw new IllegalArgumentException("Bạn chỉ có thể đánh giá sản phẩm trong đơn hàng đã hoàn tất.");
        }

        String safeContent = content == null ? "" : content.trim();
        if (safeContent.isEmpty()) throw new IllegalArgumentException("Nội dung đánh giá không được để trống.");
        if (safeContent.length() > 2000) throw new IllegalArgumentException("Nội dung đánh giá tối đa 2000 ký tự.");

        Long eligibleCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                JOIN checkouts c ON c.id = o.checkout_id
                WHERE oi.id = ?
                  AND oi.variant_id = ?
                  AND c.user_id = ?
                  AND o.status = 'COMPLETED'
                  AND oi.reviewed_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM reviews r WHERE r.order_item_id = oi.id)
                """, Long.class, orderItemId, variantId, userId);

        if (eligibleCount == null || eligibleCount == 0) {
            throw new IllegalArgumentException("Bạn chỉ có thể đánh giá sản phẩm trong đơn hàng đã hoàn tất.");
        }

        Review review = new Review(variantId, userId, rating, safeContent);
        review.setOrderItemId(orderItemId);
        Review saved = reviewRepository.save(review);
        int updated = jdbc.update("UPDATE order_items SET reviewed_at = CURRENT_TIMESTAMP(3) WHERE id=? AND variant_id=? AND reviewed_at IS NULL", orderItemId, variantId);
        if (updated != 1) {
            throw new IllegalArgumentException("Sản phẩm trong đơn này đã được đánh giá.");
        }
        return saved;
    }

    @Transactional
    public Review updateReview(Long reviewId, Long variantId, Long userId, Integer rating, String content) {
        Review review = reviewRepository.findByIdAndVariantIdAndUserId(reviewId, variantId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn không có quyền sửa đánh giá này."));
        if (rating == null || rating < 1 || rating > 5) throw new IllegalArgumentException("Số sao phải từ 1 đến 5.");
        String safeContent = content == null ? "" : content.trim();
        if (safeContent.isEmpty() || safeContent.length() > 2000) throw new IllegalArgumentException("Nội dung đánh giá phải từ 1 đến 2000 ký tự.");
        review.setRating(rating); review.setContent(safeContent);
        return reviewRepository.save(review);
    }

    @Transactional
    public void deleteReview(Long reviewId, Long variantId, Long userId) {
        Review review = reviewRepository.findByIdAndVariantIdAndUserId(reviewId, variantId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bạn không có quyền xóa đánh giá này."));
        reviewRepository.delete(review);
        if (review.getOrderItemId() != null) jdbc.update("UPDATE order_items SET reviewed_at = NULL WHERE id=?", review.getOrderItemId());
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getReviewsForVariant(Long variantId) {
        List<Review> reviews = reviewRepository.findByVariantIdOrderByCreatedAtDesc(variantId);
        List<ReviewDto> dtos = new ArrayList<>();

        for (Review r : reviews) {
            ReviewDto dto = new ReviewDto();
            dto.setId(r.getId());
            dto.setVariantId(r.getVariantId());
            dto.setUserId(r.getUserId());
            dto.setRating(r.getRating());
            dto.setContent(r.getContent());
            dto.setCreatedAt(r.getCreatedAt());

            if (r.getCreatedAt() != null) {
                dto.setFormattedDate(r.getCreatedAt().format(DATE_FORMATTER));
                dto.setTimeAgo(formatTimeAgo(r.getCreatedAt()));
            } else {
                dto.setFormattedDate("");
                dto.setTimeAgo("Vừa xong");
            }

            dto.setStars("★".repeat(Math.max(1, Math.min(5, r.getRating()))));

            String name = "Khách hàng FreshFruit";
            if (r.getUser() != null && r.getUser().getFullName() != null && !r.getUser().getFullName().isBlank()) {
                name = r.getUser().getFullName();
            } else if (r.getUserId() != null) {
                User u = userRepository.findById(r.getUserId()).orElse(null);
                if (u != null && u.getFullName() != null && !u.getFullName().isBlank()) {
                    name = u.getFullName();
                }
            }
            dto.setUserName(name);

            String initial = "K";
            if (!name.isBlank()) {
                initial = name.substring(0, 1).toUpperCase(Locale.ROOT);
            }
            dto.setUserAvatarInitial(initial);

            dtos.add(dto);
        }

        return dtos;
    }

    public ReviewStatsDto getReviewStats(Long variantId) {
        ReviewStatsDto stats = new ReviewStatsDto();
        long total = reviewRepository.countByVariantId(variantId);
        stats.setTotalReviews(total);

        if (total > 0) {
            Double avg = reviewRepository.getAverageRatingByVariantId(variantId);
            double avgScore = avg != null ? Math.round(avg * 10.0) / 10.0 : 5.0;
            stats.setAverageRating(avgScore);
            stats.setAverageRatingFormatted(String.format(Locale.US, "%.1f", avgScore));

            stats.setCount5Star(reviewRepository.countByVariantIdAndRating(variantId, 5));
            stats.setCount4Star(reviewRepository.countByVariantIdAndRating(variantId, 4));
            stats.setCount3Star(reviewRepository.countByVariantIdAndRating(variantId, 3));
            stats.setCount2Star(reviewRepository.countByVariantIdAndRating(variantId, 2));
            stats.setCount1Star(reviewRepository.countByVariantIdAndRating(variantId, 1));
        } else {
            stats.setAverageRating(5.0);
            stats.setAverageRatingFormatted("5.0");
        }

        return stats;
    }

    private String formatTimeAgo(LocalDateTime time) {
        if (time == null) return "Vừa xong";
        Duration d = Duration.between(time, LocalDateTime.now());
        long seconds = d.getSeconds();
        if (seconds < 60) return "Vừa xong";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " phút trước";
        long hours = minutes / 60;
        if (hours < 24) return hours + " giờ trước";
        long days = hours / 24;
        if (days < 30) return days + " ngày trước";
        long months = days / 30;
        if (months < 12) return months + " tháng trước";
        return (months / 12) + " năm trước";
    }
}
