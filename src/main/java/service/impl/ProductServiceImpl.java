package service.impl;

import dto.ProductCreateRequest;
import dto.ProductResponseDTO;
import entity.Product;
import exception.ProductBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import repository.ProductRepository;
import service.ProductService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final long MAX_FILE_SIZE =
            5L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "png");

    private final ProductRepository productRepository;

    private final Path uploadDirectory;

    public ProductServiceImpl(
            ProductRepository productRepository,
            @Value("${app.upload.product-dir:uploads/products}")
            String uploadDirectory
    ) {
        this.productRepository = productRepository;

        this.uploadDirectory = Paths
                .get(uploadDirectory)
                .toAbsolutePath()
                .normalize();
    }

    // =========================================================
    // PRODUCT LIST
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductList(
            Long shopId,
            String keyword,
            Pageable pageable
    ) {

        validateShopId(shopId);

        String searchKeyword =
                keyword == null
                        ? ""
                        : keyword.trim();

        Page<ProductRepository.ProductListProjection> result =
                productRepository.findProductList(
                        shopId,
                        searchKeyword,
                        pageable
                );

        return result.map(this::toResponseDTO);
    }

    // =========================================================
    // ADD PRODUCT
    // =========================================================

    @Override
    public ProductResponseDTO addProduct(
            Long shopId,
            Long userId,
            ProductCreateRequest request
    ) {

        validateShopId(shopId);

        if (userId == null) {
            throw new ProductBusinessException(
                    "Không xác định được người dùng hiện tại."
            );
        }

        // ---------------------------------------------------------
        // 1. Check Shop
        // ---------------------------------------------------------

        if (productRepository.countOwnedShop(
                shopId,
                userId
        ) == 0) {

            throw new ProductBusinessException(
                    "Bạn không có quyền thao tác với cửa hàng này."
            );
        }

        // ---------------------------------------------------------
        // 2. Normalize input
        // ---------------------------------------------------------

        String productName =
                request.getName().trim();

        String batchCode =
                request.getBatchCode().trim();

        String sku =
                request.getSku().trim();

        // ---------------------------------------------------------
        // 3. Duplicate check
        // ---------------------------------------------------------

        if (productRepository
                .existsByShopIdAndNameIgnoreCase(
                        shopId,
                        productName
                )) {

            throw new ProductBusinessException(
                    "Tên sản phẩm đã tồn tại trong shop."
            );
        }

        if (productRepository
                .existsByShopIdAndBatchCodeIgnoreCase(
                        shopId,
                        batchCode
                )) {

            throw new ProductBusinessException(
                    "Mã lô đã tồn tại trong shop."
            );
        }

        // ---------------------------------------------------------
        // 4. Check category
        // ---------------------------------------------------------

        if (productRepository.countActiveCategory(
                request.getCategoryId()
        ) == 0) {

            throw new ProductBusinessException(
                    "Danh mục không tồn tại hoặc đã bị khóa."
            );
        }

        // ---------------------------------------------------------
        // 5. Check unit
        // ---------------------------------------------------------

        if (productRepository.countActiveUnit(
                request.getUnitId()
        ) == 0) {

            throw new ProductBusinessException(
                    "Đơn vị không tồn tại hoặc đã bị khóa."
            );
        }

        // ---------------------------------------------------------
        // 6. Business validation
        // ---------------------------------------------------------

        validatePrice(request.getPrice());

        validateDates(
                request.getReceivedDate(),
                request.getExpiryDate()
        );

        validateImage(
                request.getImageFile()
        );

        // ---------------------------------------------------------
        // 7. Save image
        // ---------------------------------------------------------

        String imageUrl =
                saveImage(request.getImageFile());

        try {

            // -----------------------------------------------------
            // 8. Create Product
            // -----------------------------------------------------

            Product product = Product.builder()
                    .shopId(shopId)
                    .name(productName)
                    .description(
                            normalize(
                                    request.getDescription()
                            )
                    )
                    .origin(
                            normalize(
                                    request.getOrigin()
                            )
                    )
                    .batchCode(batchCode)
                    .receivedDate(
                            request.getReceivedDate()
                    )
                    .expiryDate(
                            request.getExpiryDate()
                    )

                    /*
                     * Product mới chưa được duyệt.
                     */
                    .approvalStatus("DRAFT")

                    /*
                     * Chưa được bán.
                     */
                    .sellingStatus("DRAFT")

                    .build();

            Product savedProduct =
                    productRepository.save(product);

            // -----------------------------------------------------
            // 9. Category
            // -----------------------------------------------------

            productRepository.insertCategory(
                    savedProduct.getId(),
                    request.getCategoryId()
            );

            // -----------------------------------------------------
            // 10. Initial Variant
            // -----------------------------------------------------

            /*
             * Product Variant là task riêng của teammate.
             *
             * Ở đây chỉ tạo đúng 1 variant mặc định để Product
             * có thể có SKU + price theo thiết kế DB.
             *
             * Không tạo ProductVariant Entity.
             */
            productRepository.insertVariant(
                    savedProduct.getId(),
                    shopId,
                    sku,
                    request.getVariantName().trim(),
                    imageUrl,
                    request.getUnitId(),
                    request.getPrice()
            );

            Long variantId =
                    productRepository.findVariantId(
                            savedProduct.getId(),
                            shopId,
                            sku
                    );

            if (variantId == null) {
                throw new ProductBusinessException(
                        "Không thể tạo variant mặc định."
                );
            }

            // -----------------------------------------------------
            // 11. Image
            // -----------------------------------------------------

            productRepository.insertImage(
                    savedProduct.getId(),
                    imageUrl
            );

            // -----------------------------------------------------
            // 12. Initial Inventory
            // -----------------------------------------------------

            /*
             * Chỉ tạo inventory ban đầu.
             *
             * Các nghiệp vụ Restock / Update Stock / Low Stock
             * vẫn thuộc module Inventory của teammate.
             */
            productRepository.insertInventory(
                    variantId,
                    request.getStockQuantity(),
                    request.getLowStockThresholdPct(),
                    userId
            );

            // -----------------------------------------------------
            // 13. Return DTO
            // -----------------------------------------------------

            return ProductResponseDTO.builder()
                    .id(savedProduct.getId())
                    .name(savedProduct.getName())
                    .sku(sku)
                    .categoryId(
                            request.getCategoryId()
                    )
                    .price(
                            request.getPrice()
                    )
                    .stockQuantity(
                            request.getStockQuantity()
                    )
                    .imageUrl(imageUrl)
                    .status(
                            savedProduct.getSellingStatus()
                    )
                    .createdAt(
                            savedProduct.getCreatedAt()
                    )
                    .build();

        } catch (DataIntegrityViolationException exception) {

            deleteImage(imageUrl);

            throw new ProductBusinessException(
                    "Không thể tạo sản phẩm. "
                            + "Dữ liệu bị trùng hoặc không hợp lệ.",
                    exception
            );

        } catch (ProductBusinessException exception) {

            deleteImage(imageUrl);

            throw exception;
        }
    }

    // =========================================================
    // VALIDATE
    // =========================================================

    private void validateShopId(Long shopId) {

        if (shopId == null) {
            throw new ProductBusinessException(
                    "Không xác định được cửa hàng."
            );
        }
    }

    private void validatePrice(BigDecimal price) {

        if (price == null) {
            throw new ProductBusinessException(
                    "Giá không được để trống."
            );
        }

        if (price.compareTo(
                BigDecimal.valueOf(1000)
        ) < 0) {

            throw new ProductBusinessException(
                    "Giá tối thiểu là 1000 VNĐ."
            );
        }
    }

    private void validateDates(
            LocalDate receivedDate,
            LocalDate expiryDate
    ) {

        if (receivedDate == null
                || expiryDate == null) {

            throw new ProductBusinessException(
                    "Ngày nhập và ngày hết hạn không được để trống."
            );
        }

        if (expiryDate.isBefore(receivedDate)) {

            throw new ProductBusinessException(
                    "Ngày hết hạn phải lớn hơn hoặc bằng ngày nhập."
            );
        }
    }

    private void validateImage(
            MultipartFile file
    ) {

        /*
         * MultipartFile được validate ở Service vì requirement
         * không cho dùng annotation validation cho imageFile.
         */

        if (file == null) {

            throw new ProductBusinessException(
                    "Vui lòng chọn hình ảnh sản phẩm."
            );
        }

        if (file.isEmpty()) {

            throw new ProductBusinessException(
                    "Hình ảnh không được để trống."
            );
        }

        /*
         * Requirement: nhỏ hơn 5MB.
         */
        if (file.getSize() >= MAX_FILE_SIZE) {

            throw new ProductBusinessException(
                    "Dung lượng hình ảnh phải nhỏ hơn 5MB."
            );
        }

        String filename =
                file.getOriginalFilename();

        if (filename == null
                || filename.isBlank()) {

            throw new ProductBusinessException(
                    "Tên file không hợp lệ."
            );
        }

        String extension =
                getExtension(filename);

        if (!ALLOWED_EXTENSIONS.contains(
                extension
        )) {

            throw new ProductBusinessException(
                    "Chỉ chấp nhận file JPG hoặc PNG."
            );
        }

        String contentType =
                file.getContentType();

        if (contentType == null
                || (
                !contentType.equalsIgnoreCase(
                        "image/jpeg"
                )
                        &&
                        !contentType.equalsIgnoreCase(
                                "image/png"
                        )
        )) {

            throw new ProductBusinessException(
                    "File hình ảnh không hợp lệ."
            );
        }
    }

    // =========================================================
    // FILE
    // =========================================================

    private String saveImage(
            MultipartFile file
    ) {

        try {

            Files.createDirectories(
                    uploadDirectory
            );

            String extension =
                    getExtension(
                            file.getOriginalFilename()
                    );

            String filename =
                    UUID.randomUUID()
                            + "."
                            + extension;

            Path target =
                    uploadDirectory
                            .resolve(filename)
                            .normalize();

            Files.copy(
                    file.getInputStream(),
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return "/uploads/products/"
                    + filename;

        } catch (IOException exception) {

            throw new ProductBusinessException(
                    "Không thể lưu hình ảnh.",
                    exception
            );
        }
    }

    private void deleteImage(
            String imageUrl
    ) {

        if (imageUrl == null) {
            return;
        }

        try {

            String filename =
                    Paths.get(imageUrl)
                            .getFileName()
                            .toString();

            Path target =
                    uploadDirectory
                            .resolve(filename)
                            .normalize();

            Files.deleteIfExists(target);

        } catch (Exception ignored) {
            // Không làm mất exception gốc.
        }
    }

    // =========================================================
    // MAPPING
    // =========================================================

    private ProductResponseDTO toResponseDTO(
            ProductRepository.ProductListProjection projection
    ) {

        return ProductResponseDTO.builder()
                .id(projection.getId())
                .name(projection.getName())
                .sku(projection.getSku())
                .categoryId(
                        projection.getCategoryId()
                )
                .price(
                        projection.getPrice()
                )
                .unit(
                        projection.getUnit()
                )
                .stockQuantity(
                        projection.getStockQuantity()
                )
                .imageUrl(
                        projection.getImageUrl()
                )
                .status(
                        projection.getStatus()
                )
                .createdAt(
                        projection.getCreatedAt()
                )
                .build();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String normalize(String value) {

        if (value == null) {
            return null;
        }

        String result = value.trim();

        return result.isEmpty()
                ? null
                : result;
    }

    private String getExtension(
            String filename
    ) {

        int index =
                filename.lastIndexOf('.');

        if (index < 0
                || index == filename.length() - 1) {

            return "";
        }

        return filename
                .substring(index + 1)
                .toLowerCase(Locale.ROOT);
    }
}