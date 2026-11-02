package controller.shop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.customer.CurrentUserProvider;
import service.shop.ShopReopenRequestService;

@Controller
public class ShopReopenRequestController {
    private final CurrentUserProvider currentUser;
    private final ShopReopenRequestService requests;

    public ShopReopenRequestController(CurrentUserProvider currentUser, ShopReopenRequestService requests) {
        this.currentUser = currentUser;
        this.requests = requests;
    }

    @GetMapping("/shopowner/reopen-request")
    public String page(HttpServletRequest request, Model model) {
        Long ownerId = currentUser.requireUserId(request);
        model.addAttribute("shop", requests.getOwnerShopState(ownerId));
        model.addAttribute("requests", requests.getOwnerHistory(ownerId));
        return "shopowner/reopen-request";
    }

    @PostMapping("/shopowner/reopen-request")
    public String submit(HttpServletRequest request, @RequestParam String explanation, RedirectAttributes redirect) {
        Long ownerId = currentUser.requireUserId(request);
        try {
            requests.submit(ownerId, explanation);
            redirect.addFlashAttribute("success", "Đã gửi giải trình yêu cầu mở lại Shop.");
        } catch (ResponseStatusException ex) {
            redirect.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/shopowner/reopen-request";
    }
}
