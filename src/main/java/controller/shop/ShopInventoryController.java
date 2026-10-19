package controller.shop;

import dto.shop.InventoryActionRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.shop.InventoryActionException;
import service.shop.InventoryService;

@Controller
public class ShopInventoryController {

    private final InventoryService inventoryService;

    public ShopInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping({"/shop/inventory", "/shopowner/inventory"})
    public String inventoryManagement(HttpServletRequest request,
                                     @RequestParam(defaultValue = "inventory") String tab,
                                     Model model) {
        Long ownerUserId = ownerId(request);
        if (ownerUserId == null) return "redirect:/login";

        try {
            model.addAttribute("inventoryRows", inventoryService.getInventoryRows(ownerUserId));
            model.addAttribute("transactionRows", inventoryService.getTransactionHistory(ownerUserId));
        } catch (InventoryActionException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("inventoryRows", java.util.List.of());
            model.addAttribute("transactionRows", java.util.List.of());
        }
        model.addAttribute("inventoryActionRequest", new InventoryActionRequest());
        model.addAttribute("activeTab", "history".equalsIgnoreCase(tab) ? "history" : "inventory");
        return "shopowner/inventory-management";
    }

    @PostMapping({"/shop/inventory/action", "/shopowner/inventory/action"})
    public String processInventoryAction(
            @Valid @ModelAttribute("inventoryActionRequest") InventoryActionRequest form,
            BindingResult binding,
            HttpServletRequest request,
            RedirectAttributes redirect) {
        Long ownerUserId = ownerId(request);
        if (ownerUserId == null) return "redirect:/login";

        if (binding.hasErrors()) {
            String message = binding.getFieldErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse("Vui lòng kiểm tra lại thông tin giao dịch.");
            redirect.addFlashAttribute("error", message);
            return "redirect:/shop/inventory?tab=inventory";
        }

        try {
            inventoryService.processInventoryAction(form, ownerUserId);
            redirect.addFlashAttribute("success", successMessage(form));
            return "redirect:/shop/inventory?tab=history";
        } catch (InventoryActionException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/shop/inventory?tab=inventory";
        }
    }

    private String successMessage(InventoryActionRequest form) {
        return switch (form.getActionType()) {
            case RECEIVE -> "Đã cập nhật tăng số lượng tồn kho trên sàn.";
            case DISCARD -> "Đã ghi nhận hàng hỏng và cập nhật số lượng trên sàn.";
            case ADJUST -> "Đã điều chỉnh số lượng tồn kho trên sàn.";
        };
    }

    private Long ownerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) return null;
        Object value = session.getAttribute("userId");
        return value instanceof Number number ? number.longValue() : null;
    }
}
