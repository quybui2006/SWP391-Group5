package controller;

import entity.Category;
import entity.ProductVariant;
import repository.CategoryRepository;
import service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

@Controller
public class SearchController {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping("/search")
    public String searchPage(
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "categoryId", required = false) List<Long> categoryIds,
            @RequestParam(value = "priceRange", required = false, defaultValue = "all") String priceRange,
            Model model) {

        // 1. Lấy tất cả danh mục để hiển thị ở Sidebar
        List<Category> allCategories = categoryRepository.findByIsActiveTrue();

        // 2. Truy vấn sản phẩm theo các tiêu chí Lọc
        List<ProductVariant> searchResults = productService.searchAndFilterProducts(keyword, categoryIds, priceRange);

        // 3. Đẩy dữ liệu sang Thymeleaf
        model.addAttribute("categories", allCategories);
        model.addAttribute("products", searchResults);

        // Trả lại các giá trị user đã chọn để giữ trạng thái Checked trên giao diện
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategories", categoryIds != null ? categoryIds : new ArrayList<>());
        model.addAttribute("priceRange", priceRange);
        model.addAttribute("resultCount", searchResults.size());

        return "search";
    }
}
