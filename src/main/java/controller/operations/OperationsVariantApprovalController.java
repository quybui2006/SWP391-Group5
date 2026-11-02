package controller.operations;

import dto.operations.ApprovalActionResponse;
import dto.operations.OperationsVariantDetailDto;
import dto.operations.PendingVariantApprovalDto;
import dto.operations.RejectApprovalRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import service.operations.OperationsVariantApprovalService;

import java.util.List;

@Controller
public class OperationsVariantApprovalController {
    private final OperationsVariantApprovalService approvals;

    public OperationsVariantApprovalController(OperationsVariantApprovalService approvals) {
        this.approvals = approvals;
    }

    /** Danh sách biến thể đang chờ Operations hậu kiểm. */
    @GetMapping("/operations/variants")
    public String pendingVariants(Model model) {
        List<PendingVariantApprovalDto> variants = approvals.findPendingVariants();
        model.addAttribute("variants", variants);
        return "operations/variants";
    }

    /** Chi tiết biến thể, sản phẩm cha, cửa hàng và tồn kho để phục vụ kiểm duyệt. */
    @GetMapping("/operations/variants/{variantId}")
    public String variantDetails(@PathVariable Long variantId,
                                 @RequestParam(required = false) Long returnShopId,
                                 @RequestParam(required = false) String returnPage,
                                 Model model) {
        OperationsVariantDetailDto variant = approvals.getVariantDetail(variantId);
        model.addAttribute("variant", variant);
        // Keep shop-based reviews in that shop's context after the action completes.
        boolean returningToShop = returnShopId != null && returnShopId.equals(variant.shopId());
        model.addAttribute("returnShopId", returningToShop ? variant.shopId() : null);
        model.addAttribute("returnUrl", returningToShop
                ? ("shops".equals(returnPage) ? "/operations/shops/" : "/operations/shop-list/") + variant.shopId()
                : "/operations/variants");
        return "operations/variant-detail";
    }

    /** Duyệt biến thể đang chờ và cho phép biến thể tiếp tục mở bán. */
    @PostMapping("/operations/api/variants/{variantId}/approve")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> approveVariant(@PathVariable Long variantId) {
        approvals.approveVariant(variantId);
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã duyệt biến thể. Biến thể đủ điều kiện mở bán."));
    }

    /** Từ chối biến thể với lý do bắt buộc để ngừng bán và phản hồi cho shop. */
    @PostMapping("/operations/api/variants/{variantId}/reject")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> rejectVariant(
            @PathVariable Long variantId, @Valid @RequestBody RejectApprovalRequest request) {
        approvals.rejectVariant(variantId, request.getRejectionReason());
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã từ chối biến thể và ngừng bán."));
    }
}
