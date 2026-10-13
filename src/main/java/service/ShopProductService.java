package service;

import dto.ProductDetailView;
import dto.ProductEditView;
import dto.ProductCreateRequest;
import dto.ProductVariantCreateRequest;
import dto.ProductVariantEditForm;
import dto.ProductVariantDetailView;
import dto.ShopProductListItem;
import entity.Unit;
import entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;
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
import repository.ProductVariantRepository;
import repository.UnitRepository;
import entity.Category;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
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

    private static final Set<String> SELLING_STATUS =
            Set.of("DRAFT", "ACTIVE", "PAUSED", "ARCHIVED");

    /** Cảnh báo khi còn tối đa 3 ngày tới hạn. */
    private static final long EXPIRY_WARNING_DAYS = 3L;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private ShopProductRepository productRepository;
    @Autowired private ProductImageRepository imageRepository;
    @Autowired private VariantInventoryRepository inventoryRepository;
    @Autowired private ProductCategoryLinkRepository categoryLinkRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private UnitRepository unitRepository;
    @Autowired private ProductImageStorage imageStorage;
    @Autowired private JdbcTemplate jdbc;

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

    public Page<ShopProductListItem> searchShopProducts(Long ownerUserId, String keyword, int page, int size) {
        Long shopId = requireShopId(ownerUserId);
        String term = keyword == null ? "" : keyword.trim();
        String like = "%" + term.toLowerCase() + "%";
        String condition = "p.shop_id=? AND (?='' OR LOWER(p.name) LIKE ? OR EXISTS "
                + "(SELECT 1 FROM product_variants pv WHERE pv.product_id=p.id AND LOWER(pv.sku) LIKE ?))";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM products p WHERE " + condition,
                Long.class, shopId, term, like, like);
        List<ShopProductListItem> rows = jdbc.query("""
                SELECT p.id, p.name, p.selling_status AS status, p.approval_status, p.rejection_reason, p.created_at,
                    (SELECT pi.image_url FROM product_images pi WHERE pi.product_id=p.id ORDER BY pi.sort_order LIMIT 1) AS image_url,
                     (SELECT COUNT(*) FROM product_variants pv WHERE pv.product_id=p.id) AS variant_count,
                     (SELECT COALESCE(SUM(vi.quantity_on_hand), 0) FROM product_variants pv LEFT JOIN variant_inventory vi ON vi.variant_id=pv.id
                         WHERE pv.product_id=p.id) AS stock_quantity,
                    (SELECT c.name FROM product_categories pc JOIN categories c ON c.id=pc.category_id
                        WHERE pc.product_id=p.id ORDER BY c.name LIMIT 1) AS category_name
                FROM products p WHERE %s
                ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?
                """.formatted(condition), (rs, row) -> new ShopProductListItem(
                rs.getLong("id"), rs.getString("name"), rs.getString("image_url"),
                ((Number) rs.getObject("variant_count")).intValue(), rs.getString("category_name"),
                ((Number) rs.getObject("stock_quantity")).intValue(), rs.getString("status"),
                rs.getString("approval_status"), rs.getString("rejection_reason"),
                rs.getTimestamp("created_at").toLocalDateTime()),
                shopId, term, like, like, size, (long) page * size);
        return new PageImpl<>(rows, PageRequest.of(page, size), total == null ? 0 : total);
    }

    public List<ProductVariantEditForm.UnitOption> activeUnitOptions() {
        return unitRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(unit -> new ProductVariantEditForm.UnitOption(unit.getId(), unit.getName())).toList();
    }

    public ProductVariantEditForm buildVariantEditForm(Long variantId, Long ownerUserId) {
        ProductVariant variant = loadOwnedVariant(variantId, ownerUserId);
        VariantInventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy tồn kho biến thể."));
        ProductVariantEditForm form = new ProductVariantEditForm();
        form.setId(variant.getId()); form.setProductName(variant.getProduct().getName());
        form.setProductApproved("APPROVED".equals(variant.getProduct().getApprovalStatus()));
        form.setVariantName(variant.getName()); form.setSku(variant.getSku()); form.setSpecification(variant.getSpecification());
        // UI không được hiển thị ACTIVE / INACTIVE nếu sản phẩm cha chưa được duyệt.
        form.setStatus(form.isProductApproved() ? variant.getStatus() : "DRAFT");
        form.setUnitId(variant.getBaseUnit().getId()); form.setPrice(variant.getPrice());
        form.setQuantityOnHand(inventory.getQuantityOnHand()); form.setLowStockThresholdPct(inventory.getLowStockThresholdPct());
        form.setUnits(activeUnitOptions());
        return form;
    }

    public ProductVariantDetailView getVariantDetail(Long variantId, Long ownerUserId) {
        ProductVariant variant = loadOwnedVariant(variantId, ownerUserId);
        VariantInventory inventory = inventoryRepository.findByVariantId(variantId).orElse(null);
        return new ProductVariantDetailView(variant.getId(), variant.getProduct().getName(), variant.getName(),
                variant.getSku(), variant.getSpecification(), variant.getPrice(), variant.getBaseUnit().getName(),
                variant.getStatus(), inventory == null ? 0 : inventory.getQuantityOnHand(),
                inventory == null ? 0 : inventory.getReservedQuantity(), inventory == null ? 0 : inventory.getAvailableQuantity(),
                inventory == null ? null : inventory.getLowStockThresholdPct());
    }

    @Transactional
    public void updateVariant(Long variantId, Long ownerUserId, ProductVariantEditForm form) {
        ProductVariant variant = loadOwnedVariant(variantId, ownerUserId);
        VariantInventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy tồn kho biến thể."));
        Map<String, String> errors = new LinkedHashMap<>();
        String name = blankToNull(form.getVariantName()); String sku = blankToNull(form.getSku());
        if (name == null || name.length() > 150) errors.put("variantName", "Tên biến thể phải có từ 1 đến 150 ký tự.");
        if (sku == null || sku.length() > 80) errors.put("sku", "SKU phải có từ 1 đến 80 ký tự.");
        if (sku != null && !sku.equals(variant.getSku()) && variantRepository.existsByShopIdAndSku(variant.getShopId(), sku)) errors.put("sku", "SKU này đã được dùng trong cửa hàng.");
        if (form.getPrice() == null || form.getPrice().signum() <= 0) errors.put("price", "Giá bán phải lớn hơn 0.");
        if (form.getQuantityOnHand() == null || form.getQuantityOnHand() < inventory.getReservedQuantity()) errors.put("quantityOnHand", "Tồn kho không được thấp hơn số lượng đang giữ cho đơn hàng.");
        if (form.getLowStockThresholdPct() == null || form.getLowStockThresholdPct().signum() < 0 || form.getLowStockThresholdPct().compareTo(new BigDecimal("100")) > 0) errors.put("lowStockThresholdPct", "Ngưỡng cảnh báo phải từ 0 đến 100.");
        String requestedStatus = upperOrNull(form.getStatus());
        if (requestedStatus == null || !Set.of("DRAFT", "ACTIVE", "INACTIVE").contains(requestedStatus)) {
            errors.put("status", "Trạng thái biến thể không hợp lệ.");
        } else if (!"APPROVED".equals(variant.getProduct().getApprovalStatus())
                && !"DRAFT".equals(requestedStatus)) {
            errors.put("status", "Sản phẩm cần được Quản lý vận hành duyệt trước khi bán.");
        }
        Unit unit = form.getUnitId() == null ? null : unitRepository.findById(form.getUnitId()).filter(u -> Boolean.TRUE.equals(u.getIsActive())).orElse(null);
        if (unit == null) errors.put("unitId", "Đơn vị không tồn tại hoặc đã ngừng hoạt động.");
        if (!errors.isEmpty()) throw new ProductValidationException(errors);
        variant.setName(name); variant.setSku(sku); variant.setSpecification(blankToNull(form.getSpecification()));
        variant.setPrice(form.getPrice()); variant.setStatus(requestedStatus); variant.setBaseUnit(unit);
        inventory.setQuantityOnHand(form.getQuantityOnHand()); inventory.setLowStockThresholdPct(form.getLowStockThresholdPct());
        variantRepository.save(variant); inventoryRepository.save(inventory);
    }

    // Tan PTH integration: expose the same persisted variant/inventory rows used by product detail.
    public List<Map<String, Object>> searchShopVariants(Long ownerUserId, String keyword) {
        Long shopId = requireShopId(ownerUserId);
        String term = keyword == null ? "" : keyword.trim();
        String like = "%" + term.toLowerCase() + "%";
        return jdbc.queryForList("""
                SELECT pv.id AS variant_id, p.id AS product_id, p.name AS product_name,
                    pv.name AS variant_name, pv.sku, pv.specification, pv.price,
                    u.name AS unit_name, COALESCE(vi.quantity_on_hand, 0) AS stock_quantity,
                    COALESCE(vi.available_quantity, 0) AS available_quantity,
                    pv.status AS variant_status, p.selling_status AS product_status
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.shop_id = pv.shop_id
                JOIN units u ON u.id = pv.base_unit_id
                LEFT JOIN variant_inventory vi ON vi.variant_id = pv.id
                WHERE pv.shop_id = ?
                  AND (? = '' OR LOWER(p.name) LIKE ? OR LOWER(pv.name) LIKE ? OR LOWER(pv.sku) LIKE ?)
                ORDER BY p.name, pv.name, pv.id
                """, shopId, term, like, like, like);
    }

    public Map<String, Long> shopVariantSummary(Long ownerUserId) {
        Long shopId = requireShopId(ownerUserId);
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                    COALESCE(SUM(CASE WHEN pv.status = 'ACTIVE' AND p.selling_status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active,
                    COALESCE(SUM(CASE WHEN pv.status = 'DRAFT' THEN 1 ELSE 0 END), 0) AS draft,
                    COALESCE(SUM(CASE WHEN pv.status IN ('PAUSED', 'ARCHIVED') OR p.selling_status IN ('PAUSED', 'ARCHIVED') THEN 1 ELSE 0 END), 0) AS paused
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.shop_id = pv.shop_id
                WHERE pv.shop_id = ?
                """, shopId).entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> ((Number) entry.getValue()).longValue()));
    }

    public Map<String, Object> shopDashboard(Long ownerUserId) {
        Shop shop = shopRepository.findByOwnerId(ownerUserId).orElse(null);
        if (shop == null) return Map.of("name", "Chưa đăng ký cửa hàng", "approvalStatus", "NOT_REGISTERED", "operatingStatus", "CLOSED");
        return Map.of("name", shop.getName(), "approvalStatus", shop.getApprovalStatus(), "operatingStatus", shop.getOperatingStatus());
    }

    public Map<String, Long> shopDashboardStats(Long ownerUserId) {
        if (shopRepository.findByOwnerId(ownerUserId).isEmpty()) return Map.of("products", 0L, "variants", 0L, "active_variants", 0L, "low_stock", 0L);
        Long shopId = requireShopId(ownerUserId);
        return jdbc.queryForMap("""
                SELECT COUNT(DISTINCT p.id) AS products,
                    COUNT(pv.id) AS variants,
                    COALESCE(SUM(CASE WHEN pv.status='ACTIVE' AND p.selling_status='ACTIVE' THEN 1 ELSE 0 END),0) AS active_variants,
                    COALESCE(SUM(CASE WHEN vi.quantity_on_hand - vi.reserved_quantity <= vi.initial_quantity * vi.low_stock_threshold_pct / 100 THEN 1 ELSE 0 END),0) AS low_stock
                FROM products p LEFT JOIN product_variants pv ON pv.product_id=p.id
                    LEFT JOIN variant_inventory vi ON vi.variant_id=pv.id
                WHERE p.shop_id=?
                """, shopId).entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> ((Number) entry.getValue()).longValue()));
    }

    public List<Map<String, Object>> recentShopVariants(Long ownerUserId) {
        if (shopRepository.findByOwnerId(ownerUserId).isEmpty()) return List.of();
        Long shopId = requireShopId(ownerUserId);
        return jdbc.queryForList("""
                SELECT pv.id AS variant_id, p.id AS product_id, p.name AS product_name,
                       pv.name AS variant_name, pv.sku, pv.price,
                       COALESCE(vi.quantity_on_hand,0) AS stock_quantity,
                       pv.status AS variant_status, p.selling_status AS product_status
                FROM product_variants pv JOIN products p ON p.id=pv.product_id
                LEFT JOIN variant_inventory vi ON vi.variant_id=pv.id
                WHERE pv.shop_id=? ORDER BY pv.id DESC LIMIT 5
                """, shopId);
    }

    @Transactional
    public Long createProduct(Long ownerUserId, ProductCreateRequest form) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (form.getReceivedDate() != null && form.getExpiryDate() != null
                && !form.getExpiryDate().isAfter(form.getReceivedDate())) {
            errors.put("expiryDate", "Ngày hết hạn phải sau ngày nhập kho.");
        }
        if (!errors.isEmpty()) throw new ProductValidationException(errors);

        Shop shop = shopRepository.findByOwnerId(ownerUserId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Tài khoản chưa có cửa hàng."));

        String batchCode = form.getBatchCode().trim();
        if (productRepository.existsByShopIdAndBatchCode(shop.getId(), batchCode)) {
            throw new ProductValidationException("batchCode", "Mã lô này đã được dùng trong cửa hàng.");
        }

        if (form.getImageFile() == null || form.getImageFile().isEmpty()) {
            throw new ProductValidationException("imageFile", "Vui lòng chọn ảnh sản phẩm.");
        }

        // Ảnh chính thuộc sản phẩm, không phụ thuộc việc đã tạo biến thể hay chưa.
        String imageUrl = imageStorage.store(form.getImageFile());
        try {
            Product product = new Product();
            product.setShopId(shop.getId());
            product.setName(form.getName().trim());
            product.setDescription(blankToNull(form.getDescription()));
            product.setOrigin(blankToNull(form.getOrigin()));
            product.setBatchCode(batchCode);
            product.setReceivedDate(form.getReceivedDate());
            product.setExpiryDate(form.getExpiryDate());
            // Sản phẩm mới luôn phải qua luồng DRAFT -> PENDING -> APPROVED.
            product.setApprovalStatus("DRAFT");
            product.setSellingStatus("DRAFT");
            product.setSubmittedAt(null);
            product.setReviewedAt(null);
            product.setRejectionReason(null);
            product = productRepository.saveAndFlush(product);

            ProductImage image = new ProductImage();
            image.setProductId(product.getId());
            image.setImageUrl(imageUrl);
            image.setSortOrder(0);
            imageRepository.saveAndFlush(image);
            return product.getId();
        } catch (RuntimeException ex) {
            imageStorage.delete(imageUrl);
            throw ex;
        }
    }

    @Transactional
    public void createVariantInventory(Long ownerUserId, ProductVariantCreateRequest form) {
        Product product = loadOwnedProduct(form.getProductId(), ownerUserId);
        Unit unit = unitRepository.findById(form.getUnitId())
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> new ProductValidationException("unitId", "Đơn vị không tồn tại hoặc đã ngừng hoạt động."));

        String sku = form.getSku().trim();
        if (variantRepository.existsByShopIdAndSku(product.getShopId(), sku)) {
            throw new ProductValidationException("sku", "SKU này đã được dùng trong cửa hàng.");
        }
        if (form.getImageFile() == null || form.getImageFile().isEmpty()) {
            throw new ProductValidationException("imageFile", "Vui lòng chọn ảnh sản phẩm.");
        }

        String imageUrl = imageStorage.store(form.getImageFile());
        try {
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setShopId(product.getShopId());
            variant.setSku(sku);
            variant.setName(form.getVariantName().trim());
            variant.setBaseUnit(unit);
            variant.setPrice(form.getPrice());
            variant.setStatus("DRAFT");
            variant.setImageUrl(imageUrl);
            variant.setMinOrderQuantity(1);
            variant = variantRepository.saveAndFlush(variant);

            VariantInventory inventory = new VariantInventory();
            inventory.setVariantId(variant.getId());
            inventory.setInitialQuantity(form.getStockQuantity());
            inventory.setQuantityOnHand(form.getStockQuantity());
            inventory.setReservedQuantity(0);
            inventory.setLowStockThresholdPct(form.getLowStockThresholdPct());
            inventory.setStatus("ACTIVE");
            inventory.setCreatedBy(ownerUserId);
            inventoryRepository.save(inventory);

            if (imageRepository.findByProductIdOrderBySortOrderAsc(product.getId()).isEmpty()) {
                ProductImage image = new ProductImage();
                image.setProductId(product.getId());
                image.setImageUrl(imageUrl);
                image.setSortOrder(0);
                imageRepository.save(image);
            }
        } catch (RuntimeException ex) {
            imageStorage.delete(imageUrl);
            throw ex;
        }
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

        if ("PENDING".equals(product.getApprovalStatus())) {
            throw new ProductValidationException("name",
                    "Sản phẩm đang chờ Quản lý vận hành duyệt nên chưa thể chỉnh sửa.");
        }

        validate(form);

        String name = form.getName().trim();
        String origin = blankToNull(form.getOrigin());
        String description = blankToNull(form.getDescription());
        String sellingStatus = upperOrNull(form.getSellingStatus());
        String currentApprovalStatus = product.getApprovalStatus();

        if ("ACTIVE".equals(sellingStatus) && !"APPROVED".equals(currentApprovalStatus)) {
            throw new ProductValidationException("sellingStatus",
                    "Sản phẩm cần được Quản lý vận hành duyệt trước khi chuyển sang Đang bán.");
        }

        product.setName(name);
        product.setOrigin(origin);
        product.setDescription(description);
        product.setReceivedDate(form.getReceivedDate());
        product.setExpiryDate(form.getExpiryDate());
        product.setSellingStatus(sellingStatus);

        // Sửa yêu cầu đã bị từ chối sẽ đưa nó về DRAFT để chủ shop chỉnh sửa rồi gửi duyệt lại.
        // Tuyệt đối không tự nâng approval_status lên APPROVED khi Shop Owner bấm Lưu.
        if ("REJECTED".equals(currentApprovalStatus)) {
            product.setApprovalStatus("DRAFT");
            product.setSellingStatus("DRAFT");
            product.setSubmittedAt(null);
            product.setReviewedAt(null);
            product.setRejectionReason(null);
        }

        productRepository.save(product);

        // Giữ đúng một danh mục cho sản phẩm
        if (form.getCategoryId() != null) {
            categoryLinkRepository.replaceCategory(productId, form.getCategoryId());
        }
    }

    /** Shop Owner chỉ được gửi sản phẩm DRAFT sang Operations Manager duyệt. */
    @Transactional
    public void submitForApproval(Long productId, Long ownerUserId) {
        Product product = loadOwnedProduct(productId, ownerUserId);
        if (!"DRAFT".equals(product.getApprovalStatus())) {
            throw new ProductValidationException("approvalStatus",
                    "Chỉ sản phẩm ở trạng thái Bản nháp mới có thể gửi duyệt.");
        }
        int changed = productRepository.submitDraftForApproval(
                productId, product.getShopId(), LocalDateTime.now(BUSINESS_ZONE));
        if (changed != 1) {
            throw new ProductValidationException("approvalStatus",
                    "Trạng thái sản phẩm đã thay đổi. Vui lòng tải lại danh sách và thử lại.");
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
        // Quy tắc nghiệp vụ: expiry_date phải lớn hơn received_date
        if (received != null && expiry != null && !expiry.isAfter(received)) {
            errors.put("expiryDate", "Ngày hết hạn phải sau ngày nhập kho");
        }

        String selling = upperOrNull(form.getSellingStatus());
        if (selling == null || !SELLING_STATUS.contains(selling)) {
            errors.put("sellingStatus", "Trạng thái bán không hợp lệ");
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

    private ProductVariant loadOwnedVariant(Long variantId, Long ownerUserId) {
        Long shopId = requireShopId(ownerUserId);
        return variantRepository.findById(variantId)
                .filter(variant -> shopId.equals(variant.getShopId()))
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy biến thể."));
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
