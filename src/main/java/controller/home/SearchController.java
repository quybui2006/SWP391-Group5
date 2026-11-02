package controller.home;

import entity.Category;
import entity.ProductVariant;
import repository.CategoryRepository;
import repository.ShopProductRepository;
import service.shop.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class SearchController {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ShopProductRepository shopProductRepository;

    @GetMapping("/search")
    public String searchPage(
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "categoryId", required = false) List<Long> categoryIds,
            @RequestParam(value = "priceRange", required = false, defaultValue = "all") String priceRange,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "origin", required = false) List<String> origins,
            @RequestParam(value = "inStockOnly", required = false, defaultValue = "false") Boolean inStockOnly,
            @RequestParam(value = "sort", required = false, defaultValue = "popular") String sort,
            Model model) {

        // 1. Lấy tất cả danh mục để hiển thị ở Sidebar
        List<Category> allCategories = categoryRepository.findByIsActiveTrue();

        // 2. Lấy danh sách xuất xứ / vùng miền thực tế của sản phẩm
        List<String> allOrigins = shopProductRepository.findDistinctActiveOrigins();

        // 3. Truy vấn sản phẩm theo các tiêu chí Lọc & Tìm kiếm đa trường
        List<ProductVariant> searchResults = productService.searchAndFilterProducts(
                keyword, categoryIds, priceRange, minPrice, maxPrice, origins, inStockOnly, sort);

        // 4. Đẩy dữ liệu sang Thymeleaf
        model.addAttribute("categories", allCategories);
        model.addAttribute("origins", allOrigins);
        model.addAttribute("products", searchResults);

        // Trả lại các giá trị user đã chọn để giữ trạng thái Checked trên giao diện
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategories", categoryIds != null ? categoryIds : new ArrayList<>());
        model.addAttribute("priceRange", priceRange);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("selectedOrigins", origins != null ? origins : new ArrayList<>());
        model.addAttribute("inStockOnly", Boolean.TRUE.equals(inStockOnly));
        model.addAttribute("sort", sort);
        model.addAttribute("resultCount", searchResults.size());

        return "home/search";
    }
}
