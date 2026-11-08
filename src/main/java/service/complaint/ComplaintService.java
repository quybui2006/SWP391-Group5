package service.complaint;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import service.operations.CustomerRefundService;

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

    public static final String STATUS_PENDING_OPERATIONS = ComplaintStatus.PENDING_OPERATIONS.getCode();
    public static final String STATUS_PENDING_SHOP = ComplaintStatus.PENDING_SHOP.getCode();
    public static final String STATUS_ESCALATED = ComplaintStatus.ESCALATED.getCode();
    public static final String STATUS_RESOLVED = ComplaintStatus.RESOLVED.getCode();
    public static final String STATUS_REJECTED = ComplaintStatus.REJECTED.getCode();
    public static final String STATUS_CANCELLED = ComplaintStatus.CANCELLED.getCode();

    public static final Set<String> PRODUCT_DEFECT_REASONS = Set.of(
            "QUALITY_ISSUE",
            "QUANTITY_SHORTAGE",
            "WRONG_PRODUCT",
            "WEIGHT_OR_QUANTITY_SHORTAGE",
            "WRONG_SPECIFICATION",
            "DELIVERY_ISSUE"
    );

    public static final Set<String> ACTIVE_REASONS = Set.of(
            "QUALITY_ISSUE",
            "QUANTITY_SHORTAGE",
            "WRONG_PRODUCT",
            "DELIVERY_ISSUE"
    );

    public static final Set<String> SERVICE_ISSUE_REASONS = Set.of(
            "PACKAGING_DAMAGE",
            "DELIVERY_OR_SERVICE",
            "OTHER"
    );

    public static final Set<String> SHIPPING_OR_CANCELLED_ALLOWED_REASONS = Set.of(
            "DELIVERY_ISSUE",
            "DELIVERY_OR_SERVICE",
            "OTHER"
    );

    public static final Map<String, String> REASON_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> ACTIVE_REASON_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> DELIVERY_ONLY_REASON_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> RESOLUTION_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> DESIRED_RESOLUTION_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> PRIORITY_LABELS = new LinkedHashMap<>();

    static {
        ACTIVE_REASON_LABELS.put("QUALITY_ISSUE", "Chất lượng hoa quả không đạt (dập nát, hư hỏng)");
        ACTIVE_REASON_LABELS.put("QUANTITY_SHORTAGE", "Thiếu số lượng / thiếu cân");
        ACTIVE_REASON_LABELS.put("WRONG_PRODUCT", "Giao sai sản phẩm / sai loại");
        ACTIVE_REASON_LABELS.put("DELIVERY_ISSUE", "Sự cố giao hàng (Hư hỏng do vận chuyển / giao trễ / shipper)");

        DELIVERY_ONLY_REASON_LABELS.put("DELIVERY_ISSUE", "Sự cố giao hàng (Từ chối nhận hàng / hư hỏng do vận chuyển / giao trễ / shipper)");
        DELIVERY_ONLY_REASON_LABELS.put("DELIVERY_OR_SERVICE", "Sự cố giao hàng / thái độ phục vụ");
        DELIVERY_ONLY_REASON_LABELS.put("OTHER", "Lý do khác liên quan đến giao nhận");

        REASON_LABELS.putAll(ACTIVE_REASON_LABELS);
        REASON_LABELS.put("WEIGHT_OR_QUANTITY_SHORTAGE", "Giao thiếu số lượng / thiếu cân");
        REASON_LABELS.put("WRONG_SPECIFICATION", "Sai chủng loại / sai kích cỡ (Biến thể)");
        REASON_LABELS.put("PACKAGING_DAMAGE", "Quy cách đóng gói / bảo quản kém");
        REASON_LABELS.put("DELIVERY_OR_SERVICE", "Sự cố giao hàng / thái độ phục vụ");
        REASON_LABELS.put("OTHER", "Lý do khác");

        DESIRED_RESOLUTION_LABELS.put("RESEND_ITEMS", "Giao bù hàng");
        DESIRED_RESOLUTION_LABELS.put("ISSUE_VOUCHER", "Đền bù voucher");
        DESIRED_RESOLUTION_LABELS.put("RETURN_REFUND", "Hoàn tiền ngân hàng");

        RESOLUTION_LABELS.put("RETURN_REFUND", "Chấp thuận & Hoàn tiền về Tài khoản ngân hàng");
        RESOLUTION_LABELS.put("RESEND_ITEMS", "Chấp thuận & Giao bù hàng (Đơn mới 0đ)");
        RESOLUTION_LABELS.put("ISSUE_VOUCHER", "Chấp thuận & Tặng voucher đền bù");
        RESOLUTION_LABELS.put("REJECTED", "Từ chối khiếu nại");
        // Tương thích hiển thị bản ghi cũ
        RESOLUTION_LABELS.put("COMPENSATE_POINTS", "Chấp thuận & Hoàn tiền vào Ví tiền của Khách");
        RESOLUTION_LABELS.put("SHOP_WARNING_ESCALATE", "Nhắc nhở / Cảnh cáo Shop & Chuyển Hậu kiểm");
        RESOLUTION_LABELS.put("EXPLANATION_ONLY", "Ghi nhận phản ánh dịch vụ & Nhắc nhở Shop");

        STATUS_LABELS.put(STATUS_PENDING_OPERATIONS, ComplaintStatus.PENDING_OPERATIONS.getLabel());
        STATUS_LABELS.put(STATUS_PENDING_SHOP, ComplaintStatus.PENDING_SHOP.getLabel());
        STATUS_LABELS.put(STATUS_ESCALATED, ComplaintStatus.ESCALATED.getLabel());
        STATUS_LABELS.put(STATUS_RESOLVED, ComplaintStatus.RESOLVED.getLabel());
        STATUS_LABELS.put(STATUS_REJECTED, ComplaintStatus.REJECTED.getLabel());
        STATUS_LABELS.put(STATUS_CANCELLED, ComplaintStatus.CANCELLED.getLabel());
        STATUS_LABELS.put("PENDING_OPS", ComplaintStatus.PENDING_OPERATIONS.getLabel());
        STATUS_LABELS.put("PENDING", ComplaintStatus.PENDING_OPERATIONS.getLabel());
        STATUS_LABELS.put("IN_PROGRESS", ComplaintStatus.ESCALATED.getLabel());

        PRIORITY_LABELS.put("HIGH", "Khẩn cấp (SLA Shop 6h)");
        PRIORITY_LABELS.put("NORMAL", "Tiêu chuẩn (SLA Shop 6h)");
        PRIORITY_LABELS.put("LOW", "Thấp (SLA Shop 6h)");
    }

    private final JdbcTemplate jdbc;
    private final CustomerRefundService customerRefundService;
    private final Path uploadDirectory;

    public ComplaintService(
            JdbcTemplate jdbc,
            @Value("${app.upload.complaint-dir:uploads/complaints}") String uploadDir) {
        this(jdbc, new CustomerRefundService(jdbc), uploadDir);
    }

    @Autowired
    public ComplaintService(
            JdbcTemplate jdbc,
            CustomerRefundService customerRefundService,
            @Value("${app.upload.complaint-dir:uploads/complaints}") String uploadDir) {
        this.jdbc = jdbc;
        this.customerRefundService = customerRefundService;
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
                    status VARCHAR(25) NOT NULL DEFAULT 'PENDING_OPERATIONS',
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
                        status VARCHAR(25) NOT NULL DEFAULT 'PENDING_OPERATIONS',
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

        // Gỡ bỏ các CHECK constraint cũ trên MySQL nếu CSDL được tạo từ phiên bản cũ
        for (int i = 1; i <= 8; i++) {
            safeExecuteAlter("ALTER TABLE complaints DROP CHECK complaints_chk_" + i);
        }
        for (int i = 1; i <= 10; i++) {
            safeExecuteAlter("ALTER TABLE orders DROP CHECK orders_chk_" + i);
        }

        // Bổ sung an toàn các cột mới cho CSDL hiện hữu (bao gồm V19 & V22)
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN requested_refund_amount BIGINT NOT NULL DEFAULT 0");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN final_refund_amount BIGINT NOT NULL DEFAULT 0");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN shop_decision VARCHAR(25) NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN shop_evidence_image_url VARCHAR(1000) NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN escalated_at TIMESTAMP NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN shop_remind_count INT NOT NULL DEFAULT 0");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN last_reminded_at TIMESTAMP NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN cs_shop_request_note VARCHAR(1000) NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN fault_party VARCHAR(25) NOT NULL DEFAULT 'SHOP'");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN gifted_freeship_voucher VARCHAR(50) NULL");
        safeExecuteAlter("ALTER TABLE shops ADD COLUMN reputation_score INT NOT NULL DEFAULT 100");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN desired_resolution VARCHAR(30) NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN replacement_order_id BIGINT NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN voucher_id BIGINT NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN voucher_code VARCHAR(50) NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN operations_decision VARCHAR(25) NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN operations_note VARCHAR(1500) NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN operations_reviewed_at TIMESTAMP NULL");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN refund_method VARCHAR(30) DEFAULT 'WALLET'");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN refund_status VARCHAR(25) DEFAULT 'NONE'");
        safeExecuteAlter("ALTER TABLE complaints ADD COLUMN refund_completed_at TIMESTAMP NULL");
        safeExecuteAlter("ALTER TABLE orders ADD COLUMN original_order_id BIGINT NULL");
        safeExecuteAlter("ALTER TABLE orders ADD COLUMN order_type VARCHAR(30) DEFAULT 'STANDARD'");

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS shop_reputation_logs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    shop_id BIGINT NOT NULL,
                    complaint_id BIGINT NULL,
                    points_change INT NOT NULL,
                    current_reputation INT NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
        } catch (Exception e1) {
            try {
                jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS shop_reputation_logs (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        shop_id BIGINT NOT NULL,
                        complaint_id BIGINT NULL,
                        points_change INT NOT NULL,
                        current_reputation INT NOT NULL,
                        reason VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);
            } catch (Exception ignored) {
            }
        }

        // Đồng bộ trạng thái cũ sang mô hình V22
        safeExecuteAlter("UPDATE complaints SET status = 'PENDING_SHOP' WHERE status = 'PENDING'");
        safeExecuteAlter("UPDATE complaints SET status = 'PENDING_OPERATIONS' WHERE status = 'PENDING_OPS'");
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
        if ("DELIVERED".equalsIgnoreCase(orderStatus)) {
            return ACTIVE_REASON_LABELS;
        }
        if ("SHIPPING".equalsIgnoreCase(orderStatus) || "CANCELLED".equalsIgnoreCase(orderStatus)) {
            return DELIVERY_ONLY_REASON_LABELS;
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
        String deliveryStatus = order.get("delivery_status") != null ? String.valueOf(order.get("delivery_status")) : "";
        LocalDateTime deliveredAt = toLocalDateTime(order.get("delivered_at"));
        LocalDateTime cancelledAt = toLocalDateTime(order.get("cancelled_at"));
        LocalDateTime now = LocalDateTime.now();

        long nonCancelledCount = queryCount(
                "SELECT COUNT(*) FROM complaints WHERE order_id = ? AND status <> 'CANCELLED'",
                orderId
        );
        long activeCount = queryCount(
                "SELECT COUNT(*) FROM complaints WHERE order_id = ? AND status IN ('PENDING_OPERATIONS', 'PENDING_OPS', 'PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS')",
                orderId
        );
        boolean hasActiveComplaint = activeCount > 0;
        result.put("hasActiveComplaint", hasActiveComplaint);
        result.put("canCompleteOrder", "DELIVERED".equals(orderStatus) && !hasActiveComplaint);
        result.put("canRefuseDelivery", ("SHIPPING".equals(orderStatus) || "DELIVERED".equals(orderStatus)) && !hasActiveComplaint);

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

        // 2. Kiểm tra theo trạng thái đơn hàng:
        // - DELIVERED: cho phép khiếu nại toàn bộ lý do trong 24 giờ.
        // - SHIPPING hoặc CANCELLED do giao hàng thất bại/khách từ chối nhận (delivery_status = 'FAILED'):
        //   cho phép khiếu nại nhóm sự cố giao hàng (DELIVERY_ISSUE / DELIVERY_OR_SERVICE / OTHER).
        boolean isFailedOrRefusedDelivery = "CANCELLED".equals(orderStatus) && "FAILED".equalsIgnoreCase(deliveryStatus);
        if ("DELIVERED".equals(orderStatus)) {
            if (deliveredAt != null && now.isAfter(deliveredAt.plusHours(DELIVERED_COMPLAINT_WINDOW_HOURS))) {
                result.put("blockReason", "Đã quá thời hạn 24 giờ kể từ thời điểm giao hàng thành công để gửi khiếu nại.");
                return result;
            }
            result.put("canCreateComplaint", true);
            result.put("allowedReasons", ACTIVE_REASON_LABELS);
            result.put("allowedDesiredResolutions", DESIRED_RESOLUTION_LABELS);
            result.put("allowRefundRequest", true);
            return result;
        } else if ("SHIPPING".equals(orderStatus) || isFailedOrRefusedDelivery) {
            if (isFailedOrRefusedDelivery && cancelledAt != null && now.isAfter(cancelledAt.plusHours(DELIVERED_COMPLAINT_WINDOW_HOURS))) {
                result.put("blockReason", "Đã quá thời hạn 24 giờ kể từ thời điểm từ chối nhận hàng / giao thất bại để gửi khiếu nại.");
                return result;
            }
            result.put("canCreateComplaint", true);
            result.put("allowedReasons", DELIVERY_ONLY_REASON_LABELS);
            result.put("allowedDesiredResolutions", DESIRED_RESOLUTION_LABELS);
            result.put("allowRefundRequest", true);
            return result;
        }

        result.put("blockReason", "Chỉ được phép khiếu nại sau khi đơn hàng đã được giao thành công (DELIVERED trong vòng 24 giờ) hoặc khi gặp sự cố giao hàng.");
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
        return submitCustomerComplaint(customerId, orderId, orderItemId, reasonType, title, description, evidenceImage, "RESEND_ITEMS", 0L);
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
        return submitCustomerComplaint(customerId, orderId, orderItemId, reasonType, title, description, evidenceImage, "RESEND_ITEMS", requestedRefundAmount);
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
            String desiredResolution) {
        return submitCustomerComplaint(customerId, orderId, orderItemId, reasonType, title, description, evidenceImage, desiredResolution, 0L);
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
            String desiredResolution,
            Long requestedRefundAmount) {

        if (customerId == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để gửi khiếu nại.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("Không xác định được đơn hàng cần khiếu nại.");
        }
        String cleanReason = reasonType != null ? reasonType.trim() : "";
        if (!REASON_LABELS.containsKey(cleanReason)) {
            cleanReason = REASON_LABELS.keySet().stream()
                    .filter(k -> k.equalsIgnoreCase(reasonType != null ? reasonType.trim() : ""))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Vui lòng chọn lý do khiếu nại hợp lệ."));
        }

        String cleanResolution = (desiredResolution != null && !desiredResolution.isBlank())
                ? desiredResolution.trim().toUpperCase(Locale.ROOT) : "RESEND_ITEMS";
        if (!"RESEND_ITEMS".equals(cleanResolution)
                && !"ISSUE_VOUCHER".equals(cleanResolution)
                && !"RETURN_REFUND".equals(cleanResolution)) {
            cleanResolution = "RESEND_ITEMS";
        }

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
        String deliveryStatus = order.get("delivery_status") != null ? String.valueOf(order.get("delivery_status")) : "";
        LocalDateTime now = LocalDateTime.now();

        // 1. Giới hạn số lần khiếu nại: Mỗi đơn chỉ tối đa 1 lần (trừ khi khiếu nại trước đó đã CANCELLED)
        Long existingNonCancelled = jdbc.queryForObject("""
            SELECT COUNT(*) FROM complaints
            WHERE order_id = ? AND status <> 'CANCELLED'
        """, Long.class, orderId);
        if (existingNonCancelled != null && existingNonCancelled > 0) {
            throw new IllegalArgumentException("Mỗi đơn hàng chỉ được phép gửi khiếu nại tối đa 1 lần (trừ trường hợp đã hủy khiếu nại trước đó).");
        }

        // 2. Lọc theo trạng thái đơn hàng (OrderStatus):
        // - DELIVERED: cho phép khiếu nại mọi lý do trong 24 giờ kể từ delivered_at.
        // - SHIPPING hoặc CANCELLED do giao hàng thất bại/từ chối nhận hàng (delivery_status = 'FAILED'):
        //   chỉ cho phép khiếu nại sự cố giao hàng (SHIPPING_OR_CANCELLED_ALLOWED_REASONS).
        boolean isDeliveryExceptionAllowed = ("SHIPPING".equals(orderStatus)
                || ("CANCELLED".equals(orderStatus) && "FAILED".equalsIgnoreCase(deliveryStatus)))
                && SHIPPING_OR_CANCELLED_ALLOWED_REASONS.contains(cleanReason);

        if (!"DELIVERED".equals(orderStatus) && !isDeliveryExceptionAllowed) {
            throw new IllegalArgumentException("Chỉ được phép khiếu nại sau khi đơn hàng đã được giao thành công trong vòng 24 giờ (chỉ áp dụng đơn DELIVERED, hoặc khiếu nại sự cố giao hàng DELIVERY_ISSUE khi đang giao / từ chối nhận hàng).");
        }

        if ("DELIVERED".equals(orderStatus)) {
            LocalDateTime deliveredAt = toLocalDateTime(order.get("delivered_at"));
            if (deliveredAt != null && now.isAfter(deliveredAt.plusHours(DELIVERED_COMPLAINT_WINDOW_HOURS))) {
                throw new IllegalArgumentException("Đã quá thời hạn 24 giờ kể từ lúc đơn hàng giao thành công, không thể tạo khiếu nại.");
            }
        } else if ("CANCELLED".equals(orderStatus)) {
            LocalDateTime cancelledAt = toLocalDateTime(order.get("cancelled_at"));
            if (cancelledAt != null && now.isAfter(cancelledAt.plusHours(DELIVERED_COMPLAINT_WINDOW_HOURS))) {
                throw new IllegalArgumentException("Đã quá thời hạn 24 giờ kể từ lúc từ chối nhận hàng / giao thất bại, không thể tạo khiếu nại.");
            }
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

        // Validate bằng chứng: Nhóm lỗi hàng hóa trên đơn đã giao (DELIVERED) bắt buộc đính kèm ít nhất 1 hình ảnh bằng chứng
        if ("DELIVERED".equals(orderStatus) && PRODUCT_DEFECT_REASONS.contains(cleanReason) && (imageUrl == null || imageUrl.isBlank())) {
            throw new IllegalArgumentException("Nhóm lỗi hàng hóa (Chất lượng hoa quả, Thiếu số lượng, Giao sai sản phẩm) bắt buộc phải đính kèm ít nhất 1 hình ảnh bằng chứng.");
        }

        long actualPaidAmount = calculateActualPaidAmountFromOrderMap(order, validItemId);
        long reqAmount = requestedRefundAmount != null ? requestedRefundAmount : 0L;
        if (reqAmount < 0) {
            throw new IllegalArgumentException("Số tiền/điểm yêu cầu hoàn trả không được âm.");
        }
        if (reqAmount > actualPaidAmount) {
            throw new IllegalArgumentException("Số tiền/điểm yêu cầu đền bù (" + reqAmount + "đ) không được vượt quá số tiền thực trả (" + actualPaidAmount + "đ).");
        }
        long finalRequestedRefund = ("RETURN_REFUND".equals(cleanResolution) && reqAmount == 0L)
                ? actualPaidAmount : reqAmount;

        Long shopId = ((Number) order.get("shop_id")).longValue();
        BigDecimal totalAmount = toBigDecimal(order.get("total_amount"));
        String priority = determineAutoPriority(cleanReason, totalAmount);
        LocalDateTime slaDueAt = now.plusHours(SHOP_SLA_HOURS);
        String complaintCode = generateComplaintCode(now);

        final String finalTitle = cleanTitle;
        final Long finalItemId = validItemId;
        final String finalResolution = cleanResolution;
        final long refundToInsert = finalRequestedRefund;
        final String finalReason = cleanReason;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO complaints (
                    complaint_code, order_id, order_item_id, user_id, shop_id,
                    reason_type, title, description, evidence_image_url,
                    priority, status, source_type, desired_resolution, requested_refund_amount, final_refund_amount,
                    sla_due_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_OPERATIONS', 'CUSTOMER', ?, ?, 0, ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, complaintCode);
            ps.setLong(2, orderId);
            if (finalItemId != null) ps.setLong(3, finalItemId); else ps.setNull(3, java.sql.Types.BIGINT);
            ps.setLong(4, customerId);
            ps.setLong(5, shopId);
            ps.setString(6, finalReason);
            ps.setString(7, finalTitle);
            ps.setString(8, cleanDesc);
            ps.setString(9, imageUrl);
            ps.setString(10, priority);
            ps.setString(11, finalResolution);
            ps.setLong(12, refundToInsert);
            ps.setTimestamp(13, Timestamp.valueOf(slaDueAt));
            ps.setTimestamp(14, Timestamp.valueOf(now));
            ps.setTimestamp(15, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);

        Long complaintId = extractKey(keyHolder, complaintCode);
        String orderCode = String.valueOf(order.get("order_code"));
        Long shopOwnerId = order.get("shop_owner_id") instanceof Number n ? n.longValue() : null;

        createNotificationSafe(customerId,
                "COMPLAINT_CREATED_CUST_" + complaintId,
                "COMPLAINT",
                "Đã gửi khiếu nại " + complaintCode,
                "Khiếu nại cho đơn hàng " + orderCode + " đã được gửi tới Ban Vận hành để thẩm định.",
                "COMPLAINT", complaintId);

        notifyRoleUsers("OPERATIONS_MANAGER",
                "COMPLAINT_CREATED_OPS_" + complaintId,
                "COMPLAINT",
                "Khiếu nại mới chờ duyệt: " + complaintCode,
                "Khách hàng gửi khiếu nại cho đơn " + orderCode + ": '" + finalTitle + "'. Vui lòng thẩm định.",
                "COMPLAINT", complaintId);

        if (shopOwnerId != null) {
            createNotificationSafe(shopOwnerId,
                    "COMPLAINT_CREATED_SHOP_" + complaintId,
                    "COMPLAINT",
                    "Đơn hàng " + orderCode + " có khiếu nại mới (" + complaintCode + ")",
                    "Khách hàng gửi khiếu nại: '" + finalTitle + "' (Đang chờ Ban Vận hành thẩm định).",
                    "ORDER", orderId);
        }

        return complaintId;
    }

    /**
     * Customer tự hủy: Khi đang ở PENDING_OPERATIONS hoặc PENDING_SHOP, Customer có quyền bấm hủy -> chuyển sang CANCELLED.
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
        if (!STATUS_PENDING_OPERATIONS.equals(currentStatus) && !STATUS_PENDING_SHOP.equals(currentStatus) && !STATUS_ESCALATED.equals(currentStatus)
                && !"PENDING_OPS".equals(currentStatus) && !"PENDING".equals(currentStatus) && !"IN_PROGRESS".equals(currentStatus)) {
            throw new IllegalArgumentException("Chỉ có thể rút lại khiếu nại khi đang ở trạng thái Chờ Vận hành duyệt hoặc Chờ Shop xử lý.");
        }

        int updated = jdbc.update("""
            UPDATE complaints
            SET status = 'CANCELLED', updated_at = ?
            WHERE id = ? AND user_id = ? AND status IN ('PENDING_OPERATIONS', 'PENDING_OPS', 'PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS')
        """, Timestamp.valueOf(LocalDateTime.now()), complaintId, customerId);
        if (updated == 0) {
            throw new IllegalArgumentException("Trạng thái khiếu nại vừa thay đổi, không thể hủy.");
        }
    }

    // =========================================================================
    // 2. BAN VẬN HÀNH (OPERATIONS MANAGER) THẨM ĐỊNH & PHÊ DUYỆT KHIẾU NẠI (BƯỚC 1)
    // =========================================================================

    @Transactional
    public void approveComplaintByOperations(Long operationsUserId, Long complaintId, String operationsNote) {
        if (complaintId == null) {
            throw new IllegalArgumentException("Mã khiếu nại không hợp lệ.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM complaints WHERE id = ? FOR UPDATE", complaintId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy khiếu nại #" + complaintId);
        }
        Map<String, Object> c = rows.get(0);
        String currentStatus = String.valueOf(c.get("status"));
        if (!STATUS_PENDING_OPERATIONS.equals(currentStatus) && !"PENDING_OPS".equals(currentStatus) && !"PENDING".equals(currentStatus)) {
            throw new IllegalArgumentException("Chỉ có thể duyệt khiếu nại đang ở trạng thái Chờ Vận hành duyệt (trạng thái hiện tại: "
                    + STATUS_LABELS.getOrDefault(currentStatus, currentStatus) + ").");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime shopSlaDueAt = now.plusHours(SHOP_SLA_HOURS);
        String cleanNote = operationsNote != null ? operationsNote.trim() : "";
        if (cleanNote.length() > 1500) {
            throw new IllegalArgumentException("Ghi chú phê duyệt không được vượt quá 1500 ký tự.");
        }

        jdbc.update("""
            UPDATE complaints
            SET status = 'PENDING_SHOP',
                operations_decision = 'APPROVED',
                operations_note = ?,
                operations_reviewed_at = ?,
                sla_due_at = ?,
                assigned_to = COALESCE(assigned_to, ?),
                updated_at = ?
            WHERE id = ? AND status IN ('PENDING_OPERATIONS', 'PENDING_OPS', 'PENDING')
        """, cleanNote, Timestamp.valueOf(now), Timestamp.valueOf(shopSlaDueAt), operationsUserId, Timestamp.valueOf(now), complaintId);

        Long shopId = ((Number) c.get("shop_id")).longValue();
        Long customerId = c.get("user_id") instanceof Number n ? n.longValue() : null;
        Long shopOwnerId = null;
        try {
            shopOwnerId = jdbc.queryForObject("SELECT owner_id FROM shops WHERE id = ?", Long.class, shopId);
        } catch (Exception ignored) {}
        String complaintCode = String.valueOf(c.get("complaint_code"));
        if (shopOwnerId != null) {
            createNotificationSafe(shopOwnerId,
                    "COMPLAINT_OPS_APPROVED_" + complaintId,
                    "COMPLAINT",
                    "Khiếu nại " + complaintCode + " đã được Ban Vận hành duyệt",
                    "Ban Vận hành đã duyệt yêu cầu khiếu nại " + complaintCode + ". Vui lòng xử lý giao bù hàng hoặc tặng voucher đền bù trong vòng " + SHOP_SLA_HOURS + " giờ.",
                    "COMPLAINT", complaintId);
        }
        if (customerId != null) {
            createNotificationSafe(customerId,
                    "COMPLAINT_OPS_APPROVED_CUST_" + complaintId,
                    "COMPLAINT",
                    "Khiếu nại " + complaintCode + " đã được Ban Vận hành chấp thuận",
                    "Yêu cầu khiếu nại " + complaintCode + " đã được Ban Vận hành phê duyệt và chuyển cho Cửa hàng xử lý đền bù.",
                    "COMPLAINT", complaintId);
        }
    }

    /**
     * Ban Vận hành (Operations Manager) trực tiếp duyệt khiếu nại và hoàn tiền về tài khoản ngân hàng (RETURN_REFUND).
     * Đảm bảo tính nhất quán dữ liệu (@Transactional) và chống double-refund:
     * 1. Khóa dòng khiếu nại bằng SELECT ... FOR UPDATE.
     * 2. Kiểm tra trạng thái khiếu nại chưa RESOLVED và refund_status chưa COMPLETED.
     * 3. Gọi CustomerRefundService.queueComplaintReturnRefund() để kiểm tra trùng (order_id, 'RETURN_REFUND') và lưu vào bảng customer_refunds.
     * 4. Cập nhật trạng thái khiếu nại sang RESOLVED, resolution_type = 'RETURN_REFUND', refund_status = 'COMPLETED'.
     */
    @Transactional
    public void approveComplaintRefundByOperations(
            Long operationsUserId,
            Long complaintId,
            Long refundAmountInput,
            String operationsNote) {

        if (complaintId == null) {
            throw new IllegalArgumentException("Mã khiếu nại không hợp lệ.");
        }

        // 1. Khóa bản ghi khiếu nại để chống nhấn duyệt nhiều lần đồng thời (Double-Refund / Race Condition)
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM complaints WHERE id = ? FOR UPDATE", complaintId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy khiếu nại #" + complaintId);
        }
        Map<String, Object> c = rows.get(0);
        String currentStatus = String.valueOf(c.get("status"));
        String refundStatus = c.get("refund_status") != null ? String.valueOf(c.get("refund_status")) : "NONE";

        if (STATUS_RESOLVED.equals(currentStatus) || "COMPLETED".equalsIgnoreCase(refundStatus)
                || "RETURN_REFUND".equalsIgnoreCase(String.valueOf(c.get("resolution_type")))) {
            throw new IllegalArgumentException("Khiếu nại này đã được giải quyết hoặc đã hoàn tiền trước đó.");
        }
        if (STATUS_REJECTED.equals(currentStatus) || STATUS_CANCELLED.equals(currentStatus)) {
            throw new IllegalArgumentException("Không thể duyệt hoàn tiền cho khiếu nại đã từ chối hoặc đã hủy (trạng thái hiện tại: "
                    + STATUS_LABELS.getOrDefault(currentStatus, currentStatus) + ").");
        }

        Long orderId = ((Number) c.get("order_id")).longValue();
        Long orderItemId = c.get("order_item_id") instanceof Number n ? n.longValue() : null;
        Long customerId = ((Number) c.get("user_id")).longValue();
        Long shopId = ((Number) c.get("shop_id")).longValue();
        String complaintCode = String.valueOf(c.get("complaint_code"));

        List<Map<String, Object>> orderRows = jdbc.queryForList(
                "SELECT status, payment_status, total_amount, shop_revenue_amount FROM orders WHERE id = ? FOR UPDATE", orderId);
        if (!orderRows.isEmpty()) {
            Map<String, Object> ord = orderRows.get(0);
            String ordStatus = String.valueOf(ord.get("status"));
            String payStatus = String.valueOf(ord.get("payment_status"));
            if (!"PAID".equalsIgnoreCase(payStatus) && !"DELIVERED".equalsIgnoreCase(ordStatus) && !"COMPLETED".equalsIgnoreCase(ordStatus)) {
                throw new IllegalArgumentException("Đơn hàng chưa thanh toán (COD chưa thu tiền), không thể lập lệnh hoàn tiền.");
            }
        }

        long maxRefund = calculateActualPaidAmount(orderId, orderItemId);
        if (maxRefund <= 0) {
            try {
                BigDecimal orderTotal = jdbc.queryForObject("SELECT total_amount FROM orders WHERE id = ?", BigDecimal.class, orderId);
                if (orderTotal != null && orderTotal.longValue() > 0) {
                    maxRefund = orderTotal.longValue();
                }
            } catch (Exception ignored) {
            }
        }

        long requestedRefund = toLong(c.get("requested_refund_amount"));
        long finalRefund;
        if (refundAmountInput != null && refundAmountInput > 0) {
            finalRefund = refundAmountInput;
        } else if (requestedRefund > 0) {
            finalRefund = Math.min(requestedRefund, maxRefund > 0 ? maxRefund : requestedRefund);
        } else {
            finalRefund = maxRefund;
        }

        if (finalRefund <= 0) {
            throw new IllegalArgumentException("Số tiền hoàn trả phải lớn hơn 0đ.");
        }
        if (maxRefund > 0 && finalRefund > maxRefund) {
            throw new IllegalArgumentException("Số tiền hoàn trả (" + String.format("%,d", finalRefund)
                    + "đ) không được vượt quá số tiền thực trả của đơn hàng (" + String.format("%,d", maxRefund) + "đ).");
        }

        String cleanNote = (operationsNote == null || operationsNote.isBlank())
                ? "Ban Vận hành phê duyệt khiếu nại " + complaintCode + " và hoàn tiền cho khách hàng."
                : operationsNote.trim();
        if (cleanNote.length() > 1500) {
            throw new IllegalArgumentException("Ghi chú hoàn tiền không được vượt quá 1500 ký tự.");
        }

        // 2. Ghi nhận vào sổ cái hoàn tiền customer_refunds (có kiểm tra chống trùng order_id + RETURN_REFUND)
        customerRefundService.queueComplaintReturnRefund(
                orderId,
                customerId,
                BigDecimal.valueOf(finalRefund),
                cleanNote
        );

        // 3. Cập nhật trạng thái khiếu nại sang RESOLVED & đánh dấu hoàn tiền COMPLETED
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("""
            UPDATE complaints
            SET status = 'RESOLVED',
                operations_decision = 'APPROVED',
                operations_note = ?,
                operations_reviewed_at = COALESCE(operations_reviewed_at, ?),
                resolution_type = 'RETURN_REFUND',
                resolution_note = ?,
                final_refund_amount = ?,
                compensated_points = ?,
                refund_method = 'BANK_TRANSFER',
                refund_status = 'COMPLETED',
                refund_completed_at = ?,
                assigned_to = COALESCE(assigned_to, ?),
                resolved_by = ?,
                resolved_at = ?,
                is_shop_fault = 1,
                fault_party = 'SHOP',
                updated_at = ?
            WHERE id = ? AND status NOT IN ('RESOLVED', 'REJECTED', 'CANCELLED')
        """, cleanNote, Timestamp.valueOf(now), cleanNote, finalRefund, finalRefund,
                Timestamp.valueOf(now), operationsUserId, operationsUserId, Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

        if (updated != 1) {
            throw new IllegalArgumentException("Trạng thái khiếu nại đã thay đổi bởi thao tác khác, không thể duyệt hoàn tiền trùng lặp.");
        }

        // 4. Nếu hoàn toàn bộ đơn (hoặc số tiền hoàn >= doanh thu Shop của đơn), khóa quyết toán đơn hàng cho Shop (is_settled = 1)
        try {
            BigDecimal shopRev = !orderRows.isEmpty() ? toBigDecimal(orderRows.get(0).get("shop_revenue_amount")) : BigDecimal.ZERO;
            if (finalRefund >= maxRefund || (shopRev.longValue() > 0 && finalRefund >= shopRev.longValue())) {
                jdbc.update("UPDATE orders SET is_settled = 1 WHERE id = ?", orderId);
            }
        } catch (Exception ignored) {
        }

        deductShopReputation(shopId, complaintId, 5, "Khiếu nại " + complaintCode + " được Vận hành duyệt hoàn tiền");

        createNotificationSafe(customerId,
                "COMPLAINT_OPS_REFUND_" + complaintId,
                "COMPLAINT",
                "Khiếu nại " + complaintCode + " đã được duyệt hoàn tiền",
                "Ban Vận hành đã chấp thuận khiếu nại " + complaintCode + " và lập lệnh hoàn tiền "
                        + String.format("%,d", finalRefund) + "đ về tài khoản ngân hàng của bạn.",
                "COMPLAINT", complaintId);

        Long shopOwnerId = null;
        try {
            shopOwnerId = jdbc.queryForObject("SELECT owner_id FROM shops WHERE id = ?", Long.class, shopId);
        } catch (Exception ignored) {
        }
        if (shopOwnerId != null) {
            createNotificationSafe(shopOwnerId,
                    "COMPLAINT_OPS_REFUND_SHOP_" + complaintId,
                    "COMPLAINT",
                    "Khiếu nại " + complaintCode + " đã được Ban Vận hành hoàn tiền cho khách",
                    "Ban Vận hành đã duyệt hoàn tiền " + String.format("%,d", finalRefund) + "đ cho khiếu nại " + complaintCode + ". Ghi chú: " + cleanNote,
                    "COMPLAINT", complaintId);
        }
    }

    @Transactional
    public void rejectComplaintByOperations(Long operationsUserId, Long complaintId, String rejectReason) {
        if (complaintId == null) {
            throw new IllegalArgumentException("Mã khiếu nại không hợp lệ.");
        }
        String cleanReason = rejectReason != null ? rejectReason.trim() : "";
        if (cleanReason.length() < 5 || cleanReason.length() > 1500) {
            throw new IllegalArgumentException("Vui lòng nhập lý do từ chối khiếu nại chi tiết (từ 5 đến 1500 ký tự).");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM complaints WHERE id = ? FOR UPDATE", complaintId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy khiếu nại #" + complaintId);
        }
        Map<String, Object> c = rows.get(0);
        String currentStatus = String.valueOf(c.get("status"));
        if (!STATUS_PENDING_OPERATIONS.equals(currentStatus) && !"PENDING_OPS".equals(currentStatus) && !"PENDING".equals(currentStatus)) {
            throw new IllegalArgumentException("Chỉ có thể từ chối khiếu nại đang ở trạng thái Chờ Vận hành duyệt (trạng thái hiện tại: "
                    + STATUS_LABELS.getOrDefault(currentStatus, currentStatus) + ").");
        }

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            UPDATE complaints
            SET status = 'REJECTED',
                resolution_type = 'REJECTED',
                resolution_note = ?,
                operations_decision = 'REJECTED',
                operations_note = ?,
                operations_reviewed_at = ?,
                resolved_by = ?,
                resolved_at = ?,
                is_shop_fault = 0,
                fault_party = 'NONE',
                updated_at = ?
            WHERE id = ? AND status IN ('PENDING_OPERATIONS', 'PENDING_OPS', 'PENDING')
        """, cleanReason, cleanReason, Timestamp.valueOf(now), operationsUserId, Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

        Long customerId = ((Number) c.get("user_id")).longValue();
        String complaintCode = String.valueOf(c.get("complaint_code"));
        createNotificationSafe(customerId,
                "COMPLAINT_OPS_REJECTED_" + complaintId,
                "COMPLAINT",
                "Khiếu nại " + complaintCode + " không được chấp thuận",
                "Ban Vận hành đã xem xét và từ chối khiếu nại " + complaintCode + ". Lý do: " + cleanReason,
                "COMPLAINT", complaintId);
    }

    // =========================================================================
    // 3. SHOP XỬ LÝ KHIẾU NẠI (BƯỚC 2 - PENDING_SHOP)
    // =========================================================================

    /**
     * Shop xử lý khiếu nại (chỉ thực hiện được khi đơn đang ở PENDING_SHOP):
     * - RESEND_ITEMS: Tạo đơn giao bù 0đ -> RESOLVED.
     * - ISSUE_VOUCHER: Phát hành voucher đền bù riêng vào ví khách hàng -> RESOLVED.
     * Shop không có quyền từ chối khiếu nại sau khi Ban Vận hành đã phê duyệt.
     */
    @Transactional
    public void submitShopDecision(
            Long ownerId,
            Long complaintId,
            String decision,
            String shopResponse,
            MultipartFile shopEvidenceImage) {
        submitShopDecision(ownerId, complaintId, decision, shopResponse, shopEvidenceImage, null);
    }

    @Transactional
    public void submitShopDecision(
            Long ownerId,
            Long complaintId,
            String decision,
            String shopResponse,
            MultipartFile shopEvidenceImage,
            Long voucherDiscount) {

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

        String rawDecision = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        String normalizedDecision;
        if ("ACCEPT".equals(rawDecision)) {
            String desired = c.get("desired_resolution") != null ? String.valueOf(c.get("desired_resolution")) : "";
            if ("ISSUE_VOUCHER".equalsIgnoreCase(desired)) {
                normalizedDecision = "ISSUE_VOUCHER";
            } else {
                normalizedDecision = "RESEND_ITEMS";
            }
        } else if ("REJECT".equals(rawDecision)) {
            normalizedDecision = "REJECTED";
        } else {
            normalizedDecision = rawDecision;
        }

        if (!"RESEND_ITEMS".equals(normalizedDecision) && !"ISSUE_VOUCHER".equals(normalizedDecision) && !"REJECTED".equals(normalizedDecision)) {
            throw new IllegalArgumentException("Quyết định xử lý của Shop không hợp lệ (chấp nhận RESEND_ITEMS hoặc ISSUE_VOUCHER).");
        }

        LocalDateTime now = LocalDateTime.now();
        Long customerId = ((Number) c.get("user_id")).longValue();
        Long orderId = ((Number) c.get("order_id")).longValue();
        Long orderItemId = c.get("order_item_id") instanceof Number n ? n.longValue() : null;
        Long shopId = ((Number) c.get("shop_id")).longValue();
        String complaintCode = String.valueOf(c.get("complaint_code"));
        String orderCode = String.valueOf(c.get("order_code"));

        if ("RESEND_ITEMS".equals(normalizedDecision)) {
            if (c.get("replacement_order_id") != null) {
                throw new IllegalArgumentException("Khiếu nại này đã có đơn giao bù được tạo trước đó.");
            }
            String cleanNote = (shopResponse == null || shopResponse.isBlank())
                    ? "Cửa hàng chấp thuận khiếu nại và tiến hành giao bù sản phẩm cho khách hàng."
                    : shopResponse.trim();
            if (cleanNote.length() > 1500) {
                throw new IllegalArgumentException("Nội dung phản hồi không được vượt quá 1500 ký tự.");
            }

            Long replacementOrderId = createReplacementOrder(complaintId, orderId, orderItemId, shopId, customerId, cleanNote);

            int updated = jdbc.update("""
                UPDATE complaints
                SET status = 'RESOLVED',
                    shop_decision = 'RESEND_ITEMS',
                    resolution_type = 'RESEND_ITEMS',
                    resolution_note = ?,
                    replacement_order_id = ?,
                    shop_response = ?,
                    shop_responded_at = ?,
                    resolved_by = ?,
                    resolved_at = ?,
                    is_shop_fault = 1,
                    fault_party = 'SHOP',
                    updated_at = ?
                WHERE id = ? AND status IN ('PENDING_SHOP', 'PENDING')
            """, cleanNote, replacementOrderId, cleanNote, Timestamp.valueOf(now), ownerId, Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

            if (updated == 0) {
                throw new IllegalArgumentException("Khiếu nại đã được cập nhật bởi thao tác khác, vui lòng tải lại trang.");
            }

            String replacementOrderCode = jdbc.queryForObject("SELECT order_code FROM orders WHERE id = ?", String.class, replacementOrderId);

            createNotificationSafe(customerId,
                    "COMPLAINT_RESEND_ITEMS_" + complaintId,
                    "COMPLAINT",
                    "Shop đồng ý giao bù đơn hàng cho khiếu nại " + complaintCode,
                    "Cửa hàng đã tạo đơn giao bù mới 0đ (Mã đơn: " + replacementOrderCode + ") cho khiếu nại trên đơn " + orderCode + ". Bạn không phải thanh toán thêm bất kỳ khoản phí nào.",
                    "ORDER", replacementOrderId);

        } else if ("ISSUE_VOUCHER".equals(normalizedDecision)) {
            if (c.get("voucher_id") != null) {
                throw new IllegalArgumentException("Khiếu nại này đã được tặng voucher đền bù trước đó.");
            }
            String cleanNote = (shopResponse == null || shopResponse.isBlank())
                    ? "Cửa hàng chấp thuận khiếu nại và tặng voucher đền bù cho khách hàng."
                    : shopResponse.trim();
            if (cleanNote.length() > 1500) {
                throw new IllegalArgumentException("Nội dung phản hồi không được vượt quá 1500 ký tự.");
            }

            long discountVal;
            if (voucherDiscount != null) {
                if (voucherDiscount <= 0) {
                    throw new IllegalArgumentException("Giá trị voucher đền bù phải lớn hơn 0.");
                }
                discountVal = voucherDiscount;
            } else {
                long actualPaid = calculateActualPaidAmount(orderId, orderItemId);
                discountVal = actualPaid > 0 ? actualPaid : 20000L;
            }

            String baseVoucherCode = "VC-KN-" + complaintCode.replace("KN-", "").replace("-", "");
            String voucherCode = ensureUniqueVoucherCode(shopId, baseVoucherCode);
            String voucherName = "Voucher đền bù khiếu nại " + complaintCode;
            LocalDateTime voucherExpiry = now.plusDays(30);

            KeyHolder vKeyHolder = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO vouchers (
                        shop_id, code, name, discount_type, discount_value, min_order_value,
                        max_usages, used_count, is_public, voucher_type, status, starts_at, ends_at
                    ) VALUES (?, ?, ?, 'FIXED_AMOUNT', ?, 0, 1, 0, FALSE, 'DISCOUNT', 'ACTIVE', ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, shopId);
                ps.setString(2, voucherCode);
                ps.setString(3, voucherName);
                ps.setBigDecimal(4, BigDecimal.valueOf(discountVal));
                ps.setTimestamp(5, Timestamp.valueOf(now));
                ps.setTimestamp(6, Timestamp.valueOf(voucherExpiry));
                return ps;
            }, vKeyHolder);

            Long voucherId = extractVoucherKey(vKeyHolder, shopId, voucherCode);

            try {
                jdbc.update("""
                    INSERT INTO customer_saved_vouchers (user_id, voucher_id, saved_at)
                    VALUES (?, ?, ?)
                """, customerId, voucherId, Timestamp.valueOf(now));
            } catch (Exception ex) {
                System.err.println("Warning: Cannot insert customer_saved_vouchers: " + ex.getMessage());
            }

            int updated = jdbc.update("""
                UPDATE complaints
                SET status = 'RESOLVED',
                    shop_decision = 'ISSUE_VOUCHER',
                    resolution_type = 'ISSUE_VOUCHER',
                    resolution_note = ?,
                    voucher_id = ?,
                    voucher_code = ?,
                    shop_response = ?,
                    shop_responded_at = ?,
                    resolved_by = ?,
                    resolved_at = ?,
                    is_shop_fault = 1,
                    fault_party = 'SHOP',
                    updated_at = ?
                WHERE id = ? AND status IN ('PENDING_SHOP', 'PENDING')
            """, cleanNote, voucherId, voucherCode, cleanNote, Timestamp.valueOf(now), ownerId, Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

            if (updated == 0) {
                throw new IllegalArgumentException("Khiếu nại đã được cập nhật bởi thao tác khác, vui lòng tải lại trang.");
            }

            createNotificationSafe(customerId,
                    "COMPLAINT_ISSUE_VOUCHER_" + complaintId,
                    "COMPLAINT",
                    "Shop đã tặng voucher đền bù cho khiếu nại " + complaintCode,
                    "Cửa hàng gửi tặng bạn voucher đền bù " + String.format("%,d", discountVal) + "đ (Mã: " + voucherCode + ") cho đơn hàng tiếp theo. Hạn dùng 30 ngày.",
                    "COMPLAINT", complaintId);

        } else {
            // Shop không được quyền từ chối sau khi Ban Vận hành đã phê duyệt
            throw new IllegalArgumentException("Khiếu nại này đã được Ban Vận hành phê duyệt. Cửa hàng không có quyền từ chối mà bắt buộc phải chọn Tạo đơn giao bù (RESEND_ITEMS) hoặc Tặng voucher đền bù (ISSUE_VOUCHER).");
        }
    }

    @Transactional
    public Long createReplacementOrder(
            Long complaintId,
            Long originalOrderId,
            Long orderItemId,
            Long shopId,
            Long customerId,
            String shopNote) {
        return createReplacementOrder(complaintId, originalOrderId, orderItemId, shopId, customerId, shopNote, null, null);
    }

    @Transactional
    public Long createReplacementOrder(
            Long complaintId,
            Long originalOrderId,
            Long orderItemId,
            Long shopId,
            Long customerId,
            String shopNote,
            List<Long> customVariantIds,
            List<Integer> customQuantities) {

        List<Map<String, Object>> originalOrders = jdbc.queryForList(
                "SELECT * FROM orders WHERE id = ?", originalOrderId);
        if (originalOrders.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng gốc #" + originalOrderId);
        }
        Map<String, Object> orig = originalOrders.get(0);

        String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String newOrderCode = "RB-" + randomSuffix;
        String newDeliveryCode = "DL-RB-" + randomSuffix;
        String newPaymentCode = "PAY-RB-" + randomSuffix;
        String newCheckoutCode = "CHK-RB-" + randomSuffix;
        String newRequestKey = "REQ-RB-" + randomSuffix;

        String contactEmail = "customer@freshfruit.local";
        try {
            Long origCheckoutId = orig.get("checkout_id") instanceof Number n ? n.longValue() : null;
            String foundEmail = jdbc.queryForObject("SELECT contact_email FROM checkouts WHERE id = ?", String.class, origCheckoutId);
            if (foundEmail != null && !foundEmail.isBlank()) contactEmail = foundEmail;
        } catch (Exception ignored) {}

        LocalDateTime now = LocalDateTime.now();

        // 1. Create a dedicated checkouts record to satisfy UNIQUE(checkout_id, shop_id)
        KeyHolder chkKeyHolder = new GeneratedKeyHolder();
        final String finalContactEmail = contactEmail;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO checkouts (
                    checkout_code, request_key, user_id, contact_email, payment_method, total_amount, status
                ) VALUES (
                    ?, ?, ?, ?, 'COD', 0, 'CONFIRMED'
                )
            """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, newCheckoutCode);
            ps.setString(2, newRequestKey);
            ps.setLong(3, customerId);
            ps.setString(4, finalContactEmail);
            return ps;
        }, chkKeyHolder);

        Long replacementCheckoutId = extractKeyByTable(chkKeyHolder, "checkouts", "checkout_code", newCheckoutCode);

        String recipientName = String.valueOf(orig.get("recipient_name"));
        String recipientPhone = String.valueOf(orig.get("recipient_phone"));
        String deliveryProvinceCode = String.valueOf(orig.get("delivery_province_code"));
        String deliveryProvinceName = String.valueOf(orig.get("delivery_province_name"));
        String deliveryWardName = String.valueOf(orig.get("delivery_ward_name"));
        String deliveryAddress = String.valueOf(orig.get("delivery_address"));
        String shopProvinceCode = String.valueOf(orig.get("shop_province_code"));
        String customerNote = "Đơn giao bù khiếu nại #" + complaintId + (shopNote != null && !shopNote.isBlank() ? " (" + shopNote.trim() + ")" : "");

        KeyHolder orderKeyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO orders (
                    order_code, checkout_id, original_order_id, order_type, shop_id,
                    recipient_name, recipient_phone,
                    delivery_province_code, delivery_province_name, delivery_ward_name, delivery_address,
                    shop_province_code, customer_note,
                    subtotal, points_used, points_discount, points_subsidy_amount, qualifying_amount, points_earned,
                    shipping_fee, total_amount, shop_revenue_amount,
                    payment_method, delivery_code, status, payment_status, delivery_status,
                    created_at
                ) VALUES (
                    ?, ?, ?, 'RESEND', ?,
                    ?, ?,
                    ?, ?, ?, ?,
                    ?, ?,
                    0, 0, 0, 0, 0, 0,
                    0, 0, 0,
                    'RESEND', ?, 'CONFIRMED', 'PAID', 'NOT_STARTED',
                    ?
                )
            """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, newOrderCode);
            ps.setLong(2, replacementCheckoutId);
            ps.setLong(3, originalOrderId);
            ps.setLong(4, shopId);
            ps.setString(5, recipientName);
            ps.setString(6, recipientPhone);
            ps.setString(7, deliveryProvinceCode);
            ps.setString(8, deliveryProvinceName);
            ps.setString(9, deliveryWardName);
            ps.setString(10, deliveryAddress);
            ps.setString(11, shopProvinceCode);
            ps.setString(12, customerNote);
            ps.setString(13, newDeliveryCode);
            ps.setTimestamp(14, Timestamp.valueOf(now));
            return ps;
        }, orderKeyHolder);

        Long replacementOrderId = extractKeyByTable(orderKeyHolder, "orders", "order_code", newOrderCode);

        // 2. Insert items into order_items and deduct inventory accurately
        boolean customItemsInserted = false;
        if (customVariantIds != null && !customVariantIds.isEmpty()) {
            for (int i = 0; i < customVariantIds.size(); i++) {
                Long vId = customVariantIds.get(i);
                int qty = (customQuantities != null && customQuantities.size() > i && customQuantities.get(i) != null)
                        ? customQuantities.get(i) : 0;
                if (vId == null || qty <= 0) continue;

                List<Map<String, Object>> vRows = jdbc.queryForList("""
                    SELECT v.id as variant_id, v.sku, v.name as variant_name, v.image_url,
                           p.id as product_id, p.name as product_name,
                           u.name as base_unit_name,
                           COALESCE(vi.id, 0) as inventory_id
                    FROM product_variants v
                    JOIN products p ON p.id = v.product_id
                    JOIN units u ON u.id = v.base_unit_id
                    LEFT JOIN variant_inventory vi ON vi.variant_id = v.id
                    WHERE v.id = ? AND v.shop_id = ?
                """, vId, shopId);

                if (vRows.isEmpty()) {
                    throw new IllegalArgumentException("Sản phẩm biến thể #" + vId + " không thuộc cửa hàng này.");
                }
                Map<String, Object> vr = vRows.get(0);
                Long invId = vr.get("inventory_id") instanceof Number n ? n.longValue() : 0L;
                String pName = String.valueOf(vr.get("product_name"));
                String vName = String.valueOf(vr.get("variant_name"));
                String sku = String.valueOf(vr.get("sku"));
                String img = vr.get("image_url") != null ? String.valueOf(vr.get("image_url")) : null;
                String uName = String.valueOf(vr.get("base_unit_name"));

                deductInventoryForResendItem(invId, qty, pName + " - " + vName);

                KeyHolder itemKeyHolder = new GeneratedKeyHolder();
                jdbc.update(con -> {
                    PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO order_items (
                            order_id, shop_id, variant_id, inventory_id, promotion_id,
                            product_name, variant_name, sku, image_url, base_unit_name,
                            quantity, unit_price, sale_unit_price, line_total, allocation_status
                        ) VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0, 0, 0, 'CONSUMED')
                    """, Statement.RETURN_GENERATED_KEYS);
                    ps.setLong(1, replacementOrderId);
                    ps.setLong(2, shopId);
                    ps.setLong(3, vId);
                    ps.setLong(4, invId);
                    ps.setString(5, pName);
                    ps.setString(6, vName);
                    ps.setString(7, sku);
                    ps.setString(8, img);
                    ps.setString(9, uName);
                    ps.setInt(10, qty);
                    return ps;
                }, itemKeyHolder);

                Long newItemId = extractGeneratedId(itemKeyHolder);
                recordResendInventoryTransaction(invId, newItemId, qty, newOrderCode);
                customItemsInserted = true;
            }
            if (!customItemsInserted) {
                throw new IllegalArgumentException("Vui lòng chọn ít nhất 1 sản phẩm hợp lệ với số lượng lớn hơn 0 để tạo đơn giao bù.");
            }
        }

        // Fallback: Copy original items if no custom items provided
        if (!customItemsInserted) {
            List<Map<String, Object>> itemsToCopy;
            if (orderItemId != null) {
                itemsToCopy = jdbc.queryForList("SELECT * FROM order_items WHERE id = ? AND order_id = ?", orderItemId, originalOrderId);
            } else {
                itemsToCopy = jdbc.queryForList("SELECT * FROM order_items WHERE order_id = ?", originalOrderId);
            }

            for (Map<String, Object> item : itemsToCopy) {
                Long variantId = ((Number) item.get("variant_id")).longValue();
                Long inventoryId = ((Number) item.get("inventory_id")).longValue();
                Long promotionId = item.get("promotion_id") instanceof Number pn ? pn.longValue() : null;
                String productName = String.valueOf(item.get("product_name"));
                String variantName = String.valueOf(item.get("variant_name"));
                String sku = String.valueOf(item.get("sku"));
                String imageUrl = item.get("image_url") != null ? String.valueOf(item.get("image_url")) : null;
                String baseUnitName = String.valueOf(item.get("base_unit_name"));
                int quantity = ((Number) item.get("quantity")).intValue();

                deductInventoryForResendItem(inventoryId, quantity, productName + " - " + variantName);

                KeyHolder itemKeyHolder = new GeneratedKeyHolder();
                jdbc.update(con -> {
                    PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO order_items (
                            order_id, shop_id, variant_id, inventory_id, promotion_id,
                            product_name, variant_name, sku, image_url, base_unit_name,
                            quantity, unit_price, sale_unit_price, line_total, allocation_status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 'CONSUMED')
                    """, Statement.RETURN_GENERATED_KEYS);
                    ps.setLong(1, replacementOrderId);
                    ps.setLong(2, shopId);
                    ps.setLong(3, variantId);
                    ps.setLong(4, inventoryId);
                    if (promotionId != null) ps.setLong(5, promotionId); else ps.setNull(5, java.sql.Types.BIGINT);
                    ps.setString(6, productName);
                    ps.setString(7, variantName);
                    ps.setString(8, sku);
                    ps.setString(9, imageUrl);
                    ps.setString(10, baseUnitName);
                    ps.setInt(11, quantity);
                    return ps;
                }, itemKeyHolder);

                Long newItemId = extractGeneratedId(itemKeyHolder);
                recordResendInventoryTransaction(inventoryId, newItemId, quantity, newOrderCode);
            }
        }

        // Create 0đ paid payment record
        try {
            jdbc.update("""
                INSERT INTO payments (
                    payment_code, order_id, request_key, method, amount, status, paid_at
                ) VALUES (?, ?, ?, 'RESEND', 0, 'SUCCEEDED', ?)
            """, newPaymentCode, replacementOrderId, UUID.randomUUID().toString(), Timestamp.valueOf(now));
        } catch (Exception ex) {
            System.err.println("Warning: Cannot insert payment for replacement order: " + ex.getMessage());
        }

        return replacementOrderId;
    }

    private void deductInventoryForResendItem(Long inventoryId, int quantity, String itemLabel) {
        if (inventoryId == null || inventoryId <= 0) return;
        List<Map<String, Object>> invRows = jdbc.queryForList(
                "SELECT id, quantity_on_hand, reserved_quantity FROM variant_inventory WHERE id = ? FOR UPDATE",
                inventoryId);
        if (invRows.isEmpty()) {
            return;
        }
        Map<String, Object> inv = invRows.get(0);
        int onHand = ((Number) inv.get("quantity_on_hand")).intValue();
        int reserved = ((Number) inv.get("reserved_quantity")).intValue();
        int available = onHand - reserved;
        if (quantity > available) {
            throw new IllegalArgumentException("Tồn kho khả dụng không đủ để giao bù cho sản phẩm '" + itemLabel
                    + "' (Cần " + quantity + ", hiện còn " + Math.max(0, available) + ").");
        }
        int updated = jdbc.update(
                "UPDATE variant_inventory SET quantity_on_hand = quantity_on_hand - ? WHERE id = ? AND quantity_on_hand - reserved_quantity >= ?",
                quantity, inventoryId, quantity);
        if (updated != 1) {
            throw new IllegalArgumentException("Không thể trừ tồn kho giao bù cho sản phẩm '" + itemLabel + "'. Vui lòng kiểm tra lại tồn kho.");
        }
    }

    private void recordResendInventoryTransaction(Long inventoryId, Long orderItemId, int quantity, String orderCode) {
        if (inventoryId == null || inventoryId <= 0 || orderItemId == null) return;
        try {
            List<Map<String, Object>> invRows = jdbc.queryForList(
                    "SELECT quantity_on_hand, reserved_quantity FROM variant_inventory WHERE id = ?", inventoryId);
            if (invRows.isEmpty()) return;
            int onHandAfter = ((Number) invRows.get(0).get("quantity_on_hand")).intValue();
            int reservedAfter = ((Number) invRows.get(0).get("reserved_quantity")).intValue();
            jdbc.update("""
                INSERT INTO inventory_transactions(
                    inventory_id, order_item_id, type, on_hand_delta, reserved_delta,
                    on_hand_after, reserved_after, event_key, reason, created_by
                ) VALUES (?, ?, 'SALE', ?, 0, ?, ?, ?, ?, NULL)
            """, inventoryId, orderItemId, -quantity, onHandAfter, reservedAfter,
                    "sale-resend-" + orderItemId, "Xuất kho giao bù theo đơn " + orderCode);
        } catch (Exception ignored) {
        }
    }

    public List<Map<String, Object>> getAvailableShopVariants(Long shopId) {
        if (shopId == null) return List.of();
        return jdbc.queryForList("""
            SELECT v.id as variant_id, v.sku, v.name as variant_name, v.price, v.image_url,
                   p.id as product_id, p.name as product_name,
                   u.name as base_unit_name,
                   COALESCE(vi.id, 0) as inventory_id,
                   COALESCE(vi.quantity_on_hand - vi.reserved_quantity, 0) as available_quantity
            FROM product_variants v
            JOIN products p ON p.id = v.product_id
            JOIN units u ON u.id = v.base_unit_id
            LEFT JOIN variant_inventory vi ON vi.variant_id = v.id
            WHERE v.shop_id = ? AND v.status = 'ACTIVE'
            ORDER BY p.name ASC, v.name ASC
        """, shopId);
    }

    @Transactional
    public Long submitShopDecisionResendCustom(
            Long ownerId,
            Long complaintId,
            List<Long> customVariantIds,
            List<Integer> customQuantities,
            String shopResponse) {

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
        Map<String, Object> cp = rows.get(0);
        Long shopId = ((Number) cp.get("shop_id")).longValue();
        String currentStatus = String.valueOf(cp.get("status"));
        if (!"PENDING_SHOP".equalsIgnoreCase(currentStatus) && !"PENDING".equalsIgnoreCase(currentStatus)) {
            throw new IllegalArgumentException("Khiếu nại không ở trạng thái chờ Shop phản hồi.");
        }
        if (cp.get("replacement_order_id") != null) {
            throw new IllegalArgumentException("Khiếu nại này đã có đơn giao bù được tạo trước đó.");
        }

        Long originalOrderId = ((Number) cp.get("order_id")).longValue();
        Long customerId = cp.get("user_id") instanceof Number n ? n.longValue() : (cp.get("customer_id") instanceof Number n2 ? n2.longValue() : null);
        if (customerId == null) {
            throw new IllegalArgumentException("Không xác định được tài khoản khách hàng để tạo đơn.");
        }
        String complaintCode = String.valueOf(cp.get("complaint_code"));
        String orderCode = String.valueOf(cp.get("order_code"));
        String cleanNote = (shopResponse == null || shopResponse.isBlank())
                ? "Cửa hàng đã tạo đơn giao bù sản phẩm mới gửi đến quý khách."
                : shopResponse.trim();
        if (cleanNote.length() > 1500) {
            throw new IllegalArgumentException("Nội dung phản hồi không được vượt quá 1500 ký tự.");
        }
        LocalDateTime now = LocalDateTime.now();

        Long replacementOrderId = createReplacementOrder(complaintId, originalOrderId, null, shopId, customerId, cleanNote, customVariantIds, customQuantities);

        int updated = jdbc.update("""
            UPDATE complaints
            SET status = 'RESOLVED',
                shop_decision = 'RESEND_ITEMS',
                resolution_type = 'RESEND_ITEMS',
                resolution_note = ?,
                replacement_order_id = ?,
                shop_response = ?,
                shop_responded_at = ?,
                resolved_by = ?,
                resolved_at = ?,
                is_shop_fault = 1,
                fault_party = 'SHOP',
                updated_at = ?
            WHERE id = ? AND status IN ('PENDING_SHOP', 'PENDING')
        """, cleanNote, replacementOrderId, cleanNote, Timestamp.valueOf(now), ownerId, Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

        if (updated == 0) {
            throw new IllegalArgumentException("Khiếu nại đã được cập nhật bởi thao tác khác, vui lòng tải lại trang.");
        }

        String replacementOrderCode = jdbc.queryForObject("SELECT order_code FROM orders WHERE id = ?", String.class, replacementOrderId);

        createNotificationSafe(customerId,
                "COMPLAINT_RESEND_ITEMS_" + complaintId,
                "COMPLAINT",
                "Shop đồng ý giao bù đơn hàng cho khiếu nại " + complaintCode,
                "Cửa hàng đã tạo đơn giao bù mới 0đ (Mã đơn: " + replacementOrderCode + ") cho khiếu nại trên đơn " + orderCode + ". Bạn không phải thanh toán thêm bất kỳ khoản phí nào.",
                "ORDER", replacementOrderId);

        return replacementOrderId;
    }

    @Transactional
    public Long submitShopDecisionIssueVoucherCustom(
            Long ownerId,
            Long complaintId,
            String voucherCode,
            String voucherName,
            String discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscountAmount,
            BigDecimal minOrderValue,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String shopResponse) {

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
        Map<String, Object> cp = rows.get(0);
        Long shopId = ((Number) cp.get("shop_id")).longValue();
        String currentStatus = String.valueOf(cp.get("status"));
        if (!"PENDING_SHOP".equalsIgnoreCase(currentStatus) && !"PENDING".equalsIgnoreCase(currentStatus)) {
            throw new IllegalArgumentException("Khiếu nại không ở trạng thái chờ Shop phản hồi.");
        }
        if (cp.get("voucher_id") != null) {
            throw new IllegalArgumentException("Khiếu nại này đã được tặng voucher đền bù trước đó.");
        }

        Long customerId = cp.get("user_id") instanceof Number n ? n.longValue() : (cp.get("customer_id") instanceof Number n2 ? n2.longValue() : null);
        if (customerId == null) {
            throw new IllegalArgumentException("Không xác định được tài khoản khách hàng để tạo đơn.");
        }
        String complaintCode = String.valueOf(cp.get("complaint_code"));
        String cleanNote = (shopResponse == null || shopResponse.isBlank())
                ? "Cửa hàng gửi tặng voucher đền bù vào ví của quý khách."
                : shopResponse.trim();
        if (cleanNote.length() > 1500) {
            throw new IllegalArgumentException("Nội dung phản hồi không được vượt quá 1500 ký tự.");
        }
        LocalDateTime now = LocalDateTime.now();

        String discType = "PERCENTAGE".equalsIgnoreCase(discountType) ? "PERCENTAGE" : "FIXED_AMOUNT";
        if (discountValue != null && discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Giá trị giảm của voucher phải lớn hơn 0.");
        }
        BigDecimal discVal = discountValue != null ? discountValue : BigDecimal.valueOf(50000);
        if ("PERCENTAGE".equals(discType) && discVal.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Mức giảm theo phần trăm không được vượt quá 100%.");
        }
        if (minOrderValue != null && minOrderValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Giá trị đơn hàng tối thiểu không được âm.");
        }
        if (maxDiscountAmount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Mức giảm tối đa phải lớn hơn 0.");
        }
        BigDecimal minVal = minOrderValue != null ? minOrderValue : BigDecimal.ZERO;

        String code;
        if (voucherCode == null || voucherCode.isBlank()) {
            code = ensureUniqueVoucherCode(shopId, "VC-KN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        } else {
            code = voucherCode.trim().toUpperCase(Locale.ROOT);
            if (code.length() > 40) {
                throw new IllegalArgumentException("Mã voucher không được vượt quá 40 ký tự.");
            }
            long existingCount = queryCount("SELECT COUNT(*) FROM vouchers WHERE shop_id = ? AND code = ?", shopId, code);
            if (existingCount > 0) {
                throw new IllegalArgumentException("Mã voucher '" + code + "' đã tồn tại trong cửa hàng. Vui lòng chọn mã khác.");
            }
        }

        String name = (voucherName == null || voucherName.isBlank())
                ? "Voucher đền bù khiếu nại " + complaintCode
                : voucherName.trim();
        LocalDateTime start = startsAt != null ? startsAt : now;
        LocalDateTime end = endsAt != null && endsAt.isAfter(start) ? endsAt : start.plusDays(30);

        final String finalCode = code;
        final String finalName = name;
        KeyHolder vKeyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO vouchers (
                    shop_id, code, name, discount_type, discount_value, max_discount_amount, min_order_value,
                    max_usages, used_count, is_public, voucher_type, status, starts_at, ends_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, FALSE, 'DISCOUNT', 'ACTIVE', ?, ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, shopId);
            ps.setString(2, finalCode);
            ps.setString(3, finalName);
            ps.setString(4, discType);
            ps.setBigDecimal(5, discVal);
            if (maxDiscountAmount != null && "PERCENTAGE".equals(discType)) {
                ps.setBigDecimal(6, maxDiscountAmount);
            } else {
                ps.setNull(6, java.sql.Types.DECIMAL);
            }
            ps.setBigDecimal(7, minVal);
            ps.setTimestamp(8, Timestamp.valueOf(start));
            ps.setTimestamp(9, Timestamp.valueOf(end));
            ps.setTimestamp(10, Timestamp.valueOf(now));
            ps.setTimestamp(11, Timestamp.valueOf(now));
            return ps;
        }, vKeyHolder);

        Long voucherId = extractVoucherKey(vKeyHolder, shopId, code);

        try {
            jdbc.update("""
                INSERT INTO customer_saved_vouchers (user_id, voucher_id, saved_at)
                VALUES (?, ?, ?)
            """, customerId, voucherId, Timestamp.valueOf(now));
        } catch (Exception ex) {
            System.err.println("Warning: Cannot save voucher to customer wallet: " + ex.getMessage());
        }

        int updated = jdbc.update("""
            UPDATE complaints
            SET status = 'RESOLVED',
                shop_decision = 'ISSUE_VOUCHER',
                resolution_type = 'ISSUE_VOUCHER',
                resolution_note = ?,
                voucher_id = ?,
                voucher_code = ?,
                shop_response = ?,
                shop_responded_at = ?,
                resolved_by = ?,
                resolved_at = ?,
                is_shop_fault = 1,
                fault_party = 'SHOP',
                updated_at = ?
            WHERE id = ? AND status IN ('PENDING_SHOP', 'PENDING')
        """, cleanNote, voucherId, code, cleanNote, Timestamp.valueOf(now), ownerId, Timestamp.valueOf(now), Timestamp.valueOf(now), complaintId);

        if (updated == 0) {
            throw new IllegalArgumentException("Khiếu nại đã được cập nhật bởi thao tác khác, vui lòng tải lại trang.");
        }

        createNotificationSafe(customerId,
                "COMPLAINT_ISSUE_VOUCHER_" + complaintId,
                "COMPLAINT",
                "Shop đã tặng voucher đền bù cho khiếu nại " + complaintCode,
                "Cửa hàng gửi tặng bạn voucher đền bù " + ("PERCENTAGE".equals(discType) ? (discVal + "%") : (String.format("%,d", discVal.longValue()) + "đ")) + " (Mã: " + code + ") vào ví voucher. Hạn dùng đến " + end.toLocalDate() + ".",
                "COMPLAINT", complaintId);

        return voucherId;
    }

    private String ensureUniqueVoucherCode(Long shopId, String candidate) {
        String clean = candidate.length() > 35 ? candidate.substring(0, 35) : candidate;
        long count = queryCount("SELECT COUNT(*) FROM vouchers WHERE shop_id = ? AND code = ?", shopId, clean);
        if (count == 0) {
            return clean;
        }
        return clean + "-" + (100 + new Random().nextInt(900));
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
     * Tạo phiếu hỗ trợ/khiếu nại thủ công hoặc tự động 1-click từ đơn hàng gặp sự cố.
     * Tuân thủ quy trình V22: Khởi tạo ở trạng thái PENDING_OPERATIONS để Ban Vận hành thẩm định.
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
                ? "Phiếu hỗ trợ được khởi tạo cho đơn hàng " + cleanCode + "."
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
                    priority, status, source_type, desired_resolution, requested_refund_amount, final_refund_amount,
                    assigned_to, sla_due_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_OPERATIONS', ?, 'RESEND_ITEMS', 0, 0, ?, ?, ?, ?)
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
                    "Đã mở phiếu hỗ trợ " + complaintCode + " cho đơn " + cleanCode,
                    "Hệ thống đã ghi nhận sự cố trên đơn hàng " + cleanCode + " và chuyển tới Ban Vận hành thẩm định.",
                    "COMPLAINT", complaintId);
        }
        notifyRoleUsers("OPERATIONS_MANAGER",
                "COMPLAINT_CS_CREATED_OPS_" + complaintId,
                "COMPLAINT",
                "Phiếu khiếu nại mới: " + complaintCode,
                "Phiếu hỗ trợ vừa được khởi tạo cho đơn " + cleanCode + ". Vui lòng thẩm định.",
                "COMPLAINT", complaintId);
        return complaintId;
    }

    /**
     * Gửi thông báo yêu cầu / đôn đốc Chủ Shop giải trình khiếu nại.
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
                ? "Ban Vận hành đề nghị Cửa hàng kiểm tra lại đơn hàng và xử lý khiếu nại đúng hạn."
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
                    "⚠️ [Đôn đốc xử lý] Khiếu nại " + complaintCode + " (Đơn " + orderCode + ")",
                    "Ban Vận hành nhắc nhở xử lý (Lần " + nextRemindCount + "): \"" + cleanNote
                            + "\". Vui lòng kiểm tra chi tiết đơn hàng trước " + slaText + ".",
                    "ORDER", orderId);
        }
    }

    // =========================================================================
    // 4. DANH SÁCH YÊU CẦU KHIẾU NẠI (CUSTOMER, SHOP_OWNER, OPERATIONS)
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
            if ("PROCESSING".equals(normalized) || "PENDING".equals(normalized)) {
                sql.append(" AND c.status IN ('PENDING_OPERATIONS', 'PENDING_OPS', 'PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS') ");
            } else if ("PENDING_OPERATIONS".equals(normalized) || "PENDING_OPS".equals(normalized)) {
                sql.append(" AND c.status IN ('PENDING_OPERATIONS', 'PENDING_OPS') ");
            } else if ("PENDING_SHOP".equals(normalized)) {
                sql.append(" AND c.status = 'PENDING_SHOP' ");
            } else if ("ESCALATED".equals(normalized)) {
                sql.append(" AND c.status IN ('ESCALATED', 'IN_PROGRESS') ");
            } else if ("PROCESSED".equals(normalized)) {
                sql.append(" AND c.status IN ('RESOLVED', 'REJECTED') ");
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
        long pendingOps = queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status IN ('PENDING_OPERATIONS', 'PENDING_OPS')", customerId);
        long pendingShop = queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status IN ('PENDING_SHOP', 'PENDING')", customerId);
        long escalated = queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status IN ('ESCALATED', 'IN_PROGRESS')", customerId);
        long resolved = queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'RESOLVED'", customerId);
        long rejected = queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'REJECTED'", customerId);
        long cancelled = queryCount("SELECT COUNT(*) FROM complaints WHERE user_id = ? AND status = 'CANCELLED'", customerId);

        counts.put("processing", pendingOps + pendingShop + escalated);
        counts.put("pending_operations", pendingOps);
        counts.put("processed", resolved + rejected);
        counts.put("cancelled", cancelled);

        counts.put("pending_shop", pendingShop);
        counts.put("escalated", escalated);
        counts.put("resolved", resolved);
        counts.put("rejected", rejected);
        counts.put("pending", pendingShop);
        counts.put("in_progress", escalated);
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
                sql.append(" AND c.status IN ('PENDING_OPERATIONS', 'PENDING_OPS', 'PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS') ");
            } else if ("PENDING_OPERATIONS".equals(norm) || "PENDING_OPS".equals(norm)) {
                sql.append(" AND c.status IN ('PENDING_OPERATIONS', 'PENDING_OPS') ");
            } else if ("PENDING_SHOP".equals(norm)) {
                sql.append(" AND c.status = 'PENDING_SHOP' ");
            } else if ("PENDING".equals(norm)) {
                sql.append(" AND c.status IN ('PENDING_OPERATIONS', 'PENDING_OPS', 'PENDING_SHOP', 'PENDING') ");
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
                CASE WHEN c.status IN ('PENDING_OPERATIONS', 'PENDING_OPS') THEN 0
                     WHEN c.status = 'PENDING_SHOP' THEN 1
                     WHEN c.status IN ('ESCALATED', 'IN_PROGRESS') THEN 2
                     ELSE 3 END ASC,
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
    // 5. XEM CHI TIẾT KHIẾU NẠI & BÁO CÁO GIÁM SÁT
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
        Long customerId = detail.get("user_id") instanceof Number n ? n.longValue() : null;
        Long orderItemId = detail.get("order_item_id") instanceof Number n ? n.longValue() : null;

        long actualPaidAmount = calculateActualPaidAmount(orderId, orderItemId);
        detail.put("actual_paid_amount", actualPaidAmount);

        if (customerId != null) {
            List<Map<String, Object>> bankRows = safeQueryList(
                    "SELECT bank_name, bank_account_no, bank_account_holder FROM customer_bank_accounts WHERE user_id = ?",
                    customerId);
            if (!bankRows.isEmpty()) {
                Map<String, Object> b = bankRows.get(0);
                detail.put("customer_bank_name", b.get("bank_name"));
                detail.put("customer_bank_account_no", b.get("bank_account_no"));
                detail.put("customer_bank_account_holder", b.get("bank_account_holder"));
            }
        }

        List<Map<String, Object>> refundRows = safeQueryList(
                "SELECT id, refund_amount, status, created_at, exported_at FROM customer_refunds WHERE order_id = ? AND refund_type = 'RETURN_REFUND'",
                orderId);
        if (!refundRows.isEmpty()) {
            detail.put("customer_refund_record", refundRows.get(0));
        }

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
     * Ghi nhận người phụ trách theo dõi phiếu, KHÔNG chuyển trạng thái sang ESCALATED.
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
        if (STATUS_RESOLVED.equals(status) || STATUS_REJECTED.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw new IllegalArgumentException("Phiếu khiếu nại này đã kết thúc.");
        }

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            UPDATE complaints
            SET assigned_to = COALESCE(assigned_to, ?),
                updated_at = ?
            WHERE id = ?
        """, csUserId, Timestamp.valueOf(now), complaintId);
    }

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
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bộ phận CSKH chỉ có quyền xem báo cáo thống kê, không có thẩm quyền quyết định hay phân xử khiếu nại. Quyền quyết định thuộc về Ban Vận hành và Chủ Shop.");
    }

    @Transactional
    public void resolveComplaint(
            Long complaintId,
            Long csUserId,
            String decisionStatus,
            String resolutionType,
            String resolutionNote,
            Long finalRefundAmountInput,
            boolean isShopFault,
            String faultParty,
            boolean giftFreeShipping,
            boolean escalateToOps) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bộ phận CSKH chỉ có quyền xem báo cáo thống kê, không có thẩm quyền quyết định hay phân xử khiếu nại. Quyền quyết định thuộc về Ban Vận hành và Chủ Shop.");
    }

    public void deductShopReputation(Long shopId, Long complaintId, int pointsDeducted, String reason) {
        if (shopId == null || pointsDeducted <= 0) return;
        try {
            jdbc.update("UPDATE shops SET reputation_score = GREATEST(0, COALESCE(reputation_score, 100) - ?) WHERE id = ?", pointsDeducted, shopId);
            Integer currentScore = 100 - pointsDeducted;
            try {
                Integer s = jdbc.queryForObject("SELECT COALESCE(reputation_score, 100) FROM shops WHERE id = ?", Integer.class, shopId);
                if (s != null) currentScore = s;
            } catch (Exception ignored) {}

            jdbc.update("""
                INSERT INTO shop_reputation_logs(shop_id, complaint_id, points_change, current_reputation, reason)
                VALUES (?, ?, ?, ?, ?)
            """, shopId, complaintId, -pointsDeducted, currentScore, reason);

            if (currentScore < 80) {
                notifyRoleUsers("OPERATIONS_MANAGER",
                        "SHOP_LOW_REPUTATION_" + shopId + "_" + System.currentTimeMillis(),
                        "VIOLATION_ALERT",
                        "⚠️ Điểm uy tín Shop giảm sâu: Còn " + currentScore + "/100 điểm",
                        "Cửa hàng #" + shopId + " vừa bị trừ " + pointsDeducted + " điểm uy tín (Lý do: " + reason
                                + "). Điểm hiện tại còn " + currentScore + "/100. Đề nghị Ban Vận hành theo dõi hoặc tạm hạn chế hiển thị.",
                        "SHOP", shopId);
            }
        } catch (Exception ex) {
            System.err.println("Warning: Không thể cập nhật shop reputation: " + ex.getMessage());
        }
    }

    /**
     * Chủ động gửi cảnh báo vi phạm của Shop lên bộ phận Vận hành (Operations Manager).
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
                "Hồ sơ cảnh báo vi phạm của shop '" + shopName + "' đã được chuyển sang Ban Vận hành. Lý do: " + cleanReason,
                "SHOP", shopId);
    }

    // =========================================================================
    // 6. NĂM TÍNH NĂNG BÁO CÁO & GIÁM SÁT (OPERATIONS & CS DASHBOARD)
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
        long pendingDisputes = queryCount("SELECT COUNT(*) FROM complaints WHERE status IN ('PENDING_OPERATIONS', 'PENDING_OPS', 'PENDING_SHOP', 'PENDING')");
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
            SELECT id, priority, status, created_at, operations_reviewed_at, sla_due_at, resolved_at
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
            LocalDateTime opsReviewedAt = toLocalDateTime(r.get("operations_reviewed_at"));
            LocalDateTime slaDueAt = toLocalDateTime(r.get("sla_due_at"));
            LocalDateTime resolvedAt = toLocalDateTime(r.get("resolved_at"));

            if (createdAt == null || slaDueAt == null) continue;

            boolean isHigh = "HIGH".equals(priority);
            if (isHigh) highTotal++; else normalTotal++;

            LocalDateTime slaStart = opsReviewedAt != null ? opsReviewedAt : createdAt;

            if (STATUS_RESOLVED.equals(status) || STATUS_REJECTED.equals(status)) {
                processedTickets++;
                LocalDateTime finishTime = resolvedAt != null ? resolvedAt : now;
                long mins = Math.max(1, Duration.between(slaStart, finishTime).toMinutes());
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
                // SLA 6 giờ của Shop chỉ tính khi đã chuyển sang PENDING_SHOP
                if (STATUS_PENDING_SHOP.equals(status) || STATUS_ESCALATED.equals(status) || "PENDING".equals(status)) {
                    if (now.isAfter(slaDueAt)) {
                        overdueActiveTickets++;
                    } else if (Duration.between(now, slaDueAt).toMinutes() <= 120) {
                        dueSoonActiveTickets++;
                    }
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
        String rawStatus = String.valueOf(row.getOrDefault("status", STATUS_PENDING_OPERATIONS));
        String status = switch (rawStatus) {
            case "PENDING_OPS" -> STATUS_PENDING_OPERATIONS;
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

        // Trạng thái rút gọn cho Khách hàng (Customer Facing)
        String customerStatus;
        String customerStatusBadgeClass;
        if (STATUS_PENDING_OPERATIONS.equals(status)) {
            customerStatus = "Chờ Vận hành duyệt";
            customerStatusBadgeClass = "processing";
        } else if (STATUS_PENDING_SHOP.equals(status) || STATUS_ESCALATED.equals(status)) {
            customerStatus = "Chờ Shop xử lý";
            customerStatusBadgeClass = "processing";
        } else if (STATUS_RESOLVED.equals(status)) {
            customerStatus = "Đã xử lý (Thành công)";
            customerStatusBadgeClass = "resolved";
        } else if (STATUS_REJECTED.equals(status)) {
            customerStatus = "Đã xử lý (Bác bỏ)";
            customerStatusBadgeClass = "rejected";
        } else if (STATUS_CANCELLED.equals(status)) {
            customerStatus = "Đã rút khiếu nại";
            customerStatusBadgeClass = "cancelled";
        } else {
            customerStatus = STATUS_LABELS.getOrDefault(status, status);
            customerStatusBadgeClass = "default";
        }
        row.put("customer_status_label", customerStatus);
        row.put("customer_status_badge", customerStatusBadgeClass);

        String desiredRes = row.get("desired_resolution") != null ? String.valueOf(row.get("desired_resolution")) : null;
        row.put("desired_resolution", desiredRes);
        row.put("desired_resolution_label", desiredRes != null ? DESIRED_RESOLUTION_LABELS.getOrDefault(desiredRes, desiredRes) : "Giao bù hàng");

        if (row.get("replacement_order_id") != null) {
            Long rOrderId = ((Number) row.get("replacement_order_id")).longValue();
            List<Map<String, Object>> rOrders = safeQueryList("SELECT id, order_code, status, delivery_status, total_amount, created_at FROM orders WHERE id = ?", rOrderId);
            if (!rOrders.isEmpty()) {
                Map<String, Object> rOrder = rOrders.get(0);
                row.put("replacement_order_code", rOrder.get("order_code"));
                row.put("replacement_order_status", rOrder.get("status"));
                row.put("replacement_delivery_status", rOrder.get("delivery_status"));
            }
        }
        row.put("fault_party", row.getOrDefault("fault_party", "SHOP"));
        row.put("gifted_freeship_voucher", row.get("gifted_freeship_voucher"));
        row.put("resolution_label", resolutionType != null ? RESOLUTION_LABELS.getOrDefault(resolutionType, resolutionType) : "Chưa có kết luận");
        row.put("source_label", switch (sourceType) {
            case "CS_MANUAL" -> "Nhân sự tạo thủ công";
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
        LocalDateTime opsReviewedAt = toLocalDateTime(row.get("operations_reviewed_at"));
        if (opsReviewedAt != null) {
            row.put("operations_reviewed_at_fmt", opsReviewedAt.format(DT_FMT));
        }
        row.put("compensated_points", finalRefund);

        LocalDateTime createdAt = toLocalDateTime(row.get("created_at"));
        LocalDateTime slaDueAt = toLocalDateTime(row.get("sla_due_at"));
        LocalDateTime resolvedAt = toLocalDateTime(row.get("resolved_at"));
        LocalDateTime shopRespondedAt = toLocalDateTime(row.get("shop_responded_at"));
        LocalDateTime escalatedAt = toLocalDateTime(row.get("escalated_at"));
        LocalDateTime lastRemindedAt = toLocalDateTime(row.get("last_reminded_at"));
        LocalDateTime now = LocalDateTime.now();

        row.put("created_at_fmt", createdAt != null ? createdAt.format(DT_FMT) : "—");
        row.put("sla_due_at_fmt", STATUS_PENDING_OPERATIONS.equals(status) ? "Bắt đầu sau khi Vận hành duyệt" : (slaDueAt != null ? slaDueAt.format(DT_FMT) : "—"));
        row.put("resolved_at_fmt", resolvedAt != null ? resolvedAt.format(DT_FMT) : "—");
        row.put("shop_responded_at_fmt", shopRespondedAt != null ? shopRespondedAt.format(DT_FMT) : "—");
        row.put("escalated_at_fmt", escalatedAt != null ? escalatedAt.format(DT_FMT) : "—");
        row.put("last_reminded_at_fmt", lastRemindedAt != null ? lastRemindedAt.format(DT_FMT) : "—");

        long remindCount = toLong(row.get("shop_remind_count"));
        row.put("shop_remind_count", remindCount);
        boolean hasShopResponded = row.get("shop_response") != null && !String.valueOf(row.get("shop_response")).isBlank();
        row.put("has_shop_responded", hasShopResponded);
        row.put("can_penalize_shop", true);

        String slaState = "ON_TIME";
        String slaBadgeText = "Trong hạn";
        if (STATUS_PENDING_OPERATIONS.equals(status)) {
            slaState = "WAITING_OPERATIONS";
            slaBadgeText = "Chờ Vận hành duyệt";
        } else if (slaDueAt != null) {
            LocalDateTime slaStart = opsReviewedAt != null ? opsReviewedAt : (createdAt != null ? createdAt : now);
            if (STATUS_RESOLVED.equals(status) || STATUS_REJECTED.equals(status)) {
                LocalDateTime finish = resolvedAt != null ? resolvedAt : now;
                double hoursTaken = Math.max(0.1, Math.round((Duration.between(slaStart, finish).toMinutes() / 60.0) * 10.0) / 10.0);
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

    private Long extractGeneratedId(KeyHolder keyHolder) {
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
        return null;
    }

    private Long extractKey(KeyHolder keyHolder, String complaintCode) {
        Long id = extractGeneratedId(keyHolder);
        if (id != null) return id;
        List<Long> ids = jdbc.queryForList("SELECT id FROM complaints WHERE complaint_code = ?", Long.class, complaintCode);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long extractKeyByTable(KeyHolder keyHolder, String table, String codeColumn, String codeValue) {
        Long id = extractGeneratedId(keyHolder);
        if (id != null) return id;
        List<Long> ids = jdbc.queryForList("SELECT id FROM " + table + " WHERE " + codeColumn + " = ?", Long.class, codeValue);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long extractVoucherKey(KeyHolder keyHolder, Long shopId, String voucherCode) {
        Long id = extractGeneratedId(keyHolder);
        if (id != null) return id;
        List<Long> ids = jdbc.queryForList("SELECT id FROM vouchers WHERE shop_id = ? AND code = ?", Long.class, shopId, voucherCode);
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
