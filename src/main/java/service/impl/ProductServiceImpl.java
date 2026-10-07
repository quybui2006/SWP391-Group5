package service.impl;

import dto.ProductCreateRequest;
import dto.ProductResponseDTO;
import entity.Product;
import entity.ProductVariant;
import exception.ProductBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import repository.ProductRepository;
import repository.ProductVariantRepository;
import service.ProductService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final Path uploadDirectory;

    public ProductServiceImpl(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            @Value("${app.upload.product-dir:uploads/products}") String uploadDirectory
    ) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    /** Luồng danh sách: kiểm tra shop -> tìm kiếm/phân trang -> đổi projection thành DTO. */
    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductList(Long shopId, String keyword, Pageable pageable) {
        requireShopId(shopId);
        String searchKeyword = keyword == null ? "" : keyword.trim();
        return productRepository.findProductList(shopId, searchKeyword, pageable)
                .map(this::toProductResponse);
    }

    /**
     * Luồng thêm sản phẩm:
     * 1. kiểm tra quyền và dữ liệu;
     * 2. lưu ảnh;
     * 3. tạo product, variant mặc định, category, image và tồn kho trong một transaction.
     */
    @Override
    public ProductResponseDTO addProduct(Long shopId, Long userId, ProductCreateRequest request) {
        requireShopId(shopId);
        requireUserId(userId);
        validateRequest(shopId, userId, request);

        String imageUrl = saveImage(request.getImageFile());
        try {
            Product product = productRepository.save(Product.builder()
                    .shopId(shopId)
                    .name(request.getName().trim())
                    .description(blankToNull(request.getDescription()))
                    .origin(blankToNull(request.getOrigin()))
                    .batchCode(request.getBatchCode().trim())
                    .receivedDate(request.getReceivedDate())
                    .expiryDate(request.getExpiryDate())
                    .approvalStatus("DRAFT")
                    .sellingStatus("DRAFT")
                    .build());

            productRepository.insertCategory(product.getId(), request.getCategoryId());
            productRepository.insertVariant(
                    product.getId(), shopId, request.getSku().trim(), request.getVariantName().trim(),
                    imageUrl, request.getUnitId(), request.getPrice());
            productRepository.insertImage(product.getId(), imageUrl);

            Long variantId = productRepository.findVariantId(product.getId(), shopId, request.getSku().trim());
            if (variantId == null) {
                throw new ProductBusinessException("Không thể tạo biến thể sản phẩm.");
            }
            productRepository.insertInventory(
                    variantId, request.getStockQuantity(), request.getLowStockThresholdPct(), userId);

            return ProductResponseDTO.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .sku(request.getSku().trim())
                    .categoryId(request.getCategoryId())
                    .price(request.getPrice())
                    .stockQuantity(request.getStockQuantity())
                    .imageUrl(imageUrl)
                    .status(product.getSellingStatus())
                    .createdAt(product.getCreatedAt())
                    .build();
        } catch (RuntimeException exception) {
            // Database rollback không thể tự xóa file, nên xóa ảnh vừa lưu khi tạo sản phẩm thất bại.
            deleteImage(imageUrl);
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariant> searchAndFilterProducts(
            String keyword, List<Long> categoryIds, String priceRange) {
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        if ("under50".equals(priceRange)) {
            maxPrice = BigDecimal.valueOf(50_000);
        } else if ("50to100".equals(priceRange)) {
            minPrice = BigDecimal.valueOf(50_000);
            maxPrice = BigDecimal.valueOf(100_000);
        } else if ("over100".equals(priceRange)) {
            minPrice = BigDecimal.valueOf(100_000);
        }

        boolean filterByCategory = categoryIds != null && !categoryIds.isEmpty();
        List<Long> safeCategoryIds = filterByCategory ? categoryIds : List.of(-1L);
        return productVariantRepository.searchAndFilter(
                keyword == null ? "" : keyword.trim(), minPrice, maxPrice, filterByCategory, safeCategoryIds);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariant getProductVariantById(Long id) {
        return productVariantRepository.findById(id)
                .filter(variant -> "ACTIVE".equals(variant.getStatus()))
                .filter(variant -> "ACTIVE".equals(variant.getProduct().getSellingStatus()))
                .orElse(null);
    }

    private void validateRequest(Long shopId, Long userId, ProductCreateRequest request) {
        if (productRepository.countOwnedShop(shopId, userId) == 0) {
            throw new ProductBusinessException("Bạn không có quyền thao tác với cửa hàng này.");
        }
        if (productRepository.existsByShopIdAndNameIgnoreCase(shopId, request.getName().trim())) {
            throw new ProductBusinessException("Tên sản phẩm đã tồn tại trong shop.");
        }
        if (productRepository.existsByShopIdAndBatchCodeIgnoreCase(shopId, request.getBatchCode().trim())) {
            throw new ProductBusinessException("Mã lô đã tồn tại trong shop.");
        }
        if (productRepository.countActiveCategory(request.getCategoryId()) == 0) {
            throw new ProductBusinessException("Danh mục không tồn tại hoặc đã bị khóa.");
        }
        if (productRepository.countActiveUnit(request.getUnitId()) == 0) {
            throw new ProductBusinessException("Đơn vị không tồn tại hoặc đã bị khóa.");
        }
        if (request.getExpiryDate().isBefore(request.getReceivedDate())) {
            throw new ProductBusinessException("Ngày hết hạn phải sau hoặc bằng ngày nhập.");
        }
        validateImage(request.getImageFile());
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ProductBusinessException("Vui lòng chọn hình ảnh sản phẩm.");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new ProductBusinessException("Hình ảnh không được lớn hơn 5MB.");
        }
        String extension = extensionOf(image.getOriginalFilename());
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new ProductBusinessException("Chỉ chấp nhận ảnh JPG hoặc PNG.");
        }
    }

    private String saveImage(MultipartFile image) {
        try {
            Files.createDirectories(uploadDirectory);
            String fileName = UUID.randomUUID() + "." + extensionOf(image.getOriginalFilename());
            Files.copy(image.getInputStream(), uploadDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/products/" + fileName;
        } catch (IOException exception) {
            throw new ProductBusinessException("Không thể lưu hình ảnh sản phẩm.", exception);
        }
    }

    private void deleteImage(String imageUrl) {
        try {
            if (imageUrl != null) {
                Files.deleteIfExists(uploadDirectory.resolve(Path.of(imageUrl).getFileName()).normalize());
            }
        } catch (IOException ignored) {
            // Giữ exception nghiệp vụ gốc nếu thao tác dọn file thất bại.
        }
    }

    private ProductResponseDTO toProductResponse(ProductRepository.ProductListProjection product) {
        return ProductResponseDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .categoryId(product.getCategoryId())
                .price(product.getPrice())
                .unit(product.getUnit())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageUrl())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .build();
    }

    private void requireShopId(Long shopId) {
        if (shopId == null) {
            throw new ProductBusinessException("Không xác định được cửa hàng.");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new ProductBusinessException("Không xác định được người dùng hiện tại.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
