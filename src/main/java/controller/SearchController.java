package controller;

import entity.ProductVariant;
import service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class SearchController {

    @Autowired
    private ProductService productService;

    @GetMapping("/search")
    public String searchPage(
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            Model model) {

        // 1. Gọi Service lấy danh sách sản phẩm theo keyword
        List searchResults = productService.searchActiveProducts(keyword);

        // 2. Đẩy dữ liệu sang Thymeleaf
        model.addAttribute("products", searchResults);
        model.addAttribute("keyword", keyword); // Trả lại keyword để giữ text trên thanh tìm kiếm
        model.addAttribute("resultCount", searchResults.size()); // Đếm số kết quả

        // 3. Trả về giao diện search.html
        return "search";
    }
}