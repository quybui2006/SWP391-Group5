package controller.shop;

import dto.shop.PromotionForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.shop.PromotionValidationException;
import service.shop.impl.PromotionServiceImpl;
import dto.shop.PromotionListItem;
import java.util.Collections;

@Controller
@RequestMapping("/shop/promotions")
public class ShopPromotionController {
    private final PromotionServiceImpl promotionService;
    /** Kết nối các trang quản lý khuyến mãi với service nghiệp vụ. */
    public ShopPromotionController(PromotionServiceImpl promotionService) { this.promotionService = promotionService; }

    /** Danh sách chương trình khuyến mãi của shop, hỗ trợ tìm kiếm. */
    @GetMapping
    public String list(@RequestParam(defaultValue = "") String keyword, HttpServletRequest request, Model model) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        try {
            model.addAttribute("promotions", promotionService.getPromotions(ownerId, keyword));
        } catch (DataAccessException ex) {
            model.addAttribute("promotions", Collections.<PromotionListItem>emptyList());
            model.addAttribute("error", "Không thể tải danh sách khuyến mãi lúc này. Vui lòng tải lại trang sau.");
        }
        model.addAttribute("keyword", keyword.trim());
        return "shopowner/promotion-list";
    }

    /** Hiển thị biểu mẫu tạo khuyến mãi và các biến thể đủ điều kiện. */
    @GetMapping("/add")
    public String addForm(HttpServletRequest request, Model model) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        PromotionForm form = new PromotionForm();
        model.addAttribute("promotionForm", form);
        populateForm(model, ownerId);
        return "shopowner/promotion-form";
    }

    /** Tạo khuyến mãi sau khi kiểm tra giá, thời gian, tồn kho và lịch bị trùng. */
    @PostMapping("/add")
    public String create(@Valid @ModelAttribute("promotionForm") PromotionForm form, BindingResult binding,
                         HttpServletRequest request, Model model, RedirectAttributes redirect) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        if (binding.hasErrors()) { populateForm(model, ownerId); return "shopowner/promotion-form"; }
        try {
            promotionService.save(form, ownerId, null);
            redirect.addFlashAttribute("success", "Đã tạo chương trình khuyến mãi.");
            return "redirect:/shop/promotions";
        } catch (PromotionValidationException ex) {
            if ("serviceError".equals(ex.getField())) model.addAttribute("serviceError", ex.getMessage());
            else binding.rejectValue(ex.getField(), "promotion.invalid", ex.getMessage());
        } catch (DataAccessException ex) {
            model.addAttribute("serviceError", "Không thể lưu khuyến mãi lúc này. Vui lòng kiểm tra dữ liệu và thử lại.");
        }
        populateForm(model, ownerId);
        return "shopowner/promotion-form";
    }

    /** Nạp dữ liệu khuyến mãi thuộc shop để chỉnh sửa. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpServletRequest request, Model model, RedirectAttributes redirect) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        try {
            model.addAttribute("promotionForm", promotionService.getForm(id, ownerId));
            model.addAttribute("promotionId", id);
            populateForm(model, ownerId);
            return "shopowner/promotion-form";
        } catch (PromotionValidationException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/shop/promotions";
        }
    }

    /** Cập nhật khuyến mãi sau khi xác thực quyền sở hữu và các điều kiện nghiệp vụ. */
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("promotionForm") PromotionForm form,
                         BindingResult binding, HttpServletRequest request, Model model, RedirectAttributes redirect) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        if (binding.hasErrors()) { model.addAttribute("promotionId", id); populateForm(model, ownerId); return "shopowner/promotion-form"; }
        try {
            promotionService.save(form, ownerId, id);
            redirect.addFlashAttribute("success", "Đã cập nhật chương trình khuyến mãi.");
            return "redirect:/shop/promotions";
        } catch (PromotionValidationException ex) {
            if ("serviceError".equals(ex.getField())) model.addAttribute("serviceError", ex.getMessage());
            else binding.rejectValue(ex.getField(), "promotion.invalid", ex.getMessage());
        } catch (DataAccessException ex) {
            model.addAttribute("serviceError", "Không thể cập nhật khuyến mãi lúc này. Vui lòng thử lại.");
        }
        model.addAttribute("promotionId", id);
        populateForm(model, ownerId);
        return "shopowner/promotion-form";
    }

    /** Bật hoặc tắt khuyến mãi, không cho bật lịch đã hết hạn hoặc bị chồng lấn. */
    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        try {
            promotionService.toggle(id, ownerId);
            redirect.addFlashAttribute("success", "Đã cập nhật trạng thái khuyến mãi.");
        } catch (PromotionValidationException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("error", "Không thể cập nhật trạng thái. Vui lòng thử lại.");
        }
        return "redirect:/shop/promotions";
    }

    /** Xóa khuyến mãi chưa được dùng trong đơn để bảo toàn lịch sử giao dịch. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        try {
            promotionService.delete(id, ownerId);
            redirect.addFlashAttribute("success", "Đã xóa chương trình khuyến mãi.");
        } catch (PromotionValidationException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("error", "Không thể xóa khuyến mãi lúc này. Vui lòng thử lại.");
        }
        return "redirect:/shop/promotions";
    }

    /** Nạp biến thể hợp lệ vào form và đưa thông báo nếu không đọc được dữ liệu. */
    private void populateForm(Model model, Long ownerId) {
        try {
            model.addAttribute("variantOptions", promotionService.getVariantOptions(ownerId));
        } catch (DataAccessException ex) {
            model.addAttribute("variantOptions", java.util.Collections.emptyList());
            model.addAttribute("serviceError", "Không thể tải danh sách biến thể sản phẩm. Vui lòng thử lại sau.");
        }
    }

    /** Lấy mã chủ shop từ phiên đăng nhập và chặn người dùng sai vai trò. */
    private Long ownerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) return null;
        Object value = session.getAttribute("userId");
        return value instanceof Number number ? number.longValue() : null;
    }
}
