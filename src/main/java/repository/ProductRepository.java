package repository;

import entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // =========================================================
    // CHECK DUPLICATE
    // =========================================================

    boolean existsByShopIdAndNameIgnoreCase(
            Long shopId,
            String name
    );

    boolean existsByShopIdAndBatchCodeIgnoreCase(
            Long shopId,
            String batchCode
    );

    // =========================================================
    // CHECK SHOP / CATEGORY / UNIT
    // =========================================================

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM shops
                    WHERE id = :shopId
                    AND owner_id = :userId
                    """,
            nativeQuery = true
    )
    long countOwnedShop(
            @Param("shopId") Long shopId,
            @Param("userId") Long userId
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM categories
                    WHERE id = :categoryId
                    AND is_active = 1
                    """,
            nativeQuery = true
    )
    long countActiveCategory(
            @Param("categoryId") Long categoryId
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM units
                    WHERE id = :unitId
                    AND is_active = 1
                    """,
            nativeQuery = true
    )
    long countActiveUnit(
            @Param("unitId") Long unitId
    );

    // =========================================================
    // PRODUCT LIST
    // =========================================================

    @Query(
            value = """
                    SELECT
                        p.id AS id,
                        p.name AS name,

                        (
                            SELECT v.sku
                            FROM product_variants v
                            WHERE v.product_id = p.id
                            ORDER BY v.id
                            LIMIT 1
                        ) AS sku,

                        (
                            SELECT pc.category_id
                            FROM product_categories pc
                            WHERE pc.product_id = p.id
                            ORDER BY pc.category_id
                            LIMIT 1
                        ) AS categoryId,

                        (
                            SELECT v.price
                            FROM product_variants v
                            WHERE v.product_id = p.id
                            ORDER BY v.id
                            LIMIT 1
                        ) AS price,

                        (
                            SELECT u.name
                            FROM product_variants v
                            JOIN units u
                                ON u.id = v.base_unit_id
                            WHERE v.product_id = p.id
                            ORDER BY v.id
                            LIMIT 1
                        ) AS unit,

                        (
                            SELECT COALESCE(
                                SUM(
                                    vi.quantity_on_hand
                                    - vi.reserved_quantity
                                ),
                                0
                            )
                            FROM product_variants v
                            JOIN variant_inventory vi
                                ON vi.variant_id = v.id
                            WHERE v.product_id = p.id
                        ) AS stockQuantity,

                        (
                            SELECT pi.image_url
                            FROM product_images pi
                            WHERE pi.product_id = p.id
                            ORDER BY pi.sort_order
                            LIMIT 1
                        ) AS imageUrl,

                        p.selling_status AS status,
                        p.created_at AS createdAt

                    FROM products p

                    WHERE p.shop_id = :shopId

                    AND (
                        :keyword = ''

                        OR LOWER(p.name)
                           LIKE LOWER(
                               CONCAT('%', :keyword, '%')
                           )

                        OR EXISTS (
                            SELECT 1
                            FROM product_variants v2
                            WHERE v2.product_id = p.id

                            AND LOWER(v2.sku)
                                LIKE LOWER(
                                    CONCAT('%', :keyword, '%')
                                )
                        )
                    )

                    ORDER BY p.created_at DESC
                    """,

            countQuery = """
                    SELECT COUNT(*)
                    FROM products p

                    WHERE p.shop_id = :shopId

                    AND (
                        :keyword = ''

                        OR LOWER(p.name)
                           LIKE LOWER(
                               CONCAT('%', :keyword, '%')
                           )

                        OR EXISTS (
                            SELECT 1
                            FROM product_variants v
                            WHERE v.product_id = p.id

                            AND LOWER(v.sku)
                                LIKE LOWER(
                                    CONCAT('%', :keyword, '%')
                                )
                        )
                    )
                    """,

            nativeQuery = true
    )
    Page<ProductListProjection> findProductList(
            @Param("shopId") Long shopId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    interface ProductListProjection {

        Long getId();

        String getName();

        String getSku();

        Long getCategoryId();

        BigDecimal getPrice();

        String getUnit();

        Integer getStockQuantity();

        String getImageUrl();

        String getStatus();

        java.time.LocalDateTime getCreatedAt();
    }

    // =========================================================
    // INSERT INTO RELATED TABLES
    // =========================================================

    @Modifying
    @Query(
            value = """
                    INSERT INTO product_categories(
                        product_id,
                        category_id
                    )
                    VALUES(
                        :productId,
                        :categoryId
                    )
                    """,
            nativeQuery = true
    )
    void insertCategory(
            @Param("productId") Long productId,
            @Param("categoryId") Long categoryId
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO product_variants(
                        product_id,
                        shop_id,
                        sku,
                        name,
                        image_url,
                        base_unit_id,
                        price,
                        min_order_quantity,
                        status
                    )
                    VALUES(
                        :productId,
                        :shopId,
                        :sku,
                        :variantName,
                        :imageUrl,
                        :unitId,
                        :price,
                        1,
                        'DRAFT'
                    )
                    """,
            nativeQuery = true
    )
    void insertVariant(
            @Param("productId") Long productId,
            @Param("shopId") Long shopId,
            @Param("sku") String sku,
            @Param("variantName") String variantName,
            @Param("imageUrl") String imageUrl,
            @Param("unitId") Long unitId,
            @Param("price") BigDecimal price
    );

    @Query(
            value = """
                    SELECT id
                    FROM product_variants
                    WHERE product_id = :productId
                    AND shop_id = :shopId
                    AND sku = :sku
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    Long findVariantId(
            @Param("productId") Long productId,
            @Param("shopId") Long shopId,
            @Param("sku") String sku
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO product_images(
                        product_id,
                        image_url,
                        sort_order
                    )
                    VALUES(
                        :productId,
                        :imageUrl,
                        0
                    )
                    """,
            nativeQuery = true
    )
    void insertImage(
            @Param("productId") Long productId,
            @Param("imageUrl") String imageUrl
    );

    @Modifying
    @Query(
            value = """
                    INSERT INTO variant_inventory(
                        variant_id,
                        initial_quantity,
                        quantity_on_hand,
                        reserved_quantity,
                        low_stock_threshold_pct,
                        status,
                        created_by
                    )
                    VALUES(
                        :variantId,
                        :quantity,
                        :quantity,
                        0,
                        :threshold,
                        'ACTIVE',
                        :userId
                    )
                    """,
            nativeQuery = true
    )
    void insertInventory(
            @Param("variantId") Long variantId,
            @Param("quantity") Integer quantity,
            @Param("threshold") BigDecimal threshold,
            @Param("userId") Long userId
    );
}