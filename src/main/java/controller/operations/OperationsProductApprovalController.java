package controller.operations;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** URL cũ được chuyển sang hàng đợi duyệt biến thể; parent Product không còn được duyệt. */
@Controller
public class OperationsProductApprovalController {
    @GetMapping("/operations/products")
    public String legacyProductApprovalsRedirect() {
        return "redirect:/operations/variants";
    }
}
