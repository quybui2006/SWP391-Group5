package controller.customer;

import dto.customer.ReviewDto;
import dto.customer.ReviewStatsDto;
import entity.ProductVariant;
import entity.Province;
import entity.Review;
import entity.Shop;
import repository.ProductVariantRepository;
import repository.ProvinceRepository;
import repository.ShopRepository;
import service.shop.ProductService;
import service.customer.ReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Locale;
import entity.VariantInventory;
import repository.ProductImageRepository;
import repository.VariantInventoryRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
public class CustomerProductDetailController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private VariantInventoryRepository variantInventoryRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /** Hiển thị sản phẩm, giá khuyến mãi đang chạy và các voucher giảm tiền công khai của shop. */
    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable("id") Long id,
                                HttpSession session,
                                Model model) {
        ProductVariant variant = productService.getProductVariantById(id);

        // Nếu không tìm thấy sản phẩm hoặc sản phẩm đã bị ẩn -> Quay về trang chủ
        if (variant == null) {
            return "redirect:/";
        }

        // 1. Thông tin Cửa hàng (Shop)
        Shop shop = null;
        String shopProvinceName = "";
        Long shopId = variant.getShopId();
        if (shopId == null && variant.getProduct() != null) {
            shopId = variant.getProduct().getShopId();
        }

        if (shopId != null) {
            shop = shopRepository.findById(shopId).orElse(null);
            if (shop != null && shop.getProvinceCode() != null) {
                shopProvinceName = provinceRepository.findById(shop.getProvinceCode())
                        .map(Province::getName)
                        .orElse(shop.getProvinceCode());
            }
        }

        // 2. Thông tin đánh giá sản phẩm (Reviews)
        List<ReviewDto> reviews = reviewService.getReviewsForVariant(id);
        ReviewStatsDto reviewStats = reviewService.getReviewStats(id);

        // 3. Trạng thái người dùng (Guest vs Customer)
        boolean isCustomer = false;
        Long currentUserId = null;
        String currentFullName = "";

        if (session != null) {
            Object role = session.getAttribute("role");
            Object uid = session.getAttribute("userId");
            if ("CUSTOMER".equals(role) && uid instanceof Number num) {
                isCustomer = true;
                currentUserId = num.longValue();
                Object nameObj = session.getAttribute("fullName");
                currentFullName = nameObj != null ? nameObj.toString() : "";
            }
        }

        // Đẩy dữ liệu sang giao diện
        model.addAttribute("variant", variant);
        model.addAttribute("shop", shop);
        model.addAttribute("shopProvinceName", shopProvinceName);
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewStats", reviewStats);
        model.addAttribute("isCustomer", isCustomer);
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("canReview", isCustomer && reviewService.canReview(id, currentUserId));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        model.addAttribute("shopVouchers", jdbc.queryForList(
                "SELECT id,code,name,discount_type,discount_value,max_discount_amount,min_order_value " +
                        "FROM vouchers WHERE shop_id=? AND voucher_type='DISCOUNT' AND is_public=1 AND status='ACTIVE' " +
                        "AND starts_at<=? AND ends_at>? AND used_count<max_usages ORDER BY starts_at,id",
                shopId, now, now));
        model.addAttribute("shopCashbackVouchers", jdbc.queryForList(
                "SELECT id,code,name,discount_type,discount_value,max_discount_amount,min_order_value " +
                        "FROM vouchers WHERE shop_id=? AND voucher_type='CASHBACK' AND is_public=1 AND status='ACTIVE' " +
                        "AND starts_at<=? AND ends_at>? AND used_count<max_usages ORDER BY starts_at,id",
                shopId, now, now));

        Set<Long> savedVoucherIds = new HashSet<>();
        if (isCustomer && shopId != null) {
            savedVoucherIds.addAll(jdbc.queryForList(
                    "SELECT v.id FROM customer_saved_vouchers sv " +
                            "JOIN vouchers v ON v.id=sv.voucher_id " +
                            "WHERE sv.user_id=? AND v.shop_id=?",
                    Long.class, currentUserId, shopId));
        }
        model.addAttribute("savedVoucherIds", savedVoucherIds);

        // 3a. Tồn kho thực tế (Stock)
        VariantInventory inventory = variantInventoryRepository.findByVariantId(id).orElse(null);
        int stockQuantity = 0;
        if (inventory != null) {
            int onHand = inventory.getQuantityOnHand() != null ? inventory.getQuantityOnHand() : 0;
            int reserved = inventory.getReservedQuantity() != null ? inventory.getReservedQuantity() : 0;
            stockQuantity = Math.max(0, onHand - reserved);
        }
        model.addAttribute("stockQuantity", stockQuantity);

        // 3b. Số lượng đã bán thực tế (Sold count)
        Long soldCount = 0L;
        try {
            soldCount = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi " +
                    "JOIN orders o ON oi.order_id = o.id " +
                    "WHERE oi.variant_id = ? AND o.status != 'CANCELLED'",
                    Long.class, id);
            if (soldCount == null) soldCount = 0L;
        } catch (Exception ignored) {}
        String soldCountFormatted = (soldCount >= 1000)
                ? String.format(Locale.US, "%.1fk", soldCount / 1000.0)
                : String.valueOf(soldCount);
        model.addAttribute("soldCount", soldCount);
        model.addAttribute("soldCountFormatted", soldCountFormatted);

        // 3c. Lượt yêu thích (Favorites)
        Long favoriteCount = 0L;
        try {
            favoriteCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM customer_favorites WHERE variant_id = ?",
                    Long.class, id);
            if (favoriteCount == null) favoriteCount = 0L;
        } catch (Exception ignored) {}
        boolean isFavorite = false;
        if (isCustomer && currentUserId != null) {
            try {
                Integer favMatch = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM customer_favorites WHERE user_id = ? AND variant_id = ?",
                        Integer.class, currentUserId, id);
                isFavorite = (favMatch != null && favMatch > 0);
            } catch (Exception ignored) {}
        }
        model.addAttribute("favoriteCount", favoriteCount);
        model.addAttribute("isFavorite", isFavorite);

        // 3d. Bộ sưu tập hình ảnh sản phẩm (Gallery Images)
        List<String> galleryImages = new ArrayList<>();
        if (variant.getImageUrl() != null && !variant.getImageUrl().isBlank()) {
            galleryImages.add(variant.getImageUrl());
        }
        if (variant.getProduct() != null && variant.getProduct().getId() != null) {
            List<entity.ProductImage> pImages = productImageRepository.findByProductIdOrderBySortOrderAsc(variant.getProduct().getId());
            for (entity.ProductImage pi : pImages) {
                if (pi.getImageUrl() != null && !pi.getImageUrl().isBlank() && !galleryImages.contains(pi.getImageUrl())) {
                    galleryImages.add(pi.getImageUrl());
                }
            }
        }
        model.addAttribute("galleryImages", galleryImages);

        // 3e. Danh mục sản phẩm (Categories for specs & breadcrumb)
        List<String> catNames = new ArrayList<>();
        if (variant.getProduct() != null && variant.getProduct().getId() != null) {
            try {
                catNames = jdbc.queryForList(
                        "SELECT c.name FROM categories c " +
                        "JOIN product_categories pc ON pc.category_id = c.id " +
                        "WHERE pc.product_id = ? ORDER BY c.id",
                        String.class, variant.getProduct().getId());
            } catch (Exception ignored) {}
        }
        String categoryNames = !catNames.isEmpty() ? String.join(", ", catNames) : "Trái cây tươi";
        model.addAttribute("categoryNames", categoryNames);

        // 3f. Thông số gian hàng cập nhật thực tế (Shop Metrics)
        Integer shopTotalReviews = 0;
        String shopTotalReviewsFormatted = "0";
        String shopAverageRatingFormatted = "5.0";
        Integer shopTotalProducts = 0;
        String shopJoinTime = "Mới tham gia";
        String shopResponseRate = "100%";
        String shopResponseTime = "trong vài phút";

        if (shopId != null) {
            try {
                Integer revCount = jdbc.queryForObject(
                        "SELECT COUNT(r.id) FROM reviews r " +
                        "JOIN product_variants pv ON pv.id = r.variant_id " +
                        "WHERE pv.shop_id = ?",
                        Integer.class, shopId);
                if (revCount != null) {
                    shopTotalReviews = revCount;
                    shopTotalReviewsFormatted = (revCount >= 1000)
                            ? String.format(Locale.US, "%.1fk", revCount / 1000.0)
                            : String.valueOf(revCount);
                }

                Double avgRating = jdbc.queryForObject(
                        "SELECT AVG(r.rating) FROM reviews r " +
                        "JOIN product_variants pv ON pv.id = r.variant_id " +
                        "WHERE pv.shop_id = ?",
                        Double.class, shopId);
                if (avgRating != null) {
                    shopAverageRatingFormatted = String.format(Locale.US, "%.1f", avgRating);
                }

                Integer prodCount = jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT pv.id) FROM product_variants pv " +
                        "JOIN products p ON p.id = pv.product_id " +
                        "WHERE pv.shop_id = ? AND pv.status = 'ACTIVE' AND pv.approval_status IN ('APPROVED', 'PENDING') AND p.is_visible = 1",
                        Integer.class, shopId);
                if (prodCount != null) {
                    shopTotalProducts = prodCount;
                }
            } catch (Exception ignored) {}

            if (shop != null && shop.getCreatedAt() != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(shop.getCreatedAt(), LocalDateTime.now());
                if (days < 30) {
                    shopJoinTime = days <= 1 ? "Hôm nay" : days + " ngày trước";
                } else if (days < 365) {
                    shopJoinTime = (days / 30) + " tháng trước";
                } else {
                    shopJoinTime = (days / 365) + " năm trước";
                }
            }
        }
        model.addAttribute("shopTotalReviews", shopTotalReviews);
        model.addAttribute("shopTotalReviewsFormatted", shopTotalReviewsFormatted);
        model.addAttribute("shopAverageRatingFormatted", shopAverageRatingFormatted);
        model.addAttribute("shopTotalProducts", shopTotalProducts);
        model.addAttribute("shopJoinTime", shopJoinTime);
        model.addAttribute("shopResponseRate", shopResponseRate);
        model.addAttribute("shopResponseTime", shopResponseTime);
        // 4. Danh sách sản phẩm liên quan / gợi ý hôm nay (Shopee style)
        List<ProductVariant> allActiveVariants = productVariantRepository.findApprovedActiveForCatalog();
        List<ProductVariant> relatedProducts = allActiveVariants.stream()
                .filter(v -> !v.getId().equals(id))
                .limit(6)
                .toList();
        model.addAttribute("relatedProducts", relatedProducts);

        // 5. Các biến thể khác của CÙNG sản phẩm (chips "phân loại hàng" kiểu Shopee)
        //    Chỉ hiển thị khi sản phẩm có từ 2 biến thể ACTIVE trở lên.
        List<ProductVariant> siblingVariants = allActiveVariants.stream()
                .filter(v -> v.getProduct() != null
                        && variant.getProduct() != null
                        && v.getProduct().getId().equals(variant.getProduct().getId()))
                .toList();
        model.addAttribute("siblingVariants", siblingVariants);

        return "customer/customerproductdetail";
    }

    /**
     * Gửi đánh giá qua form submission tiêu chuẩn
     */
    @PostMapping("/product/{id}/review")
    public String submitReview(@PathVariable("id") Long id,
                               @RequestParam(value = "rating", defaultValue = "5") Integer rating,
                               @RequestParam(value = "content", required = false) String content,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (session == null || !"CUSTOMER".equals(session.getAttribute("role")) || !(session.getAttribute("userId") instanceof Number)) {
            redirectAttributes.addFlashAttribute("reviewError", "Vui lòng đăng nhập với tài khoản khách hàng để gửi đánh giá.");
            return "redirect:/login?continueTo=/product/" + id;
        }

        Long userId = ((Number) session.getAttribute("userId")).longValue();

        ProductVariant variant = productService.getProductVariantById(id);
        if (variant == null) {
            return "redirect:/";
        }

        if (content == null || content.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Vui lòng nhập nội dung đánh giá sản phẩm.");
            return "redirect:/product/" + id + "#reviews-section";
        }

        try {
            Long orderItemId = reviewService.findPurchasableOrderItem(id, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Bạn chỉ có thể đánh giá sản phẩm đã mua và giao thành công."));
            reviewService.addReview(id, userId, orderItemId, rating, content);
            redirectAttributes.addFlashAttribute("reviewSuccess", "Cảm ơn bạn! Đánh giá của bạn đã được đăng thành công.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("reviewError", ex.getMessage());
        }

        return "redirect:/product/" + id + "#reviews-section";
    }

    /**
     * API AJAX gửi đánh giá tức thì không cần tải lại toàn bộ trang
     */
    @PostMapping("/api/products/{id}/reviews")
    @ResponseBody
    public ResponseEntity<?> submitReviewApi(@PathVariable("id") Long id,
                                             @RequestBody Map<String, Object> payload,
                                             HttpSession session) {
        if (session == null || !"CUSTOMER".equals(session.getAttribute("role")) || !(session.getAttribute("userId") instanceof Number)) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Vui lòng đăng nhập tài khoản Khách hàng để đánh giá sản phẩm.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }

        Long userId = ((Number) session.getAttribute("userId")).longValue();
        ProductVariant variant = productService.getProductVariantById(id);
        if (variant == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Sản phẩm không tồn tại.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
        }

        Integer rating = 5;
        if (payload.get("rating") instanceof Number numRating) {
            rating = numRating.intValue();
        }

        String content = payload.get("content") != null ? payload.get("content").toString().trim() : "";
        if (content.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Nội dung đánh giá không được để trống.");
            return ResponseEntity.badRequest().body(err);
        }

        Long orderItemId = reviewService.findPurchasableOrderItem(id, userId).orElse(null);
        if (orderItemId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Bạn chỉ có thể đánh giá sản phẩm đã mua và giao thành công."));
        }
        Review saved;
        try {
            saved = reviewService.addReview(id, userId, orderItemId, rating, content);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
        List<ReviewDto> updatedReviews = reviewService.getReviewsForVariant(id);
        ReviewStatsDto updatedStats = reviewService.getReviewStats(id);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Đánh giá của bạn đã được đăng thành công!");
        res.put("reviews", updatedReviews);
        res.put("stats", updatedStats);

        return ResponseEntity.ok(res);
    }

    @PostMapping("/product/{id}/review/{reviewId}/edit")
    public String updateReview(@PathVariable Long id, @PathVariable Long reviewId,
                               @RequestParam Integer rating, @RequestParam String content,
                               HttpSession session, RedirectAttributes redirect) {
        if (session == null || !"CUSTOMER".equals(session.getAttribute("role")) || !(session.getAttribute("userId") instanceof Number)) {
            return "redirect:/login?continueTo=/product/" + id;
        }
        try {
            reviewService.updateReview(reviewId, id, ((Number) session.getAttribute("userId")).longValue(), rating, content);
            redirect.addFlashAttribute("reviewSuccess", "Đã cập nhật đánh giá.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("reviewError", ex.getMessage());
        }
        return "redirect:/product/" + id + "#reviews-section";
    }

    @PostMapping("/product/{id}/review/{reviewId}/delete")
    public String deleteReview(@PathVariable Long id, @PathVariable Long reviewId,
                               HttpSession session, RedirectAttributes redirect) {
        if (session == null || !"CUSTOMER".equals(session.getAttribute("role")) || !(session.getAttribute("userId") instanceof Number)) {
            return "redirect:/login?continueTo=/product/" + id;
        }
        try {
            reviewService.deleteReview(reviewId, id, ((Number) session.getAttribute("userId")).longValue());
            redirect.addFlashAttribute("reviewSuccess", "Đã xóa đánh giá.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("reviewError", ex.getMessage());
        }
        return "redirect:/product/" + id + "#reviews-section";
    }
}
