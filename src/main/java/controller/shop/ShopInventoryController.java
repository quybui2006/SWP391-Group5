
package controller.shop;

import dto.shop.InventoryAdjustmentForm;
import dto.shop.InventoryBatchForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.CategoryRepository;
import service.shop.InventoryActionException;
import service.shop.InventoryHistoryExcelExporter;
import service.shop.InventoryService;

import java.time.LocalDateTime;

/**
 * Chịu trách nhiệm tiếp nhận các request liên quan đến:
 * - Xem danh sách tồn kho của các biến thể sản phẩm.
 * - Xem lịch sử nhập, xuất và điều chỉnh kho.
 * - Xuất lịch sử giao dịch kho ra file Excel.
 * - Tạo lô hàng mới khi nhập thêm sản phẩm.
 * - Điều chỉnh số lượng tồn kho của từng lô hàng.
 *
 * Controller chỉ xử lý request, kiểm tra quyền truy cập,
 * validation đầu vào và chuyển dữ liệu giữa View với Service.
 * Các nghiệp vụ xử lý tồn kho được thực hiện trong InventoryService.
 */
@Controller
public class ShopInventoryController {

    // Service xử lý các nghiệp vụ quản lý tồn kho và lô hàng.
    private final InventoryService inventoryService;

    // Thành phần chịu trách nhiệm tạo file Excel từ lịch sử giao dịch kho.
    private final InventoryHistoryExcelExporter excelExporter;

    // Repository dùng để lấy danh sách danh mục đang hoạt động.
    private final CategoryRepository categoryRepository;

    /**
     * Constructor Injection.
     *
     * Spring tự động khởi tạo và truyền các dependency vào Controller.
     */
    public ShopInventoryController(InventoryService inventoryService,
                                   InventoryHistoryExcelExporter excelExporter,
                                   CategoryRepository categoryRepository) {
        this.inventoryService = inventoryService;
        this.excelExporter = excelExporter;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Xuất lịch sử giao dịch tồn kho của Shop Owner ra file Excel.
     *
     * GET /shop/inventory/history/export.xlsx
     * GET /shopowner/inventory/history/export.xlsx
     *
     * Luồng xử lý:
     * 1. Lấy userId từ Session.
     * 2. Kiểm tra người dùng có quyền Shop Owner không.
     * 3. Lấy lịch sử giao dịch kho từ InventoryService.
     * 4. Chuyển dữ liệu sang InventoryHistoryExcelExporter để tạo file Excel.
     * 5. Trả file Excel về trình duyệt để tải xuống.
     */
    @GetMapping({
            "/shop/inventory/history/export.xlsx",
            "/shopowner/inventory/history/export.xlsx"
    })
    public ResponseEntity<byte[]> exportHistory(HttpServletRequest request) {

        // Lấy ID người dùng và kiểm tra quyền Shop Owner.
        Long ownerId = ownerId(request);

        // Nếu chưa đăng nhập hoặc không đúng quyền thì trả về HTTP 401.
        if (ownerId == null) {
            return ResponseEntity.status(401).build();
        }

        // Gọi Service lấy toàn bộ lịch sử giao dịch kho của cửa hàng.
        // Sau đó truyền dữ liệu cho ExcelExporter để tạo nội dung file Excel dạng byte[].
        byte[] workbook = excelExporter.export(
                inventoryService.getTransactionHistory(ownerId)
        );

        // Trả file Excel về trình duyệt.
        return ResponseEntity.ok()

                // Xác định kiểu nội dung là file Excel định dạng .xlsx.
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))

