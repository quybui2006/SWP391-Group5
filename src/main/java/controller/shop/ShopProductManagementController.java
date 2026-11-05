
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

@Controller
public class ShopProductManagementController {

    // Số lượng sản phẩm hiển thị trên mỗi trang.
    private static final int PAGE_SIZE = 10;

    // Service xử lý nghiệp vụ quản lý sản phẩm của Shop Owner.
    private final ShopProductService products;

    // Repository dùng để lấy danh sách danh mục đang hoạt động.
    private final CategoryRepository categoryRepository;

    // Constructor Injection: Spring tự động truyền các dependency cần thiết.
    public ShopProductManagementController(ShopProductService products,
                                           CategoryRepository categoryRepository) {
        this.products = products;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Hiển thị danh sách sản phẩm của Shop Owner.
     *
     * Hỗ trợ:
     * - Tìm kiếm sản phẩm theo từ khóa.
     * - Lọc sản phẩm theo danh mục.
     * - Phân trang danh sách sản phẩm.
     *
     * GET /shop/products
     * GET /shopowner/products
     */
    @GetMapping({"/shop/products", "/shopowner/products"})
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(defaultValue = "0") int page,
                       HttpServletRequest request, Model model) {

        // Lấy ID người dùng từ Session và kiểm tra quyền Shop Owner.
        Long ownerId = ownerId(request);

        // Nếu chưa đăng nhập hoặc không phải Shop Owner thì chuyển đến trang đăng nhập.
        if (ownerId == null) return "redirect:/login";

        // Gọi Service để tìm kiếm sản phẩm theo từ khóa, danh mục và số trang.
        // Math.max(0, page) đảm bảo số trang không nhỏ hơn 0.
        Page<?> result = products.searchShopProducts(
                ownerId, keyword, categoryId, Math.max(0, page), PAGE_SIZE);

        // Đưa danh sách sản phẩm có phân trang sang giao diện.
        model.addAttribute("products", result);

        // Giữ lại từ khóa tìm kiếm sau khi tải lại trang.
        model.addAttribute("keyword", keyword.trim());

        // Lấy danh sách danh mục đang hoạt động để hiển thị bộ lọc.
        model.addAttribute("categories", categoryRepository.findByIsActiveTrue());

        // Giữ lại danh mục mà người dùng đang chọn.
        model.addAttribute("selectedCategoryId", categoryId);

        // Trả về giao diện danh sách sản phẩm.
        return "shopowner/product-list";
    }

    /**
     * Hiển thị biểu mẫu thêm sản phẩm mới.
     *
     * - Kiểm tra người dùng có quyền Shop Owner.
     * - Khởi tạo đối tượng ProductCreateRequest để nhận dữ liệu từ form.
     * - Lấy danh sách danh mục đang hoạt động.
     *
     * GET /shop/products/add
     * GET /shopowner/products/add
     */
    @GetMapping({"/shop/products/add", "/shopowner/products/add"})
    public String addForm(HttpServletRequest request, Model model) {

        // Kiểm tra quyền truy cập trước khi hiển thị biểu mẫu.
        if (ownerId(request) == null) return "redirect:/login";

        // Tạo DTO rỗng để liên kết với các trường nhập liệu trên Thymeleaf.
        ProductCreateRequest form = new ProductCreateRequest();
        model.addAttribute("productCreateRequest", form);

        // Đưa danh sách danh mục đang hoạt động sang giao diện.
        populateActiveCategories(model);

        // Trả về trang thêm sản phẩm.
        return "shopowner/product-form";
    }

