package controller.shop;


import dto.shop.ProductVariantCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.UnitRepository;
import service.customer.CurrentUserProvider;
import service.shop.ProductValidationException;
import service.shop.ShopOrderService;
import service.shop.ShopProductService;

@Controller
public class ShopOwnerController {

    private final ShopProductService products;
    private final CurrentUserProvider currentUser;
    private final ShopOrderService orders;
    private final UnitRepository units;

    public ShopOwnerController(
            ShopProductService products,
            CurrentUserProvider currentUser,
            ShopOrderService orders,
            UnitRepository units) {

        this.products = products;
        this.currentUser = currentUser;
        this.orders = orders;
        this.units = units;
    }

    @GetMapping("/shopowner/home")
    public String shopOwnerHome(
            HttpServletRequest request,
            Model model) {

        Long ownerId = currentUser.requireUserId(request);

        HttpSession session = request.getSession(false);
        model.addAttribute(
                "ownerName",
                session != null ? session.getAttribute("fullName") : null
        );

        model.addAttribute("shop", products.shopDashboard(ownerId));
        model.addAttribute("stats", products.shopDashboardStats(ownerId));
        model.addAttribute("recentVariants", products.recentShopVariants(ownerId));
        model.addAttribute("orderSummary", orders.summary(ownerId));

        return "shopowner/home";
    }

    @GetMapping("/shopowner/product-variants")
    public String productVariants(
            @RequestParam(defaultValue = "") String keyword,
            HttpServletRequest request,
            Model model) {

        Long ownerId = ownerId(request);

        if (ownerId == null) {
            return "redirect:/login";
        }

        model.addAttribute(
                "variants",
                products.searchShopVariants(ownerId, keyword)
        );
        model.addAttribute(
                "variantSummary",
                products.shopVariantSummary(ownerId)
        );
        model.addAttribute("keyword", keyword.trim());

        return "shopowner/product-variants";
    }

    @GetMapping("/shopowner/product-variants/add")
    public String addVariantForm(
            @RequestParam(required = false) Long productId,
            HttpServletRequest request,
            Model model) {

        Long ownerId = ownerId(request);

        if (ownerId == null) {
            return "redirect:/login";
        }

        ProductVariantCreateRequest form = new ProductVariantCreateRequest();
        form.setProductId(productId);

        model.addAttribute("variantCreateRequest", form);
        addVariantOptions(ownerId, model);

        return "shopowner/product-variant-form";
    }

    @PostMapping("/shopowner/product-variants/add")
    public String createVariant(
            @Valid @ModelAttribute("variantCreateRequest")
            ProductVariantCreateRequest form,
            BindingResult binding,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirect) {

        Long ownerId = ownerId(request);

        if (ownerId == null) {
            return "redirect:/login";
        }

        if (binding.hasErrors()) {
            addVariantOptions(ownerId, model);
            return "shopowner/product-variant-form";
        }

        try {
            products.createVariantInventory(ownerId, form);

            redirect.addFlashAttribute(
                    "success",
                    "Đã lưu biến thể và thông tin tồn kho."
            );

            return "redirect:/shopowner/product-variants";

        } catch (ProductValidationException ex) {
            // These are display messages from the service, not message-code keys.
            // Keep them as default messages so Thymeleaf does not try to resolve
            // the literal Vietnamese sentence through MessageSource.
            ex.getFieldErrors().forEach((field, message) -> {
                if (field != null && !field.isBlank() && message != null && !message.isBlank()) {
                    binding.rejectValue(field, null, message);
                }
            });

        } catch (DataIntegrityViolationException ex) {
            model.addAttribute(
                    "serviceError",
                    "Không thể lưu biến thể. Hãy kiểm tra SKU và dữ liệu bắt buộc."
            );
        }

        addVariantOptions(ownerId, model);

        return "shopowner/product-variant-form";
    }

    private void addVariantOptions(Long ownerId, Model model) {
        model.addAttribute(
                "products",
                products.getListByShop(ownerId)
        );
        model.addAttribute(
                "units",
                units.findByIsActiveTrueOrderByNameAsc()
        );
    }

    private Long ownerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null
                || !"SHOP_OWNER".equals(session.getAttribute("role"))) {
            return null;
        }

        Object id = session.getAttribute("userId");

        return id instanceof Number number
                ? number.longValue()
                : null;
    }
}
