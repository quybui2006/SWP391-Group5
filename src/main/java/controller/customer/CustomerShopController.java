package controller.customer;

import entity.Category;
import entity.ProductVariant;
import entity.Province;
import entity.Shop;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import repository.CategoryRepository;
import repository.ProductVariantRepository;
import repository.ProvinceRepository;
import repository.ShopRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    @Autowired
    private JdbcTemplate jdbc;

    @GetMapping({"/shops/{id}", "/shop/{id:[0-9]+}"})
    public String shopProfilePage(
            @PathVariable("id") Long shopId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "sort", required = false, defaultValue = "all") String sort,
            HttpSession session,
            Model model) {

        // 1. TÃ¬m thÃ´ng tin Cá»­a hÃ ng (Shop)
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null) {
            // Thá»­ láº¥y shop Ä‘áº§u tiÃªn trong CSDL hoáº·c táº¡o shop máº·c Ä‘á»‹nh Ä‘á»ƒ trang luÃ´n hiá»ƒn thá»‹ Ä‘áº§y Ä‘á»§
            List<Shop> allShops = shopRepository.findAll();
            if (!allShops.isEmpty()) {
                shop = allShops.get(0);
            } else {
                shop = new Shop();
                shop.setId(shopId);
                shop.setName("FreshFruit NÃ´ng Tráº¡i Xanh");
                shop.setDescription("Há»‡ thá»‘ng cá»­a hÃ ng phÃ¢n phá»‘i trÃ¡i cÃ¢y chuáº©n VietGAP, tÆ°Æ¡i ngon 100% thu hoáº¡ch trong ngÃ y.");
                shop.setPhone("0908 123 456");
                shop.setProvinceCode("HN");
                shop.setWardName("Quan Hoa");
                shop.setAddressDetail("Sá»‘ 128 Cáº§u Giáº¥y, HÃ  Ná»™i");
                shop.setOperatingStatus("OPEN");
                shop.setApprovalStatus("APPROVED");
            }
        }

        // 2. TÃªn Tá»‰nh/ThÃ nh phá»‘
        String shopProvinceName = "HÃ  Ná»™i";
        if (shop.getProvinceCode() != null) {
            shopProvinceName = provinceRepository.findById(shop.getProvinceCode())
                    .map(Province::getName)
                    .orElse(shop.getProvinceCode());
        }

        // 3. Danh má»¥c sáº£n pháº©m (Tabs danh má»¥c chuáº©n Shopee)
        List<Category> allCategories = categoryRepository.findByIsActiveTrue();

        // 4. Láº¥y danh sÃ¡ch sáº£n pháº©m thuá»™c Shop
        String safeKeyword = keyword != null ? keyword.trim() : "";
        List<ProductVariant> variants = productVariantRepository.findByShopAndCategoryAndKeyword(
                shop.getId(), categoryId, safeKeyword);

        // Sáº¯p xáº¿p danh sÃ¡ch sáº£n pháº©m
        List<ProductVariant> sortedVariants = new ArrayList<>(variants);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        sortedVariants.forEach(v -> jdbc.query("SELECT promotional_price FROM promotional_prices WHERE variant_id=? AND is_enabled=1 AND starts_at<=? AND ends_at>? ORDER BY starts_at DESC,id DESC LIMIT 1",
                rs -> { if (rs.next()) v.setPromotionalPrice(rs.getBigDecimal(1)); }, v.getId(), now, now));
        if ("price_asc".equals(sort)) {
            sortedVariants.sort(Comparator.comparing(
                    v -> v.getPromotionalPrice() != null ? v.getPromotionalPrice() : (v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO)
            ));
        } else if ("price_desc".equals(sort)) {
            sortedVariants.sort((v1, v2) -> {
                BigDecimal p1 = v1.getPromotionalPrice() != null ? v1.getPromotionalPrice() : (v1.getPrice() != null ? v1.getPrice() : BigDecimal.ZERO);
                BigDecimal p2 = v2.getPromotionalPrice() != null ? v2.getPromotionalPrice() : (v2.getPrice() != null ? v2.getPrice() : BigDecimal.ZERO);
                return p2.compareTo(p1);
            });
        } else if ("newest".equals(sort)) {
            sortedVariants.sort(Comparator.comparing(ProductVariant::getId).reversed());
        }

        // 5. Danh sÃ¡ch Voucher Ä‘á»™c quyá»n cá»§a Shop (Giá»¯ Ä‘Ãºng voucher cá»§a shop)
        List<Map<String, String>> shopVouchers = jdbc.query("SELECT code,name,discount_type,discount_value,max_discount_amount,min_order_value FROM vouchers WHERE shop_id=? AND voucher_type='DISCOUNT' AND is_public=1 AND status='ACTIVE' AND starts_at<=? AND ends_at>? AND used_count<max_usages AND EXISTS (SELECT 1 FROM shops s WHERE s.id=vouchers.shop_id AND s.approval_status='APPROVED' AND s.operating_status='OPEN') ORDER BY starts_at,id",
                (rs, row) -> {
                    Map<String, String> voucher = new LinkedHashMap<>();
                    String discountText = "PERCENTAGE".equals(rs.getString("discount_type"))
                            ? rs.getBigDecimal("discount_value").stripTrailingZeros().toPlainString() + "%"
                            : String.format(java.util.Locale.forLanguageTag("vi-VN"), "%,dâ‚«", rs.getBigDecimal("discount_value").longValue());
                    String condition = "ÄÆ¡n tá»‘i thiá»ƒu " + String.format(java.util.Locale.forLanguageTag("vi-VN"), "%,dâ‚«", rs.getBigDecimal("min_order_value").longValue());
                    if (rs.getBigDecimal("max_discount_amount") != null) condition += " Â· Giáº£m tá»‘i Ä‘a " + String.format(java.util.Locale.forLanguageTag("vi-VN"), "%,dâ‚«", rs.getBigDecimal("max_discount_amount").longValue());
                    voucher.put("code", rs.getString("code")); voucher.put("title", "Giáº£m " + discountText);
                    voucher.put("condition", condition); voucher.put("discountText", discountText);
                    voucher.put("tag", rs.getString("name"));
                    return voucher;
                }, shop.getId(), now, now);
        List<Map<String, String>> shopCashbackVouchers = jdbc.query("SELECT code,name,discount_type,discount_value,max_discount_amount,min_order_value FROM vouchers WHERE shop_id=? AND voucher_type='CASHBACK' AND is_public=1 AND status='ACTIVE' AND starts_at<=? AND ends_at>? AND used_count<max_usages AND EXISTS (SELECT 1 FROM shops s WHERE s.id=vouchers.shop_id AND s.approval_status='APPROVED' AND s.operating_status='OPEN') ORDER BY starts_at,id",
                (rs, row) -> {
                    Map<String, String> voucher = new LinkedHashMap<>();
                    voucher.put("code", rs.getString("code")); voucher.put("title", "Hoàn " + rs.getBigDecimal("discount_value").stripTrailingZeros().toPlainString() + "% xu");
                    voucher.put("condition", "Đơn tối thiểu " + String.format(java.util.Locale.forLanguageTag("vi-VN"), "%,d₫", rs.getBigDecimal("min_order_value").longValue()) + (rs.getBigDecimal("max_discount_amount") != null ? " · Tối đa " + rs.getBigDecimal("max_discount_amount").longValue() + " xu" : ""));
                    voucher.put("discountText", rs.getBigDecimal("discount_value").stripTrailingZeros().toPlainString() + "% xu"); voucher.put("tag", rs.getString("name")); return voucher;
                }, shop.getId(), now, now);

        // 6. ÄÆ°a dá»¯ liá»‡u sang View Thymeleaf
        model.addAttribute("shop", shop);
        model.addAttribute("shopProvinceName", shopProvinceName);
        model.addAttribute("categories", allCategories);
        model.addAttribute("products", sortedVariants);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("keyword", safeKeyword);
        model.addAttribute("sort", sort);
        model.addAttribute("resultCount", sortedVariants.size());
        model.addAttribute("shopVouchers", shopVouchers);
        model.addAttribute("shopCashbackVouchers", shopCashbackVouchers);

        return "customer/customershop";
    }
}
