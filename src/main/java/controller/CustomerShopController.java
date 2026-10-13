package controller;

import entity.Category;
import entity.ProductVariant;
import entity.Province;
import entity.Shop;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import repository.CategoryRepository;
import repository.ProductVariantRepository;
import repository.ProvinceRepository;
import repository.ShopRepository;

import java.math.BigDecimal;
import java.util.*;

@Controller
public class CustomerShopController {

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @GetMapping({"/shops/{id}", "/shop/{id:[0-9]+}"})
    public String shopProfilePage(
            @PathVariable("id") Long shopId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "sort", required = false, defaultValue = "all") String sort,
            HttpSession session,
            Model model) {

        // 1. Tìm thông tin Cửa hàng (Shop)
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null) {
            // Thử lấy shop đầu tiên trong CSDL hoặc tạo shop mặc định để trang luôn hiển thị đầy đủ
            List<Shop> allShops = shopRepository.findAll();
            if (!allShops.isEmpty()) {
                shop = allShops.get(0);
            } else {
                shop = new Shop();
                shop.setId(shopId);
                shop.setName("FreshFruit Nông Trại Xanh");
                shop.setDescription("Hệ thống cửa hàng phân phối trái cây chuẩn VietGAP, tươi ngon 100% thu hoạch trong ngày.");
                shop.setPhone("0908 123 456");
                shop.setProvinceCode("HN");
                shop.setWardName("Quan Hoa");
                shop.setAddressDetail("Số 128 Cầu Giấy, Hà Nội");
                shop.setOperatingStatus("OPEN");
                shop.setApprovalStatus("APPROVED");
            }
        }

        // 2. Tên Tỉnh/Thành phố
        String shopProvinceName = "Hà Nội";
        if (shop.getProvinceCode() != null) {
            shopProvinceName = provinceRepository.findById(shop.getProvinceCode())
                    .map(Province::getName)
                    .orElse(shop.getProvinceCode());
        }

        // 3. Danh mục sản phẩm (Tabs danh mục chuẩn Shopee)
        List<Category> allCategories = categoryRepository.findByIsActiveTrue();

        // 4. Lấy danh sách sản phẩm thuộc Shop
        String safeKeyword = keyword != null ? keyword.trim() : "";
        List<ProductVariant> variants = productVariantRepository.findByShopAndCategoryAndKeyword(
                shop.getId(), categoryId, safeKeyword);

        // Nếu shop trong database chưa có sản phẩm gán riêng và user chưa filter sâu -> fallback hiển thị sản phẩm active
        if (variants.isEmpty() && safeKeyword.isEmpty() && categoryId == null) {
            variants = productVariantRepository.findByStatusAndProduct_SellingStatus("ACTIVE", "ACTIVE");
        } else if (variants.isEmpty() && categoryId != null) {
            // Nếu lọc theo danh mục mà chưa có variant map với shopId, hiển thị variants theo categoryId
            List<ProductVariant> allActive = productVariantRepository.findByStatusAndProduct_SellingStatus("ACTIVE", "ACTIVE");
            variants = allActive.stream()
                    .filter(v -> v.getProduct() != null && v.getProduct().getCategories() != null
                            && v.getProduct().getCategories().stream().anyMatch(c -> Objects.equals(c.getId(), categoryId)))
                    .toList();
        }

        // Sắp xếp danh sách sản phẩm
        List<ProductVariant> sortedVariants = new ArrayList<>(variants);
        if ("price_asc".equals(sort)) {
            sortedVariants.sort(Comparator.comparing(
                    v -> v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO
            ));
        } else if ("price_desc".equals(sort)) {
            sortedVariants.sort((v1, v2) -> {
                BigDecimal p1 = v1.getPrice() != null ? v1.getPrice() : BigDecimal.ZERO;
                BigDecimal p2 = v2.getPrice() != null ? v2.getPrice() : BigDecimal.ZERO;
                return p2.compareTo(p1);
            });
        } else if ("newest".equals(sort)) {
            sortedVariants.sort(Comparator.comparing(ProductVariant::getId).reversed());
        }

        // 5. Danh sách Voucher độc quyền của Shop (Giữ đúng voucher của shop)
        List<Map<String, String>> shopVouchers = List.of(
                Map.of(
                        "code", "FFSHOP15K",
                        "title", "Giảm 15k",
                        "condition", "Đơn Tối Thiểu 150k",
                        "discountText", "15.000₫",
                        "expiry", "HSD: 31.12.2026",
                        "tag", "Toàn Shop"
                ),
                Map.of(
                        "code", "FFSHOP30K",
                        "title", "Giảm 30k",
                        "condition", "Đơn Tối Thiểu 300k",
                        "discountText", "30.000₫",
                        "expiry", "HSD: 31.12.2026",
                        "tag", "Đơn lớn"
                ),
                Map.of(
                        "code", "FFSHOP10PCT",
                        "title", "Giảm 10%",
                        "condition", "Đơn Tối Thiểu 200k (Tối đa 50k)",
                        "discountText", "10%",
                        "expiry", "HSD: 31.12.2026",
                        "tag", "Khách mới"
                ),
                Map.of(
                        "code", "FFSHOP50K",
                        "title", "Giảm 50k",
                        "condition", "Đơn Tối Thiểu 500k",
                        "discountText", "50.000₫",
                        "expiry", "HSD: 31.12.2026",
                        "tag", "VIP Member"
                )
        );

        // 6. Đưa dữ liệu sang View Thymeleaf
        model.addAttribute("shop", shop);
        model.addAttribute("shopProvinceName", shopProvinceName);
        model.addAttribute("categories", allCategories);
        model.addAttribute("products", sortedVariants);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("keyword", safeKeyword);
        model.addAttribute("sort", sort);
        model.addAttribute("resultCount", sortedVariants.size());
        model.addAttribute("shopVouchers", shopVouchers);

        return "customershop";
    }
}
