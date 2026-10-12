package controller;

import dto.ProductCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.ProductValidationException;
import service.ShopProductService;

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

    private Long ownerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) return null;
        Object value = session.getAttribute("userId");
        return value instanceof Number number ? number.longValue() : null;
    }
}
