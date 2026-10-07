package controller;

import dto.ProductCreateRequest;
import dto.ProductResponseDTO;
import exception.ProductBusinessException;
import repository.CategoryRepository;
import repository.UnitRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import service.ProductService;

@Controller
@RequestMapping("/shop/products")
public class ProductController {

    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;

    public ProductController(
            ProductService productService,
            CategoryRepository categoryRepository,
            UnitRepository unitRepository
    ) {
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
    }

    // =========================================================
    // GET /shop/products
    // =========================================================

    @GetMapping
    public String productList(

            /*
             * shopId sẽ được lấy từ session sau khi Login
             * của teammate hoàn thành.
             */
            @SessionAttribute(
                    name = "shopId",
                    required = false
            )
            Long shopId,

            @RequestParam(
                    name = "keyword",
                    required = false
            )
            String keyword,

            @RequestParam(name = "success", required = false, defaultValue = "false")
            boolean success,

            @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable,

            Model model
    ) {

        Page<ProductResponseDTO> products =
                productService.getProductList(
                        shopId,
                        keyword,
                        pageable
                );

        model.addAttribute(
                "products",
                products
        );

        model.addAttribute(
                "keyword",
                keyword == null ? "" : keyword
        );

        model.addAttribute("success", success);

        return "shopowner/product-list";
    }

    // =========================================================
    // GET /shop/products/add
    // =========================================================

    @GetMapping("/add")
    public String addProductForm(
            Model model
    ) {

        model.addAttribute(
                "productCreateRequest",
                new ProductCreateRequest()
        );

        loadFormOptions(model);

        return "shopowner/product-form";
    }

    // =========================================================
    // POST /shop/products/add
    // =========================================================

    @PostMapping(
            value = "/add",
            consumes = "multipart/form-data"
    )
    public String addProduct(

            @SessionAttribute(
                    name = "shopId",
                    required = false
            )
            Long shopId,

            @SessionAttribute(
                    name = "userId",
                    required = false
            )
            Long userId,

            @Valid
            @ModelAttribute(
                    "productCreateRequest"
            )
            ProductCreateRequest request,

            BindingResult bindingResult,

            Model model
    ) {

        /*
         * Validation lỗi -> quay lại form ngay.
         */
        if (bindingResult.hasErrors()) {
            loadFormOptions(model);
            return "shopowner/product-form";
        }

        try {

            productService.addProduct(
                    shopId,
                    userId,
                    request
            );

            return "redirect:/shop/products?success=true";

        } catch (ProductBusinessException exception) {

            /*
             * request vẫn còn trong Model.
             * Do đó dữ liệu text/number/select vẫn giữ nguyên.
             *
             * File input phải upload lại vì browser không cho
             * server tự điền lại file input.
             */
            model.addAttribute(
                    "serviceError",
                    exception.getMessage()
            );

            loadFormOptions(model);

            return "shopowner/product-form";
        }
    }

    /** Danh mục và đơn vị chỉ được đọc một lần để render select của form. */
    private void loadFormOptions(Model model) {
        model.addAttribute("categories", categoryRepository.findByIsActiveTrueOrderByNameAsc());
        model.addAttribute("units", unitRepository.findByIsActiveTrueOrderByNameAsc());
    }
}
