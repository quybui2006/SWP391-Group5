package controller.operations;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import service.operations.OperationsShopManagementService;

/** Catalog Operations toàn sàn; mỗi biến thể vẫn dùng luồng hậu kiểm hiện có. */
@Controller
public class OperationsProductApprovalController {
    private final OperationsShopManagementService products;

    public OperationsProductApprovalController(OperationsShopManagementService products) {
        this.products = products;
    }

    @GetMapping("/operations/products")
    public String productCatalog(@RequestParam(defaultValue = "") String keyword, Model model) {
        model.addAttribute("products", products.listAllProductsWithVariants(keyword));
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("activePage", "products");
        return "operations/products";
    }
}
