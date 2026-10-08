package service;

import dto.ProductDetailView;
import dto.ProductEditView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import entity.Product;
import entity.ProductImage;
import entity.ProductVariant;
import entity.VariantInventory;
import repository.ProductImageRepository;
import repository.ProductCategoryLinkRepository;
import repository.ShopProductRepository;
import repository.ShopRepository;
import repository.VariantInventoryRepository;
import repository.CategoryRepository;
import entity.Category;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ màn hình Shop Owner: xem chi tiết và sửa sản phẩm
 * (Sheet2 No.7, No.8).
 *
 * Tách khỏi service.ProductService của Nguyễn Hồng Hà vì bên đó phục vụ
 * góc nhìn khách hàng.
 */
@Service
@Transactional(readOnly = true)
public class ShopProductService {

    /** Ràng buộc trong cột CHECK của bảng products. */
    private static final Set<String> APPROVAL_STATUS =
            Set.of("DRAFT", "PENDING", "APPROVED", "REJECTED");
    private static final Set<String> SELLING_STATUS =
            Set.of("DRAFT", "ACTIVE", "PAUSED", "ARCHIVED");

    /** Cảnh báo khi còn tối đa 3 ngày tới hạn. */
    private static final long EXPIRY_WARNING_DAYS = 3L;

    @Autowired private ShopProductRepository productRepository;
    @Autowired private ProductImageRepository imageRepository;
    @Autowired private VariantInventoryRepository inventoryRepository;
    @Autowired private ProductCategoryLinkRepository categoryLinkRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private CategoryRepository categoryRepository;

    // ======================== ĐỌC DỮ LIỆU ========================

    public ProductDetailView getDetail(Long productId, Long ownerUserId) {
        Product product = loadOwnedProduct(productId, ownerUserId);
        return toDetailView(product);
    }

    public List<ProductDetailView> getListByShop(Long ownerUserId) {
        Long shopId = requireShopId(ownerUserId);
        return productRepository.findByShopId(shopId).stream()
                .map(this::toDetailView)
                .toList();
    }

    public ProductEditView buildEditForm(Long productId, Long ownerUserId) {
        Product product = loadOwnedProduct(productId, ownerUserId);

        ProductEditView form = new ProductEditView();
        form.setId(product.getId());
        form.setName(product.getName());
        form.setOrigin(product.getOrigin());
        form.setDescription(product.getDescription());
        form.setBatchCode(product.getBatchCode());
        form.setApprovalStatus(product.getApprovalStatus());
        form.setSellingStatus(product.getSellingStatus());
        form.setReceivedDate(product.getReceivedDate());
        form.setExpiryDate(product.getExpiryDate());
        form.setShopName(shopRepository.findById(product.getShopId())
                .map(shop -> shop.getName()).orElse("-"));

        // Mỗi sản phẩm giữ đúng một danh mục
        List<Category> all = categoryRepository.findByIsActiveTrue();
        form.setCategories(all.stream()
                .map(c -> new ProductEditView.CategoryOption(c.getId(), c.getName()))
                .toList());
        form.setCategoryId(categoryLinkRepository.findCategoryIds(productId)
                .stream().findFirst().orElse(null));

        return form;
    }

    // ======================== GHI DỮ LIỆU ========================

