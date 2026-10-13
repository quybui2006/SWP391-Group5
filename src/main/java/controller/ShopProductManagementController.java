package controller;

import dto.ProductCreateRequest;
import dto.SubmitProductApprovalResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.ProductValidationException;
import service.ShopProductService;
import service.ProductNotFoundException;

import java.time.LocalDate;

@Controller
public class ShopProductManagementController {
    private static final int PAGE_SIZE = 10;
    private final ShopProductService products;

    public ShopProductManagementController(ShopProductService products) {
        this.products = products;
    }

    @GetMapping({"/shop/products", "/shopowner/products"})
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       HttpServletRequest request, Model model) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        Page<?> result = products.searchShopProducts(ownerId, keyword, Math.max(0, page), PAGE_SIZE);
        model.addAttribute("products", result);
        model.addAttribute("keyword", keyword.trim());
        return "shopowner/product-list";
    }

    @GetMapping({"/shop/products/add", "/shopowner/products/add"})
    public String addForm(HttpServletRequest request, Model model) {
        if (ownerId(request) == null) return "redirect:/login";
        ProductCreateRequest form = new ProductCreateRequest();
        form.setReceivedDate(LocalDate.now());
        model.addAttribute("productCreateRequest", form);
        return "shopowner/product-form";
    }

    @PostMapping({"/shop/products/add", "/shopowner/products/add"})
    public String create(@Valid @ModelAttribute("productCreateRequest") ProductCreateRequest form,
                        BindingResult binding, HttpServletRequest request, Model model,
                        RedirectAttributes redirect) {
        Long ownerId = ownerId(request);
        if (ownerId == null) return "redirect:/login";
        if (form.getImageFile() == null || form.getImageFile().isEmpty()) {
            binding.rejectValue("imageFile", "imageFile.required", "Vui lòng chọn ảnh sản phẩm.");
        }
        if (form.getReceivedDate() != null && form.getExpiryDate() != null
                && !form.getExpiryDate().isAfter(form.getReceivedDate())) {
            binding.rejectValue("expiryDate", "expiryDate.invalid", "Ngày hết hạn phải sau ngày nhập kho.");
        }
        if (binding.hasErrors()) return "shopowner/product-form";
        try {
            Long productId = products.createProduct(ownerId, form);
            redirect.addFlashAttribute("success", "Đã tạo sản phẩm và lưu ảnh thành công. Bạn có thể thêm biến thể trong mục Biến thể sản phẩm.");
            return "redirect:/shop/products";
        } catch (ProductValidationException ex) {
            ex.getFieldErrors().forEach(binding::rejectValue);
        } catch (DataIntegrityViolationException ex) {
            model.addAttribute("serviceError", "Không thể lưu sản phẩm. Hãy kiểm tra mã lô và dữ liệu bắt buộc.");
        }
        return "shopowner/product-form";
    }

    /** API Shop Owner gửi sản phẩm đang ở DRAFT sang Operations Manager duyệt. */
    @PostMapping({"/shop/products/{id}/submit-approval", "/shopowner/products/{id}/submit-approval"})
    @ResponseBody
    public ResponseEntity<SubmitProductApprovalResponse> submitForApproval(
            @PathVariable Long id, HttpServletRequest request) {
        Long ownerId = ownerId(request);
        if (ownerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new SubmitProductApprovalResponse(false, "Vui lòng đăng nhập bằng tài khoản Shop Owner.", null));
        }
        try {
            products.submitForApproval(id, ownerId);
            return ResponseEntity.ok(new SubmitProductApprovalResponse(
                    true, "Đã gửi yêu cầu duyệt sản phẩm đến Quản lý vận hành.", "PENDING"));
        } catch (ProductValidationException ex) {
            String message = ex.getFieldErrors().values().stream().findFirst()
                    .orElse("Dữ liệu sản phẩm chưa hợp lệ.");
            return ResponseEntity.badRequest().body(
                    new SubmitProductApprovalResponse(false, message, null));
        } catch (ProductNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new SubmitProductApprovalResponse(false, "Không tìm thấy sản phẩm của cửa hàng.", null));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(
                    new SubmitProductApprovalResponse(false,
                            ex.getReason() == null ? "Không thể gửi duyệt sản phẩm." : ex.getReason(), null));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.badRequest().body(new SubmitProductApprovalResponse(false,
                    "Không thể gửi duyệt do dữ liệu chưa đáp ứng điều kiện hệ thống. Vui lòng tải lại và thử lại.", null));
        }
    }

    private Long ownerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) return null;
        Object value = session.getAttribute("userId");
        return value instanceof Number number ? number.longValue() : null;
    }
}
