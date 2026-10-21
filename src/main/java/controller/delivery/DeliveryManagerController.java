package controller.delivery;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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
    public String listOrders(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        model.addAttribute("orders", deliveryOrderService.getDeliveryOrders());
        model.addAttribute("managerName", session.getAttribute("fullName") != null
                ? session.getAttribute("fullName") : "Delivery Manager");
        return "delivery/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable("id") Long orderId, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            model.addAttribute("order", deliveryOrderService.getOrderDetail(orderId));
            model.addAttribute("items", deliveryOrderService.getOrderItems(orderId));
            model.addAttribute("managerName", session.getAttribute("fullName") != null
                    ? session.getAttribute("fullName") : "Delivery Manager");
            return "delivery/order-detail";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/delivery/orders";
        }
    }

    @PostMapping("/orders/update-status")
    public String updateStatus(
            @RequestParam("orderId") Long orderId,
            @RequestParam("newStatus") String newStatus,
            @RequestParam(value = "cancelReason", required = false) String cancelReason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            deliveryOrderService.updateDeliveryStatus(orderId, newStatus, cancelReason);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái đơn hàng #" + orderId + " thành công!");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi cập nhật: " + e.getMessage());
        }

        return "redirect:/delivery/orders";
    }
}