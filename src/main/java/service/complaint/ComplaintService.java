package service.complaint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final int SHOP_SLA_HOURS = 6;
    private static final int DELIVERED_COMPLAINT_WINDOW_HOURS = 24;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter CODE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final String STATUS_PENDING_SHOP = ComplaintStatus.PENDING_SHOP.getCode();
    public static final String STATUS_ESCALATED = ComplaintStatus.ESCALATED.getCode();
    public static final String STATUS_RESOLVED = ComplaintStatus.RESOLVED.getCode();
    public static final String STATUS_REJECTED = ComplaintStatus.REJECTED.getCode();
    public static final String STATUS_CANCELLED = ComplaintStatus.CANCELLED.getCode();

    public static final Set<String> PRODUCT_DEFECT_REASONS = Set.of(
            "QUALITY_ISSUE",
            "WEIGHT_OR_QUANTITY_SHORTAGE",
            "WRONG_SPECIFICATION"
    );

    public static final Set<String> SERVICE_ISSUE_REASONS = Set.of(
            "PACKAGING_DAMAGE",
            "DELIVERY_OR_SERVICE",
            "OTHER"
    );

    public static final Set<String> SHIPPING_OR_CANCELLED_ALLOWED_REASONS = Set.of(
            "DELIVERY_OR_SERVICE",
            "OTHER"
    );

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

        // Quy toàn bộ về Đền bù Điểm thưởng / Hoàn tiền hoặc Nhắc nhở/Cảnh cáo Shop (Bỏ Giao bù hàng)
        RESOLUTION_LABELS.put("COMPENSATE_POINTS", "Đền bù Điểm thưởng / Hoàn tiền vào tài khoản");
        RESOLUTION_LABELS.put("SHOP_WARNING_ESCALATE", "Nhắc nhở / Cảnh cáo Shop & Chuyển Hậu kiểm");
        RESOLUTION_LABELS.put("EXPLANATION_ONLY", "Ghi nhận phản ánh dịch vụ & Nhắc nhở Shop");
        RESOLUTION_LABELS.put("REJECTED", "Bác bỏ khiếu nại (Không đủ căn cứ)");

        STATUS_LABELS.put(STATUS_PENDING_SHOP, ComplaintStatus.PENDING_SHOP.getLabel());
        STATUS_LABELS.put(STATUS_ESCALATED, ComplaintStatus.ESCALATED.getLabel());
        STATUS_LABELS.put(STATUS_RESOLVED, ComplaintStatus.RESOLVED.getLabel());
        STATUS_LABELS.put(STATUS_REJECTED, ComplaintStatus.REJECTED.getLabel());
        STATUS_LABELS.put(STATUS_CANCELLED, ComplaintStatus.CANCELLED.getLabel());
        // Tương thích hiển thị nếu có bản ghi cũ
        STATUS_LABELS.put("PENDING", ComplaintStatus.PENDING_SHOP.getLabel());
        STATUS_LABELS.put("IN_PROGRESS", ComplaintStatus.ESCALATED.getLabel());

        PRIORITY_LABELS.put("HIGH", "Khẩn cấp (SLA Shop 6h)");
        PRIORITY_LABELS.put("NORMAL", "Tiêu chuẩn (SLA Shop 6h)");
        PRIORITY_LABELS.put("LOW", "Thấp (SLA Shop 6h)");
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
                    status VARCHAR(25) NOT NULL DEFAULT 'PENDING_SHOP',
                    source_type VARCHAR(25) NOT NULL DEFAULT 'CUSTOMER',
                    requested_refund_amount BIGINT NOT NULL DEFAULT 0,
                    final_refund_amount BIGINT NOT NULL DEFAULT 0,
                    shop_decision VARCHAR(25) NULL,
                    shop_response VARCHAR(1500) NULL,
                    shop_evidence_image_url VARCHAR(1000) NULL,
                    shop_responded_at DATETIME(3) NULL,
                    escalated_at DATETIME(3) NULL,
                    shop_remind_count INT NOT NULL DEFAULT 0,
                    last_reminded_at DATETIME(3) NULL,
                    cs_shop_request_note VARCHAR(1000) NULL,
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
                        status VARCHAR(25) NOT NULL DEFAULT 'PENDING_SHOP',
                        source_type VARCHAR(25) NOT NULL DEFAULT 'CUSTOMER',
                        requested_refund_amount BIGINT NOT NULL DEFAULT 0,
                        final_refund_amount BIGINT NOT NULL DEFAULT 0,
                        shop_decision VARCHAR(25) NULL,
                        shop_response VARCHAR(1500) NULL,
                        shop_evidence_image_url VARCHAR(1000) NULL,
                        shop_responded_at TIMESTAMP NULL,
                        escalated_at TIMESTAMP NULL,
                        shop_remind_count INT NOT NULL DEFAULT 0,
                        last_reminded_at TIMESTAMP NULL,
                        cs_shop_request_note VARCHAR(1000) NULL,
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

        // Gỡ bỏ các CHECK constraint cũ trên MySQL nếu CSDL được tạo từ phiên bản V12 cũ
        for (int i = 1; i <= 8; i++) {
            safeExecuteAlter("ALTER TABLE complaints DROP CHECK complaints_chk_" + i);
        }

        // Bổ sung an toàn các cột mới cho CSDL hiện hữu
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN requested_refund_amount BIGINT NOT NULL DEFAULT 0");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN final_refund_amount BIGINT NOT NULL DEFAULT 0");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN shop_decision VARCHAR(25) NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN shop_evidence_image_url VARCHAR(1000) NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN escalated_at TIMESTAMP NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN shop_remind_count INT NOT NULL DEFAULT 0");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN last_reminded_at TIMESTAMP NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN cs_shop_request_note VARCHAR(1000) NULL");

        // Đồng bộ trạng thái cũ sang mô hình 2 tầng mới
        safeExecuteAlter("UPDATE complaints SET status = 'PENDING_SHOP' WHERE status = 'PENDING'");
        safeExecuteAlter("UPDATE complaints SET status = 'ESCALATED' WHERE status = 'IN_PROGRESS'");
        safeExecuteAlter("UPDATE complaints SET final_refund_amount = compensated_points WHERE status = 'RESOLVED' AND final_refund_amount = 0 AND compensated_points > 0");
    }

    private void safeExecuteAlter(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception ignored) {
        }
    }

    public Map<String, String> getReasonLabels() {
        return REASON_LABELS;
    }

    public Map<String, String> getResolutionLabels() {
        return RESOLUTION_LABELS;
    }

    public Map<String, String> getReasonLabelsForOrderStatus(String orderStatus) {
        if ("SHIPPING".equalsIgnoreCase(orderStatus) || "CANCELLED".equalsIgnoreCase(orderStatus)) {
            Map<String, String> filtered = new LinkedHashMap<>();
            for (String code : SHIPPING_OR_CANCELLED_ALLOWED_REASONS) {
                if (REASON_LABELS.containsKey(code)) {
                    filtered.put(code, REASON_LABELS.get(code));
                }
            }
            return filtered;
        }
        if ("DELIVERED".equalsIgnoreCase(orderStatus)) {
            return REASON_LABELS;
        }
        return Collections.emptyMap();
    }

    // =========================================================================
    // KIỂM TRA ĐIỀU KIỆN KHIẾU NẠI & TÍNH SỐ TIỀN THỰC TRẢ (ACTUAL PAID AMOUNT)
    // =========================================================================

    /**
     * Tính số tiền thực trả tối đa (actualPaidAmount) được phép yêu cầu hoàn/đền bù
     * của toàn bộ đơn hàng hoặc của riêng một sản phẩm sau khi đã trừ voucher/discount (không tính phí vận chuyển).
     */
    @Transactional(readOnly = true)
    public long calculateActualPaidAmount(Long orderId, Long orderItemId) {
        List<Map<String, Object>> orders = safeQueryList(
                "SELECT id, subtotal, voucher_discount, points_discount, total_amount, shipping_fee FROM orders WHERE id = ?",
                orderId
        );
        if (orders.isEmpty()) {
            return 0L;
        }
        return calculateActualPaidAmountFromOrderMap(orders.get(0), orderItemId);
    }

    private long calculateActualPaidAmountFromOrderMap(Map<String, Object> order, Long orderItemId) {
        BigDecimal subtotal = toBigDecimal(order.get("subtotal"));
        BigDecimal voucherDiscount = toBigDecimal(order.get("voucher_discount"));
        BigDecimal pointsDiscount = toBigDecimal(order.get("points_discount"));
        BigDecimal orderPaidGoods = subtotal.subtract(voucherDiscount).subtract(pointsDiscount).max(BigDecimal.ZERO);

        if (orderItemId == null) {
            return orderPaidGoods.setScale(0, RoundingMode.DOWN).longValue();
        }

        Long orderId = ((Number) order.get("id")).longValue();
        List<Map<String, Object>> items = safeQueryList(
                "SELECT line_total FROM order_items WHERE id = ? AND order_id = ?",
                orderItemId, orderId
        );
        if (items.isEmpty()) {
            return orderPaidGoods.setScale(0, RoundingMode.DOWN).longValue();
        }
        BigDecimal lineTotal = toBigDecimal(items.get(0).get("line_total"));
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        if (orderPaidGoods.compareTo(subtotal) >= 0) {
            return lineTotal.setScale(0, RoundingMode.DOWN).longValue();
        }
        BigDecimal itemActualPaid = lineTotal.multiply(orderPaidGoods)
                .divide(subtotal, 0, RoundingMode.HALF_UP)
                .min(orderPaidGoods)
                .max(BigDecimal.ZERO);
        return itemActualPaid.longValue();
    }

    /**
     * Cung cấp thông tin điều kiện tạo khiếu nại & khóa nút Hoàn tất đơn hàng cho giao diện chi tiết đơn hàng của Customer.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrderComplaintEligibility(Long customerId, Long orderId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canCreateComplaint", false);
        result.put("blockReason", null);
        result.put("allowedReasons", Collections.emptyMap());
        result.put("allowRefundRequest", false);
        result.put("orderActualPaidAmount", 0L);
        result.put("itemActualPaidAmounts", Collections.emptyMap());
        result.put("hasActiveComplaint", false);
        result.put("canCompleteOrder", false);

        List<Map<String, Object>> orders = safeQueryList("""
            SELECT o.*
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            WHERE o.id = ? AND c.user_id = ?
        """, orderId, customerId);
        if (orders.isEmpty()) {
            result.put("blockReason", "Không tìm thấy đơn hàng.");
            return result;
        }

        Map<String, Object> order = orders.get(0);
        String orderStatus = String.valueOf(order.get("status"));
        LocalDateTime deliveredAt = toLocalDateTime(order.get("delivered_at"));
        LocalDateTime now = LocalDateTime.now();

        long nonCancelledCount = queryCount(
                "SELECT COUNT(*) FROM complaints WHERE order_id = ? AND status <> 'CANCELLED'",
                orderId
        );
        long activeCount = queryCount(
                "SELECT COUNT(*) FROM complaints WHERE order_id = ? AND status IN ('PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS')",
                orderId
        );
        boolean hasActiveComplaint = activeCount > 0;
        result.put("hasActiveComplaint", hasActiveComplaint);
        result.put("canCompleteOrder", "DELIVERED".equals(orderStatus) && !hasActiveComplaint);

        long orderActualPaid = calculateActualPaidAmountFromOrderMap(order, null);
        result.put("orderActualPaidAmount", orderActualPaid);

        List<Map<String, Object>> itemRows = safeQueryList("SELECT id FROM order_items WHERE order_id = ?", orderId);
        Map<Long, Long> itemPaidMap = new LinkedHashMap<>();
        for (Map<String, Object> itemRow : itemRows) {
            Long itemId = ((Number) itemRow.get("id")).longValue();
            itemPaidMap.put(itemId, calculateActualPaidAmountFromOrderMap(order, itemId));
        }
        result.put("itemActualPaidAmounts", itemPaidMap);

        // 1. Kiểm tra giới hạn tối đa 1 lần khiếu nại / đơn (trừ khi đã tự hủy CANCELLED)
        if (nonCancelledCount > 0) {
            result.put("blockReason", "Mỗi đơn hàng chỉ được phép gửi khiếu nại tối đa 1 lần.");
            return result;
        }

        // 2. Kiểm tra theo trạng thái đơn hàng
        if ("PENDING".equals(orderStatus) || "CONFIRMED".equals(orderStatus)
                || "PREPARING".equals(orderStatus) || "COMPLETED".equals(orderStatus)) {
            result.put("blockReason", "Trạng thái đơn hàng hiện tại (" + orderStatus + ") không được phép tạo khiếu nại mới.");
            return result;
        }

        if ("SHIPPING".equals(orderStatus) || "CANCELLED".equals(orderStatus)) {
            result.put("canCreateComplaint", true);
            result.put("allowedReasons", getReasonLabelsForOrderStatus(orderStatus));
            result.put("allowRefundRequest", false);
            return result;
        }

        if ("DELIVERED".equals(orderStatus)) {
            if (deliveredAt != null && now.isAfter(deliveredAt.plusHours(DELIVERED_COMPLAINT_WINDOW_HOURS))) {
                result.put("blockReason", "Đã quá thời hạn 24 giờ kể từ thời điểm giao hàng thành công để gửi khiếu nại.");
                return result;
            }
            result.put("canCreateComplaint", true);
            result.put("allowedReasons", REASON_LABELS);
            result.put("allowRefundRequest", true);
            return result;
        }

        result.put("blockReason", "Trạng thái đơn hàng không hỗ trợ tạo khiếu nại.");
        return result;
    }

    // =========================================================================
    // 1. CUSTOMER TẠO KHIẾU NẠI & HỦY KHIẾU NẠI
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
        return submitCustomerComplaint(customerId, orderId, orderItemId, reasonType, title, description, evidenceImage, 0L);
    }

    @Transactional
    public Long submitCustomerComplaint(
            Long customerId,
            Long orderId,
            Long orderItemId,
            String reasonType,
            String title,
            String description,
            MultipartFile evidenceImage,
            Long requestedRefundAmount) {

        if (customerId == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để gửi khiếu nại.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("Không xác định được đơn hàng cần khiếu nại.");
        }
        if (reasonType == null || !REASON_LABELS.containsKey(reasonType.trim())) {
            throw new IllegalArgumentException("Vui lòng chọn lý do khiếu nại hợp lệ.");
        }
        String cleanReason = reasonType.trim();

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

        // Khóa dòng đơn hàng FOR UPDATE để chống Race Condition khi tạo khiếu nại đồng thời
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT o.*, s.name AS shop_name, s.owner_id AS shop_owner_id
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            JOIN shops s ON s.id = o.shop_id
            WHERE o.id = ? AND c.user_id = ?
            FOR UPDATE
        """, orderId, customerId);

        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng hoặc bạn không có quyền khiếu nại đơn hàng này.");
        }
        Map<String, Object> order = orders.get(0);
        String orderStatus = String.valueOf(order.get("status"));
        LocalDateTime now = LocalDateTime.now();

        // 1. Giới hạn số lần khiếu nại: Mỗi đơn chỉ tối đa 1 lần (trừ khi khiếu nại trước đó đã CANCELLED)
        Long existingNonCancelled = jdbc.queryForObject("""
            SELECT COUNT(*) FROM complaints
            WHERE order_id = ? AND status <> 'CANCELLED'
        """, Long.class, orderId);
        if (existingNonCancelled != null && existingNonCancelled > 0) {
            throw new IllegalArgumentException("Mỗi đơn hàng chỉ được phép gửi khiếu nại tối đa 1 lần (trừ trường hợp đã hủy khiếu nại trước đó).");
        }

        // 2. Lọc theo trạng thái đơn hàng (OrderStatus)
        if ("PENDING".equals(orderStatus) || "CONFIRMED".equals(orderStatus)
                || "PREPARING".equals(orderStatus) || "COMPLETED".equals(orderStatus)) {
            throw new IllegalArgumentException("Không được phép tạo khiếu nại mới khi đơn hàng ở trạng thái " + orderStatus + ".");
        }
        if (!"SHIPPING".equals(orderStatus) && !"CANCELLED".equals(orderStatus) && !"DELIVERED".equals(orderStatus)) {
            throw new IllegalArgumentException("Trạng thái đơn hàng hiện tại không cho phép tạo khiếu nại.");
        }

        // Kiểm tra orderItemId nếu có truyền vào
        Long validItemId = null;
        if (orderItemId != null) {
            List<Long> itemIds = jdbc.queryForList(
                    "SELECT id FROM order_items WHERE id = ? AND order_id = ?",
                    Long.class, orderItemId, orderId);
            if (itemIds.isEmpty()) {
                throw new IllegalArgumentException("Sản phẩm khiếu nại không thuộc đơn hàng này.");
            }
            validItemId = itemIds.get(0);
        }

        String imageUrl = storeEvidenceImageIfPresent(evidenceImage);
        long finalRequestedRefund = 0L;

        if ("SHIPPING".equals(orderStatus) || "CANCELLED".equals(orderStatus)) {
            // Chỉ cho phép chọn DELIVERY_OR_SERVICE hoặc OTHER
            if (!SHIPPING_OR_CANCELLED_ALLOWED_REASONS.contains(cleanReason)) {
                throw new IllegalArgumentException("Đơn hàng đang giao hoặc đã hủy chỉ được phép chọn lý do 'Sự cố giao hàng / thái độ phục vụ' hoặc 'Lý do khác'.");
            }
            // Mặc định requestedRefundAmount = 0 (khóa đòi bồi thường, chỉ phản ánh dịch vụ)
            if (requestedRefundAmount != null && requestedRefundAmount > 0) {
                throw new IllegalArgumentException("Đơn hàng ở trạng thái " + orderStatus + " chỉ hỗ trợ phản ánh dịch vụ (số tiền yêu cầu bồi thường phải bằng 0).");
            }
            finalRequestedRefund = 0L;
        } else if ("DELIVERED".equals(orderStatus)) {
            // Giới hạn thời gian 24 giờ kể từ thời điểm đơn chuyển sang DELIVERED (delivered_at)
            LocalDateTime deliveredAt = toLocalDateTime(order.get("delivered_at"));
            if (deliveredAt != null && now.isAfter(deliveredAt.plusHours(DELIVERED_COMPLAINT_WINDOW_HOURS))) {
                throw new IllegalArgumentException("Đã quá thời hạn 24 giờ kể từ lúc đơn hàng giao thành công, không thể tạo khiếu nại.");
            }

            // Validate bằng chứng: Nhóm lỗi hàng hóa bắt buộc đính kèm ít nhất 1 hình ảnh bằng chứng
            if (PRODUCT_DEFECT_REASONS.contains(cleanReason) && (imageUrl == null || imageUrl.isBlank())) {
                throw new IllegalArgumentException("Nhóm lỗi hàng hóa (Chất lượng, Thiếu cân/số lượng, Sai quy cách) bắt buộc phải đính kèm ít nhất 1 hình ảnh bằng chứng.");
            }

            // Validate số tiền yêu cầu đền bù: 0 <= requestedRefundAmount <= actualPaidAmount
            long actualPaidAmount = calculateActualPaidAmountFromOrderMap(order, validItemId);
            long reqAmount = requestedRefundAmount != null ? requestedRefundAmount : 0L;
            if (reqAmount < 0) {
                throw new IllegalArgumentException("Số tiền/điểm yêu cầu hoàn trả không được âm.");
            }
            if (reqAmount > actualPaidAmount) {
                throw new IllegalArgumentException("Số tiền/điểm yêu cầu đền bù (" + reqAmount + "đ) không được vượt quá số tiền thực trả (" + actualPaidAmount + "đ).");
            }
            finalRequestedRefund = reqAmount;
        }

        Long shopId = ((Number) order.get("shop_id")).longValue();
        BigDecimal totalAmount = toBigDecimal(order.get("total_amount"));
        String priority = determineAutoPriority(cleanReason, totalAmount);
        // SLA tầng 1 (PENDING_SHOP): 6 giờ để Shop phản hồi
        LocalDateTime slaDueAt = now.plusHours(SHOP_SLA_HOURS);
        String complaintCode = generateComplaintCode(now);

        final String finalTitle = cleanTitle;
        final Long finalItemId = validItemId;
        final long refundToInsert = finalRequestedRefund;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO complaints (
                    complaint_code, order_id, order_item_id, user_id, shop_id,
                    reason_type, title, description, evidence_image_url,
                    priority, status, source_type, requested_refund_amount, final_refund_amount,
                    sla_due_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_SHOP', 'CUSTOMER', ?, 0, ?, ?, ?)
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
            ps.setLong(11, refundToInsert);
            ps.setTimestamp(12, Timestamp.valueOf(slaDueAt));
            ps.setTimestamp(13, Timestamp.valueOf(now));
            ps.setTimestamp(14, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);

        Long complaintId = extractKey(keyHolder, complaintCode);
        String orderCode = String.valueOf(order.get("order_code"));
        Long shopOwnerId = order.get("shop_owner_id") instanceof Number n ? n.longValue() : null;

        createNotificationSafe(customerId,
                "COMPLAINT_CREATED_CUST_" + complaintId,
                "COMPLAINT",
                "Đã gửi khiếu nại " + complaintCode,
                "Khiếu nại cho đơn hàng " + orderCode + " đang chờ Cửa hàng phản hồi (Hạn SLA 6h: trước " + slaDueAt.format(DT_FMT) + ").",
                "COMPLAINT", complaintId);

        if (shopOwnerId != null) {
            createNotificationSafe(shopOwnerId,
                    "COMPLAINT_CREATED_SHOP_" + complaintId,
                    "COMPLAINT",
                    "Đơn hàng " + orderCode + " có khiếu nại mới (" + complaintCode + ")",
                    "Khách hàng gửi khiếu nại: '" + finalTitle + "' (Yêu cầu hoàn: " + refundToInsert + " điểm). Vui lòng xử lý trong 6h (trước " + slaDueAt.format(DT_FMT) + ").",
                    "ORDER", orderId);
        }

        return complaintId;
    }

    /**
     * Customer tự hủy: Khi đang ở PENDING_SHOP hoặc ESCALATED, Customer có quyền bấm hủy -> chuyển sang CANCELLED.
     * Chống xung đột thao tác (Race Condition) bằng FOR UPDATE.
     */
    @Transactional
    public void cancelCustomerComplaint(Long customerId, Long complaintId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, status, user_id
            FROM complaints
            WHERE id = ? AND user_id = ?
            FOR UPDATE
        """, complaintId, customerId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy yêu cầu khiếu nại của bạn.");
        }
        String currentStatus = String.valueOf(rows.get(0).get("status"));
        if (!STATUS_PENDING_SHOP.equals(currentStatus) && !STATUS_ESCALATED.equals(currentStatus)
                && !"PENDING".equals(currentStatus) && !"IN_PROGRESS".equals(currentStatus)) {
            throw new IllegalArgumentException("Chỉ có thể rút lại khiếu nại khi đang ở trạng thái Chờ Shop phản hồi hoặc Chuyển CSKH phân xử.");
        }

        int updated = jdbc.update("""
            UPDATE complaints
            SET status = 'CANCELLED', updated_at = ?
            WHERE id = ? AND user_id = ? AND status IN ('PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS')
        """, Timestamp.valueOf(LocalDateTime.now()), complaintId, customerId);
        if (updated == 0) {
            throw new IllegalArgumentException("Trạng thái khiếu nại vừa thay đổi, không thể hủy.");
        }
    }

    // =========================================================================
    // 2. SHOP XỬ LÝ KHIẾU NẠI (TẦNG 1 - PENDING_SHOP) & SCHEDULED JOB SLA 6H
    // =========================================================================

    /**
     * Shop xử lý khiếu nại (chỉ thực hiện được khi đơn đang ở PENDING_SHOP):
     * - ACCEPT ("Đồng ý"): finalRefundAmount = requestedRefundAmount, tự động cộng điểm cho Customer -> RESOLVED.
     * - REJECT ("Từ chối"): bắt buộc nhập lý do từ chối (+ ảnh đối chứng nếu có) -> ESCALATED chuyển CSKH phân xử.
     */
    @Transactional
    public void submitShopDecision(
            Long ownerId,
            Long complaintId,
            String decision,
            String shopResponse,
            MultipartFile shopEvidenceImage) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.*, o.order_code, s.name AS shop_name
            FROM complaints c
            JOIN shops s ON s.id = c.shop_id
            JOIN orders o ON o.id = c.order_id
            WHERE c.id = ? AND s.owner_id = ?
            FOR UPDATE
        """, complaintId, ownerId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xử lý khiếu nại này.");
        }

        Map<String, Object> c = rows.get(0);
        String currentStatus = String.valueOf(c.get("status"));
        if (!STATUS_PENDING_SHOP.equals(currentStatus) && !"PENDING".equals(currentStatus)) {
            throw new IllegalArgumentException("Chỉ có thể xử lý khiếu nại khi đang ở trạng thái Chờ Shop phản hồi (trạng thái hiện tại: "
                    + STATUS_LABELS.getOrDefault(currentStatus, currentStatus) + ").");
        }

        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!"ACCEPT".equals(normalizedDecision) && !"REJECT".equals(normalizedDecision)) {
            throw new IllegalArgumentException("Quyết định xử lý của Shop không hợp lệ (chỉ chấp nhận ACCEPT hoặc REJECT).");
        }

        LocalDateTime now = LocalDateTime.now();
        Long customerId = ((Number) c.get("user_id")).longValue();
        Long orderId = ((Number) c.get("order_id")).longValue();
        String complaintCode = String.valueOf(c.get("complaint_code"));
        String orderCode = String.valueOf(c.get("order_code"));

        if ("ACCEPT".equals(normalizedDecision)) {
            long requestedRefund = toLong(c.get("requested_refund_amount"));
            String cleanNote = (shopResponse == null || shopResponse.isBlank())
                    ? "Cửa hàng đồng ý phương án đền bù cho khách hàng."
                    : shopResponse.trim();
            if (cleanNote.length() > 1500) {
                throw new IllegalArgumentException("Nội dung phản hồi không được vượt quá 1500 ký tự.");
            }
            String resType = requestedRefund > 0 ? "COMPENSATE_POINTS" : "EXPLANATION_ONLY";

            int updated = jdbc.update("""
                UPDATE complaints
                SET status = 'RESOLVED',
                    shop_decision = 'ACCEPT',
                    shop_response = ?,
                    shop_responded_at = ?,
                    final_refund_amount = ?,
                    compensated_points = ?,
                    resolution_type = ?,
                    resolution_note = ?,
                    is_shop_fault = 1,
                    resolved_by = ?,
                    resolved_at = ?,
                    updated_at = ?
                WHERE id = ? AND status IN ('PENDING_SHOP', 'PENDING')
            """, cleanNote, Timestamp.valueOf(now), requestedRefund, requestedRefund,
                    resType, cleanNote, ownerId, Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

            if (updated == 0) {
                throw new IllegalArgumentException("Khiếu nại đã được cập nhật bởi thao tác khác, vui lòng tải lại trang.");
            }

            if (requestedRefund > 0) {
                creditCustomerRefundPoints(customerId, requestedRefund);
            }

            createNotificationSafe(customerId,
                    "COMPLAINT_SHOP_ACCEPTED_" + complaintId,
                    "COMPLAINT",
                    "Shop đã đồng ý giải quyết khiếu nại " + complaintCode,
                    "Cửa hàng đã chấp nhận khiếu nại cho đơn " + orderCode
                            + (requestedRefund > 0 ? " và hoàn +" + String.format("%,d", requestedRefund) + " điểm thưởng vào tài khoản của bạn." : ".")
                            + " Phản hồi của Shop: " + cleanNote,
                    "COMPLAINT", complaintId);
        } else {
            // REJECT -> Bắt buộc nhập lý do từ chối (+ ảnh đối chứng nếu có) -> chuyển sang ESCALATED
            String cleanReason = shopResponse != null ? shopResponse.trim() : "";
            if (cleanReason.length() < 5 || cleanReason.length() > 1500) {
                throw new IllegalArgumentException("Khi từ chối khiếu nại, Shop bắt buộc phải nhập lý do giải trình từ 5 đến 1500 ký tự.");
            }
            String shopEvidenceUrl = storeEvidenceImageIfPresent(shopEvidenceImage);

            int updated = jdbc.update("""
                UPDATE complaints
                SET status = 'ESCALATED',
                    shop_decision = 'REJECT',
                    shop_response = ?,
                    shop_evidence_image_url = COALESCE(?, shop_evidence_image_url),
                    shop_responded_at = ?,
                    escalated_at = ?,
                    updated_at = ?
                WHERE id = ? AND status IN ('PENDING_SHOP', 'PENDING')
            """, cleanReason, shopEvidenceUrl, Timestamp.valueOf(now), Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

            if (updated == 0) {
                throw new IllegalArgumentException("Khiếu nại đã được cập nhật bởi thao tác khác, vui lòng tải lại trang.");
            }

            createNotificationSafe(customerId,
                    "COMPLAINT_SHOP_REJECTED_" + complaintId,
                    "COMPLAINT",
                    "Khiếu nại " + complaintCode + " đã chuyển CSKH phân xử",
                    "Cửa hàng đã từ chối yêu cầu ban đầu trên đơn " + orderCode + " (Lý do: " + cleanReason + "). Hồ sơ đã được chuyển lên Bộ phận CSKH để phân xử.",
                    "COMPLAINT", complaintId);

            notifyRoleUsers("CS_STAFF",
                    "COMPLAINT_ESCALATED_CS_" + complaintId,
                    "COMPLAINT",
                    "⚖️ Khiếu nại " + complaintCode + " chuyển CSKH phân xử (Đơn " + orderCode + ")",
                    "Shop vừa từ chối khiếu nại với giải trình: \"" + cleanReason + "\". Vui lòng kiểm tra bằng chứng hai bên và phân xử.",
                    "COMPLAINT", complaintId);
        }
    }

    /**
     * Overload tương thích ngược khi gọi submitShopResponse (mặc định là Shop gửi giải trình từ chối để chuyển CSKH phân xử).
     */
    @Transactional
    public void submitShopResponse(Long ownerId, Long complaintId, String shopResponse) {
        submitShopDecision(ownerId, complaintId, "REJECT", shopResponse, null);
    }

    /**
     * Scheduled Job: Tự động chuyển các khiếu nại PENDING_SHOP quá hạn SLA (6h) mà Shop không phản hồi sang ESCALATED.
     */
    @Scheduled(fixedDelayString = "${complaints.sla-scan-ms:60000}")
    @Transactional
    public int autoEscalateOverdueShopComplaints() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> overdueIds = safeQueryList("""
            SELECT id FROM complaints
            WHERE status IN ('PENDING_SHOP', 'PENDING') AND sla_due_at <= ?
            ORDER BY id
        """, Timestamp.valueOf(now)).stream()
                .map(r -> ((Number) r.get("id")).longValue())
                .toList();

        int escalatedCount = 0;
        for (Long complaintId : overdueIds) {
            List<Map<String, Object>> locked = jdbc.queryForList("""
                SELECT c.id, c.complaint_code, c.user_id, c.order_id, o.order_code, s.owner_id AS shop_owner_id
                FROM complaints c
                JOIN orders o ON o.id = c.order_id
                JOIN shops s ON s.id = c.shop_id
                WHERE c.id = ? AND c.status IN ('PENDING_SHOP', 'PENDING') AND c.sla_due_at <= ?
                FOR UPDATE
            """, complaintId, Timestamp.valueOf(now));
            if (locked.isEmpty()) {
                continue;
            }
            Map<String, Object> row = locked.get(0);
            int updated = jdbc.update("""
                UPDATE complaints
                SET status = 'ESCALATED',
                    escalated_at = ?,
                    updated_at = ?
                WHERE id = ? AND status IN ('PENDING_SHOP', 'PENDING')
            """, Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

            if (updated == 1) {
                escalatedCount++;
                Long customerId = ((Number) row.get("user_id")).longValue();
                Long orderId = ((Number) row.get("order_id")).longValue();
                Long shopOwnerId = row.get("shop_owner_id") instanceof Number n ? n.longValue() : null;
                String complaintCode = String.valueOf(row.get("complaint_code"));
                String orderCode = String.valueOf(row.get("order_code"));

                createNotificationSafe(customerId,
                        "COMPLAINT_SLA_ESCALATED_CUST_" + complaintId,
                        "COMPLAINT",
                        "Khiếu nại " + complaintCode + " đã chuyển lên CSKH phân xử",
                        "Do Cửa hàng không phản hồi trong thời hạn SLA 6 giờ, hệ thống đã tự động chuyển khiếu nại đơn " + orderCode + " lên Bộ phận CSKH phân xử.",
                        "COMPLAINT", complaintId);

                if (shopOwnerId != null) {
                    createNotificationSafe(shopOwnerId,
                            "COMPLAINT_SLA_ESCALATED_SHOP_" + complaintId,
                            "COMPLAINT",
                            "⚠️ Quá hạn 6h phản hồi khiếu nại " + complaintCode,
                            "Khiếu nại trên đơn hàng " + orderCode + " đã quá hạn SLA 6 giờ và được chuyển thẳng lên CSKH phân xử.",
                            "ORDER", orderId);
                }

                notifyRoleUsers("CS_STAFF",
                        "COMPLAINT_SLA_ESCALATED_CS_" + complaintId,
                        "COMPLAINT",
                        "⏰ [Quá hạn SLA 6h] Khiếu nại " + complaintCode + " chuyển CSKH phân xử",
                        "Shop không phản hồi khiếu nại đơn " + orderCode + " trong 6 giờ. Vui lòng tiếp nhận phân xử.",
                        "COMPLAINT", complaintId);
            }
        }
        return escalatedCount;
    }

    /**
     * CSKH tạo phiếu hỗ trợ/khiếu nại thủ công hoặc tự động 1-click từ đơn hàng gặp sự cố.
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
            FOR UPDATE
        """, cleanCode);

        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng có mã: " + cleanCode);
        }
        Map<String, Object> order = orders.get(0);
        Long orderId = ((Number) order.get("id")).longValue();
        Long customerId = order.get("user_id") instanceof Number n ? n.longValue() : csUserId;
        Long shopId = ((Number) order.get("shop_id")).longValue();

        Long existingNonCancelled = jdbc.queryForObject("""
            SELECT COUNT(*) FROM complaints
            WHERE order_id = ? AND status <> 'CANCELLED'
        """, Long.class, orderId);
        if (existingNonCancelled != null && existingNonCancelled > 0) {
            throw new IllegalArgumentException("Đơn hàng " + cleanCode + " đã có phiếu khiếu nại.");
        }

        String cleanReason = (reasonType == null || !REASON_LABELS.containsKey(reasonType.trim()))
                ? "DELIVERY_OR_SERVICE" : reasonType.trim();
        String cleanTitle = (title == null || title.isBlank())
                ? REASON_LABELS.get(cleanReason) : title.trim();
        String cleanDesc = (description == null || description.trim().length() < 5)
                ? "Phiếu hỗ trợ được khởi tạo bởi bộ phận CSKH cho đơn hàng " + cleanCode + "."
                : description.trim();
        String cleanSource = "AUTO_SYSTEM".equals(sourceType) ? "AUTO_SYSTEM" : "CS_MANUAL";

        BigDecimal totalAmount = toBigDecimal(order.get("total_amount"));
        String priority = determineAutoPriority(cleanReason, totalAmount);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slaDueAt = now.plusHours(SHOP_SLA_HOURS);
        String complaintCode = generateComplaintCode(now);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO complaints (
                    complaint_code, order_id, user_id, shop_id,
                    reason_type, title, description,
                    priority, status, source_type, requested_refund_amount, final_refund_amount,
                    assigned_to, escalated_at, sla_due_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ESCALATED', ?, 0, 0, ?, ?, ?, ?, ?)
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
            ps.setTimestamp(11, Timestamp.valueOf(now));
            ps.setTimestamp(12, Timestamp.valueOf(slaDueAt));
            ps.setTimestamp(13, Timestamp.valueOf(now));
            ps.setTimestamp(14, Timestamp.valueOf(now));
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
     * Nhân viên CSKH gửi thông báo yêu cầu / đôn đốc Chủ Shop giải trình khiếu nại.
     */
    @Transactional
    public void requestShopResponse(Long complaintId, Long csUserId, String requestNote) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.id, c.complaint_code, c.order_id, c.status, c.sla_due_at,
                   c.shop_remind_count, o.order_code, s.name AS shop_name, s.owner_id AS shop_owner_id
            FROM complaints c
            JOIN orders o ON o.id = c.order_id
            JOIN shops s ON s.id = c.shop_id
            WHERE c.id = ?
            FOR UPDATE
        """, complaintId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy phiếu khiếu nại #" + complaintId);
        }
        Map<String, Object> c = rows.get(0);
        String status = String.valueOf(c.get("status"));
        if (STATUS_RESOLVED.equals(status) || STATUS_REJECTED.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw new IllegalArgumentException("Phiếu khiếu nại này đã đóng, không thể gửi yêu cầu giải trình.");
        }

        String cleanNote = (requestNote == null || requestNote.isBlank())
                ? "Bộ phận CSKH đề nghị Cửa hàng kiểm tra lại đơn hàng và gửi phản hồi giải trình."
                : requestNote.trim();
        if (cleanNote.length() > 1000) {
            throw new IllegalArgumentException("Nội dung yêu cầu giải trình không được vượt quá 1000 ký tự.");
        }

        LocalDateTime now = LocalDateTime.now();
        long nextRemindCount = toLong(c.get("shop_remind_count")) + 1;

        jdbc.update("""
            UPDATE complaints
            SET assigned_to = COALESCE(assigned_to, ?),
                shop_remind_count = ?,
                last_reminded_at = ?,
                cs_shop_request_note = ?,
                updated_at = ?
            WHERE id = ?
        """, csUserId, nextRemindCount, Timestamp.valueOf(now), cleanNote, Timestamp.valueOf(now), complaintId);

        Long shopOwnerId = c.get("shop_owner_id") instanceof Number n ? n.longValue() : null;
        Long orderId = ((Number) c.get("order_id")).longValue();
        String complaintCode = String.valueOf(c.get("complaint_code"));
        String orderCode = String.valueOf(c.get("order_code"));
        LocalDateTime slaDueAt = toLocalDateTime(c.get("sla_due_at"));
        String slaText = slaDueAt != null ? slaDueAt.format(DT_FMT) : "sớm nhất";

        if (shopOwnerId != null) {
            createNotificationSafe(shopOwnerId,
                    "COMPLAINT_REMIND_SHOP_" + complaintId + "_" + System.currentTimeMillis(),
                    "COMPLAINT",
                    "⚠️ [Yêu cầu giải trình] Khiếu nại " + complaintCode + " (Đơn " + orderCode + ")",
                    "CSKH yêu cầu phản hồi (Lần " + nextRemindCount + "): \"" + cleanNote
                            + "\". Vui lòng kiểm tra chi tiết đơn hàng trước " + slaText + ".",
                    "ORDER", orderId);
        }
    }

    // =========================================================================
    // 3. DANH SÁCH YÊU CẦU KHIẾU NẠI (CUSTOMER, CS_STAFF, SHOP_OWNER)
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
            String normalized = statusFilter.trim().toUpperCase(Locale.ROOT);
            if ("PENDING".equals(normalized) || STATUS_PENDING_SHOP.equals(normalized)) {
                sql.append(" AND c.status IN ('PENDING_SHOP', 'PENDING') ");
            } else if ("IN_PROGRESS".equals(normalized) || STATUS_ESCALATED.equals(normalized)) {
                sql.append(" AND c.status IN ('ESCALATED', 'IN_PROGRESS') ");
            } else {
                sql.append(" AND c.status = ? ");
                params.add(normalized);
            }
        }
        sql.append(" ORDER BY c.created_at DESC, c.id DESC");

        List<Map<String, Object>> list = safeQueryList(sql.toString(), params.toArray());
        return enrichComplaintRows(list);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countCustomerComplaintsByStatus(Long customerId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ?", customerId));
        counts.put("pending_shop", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status IN ('PENDING_SHOP', 'PENDING')", customerId));
        counts.put("escalated", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status IN ('ESCALATED', 'IN_PROGRESS')", customerId));
        counts.put("resolved", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'RESOLVED'", customerId));
        counts.put("rejected", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'REJECTED'", customerId));
        counts.put("cancelled", queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'CANCELLED'", customerId));
        // Giữ alias cho template cũ nếu có
        counts.put("pending", counts.get("pending_shop"));
        counts.put("in_progress", counts.get("escalated"));
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
            String norm = statusFilter.trim().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(norm)) {
                sql.append(" AND c.status IN ('PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS') ");
            } else if ("PENDING".equals(norm) || STATUS_PENDING_SHOP.equals(norm)) {
                sql.append(" AND c.status IN ('PENDING_SHOP', 'PENDING') ");
            } else if ("IN_PROGRESS".equals(norm) || STATUS_ESCALATED.equals(norm)) {
                sql.append(" AND c.status IN ('ESCALATED', 'IN_PROGRESS') ");
            } else {
                sql.append(" AND c.status = ? ");
                params.add(norm);
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
                CASE WHEN c.status IN ('ESCALATED', 'IN_PROGRESS') THEN 0
                     WHEN c.status IN ('PENDING_SHOP', 'PENDING') THEN 1
                     ELSE 2 END ASC,
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
    // 4. XEM CHI TIẾT VÀ CSKH PHÂN XỬ KHIẾU NẠI (TẦNG 2 - ESCALATED)
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
                   o.order_code, o.total_amount, o.subtotal, o.voucher_discount, o.points_discount,
                   o.shipping_fee, o.status AS order_status,
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

        Map<String, Object> raw = rows.get(0);
        Map<String, Object> detail = enrichSingleComplaintRow(raw);
        Long orderId = ((Number) detail.get("order_id")).longValue();
        Long shopId = ((Number) detail.get("shop_id")).longValue();
        Long orderItemId = detail.get("order_item_id") instanceof Number n ? n.longValue() : null;

        long actualPaidAmount = calculateActualPaidAmount(orderId, orderItemId);
        detail.put("actual_paid_amount", actualPaidAmount);

        List<Map<String, Object>> orderItems = safeQueryList("""
            SELECT id, variant_id, product_name, variant_name, sku, image_url,
                   base_unit_name, quantity, unit_price, sale_unit_price, line_total
            FROM order_items
            WHERE order_id = ?
            ORDER BY id
        """, orderId);
        detail.put("orderItems", orderItems);

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
     * Chuyển phiếu từ PENDING_SHOP sang ESCALATED khi CSKH tiếp nhận can thiệp (hoặc gán người phụ trách).
     */
    @Transactional
    public void startProcessing(Long complaintId, Long csUserId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, complaint_code, user_id, order_id, status FROM complaints WHERE id = ? FOR UPDATE", complaintId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy yêu cầu khiếu nại.");
        }
        Map<String, Object> c = rows.get(0);
        String status = String.valueOf(c.get("status"));
        if (!STATUS_PENDING_SHOP.equals(status) && !STATUS_ESCALATED.equals(status)
                && !"PENDING".equals(status) && !"IN_PROGRESS".equals(status)) {
            throw new IllegalArgumentException("Phiếu khiếu nại này đã kết thúc.");
        }

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            UPDATE complaints
            SET status = 'ESCALATED',
                escalated_at = COALESCE(escalated_at, ?),
                assigned_to = ?,
                updated_at = ?
            WHERE id = ?
        """, Timestamp.valueOf(now), csUserId, Timestamp.valueOf(now), complaintId);
    }

    /**
     * CSKH phân xử (chỉ thực hiện được khi đơn đang ở ESCALATED):
     * - RESOLVED (Chấp nhận khiếu nại): CSKH có quyền nhập/điều chỉnh lại finalRefundAmount (0 <= finalRefundAmount <= actualPaidAmount)
     *   -> Cộng điểm cho Customer theo finalRefundAmount và chuyển sang RESOLVED.
     * - REJECTED (Bác bỏ khiếu nại): Bắt buộc nhập lý do bác bỏ, finalRefundAmount = 0 + chuyển sang REJECTED.
     */
    @Transactional
    public void resolveComplaint(
            Long complaintId,
            Long csUserId,
            String decisionStatus,
            String resolutionType,
            String resolutionNote,
            Long finalRefundAmountInput,
            boolean isShopFault,
            boolean escalateToOps) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.*, o.order_code, o.status AS order_status, s.name AS shop_name, s.owner_id AS shop_owner_id
            FROM complaints c
            JOIN orders o ON o.id = c.order_id
            JOIN shops s ON s.id = c.shop_id
            WHERE c.id = ?
            FOR UPDATE
        """, complaintId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy phiếu khiếu nại #" + complaintId);
        }
        Map<String, Object> c = rows.get(0);
        String currentStatus = String.valueOf(c.get("status"));

        // Chỉ thực hiện phân xử khi khiếu nại đang ở ESCALATED
        if (!STATUS_ESCALATED.equals(currentStatus) && !"IN_PROGRESS".equals(currentStatus)) {
            if (STATUS_PENDING_SHOP.equals(currentStatus) || "PENDING".equals(currentStatus)) {
                throw new IllegalArgumentException("Khiếu nại đang ở trạng thái Chờ Shop phản hồi (PENDING_SHOP). CSKH chỉ phân xử khi Shop đã từ chối hoặc quá hạn SLA 6h chuyển sang ESCALATED.");
            }
            throw new IllegalArgumentException("Phiếu khiếu nại này không ở trạng thái Chờ CSKH phân xử (trạng thái hiện tại: "
                    + STATUS_LABELS.getOrDefault(currentStatus, currentStatus) + ").");
        }

        String finalStatus = STATUS_REJECTED.equalsIgnoreCase(decisionStatus) ? STATUS_REJECTED : STATUS_RESOLVED;
        String cleanNote = resolutionNote != null ? resolutionNote.trim() : "";
        if (cleanNote.length() < 5 || cleanNote.length() > 1500) {
            throw new IllegalArgumentException("Bắt buộc nhập lý do phân xử / bác bỏ từ 5 đến 1500 ký tự.");
        }

        Long orderId = ((Number) c.get("order_id")).longValue();
        Long orderItemId = c.get("order_item_id") instanceof Number n ? n.longValue() : null;
        String orderStatus = String.valueOf(c.get("order_status"));
        long actualPaidAmount = ("SHIPPING".equals(orderStatus) || "CANCELLED".equals(orderStatus))
                ? 0L : calculateActualPaidAmount(orderId, orderItemId);

        long finalRefund = 0L;
        String finalResType;

        if (STATUS_REJECTED.equals(finalStatus)) {
            finalRefund = 0L;
            finalResType = "REJECTED";
        } else {
            long requestedRefund = toLong(c.get("requested_refund_amount"));
            finalRefund = finalRefundAmountInput != null ? finalRefundAmountInput : requestedRefund;
            if (finalRefund < 0) {
                throw new IllegalArgumentException("Số tiền/điểm đền bù thực tế (finalRefundAmount) không được âm.");
            }
            if (finalRefund > actualPaidAmount) {
                throw new IllegalArgumentException("Số tiền/điểm đền bù thực tế (" + finalRefund + "đ) không được vượt quá số tiền thực trả (" + actualPaidAmount + "đ).");
            }
            if (resolutionType != null && RESOLUTION_LABELS.containsKey(resolutionType) && !"REJECTED".equals(resolutionType)) {
                finalResType = resolutionType;
            } else {
                finalResType = finalRefund > 0 ? "COMPENSATE_POINTS" : "EXPLANATION_ONLY";
            }
        }

        boolean finalShopFault = STATUS_RESOLVED.equals(finalStatus) && (isShopFault || "SHOP_WARNING_ESCALATE".equals(finalResType));
        boolean finalEscalate = finalShopFault && (escalateToOps || "SHOP_WARNING_ESCALATE".equals(finalResType));

        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("""
            UPDATE complaints
            SET status = ?,
                resolution_type = ?,
                resolution_note = ?,
                final_refund_amount = ?,
                compensated_points = ?,
                is_shop_fault = ?,
                escalated_to_ops = ?,
                assigned_to = COALESCE(assigned_to, ?),
                resolved_by = ?,
                resolved_at = ?,
                updated_at = ?
            WHERE id = ? AND status IN ('ESCALATED', 'IN_PROGRESS')
        """,
                finalStatus,
                finalResType,
                cleanNote,
                finalRefund,
                finalRefund,
                finalShopFault ? 1 : 0,
                finalEscalate ? 1 : 0,
                csUserId,
                csUserId,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                complaintId);

        if (updated == 0) {
            throw new IllegalArgumentException("Trạng thái khiếu nại vừa thay đổi bởi thao tác khác, vui lòng tải lại trang.");
        }

        Long customerId = ((Number) c.get("user_id")).longValue();
        Long shopId = ((Number) c.get("shop_id")).longValue();
        Long shopOwnerId = c.get("shop_owner_id") instanceof Number n ? n.longValue() : null;
        String complaintCode = String.valueOf(c.get("complaint_code"));
        String orderCode = String.valueOf(c.get("order_code"));
        String shopName = String.valueOf(c.get("shop_name"));

        // Cộng điểm cho Customer theo finalRefundAmount khi RESOLVED
        if (STATUS_RESOLVED.equals(finalStatus) && finalRefund > 0) {
            creditCustomerRefundPoints(customerId, finalRefund);
        }

        String custTitle = STATUS_RESOLVED.equals(finalStatus)
                ? "Khiếu nại " + complaintCode + " đã được CSKH giải quyết"
                : "Khiếu nại " + complaintCode + " đã bị bác bỏ";
        String custBody = "Đơn hàng " + orderCode + " — Phán quyết CSKH: " + RESOLUTION_LABELS.getOrDefault(finalResType, finalResType)
                + (finalRefund > 0 ? " (Đã cộng +" + String.format("%,d", finalRefund) + " điểm thưởng vào tài khoản của bạn)." : ".")
                + " Lý do: " + cleanNote;
        createNotificationSafe(customerId,
                "COMPLAINT_RESOLVED_CUST_" + complaintId,
                "COMPLAINT",
                custTitle,
                custBody,
                "COMPLAINT", complaintId);

        if (shopOwnerId != null) {
            String shopTitle = "Phán quyết CSKH cho khiếu nại " + complaintCode + " (Đơn " + orderCode + ")";
            String shopBody = "Kết quả: " + STATUS_LABELS.getOrDefault(finalStatus, finalStatus)
                    + (finalRefund > 0 ? " — Mức đền bù duyệt: " + String.format("%,d", finalRefund) + " điểm." : ".")
                    + (finalShopFault ? " [Ghi nhận lỗi của Shop]." : "")
                    + " Nội dung: " + cleanNote;
            createNotificationSafe(shopOwnerId,
                    "COMPLAINT_RESOLVED_SHOP_" + complaintId,
                    "COMPLAINT",
                    shopTitle,
                    shopBody,
                    "ORDER", orderId);
        }

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

    private void creditCustomerRefundPoints(Long customerId, long points) {
        if (customerId == null || points <= 0) return;
        jdbc.update("UPDATE users SET points_balance = COALESCE(points_balance, 0) + ? WHERE id = ?", points, customerId);
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
    // 5. NĂM TÍNH NĂNG BÁO CÁO & GIÁM SÁT (OPERATIONS & CS DASHBOARD)
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

    private Map<String, Object> buildDisputeReport() {
        Map<String, Object> report = new HashMap<>();

        long totalOrders = queryCount("SELECT COUNT(*) FROM orders");
        long totalDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status <> 'CANCELLED'");
        long pendingDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status IN ('PENDING_SHOP', 'PENDING')");
        long inProgressDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status IN ('ESCALATED', 'IN_PROGRESS')");
        long resolvedDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status = 'RESOLVED'");
        long rejectedDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status = 'REJECTED'");
        long shopFaultDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE is_shop_fault = 1");
        long escalatedDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE escalated_to_ops = 1");
        long distinctDisputedOrders = queryCount("SELECT COUNT(DISTINCT order_id) FROM complaints WHERE status <> 'CANCELLED'");

        Long totalCompensatedPoints = 0L;
        try {
            totalCompensatedPoints = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(COALESCE(NULLIF(final_refund_amount, 0), compensated_points)), 0) FROM complaints WHERE status = 'RESOLVED'",
                    Long.class);
        } catch (Exception ignored) {
        }

        double disputeRatePct = totalOrders > 0 ? Math.round((distinctDisputedOrders * 1000.0) / totalOrders) / 10.0 : 0.0;
        long closedDisputes = resolvedDisputes + rejectedDisputes;
        double resolutionRatePct = totalDisputes > 0 ? Math.round((closedDisputes * 1000.0) / totalDisputes) / 10.0 : 100.0;

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
        double totalResolutionMinutes = 0;

        long highTotal = 0, highProcessed = 0, highMetSla = 0;
        double highTotalMinutes = 0;
        long normalTotal = 0, normalProcessed = 0, normalMetSla = 0;
        double normalTotalMinutes = 0;

        for (Map<String, Object> r : rows) {
            String status = String.valueOf(r.get("status"));
            String priority = String.valueOf(r.get("priority"));
            LocalDateTime createdAt = toLocalDateTime(r.get("created_at"));
            LocalDateTime slaDueAt = toLocalDateTime(r.get("sla_due_at"));
            LocalDateTime resolvedAt = toLocalDateTime(r.get("resolved_at"));

            if (createdAt == null || slaDueAt == null) continue;

            boolean isHigh = "HIGH".equals(priority);
            if (isHigh) highTotal++; else normalTotal++;

            if (STATUS_RESOLVED.equals(status) || STATUS_REJECTED.equals(status)) {
                processedTickets++;
                LocalDateTime finishTime = resolvedAt != null ? resolvedAt : now;
                long mins = Math.max(1, Duration.between(createdAt, finishTime).toMinutes());
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
                } else if (Duration.between(now, slaDueAt).toMinutes() <= 120) {
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

    private List<Map<String, Object>> buildAutoSuggestIssues() {
        return safeQueryList("""
            SELECT o.id AS order_id, o.order_code, o.recipient_name, o.recipient_phone,
                   o.status, o.delivery_status, o.cancellation_reason, o.delivery_failure_reason,
                   s.name AS shop_name
            FROM orders o
            JOIN shops s ON s.id = o.shop_id
            WHERE (o.status = 'CANCELLED' OR o.delivery_status = 'FAILED')
              AND NOT EXISTS (SELECT 1 FROM complaints c WHERE c.order_id = o.id AND c.status <> 'CANCELLED')
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
        String rawStatus = String.valueOf(row.getOrDefault("status", STATUS_PENDING_SHOP));
        String status = switch (rawStatus) {
            case "PENDING" -> STATUS_PENDING_SHOP;
            case "IN_PROGRESS" -> STATUS_ESCALATED;
            default -> rawStatus;
        };
        row.put("status", status);

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

        long requestedRefund = toLong(row.get("requested_refund_amount"));
        long finalRefund = toLong(row.get("final_refund_amount"));
        if (finalRefund == 0 && STATUS_RESOLVED.equals(status)) {
            finalRefund = toLong(row.get("compensated_points"));
        }
        row.put("requested_refund_amount", requestedRefund);
        row.put("final_refund_amount", finalRefund);
        row.put("compensated_points", finalRefund);

        LocalDateTime createdAt = toLocalDateTime(row.get("created_at"));
        LocalDateTime slaDueAt = toLocalDateTime(row.get("sla_due_at"));
        LocalDateTime resolvedAt = toLocalDateTime(row.get("resolved_at"));
        LocalDateTime shopRespondedAt = toLocalDateTime(row.get("shop_responded_at"));
        LocalDateTime escalatedAt = toLocalDateTime(row.get("escalated_at"));
        LocalDateTime lastRemindedAt = toLocalDateTime(row.get("last_reminded_at"));
        LocalDateTime now = LocalDateTime.now();

        row.put("created_at_fmt", createdAt != null ? createdAt.format(DT_FMT) : "—");
        row.put("sla_due_at_fmt", slaDueAt != null ? slaDueAt.format(DT_FMT) : "—");
        row.put("resolved_at_fmt", resolvedAt != null ? resolvedAt.format(DT_FMT) : "—");
        row.put("shop_responded_at_fmt", shopRespondedAt != null ? shopRespondedAt.format(DT_FMT) : "—");
        row.put("escalated_at_fmt", escalatedAt != null ? escalatedAt.format(DT_FMT) : "—");
        row.put("last_reminded_at_fmt", lastRemindedAt != null ? lastRemindedAt.format(DT_FMT) : "—");

        long remindCount = toLong(row.get("shop_remind_count"));
        row.put("shop_remind_count", remindCount);
        boolean hasShopResponded = row.get("shop_response") != null && !String.valueOf(row.get("shop_response")).isBlank();
        boolean isOverdueSla = slaDueAt != null && now.isAfter(slaDueAt);
        row.put("has_shop_responded", hasShopResponded);
        row.put("can_penalize_shop", true);

        String slaState = "ON_TIME";
        String slaBadgeText = "Trong hạn";
        if (slaDueAt != null) {
            if (STATUS_RESOLVED.equals(status) || STATUS_REJECTED.equals(status)) {
                LocalDateTime finish = resolvedAt != null ? resolvedAt : now;
                double hoursTaken = Math.max(0.1, Math.round((Duration.between(createdAt != null ? createdAt : finish, finish).toMinutes() / 60.0) * 10.0) / 10.0);
                if (!finish.isAfter(slaDueAt)) {
                    slaState = "MET_SLA";
                    slaBadgeText = "Đúng hạn (" + hoursTaken + "h)";
                } else {
                    slaState = "BREACHED_SLA";
                    slaBadgeText = "Trễ hạn (" + hoursTaken + "h)";
                }
            } else if (STATUS_PENDING_SHOP.equals(status) || STATUS_ESCALATED.equals(status)) {
                long minsRemaining = Duration.between(now, slaDueAt).toMinutes();
                if (minsRemaining < 0) {
                    double overdueHours = Math.round((Math.abs(minsRemaining) / 60.0) * 10.0) / 10.0;
                    slaState = "OVERDUE";
                    slaBadgeText = "Quá hạn " + overdueHours + "h";
                } else if (minsRemaining <= 120) {
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

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.longValue());
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
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