    @Transactional
    public void update(Long productId, Long ownerUserId, ProductEditView form) {
        Product product = loadOwnedProduct(productId, ownerUserId);

        validate(form);

        String name = form.getName().trim();
        String origin = blankToNull(form.getOrigin());
        String description = blankToNull(form.getDescription());
        String approvalStatus = form.getApprovalStatus().trim().toUpperCase();
        String sellingStatus = form.getSellingStatus().trim().toUpperCase();

        LocalDateTime submittedAt = null;
        LocalDateTime reviewedAt = null;
        // CHECK (approval_status <> 'PENDING' OR submitted_at IS NOT NULL)
        if ("PENDING".equals(approvalStatus)) {
            submittedAt = utcNow();
        }
        // CHECK (approval_status NOT IN ('APPROVED','REJECTED') OR reviewed_at IS NOT NULL)
        if ("APPROVED".equals(approvalStatus) || "REJECTED".equals(approvalStatus)) {
            reviewedAt = utcNow();
        }
        // CHECK (approval_status <> 'REJECTED' OR rejection_reason IS NOT NULL)
        if ("REJECTED".equals(approvalStatus)
                && blankToNull(product.getRejectionReason()) == null) {
            product.setRejectionReason("Không có lý do được cung cấp");
        }

        product.setName(name);
        product.setOrigin(origin);
        product.setDescription(description);
        product.setReceivedDate(form.getReceivedDate());
        product.setExpiryDate(form.getExpiryDate());
        product.setApprovalStatus(approvalStatus);
        product.setSellingStatus(sellingStatus);
        product.setSubmittedAt(submittedAt);
        product.setReviewedAt(reviewedAt);

        productRepository.save(product);

        // Giữ đúng một danh mục cho sản phẩm
        if (form.getCategoryId() != null) {
            categoryLinkRepository.replaceCategory(productId, form.getCategoryId());
        }
    }

    // ======================== KIỂM TRA DỮ LIỆU ========================

    /**
     * Kiểm tra trước khi ghi, bám theo ràng buộc CHECK trong SQL.
     * Kiểm tra bằng Java để trả về thông báo tiếng Việt rõ ràng,
     * thay vì lỗi tiếng Anh từ MySQL.
     */
    private void validate(ProductEditView form) {
        Map<String, String> errors = new LinkedHashMap<>();

        String name = blankToNull(form.getName());
        if (name == null) {
            errors.put("name", "Tên sản phẩm không được để trống");
        } else if (name.length() > 200) {
            errors.put("name", "Tên sản phẩm tối đa 200 ký tự");
        }

        if (form.getOrigin() != null && form.getOrigin().trim().length() > 150) {
            errors.put("origin", "Xuất xứ tối đa 150 ký tự");
        }
        if (form.getDescription() != null && form.getDescription().trim().length() > 2000) {
            errors.put("description", "Mô tả tối đa 2000 ký tự");
        }

        LocalDate received = form.getReceivedDate();
        LocalDate expiry = form.getExpiryDate();
        if (received == null) {
            errors.put("receivedDate", "Ngày nhập kho không được để trống");
        } else if (received.isAfter(LocalDate.now())) {
            errors.put("receivedDate", "Ngày nhập kho không được ở tương lai");
        }
        if (expiry == null) {
            errors.put("expiryDate", "Hạn sử dụng không được để trống");
        }
        // CHECK (expiry_date >= received_date)
        if (received != null && expiry != null && expiry.isBefore(received)) {
            errors.put("expiryDate", "Hạn sử dụng phải từ ngày nhập kho trở đi");
        }

        String approval = upperOrNull(form.getApprovalStatus());
        if (approval == null || !APPROVAL_STATUS.contains(approval)) {
            errors.put("approvalStatus", "Trạng thái duyệt không hợp lệ");
        }
        String selling = upperOrNull(form.getSellingStatus());
        if (selling == null || !SELLING_STATUS.contains(selling)) {
            errors.put("sellingStatus", "Trạng thái bán không hợp lệ");
        }
        // CHECK (selling_status <> 'ACTIVE' OR approval_status = 'APPROVED')
        if ("ACTIVE".equals(selling) && !"APPROVED".equals(approval)) {
            errors.put("sellingStatus", "Chỉ được mở bán sản phẩm đã được duyệt");
        }

        // Danh mục phải tồn tại, nếu không sẽ vi phạm foreign key
        // của bảng product_categories và gây lỗi 500.
        if (form.getCategoryId() != null
                && !categoryRepository.existsById(form.getCategoryId())) {
            errors.put("categoryId", "Danh mục không tồn tại");
        }

        if (!errors.isEmpty()) {
            throw new ProductValidationException(errors);
        }
    }