                // Thiết lập tên file khi người dùng tải xuống.
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=shop-inventory-history.xlsx"
                )

                // Gửi dữ liệu file Excel về cho client.
                .body(workbook);
    }

    /**
     * Hiển thị màn hình quản lý tồn kho.
     *
     * GET /shop/inventory
     * GET /shopowner/inventory
     *
     * Hỗ trợ:
     * - Tìm kiếm sản phẩm theo từ khóa.
     * - Lọc sản phẩm theo danh mục.
     * - Hiển thị danh sách tồn kho.
     * - Hiển thị lịch sử giao dịch kho thông qua tab.
     *
     * Tham số:
     * - keyword: Từ khóa tìm kiếm sản phẩm.
     * - categoryId: ID danh mục cần lọc.
     * - tab: Tab muốn hiển thị (inventory hoặc history).
     */
    @GetMapping({"/shop/inventory", "/shopowner/inventory"})
    public String inventoryManagement(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "inventory") String tab,
            Model model) {

        // Lấy ID người dùng từ Session.
        Long ownerId = ownerId(request);

        // Chỉ Shop Owner đã đăng nhập mới được truy cập màn hình quản lý tồn kho.
        if (ownerId == null) {
            return "redirect:/login";
        }

        try {
            // Lấy danh sách sản phẩm và số liệu tồn kho thuộc cửa hàng.
            // Có hỗ trợ tìm kiếm bằng keyword và lọc theo categoryId.
            model.addAttribute(
                    "inventoryProducts",
                    inventoryService.getInventoryProducts(ownerId, keyword, categoryId)
            );

            // Lấy lịch sử nhập, xuất và điều chỉnh tồn kho của cửa hàng.
            model.addAttribute(
                    "transactionRows",
                    inventoryService.getTransactionHistory(ownerId)
            );

        } catch (InventoryActionException ex) {

            // Nếu Service phát hiện lỗi nghiệp vụ thì đưa thông báo lỗi sang View.
            model.addAttribute("error", ex.getMessage());

            // Trả danh sách rỗng để giao diện không bị lỗi khi không lấy được dữ liệu.
            model.addAttribute("inventoryProducts", java.util.List.of());
            model.addAttribute("transactionRows", java.util.List.of());
        }

        // Lấy danh sách danh mục đang hoạt động để hiển thị bộ lọc.
        model.addAttribute(
                "categories",
                categoryRepository.findByIsActiveTrue()
        );

        // Giữ lại từ khóa tìm kiếm sau khi tải lại trang.
        model.addAttribute("keyword", keyword);

        // Giữ lại danh mục người dùng đã chọn.
        model.addAttribute("categoryId", categoryId);

        // Xác định tab đang hoạt động.
        // Nếu tab = history thì hiển thị lịch sử giao dịch.
        // Các giá trị khác sẽ mặc định hiển thị tab inventory.
        model.addAttribute(
                "activeTab",
                "history".equalsIgnoreCase(tab) ? "history" : "inventory"
        );

        // Trả về giao diện quản lý tồn kho của Shop Owner.
        return "shopowner/inventory-management";
    }

    /**
     * Hiển thị biểu mẫu tạo lô hàng mới.
     *
     * GET /shop/inventory/batches/new
     * GET /shopowner/inventory/batches/new
     *
     * Tham số:
     * - inventoryId: ID bản ghi tồn kho của biến thể cần nhập thêm hàng.
     *
     * Luồng xử lý:
     * 1. Kiểm tra quyền Shop Owner.
     * 2. Khởi tạo InventoryBatchForm.
     * 3. Thiết lập tồn kho được chọn nếu có.
     * 4. Đặt số lượng mặc định là 1.
     * 5. Đặt thời điểm nhập mặc định là thời gian hiện tại.
     * 6. Lấy danh sách biến thể để người dùng lựa chọn.
     */
    @GetMapping({
            "/shop/inventory/batches/new",
            "/shopowner/inventory/batches/new"
    })
    public String newBatchForm(
            @RequestParam(required = false) Long inventoryId,
            HttpServletRequest request,
            Model model) {

        // Kiểm tra người dùng có quyền Shop Owner hay không.
        if (ownerId(request) == null) {
            return "redirect:/login";
        }

        // Khởi tạo DTO nhận dữ liệu từ biểu mẫu tạo lô hàng.
        InventoryBatchForm form = new InventoryBatchForm();

        // Gắn ID tồn kho nếu người dùng truy cập từ một biến thể cụ thể.
        form.setInventoryId(inventoryId);

        // Số lượng nhập mặc định là 1.
        form.setQuantity(1);

        // Thiết lập thời điểm nhập mặc định là thời gian hiện tại.
        // Xóa giây và nano giây để phù hợp với dữ liệu nhập từ giao diện.
        form.setReceivedAt(
                LocalDateTime.now().withSecond(0).withNano(0)
        );

        // Đưa DTO sang View để liên kết với các trường nhập liệu.
        model.addAttribute("form", form);

        // Lấy danh sách biến thể thuộc cửa hàng để hiển thị trong dropdown.
        model.addAttribute(
                "variants",
                inventoryService.getVariantOptions(ownerId(request))
        );

        // Trả về giao diện tạo lô hàng mới.
        return "shopowner/inventory-batch-form";
    }

    /**
     * Tiếp nhận dữ liệu và thực hiện nhập thêm một lô hàng mới.
     *
     * POST /shop/inventory/batches
     * POST /shopowner/inventory/batches
     *
     * Luồng xử lý:
     * 1. Kiểm tra quyền Shop Owner.
     * 2. Kiểm tra dữ liệu bằng Bean Validation.
     * 3. Nếu hợp lệ thì gọi InventoryService để nhập lô hàng.
     * 4. Nếu thành công thì chuyển về màn hình quản lý tồn kho.
     * 5. Nếu lỗi thì trả lại biểu mẫu cùng thông báo.
     */
    @PostMapping({
            "/shop/inventory/batches",
            "/shopowner/inventory/batches"
    })
    public String receiveBatch(
            @Valid @ModelAttribute("form") InventoryBatchForm form,
            BindingResult binding,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirect) {

        // Lấy ID người dùng và kiểm tra quyền Shop Owner.
        Long ownerId = ownerId(request);

        // Nếu không có quyền truy cập thì chuyển về trang đăng nhập.
        if (ownerId == null) {
            return "redirect:/login";
        }

        // Kiểm tra các trường dữ liệu được khai báo validation trong InventoryBatchForm.
        if (binding.hasErrors()) {

            // Nếu có lỗi, tải lại danh sách biến thể để biểu mẫu vẫn hoạt động.
            model.addAttribute(
                    "variants",
                    inventoryService.getVariantOptions(ownerId)
            );

            // Hiển thị thông báo lỗi chung.
            model.addAttribute(
                    "error",
                    "Kiểm tra số lượng và thời điểm nhập."
            );

            // Trả lại biểu mẫu để người dùng sửa dữ liệu.
            return "shopowner/inventory-batch-form";
        }

        try {
            // Gọi Service thực hiện nghiệp vụ nhập lô hàng.
            // Service sẽ kiểm tra quyền sở hữu, số lượng và cập nhật dữ liệu kho.
            inventoryService.receiveBatch(form, ownerId);

            // Thông báo thành công, hiển thị sau khi chuyển hướng.
            redirect.addFlashAttribute(
                    "success",
                    "Đã tạo batch mới và ghi lịch sử nhập kho."
            );

            // Chuyển về màn hình quản lý tồn kho.
            return "redirect:/shopowner/inventory";

        } catch (InventoryActionException ex) {

            // Bắt lỗi nghiệp vụ do Service trả về.
            // Ví dụ: dữ liệu không hợp lệ hoặc biến thể không thuộc cửa hàng.
            model.addAttribute(
                    "variants",
                    inventoryService.getVariantOptions(ownerId)
            );

            // Hiển thị nội dung lỗi cụ thể từ Service.
            model.addAttribute("error", ex.getMessage());

            // Giữ lại biểu mẫu và trả người dùng về trang nhập lô hàng.
            return "shopowner/inventory-batch-form";
        }
    }

    /**
     * Hiển thị biểu mẫu điều chỉnh số lượng của một lô hàng.
     *
     * GET /shop/inventory/adjust
     * GET /shopowner/inventory/adjust
     *
     * Tham số:
     * - batchId: ID lô hàng cần điều chỉnh (nếu được truyền vào).
     * - inventoryId: ID tồn kho của biến thể.
     *
     * Nếu có batchId:
     * - Kiểm tra lô hàng có thuộc cửa hàng của người đang đăng nhập không.
     * - Lấy inventoryId tương ứng với lô hàng.
     *
     * Sau đó tải danh sách lô hàng hợp lệ để hiển thị trên giao diện.
     */
    @GetMapping({
            "/shop/inventory/adjust",
            "/shopowner/inventory/adjust"
    })
    public String adjustmentForm(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long inventoryId,
            HttpServletRequest request,
            Model model) {

        // Kiểm tra quyền Shop Owner trước khi hiển thị biểu mẫu.
        if (ownerId(request) == null) {
            return "redirect:/login";
        }

        // Nếu có batchId thì cần xác định lô hàng thuộc bản ghi tồn kho nào.
        if (batchId != null) {

            // Gọi Service kiểm tra lô hàng và lấy inventoryId tương ứng.
            // Service chỉ trả về ID nếu lô hàng thuộc cửa hàng của người dùng.
            inventoryId = inventoryService.getBatchInventoryId(
                    ownerId(request),
                    batchId
            );

            // Nếu không tìm thấy hoặc lô hàng không thuộc cửa hàng,
            // không cho phép tiếp tục điều chỉnh.
            if (inventoryId == null) {

                // Thông báo lỗi trên giao diện.
                model.addAttribute(
                        "error",
                        "Không tìm thấy lô hàng thuộc cửa hàng của bạn."
                );

                // Khởi tạo form rỗng.
                model.addAttribute("form", new InventoryAdjustmentForm());

                // Không hiển thị danh sách lô hàng khi không xác định được tồn kho.
                model.addAttribute("batches", java.util.List.of());

                // Trả về biểu mẫu điều chỉnh tồn kho.
                return "shopowner/inventory-adjust-form";
            }
        }

        // Khởi tạo DTO nhận dữ liệu điều chỉnh tồn kho.
        InventoryAdjustmentForm form = new InventoryAdjustmentForm();

        // Gắn ID lô hàng và ID tồn kho tương ứng vào form.
        form.setBatchId(batchId);
        form.setInventoryId(inventoryId);

        // Hướng điều chỉnh mặc định là DECREASE (giảm số lượng).
        form.setDirection("DECREASE");

        // Số lượng điều chỉnh mặc định là 1.
        form.setQuantity(1);

        // Đưa dữ liệu form sang giao diện.
        model.addAttribute("form", form);

        // Lấy danh sách lô hàng hợp lệ thuộc cửa hàng.
        // Nếu có inventoryId thì danh sách được giới hạn theo tồn kho đó.
        model.addAttribute(
                "batches",
                inventoryService.getBatchOptions(ownerId(request), inventoryId)
        );

        // Hiển thị biểu mẫu điều chỉnh tồn kho.
        return "shopowner/inventory-adjust-form";
    }

    /**
     * Tiếp nhận yêu cầu điều chỉnh số lượng của một lô hàng.
     *
     * POST /shop/inventory/adjust
     * POST /shopowner/inventory/adjust
     *
     * Luồng xử lý:
     * 1. Kiểm tra quyền Shop Owner.
     * 2. Kiểm tra dữ liệu bằng Bean Validation.
     * 3. Xác định lại inventoryId từ batchId để kiểm tra lô hàng.
     * 4. Nếu dữ liệu hợp lệ thì gọi Service điều chỉnh tồn kho.
     * 5. Nếu thành công thì chuyển đến tab lịch sử giao dịch.
     * 6. Nếu lỗi thì tải lại các lô hàng và hiển thị thông báo.
     */
    @PostMapping({
            "/shop/inventory/adjust",
            "/shopowner/inventory/adjust"
    })
    public String adjustBatch(
            @Valid @ModelAttribute("form") InventoryAdjustmentForm form,
            BindingResult binding,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirect) {

        // Lấy ID người dùng và kiểm tra quyền Shop Owner.
        Long ownerId = ownerId(request);

        // Không cho phép người dùng chưa đăng nhập hoặc sai quyền thực hiện điều chỉnh.
        if (ownerId == null) {
            return "redirect:/login";
        }

        // Kiểm tra dữ liệu đầu vào theo các annotation validation trong DTO.
        if (binding.hasErrors()) {

            // Lấy inventoryId từ batchId để xác định lại lô hàng đang được chọn.
            Long inventoryId = inventoryService.getBatchInventoryId(
                    ownerId,
                    form.getBatchId()
            );

            // Gắn lại inventoryId vào form.
            form.setInventoryId(inventoryId);

            // Tải lại danh sách lô hàng.
            // Nếu có batchId nhưng không tìm thấy inventoryId thì trả danh sách rỗng.
            model.addAttribute(
                    "batches",
                    form.getBatchId() != null && inventoryId == null
                            ? java.util.List.of()
                            : inventoryService.getBatchOptions(ownerId, inventoryId)
            );

            // Hiển thị thông báo yêu cầu kiểm tra lại dữ liệu.
            model.addAttribute(
                    "error",
                    "Chọn lô hàng, nhập số lượng giảm và lý do hợp lệ."
            );

            // Trả người dùng về biểu mẫu điều chỉnh tồn kho.
            return "shopowner/inventory-adjust-form";
        }

        try {
            // Gọi Service xử lý nghiệp vụ điều chỉnh số lượng lô hàng.
            // Service sẽ kiểm tra dữ liệu, cập nhật tồn kho và ghi lịch sử giao dịch.
            inventoryService.adjustBatch(form, ownerId);

            // Gửi thông báo thành công sau khi chuyển hướng.
            redirect.addFlashAttribute(
                    "success",
                    "Đã điều chỉnh batch và lưu lịch sử giao dịch."
            );

            // Chuyển đến màn hình quản lý tồn kho, tự động mở tab lịch sử.
            return "redirect:/shopowner/inventory?tab=history";

        } catch (InventoryActionException ex) {

            // Nếu xảy ra lỗi nghiệp vụ, xác định lại tồn kho của lô hàng.
            Long inventoryId = inventoryService.getBatchInventoryId(
                    ownerId,
                    form.getBatchId()
            );

            // Tải lại danh sách lô hàng hợp lệ.
            // Nếu không tìm thấy inventoryId thì hiển thị danh sách rỗng.
            model.addAttribute(
                    "batches",
                    inventoryId == null
                            ? java.util.List.of()
                            : inventoryService.getBatchOptions(ownerId, inventoryId)
            );

            // Hiển thị thông báo lỗi cụ thể từ Service.
            model.addAttribute("error", ex.getMessage());

            // Giữ lại biểu mẫu để người dùng kiểm tra và sửa dữ liệu.
            return "shopowner/inventory-adjust-form";
        }
    }

    /**
     * Lấy ID người dùng từ HTTP Session và xác minh quyền Shop Owner.
     *
     * Điều kiện:
     * - Session phải tồn tại.
     * - Role phải là SHOP_OWNER.
     * - Session phải có userId hợp lệ.
     *
     * Kết quả:
     * - Trả về userId nếu người dùng hợp lệ.
     * - Trả về null nếu chưa đăng nhập hoặc không đúng quyền.
     */
    private Long ownerId(HttpServletRequest request) {

        // Lấy Session hiện tại.
        // false nghĩa là không tạo Session mới nếu chưa tồn tại.
        HttpSession session = request.getSession(false);

        // Kiểm tra Session và vai trò người dùng.
        if (session == null
                || !"SHOP_OWNER".equals(session.getAttribute("role"))) {
            return null;
        }

        // Lấy ID người dùng được lưu trong Session.
        Object value = session.getAttribute("userId");

        // Kiểm tra kiểu dữ liệu và chuyển userId sang Long.
        return value instanceof Number number
                ? number.longValue()
                : null;
    }
}
