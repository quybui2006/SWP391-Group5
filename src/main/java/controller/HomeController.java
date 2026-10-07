package controller;

import entity.ProductVariant;
import service.HomeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    @Autowired
    private HomeService homeService;

    @GetMapping({"/", "/home"})
    public String homePage(Model model) {
        // 1. Lấy danh sách sản phẩm từ database
        List featuredProducts = homeService.getActiveProductsForHome();

        // 2. Đưa dữ liệu vào Model để Thymeleaf có thể đọc được
        model.addAttribute("products", featuredProducts);

        // 3. Trả về tên file giao diện (sẽ map với thư mục templates/home.html)
        return "home";
    }
}