    // ======================== NỘI BỘ ========================

    /**
     * Lấy sản phẩm và bảo đảm nó thuộc shop của chủ sở hữu.
     * Nếu không kiểm tra bước này, Shop Owner A có thể xem sản phẩm
     * của Shop Owner B chỉ bằng cách đoán id trên URL.
     */
    private Product loadOwnedProduct(Long productId, Long ownerUserId) {
        Long shopId = requireShopId(ownerUserId);
        return productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private Long requireShopId(Long ownerUserId) {
        return shopRepository.findByOwnerId(ownerUserId)
                .map(shop -> shop.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Tài khoản này chưa đăng ký cửa hàng"));
    }

    private ProductDetailView toDetailView(Product product) {
        ProductDetailView view = new ProductDetailView();
        view.setId(product.getId());
        view.setName(product.getName());
        view.setOrigin(product.getOrigin());
        view.setDescription(product.getDescription());
        view.setBatchCode(product.getBatchCode());
        view.setApprovalStatus(product.getApprovalStatus());
        view.setSellingStatus(product.getSellingStatus());
        view.setReceivedDate(product.getReceivedDate());
        view.setExpiryDate(product.getExpiryDate());
        view.setShopName(shopRepository.findById(product.getShopId())
                .map(shop -> shop.getName()).orElse("-"));

        List<String> categories = categoryLinkRepository.findCategoryNames(product.getId());
        view.setCategoryName(categories.isEmpty() ? "Trái cây" : String.join(", ", categories));

        long days = product.getExpiryDate() == null ? 0L
                : ChronoUnit.DAYS.between(LocalDate.now(), product.getExpiryDate());
        view.setDaysUntilExpiry(days);
        view.setExpiringSoon(days >= 0 && days <= EXPIRY_WARNING_DAYS);

        view.setImageUrls(imageRepository
                .findByProductIdOrderBySortOrderAsc(product.getId()).stream()
                .map(ProductImage::getImageUrl)
                .toList());

        List<ProductVariant> variants = product.getVariants() == null
                ? List.of()
                : product.getVariants().stream()
                        .sorted(java.util.Comparator.comparing(ProductVariant::getPrice))
                        .toList();

        Map<Long, VariantInventory> inventories = inventoryRepository
                .findByVariantIdIn(variants.stream().map(ProductVariant::getId).toList())
                .stream()
                .collect(Collectors.toMap(VariantInventory::getVariantId, Function.identity()));

        List<ProductDetailView.VariantRow> rows = variants.stream()
                .map(v -> {
                    VariantInventory inv = inventories.get(v.getId());
                    return new ProductDetailView.VariantRow(
                            v.getId(),
                            v.getSku(),
                            v.getName(),
                            v.getSpecification(),
                            v.getStatus(),
                            v.getPrice(),
                            v.getBaseUnit() == null ? "" : v.getBaseUnit().getName(),
                            inv == null ? 0 : nz(inv.getQuantityOnHand()),
                            inv == null ? 0 : nz(inv.getReservedQuantity()),
                            inv == null ? null : inv.getAvailableQuantity(),
                            inv != null && isLowStock(inv.getAvailableQuantity(),
                                                      inv.getLowStockThresholdPct()));
                })
                .toList();

        view.setVariants(rows);
        view.setPrice(rows.isEmpty() ? null : rows.get(0).price());
        view.setUnitName(rows.isEmpty() ? "" : rows.get(0).unitName());
        return view;
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    /** Cảnh báo tồn kho thấp theo ngưỡng phần trăm lưu trong DB. */
    private boolean isLowStock(Integer available, BigDecimal thresholdPct) {
        if (available == null || thresholdPct == null) {
            return false;
        }
        return available <= thresholdPct.setScale(0, RoundingMode.CEILING).intValue();
    }

    private String upperOrNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Schema lưu mốc thời gian theo UTC. */
    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
