package controller.operations;

import dto.operations.OperationsVariantDetailDto;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.operations.OperationsVariantApprovalService;


@Controller
public class OperationsVariantApprovalController {
    private final OperationsVariantApprovalService approvals;

    public OperationsVariantApprovalController(OperationsVariantApprovalService approvals) {
        this.approvals = approvals;
    }

    /** Đưa URL danh sách cũ về catalog sản phẩm hiện tại. */
    @GetMapping("/operations/variants")
    public String pendingVariants(Model model) {
        return "redirect:/operations/products";
    }

    /** Hiển thị thông tin biến thể để Operations theo dõi. */
    @GetMapping("/operations/variants/{variantId}")
    public String variantDetails(@PathVariable Long variantId,
                                 @RequestParam(required = false) Long returnShopId,
                                 @RequestParam(required = false) String returnPage,
                                 Model model) {
        OperationsVariantDetailDto variant = approvals.getVariantDetail(variantId);
        model.addAttribute("variant", variant);
        boolean returningToShop = returnShopId != null && returnShopId.equals(variant.shopId());
        model.addAttribute("returnShopId", returningToShop ? variant.shopId() : null);
        String returnUrl = "/operations/variants";
        if (returningToShop) {
            returnUrl = "products".equals(returnPage)
                    ? "/operations/shop-list/" + variant.shopId() + "/products"
                    : ("shops".equals(returnPage) ? "/operations/shops/" : "/operations/shop-list/") + variant.shopId();
        }
        model.addAttribute("returnUrl", returnUrl);
        return "operations/variant-detail";
    }

    /** Chặn bán và lưu lý do do Operations nhập. */
    @PostMapping("/operations/variants/{variantId}/block")
    public String blockVariant(@PathVariable Long variantId, @RequestParam String reason,
                               RedirectAttributes redirect) {
        try {
            approvals.blockVariant(variantId, reason);
            redirect.addFlashAttribute("success", "Đã chặn bán biến thể.");
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/operations/variants/" + variantId;
    }

    /** Mở chặn; biến thể giữ trạng thái ngừng bán để shop tự quyết định bật lại. */
    @PostMapping("/operations/variants/{variantId}/unblock")
    public String unblockVariant(@PathVariable Long variantId, RedirectAttributes redirect) {
        try {
            approvals.unblockVariant(variantId);
            redirect.addFlashAttribute("success", "Đã mở chặn. Shop có thể chủ động đăng bán lại.");
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/operations/variants/" + variantId;
    }
}
