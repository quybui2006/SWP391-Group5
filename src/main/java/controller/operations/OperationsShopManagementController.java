package controller.operations;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import service.operations.OperationsShopManagementService;

@Controller
public class OperationsShopManagementController {
    private final OperationsShopManagementService shops;

    public OperationsShopManagementController(OperationsShopManagementService shops) {
        this.shops = shops;
    }

    @GetMapping("/operations/shop-list")
    public String list(@RequestParam(defaultValue = "") String keyword, Model model) {
        model.addAttribute("shops", shops.listShops(keyword));
        model.addAttribute("keyword", keyword.trim());
        return "operations/shop-list";
    }

    @GetMapping("/operations/shop-list/{shopId}")
    public String detail(@PathVariable Long shopId, Model model) {
        model.addAttribute("shop", shops.getShop(shopId));
        model.addAttribute("variants", shops.listShopVariants(shopId));
        return "operations/shop-detail";
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
