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

/**
 * Service xử lý nghiệp vụ quản lý tồn kho dành cho Shop Owner.
 *
 * Các nhiệm vụ chính:
 * - Lấy danh sách sản phẩm, biến thể, số lượng tồn kho và các lô hàng.
 * - Cung cấp dữ liệu lựa chọn biến thể và lô hàng cho các biểu mẫu.
 * - Tra cứu lịch sử giao dịch nhập, xuất và điều chỉnh kho.
 * - Tiếp nhận yêu cầu nhập lô hàng và điều chỉnh số lượng.
 * - Lấy danh sách các biến thể đang có lượng hàng khả dụng thấp.
 *
 * Controller gọi các method tại đây; những thao tác chi tiết trên từng lô
 * được chuyển tiếp cho InventoryBatchService.
 */
@Service
public class InventoryService {
    // Repository dùng để tìm cửa hàng theo chủ sở hữu.
    private final ShopRepository shopRepository;

    // JdbcTemplate dùng để thực hiện các truy vấn SQL trực tiếp.
    private final JdbcTemplate jdbc;

    // Service chuyên xử lý nghiệp vụ nhập và điều chỉnh từng lô hàng.
    private final InventoryBatchService batches;

    /**
     * Constructor Injection: Spring truyền các dependency vào Service.
     */
    public InventoryService(
            ShopRepository shopRepository,
            JdbcTemplate jdbc,
            InventoryBatchService batches) {
        this.shopRepository = shopRepository;
        this.jdbc = jdbc;
        this.batches = batches;
    }

