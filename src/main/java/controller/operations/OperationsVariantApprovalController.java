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
import org.springframework.web.bind.annotation.ResponseBody;
import service.operations.OperationsVariantApprovalService;

import java.util.List;

@Controller
public class OperationsVariantApprovalController {
    private final OperationsVariantApprovalService approvals;

    public OperationsVariantApprovalController(OperationsVariantApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/operations/variants")
    public String pendingVariants(Model model) {
        List<PendingVariantApprovalDto> variants = approvals.findPendingVariants();
        model.addAttribute("variants", variants);
        return "operations/variants";
    }

    @GetMapping("/operations/variants/{variantId}")
    public String variantDetails(@PathVariable Long variantId, Model model) {
        OperationsVariantDetailDto variant = approvals.getVariantDetail(variantId);
        model.addAttribute("variant", variant);
        return "operations/variant-detail";
    }

    @PostMapping("/operations/api/variants/{variantId}/approve")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> approveVariant(@PathVariable Long variantId) {
        approvals.approveVariant(variantId);
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã duyệt biến thể. Biến thể đủ điều kiện mở bán."));
    }

    @PostMapping("/operations/api/variants/{variantId}/reject")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> rejectVariant(
            @PathVariable Long variantId, @Valid @RequestBody RejectApprovalRequest request) {
        approvals.rejectVariant(variantId, request.getRejectionReason());
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã từ chối biến thể và ngừng bán."));
    }
}
