package controller.shop;

import dto.shop.InventoryAdjustmentForm;
import dto.shop.InventoryBatchForm;
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
import repository.CategoryRepository;
import service.shop.InventoryActionException;
import service.shop.InventoryService;

import java.time.LocalDateTime;

@Controller
public class ShopInventoryController {
    private final InventoryService inventoryService;
    private final CategoryRepository categoryRepository;

    public ShopInventoryController(InventoryService inventoryService, CategoryRepository categoryRepository) {
        this.inventoryService = inventoryService;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping({"/shop/inventory", "/shopowner/inventory"})
    public String inventoryManagement(HttpServletRequest request,
                                      @RequestParam(defaultValue = "") String keyword,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(defaultValue = "inventory") String tab,
                                      Model model) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        try {
            model.addAttribute("inventoryProducts", inventoryService.getInventoryProducts(ownerId, keyword, categoryId));
            model.addAttribute("transactionRows", inventoryService.getTransactionHistory(ownerId));
        } catch (InventoryActionException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("inventoryProducts", java.util.List.of());
            model.addAttribute("transactionRows", java.util.List.of());
        }
        model.addAttribute("categories", categoryRepository.findByIsActiveTrue());
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("activeTab", "history".equalsIgnoreCase(tab) ? "history" : "inventory");
        return "shopowner/inventory-management";
    }

    @GetMapping({"/shop/inventory/batches/new", "/shopowner/inventory/batches/new"})
    public String newBatchForm(@RequestParam(required = false) Long inventoryId,
                               HttpServletRequest request, Model model) {
        if (ownerId(request) == null) return "redirect:/login";
        InventoryBatchForm form = new InventoryBatchForm();
        form.setInventoryId(inventoryId);
        form.setQuantity(1);
        form.setReceivedAt(LocalDateTime.now().withSecond(0).withNano(0));
        model.addAttribute("form", form);
        model.addAttribute("variants", inventoryService.getVariantOptions(ownerId(request)));
        return "shopowner/inventory-batch-form";
    }

    @PostMapping({"/shop/inventory/batches", "/shopowner/inventory/batches"})
    public String receiveBatch(@Valid @ModelAttribute("form") InventoryBatchForm form,
                               BindingResult binding, HttpServletRequest request,
                               Model model, RedirectAttributes redirect) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        if (binding.hasErrors()) {
            model.addAttribute("variants", inventoryService.getVariantOptions(ownerId));
            model.addAttribute("error", "Kiểm tra biến thể, số lượng và thời điểm nhập.");
            return "shopowner/inventory-batch-form";
        }
        try {
            inventoryService.receiveBatch(form, ownerId);
            redirect.addFlashAttribute("success", "Đã tạo lô hàng mới và ghi lịch sử nhập kho.");
            return "redirect:/shopowner/inventory";
        } catch (InventoryActionException ex) {
            model.addAttribute("variants", inventoryService.getVariantOptions(ownerId));
            model.addAttribute("error", ex.getMessage());
            return "shopowner/inventory-batch-form";
        }
    }

    @GetMapping({"/shop/inventory/adjust", "/shopowner/inventory/adjust"})
    public String adjustmentForm(@RequestParam(required = false) Long batchId,
                                 @RequestParam(required = false) Long inventoryId,
                                 HttpServletRequest request, Model model) {
        if (ownerId(request) == null) return "redirect:/login";
        InventoryAdjustmentForm form = new InventoryAdjustmentForm();
        form.setBatchId(batchId);
        form.setInventoryId(inventoryId);
        form.setDirection("DECREASE");
        form.setQuantity(1);
        model.addAttribute("form", form);
        model.addAttribute("batches", inventoryService.getBatchOptions(ownerId(request), inventoryId));
        return "shopowner/inventory-adjust-form";
    }

    @PostMapping({"/shop/inventory/adjust", "/shopowner/inventory/adjust"})
    public String adjustBatch(@Valid @ModelAttribute("form") InventoryAdjustmentForm form,
                              BindingResult binding, HttpServletRequest request,
                              Model model, RedirectAttributes redirect) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        if (binding.hasErrors()) {
            model.addAttribute("batches", inventoryService.getBatchOptions(ownerId, form.getInventoryId()));
            model.addAttribute("error", "Chọn batch, hướng điều chỉnh, số lượng và lý do hợp lệ.");
            return "shopowner/inventory-adjust-form";
        }
        try {
            inventoryService.adjustBatch(form, ownerId);
            redirect.addFlashAttribute("success", "Đã điều chỉnh batch và lưu lịch sử giao dịch.");
            return "redirect:/shopowner/inventory?tab=history";
        } catch (InventoryActionException ex) {
            model.addAttribute("batches", inventoryService.getBatchOptions(ownerId, form.getInventoryId()));
            model.addAttribute("error", ex.getMessage());
            return "shopowner/inventory-adjust-form";
        }
    }

    private Long ownerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) return null;
        Object value = session.getAttribute("userId");
        return value instanceof Number number ? number.longValue() : null;
    }
}
