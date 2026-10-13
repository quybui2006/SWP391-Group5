package controller;

import dto.ApprovalActionResponse;
import dto.PendingProductApprovalDto;
import dto.RejectApprovalRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import service.OperationsProductApprovalService;

import java.util.List;

@Controller
public class OperationsProductApprovalController {
    private final OperationsProductApprovalService approvals;

    public OperationsProductApprovalController(OperationsProductApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/operations/products")
    public String pendingProducts(Model model) {
        List<PendingProductApprovalDto> products = approvals.findPendingProducts();
        model.addAttribute("products", products);
        return "operations/products";
    }

    @PostMapping("/operations/api/products/{productId}/approve")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> approveProduct(@PathVariable Long productId) {
        approvals.approveProduct(productId);
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã duyệt yêu cầu đăng sản phẩm."));
    }

    @PostMapping("/operations/api/products/{productId}/reject")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> rejectProduct(
            @PathVariable Long productId,
            @Valid @RequestBody RejectApprovalRequest request) {
        approvals.rejectProduct(productId, request.getRejectionReason());
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã từ chối yêu cầu đăng sản phẩm."));
    }
}