    /**
     * Tiếp nhận dữ liệu từ biểu mẫu và tạo sản phẩm mới.
     *
     * Quy trình:
     * 1. Kiểm tra quyền Shop Owner.
     * 2. Kiểm tra dữ liệu bằng Bean Validation.
     * 3. Kiểm tra ảnh sản phẩm bắt buộc.
     * 4. Nếu hợp lệ thì gọi Service tạo sản phẩm.
     * 5. Nếu xảy ra lỗi thì trả lại form kèm thông báo.
     *
     * POST /shop/products/add
     * POST /shopowner/products/add
     */
    @PostMapping({"/shop/products/add", "/shopowner/products/add"})
    public String create(@Valid @ModelAttribute("productCreateRequest") ProductCreateRequest form,
                         BindingResult binding, HttpServletRequest request, Model model,
                         RedirectAttributes redirect) {

        // Lấy ID người dùng và kiểm tra quyền Shop Owner.
        Long ownerId = ownerId(request);

        // Không cho phép người dùng chưa đăng nhập thực hiện tạo sản phẩm.
        if (ownerId == null) return "redirect:/login";

        // Kiểm tra riêng ảnh sản phẩm vì đây là trường bắt buộc.
        // Nếu không chọn ảnh hoặc file rỗng thì thêm lỗi vào BindingResult.
        if (form.getImageFile() == null || form.getImageFile().isEmpty()) {
            binding.rejectValue(
                    "imageFile",
                    "imageFile.required",
                    "Vui lòng chọn ảnh sản phẩm."
            );
        }

        // Kiểm tra kết quả validation.
        // Nếu có lỗi thì tải lại danh mục và trả về form để người dùng sửa.
        if (binding.hasErrors()) {
            populateActiveCategories(model);
            return "shopowner/product-form";
        }

        try {
            // Gọi Service xử lý nghiệp vụ tạo sản phẩm và lưu xuống Database.
            Long productId = products.createProduct(ownerId, form);

            // Gửi thông báo thành công sang trang danh sách sau khi chuyển hướng.
            redirect.addFlashAttribute(
                    "success",
                    "Đã tạo sản phẩm ở trạng thái chưa đăng bán. Hãy thêm biến thể, sau đó đăng bán sản phẩm."
            );

            // Tạo sản phẩm thành công thì quay về trang danh sách sản phẩm.
            return "redirect:/shop/products";

        } catch (ProductValidationException ex) {

            // Nhận các lỗi validation do Service trả về.
            // Gắn từng lỗi vào đúng trường tương ứng trên biểu mẫu.
            ex.getFieldErrors().forEach((field, message) -> {
                if (field != null && !field.isBlank()
                        && message != null && !message.isBlank()) {

                    binding.rejectValue(field, null, message);
                }
            });

        } catch (DataIntegrityViolationException ex) {

            // Bắt lỗi vi phạm ràng buộc Database.
            // Ví dụ: mã lô bị trùng, khóa ngoại không hợp lệ hoặc thiếu dữ liệu bắt buộc.
            model.addAttribute(
                    "serviceError",
                    "Không thể lưu sản phẩm. Hãy kiểm tra mã lô, danh mục và dữ liệu bắt buộc."
            );
        }

        // Khi xảy ra lỗi, tải lại danh mục để form vẫn hiển thị đầy đủ dữ liệu.
        populateActiveCategories(model);

        // Trả người dùng về form để kiểm tra và sửa dữ liệu.
        return "shopowner/product-form";
    }

    /**
     * Lấy danh sách danh mục đang hoạt động từ Database
     * và đưa vào Model để hiển thị trên giao diện.
     *
     * Được sử dụng khi hiển thị form thêm sản phẩm
     * hoặc khi form có lỗi validation cần tải lại.
     */
    private void populateActiveCategories(Model model) {
        model.addAttribute(
                "categories",
                categoryRepository.findByIsActiveTrue()
        );
    }

    /**
     * Lấy ID của Shop Owner từ HTTP Session.
     *
     * Điều kiện:
     * - Session phải tồn tại.
     * - Role phải là SHOP_OWNER.
     * - Session phải chứa userId hợp lệ.
     *
     * Trả về:
     * - userId nếu người dùng hợp lệ.
     * - null nếu chưa đăng nhập hoặc không đúng quyền.
     */
    private Long ownerId(HttpServletRequest request) {

        // Lấy Session hiện tại.
        // false nghĩa là không tự tạo Session mới nếu chưa tồn tại.
        HttpSession session = request.getSession(false);

        // Kiểm tra Session và quyền người dùng.
        if (session == null
                || !"SHOP_OWNER".equals(session.getAttribute("role"))) {
            return null;
        }

        // Lấy ID người dùng đã lưu trong Session.
        Object value = session.getAttribute("userId");

        // Chỉ chấp nhận giá trị có kiểu Number, sau đó chuyển thành Long.
        return value instanceof Number number
                ? number.longValue()
                : null;
    }
}
