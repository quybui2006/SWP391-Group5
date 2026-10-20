package service.operations;

import dto.operations.ManagedShopListItem;
import dto.operations.ManagedShopVariant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import service.customer.NotificationService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
                       p.batch_code, p.expiry_date
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.shop_id = pv.shop_id
                JOIN units u ON u.id = pv.base_unit_id
                LEFT JOIN variant_inventory vi ON vi.variant_id = pv.id
                WHERE pv.shop_id = ?
                ORDER BY p.name, pv.name, pv.id
                """, (rs, row) -> new ManagedShopVariant(
                rs.getLong("variant_id"), rs.getString("product_name"), rs.getString("variant_name"),
                rs.getString("sku"), rs.getString("image_url"), rs.getBigDecimal("price"),
                rs.getString("unit_name"), rs.getString("variant_status"), rs.getString("approval_status"),
                nullableInt(rs, "quantity_on_hand"), nullableInt(rs, "available_quantity"),
                rs.getString("batch_code"), localDate(rs, "expiry_date")), shopId);
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

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
