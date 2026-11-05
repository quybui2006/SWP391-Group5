package service.operations;

import dto.operations.ManagedShopListItem;
import dto.operations.ManagedShopProduct;
import dto.operations.ManagedShopVariant;
import dto.operations.OperationsCatalogProduct;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import service.customer.NotificationService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class OperationsShopManagementService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public OperationsShopManagementService(JdbcTemplate jdbc, NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    public List<ManagedShopListItem> listShops(String keyword) {
        String term = keyword == null ? "" : keyword.trim().toLowerCase();
        String like = "%" + term + "%";
        return jdbc.query("""
                SELECT s.id, s.name, u.full_name AS owner_name, u.email AS owner_email, s.phone,
                       CONCAT_WS(', ', s.address_detail, s.ward_name, s.province_code) AS address,
                       s.approval_status, s.operating_status, s.suspension_reason, s.created_at,
                       (SELECT COUNT(*) FROM products p WHERE p.shop_id = s.id) AS product_count,
                       (SELECT COUNT(*) FROM product_variants pv WHERE pv.shop_id = s.id) AS variant_count
                FROM shops s JOIN users u ON u.id = s.owner_id
                WHERE ? = '' OR LOWER(s.name) LIKE ? OR LOWER(u.full_name) LIKE ? OR LOWER(u.email) LIKE ?
                ORDER BY s.created_at DESC, s.id DESC
                """, this::mapShop, term, like, like, like);
    }

    public ManagedShopListItem getShop(Long shopId) {
        List<ManagedShopListItem> rows = jdbc.query("""
                SELECT s.id, s.name, u.full_name AS owner_name, u.email AS owner_email, s.phone,
                       CONCAT_WS(', ', s.address_detail, s.ward_name, s.province_code) AS address,
                       s.approval_status, s.operating_status, s.suspension_reason, s.created_at,
                       (SELECT COUNT(*) FROM products p WHERE p.shop_id = s.id) AS product_count,
                       (SELECT COUNT(*) FROM product_variants pv WHERE pv.shop_id = s.id) AS variant_count
                FROM shops s JOIN users u ON u.id = s.owner_id WHERE s.id = ?
                """, this::mapShop, shopId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cửa hàng.");
        return rows.get(0);
    }

    public List<ManagedShopVariant> listShopVariants(Long shopId) {
        getShop(shopId);
        return jdbc.query("""
                SELECT pv.id AS variant_id, p.name AS product_name, pv.name AS variant_name, pv.sku,
                       COALESCE(NULLIF(pv.image_url, ''), (SELECT pi.image_url FROM product_images pi
                           WHERE pi.product_id = p.id ORDER BY pi.sort_order LIMIT 1)) AS image_url,
                       pv.price, u.name AS unit_name, pv.status AS variant_status, pv.approval_status,
                       COALESCE(vi.quantity_on_hand, 0) AS quantity_on_hand,
                       COALESCE(vi.available_quantity, 0) AS available_quantity,
                       COALESCE(sold.quantity, 0) AS sold_quantity,
                       p.batch_code
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.shop_id = pv.shop_id
                JOIN units u ON u.id = pv.base_unit_id
                LEFT JOIN variant_inventory vi ON vi.variant_id = pv.id
                LEFT JOIN (
                    SELECT oi.variant_id, SUM(oi.quantity) AS quantity
                    FROM order_items oi
                    JOIN orders o ON o.id = oi.order_id
                    WHERE o.status = 'COMPLETED'
                    GROUP BY oi.variant_id
                ) sold ON sold.variant_id = pv.id
                WHERE pv.shop_id = ?
                ORDER BY p.name, pv.name, pv.id
                """, (rs, row) -> new ManagedShopVariant(
                rs.getLong("variant_id"), rs.getString("product_name"), rs.getString("variant_name"),
                rs.getString("sku"), rs.getString("image_url"), rs.getBigDecimal("price"),
                rs.getString("unit_name"), rs.getString("variant_status"), rs.getString("approval_status"),
                nullableInt(rs, "quantity_on_hand"), nullableInt(rs, "available_quantity"),
                nullableLong(rs, "sold_quantity"),
                rs.getString("batch_code")), shopId);
    }

    public List<ManagedShopProduct> listShopProductsWithVariants(Long shopId) {
        return listShopProductsWithVariants(shopId, "", null);
    }

    public List<ManagedShopProduct> listShopProductsWithVariants(Long shopId, String keyword, Long categoryId) {
        getShop(shopId);
        String term = keyword == null ? "" : keyword.trim().toLowerCase();
        String like = "%" + term + "%";
        String categoryFilter = categoryId == null ? "" : " AND EXISTS (SELECT 1 FROM product_categories pc "
                + "WHERE pc.product_id = p.id AND pc.category_id = ?)";
        List<Object> args = new ArrayList<>(List.of(shopId, term, like, like, like));
        if (categoryId != null) args.add(categoryId);
        Map<Long, ProductWithVariants> products = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.id AS product_id, p.name AS product_name,
                       (SELECT pi.image_url FROM product_images pi
                        WHERE pi.product_id = p.id ORDER BY pi.sort_order LIMIT 1) AS product_image_url,
                       COALESCE((SELECT GROUP_CONCAT(c.name ORDER BY c.name SEPARATOR ', ')
                                 FROM product_categories pc JOIN categories c ON c.id = pc.category_id
                                 WHERE pc.product_id = p.id), 'Chưa phân loại') AS category_name,
                       pv.id AS variant_id, pv.name AS variant_name, pv.sku,
                       COALESCE(NULLIF(pv.image_url, ''),
                           (SELECT pi.image_url FROM product_images pi
                            WHERE pi.product_id = p.id ORDER BY pi.sort_order LIMIT 1)) AS image_url,
                       pv.price, u.name AS unit_name, pv.status AS variant_status, pv.approval_status,
                       COALESCE(vi.quantity_on_hand, 0) AS quantity_on_hand,
                       COALESCE(vi.available_quantity, 0) AS available_quantity,
                       COALESCE(sold.quantity, 0) AS sold_quantity
                FROM products p
                LEFT JOIN product_variants pv ON pv.product_id = p.id AND pv.shop_id = p.shop_id
                LEFT JOIN units u ON u.id = pv.base_unit_id
                LEFT JOIN variant_inventory vi ON vi.variant_id = pv.id
                LEFT JOIN (
                    SELECT oi.variant_id, SUM(oi.quantity) AS quantity
                    FROM order_items oi
                    JOIN orders o ON o.id = oi.order_id
                    WHERE o.status = 'COMPLETED'
                    GROUP BY oi.variant_id
                ) sold ON sold.variant_id = pv.id
                WHERE p.shop_id = ?
                  AND (? = '' OR LOWER(p.name) LIKE ? OR EXISTS
                       (SELECT 1 FROM product_variants search_pv WHERE search_pv.product_id = p.id
                        AND (LOWER(search_pv.name) LIKE ? OR LOWER(search_pv.sku) LIKE ?)))
                %s
                ORDER BY p.name, p.id, pv.name, pv.id
                """.formatted(categoryFilter), rs -> {
            long productId = rs.getLong("product_id");
            ProductWithVariants product = products.computeIfAbsent(productId, ignored ->
                    new ProductWithVariants(productId, getString(rs, "product_name"),
                            getString(rs, "product_image_url"), getString(rs, "category_name")));
            Long variantId = nullableLong(rs, "variant_id");
            if (variantId != null) {
                product.variants.add(new ManagedShopVariant(
                        variantId, product.name, getString(rs, "variant_name"), getString(rs, "sku"),
                        getString(rs, "image_url"), rs.getBigDecimal("price"), getString(rs, "unit_name"),
                        getString(rs, "variant_status"), getString(rs, "approval_status"),
                        nullableInt(rs, "quantity_on_hand"), nullableInt(rs, "available_quantity"),
                        nullableLong(rs, "sold_quantity"), null));
            }
        }, args.toArray());

        // Group every product with its variants and preserve products that have none yet.
        return products.values().stream()
                .map(product -> new ManagedShopProduct(product.id, product.name, product.imageUrl, product.categoryName,
                        List.copyOf(product.variants)))
                .toList();
    }

    public List<OperationsCatalogProduct> listAllProductsWithVariants(String keyword) {
        String term = keyword == null ? "" : keyword.trim().toLowerCase();
        String like = "%" + term + "%";
        Map<Long, CatalogProductWithVariants> products = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.id AS product_id, p.name AS product_name, p.selling_status AS product_status,
                       p.is_visible AS product_visible, s.id AS shop_id, s.name AS shop_name,
                       (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id ORDER BY pi.sort_order LIMIT 1) AS product_image_url,
                       pv.id AS variant_id, pv.name AS variant_name, pv.sku,
                       COALESCE(NULLIF(pv.image_url, ''), (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id ORDER BY pi.sort_order LIMIT 1)) AS image_url,
                       pv.price, u.name AS unit_name, pv.status AS variant_status, pv.approval_status,
                       COALESCE(vi.quantity_on_hand, 0) AS quantity_on_hand,
                       COALESCE(vi.available_quantity, 0) AS available_quantity,
                       COALESCE(sold.quantity, 0) AS sold_quantity
                FROM products p JOIN shops s ON s.id = p.shop_id
                LEFT JOIN product_variants pv ON pv.product_id = p.id AND pv.shop_id = p.shop_id
                LEFT JOIN units u ON u.id = pv.base_unit_id
                LEFT JOIN variant_inventory vi ON vi.variant_id = pv.id
                LEFT JOIN (SELECT oi.variant_id, SUM(oi.quantity) AS quantity FROM order_items oi
                           JOIN orders o ON o.id = oi.order_id WHERE o.status = 'COMPLETED' GROUP BY oi.variant_id) sold
                       ON sold.variant_id = pv.id
                WHERE ? = '' OR LOWER(p.name) LIKE ? OR LOWER(s.name) LIKE ? OR EXISTS
                      (SELECT 1 FROM product_variants search_pv WHERE search_pv.product_id = p.id AND LOWER(search_pv.sku) LIKE ?)
                ORDER BY s.name, p.name, p.id, pv.name, pv.id
                """, rs -> {
            long productId = rs.getLong("product_id");
            CatalogProductWithVariants product = products.computeIfAbsent(productId, ignored ->
                    new CatalogProductWithVariants(productId, rsString(rs, "product_name"),
                            rsLong(rs, "shop_id"), rsString(rs, "shop_name"), rsString(rs, "product_status"),
                            rsBoolean(rs, "product_visible"), rsString(rs, "product_image_url")));
            Long variantId = nullableLong(rs, "variant_id");
            if (variantId != null) {
                product.variants.add(new ManagedShopVariant(variantId, product.name, rsString(rs, "variant_name"),
                        rsString(rs, "sku"), rsString(rs, "image_url"), rs.getBigDecimal("price"),
                        rsString(rs, "unit_name"), rsString(rs, "variant_status"), rsString(rs, "approval_status"),
                        nullableInt(rs, "quantity_on_hand"), nullableInt(rs, "available_quantity"),
                        nullableLong(rs, "sold_quantity"), null));
            }
        }, term, like, like, like);
        return products.values().stream().map(product -> new OperationsCatalogProduct(product.id, product.name,
                product.shopId, product.shopName, product.status, product.visible, product.imageUrl,
                List.copyOf(product.variants))).toList();
    }

    @Transactional
    public void suspendShop(Long shopId, String reason) {
        String normalized = normalizeReason(reason, "Vui lòng nhập lý do chặn Shop.");
        int updated = jdbc.update("""
                UPDATE shops SET operating_status = 'SUSPENDED', suspension_reason = ?
                WHERE id = ? AND approval_status = 'APPROVED' AND operating_status <> 'SUSPENDED'
                """, normalized, shopId);
        if (updated != 1) {
            if (!existsShop(shopId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cửa hàng.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop đang bị chặn hoặc chưa được duyệt mở.");
        }
        Long ownerId = jdbc.queryForObject("SELECT owner_id FROM shops WHERE id = ?", Long.class, shopId);
        String shopName = jdbc.queryForObject("SELECT name FROM shops WHERE id = ?", String.class, shopId);
        notifications.createNotification(ownerId, "ACCOUNT", "Cửa hàng tạm ngưng hoạt động",
                "Cửa hàng " + shopName + " đã bị Operations tạm ngưng. Lý do: " + normalized,
                "SHOP", shopId);
    }

    private ManagedShopListItem mapShop(ResultSet rs, int row) throws SQLException {
        return new ManagedShopListItem(rs.getLong("id"), rs.getString("name"), rs.getString("owner_name"),
                rs.getString("owner_email"), rs.getString("phone"), rs.getString("address"),
                rs.getString("approval_status"), rs.getString("operating_status"), rs.getString("suspension_reason"),
                rs.getLong("product_count"), rs.getLong("variant_count"), localDateTime(rs, "created_at"));
    }

    private boolean existsShop(Long shopId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM shops WHERE id = ?", Integer.class, shopId);
        return count != null && count > 0;
    }

    private String normalizeReason(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        String normalized = value.trim();
        if (normalized.length() < 5 || normalized.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do phải có từ 5 đến 1000 ký tự.");
        }
        return normalized;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private static String getString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ex) {
            throw new IllegalStateException("Không thể đọc dữ liệu hậu kiểm cửa hàng.", ex);
        }
    }

    private static final class ProductWithVariants {
        private final Long id;
        private final String name;
        private final String imageUrl;
        private final String categoryName;
        private final List<ManagedShopVariant> variants = new ArrayList<>();

        private ProductWithVariants(Long id, String name, String imageUrl, String categoryName) {
            this.id = id;
            this.name = name;
            this.imageUrl = imageUrl;
            this.categoryName = categoryName;
        }
    }

    private static final class CatalogProductWithVariants {
        private final Long id;
        private final String name;
        private final Long shopId;
        private final String shopName;
        private final String status;
        private final boolean visible;
        private final String imageUrl;
        private final List<ManagedShopVariant> variants = new ArrayList<>();

        private CatalogProductWithVariants(Long id, String name, Long shopId, String shopName,
                                           String status, boolean visible, String imageUrl) {
            this.id = id;
            this.name = name;
            this.shopId = shopId;
            this.shopName = shopName;
            this.status = status;
            this.visible = visible;
            this.imageUrl = imageUrl;
        }
    }

    private static String rsString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ex) {
            throw new org.springframework.jdbc.UncategorizedSQLException("Đọc catalog sản phẩm Operations", "", ex);
        }
    }

    private static Long rsLong(ResultSet rs, String column) {
        try {
            return rs.getLong(column);
        } catch (SQLException ex) {
            throw new org.springframework.jdbc.UncategorizedSQLException("Đọc catalog sản phẩm Operations", "", ex);
        }
    }

    private static boolean rsBoolean(ResultSet rs, String column) {
        try {
            return rs.getBoolean(column);
        } catch (SQLException ex) {
            throw new org.springframework.jdbc.UncategorizedSQLException("Đọc catalog sản phẩm Operations", "", ex);
        }
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
