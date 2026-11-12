package service.operations;

import dto.operations.OperationsVariantDetailDto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Đọc catalog biến thể và xử lý thao tác chặn bán của Operations. */
@Service
public class OperationsVariantApprovalService {
    private final JdbcTemplate jdbc;

    public OperationsVariantApprovalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ghép thông tin shop/sản phẩm/biến thể/tồn kho để Operations xem và quyết định chặn bán. */
    @Transactional(readOnly = true)
    public OperationsVariantDetailDto getVariantDetail(Long variantId) {
        List<OperationsVariantDetailDto> rows = jdbc.query("""
                SELECT pv.id AS variant_id, pv.product_id, pv.shop_id, pv.name AS variant_name, pv.sku,
                       pv.specification,
                       pv.image_url AS image_url,
                       pv.price, pv.min_order_quantity,
                       CASE WHEN pv.operations_blocked=1 THEN 'BLOCKED' ELSE pv.status END AS variant_status,
                       pv.operations_blocked,pv.operations_block_reason,
                       pv.approval_status, pv.rejection_reason,
                       pv.submitted_at, pv.reviewed_at, pv.created_at AS variant_created_at, pv.updated_at AS variant_updated_at,
                       p.name AS product_name, p.description AS product_description, p.origin, p.batch_code,
                       COALESCE((SELECT c.name FROM product_categories pc JOIN categories c ON c.id = pc.category_id
                                 WHERE pc.product_id = p.id ORDER BY c.name LIMIT 1), 'Chưa phân loại') AS category_name,
                       s.name AS shop_name, s.phone AS shop_phone,
                       CONCAT_WS(', ', s.address_detail, s.ward_name, s.province_code) AS shop_address,
                       u.name AS unit_name,
                       vi.initial_quantity, vi.quantity_on_hand, vi.reserved_quantity, vi.available_quantity,
                       vi.low_stock_threshold_quantity, vi.status AS inventory_status
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.shop_id = pv.shop_id
                JOIN shops s ON s.id = pv.shop_id
                JOIN units u ON u.id = pv.base_unit_id
                LEFT JOIN variant_inventory vi ON vi.variant_id = pv.id
                WHERE pv.id = ?
                """, (rs, rowNum) -> new OperationsVariantDetailDto(
                rs.getLong("variant_id"), rs.getLong("product_id"), rs.getLong("shop_id"), rs.getString("variant_name"),
                rs.getString("sku"), rs.getString("specification"), rs.getString("image_url"),
                rs.getBigDecimal("price"), nullableInt(rs, "min_order_quantity"), rs.getString("unit_name"),
                rs.getString("variant_status"), rs.getBoolean("operations_blocked"),
                rs.getString("operations_block_reason"),
                rs.getString("approval_status"), rs.getString("rejection_reason"),
                localDateTime(rs, "submitted_at"), localDateTime(rs, "reviewed_at"),
                localDateTime(rs, "variant_created_at"), localDateTime(rs, "variant_updated_at"),
                rs.getString("product_name"), rs.getString("product_description"), rs.getString("category_name"),
                rs.getString("origin"), rs.getString("batch_code"),
                rs.getString("shop_name"), rs.getString("shop_phone"), rs.getString("shop_address"),
                nullableInt(rs, "initial_quantity"), nullableInt(rs, "quantity_on_hand"),
                nullableInt(rs, "reserved_quantity"), nullableInt(rs, "available_quantity"),
                nullableInt(rs, "low_stock_threshold_quantity"), rs.getString("inventory_status")), variantId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể sản phẩm.");
        return rows.get(0);
    }

    /** Ghi lý do và khóa trạng thái bán của biến thể; không thay đổi dữ liệu tồn kho hay trạng thái duyệt. */
    @Transactional
    public void blockVariant(Long variantId, String reason) {
        String cleanReason = normalizeReason(reason);
        requireVariant(variantId);
        Boolean alreadyBlocked = jdbc.queryForObject(
                "SELECT operations_blocked FROM product_variants WHERE id=? FOR UPDATE",
                Boolean.class, variantId);
        if (Boolean.TRUE.equals(alreadyBlocked)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Biến thể đã bị chặn bán.");
        }
        int updated = jdbc.update("""
                UPDATE product_variants
                   SET operations_blocked=1,operations_block_reason=?,
                       operations_blocked_at=CURRENT_TIMESTAMP(3),status='INACTIVE'
                 WHERE id=? AND operations_blocked=0
                """, cleanReason, variantId);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Trạng thái biến thể vừa thay đổi. Tải lại trang.");
    }

    /** Gỡ cờ chặn của Operations; không tự mở bán nếu biến thể vẫn đang INACTIVE. */
    @Transactional
    public void unblockVariant(Long variantId) {
        requireVariant(variantId);
        int updated = jdbc.update("""
                UPDATE product_variants
                   SET operations_blocked=0,operations_block_reason=NULL
                 WHERE id=? AND operations_blocked=1
                """, variantId);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Biến thể chưa bị chặn bán.");
    }

    private void requireVariant(Long id) {
        Integer count = id == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_variants WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể sản phẩm.");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do chặn bán.");
        String result = reason.trim();
        if (result.length() < 5 || result.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do chặn bán phải từ 5 đến 1000 ký tự.");
        return result;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
