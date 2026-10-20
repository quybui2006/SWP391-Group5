package controller.delivery;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.delivery.DeliveryOrderService;

@Controller
@RequestMapping("/delivery")
public class DeliveryManagerController {

    private final DeliveryOrderService deliveryOrderService;

    public DeliveryManagerController(DeliveryOrderService deliveryOrderService) {
        this.deliveryOrderService = deliveryOrderService;
    }

    @GetMapping({"", "/", "/orders"})
    public String listOrders(
            @RequestParam(value = "status", defaultValue = "ALL") String status,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            HttpSession session,
            Model model) {

        // Kiểm tra đăng nhập (Cho phép DELIVERY_MANAGER hoặc ADMIN truy cập để bạn dễ test)
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        model.addAttribute("orders", deliveryOrderService.getDeliveryOrders(status, keyword));
        model.addAttribute("counts", deliveryOrderService.getOrderCounts());
        model.addAttribute("currentStatus", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("managerName", session.getAttribute("fullName") != null
                ? session.getAttribute("fullName") : "Delivery Manager");

        return "delivery/orders";
    }

    @PostMapping("/orders/update-status")
    public String updateStatus(
            @RequestParam("orderId") Long orderId,
            @RequestParam("newStatus") String newStatus,
            @RequestParam(value = "cancelReason", required = false) String cancelReason,
            @RequestParam(value = "currentStatus", defaultValue = "ALL") String currentStatus,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            deliveryOrderService.updateDeliveryStatus(orderId, newStatus, cancelReason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật trạng thái đơn hàng #" + orderId + " sang " + newStatus + " thành công!");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi cập nhật: " + e.getMessage());
        }

        return "redirect:/delivery/orders?status=" + currentStatus;
    }
}