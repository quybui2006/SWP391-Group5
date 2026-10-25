package service.complaint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ComplaintService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter CODE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final Map<String, String> REASON_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> RESOLUTION_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> PRIORITY_LABELS = new LinkedHashMap<>();

    static {
        REASON_LABELS.put("QUALITY_ISSUE", "Trái cây dập nát / hư hỏng / không tươi");
        REASON_LABELS.put("WEIGHT_OR_QUANTITY_SHORTAGE", "Giao thiếu số lượng / thiếu cân");
        REASON_LABELS.put("WRONG_SPECIFICATION", "Sai chủng loại / sai kích cỡ (Biến thể)");
        REASON_LABELS.put("PACKAGING_DAMAGE", "Quy cách đóng gói / bảo quản kém");
        REASON_LABELS.put("DELIVERY_OR_SERVICE", "Sự cố giao hàng / thái độ phục vụ");
        REASON_LABELS.put("OTHER", "Lý do khác");

        RESOLUTION_LABELS.put("REPLACEMENT_OR_RESEND", "Yêu cầu Shop giao bù / đổi sản phẩm mới");
        RESOLUTION_LABELS.put("COMPENSATE_POINTS", "Đền bù Điểm thưởng tích lũy FreshFruit");
        RESOLUTION_LABELS.put("SHOP_WARNING_ESCALATE", "Cảnh cáo Shop & Chuyển Hậu kiểm Vận hành");
        RESOLUTION_LABELS.put("EXPLANATION_ONLY", "Giải thích / Hòa giải & Đóng hồ sơ");
        RESOLUTION_LABELS.put("REJECTED", "Từ chối khiếu nại (Không đủ căn cứ)");

        STATUS_LABELS.put("PENDING", "Chờ tiếp nhận");
        STATUS_LABELS.put("IN_PROGRESS", "Đang xử lý");
        STATUS_LABELS.put("RESOLVED", "Đã giải quyết");
        STATUS_LABELS.put("REJECTED", "Đã từ chối");
        STATUS_LABELS.put("CANCELLED", "Đã hủy");

        PRIORITY_LABELS.put("HIGH", "Khẩn cấp (12h)");
        PRIORITY_LABELS.put("NORMAL", "Tiêu chuẩn (24h)");
        PRIORITY_LABELS.put("LOW", "Thấp (48h)");
    }

    private final JdbcTemplate jdbc;
    private final Path uploadDirectory;

    public ComplaintService(
            JdbcTemplate jdbc,
            @Value("${app.upload.complaint-dir:uploads/complaints}") String uploadDir) {
        this.jdbc = jdbc;
        this.uploadDirectory = Path.of(uploadDir).toAbsolutePath().normalize();
        ensureSchemaSafe();
    }

    private void ensureSchemaSafe() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS complaints (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    complaint_code VARCHAR(40) NOT NULL UNIQUE,
                    order_id BIGINT NOT NULL,
                    order_item_id BIGINT NULL,
                    user_id BIGINT NOT NULL,
                    shop_id BIGINT NOT NULL,
                    reason_type VARCHAR(40) NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    description VARCHAR(2000) NOT NULL,
                    evidence_image_url VARCHAR(1000) NULL,
                    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
                    status VARCHAR(25) NOT NULL DEFAULT 'PENDING',
                    source_type VARCHAR(25) NOT NULL DEFAULT 'CUSTOMER',
                    shop_response VARCHAR(1500) NULL,
                    shop_responded_at DATETIME(3) NULL,
                    resolution_type VARCHAR(40) NULL,
                    resolution_note VARCHAR(1500) NULL,
                    compensated_points BIGINT NOT NULL DEFAULT 0,
                    is_shop_fault TINYINT(1) NOT NULL DEFAULT 0,
                    escalated_to_ops TINYINT(1) NOT NULL DEFAULT 0,
                    sla_due_at DATETIME(3) NOT NULL,
                    assigned_to BIGINT NULL,
                    resolved_by BIGINT NULL,
                    resolved_at DATETIME(3) NULL,
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
                )
            """);
        } catch (Exception e1) {
            try {
                jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS complaints (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        complaint_code VARCHAR(40) NOT NULL UNIQUE,
                        order_id BIGINT NOT NULL,
                        order_item_id BIGINT NULL,
                        user_id BIGINT NOT NULL,
                        shop_id BIGINT NOT NULL,
                        reason_type VARCHAR(40) NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        description VARCHAR(2000) NOT NULL,
                        evidence_image_url VARCHAR(1000) NULL,
                        priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
                        status VARCHAR(25) NOT NULL DEFAULT 'PENDING',
                        source_type VARCHAR(25) NOT NULL DEFAULT 'CUSTOMER',
                        shop_response VARCHAR(1500) NULL,
                        shop_responded_at TIMESTAMP NULL,
                        resolution_type VARCHAR(40) NULL,
                        resolution_note VARCHAR(1500) NULL,
                        compensated_points BIGINT NOT NULL DEFAULT 0,
                        is_shop_fault TINYINT NOT NULL DEFAULT 0,
                        escalated_to_ops TINYINT NOT NULL DEFAULT 0,
                        sla_due_at TIMESTAMP NOT NULL,
                        assigned_to BIGINT NULL,
                        resolved_by BIGINT NULL,
                        resolved_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """);
            } catch (Exception ignored) {
            }
        }
    }

    public Map<String, String> getReasonLabels() {
        return REASON_LABELS;
    }

    public Map<String, String> getResolutionLabels() {
        return RESOLUTION_LABELS;
    }

    // =========================================================================
    // 1. GỬI YÊU CẦU KHIẾU NẠI (CUSTOMER & CS MANUAL / AUTO-ASSIST)
    // =========================================================================

    @Transactional
    public Long submitCustomerComplaint(
            Long customerId,
            Long orderId,
            Long orderItemId,
            String reasonType,
            String title,
            String description,
            MultipartFile evidenceImage) {

        if (customerId == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để gửi khiếu nại.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("Không xác định được đơn hàng cần khiếu nại.");
        }
        String cleanReason = (reasonType == null || !REASON_LABELS.containsKey(reasonType.trim()))
                ? "QUALITY_ISSUE" : reasonType.trim();

        String cleanTitle = title != null ? title.trim() : "";
        if (cleanTitle.isEmpty()) {
            cleanTitle = REASON_LABELS.get(cleanReason);
        }
        if (cleanTitle.length() > 200) {
            throw new IllegalArgumentException("Tiêu đề khiếu nại không được vượt quá 200 ký tự.");
        }

        String cleanDesc = description != null ? description.trim() : "";
        if (cleanDesc.length() < 5 || cleanDesc.length() > 2000) {
            throw new IllegalArgumentException("Vui lòng mô tả chi tiết tình trạng (từ 5 đến 2000 ký tự).");
        }

        // Kiểm tra đơn hàng thuộc về khách hàng
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT o.id, o.order_code, o.shop_id, o.status, o.delivery_status, o.total_amount,
                   s.name AS shop_name, s.owner_id AS shop_owner_id
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            JOIN shops s ON s.id = o.shop_id
            WHERE o.id = ? AND c.user_id = ?
        """, orderId, customerId);

        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng hoặc bạn không có quyền khiếu nại đơn hàng này.");
        }
        Map<String, Object> order = orders.get(0);
        String orderStatus = String.valueOf(order.get("status"));
        if ("PENDING".equals(orderStatus) || "CONFIRMED".equals(orderStatus) || "PREPARING".equals(orderStatus)) {
            throw new IllegalArgumentException("Đơn hàng chưa được giao. Bạn chỉ có thể gửi khiếu nại khi đơn đang giao, đã giao, hoàn tất hoặc gặp sự cố.");
        }

        // Kiểm tra trùng khiếu nại đang mở trên cùng đơn hàng
        Long activeCount = jdbc.queryForObject("""
            SELECT COUNT(*) FROM complaints
            WHERE order_id = ? AND user_id = ? AND status IN ('PENDING', 'IN_PROGRESS')
        """, Long.class, orderId, customerId);
        if (activeCount != null && activeCount > 0) {
            throw new IllegalArgumentException("Đơn hàng này đã có một yêu cầu khiếu nại đang được xử lý.");
        }

        // Kiểm tra orderItemId nếu có truyền vào
        Long validItemId = null;
        if (orderItemId != null) {
            List<Long> itemIds = jdbc.queryForList(
                    "SELECT id FROM order_items WHERE id = ? AND order_id = ?",
                    Long.class, orderItemId, orderId);
            if (!itemIds.isEmpty()) {
                validItemId = itemIds.get(0);
            }
        }

        String imageUrl = storeEvidenceImageIfPresent(evidenceImage);
        Long shopId = ((Number) order.get("shop_id")).longValue();
        BigDecimal totalAmount = order.get("total_amount") != null
                ? new BigDecimal(order.get("total_amount").toString()) : BigDecimal.ZERO;

        // Tự động gán Priority & Thời hạn xử lý theo quy tắc ngành hàng tươi sống
        String priority = determineAutoPriority(cleanReason, totalAmount);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slaDueAt = "HIGH".equals(priority) ? now.plusHours(12) : now.plusHours(24);
        String complaintCode = generateComplaintCode(now);

        final String finalTitle = cleanTitle;
        final Long finalItemId = validItemId;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO complaints (
                    complaint_code, order_id, order_item_id, user_id, shop_id,
                    reason_type, title, description, evidence_image_url,
                    priority, status, source_type, sla_due_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'CUSTOMER', ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, complaintCode);
            ps.setLong(2, orderId);
            if (finalItemId != null) ps.setLong(3, finalItemId); else ps.setNull(3, java.sql.Types.BIGINT);
            ps.setLong(4, customerId);
            ps.setLong(5, shopId);
            ps.setString(6, cleanReason);
            ps.setString(7, finalTitle);
            ps.setString(8, cleanDesc);
            ps.setString(9, imageUrl);
            ps.setString(10, priority);
            ps.setTimestamp(11, Timestamp.valueOf(slaDueAt));
            ps.setTimestamp(12, Timestamp.valueOf(now));
            ps.setTimestamp(13, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);

        Long complaintId = extractKey(keyHolder, complaintCode);
        String orderCode = String.valueOf(order.get("order_code"));
        Long shopOwnerId = order.get("shop_owner_id") instanceof Number n ? n.longValue() : null;

        // Gửi thông báo cho Khách hàng, Chủ Shop và Nhân viên CSKH
        createNotificationSafe(customerId,
                "COMPLAINT_CREATED_CUST_" + complaintId,
                "COMPLAINT",
                "Đã tiếp nhận khiếu nại " + complaintCode,
                "Yêu cầu khiếu nại cho đơn hàng " + orderCode + " đã được ghi nhận. Dự kiến phản hồi trước " + slaDueAt.format(DT_FMT) + ".",
                "COMPLAINT", complaintId);

        if (shopOwnerId != null) {
            createNotificationSafe(shopOwnerId,
                    "COMPLAINT_CREATED_SHOP_" + complaintId,
                    "COMPLAINT",
                    "Đơn hàng " + orderCode + " có khiếu nại mới (" + complaintCode + ")",
                    "Khách hàng vừa gửi khiếu nại: '" + finalTitle + "'. Vui lòng kiểm tra chi tiết đơn hàng và gửi giải trình để CSKH đối chiếu.",
                    "ORDER", orderId);
        }

        notifyRoleUsers("CS_STAFF",
                "COMPLAINT_NEW_CS_" + complaintId,
                "COMPLAINT",
                ("HIGH".equals(priority) ? "[KHẨN CẤP] " : "") + "Khiếu nại mới " + complaintCode + " - Đơn " + orderCode,
                "Lý do: " + REASON_LABELS.get(cleanReason) + ". Hạn xử lý: " + slaDueAt.format(DT_FMT) + ".",
                "COMPLAINT", complaintId);

        return complaintId;
    }

    /**
     * CSKH tạo phiếu hỗ trợ/khiếu nại thủ công hoặc tự động 1-click từ đơn hàng gặp sự cố / đánh giá 1-2 sao.
     */
    @Transactional
    public Long createTicketByCs(
            Long csUserId,
            String orderCodeInput,
            String reasonType,
            String title,
            String description,
            String sourceType) {

        if (orderCodeInput == null || orderCodeInput.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập Mã đơn hàng (ví dụ: FF-...).");
        }
        String cleanCode = orderCodeInput.trim();
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT o.id, o.order_code, o.shop_id, o.total_amount, c.user_id,
                   s.owner_id AS shop_owner_id
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            JOIN shops s ON s.id = o.shop_id
            WHERE o.order_code = ?
        """, cleanCode);

        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng có mã: " + cleanCode);
        }
        Map<String, Object> order = orders.get(0);
        Long orderId = ((Number) order.get("id")).longValue();
        Long customerId = order.get("user_id") instanceof Number n ? n.longValue() : csUserId;
        Long shopId = ((Number) order.get("shop_id")).longValue();

        Long activeCount = jdbc.queryForObject("""
            SELECT COUNT(*) FROM complaints
            WHERE order_id = ? AND status IN ('PENDING', 'IN_PROGRESS')
        """, Long.class, orderId);
        if (activeCount != null && activeCount > 0) {
            throw new IllegalArgumentException("Đơn hàng " + cleanCode + " đã có phiếu khiếu nại đang mở.");
        }

        String cleanReason = (reasonType == null || !REASON_LABELS.containsKey(reasonType.trim()))
                ? "DELIVERY_OR_SERVICE" : reasonType.trim();
        String cleanTitle = (title == null || title.isBlank())
                ? REASON_LABELS.get(cleanReason) : title.trim();
        String cleanDesc = (description == null || description.trim().length() < 5)
                ? "Phiếu hỗ trợ được khởi tạo bởi bộ phận CSKH cho đơn hàng " + cleanCode + "."
                : description.trim();
        String cleanSource = "AUTO_SYSTEM".equals(sourceType) ? "AUTO_SYSTEM" : "CS_MANUAL";

        BigDecimal totalAmount = order.get("total_amount") != null
                ? new BigDecimal(order.get("total_amount").toString()) : BigDecimal.ZERO;
        String priority = determineAutoPriority(cleanReason, totalAmount);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slaDueAt = "HIGH".equals(priority) ? now.plusHours(12) : now.plusHours(24);
        String complaintCode = generateComplaintCode(now);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO complaints (
                    complaint_code, order_id, user_id, shop_id,
                    reason_type, title, description,
                    priority, status, source_type, assigned_to, sla_due_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, complaintCode);
            ps.setLong(2, orderId);
            ps.setLong(3, customerId);
            ps.setLong(4, shopId);
            ps.setString(5, cleanReason);
            ps.setString(6, cleanTitle);
            ps.setString(7, cleanDesc);
            ps.setString(8, priority);
            ps.setString(9, cleanSource);
            if (csUserId != null) ps.setLong(10, csUserId); else ps.setNull(10, java.sql.Types.BIGINT);
            ps.setTimestamp(11, Timestamp.valueOf(slaDueAt));
            ps.setTimestamp(12, Timestamp.valueOf(now));
            ps.setTimestamp(13, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);

        Long complaintId = extractKey(keyHolder, complaintCode);
        if (customerId != null) {
            createNotificationSafe(customerId,
                    "COMPLAINT_CS_CREATED_" + complaintId,
                    "COMPLAINT",
                    "CSKH đã mở phiếu hỗ trợ " + complaintCode + " cho đơn " + cleanCode,
                    "Bộ phận CSKH đang kiểm tra và hỗ trợ sự cố trên đơn hàng " + cleanCode + " của bạn.",
                    "COMPLAINT", complaintId);
        }
        return complaintId;
    }

    /**
     * Chủ Shop gửi phản hồi / giải trình trên đơn hàng có khiếu nại (Mô hình tranh chấp 3 bên).
     */
    @Transactional
    public void submitShopResponse(Long ownerId, Long complaintId, String shopResponse) {
        if (shopResponse == null || shopResponse.trim().length() < 5 || shopResponse.trim().length() > 1500) {
            throw new IllegalArgumentException("Nội dung giải trình của Shop cần từ 5 đến 1500 ký tự.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.id, c.complaint_code, c.order_id, c.status
            FROM complaints c
            JOIN shops s ON s.id = c.shop_id
            WHERE c.id = ? AND s.owner_id = ?
        """, complaintId, ownerId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền phản hồi khiếu nại này.");
        }
        String status = String.valueOf(rows.get(0).get("status"));
        if (!"PENDING".equals(status) && !"IN_PROGRESS".equals(status)) {
            throw new IllegalArgumentException("Khiếu nại này đã đóng, không thể cập nhật giải trình.");
        }

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            UPDATE complaints
            SET shop_response = ?, shop_responded_at = ?, updated_at = ?
            WHERE id = ?
        """, shopResponse.trim(), Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

        String code = String.valueOf(rows.get(0).get("complaint_code"));
        notifyRoleUsers("CS_STAFF",
                "COMPLAINT_SHOP_REPLY_" + complaintId + "_" + System.currentTimeMillis(),
                "COMPLAINT",
                "Shop đã gửi giải trình cho khiếu nại " + code,
                "Chủ shop vừa phản hồi trên phiếu " + code + ". Vui lòng kiểm tra để đưa ra quyết định xử lý.",
                "COMPLAINT", complaintId);
    }

    // =========================================================================
    // 2. DANH SÁCH YÊU CẦU KHIẾU NẠI (CUSTOMER, CS_STAFF, SHOP_OWNER)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCustomerComplaints(Long customerId, String statusFilter) {
        StringBuilder sql = new StringBuilder("""
            SELECT c.*, o.order_code, o.total_amount, o.status AS order_status,
                   s.name AS shop_name,
                   oi.product_name AS item_product_name, oi.variant_name AS item_variant_name
            FROM complaints c
            JOIN orders o ON o.id = c.order_id
            JOIN shops s ON s.id = c.shop_id
            LEFT JOIN order_items oi ON oi.id = c.order_item_id
            WHERE c.user_id = ?
        """);
        List<Object> params = new ArrayList<>();
        params.add(customerId);

        if (statusFilter != null && !statusFilter.isBlank() && !"all".equalsIgnoreCase(statusFilter)) {
            sql.append(" AND c.status = ? ");
            params.add(statusFilter.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY c.created_at DESC, c.id DESC");

        List<Map<String, Object>> list = safeQueryList(sql.toString(), params.toArray());
        return enrichComplaintRows(list);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countCustomerComplaintsByStatus(Long customerId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ?", customerId));
        counts.put("pending", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'PENDING'", customerId));
        counts.put("in_progress", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'IN_PROGRESS'", customerId));
        counts.put("resolved", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'RESOLVED'", customerId));
        counts.put("rejected", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'REJECTED'", customerId));
        return counts;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCsComplaints(
            String statusFilter,
            String priorityFilter,
            String reasonFilter,
            Boolean overdueOnly,
            String keyword) {

        StringBuilder sql = new StringBuilder("""
            SELECT c.*, o.order_code, o.total_amount, o.status AS order_status, o.delivery_status,
                   u.full_name AS customer_name, u.phone AS customer_phone, u.email AS customer_email,
                   s.name AS shop_name, s.phone AS shop_phone,
                   oi.product_name AS item_product_name, oi.variant_name AS item_variant_name,
                   staff.full_name AS resolved_by_name
            FROM complaints c
            JOIN orders o ON o.id = c.order_id
            JOIN users u ON u.id = c.user_id
            JOIN shops s ON s.id = c.shop_id
            LEFT JOIN order_items oi ON oi.id = c.order_item_id
            LEFT JOIN users staff ON staff.id = COALESCE(c.resolved_by, c.assigned_to)
            WHERE 1=1
        """);
        List<Object> params = new ArrayList<>();

        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            if ("ACTIVE".equalsIgnoreCase(statusFilter)) {
                sql.append(" AND c.status IN ('PENDING', 'IN_PROGRESS') ");
            } else {
                sql.append(" AND c.status = ? ");
                params.add(statusFilter.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (priorityFilter != null && !priorityFilter.isBlank() && !"ALL".equalsIgnoreCase(priorityFilter)) {
            sql.append(" AND c.priority = ? ");
            params.add(priorityFilter.trim().toUpperCase(Locale.ROOT));
        }
        if (reasonFilter != null && !reasonFilter.isBlank() && !"ALL".equalsIgnoreCase(reasonFilter)) {
            sql.append(" AND c.reason_type = ? ");
            params.add(reasonFilter.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            sql.append("""
                AND (c.complaint_code LIKE ?
                     OR o.order_code LIKE ?
                     OR u.full_name LIKE ?
                     OR u.phone LIKE ?
                     OR s.name LIKE ?
                     OR c.title LIKE ?)
            """);
            Collections.addAll(params, kw, kw, kw, kw, kw, kw);
        }

        sql.append("""
            ORDER BY
                CASE WHEN c.status IN ('PENDING', 'IN_PROGRESS') THEN 0 ELSE 1 END ASC,
                CASE WHEN c.priority = 'HIGH' THEN 0 ELSE 1 END ASC,
                c.sla_due_at ASC,
                c.created_at DESC
        """);

        List<Map<String, Object>> rows = enrichComplaintRows(safeQueryList(sql.toString(), params.toArray()));
        if (Boolean.TRUE.equals(overdueOnly)) {
            rows.removeIf(r -> !"OVERDUE".equals(r.get("sla_state")) && !"BREACHED_SLA".equals(r.get("sla_state")));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listComplaintsByOrder(Long orderId) {
        if (orderId == null) return List.of();
        List<Map<String, Object>> rows = safeQueryList("""
            SELECT c.*, o.order_code, s.name AS shop_name,
                   oi.product_name AS item_product_name, oi.variant_name AS item_variant_name
            FROM complaints c
            JOIN orders o ON o.id = c.order_id
            JOIN shops s ON s.id = c.shop_id
            LEFT JOIN order_items oi ON oi.id = c.order_item_id
            WHERE c.order_id = ?
            ORDER BY c.created_at DESC, c.id DESC
        """, orderId);
        return enrichComplaintRows(rows);
    }

    // =========================================================================
    // 3. XEM CHI TIẾT VÀ XỬ LÝ KHIẾU NẠI
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerComplaintDetail(Long customerId, Long complaintId) {
        Map<String, Object> detail = getComplaintDetail(complaintId);
        Long ownerUserId = detail.get("user_id") instanceof Number n ? n.longValue() : null;
        if (!Objects.equals(customerId, ownerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem khiếu nại này.");
        }
        return detail;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getComplaintDetail(Long complaintId) {
        List<Map<String, Object>> rows = safeQueryList("""
            SELECT c.*,
                   o.order_code, o.total_amount, o.subtotal, o.shipping_fee, o.status AS order_status,
                   o.payment_method, o.payment_status, o.delivery_status, o.delivery_code,
                   o.recipient_name, o.recipient_phone, o.delivery_address, o.delivery_ward_name, o.delivery_province_name,
                   o.created_at AS order_created_at, o.delivered_at AS order_delivered_at, o.completed_at AS order_completed_at,
                   u.full_name AS customer_name, u.email AS customer_email, u.phone AS customer_phone, u.points_balance AS customer_points,
                   s.name AS shop_name, s.phone AS shop_phone, s.operating_status AS shop_operating_status, s.owner_id AS shop_owner_id,
                   owner.full_name AS shop_owner_name,
                   oi.product_name AS item_product_name, oi.variant_name AS item_variant_name, oi.sku AS item_sku,
                   oi.quantity AS item_quantity, oi.line_total AS item_line_total,
                   staff.full_name AS resolved_by_name, assignee.full_name AS assigned_to_name
            FROM complaints c
            JOIN orders o ON o.id = c.order_id
            JOIN users u ON u.id = c.user_id
            JOIN shops s ON s.id = c.shop_id
            JOIN users owner ON owner.id = s.owner_id
            LEFT JOIN order_items oi ON oi.id = c.order_item_id
            LEFT JOIN users staff ON staff.id = c.resolved_by
            LEFT JOIN users assignee ON assignee.id = c.assigned_to
            WHERE c.id = ?
        """, complaintId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu khiếu nại #" + complaintId);
        }

        Map<String, Object> detail = enrichSingleComplaintRow(rows.get(0));
        Long orderId = ((Number) detail.get("order_id")).longValue();
        Long shopId = ((Number) detail.get("shop_id")).longValue();

        // Danh sách sản phẩm trong đơn hàng
        List<Map<String, Object>> orderItems = safeQueryList("""
            SELECT id, variant_id, product_name, variant_name, sku, image_url,
                   base_unit_name, quantity, unit_price, sale_unit_price, line_total
            FROM order_items
            WHERE order_id = ?
            ORDER BY id
        """, orderId);
        detail.put("orderItems", orderItems);

        // Lịch sử vi phạm của Shop này để hỗ trợ CSKH ra quyết định
        long shopTotalComplaints = queryCount("SELECT COUNT(*) FROM complaints WHERE shop_id = ?", shopId);
        long shopFaultComplaints = queryCount("SELECT COUNT(*) FROM complaints WHERE shop_id = ? AND is_shop_fault = 1", shopId);
        long shopLowReviews = queryCount("""
            SELECT COUNT(*) FROM reviews r
            JOIN product_variants pv ON pv.id = r.variant_id
            WHERE pv.shop_id = ? AND r.rating <= 2
        """, shopId);

        detail.put("shopTotalComplaints", shopTotalComplaints);
        detail.put("shopFaultComplaints", shopFaultComplaints);
        detail.put("shopLowReviews", shopLowReviews);
        detail.put("shopHighRisk", shopFaultComplaints >= 2 || shopLowReviews >= 2);

        return detail;
    }

    /**
     * Nhân viên CSKH tiếp nhận xử lý phiếu (chuyển PENDING -> IN_PROGRESS).
     */
    @Transactional
    public void startProcessing(Long complaintId, Long csUserId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, complaint_code, user_id, order_id, status FROM complaints WHERE id = ?", complaintId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy yêu cầu khiếu nại.");
        }
        Map<String, Object> c = rows.get(0);
        String status = String.valueOf(c.get("status"));
        if (!"PENDING".equals(status)) {
            throw new IllegalArgumentException("Chỉ có thể tiếp nhận phiếu đang ở trạng thái Chờ tiếp nhận.");
        }

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            UPDATE complaints
            SET status = 'IN_PROGRESS', assigned_to = ?, updated_at = ?
            WHERE id = ?
        """, csUserId, Timestamp.valueOf(now), complaintId);

        Long customerId = c.get("user_id") instanceof Number n ? n.longValue() : null;
        String code = String.valueOf(c.get("complaint_code"));
        if (customerId != null) {
            createNotificationSafe(customerId,
                    "COMPLAINT_IN_PROGRESS_" + complaintId,
                    "COMPLAINT",
                    "Khiếu nại " + code + " đang được xử lý",
                    "Nhân viên Chăm sóc khách hàng đã tiếp nhận hồ sơ khiếu nại " + code + " của bạn và đang xác minh với Cửa hàng.",
                    "COMPLAINT", complaintId);
        }
    }

    /**
     * Nhân viên CSKH hoàn tất xử lý khiếu nại (RESOLVED hoặc REJECTED).
     */
    @Transactional
    public void resolveComplaint(
            Long complaintId,
            Long csUserId,
            String decisionStatus,
            String resolutionType,
            String resolutionNote,
            Long compensatedPoints,
            boolean isShopFault,
            boolean escalateToOps) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.*, o.order_code, s.name AS shop_name, s.owner_id AS shop_owner_id
            FROM complaints c
            JOIN orders o ON o.id = c.order_id
            JOIN shops s ON s.id = c.shop_id
            WHERE c.id = ?
        """, complaintId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy phiếu khiếu nại #" + complaintId);
        }
        Map<String, Object> c = rows.get(0);
        String currentStatus = String.valueOf(c.get("status"));
        if ("RESOLVED".equals(currentStatus) || "REJECTED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
            throw new IllegalArgumentException("Phiếu khiếu nại này đã được đóng trước đó.");
        }

        String finalStatus = "REJECTED".equalsIgnoreCase(decisionStatus) ? "REJECTED" : "RESOLVED";
        String cleanNote = resolutionNote != null ? resolutionNote.trim() : "";
        if (cleanNote.length() < 5 || cleanNote.length() > 1500) {
            throw new IllegalArgumentException("Vui lòng nhập kết luận / phản hồi xử lý từ 5 đến 1500 ký tự.");
        }

        String finalResType;
        if ("REJECTED".equals(finalStatus)) {
            finalResType = "REJECTED";
        } else {
            finalResType = (resolutionType != null && RESOLUTION_LABELS.containsKey(resolutionType))
                    ? resolutionType : "REPLACEMENT_OR_RESEND";
        }

        long pointsToCredit = 0L;
        if ("RESOLVED".equals(finalStatus) && "COMPENSATE_POINTS".equals(finalResType)) {
            pointsToCredit = (compensatedPoints != null && compensatedPoints > 0) ? compensatedPoints : 10000L;
            if (pointsToCredit > 500000L) {
                throw new IllegalArgumentException("Số điểm thưởng đền bù tối đa cho một lần xử lý là 500,000 điểm.");
            }
        }

        boolean finalShopFault = "RESOLVED".equals(finalStatus) && (isShopFault || "SHOP_WARNING_ESCALATE".equals(finalResType));
        boolean finalEscalate = finalShopFault && (escalateToOps || "SHOP_WARNING_ESCALATE".equals(finalResType));

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            UPDATE complaints
            SET status = ?,
                resolution_type = ?,
                resolution_note = ?,
                compensated_points = ?,
                is_shop_fault = ?,
                escalated_to_ops = ?,
                assigned_to = COALESCE(assigned_to, ?),
                resolved_by = ?,
                resolved_at = ?,
                updated_at = ?
            WHERE id = ?
        """,
                finalStatus,
                finalResType,
                cleanNote,
                pointsToCredit,
                finalShopFault ? 1 : 0,
                finalEscalate ? 1 : 0,
                csUserId,
                csUserId,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                complaintId);

        Long customerId = ((Number) c.get("user_id")).longValue();
        Long shopId = ((Number) c.get("shop_id")).longValue();
        Long orderId = ((Number) c.get("order_id")).longValue();
        Long shopOwnerId = c.get("shop_owner_id") instanceof Number n ? n.longValue() : null;
        String complaintCode = String.valueOf(c.get("complaint_code"));
        String orderCode = String.valueOf(c.get("order_code"));
        String shopName = String.valueOf(c.get("shop_name"));

        // Cộng điểm thưởng thiện chí vào tài khoản người mua nếu áp dụng COMPENSATE_POINTS
        if (pointsToCredit > 0) {
            try {
                jdbc.update("UPDATE users SET points_balance = COALESCE(points_balance, 0) + ? WHERE id = ?",
                        pointsToCredit, customerId);
            } catch (Exception ignored) {
            }
        }

        // Thông báo kết quả cho Khách hàng
        String custTitle = "RESOLVED".equals(finalStatus)
                ? "Khiếu nại " + complaintCode + " đã được giải quyết"
                : "Kết quả xử lý khiếu nại " + complaintCode;
        String custBody = "Đơn hàng " + orderCode + " — Phương án: " + RESOLUTION_LABELS.get(finalResType)
                + (pointsToCredit > 0 ? " (Đã cộng +" + String.format("%,d", pointsToCredit) + " điểm thưởng vào tài khoản của bạn)." : ".")
                + " Phản hồi CSKH: " + cleanNote;
        createNotificationSafe(customerId,
                "COMPLAINT_RESOLVED_CUST_" + complaintId,
                "COMPLAINT",
                custTitle,
                custBody,
                "COMPLAINT", complaintId);

        // Thông báo cho Chủ Shop
        if (shopOwnerId != null) {
            String shopTitle = "Kết luận xử lý khiếu nại " + complaintCode + " (Đơn " + orderCode + ")";
            String shopBody = "Quyết định từ CSKH: " + RESOLUTION_LABELS.get(finalResType)
                    + (finalShopFault ? " [Ghi nhận lỗi vi phạm chất lượng/quy trình của Shop]." : ".")
                    + " Nội dung: " + cleanNote;
            createNotificationSafe(shopOwnerId,
                    "COMPLAINT_RESOLVED_SHOP_" + complaintId,
                    "COMPLAINT",
                    shopTitle,
                    shopBody,
                    "ORDER", orderId);
        }

        // Tự động kiểm tra ngưỡng vi phạm của Shop -> nếu vượt ngưỡng hoặc CSKH bấm chuyển Hậu kiểm -> Cảnh báo Operations Manager
        long totalShopFaults = queryCount("SELECT COUNT(*) FROM complaints WHERE shop_id = ? AND is_shop_fault = 1", shopId);
        if (finalEscalate || (finalShopFault && totalShopFaults >= 2)) {
            jdbc.update("UPDATE complaints SET escalated_to_ops = 1 WHERE id = ?", complaintId);
            notifyRoleUsers("OPERATIONS_MANAGER",
                    "VIOLATION_ALERT_SHOP_" + shopId + "_" + complaintId,
                    "VIOLATION_ALERT",
                    "⚠️ Cảnh báo vi phạm gian hàng: " + shopName,
                    "Shop '" + shopName + "' vừa bị xác nhận lỗi trong khiếu nại " + complaintCode
                            + " (Tổng số vụ vi phạm chất lượng: " + totalShopFaults + " vụ). Đề nghị bộ phận Vận hành hậu kiểm hoặc tạm ngưng gian hàng.",
                    "SHOP", shopId);
        }
    }

    /**
     * Khách hàng tự hủy yêu cầu khiếu nại khi đang ở trạng thái PENDING.
     */
    @Transactional
    public void cancelCustomerComplaint(Long customerId, Long complaintId) {
        int updated = jdbc.update("""
            UPDATE complaints
            SET status = 'CANCELLED', updated_at = ?
            WHERE id = ? AND user_id = ? AND status = 'PENDING'
        """, Timestamp.valueOf(LocalDateTime.now()), complaintId, customerId);
        if (updated == 0) {
            throw new IllegalArgumentException("Chỉ có thể rút lại khiếu nại khi đang ở trạng thái Chờ tiếp nhận.");
        }
    }

    /**
     * CSKH chủ động gửi cảnh báo vi phạm của Shop lên bộ phận Vận hành (Operations Manager).
     */
    @Transactional
    public void escalateShopToOperations(Long shopId, String reason) {
        List<Map<String, Object>> shops = jdbc.queryForList("SELECT id, name FROM shops WHERE id = ?", shopId);
        if (shops.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy cửa hàng #" + shopId);
        }
        String shopName = String.valueOf(shops.get(0).get("name"));
        String cleanReason = (reason == null || reason.isBlank())
                ? "Gian hàng có nhiều khiếu nại chất lượng nông sản và đánh giá tiêu cực từ người mua."
                : reason.trim();

        jdbc.update("UPDATE complaints SET escalated_to_ops = 1 WHERE shop_id = ? AND is_shop_fault = 1", shopId);

        notifyRoleUsers("OPERATIONS_MANAGER",
                "MANUAL_ESCALATE_SHOP_" + shopId + "_" + System.currentTimeMillis(),
                "VIOLATION_ALERT",
                "🚨 Đề nghị Hậu kiểm khẩn cấp Shop: " + shopName,
                "Bộ phận CSKH đã chuyển hồ sơ cảnh báo vi phạm của shop '" + shopName + "' sang Ban Vận hành. Lý do: " + cleanReason,
                "SHOP", shopId);
    }

    // =========================================================================
    // 4. NĂM TÍNH NĂNG BÁO CÁO & GIÁM SÁT (OPERATIONS & CS DASHBOARD)
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getAnalyticsAndReports() {
        Map<String, Object> result = new HashMap<>();
        result.put("disputeReport", buildDisputeReport());
        result.put("csSlaReport", buildCsSlaReport());
        result.put("moderationMetrics", buildContentModerationMetrics());
        result.put("violationAlerts", buildViolationAlerts());
        result.put("autoSuggestIssues", buildAutoSuggestIssues());
        return result;
    }

    /**
     * Báo cáo tranh chấp (Dispute Reports)
     */
    private Map<String, Object> buildDisputeReport() {
        Map<String, Object> report = new HashMap<>();

        long totalOrders = queryCount("SELECT COUNT(*) FROM orders");
        long totalDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status <> 'CANCELLED'");
        long pendingDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status = 'PENDING'");
        long inProgressDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status = 'IN_PROGRESS'");
        long resolvedDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status = 'RESOLVED'");
        long rejectedDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status = 'REJECTED'");
        long shopFaultDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE is_shop_fault = 1");
        long escalatedDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE escalated_to_ops = 1");
        long distinctDisputedOrders = queryCount("SELECT COUNT(DISTINCT order_id) FROM complaints WHERE status <> 'CANCELLED'");

        Long totalCompensatedPoints = 0L;
        try {
            totalCompensatedPoints = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(compensated_points), 0) FROM complaints WHERE status = 'RESOLVED'", Long.class);
        } catch (Exception ignored) {
        }

        double disputeRatePct = totalOrders > 0 ? Math.round((distinctDisputedOrders * 1000.0) / totalOrders) / 10.0 : 0.0;
        long closedDisputes = resolvedDisputes + rejectedDisputes;
        double resolutionRatePct = totalDisputes > 0 ? Math.round((closedDisputes * 1000.0) / totalDisputes) / 10.0 : 100.0;

        // Phân tích theo nhóm lý do khiếu nại
        List<Map<String, Object>> reasonCounts = safeQueryList("""
            SELECT reason_type, COUNT(*) AS cnt
            FROM complaints
            WHERE status <> 'CANCELLED'
            GROUP BY reason_type
        """);
        Map<String, Long> reasonMap = new HashMap<>();
        for (Map<String, Object> r : reasonCounts) {
            reasonMap.put(String.valueOf(r.get("reason_type")), ((Number) r.get("cnt")).longValue());
        }
        List<Map<String, Object>> byReason = new ArrayList<>();
        for (Map.Entry<String, String> entry : REASON_LABELS.entrySet()) {
            long cnt = reasonMap.getOrDefault(entry.getKey(), 0L);
            double pct = totalDisputes > 0 ? Math.round((cnt * 1000.0) / totalDisputes) / 10.0 : 0.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", entry.getKey());
            item.put("label", entry.getValue());
            item.put("count", cnt);
            item.put("pct", pct);
            byReason.add(item);
        }

        // Phân tích theo phương án giải quyết
        List<Map<String, Object>> resCounts = safeQueryList("""
            SELECT resolution_type, COUNT(*) AS cnt
            FROM complaints
            WHERE status IN ('RESOLVED', 'REJECTED') AND resolution_type IS NOT NULL
            GROUP BY resolution_type
        """);
        Map<String, Long> resMap = new HashMap<>();
        for (Map<String, Object> r : resCounts) {
            resMap.put(String.valueOf(r.get("resolution_type")), ((Number) r.get("cnt")).longValue());
        }
        List<Map<String, Object>> byResolution = new ArrayList<>();
        for (Map.Entry<String, String> entry : RESOLUTION_LABELS.entrySet()) {
            long cnt = resMap.getOrDefault(entry.getKey(), 0L);
            double pct = closedDisputes > 0 ? Math.round((cnt * 1000.0) / closedDisputes) / 10.0 : 0.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", entry.getKey());
            item.put("label", entry.getValue());
            item.put("count", cnt);
            item.put("pct", pct);
            byResolution.add(item);
        }

        report.put("totalOrders", totalOrders);
        report.put("totalDisputes", totalDisputes);
        report.put("pendingDisputes", pendingDisputes);
        report.put("inProgressDisputes", inProgressDisputes);
        report.put("activeDisputes", pendingDisputes + inProgressDisputes);
        report.put("resolvedDisputes", resolvedDisputes);
        report.put("rejectedDisputes", rejectedDisputes);
        report.put("shopFaultDisputes", shopFaultDisputes);
        report.put("escalatedDisputes", escalatedDisputes);
        report.put("totalCompensatedPoints", totalCompensatedPoints != null ? totalCompensatedPoints : 0L);
        report.put("disputeRatePct", disputeRatePct);
        report.put("resolutionRatePct", resolutionRatePct);
        report.put("byReason", byReason);
        report.put("byResolution", byResolution);

        return report;
    }

    /**
     * Báo cáo SLA của bộ phận Chăm sóc khách hàng (Customer Service SLA Reports)
     */
    private Map<String, Object> buildCsSlaReport() {
        Map<String, Object> sla = new HashMap<>();

        List<Map<String, Object>> rows = safeQueryList("""
            SELECT id, priority, status, created_at, sla_due_at, resolved_at
            FROM complaints
            WHERE status <> 'CANCELLED'
        """);

        LocalDateTime now = LocalDateTime.now();
        long totalTickets = rows.size();
        long processedTickets = 0;
        long activeTickets = 0;
        long overdueActiveTickets = 0;
        long dueSoonActiveTickets = 0;
        long metSlaCount = 0;
        long breachedSlaCount = 0;
        double totalResolutionMinutes = 0.0;

        long highTotal = 0, highProcessed = 0, highMetSla = 0;
        double highTotalMinutes = 0.0;
        long normalTotal = 0, normalProcessed = 0, normalMetSla = 0;
        double normalTotalMinutes = 0.0;

        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("status"));
            String priority = String.valueOf(row.get("priority"));
            LocalDateTime createdAt = toLocalDateTime(row.get("created_at"));
            LocalDateTime slaDueAt = toLocalDateTime(row.get("sla_due_at"));
            LocalDateTime resolvedAt = toLocalDateTime(row.get("resolved_at"));

            if (createdAt == null) continue;
            if (slaDueAt == null) {
                slaDueAt = "HIGH".equals(priority) ? createdAt.plusHours(12) : createdAt.plusHours(24);
            }

            boolean isHigh = "HIGH".equals(priority);
            if (isHigh) highTotal++; else normalTotal++;

            if ("RESOLVED".equals(status) || "REJECTED".equals(status)) {
                processedTickets++;
                LocalDateTime finishTime = resolvedAt != null ? resolvedAt : now;
                double mins = Math.max(1.0, Duration.between(createdAt, finishTime).toMinutes());
                totalResolutionMinutes += mins;

                boolean met = !finishTime.isAfter(slaDueAt);
                if (met) metSlaCount++; else breachedSlaCount++;

                if (isHigh) {
                    highProcessed++;
                    highTotalMinutes += mins;
                    if (met) highMetSla++;
                } else {
                    normalProcessed++;
                    normalTotalMinutes += mins;
                    if (met) normalMetSla++;
                }
            } else {
                activeTickets++;
                if (now.isAfter(slaDueAt)) {
                    overdueActiveTickets++;
                } else if (Duration.between(now, slaDueAt).toMinutes() <= 180) {
                    dueSoonActiveTickets++;
                }
            }
        }

        double avgResolutionHours = processedTickets > 0
                ? Math.round((totalResolutionMinutes / processedTickets / 60.0) * 10.0) / 10.0 : 0.0;
        double slaComplianceRate = processedTickets > 0
                ? Math.round((metSlaCount * 1000.0) / processedTickets) / 10.0 : 100.0;

        double highSlaRate = highProcessed > 0
                ? Math.round((highMetSla * 1000.0) / highProcessed) / 10.0 : 100.0;
        double highAvgHours = highProcessed > 0
                ? Math.round((highTotalMinutes / highProcessed / 60.0) * 10.0) / 10.0 : 0.0;

        double normalSlaRate = normalProcessed > 0
                ? Math.round((normalMetSla * 1000.0) / normalProcessed) / 10.0 : 100.0;
        double normalAvgHours = normalProcessed > 0
                ? Math.round((normalTotalMinutes / normalProcessed / 60.0) * 10.0) / 10.0 : 0.0;

        sla.put("totalTickets", totalTickets);
        sla.put("processedTickets", processedTickets);
        sla.put("activeTickets", activeTickets);
        sla.put("overdueActiveTickets", overdueActiveTickets);
        sla.put("dueSoonActiveTickets", dueSoonActiveTickets);
        sla.put("metSlaCount", metSlaCount);
        sla.put("breachedSlaCount", breachedSlaCount);
        sla.put("avgResolutionHours", avgResolutionHours);
        sla.put("slaComplianceRate", slaComplianceRate);

        sla.put("highTotal", highTotal);
        sla.put("highSlaRate", highSlaRate);
        sla.put("highAvgHours", highAvgHours);
        sla.put("normalTotal", normalTotal);
        sla.put("normalSlaRate", normalSlaRate);
        sla.put("normalAvgHours", normalAvgHours);

        return sla;
    }

    /**
     * Thống kê kiểm duyệt nội dung (Content Moderation Metrics: Reviews + Products/Variants)
     */
    private Map<String, Object> buildContentModerationMetrics() {
        Map<String, Object> mod = new HashMap<>();

        long totalReviews = queryCount("SELECT COUNT(*) FROM reviews");
        long pendingReviews = queryCount("SELECT COUNT(*) FROM reviews WHERE COALESCE(moderation_status, 'PENDING') = 'PENDING'");
        long approvedReviews = queryCount("SELECT COUNT(*) FROM reviews WHERE moderation_status = 'APPROVED'");
        long rejectedReviews = queryCount("SELECT COUNT(*) FROM reviews WHERE moderation_status IN ('REJECTED', 'REMOVED')");
        long lowRatingReviews = queryCount("SELECT COUNT(*) FROM reviews WHERE rating <= 2");
        long positiveReviews = queryCount("SELECT COUNT(*) FROM reviews WHERE rating >= 4");

        Double avgRatingObj = 0.0;
        try {
            avgRatingObj = jdbc.queryForObject("SELECT COALESCE(AVG(rating), 0) FROM reviews", Double.class);
        } catch (Exception ignored) {
        }
        double avgRating = avgRatingObj != null ? Math.round(avgRatingObj * 10.0) / 10.0 : 0.0;

        long moderatedReviews = approvedReviews + rejectedReviews;
        double reviewModerationRate = totalReviews > 0
                ? Math.round((moderatedReviews * 1000.0) / totalReviews) / 10.0 : 100.0;

        // Kiểm duyệt Sản phẩm & Biến thể trên sàn
        long totalProducts = queryCount("SELECT COUNT(*) FROM products");
        long approvedProducts = queryCount("SELECT COUNT(*) FROM products WHERE approval_status = 'APPROVED'");
        long pendingProducts = queryCount("SELECT COUNT(*) FROM products WHERE approval_status = 'PENDING'");
        long rejectedProducts = queryCount("SELECT COUNT(*) FROM products WHERE approval_status = 'REJECTED'");

        long totalVariants = queryCount("SELECT COUNT(*) FROM product_variants");
        long approvedVariants = queryCount("SELECT COUNT(*) FROM product_variants WHERE approval_status = 'APPROVED'");
        long rejectedVariants = queryCount("SELECT COUNT(*) FROM product_variants WHERE approval_status = 'REJECTED'");

        double catalogComplianceRate = totalProducts > 0
                ? Math.round(((totalProducts - rejectedProducts) * 1000.0) / totalProducts) / 10.0 : 100.0;

        mod.put("totalReviews", totalReviews);
        mod.put("pendingReviews", pendingReviews);
        mod.put("approvedReviews", approvedReviews);
        mod.put("rejectedReviews", rejectedReviews);
        mod.put("moderatedReviews", moderatedReviews);
        mod.put("lowRatingReviews", lowRatingReviews);
        mod.put("positiveReviews", positiveReviews);
        mod.put("avgRating", avgRating);
        mod.put("reviewModerationRate", reviewModerationRate);

        mod.put("totalProducts", totalProducts);
        mod.put("approvedProducts", approvedProducts);
        mod.put("pendingProducts", pendingProducts);
        mod.put("rejectedProducts", rejectedProducts);
        mod.put("totalVariants", totalVariants);
        mod.put("approvedVariants", approvedVariants);
        mod.put("rejectedVariants", rejectedVariants);
        mod.put("catalogComplianceRate", catalogComplianceRate);

        return mod;
    }

    /**
     * Cảnh báo vi phạm tự động (Violation Alerts)
     */
    private List<Map<String, Object>> buildViolationAlerts() {
        List<Map<String, Object>> shops = safeQueryList("""
            SELECT s.id AS shop_id, s.name AS shop_name, s.phone AS shop_phone,
                   s.operating_status, s.approval_status,
                   u.full_name AS owner_name,
                   (SELECT COUNT(*) FROM orders o WHERE o.shop_id = s.id) AS total_orders,
                   (SELECT COUNT(*) FROM orders o WHERE o.shop_id = s.id AND (o.status = 'CANCELLED' OR o.delivery_status = 'FAILED')) AS failed_orders,
                   (SELECT COUNT(*) FROM complaints c WHERE c.shop_id = s.id AND c.status <> 'CANCELLED') AS total_complaints,
                   (SELECT COUNT(*) FROM complaints c WHERE c.shop_id = s.id AND c.is_shop_fault = 1) AS shop_fault_count,
                   (SELECT COUNT(*) FROM complaints c WHERE c.shop_id = s.id AND c.escalated_to_ops = 1) AS escalated_count,
                   (SELECT COUNT(*) FROM reviews r JOIN product_variants pv ON pv.id = r.variant_id WHERE pv.shop_id = s.id AND r.rating <= 2) AS low_review_count
            FROM shops s
            JOIN users u ON u.id = s.owner_id
            WHERE s.approval_status = 'APPROVED'
        """);

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Map<String, Object> s : shops) {
            long totalComplaints = toLong(s.get("total_complaints"));
            long shopFaultCount = toLong(s.get("shop_fault_count"));
            long escalatedCount = toLong(s.get("escalated_count"));
            long lowReviewCount = toLong(s.get("low_review_count"));
            long failedOrders = toLong(s.get("failed_orders"));
            String opStatus = String.valueOf(s.get("operating_status"));

            // Tính điểm rủi ro vi phạm (Violation Risk Score)
            long riskScore = (shopFaultCount * 3) + (escalatedCount * 2) + (totalComplaints) + (lowReviewCount * 2) + failedOrders;
            if (riskScore == 0 && !"SUSPENDED".equals(opStatus)) {
                continue;
            }

            String severity;
            if ("SUSPENDED".equals(opStatus) || shopFaultCount >= 2 || escalatedCount >= 1 || lowReviewCount >= 3 || riskScore >= 5) {
                severity = "CRITICAL";
            } else {
                severity = "WARNING";
            }

            List<String> reasons = new ArrayList<>();
            if (shopFaultCount > 0) reasons.add(shopFaultCount + " khiếu nại xác định lỗi thuộc về Shop");
            else if (totalComplaints > 0) reasons.add(totalComplaints + " khiếu nại từ khách hàng");
            if (lowReviewCount > 0) reasons.add(lowReviewCount + " đánh giá chất lượng kém (1-2 ★)");
            if (failedOrders > 0) reasons.add(failedOrders + " đơn hủy / giao hàng thất bại");
            if ("SUSPENDED".equals(opStatus)) reasons.add("Đang bị tạm đình chỉ hoạt động (SUSPENDED)");

            String summary = reasons.isEmpty() ? "Cần theo dõi chất lượng định kỳ" : String.join(" • ", reasons);
            String recommendedAction = "CRITICAL".equals(severity)
                    ? "Yêu cầu giải trình hậu kiểm / Tạm khóa gian hàng"
                    : "Nhắc nhở quy chuẩn đóng gói & chất lượng nông sản";

            Map<String, Object> alert = new LinkedHashMap<>(s);
            alert.put("riskScore", riskScore);
            alert.put("severity", severity);
            alert.put("severityLabel", "CRITICAL".equals(severity) ? "Nghiêm trọng" : "Cảnh báo");
            alert.put("violationSummary", summary);
            alert.put("recommendedAction", recommendedAction);
            alerts.add(alert);
        }

        alerts.sort((a, b) -> Long.compare(toLong(b.get("riskScore")), toLong(a.get("riskScore"))));
        return alerts;
    }

    /**
     * Tự động phát hiện các đơn hàng giao thất bại hoặc có đánh giá 1-2 sao nhưng chưa có phiếu khiếu nại
     * để CSKH chủ động 1-click khởi tạo phiếu hỗ trợ.
     */
    private List<Map<String, Object>> buildAutoSuggestIssues() {
        return safeQueryList("""
            SELECT o.id AS order_id, o.order_code, o.recipient_name, o.recipient_phone,
                   o.status, o.delivery_status, o.cancellation_reason, o.delivery_failure_reason,
                   s.name AS shop_name
            FROM orders o
            JOIN shops s ON s.id = o.shop_id
            WHERE (o.status = 'CANCELLED' OR o.delivery_status = 'FAILED')
              AND NOT EXISTS (SELECT 1 FROM complaints c WHERE c.order_id = o.id)
            ORDER BY o.created_at DESC
            LIMIT 5
        """);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private List<Map<String, Object>> enrichComplaintRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            result.add(enrichSingleComplaintRow(r));
        }
        return result;
    }

    private Map<String, Object> enrichSingleComplaintRow(Map<String, Object> raw) {
        Map<String, Object> row = new LinkedHashMap<>(raw);
        String reasonType = String.valueOf(row.getOrDefault("reason_type", "OTHER"));
        String status = String.valueOf(row.getOrDefault("status", "PENDING"));
        String priority = String.valueOf(row.getOrDefault("priority", "NORMAL"));
        String resolutionType = row.get("resolution_type") != null ? String.valueOf(row.get("resolution_type")) : null;
        String sourceType = String.valueOf(row.getOrDefault("source_type", "CUSTOMER"));

        row.put("reason_label", REASON_LABELS.getOrDefault(reasonType, reasonType));
        row.put("status_label", STATUS_LABELS.getOrDefault(status, status));
        row.put("priority_label", PRIORITY_LABELS.getOrDefault(priority, priority));
        row.put("resolution_label", resolutionType != null ? RESOLUTION_LABELS.getOrDefault(resolutionType, resolutionType) : "Chưa có kết luận");
        row.put("source_label", switch (sourceType) {
            case "CS_MANUAL" -> "CSKH tạo thủ công";
            case "AUTO_SYSTEM" -> "Hệ thống tự động";
            default -> "Khách hàng gửi";
        });

        LocalDateTime createdAt = toLocalDateTime(row.get("created_at"));
        LocalDateTime slaDueAt = toLocalDateTime(row.get("sla_due_at"));
        LocalDateTime resolvedAt = toLocalDateTime(row.get("resolved_at"));
        LocalDateTime shopRespondedAt = toLocalDateTime(row.get("shop_responded_at"));
        LocalDateTime now = LocalDateTime.now();

        row.put("created_at_fmt", createdAt != null ? createdAt.format(DT_FMT) : "—");
        row.put("sla_due_at_fmt", slaDueAt != null ? slaDueAt.format(DT_FMT) : "—");
        row.put("resolved_at_fmt", resolvedAt != null ? resolvedAt.format(DT_FMT) : "—");
        row.put("shop_responded_at_fmt", shopRespondedAt != null ? shopRespondedAt.format(DT_FMT) : "—");

        // Tính trạng thái thời hạn xử lý
        String slaState = "ON_TIME";
        String slaBadgeText = "Trong hạn";
        if (slaDueAt != null) {
            if ("RESOLVED".equals(status) || "REJECTED".equals(status)) {
                LocalDateTime finish = resolvedAt != null ? resolvedAt : now;
                double hoursTaken = Math.max(0.1, Math.round((Duration.between(createdAt != null ? createdAt : finish, finish).toMinutes() / 60.0) * 10.0) / 10.0);
                if (!finish.isAfter(slaDueAt)) {
                    slaState = "MET_SLA";
                    slaBadgeText = "Đúng hạn (" + hoursTaken + "h)";
                } else {
                    slaState = "BREACHED_SLA";
                    slaBadgeText = "Trễ hạn (" + hoursTaken + "h)";
                }
            } else if ("PENDING".equals(status) || "IN_PROGRESS".equals(status)) {
                long minsRemaining = Duration.between(now, slaDueAt).toMinutes();
                if (minsRemaining < 0) {
                    double overdueHours = Math.round((Math.abs(minsRemaining) / 60.0) * 10.0) / 10.0;
                    slaState = "OVERDUE";
                    slaBadgeText = "Quá hạn " + overdueHours + "h";
                } else if (minsRemaining <= 180) {
                    double remHours = Math.round((minsRemaining / 60.0) * 10.0) / 10.0;
                    slaState = "DUE_SOON";
                    slaBadgeText = "Sắp hết hạn (" + remHours + "h)";
                } else {
                    double remHours = Math.round((minsRemaining / 60.0) * 10.0) / 10.0;
                    slaState = "ON_TIME";
                    slaBadgeText = "Còn " + remHours + "h";
                }
            }
        }
        row.put("sla_state", slaState);
        row.put("sla_badge_text", slaBadgeText);

        return row;
    }

    private String determineAutoPriority(String reasonType, BigDecimal orderTotal) {
        if ("QUALITY_ISSUE".equals(reasonType) || "WEIGHT_OR_QUANTITY_SHORTAGE".equals(reasonType)) {
            return "HIGH";
        }
        if (orderTotal != null && orderTotal.compareTo(new BigDecimal("500000")) >= 0) {
            return "HIGH";
        }
        return "NORMAL";
    }

    private String generateComplaintCode(LocalDateTime now) {
        String datePart = now.format(CODE_DATE_FMT);
        int rand = 1000 + new Random().nextInt(9000);
        return "KN-" + datePart + "-" + rand;
    }

    private Long extractKey(KeyHolder keyHolder, String complaintCode) {
        try {
            Map<String, Object> keys = keyHolder.getKeys();
            if (keys != null) {
                Object idObj = keys.get("id");
                if (idObj == null) idObj = keys.get("ID");
                if (idObj instanceof Number n) {
                    return n.longValue();
                }
            }
            if (keyHolder.getKey() != null) {
                return keyHolder.getKey().longValue();
            }
        } catch (Exception ignored) {
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM complaints WHERE complaint_code = ?", Long.class, complaintCode);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String storeEvidenceImageIfPresent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Ảnh minh chứng không được vượt quá 5 MB.");
        }
        String contentType = file.getContentType();
        String ext = null;
        if ("image/jpeg".equalsIgnoreCase(contentType) || "image/jpg".equalsIgnoreCase(contentType)) {
            ext = ".jpg";
        } else if ("image/png".equalsIgnoreCase(contentType)) {
            ext = ".png";
        }
        if (ext == null || !isDecodableImage(file)) {
            throw new IllegalArgumentException("Vui lòng tải lên ảnh minh chứng định dạng JPG hoặc PNG hợp lệ.");
        }
        String fileName = UUID.randomUUID() + ext;
        try {
            Files.createDirectories(uploadDirectory);
            Path target = uploadDirectory.resolve(fileName).normalize();
            if (!target.startsWith(uploadDirectory)) {
                throw new IllegalArgumentException("Đường dẫn lưu ảnh không hợp lệ.");
            }
            file.transferTo(target);
            return "/uploads/complaints/" + fileName;
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể lưu ảnh minh chứng khiếu nại.", ex);
        }
    }

    private boolean isDecodableImage(MultipartFile file) {
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) return false;
            var readers = ImageIO.getImageReaders(input);
            return readers.hasNext();
        } catch (IOException ex) {
            return false;
        }
    }

    private void createNotificationSafe(Long userId, String eventKey, String type, String title, String content, String targetType, Long targetId) {
        if (userId == null) return;
        try {
            jdbc.update("""
                INSERT INTO notifications (user_id, event_key, type, title, content, target_type, target_id, is_read, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
            """, userId, eventKey, type, title, content, targetType, targetId, Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception ignored) {
        }
    }

    private void notifyRoleUsers(String roleCode, String eventKeyPrefix, String type, String title, String content, String targetType, Long targetId) {
        try {
            List<Long> userIds = jdbc.queryForList("""
                SELECT u.id FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE r.code = ? AND u.status = 'ACTIVE'
            """, Long.class, roleCode);
            for (Long uid : userIds) {
                createNotificationSafe(uid, eventKeyPrefix + "_U" + uid, type, title, content, targetType, targetId);
            }
        } catch (Exception ignored) {
        }
    }

    private LocalDateTime toLocalDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof Timestamp ts) return ts.toLocalDateTime();
        if (val instanceof LocalDateTime ldt) return ldt;
        return null;
    }

    private long toLong(Object val) {
        return val instanceof Number n ? n.longValue() : 0L;
    }

    private long queryCount(String sql, Object... args) {
        try {
            Long cnt = jdbc.queryForObject(sql, Long.class, args);
            return cnt != null ? cnt : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<Map<String, Object>> safeQueryList(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
