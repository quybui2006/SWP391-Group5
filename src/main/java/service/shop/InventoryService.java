package service.shop;

import dto.shop.InventoryAdjustmentForm;
import dto.shop.InventoryBatchForm;
import dto.shop.InventoryBatchHistoryRow;
import dto.shop.InventoryBatchOption;
import dto.shop.InventoryBatchView;
import dto.shop.InventoryProductView;
import dto.shop.InventoryVariantOption;
import dto.shop.InventoryVariantView;
import entity.Shop;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.ShopRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryService {
    private final ShopRepository shopRepository;
    private final JdbcTemplate jdbc;
    private final InventoryBatchService batches;

    public InventoryService(ShopRepository shopRepository, JdbcTemplate jdbc, InventoryBatchService batches) {
        this.shopRepository = shopRepository;
        this.jdbc = jdbc;
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public List<InventoryProductView> getInventoryProducts(Long ownerUserId, String keyword, Long categoryId) {
        Long shopId = requireShop(ownerUserId).getId();
        String search = keyword == null ? "" : keyword.trim();
        StringBuilder sql = new StringBuilder("""
                SELECT p.id AS product_id,p.name AS product_name,
                       (SELECT pi.image_url FROM product_images pi WHERE pi.product_id=p.id ORDER BY pi.sort_order LIMIT 1) AS product_image_url,
                       pv.id AS variant_id,pv.name AS variant_name,
                       COALESCE(NULLIF(pv.image_url,''),(SELECT pi.image_url FROM product_images pi WHERE pi.product_id=p.id ORDER BY pi.sort_order LIMIT 1)) AS variant_image_url,
                       pv.sku,u.name AS unit_name,vi.id AS inventory_id,vi.quantity_on_hand,vi.reserved_quantity,
                       vi.available_quantity,COALESCE(sold.quantity,0) AS sold_quantity,b.id AS batch_id,b.batch_code,
                       COALESCE(batch_sold.quantity,0) AS batch_sold_quantity,
                       b.received_quantity,b.quantity_on_hand AS batch_on_hand,b.reserved_quantity AS batch_reserved,
                       b.available_quantity AS batch_available,b.received_at,
                       b.status AS batch_status,b.note
                FROM products p
                JOIN shops s ON s.id=p.shop_id
                JOIN product_variants pv ON pv.product_id=p.id
                JOIN units u ON u.id=pv.base_unit_id
                JOIN variant_inventory vi ON vi.variant_id=pv.id
                -- Chỉ cộng lượng đã trừ kho khi đơn được xác nhận hoàn tất.
                LEFT JOIN (
                    SELECT b.inventory_id,SUM(-t.on_hand_delta) AS quantity
                    FROM inventory_batch_transactions t
                    JOIN inventory_batches b ON b.id=t.batch_id
                    WHERE t.type='SALE'
                    GROUP BY b.inventory_id
                ) sold ON sold.inventory_id=vi.id
                LEFT JOIN inventory_batches b ON b.inventory_id=vi.id
                LEFT JOIN (
                    SELECT batch_id,SUM(-on_hand_delta) AS quantity
                    FROM inventory_batch_transactions
                    WHERE type='SALE'
                    GROUP BY batch_id
                ) batch_sold ON batch_sold.batch_id=b.id
                WHERE s.id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(shopId);
        if (!search.isBlank()) {
            sql.append(" AND (LOWER(p.name) LIKE LOWER(?) OR LOWER(pv.name) LIKE LOWER(?) OR LOWER(pv.sku) LIKE LOWER(?)) ");
            String like = "%" + search + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (categoryId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM product_categories pc WHERE pc.product_id=p.id AND pc.category_id=?) ");
            args.add(categoryId);
        }
        sql.append(" ORDER BY p.name,p.id,pv.name,pv.id,b.received_at,b.id");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        Map<Long, ProductBuilder> products = new LinkedHashMap<>();
        Map<Long, List<String>> categoriesByProduct = categories(shopId);
        for (Map<String, Object> row : rows) {
            long productId = longNumber(row, "product_id");
            ProductBuilder product = products.computeIfAbsent(productId, ignored -> new ProductBuilder(productId,
                    String.valueOf(row.get("product_name")), (String) row.get("product_image_url"),
                    String.join(", ", categoriesByProduct.getOrDefault(productId, List.of()))));
            long variantId = longNumber(row, "variant_id");
            VariantBuilder variant = product.variants.computeIfAbsent(variantId, ignored -> new VariantBuilder(
                    longNumber(row, "inventory_id"), variantId, String.valueOf(row.get("variant_name")),
                    String.valueOf(row.get("sku")), (String) row.get("variant_image_url"), String.valueOf(row.get("unit_name")),
                    number(row, "quantity_on_hand"), number(row, "reserved_quantity"),
                    number(row, "available_quantity"), number(row, "sold_quantity")));
            if (row.get("batch_id") != null) {
                variant.batches.add(new InventoryBatchView(longNumber(row, "batch_id"), String.valueOf(row.get("batch_code")),
                        number(row, "received_quantity"), number(row, "batch_on_hand"), number(row, "batch_reserved"),
                        number(row, "batch_available"), number(row, "batch_sold_quantity"),
                        localDateTime(row.get("received_at")),
                        String.valueOf(row.get("batch_status")), (String) row.get("note")));
            }
        }
        return products.values().stream().map(ProductBuilder::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryVariantOption> getVariantOptions(Long ownerUserId) {
        Long shopId = requireShop(ownerUserId).getId();
        return jdbc.query("""
                SELECT vi.id AS inventory_id,pv.id AS variant_id,p.name AS product_name,pv.name AS variant_name,
                       pv.sku,u.name AS unit_name
                FROM variant_inventory vi JOIN product_variants pv ON pv.id=vi.variant_id
                JOIN products p ON p.id=pv.product_id JOIN units u ON u.id=pv.base_unit_id
                WHERE pv.shop_id=? ORDER BY p.name,pv.name
                """, (rs, rowNum) -> new InventoryVariantOption(rs.getLong("inventory_id"), rs.getLong("variant_id"),
                rs.getString("product_name"), rs.getString("variant_name"), rs.getString("sku"), rs.getString("unit_name")), shopId);
    }

    @Transactional(readOnly = true)
    public List<InventoryBatchOption> getBatchOptions(Long ownerUserId) {
        return getBatchOptions(ownerUserId, null);
    }

    @Transactional(readOnly = true)
    public List<InventoryBatchOption> getBatchOptions(Long ownerUserId, Long inventoryId) {
        Long shopId = requireShop(ownerUserId).getId();
        StringBuilder sql = new StringBuilder("""
                       SELECT b.id,b.batch_code,b.available_quantity,p.name AS product_name,
                       pv.name AS variant_name,pv.sku,u.name AS unit_name
                FROM inventory_batches b JOIN variant_inventory vi ON vi.id=b.inventory_id
                JOIN product_variants pv ON pv.id=vi.variant_id JOIN products p ON p.id=pv.product_id
                JOIN units u ON u.id=pv.base_unit_id
                WHERE pv.shop_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(shopId);
        if (inventoryId != null) {
            sql.append(" AND vi.id=?");
            args.add(inventoryId);
        }
        sql.append(" ORDER BY b.received_at,b.id");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new InventoryBatchOption(rs.getLong("id"), rs.getString("batch_code"),
                rs.getString("product_name"), rs.getString("variant_name"), rs.getString("sku"),
                rs.getString("unit_name"), rs.getInt("available_quantity")), args.toArray());
    }

    @Transactional(readOnly = true)
    public List<InventoryBatchHistoryRow> getTransactionHistory(Long ownerUserId) {
        Long shopId = requireShop(ownerUserId).getId();
        return jdbc.query("""
                SELECT t.id,t.created_at,t.type,t.on_hand_delta,t.reserved_delta,t.on_hand_after,t.reserved_after,
                       t.reason,p.name AS product_name,pv.name AS variant_name,pv.sku,u.name AS unit_name,b.batch_code,o.order_code
                FROM inventory_batch_transactions t
                JOIN inventory_batches b ON b.id=t.batch_id
                JOIN variant_inventory vi ON vi.id=b.inventory_id
                JOIN product_variants pv ON pv.id=vi.variant_id
                JOIN units u ON u.id=pv.base_unit_id
                JOIN products p ON p.id=pv.product_id
                LEFT JOIN order_items oi ON oi.id=t.order_item_id
                LEFT JOIN orders o ON o.id=oi.order_id
                WHERE pv.shop_id=? ORDER BY t.created_at DESC,t.id DESC
                """, (rs, rowNum) -> new InventoryBatchHistoryRow(rs.getLong("id"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("type"), rs.getInt("on_hand_delta"),
                rs.getInt("reserved_delta"), rs.getInt("on_hand_after"), rs.getInt("reserved_after"),
                rs.getString("reason"), rs.getString("product_name"), rs.getString("variant_name"),
                rs.getString("sku"), rs.getString("unit_name"), rs.getString("batch_code"), rs.getString("order_code")), shopId);
    }

    @Transactional
    public void receiveBatch(InventoryBatchForm form, Long ownerUserId) {
        if (form == null || form.getInventoryId() == null || form.getQuantity() == null
                || form.getReceivedAt() == null)
            throw new InventoryActionException("Vui lòng nhập đầy đủ thông tin lô hàng.");
        batches.receive(form.getInventoryId(), form.getQuantity(), form.getReceivedAt(), form.getNote(), ownerUserId);
    }

    @Transactional
    public void adjustBatch(InventoryAdjustmentForm form, Long ownerUserId) {
        if (form == null || form.getBatchId() == null || form.getQuantity() == null)
            throw new InventoryActionException("Vui lòng chọn batch và nhập số lượng.");
        if (form.getInventoryId() != null) {
            Integer matches = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM inventory_batches b
                    JOIN variant_inventory vi ON vi.id=b.inventory_id
                    JOIN product_variants pv ON pv.id=vi.variant_id
                    JOIN shops s ON s.id=pv.shop_id
                    WHERE b.id=? AND vi.id=? AND s.owner_id=?
                    """, Integer.class, form.getBatchId(), form.getInventoryId(), ownerUserId);
            if (matches == null || matches != 1)
                throw new InventoryActionException("Lô hàng không thuộc biến thể đã chọn.");
        }
        batches.adjust(form.getBatchId(), form.getDirection(), form.getQuantity(), form.getReason(), ownerUserId);
    }

    private Map<Long, List<String>> categories(Long shopId) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT pc.product_id,c.name FROM product_categories pc
                JOIN categories c ON c.id=pc.category_id JOIN products p ON p.id=pc.product_id
                WHERE p.shop_id=? ORDER BY c.name
                """, (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("product_id"), ignored -> new ArrayList<>()).add(rs.getString("name")), shopId);
        return result;
    }

    private Shop requireShop(Long ownerUserId) {
        if (ownerUserId == null) throw new InventoryActionException("Phiên đăng nhập không hợp lệ.");
        return shopRepository.findByOwnerId(ownerUserId)
                .orElseThrow(() -> new InventoryActionException("Tài khoản chưa có cửa hàng hợp lệ."));
    }

    private static int number(Map<String, Object> row, String name) {
        Object value = row.get(name);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long longNumber(Map<String, Object> row, String name) {
        Object value = row.get(name);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private static final class ProductBuilder {
        private final Long id;
        private final String name;
        private final String imageUrl;
        private final String categories;
        private final Map<Long, VariantBuilder> variants = new LinkedHashMap<>();

        private ProductBuilder(Long id, String name, String imageUrl, String categories) {
            this.id = id; this.name = name; this.imageUrl = imageUrl; this.categories = categories;
        }

        private InventoryProductView toView() {
            List<InventoryVariantView> variantViews = variants.values().stream().map(VariantBuilder::toView).toList();
            int onHand = variantViews.stream().mapToInt(InventoryVariantView::onHand).sum();
            int reserved = variantViews.stream().mapToInt(InventoryVariantView::reserved).sum();
            int sold = variantViews.stream().mapToInt(InventoryVariantView::sold).sum();
            return new InventoryProductView(id, name, imageUrl, categories, onHand, reserved, onHand - reserved, sold, variantViews);
        }
    }

    private static final class VariantBuilder {
        private final Long inventoryId;
        private final Long id;
        private final String name;
        private final String sku;
        private final String imageUrl;
        private final String unitName;
        private final int onHand;
        private final int reserved;
        private final int available;
        private final int sold;
        private final List<InventoryBatchView> batches = new ArrayList<>();

        private VariantBuilder(Long inventoryId, Long id, String name, String sku, String imageUrl, String unitName,
                                int onHand, int reserved, int available, int sold) {
            this.inventoryId = inventoryId; this.id = id; this.name = name; this.sku = sku; this.imageUrl = imageUrl;
            this.unitName = unitName; this.onHand = onHand; this.reserved = reserved;
            this.available = available; this.sold = sold;
        }

        private InventoryVariantView toView() {
            return new InventoryVariantView(inventoryId, id, name, sku, imageUrl, unitName, onHand, reserved,
                    available, sold, List.copyOf(batches));
        }
    }
}
