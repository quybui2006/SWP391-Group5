package service.shop;

import dto.operations.ShopReopenRequestItem;
import dto.shop.ShopReopenRequestHistory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import service.customer.NotificationService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ShopReopenRequestService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public ShopReopenRequestService(JdbcTemplate jdbc, NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    public Map<String, Object> getOwnerShopState(Long ownerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, name, approval_status, operating_status, suspension_reason
                FROM shops WHERE owner_id = ?
                """, ownerId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa tìm thấy cửa hàng.");
        return rows.get(0);
    }

    public List<ShopReopenRequestHistory> getOwnerHistory(Long ownerId) {
        Long shopId = ((Number) getOwnerShopState(ownerId).get("id")).longValue();
        return jdbc.query("""
                SELECT id, explanation, status, operation_reason, requested_at, reviewed_at
                FROM shop_reopen_requests WHERE shop_id = ? ORDER BY requested_at DESC, id DESC
                """, (rs, row) -> new ShopReopenRequestHistory(rs.getLong("id"), rs.getString("explanation"),
                rs.getString("status"), rs.getString("operation_reason"), dateTime(rs, "requested_at"),
                dateTime(rs, "reviewed_at")), shopId);
    }

    @Transactional
    public void submit(Long ownerId, String explanation) {
        String text = normalize(explanation, "Vui lòng nhập nội dung giải trình.");
        if (text.length() < 10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giải trình cần có ít nhất 10 ký tự.");
        Long shopId;
        try {
            shopId = jdbc.queryForObject("SELECT id FROM shops WHERE owner_id = ? FOR UPDATE", Long.class, ownerId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa tìm thấy cửa hàng.");
        }
        String status = jdbc.queryForObject("SELECT operating_status FROM shops WHERE id = ?", String.class, shopId);
        if (!"SUSPENDED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ Shop đang bị chặn mới có thể yêu cầu mở lại.");
        }
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM shop_reopen_requests WHERE shop_id = ? AND status = 'PENDING'", Integer.class, shopId);
        if (pending != null && pending > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop đã có yêu cầu mở lại đang chờ xử lý.");
        }
        jdbc.update("INSERT INTO shop_reopen_requests(shop_id, owner_id, explanation, status) VALUES (?, ?, ?, 'PENDING')",
                shopId, ownerId, text);
        notifications.createNotification(ownerId, "ACCOUNT", "Đã gửi yêu cầu mở lại Shop",
                "Operations sẽ xem xét phần giải trình của bạn.", "SHOP", shopId);
    }

    public List<ShopReopenRequestItem> findPending() {
        return jdbc.query("""
                SELECT r.id, r.shop_id, s.name AS shop_name, u.full_name AS owner_name, u.email AS owner_email,
                       r.explanation, r.status, r.operation_reason, r.requested_at, r.reviewed_at
                FROM shop_reopen_requests r
                JOIN shops s ON s.id = r.shop_id
                JOIN users u ON u.id = r.owner_id
                WHERE r.status = 'PENDING'
                ORDER BY r.requested_at ASC, r.id ASC
                """, (rs, row) -> mapOpsRequest(rs));
    }

    @Transactional
    public void approve(Long requestId) {
        Map<String, Object> request = lockPendingRequest(requestId);
        Long shopId = ((Number) request.get("shop_id")).longValue();
        Long ownerId = ((Number) request.get("owner_id")).longValue();
        int resumed = jdbc.update("""
                UPDATE shops SET operating_status = 'OPEN', suspension_reason = NULL
                WHERE id = ? AND approval_status = 'APPROVED' AND operating_status = 'SUSPENDED'
                """, shopId);
        if (resumed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop không còn ở trạng thái bị chặn.");
        int reviewed = jdbc.update("""
                UPDATE shop_reopen_requests SET status = 'APPROVED', operation_reason = NULL, reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PENDING'
                """, requestId);
        if (reviewed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã được xử lý trước đó.");
        notifications.createNotification(ownerId, "ACCOUNT", "Shop đã được mở lại",
                "Operations đã chấp thuận yêu cầu mở lại cửa hàng.", "SHOP", shopId);
    }

    @Transactional
    public void reject(Long requestId, String reason) {
        String normalized = normalize(reason, "Vui lòng nhập lý do từ chối yêu cầu.");
        Map<String, Object> request = lockPendingRequest(requestId);
        int reviewed = jdbc.update("""
                UPDATE shop_reopen_requests SET status = 'REJECTED', operation_reason = ?, reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PENDING'
                """, normalized, requestId);
        if (reviewed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã được xử lý trước đó.");
        Long shopId = ((Number) request.get("shop_id")).longValue();
        Long ownerId = ((Number) request.get("owner_id")).longValue();
        notifications.createNotification(ownerId, "ACCOUNT", "Yêu cầu mở lại Shop chưa được chấp thuận",
                "Lý do: " + normalized, "SHOP", shopId);
    }

    private Map<String, Object> lockPendingRequest(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, shop_id, owner_id FROM shop_reopen_requests WHERE id = ? AND status = 'PENDING' FOR UPDATE
                """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu không còn ở trạng thái chờ xử lý.");
        return rows.get(0);
    }

    private ShopReopenRequestItem mapOpsRequest(ResultSet rs) throws SQLException {
        return new ShopReopenRequestItem(rs.getLong("id"), rs.getLong("shop_id"), rs.getString("shop_name"),
                rs.getString("owner_name"), rs.getString("owner_email"), rs.getString("explanation"),
                rs.getString("status"), rs.getString("operation_reason"), dateTime(rs, "requested_at"),
                dateTime(rs, "reviewed_at"));
    }

    private String normalize(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        String text = value.trim();
        if (text.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung không được vượt quá 1000 ký tự.");
        return text;
    }

    private static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