    /**
     * Lấy dữ liệu tồn kho để hiển thị trên màn hình quản lý kho.
     *
     * Dữ liệu được truy vấn theo cửa hàng của ownerUserId, có thể lọc theo:
     * - Từ khóa: tên sản phẩm, tên biến thể hoặc SKU.
     * - Danh mục: chỉ lấy sản phẩm thuộc categoryId được chọn.
     *
     * Kết quả được gom từ các dòng SQL thành cấu trúc:
     * Sản phẩm -> Các biến thể -> Các lô hàng của từng biến thể.
     *
     * readOnly = true vì method chỉ đọc dữ liệu, không cập nhật Database.
     */
    @Transactional(readOnly = true)
    public List<InventoryProductView> getInventoryProducts(
            Long ownerUserId, String keyword, Long categoryId) {
        // Xác minh tài khoản có cửa hàng hợp lệ và lấy ID cửa hàng.
        Long shopId = requireShop(ownerUserId).getId();

        // Chuẩn hóa từ khóa tìm kiếm: nếu null thì dùng chuỗi rỗng,
        // nếu có giá trị thì loại bỏ khoảng trắng ở đầu và cuối.
        String search = keyword == null ? "" : keyword.trim();

        // Xây dựng câu SQL động để chỉ thêm điều kiện tìm kiếm/lọc
        // khi người dùng thực sự truyền các tham số tương ứng.
        StringBuilder sql = new StringBuilder("""
                SELECT p.id AS product_id,p.name AS product_name,
                       (SELECT pi.image_url
                        FROM product_images pi
                        WHERE pi.product_id=p.id
                        ORDER BY pi.sort_order
                        LIMIT 1) AS product_image_url,
                       pv.id AS variant_id,pv.name AS variant_name,
                       COALESCE(NULLIF(pv.image_url,''),(SELECT pi.image_url
                        FROM product_images pi
                        WHERE pi.product_id=p.id
                        ORDER BY pi.sort_order
                        LIMIT 1)) AS variant_image_url,
                       pv.sku,u.name AS unit_name,vi.id AS inventory_id,
                       vi.quantity_on_hand,vi.reserved_quantity,
                       vi.available_quantity,vi.low_stock_threshold_quantity,
                       -- Tổng số lượng đã bán trong các đơn hoàn tất.
                       -- Việc giảm kho thủ công không được tính là hàng đã bán.
                       COALESCE(sold.quantity,0) AS sold_quantity,
                       b.id AS batch_id,b.batch_code,b.received_quantity,
                       b.quantity_on_hand AS batch_on_hand,
                       b.reserved_quantity AS batch_reserved,
                       b.available_quantity AS batch_available,
                       b.received_at,b.status AS batch_status,b.note,
                       COALESCE(batch_sold.quantity,0) AS batch_sold
                FROM products p
                JOIN shops s ON s.id=p.shop_id
                JOIN product_variants pv ON pv.product_id=p.id
                JOIN units u ON u.id=pv.base_unit_id
                JOIN variant_inventory vi ON vi.variant_id=pv.id
                -- Chỉ đơn hàng hoàn tất mới được tính là hàng đã bán; giảm kho thủ công không tính là doanh số.
                LEFT JOIN (
                    SELECT oi.inventory_id,SUM(oi.quantity) AS quantity
                    FROM order_items oi
                    JOIN orders o ON o.id=oi.order_id
                    WHERE o.status='COMPLETED'
                    GROUP BY oi.inventory_id
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

        // Danh sách tham số tương ứng với các dấu ? trong câu SQL.
        // Tham số đầu tiên luôn là shopId để giới hạn dữ liệu theo cửa hàng.
        List<Object> args = new ArrayList<>();
        args.add(shopId);

        // Chỉ thêm điều kiện tìm kiếm khi từ khóa không rỗng.
        if (!search.isBlank()) {
            sql.append("""
                     AND (LOWER(p.name) LIKE LOWER(?)
                       OR LOWER(pv.name) LIKE LOWER(?)
                       OR LOWER(pv.sku) LIKE LOWER(?))
                    """);
            // Thêm ký tự % để tìm kiếm gần đúng, không yêu cầu khớp toàn bộ chuỗi.
            String like = "%" + search + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        // Nếu người dùng chọn danh mục thì chỉ lấy sản phẩm
        // có liên kết với danh mục đó trong bảng product_categories.
        if (categoryId != null) {
            sql.append("""
                     AND EXISTS (
                         SELECT 1
                         FROM product_categories pc
                         WHERE pc.product_id=p.id AND pc.category_id=?
                     )
                    """);
            args.add(categoryId);
        }

        // Sắp xếp dữ liệu theo sản phẩm, biến thể và thời điểm nhận lô.
        // Nhờ đó các lô của cùng biến thể nằm gần nhau trong kết quả truy vấn.
        sql.append(" ORDER BY p.name,p.id,pv.name,pv.id,b.received_at,b.id");

        // Thực thi SQL và nhận từng dòng kết quả dưới dạng Map< tên cột, giá trị >.
        List<Map<String, Object>> rows =
                jdbc.queryForList(sql.toString(), args.toArray());

        // Map này gom dữ liệu theo productId để không tạo sản phẩm lặp lại
        // khi một sản phẩm có nhiều biến thể hoặc nhiều lô hàng.
        Map<Long, ProductBuilder> products = new LinkedHashMap<>();

        // Lấy tên các danh mục của từng sản phẩm trong cửa hàng.
        Map<Long, List<String>> categoriesByProduct = categories(shopId);

        // Duyệt từng dòng SQL để dựng cấu trúc sản phẩm -> biến thể -> lô hàng.
        for (Map<String, Object> row : rows) {
            long productId = longNumber(row, "product_id");

            // Nếu sản phẩm chưa có trong Map thì tạo ProductBuilder mới.
            // Nếu đã tồn tại thì tái sử dụng đối tượng cũ để gom các biến thể vào cùng sản phẩm.
            ProductBuilder product = products.computeIfAbsent(
                    productId,
                    ignored -> new ProductBuilder(
                            productId,
                            String.valueOf(row.get("product_name")),
                            (String) row.get("product_image_url"),
                            String.join(", ",
                                    categoriesByProduct.getOrDefault(productId, List.of())),
                            number(row, "sold_quantity")));

            long variantId = longNumber(row, "variant_id");

            // Gom các dòng kết quả theo variantId.
            // Một biến thể có thể xuất hiện ở nhiều dòng vì có nhiều lô hàng.
            VariantBuilder variant = product.variants.computeIfAbsent(
                    variantId,
                    ignored -> new VariantBuilder(
                            longNumber(row, "inventory_id"),
                            variantId,
                            String.valueOf(row.get("variant_name")),
                            String.valueOf(row.get("sku")),
                            (String) row.get("variant_image_url"),
                            String.valueOf(row.get("unit_name")),
                            number(row, "quantity_on_hand"),
                            number(row, "reserved_quantity"),
                            number(row, "available_quantity"),
                            number(row, "sold_quantity"),
                            number(row, "low_stock_threshold_quantity")));

            // Chỉ tạo thông tin lô nếu dòng SQL có batch_id.
            // LEFT JOIN cho phép biến thể chưa có lô vẫn được hiển thị.
            if (row.get("batch_id") != null) {
                variant.batches.add(new InventoryBatchView(
                        longNumber(row, "batch_id"),
                        String.valueOf(row.get("batch_code")),
                        number(row, "received_quantity"),
                        number(row, "batch_on_hand"),
                        number(row, "batch_reserved"),
                        number(row, "batch_available"),
                        number(row, "batch_sold"),
                        localDateTime(row.get("received_at")),
                        String.valueOf(row.get("batch_status")),
                        (String) row.get("note")));
            }
        }

        // Chuyển từng ProductBuilder thành DTO InventoryProductView
        // để Controller đưa dữ liệu lên giao diện.
        return products.values().stream()
                .map(ProductBuilder::toView)
                .toList();
    }

    /**
     * Lấy danh sách biến thể thuộc cửa hàng để hiển thị trong dropdown.
     * Mỗi lựa chọn gồm thông tin sản phẩm, biến thể, SKU và đơn vị tính.
     */
    @Transactional(readOnly = true)
    public List<InventoryVariantOption> getVariantOptions(Long ownerUserId) {
        Long shopId = requireShop(ownerUserId).getId();

        return jdbc.query("""
                SELECT vi.id AS inventory_id,pv.id AS variant_id,
                       p.name AS product_name,pv.name AS variant_name,
                       pv.sku,u.name AS unit_name
                FROM variant_inventory vi
                JOIN product_variants pv ON pv.id=vi.variant_id
                JOIN products p ON p.id=pv.product_id
                JOIN units u ON u.id=pv.base_unit_id
                WHERE pv.shop_id=?
                ORDER BY p.name,pv.name
                """,
                (rs, rowNum) -> new InventoryVariantOption(
                        rs.getLong("inventory_id"),
                        rs.getLong("variant_id"),
                        rs.getString("product_name"),
                        rs.getString("variant_name"),
                        rs.getString("sku"),
                        rs.getString("unit_name")),
                shopId);
    }

    /**
     * Lấy tất cả lô hàng còn hàng khả dụng của cửa hàng.
     * Method này gọi phiên bản có inventoryId = null, tức là không giới hạn
     * theo một biến thể/tồn kho cụ thể.
     */
    @Transactional(readOnly = true)
    public List<InventoryBatchOption> getBatchOptions(Long ownerUserId) {
        return getBatchOptions(ownerUserId, null);
    }

    /**
     * Lấy các lô hàng đang hoạt động và còn số lượng khả dụng lớn hơn 0.
     * Nếu inventoryId khác null thì chỉ lấy lô thuộc bản ghi tồn kho đó.
     */
    @Transactional(readOnly = true)
    public List<InventoryBatchOption> getBatchOptions(
            Long ownerUserId, Long inventoryId) {
        Long shopId = requireShop(ownerUserId).getId();

        StringBuilder sql = new StringBuilder("""
                SELECT b.id,b.batch_code,b.available_quantity,
                       p.name AS product_name,pv.name AS variant_name,
                       pv.sku,u.name AS unit_name
                FROM inventory_batches b
                JOIN variant_inventory vi ON vi.id=b.inventory_id
                JOIN product_variants pv ON pv.id=vi.variant_id
                JOIN products p ON p.id=pv.product_id
                JOIN units u ON u.id=pv.base_unit_id
                WHERE pv.shop_id=?
                  AND b.status='ACTIVE'
                  AND b.available_quantity>0
                """);

        List<Object> args = new ArrayList<>();
        args.add(shopId);

        if (inventoryId != null) {
            sql.append(" AND vi.id=?");
            args.add(inventoryId);
        }

        sql.append(" ORDER BY b.received_at,b.id");

        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> new InventoryBatchOption(
                        rs.getLong("id"),
                        rs.getString("batch_code"),
                        rs.getString("product_name"),
                        rs.getString("variant_name"),
                        rs.getString("sku"),
                        rs.getString("unit_name"),
                        rs.getInt("available_quantity")),
                args.toArray());
    }

    /**
     * Tìm inventoryId tương ứng với batchId, đồng thời xác minh lô hàng
     * có thuộc cửa hàng của người đang đăng nhập hay không.
     *
     * Trả về null nếu batchId null hoặc không tìm thấy lô thuộc cửa hàng.
     */
    @Transactional(readOnly = true)
    public Long getBatchInventoryId(Long ownerUserId, Long batchId) {
        // Không có batchId thì không thể tra cứu tồn kho tương ứng.
        if (batchId == null) return null;

        // Xác minh cửa hàng của người dùng.
        Long shopId = requireShop(ownerUserId).getId();

        List<Long> inventoryIds = jdbc.queryForList("""
                SELECT vi.id
                FROM inventory_batches b
                JOIN variant_inventory vi ON vi.id=b.inventory_id
                JOIN product_variants pv ON pv.id=vi.variant_id
                WHERE b.id=? AND pv.shop_id=?
                """, Long.class, batchId, shopId);

        // Nếu không có kết quả thì trả null; ngược lại lấy inventoryId đầu tiên.
        return inventoryIds.isEmpty() ? null : inventoryIds.get(0);
    }

    /**
     * Lấy lịch sử giao dịch của tất cả lô hàng thuộc cửa hàng.
     *
     * Bao gồm loại giao dịch, biến động số lượng, số lượng sau giao dịch,
     * lý do, sản phẩm, biến thể, SKU, lô hàng và mã đơn hàng liên quan (nếu có).
     * Dữ liệu được sắp xếp từ giao dịch mới nhất đến cũ nhất.
     */
    @Transactional(readOnly = true)
    public List<InventoryBatchHistoryRow> getTransactionHistory(Long ownerUserId) {
        Long shopId = requireShop(ownerUserId).getId();

        return jdbc.query("""
                SELECT t.id,t.created_at,t.type,t.on_hand_delta,t.reserved_delta,
                       t.on_hand_after,t.reserved_after,t.reason,
                       p.name AS product_name,pv.name AS variant_name,pv.sku,
                       u.name AS unit_name,b.batch_code,o.order_code
                FROM inventory_batch_transactions t
                JOIN inventory_batches b ON b.id=t.batch_id
                JOIN variant_inventory vi ON vi.id=b.inventory_id
                JOIN product_variants pv ON pv.id=vi.variant_id
                JOIN units u ON u.id=pv.base_unit_id
                JOIN products p ON p.id=pv.product_id
                LEFT JOIN order_items oi ON oi.id=t.order_item_id
                LEFT JOIN orders o ON o.id=oi.order_id
                WHERE pv.shop_id=?
                ORDER BY t.created_at DESC,t.id DESC
                """,
                (rs, rowNum) -> new InventoryBatchHistoryRow(
                        rs.getLong("id"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("type"),
                        rs.getInt("on_hand_delta"),
                        rs.getInt("reserved_delta"),
                        rs.getInt("on_hand_after"),
                        rs.getInt("reserved_after"),
                        rs.getString("reason"),
                        rs.getString("product_name"),
                        rs.getString("variant_name"),
                        rs.getString("sku"),
                        rs.getString("unit_name"),
                        rs.getString("batch_code"),
                        rs.getString("order_code")),
                shopId);
    }

    /**
     * Tiếp nhận yêu cầu nhập thêm hàng vào kho.
     * Kiểm tra các trường bắt buộc trước khi chuyển tiếp cho
     * InventoryBatchService thực hiện việc nhập lô.
     */
    @Transactional
    public void receiveBatch(InventoryBatchForm form, Long ownerUserId) {
        // Kiểm tra DTO và các trường bắt buộc trước khi xử lý.
        if (form == null || form.getInventoryId() == null
                || form.getQuantity() == null || form.getReceivedAt() == null) {
            throw new InventoryActionException(
                    "Vui lòng nhập đầy đủ thông tin lô hàng.");
        }

        // Chuyển thông tin lô hàng cho InventoryBatchService xử lý.
        batches.receive(
                form.getInventoryId(),
                form.getQuantity(),
                form.getReceivedAt(),
                form.getNote(),
                ownerUserId);
    }

    /**
     * Tiếp nhận yêu cầu điều chỉnh số lượng của một lô hàng.
     * Method hiện tại cố định hướng điều chỉnh là DECREASE (giảm kho),
     * sau đó chuyển nghiệp vụ chi tiết cho InventoryBatchService.
     */
    @Transactional
    public void adjustBatch(
            InventoryAdjustmentForm form, Long ownerUserId) {
        // Kiểm tra DTO, mã lô và số lượng có được cung cấp hay không.
        if (form == null || form.getBatchId() == null
                || form.getQuantity() == null) {
            throw new InventoryActionException(
                    "Vui lòng chọn batch và nhập số lượng.");
        }

        // Gọi Service xử lý điều chỉnh lô hàng theo hướng giảm số lượng.
        batches.adjust(
                form.getBatchId(),
                "DECREASE",
                form.getQuantity(),
                form.getReason(),
                ownerUserId);
    }

    /**
     * Lấy tên các danh mục của sản phẩm trong một cửa hàng.
     *
     * Kết quả là Map có:
     * - Key: productId.
     * - Value: danh sách tên danh mục của sản phẩm đó.
     */
    private Map<Long, List<String>> categories(Long shopId) {
        Map<Long, List<String>> result = new LinkedHashMap<>();

        // Truy vấn danh mục và chỉ lấy sản phẩm thuộc shopId được truyền vào.
        jdbc.query("""
                SELECT pc.product_id,c.name
                FROM product_categories pc
                JOIN categories c ON c.id=pc.category_id
                JOIN products p ON p.id=pc.product_id
                WHERE p.shop_id=?
                ORDER BY c.name
                """,
                (RowCallbackHandler) rs -> result
                        .computeIfAbsent(rs.getLong("product_id"),
                                ignored -> new ArrayList<>())
                        .add(rs.getString("name")),
                shopId);

        return result;
    }

    /**
     * Lấy cửa hàng theo ownerUserId.
     * Nếu userId không hợp lệ hoặc tài khoản chưa có cửa hàng,
     * ném InventoryActionException để dừng thao tác nghiệp vụ.
     */
    private Shop requireShop(Long ownerUserId) {
        if (ownerUserId == null) {
            throw new InventoryActionException(
                    "Phiên đăng nhập không hợp lệ.");
        }

        return shopRepository.findByOwnerId(ownerUserId)
                .orElseThrow(() -> new InventoryActionException(
                        "Tài khoản chưa có cửa hàng hợp lệ."));
    }

    /**
     * Đọc một giá trị số nguyên từ Map kết quả truy vấn.
     * Nếu giá trị null hoặc không phải Number thì trả về 0.
     */
    private static int number(Map<String, Object> row, String name) {
        Object value = row.get(name);
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * Đọc một giá trị kiểu long từ Map kết quả truy vấn.
     * Nếu giá trị null hoặc không phải Number thì trả về 0L.
     */
    private static long longNumber(Map<String, Object> row, String name) {
        Object value = row.get(name);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * Chuyển giá trị thời gian lấy từ JDBC thành LocalDateTime.
     * Hỗ trợ java.sql.Timestamp và LocalDateTime; kiểu khác trả về null.
     */
    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    /**
     * Lớp trung gian gom nhiều dòng SQL thành một sản phẩm.
     * Một sản phẩm có thể có nhiều biến thể, do đó cần gom theo productId
     * trước khi chuyển thành DTO trả về giao diện.
     */
    private static final class ProductBuilder {
        private final Long id;
        private final String name;
        private final String imageUrl;
        private final String categories;
        private final int sold;
        // Key là variantId, value là dữ liệu của biến thể tương ứng.
        private final Map<Long, VariantBuilder> variants = new LinkedHashMap<>();

        // Khởi tạo thông tin chung của sản phẩm.
        private ProductBuilder(
                Long id, String name, String imageUrl, String categories, int sold) {
            this.id = id;
            this.name = name;
            this.imageUrl = imageUrl;
            this.categories = categories;
            this.sold = sold;
        }

        /**
         * Chuyển ProductBuilder thành InventoryProductView.
         * Đồng thời tính tổng tồn thực tế và tổng số lượng đang được giữ
         * từ tất cả biến thể của sản phẩm.
         */
        private InventoryProductView toView() {
            // Chuyển từng VariantBuilder thành DTO của biến thể.
            List<InventoryVariantView> variantViews = variants.values()
                    .stream()
                    .map(VariantBuilder::toView)
                    .toList();

            // Tổng tồn kho thực tế của sản phẩm bằng tổng onHand của các biến thể.
            int onHand = variantViews.stream()
                    .mapToInt(InventoryVariantView::onHand)
                    .sum();
            // Tổng số lượng đang được giữ của tất cả biến thể.
            int reserved = variantViews.stream()
                    .mapToInt(InventoryVariantView::reserved)
                    .sum();

            // available của sản phẩm được tính bằng tồn thực tế trừ số lượng đã giữ.
            return new InventoryProductView(
                    id, name, imageUrl, categories,
                    onHand, reserved, onHand - reserved, sold, variantViews);
        }
    }

    /**
     * Lớp trung gian gom thông tin của một biến thể và các lô hàng trực thuộc.
     * Một biến thể có thể có nhiều lô nên dữ liệu lô được lưu trong danh sách batches.
     */
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
        private final int lowStockThresholdQuantity;

        // Danh sách các lô hàng thuộc biến thể này.
        private final List<InventoryBatchView> batches = new ArrayList<>();

        // Khởi tạo thông tin nhận diện biến thể và số liệu tồn kho.
        private VariantBuilder(
                Long inventoryId,
                Long id,
                String name,
                String sku,
                String imageUrl,
                String unitName,
                int onHand,
                int reserved,
                int available,
                int sold,
                int lowStockThresholdQuantity) {
            this.inventoryId = inventoryId;
            this.id = id;
            this.name = name;
            this.sku = sku;
            this.imageUrl = imageUrl;
            this.unitName = unitName;
            this.onHand = onHand;
            this.reserved = reserved;
            this.available = available;
            this.sold = sold;
            this.lowStockThresholdQuantity = lowStockThresholdQuantity;
        }

        /**
         * Chuyển thông tin biến thể cùng danh sách lô thành DTO
         * InventoryVariantView để hiển thị trên giao diện.
         */
        private InventoryVariantView toView() {
            return new InventoryVariantView(
                    inventoryId, id, name, sku, imageUrl, unitName,
                    onHand, reserved, available, sold,
                    lowStockThresholdQuantity, List.copyOf(batches));
        }
    }

    /**
     * Lấy danh sách biến thể cần cảnh báo tồn kho thấp.
     *
     * Điều kiện:
     * - Biến thể thuộc cửa hàng của ownerUserId.
     * - Ngưỡng cảnh báo lớn hơn 0.
     * - Số lượng khả dụng nhỏ hơn hoặc bằng ngưỡng cảnh báo.
     *
     * Kết quả được sắp xếp theo số lượng khả dụng tăng dần,
     * để mặt hàng sắp hết được hiển thị trước.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLowStockAlerts(Long ownerUserId) {
        // Truy vấn sản phẩm, biến thể, lượng hàng khả dụng và ngưỡng cảnh báo.
        return jdbc.queryForList("""
                SELECT vi.id AS inventory_id,
                       p.name AS product_name,
                       pv.name AS variant_name,
                       pv.sku,
                       vi.available_quantity,
                       u.name AS unit_name,
                       vi.low_stock_threshold_quantity AS threshold_quantity
                FROM variant_inventory vi
                JOIN product_variants pv ON pv.id=vi.variant_id
                JOIN products p ON p.id=pv.product_id
                JOIN units u ON u.id=pv.base_unit_id
                JOIN shops s ON s.id=pv.shop_id
                WHERE s.owner_id=?
                  AND vi.low_stock_threshold_quantity>0
                  AND vi.available_quantity<=vi.low_stock_threshold_quantity
                ORDER BY vi.available_quantity ASC,p.name,pv.name
                """, ownerUserId);
    }
}
