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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ReviewService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public void ensureSampleReviewsIfEmpty(Long variantId) {
        long count = reviewRepository.countByVariantId(variantId);
        if (count > 0) {
            return;
        }

        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            return;
        }

        Long u1 = users.get(0).getId();
        Long u2 = users.size() > 1 ? users.get(1).getId() : u1;
        Long u3 = users.size() > 3 ? users.get(3).getId() : u1;

        Review r1 = new Review(variantId, u2, 5, "Trái cây rất tươi và giòn ngọt! Quả to đều, mọng nước và không bị dập hay xước. Shop đóng gói rất kỹ càng, giao hàng nhanh chóng. Sẽ ủng hộ shop dài lâu!");
        r1.setCreatedAt(LocalDateTime.now().minusHours(5));
        reviewRepository.save(r1);

        Review r2 = new Review(variantId, u1, 5, "Sản phẩm chất lượng cao, tươi ngon chuẩn như mô tả. Gia đình mình ăn ai cũng khen nức nở. 10/10 điểm cho sự chu đáo của shop.");
        r2.setCreatedAt(LocalDateTime.now().minusDays(1).minusHours(3));
        reviewRepository.save(r2);

        Review r3 = new Review(variantId, u3, 4, "Giao hàng đúng hẹn, hoa quả giữ được độ tươi mát. Hương vị thơm ngon tự nhiên. Lần sau có mã giảm giá sẽ mua thêm.");
        r3.setCreatedAt(LocalDateTime.now().minusDays(3).minusHours(2));
        reviewRepository.save(r3);
    }

    @Transactional
    public List<ReviewDto> getReviewsForVariant(Long variantId) {
        ensureSampleReviewsIfEmpty(variantId);

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
        ensureSampleReviewsIfEmpty(variantId);

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

    @Transactional
    public Review addReview(Long variantId, Long userId, Integer rating, String content) {
        if (rating == null || rating < 1) rating = 5;
        if (rating > 5) rating = 5;

        String safeContent = content != null ? content.trim() : "";
        if (safeContent.length() > 2000) {
            safeContent = safeContent.substring(0, 2000);
        }

        Review review = new Review(variantId, userId, rating, safeContent);
        review.setCreatedAt(LocalDateTime.now());
        return reviewRepository.save(review);
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
