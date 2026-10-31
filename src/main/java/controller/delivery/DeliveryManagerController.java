package controller.delivery;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.delivery.DeliveryOrderService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequestMapping("/delivery")
public class DeliveryManagerController {

    private static final String POD_UPLOAD_DIR = "src/main/resources/static/uploads/pod/";

    private final DeliveryOrderService deliveryOrderService;

    public DeliveryManagerController(DeliveryOrderService deliveryOrderService) {
        this.deliveryOrderService = deliveryOrderService;
    }

    private boolean isDeliveryManager(HttpSession session) {
        Object roleObj = session.getAttribute("role");
        if (roleObj == null) return false;
        String role = roleObj.toString().toUpperCase(Locale.ROOT);
        return role.contains("DELIVERY") || role.contains("SHIPPER");
    }

    @GetMapping({"", "/", "/orders"})
    public String listOrders(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        if (!isDeliveryManager(session)) {
            return "redirect:/"; // Không phải shipper thì đá về trang chủ
        }

        model.addAttribute("orders", deliveryOrderService.getDeliveryOrders());
        model.addAttribute("counts", deliveryOrderService.getOrderCounts());
        model.addAttribute("managerName", session.getAttribute("fullName") != null
                ? session.getAttribute("fullName") : "Delivery Manager");
        return "delivery/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable("id") Long orderId,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        if (!isDeliveryManager(session)) {
            return "redirect:/";
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
            @RequestParam(value = "deliveryNote", required = false) String deliveryNote,
            @RequestParam(value = "podImage", required = false) MultipartFile podImage,
            @RequestParam(value = "podImageUrl", required = false) String podImageUrlInput,
            @RequestParam(value = "returnToDetail", defaultValue = "false") boolean returnToDetail,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        if (!isDeliveryManager(session)) {
            return "redirect:/";
        }

        try {
            String finalPodUrl = (podImageUrlInput != null && !podImageUrlInput.isBlank())
                    ? podImageUrlInput.trim()
                    : null;

            // Xử lý file ảnh POD tải lên khi Shipper bấm Giao thành công / Hoàn tất
            if (podImage != null && !podImage.isEmpty()) {
                finalPodUrl = storePodFile(orderId, podImage);
            }

            // Gọi hàm 5 tham số đã nâng cấp trong DeliveryOrderService
            deliveryOrderService.updateDeliveryStatus(orderId, newStatus, cancelReason, finalPodUrl, deliveryNote);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cập nhật trạng thái đơn hàng #" + orderId + " thành công!");

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi cập nhật: " + e.getMessage());
        }

        if (returnToDetail) {
            return "redirect:/delivery/orders/" + orderId;
        }
        return "redirect:/delivery/orders";
    }

    /**
     * Lưu file ảnh POD vào thư mục static/uploads/pod và trả về đường dẫn hiển thị
     */
    private String storePodFile(Long orderId, MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String ext = ".jpg";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        }

        String fileName = "pod_order_" + orderId + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

        // 1. Lưu ra thư mục src/main/resources/static/uploads/pod/
        Path uploadPath = Paths.get(POD_UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        Path filePath = uploadPath.resolve(fileName);
        byte[] bytes = file.getBytes();
        Files.write(filePath, bytes);

        // 2. Đồng thời copy sang target/classes/static/uploads/pod/ để trình duyệt xem được ngay lập tức không cần restart
        try {
            Path targetPath = Paths.get("target/classes/static/uploads/pod/");
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }
            Files.write(targetPath.resolve(fileName), bytes);
        } catch (Exception ignored) {}

        return "/uploads/pod/" + fileName;
    }
}