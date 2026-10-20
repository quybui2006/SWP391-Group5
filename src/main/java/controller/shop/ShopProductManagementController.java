package controller.shop;

import dto.shop.ProductCreateRequest;
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
import service.shop.ProductValidationException;
import service.shop.ShopProductService;
import repository.CategoryRepository;

import java.time.LocalDate;

@Controller
public class ShopProductManagementController {
    private static final int PAGE_SIZE = 10;
    private final ShopProductService products;
    private final CategoryRepository categoryRepository;

    public ShopProductManagementController(ShopProductService products,
                                          CategoryRepository categoryRepository) {
        this.products = products;
        this.categoryRepository = categoryRepository;
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
        populateActiveCategories(model);
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
        if (binding.hasErrors()) {
            populateActiveCategories(model);
            return "shopowner/product-form";
        }
        try {
            Long productId = products.createProduct(ownerId, form);
            redirect.addFlashAttribute("success", "Đã tạo hồ sơ sản phẩm để quản lý biến thể. Hãy thêm biến thể để đăng bán; Operations sẽ hậu kiểm sau.");
            return "redirect:/shop/products";
        } catch (ProductValidationException ex) {
            // The service supplies user-facing text, not a Spring message-code.
            // Passing it as rejectValue's second argument makes Thymeleaf resolve
            // that Vietnamese sentence as a message key and can trigger NoSuchMessageException.
            ex.getFieldErrors().forEach((field, message) -> {
                if (field != null && !field.isBlank() && message != null && !message.isBlank()) {
                    binding.rejectValue(field, null, message);
                }
            });
        } catch (DataIntegrityViolationException ex) {
            model.addAttribute("serviceError", "Không thể lưu sản phẩm. Hãy kiểm tra mã lô, danh mục và dữ liệu bắt buộc.");
        }
        populateActiveCategories(model);
        return "shopowner/product-form";
    }

    private void populateActiveCategories(Model model) {
        model.addAttribute("categories", categoryRepository.findByIsActiveTrue());
    }

    private Long ownerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) return null;
        Object value = session.getAttribute("userId");
        return value instanceof Number number ? number.longValue() : null;
    }
}
