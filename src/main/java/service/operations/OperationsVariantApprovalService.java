package service.operations;

import dto.operations.OperationsVariantDetailDto;
import dto.operations.PendingVariantApprovalDto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Nghiệp vụ duyệt từng biến thể; hồ sơ Product cha không còn nằm trong hàng đợi duyệt. */
@Service
public class OperationsVariantApprovalService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;

    public OperationsVariantApprovalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PendingVariantApprovalDto> findPendingVariants() {
        return jdbc.query("""
                SELECT pv.id AS variant_id, pv.name AS variant_name, pv.sku,
                       pv.image_url AS image_url,
                       p.name AS product_name, s.name AS shop_name,
                       COALESCE((SELECT c.name FROM product_categories pc JOIN categories c ON c.id = pc.category_id
                                 WHERE pc.product_id = p.id ORDER BY c.name LIMIT 1), 'Chưa phân loại') AS category_name,
                       pv.price, u.name AS unit_name,
                       COALESCE(vi.quantity_on_hand, 0) AS quantity_on_hand,
                       COALESCE(vi.available_quantity, 0) AS available_quantity,
                       p.batch_code, p.expiry_date, pv.submitted_at
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.shop_id = pv.shop_id
                JOIN shops s ON s.id = pv.shop_id
                JOIN units u ON u.id = pv.base_unit_id
                LEFT JOIN variant_inventory vi ON vi.variant_id = pv.id
                WHERE pv.approval_status = 'PENDING'
                ORDER BY pv.submitted_at ASC, pv.id ASC
                """, (rs, rowNum) -> new PendingVariantApprovalDto(
                rs.getLong("variant_id"), rs.getString("variant_name"), rs.getString("sku"),
                rs.getString("image_url"), rs.getString("product_name"), rs.getString("shop_name"),
                rs.getString("category_name"), rs.getBigDecimal("price"), rs.getString("unit_name"),
                nullableInt(rs, "quantity_on_hand"), nullableInt(rs, "available_quantity"),
                rs.getString("batch_code"), localDate(rs, "expiry_date"), localDateTime(rs, "submitted_at")));
    }

    @Transactional(readOnly = true)
    public OperationsVariantDetailDto getVariantDetail(Long variantId) {
        List<OperationsVariantDetailDto> rows = jdbc.query("""
                SELECT pv.id AS variant_id, pv.product_id, pv.name AS variant_name, pv.sku,
                       pv.specification,
                       pv.image_url AS image_url,
                       pv.price, pv.min_order_quantity,
                       pv.status AS variant_status, pv.approval_status, pv.rejection_reason,
                       pv.submitted_at, pv.reviewed_at, pv.created_at AS variant_created_at, pv.updated_at AS variant_updated_at,
                       p.name AS product_name, p.description AS product_description, p.origin, p.batch_code,
                       p.received_date, p.expiry_date,
                       COALESCE((SELECT c.name FROM product_categories pc JOIN categories c ON c.id = pc.category_id
                                 WHERE pc.product_id = p.id ORDER BY c.name LIMIT 1), 'Chưa phân loại') AS category_name,
                       s.name AS shop_name, s.phone AS shop_phone,
                       CONCAT_WS(', ', s.address_detail, s.ward_name, s.province_code) AS shop_address,
                       u.name AS unit_name,
                       vi.initial_quantity, vi.quantity_on_hand, vi.reserved_quantity, vi.available_quantity,
                       vi.low_stock_threshold_pct, vi.status AS inventory_status
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.shop_id = pv.shop_id
                JOIN shops s ON s.id = pv.shop_id
                JOIN units u ON u.id = pv.base_unit_id
                LEFT JOIN variant_inventory vi ON vi.variant_id = pv.id
                WHERE pv.id = ?
                """, (rs, rowNum) -> new OperationsVariantDetailDto(
                rs.getLong("variant_id"), rs.getLong("product_id"), rs.getString("variant_name"),
                rs.getString("sku"), rs.getString("specification"), rs.getString("image_url"),
                rs.getBigDecimal("price"), nullableInt(rs, "min_order_quantity"), rs.getString("unit_name"),
                rs.getString("variant_status"), rs.getString("approval_status"), rs.getString("rejection_reason"),
                localDateTime(rs, "submitted_at"), localDateTime(rs, "reviewed_at"),
                localDateTime(rs, "variant_created_at"), localDateTime(rs, "variant_updated_at"),
                rs.getString("product_name"), rs.getString("product_description"), rs.getString("category_name"),
                rs.getString("origin"), rs.getString("batch_code"), localDate(rs, "received_date"), localDate(rs, "expiry_date"),
                rs.getString("shop_name"), rs.getString("shop_phone"), rs.getString("shop_address"),
                nullableInt(rs, "initial_quantity"), nullableInt(rs, "quantity_on_hand"),
                nullableInt(rs, "reserved_quantity"), nullableInt(rs, "available_quantity"),
                rs.getBigDecimal("low_stock_threshold_pct"), rs.getString("inventory_status")), variantId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể sản phẩm.");
        return rows.get(0);
    }

    @Transactional
    public void approveVariant(Long variantId) {
        requireVariant(variantId);
        int updated = jdbc.update("""
                UPDATE product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.shop_id = pv.shop_id
                   SET pv.approval_status = 'APPROVED', pv.status = 'ACTIVE',
                       pv.reviewed_at = ?, pv.rejection_reason = NULL
                 WHERE pv.id = ? AND pv.approval_status = 'PENDING'
                """, LocalDateTime.now(BUSINESS_ZONE), variantId);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Biến thể không còn ở trạng thái chờ duyệt. Hãy tải lại danh sách.");
    }

    @Transactional
    public void rejectVariant(Long variantId, String rejectionReason) {
        String reason = normalizeReason(rejectionReason);
        requireVariant(variantId);
        int updated = jdbc.update("""
                UPDATE product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.shop_id = pv.shop_id
                   SET pv.approval_status = 'REJECTED', pv.status = 'INACTIVE',
                       pv.reviewed_at = ?, pv.rejection_reason = ?
                 WHERE pv.id = ? AND pv.approval_status = 'PENDING'
                """, LocalDateTime.now(BUSINESS_ZONE), reason, variantId);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Biến thể không còn ở trạng thái chờ duyệt. Hãy tải lại danh sách.");
    }

    private void requireVariant(Long id) {
        Integer count = id == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_variants WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể sản phẩm.");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối.");
        String result = reason.trim();
        if (result.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do từ chối không được vượt quá 1000 ký tự.");
        return result;
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
