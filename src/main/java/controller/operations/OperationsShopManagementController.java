package controller.operations;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import repository.CategoryRepository;
import service.operations.OperationsShopApprovalService;
import service.operations.OperationsShopManagementService;

@Controller
public class OperationsShopManagementController {
    private final OperationsShopManagementService shops;
    private final OperationsShopApprovalService approvals;
    private final CategoryRepository categories;

    public OperationsShopManagementController(
            OperationsShopManagementService shops,
            OperationsShopApprovalService approvals,
            CategoryRepository categories) {
        this.shops = shops;
        this.approvals = approvals;
        this.categories = categories;
    }

    @GetMapping("/operations/shop-list")
    public String list(@RequestParam(defaultValue = "") String keyword, Model model) {
        model.addAttribute("shops", shops.listShops(keyword));
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("activePage", "shop-list");
        return "operations/shop-list";
    }

    @GetMapping("/operations/shop-list/{shopId}")
    public String detail(@PathVariable Long shopId, Model model) {
        model.addAttribute("shop", approvals.getShopDetail(shopId));
        model.addAttribute("variants", shops.listShopVariants(shopId));
        // Show the full catalog grouped by product on the shop's audit detail.
        model.addAttribute("products", shops.listShopProductsWithVariants(shopId));
        model.addAttribute("activeNav", "shop-list");
        model.addAttribute("returnPage", "shop-list");
        return "operations/shop-detail";
    }

    @GetMapping("/operations/shop-list/{shopId}/products")
    public String products(@PathVariable Long shopId,
                           @RequestParam(defaultValue = "") String keyword,
                           @RequestParam(required = false) Long categoryId,
                           Model model) {
        model.addAttribute("shop", shops.getShop(shopId));
        model.addAttribute("products", shops.listShopProductsWithVariants(shopId, keyword, categoryId));
        model.addAttribute("categories", categories.findByIsActiveTrue());
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("activePage", "shop-list");
        return "operations/shop-products";
    }

    @GetMapping("/operations/post-audit")
    public String postAuditList(@RequestParam(defaultValue = "") String keyword, Model model) {
        model.addAttribute("shops", shops.listShops(keyword));
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("activePage", "shop-list");
        return "operations/shop-list";
    }

    @PostMapping("/operations/shop-list/{shopId}/block")
    public String block(@PathVariable Long shopId, @RequestParam String reason, RedirectAttributes redirect) {
        try {
            shops.suspendShop(shopId, reason);
            redirect.addFlashAttribute("success", "Đã chặn Shop và gửi thông báo cho chủ cửa hàng.");
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/operations/shop-list/" + shopId;
    }
}
