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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        // 4. Danh sách sản phẩm liên quan / gợi ý hôm nay (Shopee style)
        List<ProductVariant> allActiveVariants = productVariantRepository.findByStatusAndProduct_SellingStatusAndProduct_ApprovalStatus("ACTIVE", "ACTIVE", "APPROVED");
        List<ProductVariant> relatedProducts = allActiveVariants.stream()
                .filter(v -> !v.getId().equals(id))
                .limit(6)
                .toList();
        model.addAttribute("relatedProducts", relatedProducts);

        return "customerproductdetail";
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

        reviewService.addReview(id, userId, rating, content);
        redirectAttributes.addFlashAttribute("reviewSuccess", "Cảm ơn bạn! Đánh giá của bạn đã được đăng thành công.");

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

        Review saved = reviewService.addReview(id, userId, rating, content);
        List<ReviewDto> updatedReviews = reviewService.getReviewsForVariant(id);
        ReviewStatsDto updatedStats = reviewService.getReviewStats(id);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Đánh giá của bạn đã được đăng thành công!");
        res.put("reviews", updatedReviews);
        res.put("stats", updatedStats);

        return ResponseEntity.ok(res);
    }
}
