package controller.operations;

import dto.operations.ApprovalActionResponse;
import dto.operations.PendingShopApprovalDto;
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
import service.operations.OperationsShopApprovalService;

import java.util.List;

@Controller
public class OperationsShopApprovalController {
    private final OperationsShopApprovalService approvals;

    public OperationsShopApprovalController(OperationsShopApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/operations")
    public String operationsHome() {
        return "redirect:/operations/shops";
    }

    @GetMapping("/operations/shops")
    public String pendingShops(Model model) {
        List<PendingShopApprovalDto> shops = approvals.findPendingShops();
        model.addAttribute("shops", shops);
        return "operations/shops";
    }

    @PostMapping("/operations/api/shops/{shopId}/approve")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> approveShop(@PathVariable Long shopId) {
        approvals.approveShop(shopId);
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã duyệt yêu cầu mở Shop."));
    }

    @PostMapping("/operations/api/shops/{shopId}/reject")
    @ResponseBody
    public ResponseEntity<ApprovalActionResponse> rejectShop(
            @PathVariable Long shopId,
            @Valid @RequestBody RejectApprovalRequest request) {
        approvals.rejectShop(shopId, request.getRejectionReason());
        return ResponseEntity.ok(new ApprovalActionResponse(true, "Đã từ chối yêu cầu mở Shop."));
    }
}
