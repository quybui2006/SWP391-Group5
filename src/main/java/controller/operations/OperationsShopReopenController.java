package controller.operations;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.shop.ShopReopenRequestService;

@Controller
public class OperationsShopReopenController {
    private final ShopReopenRequestService requests;

    public OperationsShopReopenController(ShopReopenRequestService requests) {
        this.requests = requests;
    }

    @GetMapping("/operations/reopen-requests")
    public String list(Model model) {
        model.addAttribute("requests", requests.findPending());
        return "operations/reopen-requests";
    }

    @PostMapping("/operations/reopen-requests/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            requests.approve(id);
            redirect.addFlashAttribute("success", "Đã mở lại Shop và thông báo cho chủ cửa hàng.");
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/operations/reopen-requests";
    }

    @PostMapping("/operations/reopen-requests/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam String reason, RedirectAttributes redirect) {
        try {
            requests.reject(id, reason);
            redirect.addFlashAttribute("success", "Đã từ chối yêu cầu và gửi lý do cho chủ cửa hàng.");
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/operations/reopen-requests";
    }
}
