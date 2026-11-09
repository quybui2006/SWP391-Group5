package service.complaint;

import entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import repository.*;
import service.operations.CustomerRefundService;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final List<String> ACTIVE_COMPLAINT_STATUSES = List.of(
            STATUS_PENDING_OPERATIONS, "PENDING_OPS", STATUS_PENDING_SHOP, STATUS_ESCALATED, "PENDING", "IN_PROGRESS"
    );

    public static final Set<String> ACTIVE_REASONS = Set.of(
            "QUALITY_ISSUE",
            "QUANTITY_SHORTAGE",
            "WRONG_PRODUCT",
            "DELIVERY_ISSUE"
    );

    public static final Map<String, String> REASON_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> RESOLUTION_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> DESIRED_RESOLUTION_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();
    public static final Map<String, String> PRIORITY_LABELS = new LinkedHashMap<>();

    static {
        REASON_LABELS.put("QUALITY_ISSUE", "Chất lượng hoa quả không đạt (dập nát, hư hỏng)");
        REASON_LABELS.put("QUANTITY_SHORTAGE", "Thiếu số lượng / thiếu cân");
        REASON_LABELS.put("WRONG_PRODUCT", "Giao sai sản phẩm / sai loại");
        REASON_LABELS.put("DELIVERY_ISSUE", "Sự cố giao hàng (Hư hỏng do vận chuyển / giao trễ / shipper)");

        DESIRED_RESOLUTION_LABELS.put("RESEND_ITEMS", "Giao bù hàng");
        DESIRED_RESOLUTION_LABELS.put("ISSUE_VOUCHER", "Đền bù voucher");
        DESIRED_RESOLUTION_LABELS.put("RETURN_REFUND", "Hoàn tiền ngân hàng");

        RESOLUTION_LABELS.put("RETURN_REFUND", "Chấp thuận & Hoàn tiền về Tài khoản ngân hàng");
        RESOLUTION_LABELS.put("RESEND_ITEMS", "Chấp thuận & Giao bù hàng (Đơn mới 0đ)");
        RESOLUTION_LABELS.put("ISSUE_VOUCHER", "Chấp thuận & Tặng voucher đền bù");
        RESOLUTION_LABELS.put("REJECTED", "Từ chối khiếu nại");

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

    private final ComplaintRepository complaintRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CheckoutRepository checkoutRepository;
    private final PaymentRepository paymentRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final VoucherRepository voucherRepository;
    private final CustomerSavedVoucherRepository customerSavedVoucherRepository;
    private final VariantInventoryRepository variantInventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ProductVariantRepository productVariantRepository;
    private final NotificationRepository notificationRepository;
    private final CustomerBankAccountRepository customerBankAccountRepository;
    private final ReviewRepository reviewRepository;
    private final CustomerRefundService customerRefundService;
    private final JdbcTemplate jdbc;
    private final Path uploadDirectory;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public ComplaintService(
            ComplaintRepository complaintRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CheckoutRepository checkoutRepository,
            PaymentRepository paymentRepository,
            ShopRepository shopRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            VoucherRepository voucherRepository,
            CustomerSavedVoucherRepository customerSavedVoucherRepository,
            VariantInventoryRepository variantInventoryRepository,
            InventoryTransactionRepository inventoryTransactionRepository,
            ProductVariantRepository productVariantRepository,
            NotificationRepository notificationRepository,
            CustomerBankAccountRepository customerBankAccountRepository,
            ReviewRepository reviewRepository,
            CustomerRefundService customerRefundService,
            JdbcTemplate jdbc,
            @Value("${app.upload.complaint-dir:uploads/complaints}") String uploadDir) {
        this.complaintRepository = complaintRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.checkoutRepository = checkoutRepository;
        this.paymentRepository = paymentRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.voucherRepository = voucherRepository;
        this.customerSavedVoucherRepository = customerSavedVoucherRepository;
        this.variantInventoryRepository = variantInventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.productVariantRepository = productVariantRepository;
        this.notificationRepository = notificationRepository;
        this.customerBankAccountRepository = customerBankAccountRepository;
        this.reviewRepository = reviewRepository;
        this.customerRefundService = customerRefundService;
        this.jdbc = jdbc;
        this.uploadDirectory = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Map<String, String> getReasonLabels() {
        return REASON_LABELS;
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
        if (orderId == null) {
            return 0L;
        }
        return orderRepository.findById(orderId)
                .map(order -> calculateActualPaidAmountFromOrder(order, orderItemId))
                .orElse(0L);
    }

    private long calculateActualPaidAmountFromOrder(Order order, Long orderItemId) {
        BigDecimal subtotal = order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO;
        BigDecimal voucherDiscount = order.getVoucherDiscount() != null ? order.getVoucherDiscount() : BigDecimal.ZERO;
        BigDecimal pointsDiscount = order.getPointsDiscount() != null ? order.getPointsDiscount() : BigDecimal.ZERO;
        BigDecimal orderPaidGoods = subtotal.subtract(voucherDiscount).subtract(pointsDiscount).max(BigDecimal.ZERO);

        if (orderItemId == null) {
            return orderPaidGoods.setScale(0, RoundingMode.DOWN).longValue();
        }

        Optional<OrderItem> itemOpt = orderItemRepository.findByIdAndOrderId(orderItemId, order.getId());
        if (itemOpt.isEmpty()) {
            return orderPaidGoods.setScale(0, RoundingMode.DOWN).longValue();
        }
        BigDecimal lineTotal = itemOpt.get().getLineTotal() != null ? itemOpt.get().getLineTotal() : BigDecimal.ZERO;
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
        result.put("orderActualPaidAmount", 0L);
        result.put("hasActiveComplaint", false);
        result.put("canCompleteOrder", false);

        Optional<Order> orderOpt = orderRepository.findByIdAndCustomerId(orderId, customerId);
        if (orderOpt.isEmpty()) {
            result.put("blockReason", "Không tìm thấy đơn hàng.");
            return result;
        }

        Order order = orderOpt.get();
        String orderStatus = order.getStatus();
        LocalDateTime deliveredAt = order.getDeliveredAt();
        LocalDateTime now = LocalDateTime.now();

        long nonCancelledCount = complaintRepository.countByOrderIdAndStatusNot(orderId, STATUS_CANCELLED);
        long activeCount = complaintRepository.countByOrderIdAndStatusIn(orderId, ACTIVE_COMPLAINT_STATUSES);
        boolean hasActiveComplaint = activeCount > 0;
        result.put("hasActiveComplaint", hasActiveComplaint);
        result.put("canCompleteOrder", "DELIVERED".equals(orderStatus) && !hasActiveComplaint);

        long orderActualPaid = calculateActualPaidAmountFromOrder(order, null);
        result.put("orderActualPaidAmount", orderActualPaid);

        // 1. Kiểm tra giới hạn tối đa 1 lần khiếu nại / đơn (trừ khi đã tự hủy CANCELLED)
        if (nonCancelledCount > 0) {
            result.put("blockReason", "Mỗi đơn hàng chỉ được phép gửi khiếu nại tối đa 1 lần.");
            return result;
        }

        // 2. Kiểm tra theo trạng thái đơn hàng:
        // Chỉ cho phép khiếu nại sau khi đơn đã DELIVERED (hoặc COMPLETED nếu vẫn nằm trong thời hạn 24 giờ kể từ delivered_at).
        if ("DELIVERED".equals(orderStatus) || "COMPLETED".equals(orderStatus)) {
            if (deliveredAt == null || now.isAfter(deliveredAt.plusHours(DELIVERED_COMPLAINT_WINDOW_HOURS))) {
                result.put("blockReason", "Đã quá thời hạn 24 giờ kể từ thời điểm giao hàng thành công để gửi khiếu nại.");
                return result;
            }
            result.put("canCreateComplaint", true);
            result.put("allowedReasons", REASON_LABELS);
            result.put("allowedDesiredResolutions", DESIRED_RESOLUTION_LABELS);
            return result;
        }

        result.put("blockReason", "Chỉ được phép khiếu nại sau khi đơn hàng đã được giao thành công (DELIVERED trong vòng 24 giờ kể từ thời điểm giao hàng).");
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
            MultipartFile evidenceImage,
            String desiredResolution) {

        if (customerId == null) {
            throw new IllegalArgumentException("Vui lòng đăng nhập để gửi khiếu nại.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("Không xác định được đơn hàng cần khiếu nại.");
        }
        String cleanReason = reasonType != null ? reasonType.trim().toUpperCase(Locale.ROOT) : "";
        if (!ACTIVE_REASONS.contains(cleanReason)) {
            throw new IllegalArgumentException("Vui lòng chọn lý do khiếu nại hợp lệ (QUALITY_ISSUE, QUANTITY_SHORTAGE, WRONG_PRODUCT, DELIVERY_ISSUE).");
        }

        String cleanResolution = (desiredResolution != null && !desiredResolution.isBlank())
                ? desiredResolution.trim().toUpperCase(Locale.ROOT) : "RESEND_ITEMS";
        if (!DESIRED_RESOLUTION_LABELS.containsKey(cleanResolution)) {
            throw new IllegalArgumentException("Phương án mong muốn giải quyết không hợp lệ (chỉ chấp nhận RETURN_REFUND, RESEND_ITEMS, ISSUE_VOUCHER).");
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

        // Khóa dòng đơn hàng PESSIMISTIC_WRITE để chống Race Condition khi tạo khiếu nại đồng thời
        Order order = orderRepository.findLockedByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng hoặc bạn không có quyền khiếu nại đơn hàng này."));

        String orderStatus = order.getStatus();
        LocalDateTime now = LocalDateTime.now();

        // 1. Giới hạn số lần khiếu nại: Mỗi đơn chỉ tối đa 1 lần (trừ khi khiếu nại trước đó đã CANCELLED)
        long existingNonCancelled = complaintRepository.countByOrderIdAndStatusNot(orderId, STATUS_CANCELLED);
        if (existingNonCancelled > 0) {
            throw new IllegalArgumentException("Mỗi đơn hàng chỉ được phép gửi khiếu nại tối đa 1 lần (trừ trường hợp đã hủy khiếu nại trước đó).");
        }

        // 2. Lọc theo trạng thái đơn hàng (OrderStatus):
        // Chỉ cho phép khiếu nại sau khi đơn đã DELIVERED (hoặc COMPLETED nếu vẫn nằm trong thời hạn 24 giờ kể từ delivered_at).
        if (!"DELIVERED".equals(orderStatus) && !"COMPLETED".equals(orderStatus)) {
            throw new IllegalArgumentException("Chỉ được phép khiếu nại sau khi đơn hàng đã được giao thành công trong vòng 24 giờ (chỉ áp dụng đơn DELIVERED hoặc COMPLETED trong hạn 24 giờ kể từ thời điểm giao hàng).");
        }

        LocalDateTime deliveredAt = order.getDeliveredAt();
        if (deliveredAt == null || now.isAfter(deliveredAt.plusHours(DELIVERED_COMPLAINT_WINDOW_HOURS))) {
            throw new IllegalArgumentException("Đã quá thời hạn 24 giờ kể từ lúc đơn hàng giao thành công, không thể tạo khiếu nại.");
        }

        // Kiểm tra orderItemId nếu có truyền vào
        Long validItemId = null;
        if (orderItemId != null) {
            OrderItem item = orderItemRepository.findByIdAndOrderId(orderItemId, orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm khiếu nại không thuộc đơn hàng này."));
            validItemId = item.getId();
        }

        String imageUrl = storeEvidenceImageIfPresent(evidenceImage);

        // Validate bằng chứng: Bắt buộc đính kèm ít nhất 1 hình ảnh bằng chứng đối với khiếu nại đơn hàng
        if (ACTIVE_REASONS.contains(cleanReason) && (imageUrl == null || imageUrl.isBlank())) {
            throw new IllegalArgumentException("Nhóm lỗi hàng hóa (Chất lượng hoa quả, Thiếu số lượng, Giao sai sản phẩm, Sự cố giao hàng) bắt buộc phải đính kèm ít nhất 1 hình ảnh bằng chứng.");
        }

        long actualPaidAmount = calculateActualPaidAmountFromOrder(order, validItemId);

        Long shopId = order.getShopId();
        BigDecimal totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        String priority = determineAutoPriority(cleanReason, totalAmount);
        LocalDateTime slaDueAt = now.plusHours(SHOP_SLA_HOURS);
        String complaintCode = generateComplaintCode(now);

        Complaint complaint = new Complaint();
        complaint.setComplaintCode(complaintCode);
        complaint.setOrderId(orderId);
        complaint.setOrderItemId(validItemId);
        complaint.setUserId(customerId);
        complaint.setShopId(shopId);
        complaint.setReasonType(cleanReason);
        complaint.setTitle(cleanTitle);
        complaint.setDescription(cleanDesc);
        complaint.setEvidenceImageUrl(imageUrl);
        complaint.setPriority(priority);
        complaint.setStatus(STATUS_PENDING_OPERATIONS);
        complaint.setSourceType("CUSTOMER");
        complaint.setDesiredResolution(cleanResolution);
        complaint.setRequestedRefundAmount(actualPaidAmount);
        complaint.setFinalRefundAmount(0L);
        complaint.setSlaDueAt(slaDueAt);
        complaint.setCreatedAt(now);
        complaint.setUpdatedAt(now);

        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();
        Long complaintId = saved.getId();
        String orderCode = order.getOrderCode();
        Long shopOwnerId = shopRepository.findById(shopId).map(Shop::getOwnerId).orElse(null);

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
                "Khách hàng gửi khiếu nại cho đơn " + orderCode + ": '" + cleanTitle + "'. Vui lòng thẩm định.",
                "COMPLAINT", complaintId);

        if (shopOwnerId != null) {
            createNotificationSafe(shopOwnerId,
                    "COMPLAINT_CREATED_SHOP_" + complaintId,
                    "COMPLAINT",
                    "Đơn hàng " + orderCode + " có khiếu nại mới (" + complaintCode + ")",
                    "Khách hàng gửi khiếu nại: '" + cleanTitle + "' (Đang chờ Ban Vận hành thẩm định).",
                    "ORDER", orderId);
        }

        return complaintId;
    }

    /**
     * Customer tự hủy: Khi đang ở PENDING_OPERATIONS, PENDING_SHOP hoặc ESCALATED, Customer có quyền bấm hủy -> chuyển sang CANCELLED.
     * Chống xung đột thao tác (Race Condition) bằng PESSIMISTIC_WRITE.
     */
    @Transactional
    public void cancelCustomerComplaint(Long customerId, Long complaintId) {
        Complaint complaint = complaintRepository.findLockedByIdAndUserId(complaintId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu khiếu nại của bạn."));

        String currentStatus = complaint.getStatus();
        if (!ACTIVE_COMPLAINT_STATUSES.contains(currentStatus)) {
            throw new IllegalArgumentException("Chỉ có thể rút lại khiếu nại khi đang ở trạng thái Chờ Vận hành duyệt hoặc Chờ Shop xử lý.");
        }

        complaint.setStatus(STATUS_CANCELLED);
        complaint.setUpdatedAt(LocalDateTime.now());
        complaintRepository.saveAndFlush(complaint);
    }

    // =========================================================================
    // 2. BAN VẬN HÀNH (OPERATIONS MANAGER) THẨM ĐỊNH & PHÊ DUYỆT KHIẾU NẠI (BƯỚC 1)
    // =========================================================================

    @Transactional
    public void approveComplaintByOperations(Long operationsUserId, Long complaintId, String operationsNote) {
        if (complaintId == null) {
            throw new IllegalArgumentException("Mã khiếu nại không hợp lệ.");
        }
        Complaint c = complaintRepository.findLockedById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khiếu nại #" + complaintId));

        String currentStatus = c.getStatus();
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

        c.setStatus(STATUS_PENDING_SHOP);
        c.setOperationsDecision("APPROVED");
        c.setOperationsNote(cleanNote);
        c.setOperationsReviewedAt(now);
        c.setSlaDueAt(shopSlaDueAt);
        if (c.getAssignedTo() == null) {
            c.setAssignedTo(operationsUserId);
        }
        c.setUpdatedAt(now);
        complaintRepository.saveAndFlush(c);

        Long shopId = c.getShopId();
        Long customerId = c.getUserId();
        Long shopOwnerId = shopRepository.findById(shopId).map(Shop::getOwnerId).orElse(null);
        String complaintCode = c.getComplaintCode();

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
     */
    @Transactional
    public void approveComplaintRefundByOperations(
            Long operationsUserId,
            Long complaintId,
            String operationsNote) {

        if (complaintId == null) {
            throw new IllegalArgumentException("Mã khiếu nại không hợp lệ.");
        }

        // 1. Khóa bản ghi khiếu nại để chống nhấn duyệt nhiều lần đồng thời (Double-Refund / Race Condition)
        Complaint c = complaintRepository.findLockedById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khiếu nại #" + complaintId));

        String currentStatus = c.getStatus();
        String refundStatus = c.getRefundStatus() != null ? c.getRefundStatus() : "NONE";

        if (STATUS_RESOLVED.equals(currentStatus) || "COMPLETED".equalsIgnoreCase(refundStatus)
                || "RETURN_REFUND".equalsIgnoreCase(c.getResolutionType())) {
            throw new IllegalArgumentException("Khiếu nại này đã được giải quyết hoặc đã hoàn tiền trước đó.");
        }
        if (STATUS_REJECTED.equals(currentStatus) || STATUS_CANCELLED.equals(currentStatus)) {
            throw new IllegalArgumentException("Không thể duyệt hoàn tiền cho khiếu nại đã từ chối hoặc đã hủy (trạng thái hiện tại: "
                    + STATUS_LABELS.getOrDefault(currentStatus, currentStatus) + ").");
        }

        Long orderId = c.getOrderId();
        Long orderItemId = c.getOrderItemId();
        Long customerId = c.getUserId();
        Long shopId = c.getShopId();
        String complaintCode = c.getComplaintCode();

        Optional<Order> orderOpt = orderRepository.findLockedById(orderId);
        if (orderOpt.isPresent()) {
            Order ord = orderOpt.get();
            String ordStatus = ord.getStatus();
            String payStatus = ord.getPaymentStatus();
            if (!"PAID".equalsIgnoreCase(payStatus) && !"DELIVERED".equalsIgnoreCase(ordStatus) && !"COMPLETED".equalsIgnoreCase(ordStatus)) {
                throw new IllegalArgumentException("Đơn hàng chưa thanh toán (COD chưa thu tiền), không thể lập lệnh hoàn tiền.");
            }
        }

        long maxRefund = calculateActualPaidAmount(orderId, orderItemId);
        if (maxRefund <= 0 && orderOpt.isPresent()) {
            BigDecimal orderTotal = orderOpt.get().getTotalAmount();
            if (orderTotal != null && orderTotal.longValue() > 0) {
                maxRefund = orderTotal.longValue();
            }
        }

        long finalRefund = maxRefund;
        if (finalRefund <= 0) {
            throw new IllegalArgumentException("Số tiền hoàn trả phải lớn hơn 0đ.");
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
        c.setStatus(STATUS_RESOLVED);
        c.setOperationsDecision("APPROVED");
        c.setOperationsNote(cleanNote);
        if (c.getOperationsReviewedAt() == null) {
            c.setOperationsReviewedAt(now);
        }
        c.setResolutionType("RETURN_REFUND");
        c.setResolutionNote(cleanNote);
        c.setFinalRefundAmount(finalRefund);
        c.setRefundMethod("BANK_TRANSFER");
        c.setRefundStatus("COMPLETED");
        c.setRefundCompletedAt(now);
        if (c.getAssignedTo() == null) {
            c.setAssignedTo(operationsUserId);
        }
        c.setResolvedBy(operationsUserId);
        c.setResolvedAt(now);
        c.setIsShopFault(1);
        c.setFaultParty("SHOP");
        c.setUpdatedAt(now);
        complaintRepository.saveAndFlush(c);

        // 4. Khóa quyết toán đơn hàng cho Shop (is_settled = 1)
        orderOpt.ifPresent(ord -> {
            ord.setIsSettled(1);
            orderRepository.saveAndFlush(ord);
        });

        createNotificationSafe(customerId,
                "COMPLAINT_OPS_REFUND_" + complaintId,
                "COMPLAINT",
                "Khiếu nại " + complaintCode + " đã được duyệt hoàn tiền",
                "Ban Vận hành đã chấp thuận khiếu nại " + complaintCode + " và lập lệnh hoàn tiền "
                        + String.format("%,d", finalRefund) + "đ về tài khoản ngân hàng của bạn.",
                "COMPLAINT", complaintId);

        Long shopOwnerId = shopRepository.findById(shopId).map(Shop::getOwnerId).orElse(null);
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

        Complaint c = complaintRepository.findLockedById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khiếu nại #" + complaintId));

        String currentStatus = c.getStatus();
        if (!STATUS_PENDING_OPERATIONS.equals(currentStatus) && !"PENDING_OPS".equals(currentStatus) && !"PENDING".equals(currentStatus)) {
            throw new IllegalArgumentException("Chỉ có thể từ chối khiếu nại đang ở trạng thái Chờ Vận hành duyệt (trạng thái hiện tại: "
                    + STATUS_LABELS.getOrDefault(currentStatus, currentStatus) + ").");
        }

        LocalDateTime now = LocalDateTime.now();
        c.setStatus(STATUS_REJECTED);
        c.setResolutionType("REJECTED");
        c.setResolutionNote(cleanReason);
        c.setOperationsDecision("REJECTED");
        c.setOperationsNote(cleanReason);
        c.setOperationsReviewedAt(now);
        c.setResolvedBy(operationsUserId);
        c.setResolvedAt(now);
        c.setIsShopFault(0);
        c.setFaultParty("NONE");
        c.setUpdatedAt(now);
        complaintRepository.saveAndFlush(c);

        Long customerId = c.getUserId();
        String complaintCode = c.getComplaintCode();
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

    @Transactional
    public Long createReplacementOrder(
            Long complaintId,
            Long originalOrderId,
            Long shopId,
            Long customerId,
            String shopNote,
            List<Long> customVariantIds,
            List<Integer> customQuantities) {

        Order orig = orderRepository.findById(originalOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng gốc #" + originalOrderId));

        String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String newOrderCode = "RB-" + randomSuffix;
        String newDeliveryCode = "DL-RB-" + randomSuffix;
        String newPaymentCode = "PAY-RB-" + randomSuffix;
        String newCheckoutCode = "CHK-RB-" + randomSuffix;
        String newRequestKey = "REQ-RB-" + randomSuffix;

        String contactEmail = checkoutRepository.findById(orig.getCheckoutId())
                .map(Checkout::getContactEmail)
                .filter(e -> !e.isBlank())
                .orElse("customer@freshfruit.local");

        LocalDateTime now = LocalDateTime.now();

        // 1. Create a dedicated checkouts record to satisfy UNIQUE(checkout_id, shop_id)
        Checkout replacementCheckout = new Checkout();
        replacementCheckout.setCheckoutCode(newCheckoutCode);
        replacementCheckout.setRequestKey(newRequestKey);
        replacementCheckout.setUserId(customerId);
        replacementCheckout.setContactEmail(contactEmail);
        replacementCheckout.setPaymentMethod("COD");
        replacementCheckout.setTotalAmount(BigDecimal.ZERO);
        replacementCheckout.setStatus("CONFIRMED");
        Long replacementCheckoutId = checkoutRepository.save(replacementCheckout).getId();

        String customerNote = "Đơn giao bù khiếu nại #" + complaintId + (shopNote != null && !shopNote.isBlank() ? " (" + shopNote.trim() + ")" : "");

        Order replacementOrder = new Order();
        replacementOrder.setOrderCode(newOrderCode);
        replacementOrder.setCheckoutId(replacementCheckoutId);
        replacementOrder.setOriginalOrderId(originalOrderId);
        replacementOrder.setOrderType("RESEND");
        replacementOrder.setShopId(shopId);
        replacementOrder.setRecipientName(orig.getRecipientName());
        replacementOrder.setRecipientPhone(orig.getRecipientPhone());
        replacementOrder.setDeliveryProvinceCode(orig.getDeliveryProvinceCode());
        replacementOrder.setDeliveryProvinceName(orig.getDeliveryProvinceName());
        replacementOrder.setDeliveryWardName(orig.getDeliveryWardName());
        replacementOrder.setDeliveryAddress(orig.getDeliveryAddress());
        replacementOrder.setShopProvinceCode(orig.getShopProvinceCode());
        replacementOrder.setCustomerNote(customerNote);
        replacementOrder.setSubtotal(BigDecimal.ZERO);
        replacementOrder.setPointsUsed(0L);
        replacementOrder.setPointsDiscount(BigDecimal.ZERO);
        replacementOrder.setPointsSubsidyAmount(BigDecimal.ZERO);
        replacementOrder.setQualifyingAmount(BigDecimal.ZERO);
        replacementOrder.setPointsEarned(0L);
        replacementOrder.setShippingFee(BigDecimal.ZERO);
        replacementOrder.setTotalAmount(BigDecimal.ZERO);
        replacementOrder.setShopRevenueAmount(BigDecimal.ZERO);
        replacementOrder.setVoucherDiscount(BigDecimal.ZERO);
        replacementOrder.setPaymentMethod("RESEND");
        replacementOrder.setDeliveryCode(newDeliveryCode);
        replacementOrder.setStatus("CONFIRMED");
        replacementOrder.setPaymentStatus("PAID");
        replacementOrder.setDeliveryStatus("NOT_STARTED");
        replacementOrder.setCreatedAt(now);

        Long replacementOrderId = orderRepository.save(replacementOrder).getId();

        // 2. Insert items into order_items and deduct inventory accurately
        boolean customItemsInserted = false;
        if (customVariantIds != null && !customVariantIds.isEmpty()) {
            for (int i = 0; i < customVariantIds.size(); i++) {
                Long vId = customVariantIds.get(i);
                int qty = (customQuantities != null && customQuantities.size() > i && customQuantities.get(i) != null)
                        ? customQuantities.get(i) : 0;
                if (vId == null || qty <= 0) continue;

                ProductVariant pv = productVariantRepository.findByIdAndShopIdWithProductAndUnit(vId, shopId)
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm biến thể #" + vId + " không thuộc cửa hàng này."));

                Long invId = variantInventoryRepository.findByVariantId(vId)
                        .map(VariantInventory::getId)
                        .orElse(0L);
                String pName = pv.getProduct().getName();
                String vName = pv.getName();
                String sku = pv.getSku();
                String img = pv.getImageUrl();
                String uName = pv.getBaseUnit().getName();

                deductInventoryForResendItem(invId, qty, pName + " - " + vName);

                OrderItem newItem = new OrderItem();
                newItem.setOrderId(replacementOrderId);
                newItem.setShopId(shopId);
                newItem.setVariantId(vId);
                newItem.setInventoryId(invId);
                newItem.setPromotionId(null);
                newItem.setProductName(pName);
                newItem.setVariantName(vName);
                newItem.setSku(sku);
                newItem.setImageUrl(img);
                newItem.setBaseUnitName(uName);
                newItem.setQuantity(qty);
                newItem.setUnitPrice(BigDecimal.ZERO);
                newItem.setSaleUnitPrice(BigDecimal.ZERO);
                newItem.setLineTotal(BigDecimal.ZERO);
                newItem.setAllocationStatus("CONSUMED");

                Long newItemId = orderItemRepository.save(newItem).getId();
                recordResendInventoryTransaction(invId, newItemId, qty, newOrderCode);
                customItemsInserted = true;
            }
            if (!customItemsInserted) {
                throw new IllegalArgumentException("Vui lòng chọn ít nhất 1 sản phẩm hợp lệ với số lượng lớn hơn 0 để tạo đơn giao bù.");
            }
        }

        // Fallback: Copy original items if no custom items provided
        if (!customItemsInserted) {
            List<OrderItem> itemsToCopy = orderItemRepository.findByOrderIdOrderByIdAsc(originalOrderId);

            for (OrderItem item : itemsToCopy) {
                Long variantId = item.getVariantId();
                Long inventoryId = item.getInventoryId();
                Long promotionId = item.getPromotionId();
                String productName = item.getProductName();
                String variantName = item.getVariantName();
                String sku = item.getSku();
                String imageUrl = item.getImageUrl();
                String baseUnitName = item.getBaseUnitName();
                int quantity = item.getQuantity();

                deductInventoryForResendItem(inventoryId, quantity, productName + " - " + variantName);

                OrderItem newItem = new OrderItem();
                newItem.setOrderId(replacementOrderId);
                newItem.setShopId(shopId);
                newItem.setVariantId(variantId);
                newItem.setInventoryId(inventoryId);
                newItem.setPromotionId(promotionId);
                newItem.setProductName(productName);
                newItem.setVariantName(variantName);
                newItem.setSku(sku);
                newItem.setImageUrl(imageUrl);
                newItem.setBaseUnitName(baseUnitName);
                newItem.setQuantity(quantity);
                newItem.setUnitPrice(BigDecimal.ZERO);
                newItem.setSaleUnitPrice(BigDecimal.ZERO);
                newItem.setLineTotal(BigDecimal.ZERO);
                newItem.setAllocationStatus("CONSUMED");

                Long newItemId = orderItemRepository.save(newItem).getId();
                recordResendInventoryTransaction(inventoryId, newItemId, quantity, newOrderCode);
            }
        }

        // Create 0đ paid payment record
        try {
            Payment payment = new Payment();
            payment.setPaymentCode(newPaymentCode);
            payment.setOrderId(replacementOrderId);
            payment.setRequestKey(UUID.randomUUID().toString());
            payment.setMethod("RESEND");
            payment.setAmount(BigDecimal.ZERO);
            payment.setStatus("SUCCEEDED");
            payment.setPaidAt(now);
            paymentRepository.save(payment);
        } catch (Exception ex) {
            System.err.println("Warning: Cannot insert payment for replacement order: " + ex.getMessage());
        }

        return replacementOrderId;
    }

    private void deductInventoryForResendItem(Long inventoryId, int quantity, String itemLabel) {
        if (inventoryId == null || inventoryId <= 0) return;
        Optional<VariantInventory> invOpt = variantInventoryRepository.findLockedById(inventoryId);
        if (invOpt.isEmpty()) {
            return;
        }
        VariantInventory inv = invOpt.get();
        int onHand = inv.getQuantityOnHand() != null ? inv.getQuantityOnHand() : 0;
        int reserved = inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0;
        int available = onHand - reserved;
        if (quantity > available) {
            throw new IllegalArgumentException("Tồn kho khả dụng không đủ để giao bù cho sản phẩm '" + itemLabel
                    + "' (Cần " + quantity + ", hiện còn " + Math.max(0, available) + ").");
        }
        inv.setQuantityOnHand(onHand - quantity);
        variantInventoryRepository.save(inv);
    }

    private void recordResendInventoryTransaction(Long inventoryId, Long orderItemId, int quantity, String orderCode) {
        if (inventoryId == null || inventoryId <= 0 || orderItemId == null) return;
        try {
            Optional<VariantInventory> invOpt = variantInventoryRepository.findById(inventoryId);
            if (invOpt.isEmpty()) return;
            VariantInventory inv = invOpt.get();
            int onHandAfter = inv.getQuantityOnHand() != null ? inv.getQuantityOnHand() : 0;
            int reservedAfter = inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0;

            InventoryTransaction tx = new InventoryTransaction();
            tx.setInventoryId(inventoryId);
            tx.setOrderItemId(orderItemId);
            tx.setType("SALE");
            tx.setOnHandDelta(-quantity);
            tx.setReservedDelta(0);
            tx.setOnHandAfter(onHandAfter);
            tx.setReservedAfter(reservedAfter);
            tx.setEventKey("sale-resend-" + orderItemId);
            tx.setReason("Xuất kho giao bù theo đơn " + orderCode);
            tx.setCreatedBy(null);
            inventoryTransactionRepository.save(tx);
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableShopVariants(Long shopId) {
        if (shopId == null) return List.of();
        List<ProductVariant> variants = productVariantRepository.findActiveByShopIdWithProductAndUnit(shopId);
        if (variants.isEmpty()) return List.of();

        List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<Long, VariantInventory> invMap = new HashMap<>();
        for (VariantInventory vi : variantInventoryRepository.findByVariantIdIn(variantIds)) {
            invMap.put(vi.getVariantId(), vi);
        }

        List<Map<String, Object>> result = new ArrayList<>(variants.size());
        for (ProductVariant pv : variants) {
            VariantInventory vi = invMap.get(pv.getId());
            long invId = vi != null && vi.getId() != null ? vi.getId() : 0L;
            int avail = vi != null
                    ? (vi.getQuantityOnHand() != null ? vi.getQuantityOnHand() : 0) - (vi.getReservedQuantity() != null ? vi.getReservedQuantity() : 0)
                    : 0;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("variant_id", pv.getId());
            m.put("sku", pv.getSku());
            m.put("variant_name", pv.getName());
            m.put("price", pv.getPrice());
            m.put("image_url", pv.getImageUrl());
            m.put("product_id", pv.getProduct().getId());
            m.put("product_name", pv.getProduct().getName());
            m.put("base_unit_name", pv.getBaseUnit().getName());
            m.put("inventory_id", invId);
            m.put("available_quantity", avail);
            result.add(m);
        }
        return result;
    }

    @Transactional
    public Long submitShopDecisionResendCustom(
            Long ownerId,
            Long complaintId,
            List<Long> customVariantIds,
            List<Integer> customQuantities,
            String shopResponse) {

        Complaint cp = complaintRepository.findLockedByIdAndShopOwnerId(complaintId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xử lý khiếu nại này."));

        Long shopId = cp.getShopId();
        String currentStatus = cp.getStatus();
        if (!"PENDING_SHOP".equalsIgnoreCase(currentStatus) && !"PENDING".equalsIgnoreCase(currentStatus)) {
            throw new IllegalArgumentException("Khiếu nại không ở trạng thái chờ Shop phản hồi.");
        }
        if (cp.getReplacementOrderId() != null) {
            throw new IllegalArgumentException("Khiếu nại này đã có đơn giao bù được tạo trước đó.");
        }

        Long originalOrderId = cp.getOrderId();
        Long customerId = cp.getUserId();
        String complaintCode = cp.getComplaintCode();
        String orderCode = orderRepository.findById(originalOrderId).map(Order::getOrderCode).orElse("#" + originalOrderId);
        String cleanNote = (shopResponse == null || shopResponse.isBlank())
                ? "Cửa hàng đã tạo đơn giao bù sản phẩm mới gửi đến quý khách."
                : shopResponse.trim();
        if (cleanNote.length() > 1500) {
            throw new IllegalArgumentException("Nội dung phản hồi không được vượt quá 1500 ký tự.");
        }
        LocalDateTime now = LocalDateTime.now();

        Long replacementOrderId = createReplacementOrder(complaintId, originalOrderId, shopId, customerId, cleanNote, customVariantIds, customQuantities);

        cp.setStatus(STATUS_RESOLVED);
        cp.setShopDecision("RESEND_ITEMS");
        cp.setResolutionType("RESEND_ITEMS");
        cp.setResolutionNote(cleanNote);
        cp.setReplacementOrderId(replacementOrderId);
        cp.setShopResponse(cleanNote);
        cp.setShopRespondedAt(now);
        cp.setResolvedBy(ownerId);
        cp.setResolvedAt(now);
        cp.setIsShopFault(1);
        cp.setFaultParty("SHOP");
        cp.setUpdatedAt(now);
        complaintRepository.saveAndFlush(cp);

        String replacementOrderCode = orderRepository.findById(replacementOrderId)
                .map(Order::getOrderCode)
                .orElse("#" + replacementOrderId);

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

        Complaint cp = complaintRepository.findLockedByIdAndShopOwnerId(complaintId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xử lý khiếu nại này."));

        Long shopId = cp.getShopId();
        String currentStatus = cp.getStatus();
        if (!"PENDING_SHOP".equalsIgnoreCase(currentStatus) && !"PENDING".equalsIgnoreCase(currentStatus)) {
            throw new IllegalArgumentException("Khiếu nại không ở trạng thái chờ Shop phản hồi.");
        }
        if (cp.getVoucherId() != null) {
            throw new IllegalArgumentException("Khiếu nại này đã được tặng voucher đền bù trước đó.");
        }

        Long customerId = cp.getUserId();
        String complaintCode = cp.getComplaintCode();
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
            if (voucherRepository.existsByShopIdAndCode(shopId, code)) {
                throw new IllegalArgumentException("Mã voucher '" + code + "' đã tồn tại trong cửa hàng. Vui lòng chọn mã khác.");
            }
        }

        String name = (voucherName == null || voucherName.isBlank())
                ? "Voucher đền bù khiếu nại " + complaintCode
                : voucherName.trim();
        LocalDateTime start = startsAt != null ? startsAt : now;
        LocalDateTime end = endsAt != null && endsAt.isAfter(start) ? endsAt : start.plusDays(30);

        Voucher voucher = new Voucher();
        voucher.setShopId(shopId);
        voucher.setCode(code);
        voucher.setName(name);
        voucher.setDiscountType(discType);
        voucher.setDiscountValue(discVal);
        voucher.setMaxDiscountAmount((maxDiscountAmount != null && "PERCENTAGE".equals(discType)) ? maxDiscountAmount : null);
        voucher.setMinOrderValue(minVal);
        voucher.setMaxUsages(1);
        voucher.setUsedCount(0);
        voucher.setIsPublic(false);
        voucher.setVoucherType("DISCOUNT");
        voucher.setStatus("ACTIVE");
        voucher.setStartsAt(start);
        voucher.setEndsAt(end);

        Long voucherId = voucherRepository.save(voucher).getId();

        try {
            if (!customerSavedVoucherRepository.existsByUserIdAndVoucherId(customerId, voucherId)) {
                customerSavedVoucherRepository.save(new CustomerSavedVoucher(customerId, voucherId, now));
            }
        } catch (Exception ex) {
            System.err.println("Warning: Cannot save voucher to customer wallet: " + ex.getMessage());
        }

        cp.setStatus(STATUS_RESOLVED);
        cp.setShopDecision("ISSUE_VOUCHER");
        cp.setResolutionType("ISSUE_VOUCHER");
        cp.setResolutionNote(cleanNote);
        cp.setVoucherId(voucherId);
        cp.setVoucherCode(code);
        cp.setShopResponse(cleanNote);
        cp.setShopRespondedAt(now);
        cp.setResolvedBy(ownerId);
        cp.setResolvedAt(now);
        cp.setIsShopFault(1);
        cp.setFaultParty("SHOP");
        cp.setUpdatedAt(now);
        complaintRepository.saveAndFlush(cp);

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
        if (!voucherRepository.existsByShopIdAndCode(shopId, clean)) {
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
        List<Long> overdueIds = complaintRepository.findOverduePendingShopIds(now);

        int escalatedCount = 0;
        for (Long complaintId : overdueIds) {
            Optional<Complaint> lockedOpt = complaintRepository.findLockedById(complaintId);
            if (lockedOpt.isEmpty()) {
                continue;
            }
            Complaint c = lockedOpt.get();
            if (entityManager != null && entityManager.contains(c)) {
                entityManager.refresh(c);
            }
            if (!"PENDING_SHOP".equals(c.getStatus()) && !"PENDING".equals(c.getStatus())) {
                continue;
            }
            if (c.getSlaDueAt() == null || c.getSlaDueAt().isAfter(now)) {
                continue;
            }

            c.setStatus(STATUS_ESCALATED);
            c.setEscalatedAt(now);
            c.setUpdatedAt(now);
            complaintRepository.saveAndFlush(c);

            escalatedCount++;
            Long customerId = c.getUserId();
            Long orderId = c.getOrderId();
            Long shopOwnerId = shopRepository.findById(c.getShopId()).map(Shop::getOwnerId).orElse(null);
            String complaintCode = c.getComplaintCode();
            String orderCode = orderRepository.findById(orderId).map(Order::getOrderCode).orElse("#" + orderId);

            createNotificationSafe(customerId,
                    "COMPLAINT_SLA_ESCALATED_CUST_" + complaintId,
                    "COMPLAINT",
                    "Khiếu nại " + complaintCode + " đã chuyển lên Ban Vận hành phân xử",
                    "Do Cửa hàng không phản hồi trong thời hạn SLA 6 giờ, hệ thống đã tự động chuyển khiếu nại đơn " + orderCode + " lên Bộ phận Ban Vận hành phân xử.",
                    "COMPLAINT", complaintId);

            if (shopOwnerId != null) {
                createNotificationSafe(shopOwnerId,
                        "COMPLAINT_SLA_ESCALATED_SHOP_" + complaintId,
                        "COMPLAINT",
                        "⚠️ Quá hạn 6h phản hồi khiếu nại " + complaintCode,
                        "Khiếu nại trên đơn hàng " + orderCode + " đã quá hạn SLA 6 giờ và được chuyển thẳng lên Ban Vận hành phân xử.",
                        "ORDER", orderId);
            }

            notifyRoleUsers("OPERATIONS_MANAGER",
                    "COMPLAINT_SLA_ESCALATED_OPS_" + complaintId,
                    "COMPLAINT",
                    "⏰ [Quá hạn SLA 6h] Khiếu nại " + complaintCode + " chuyển Ban Vận hành phân xử",
                    "Shop không phản hồi khiếu nại đơn " + orderCode + " trong 6 giờ. Vui lòng tiếp nhận phân xử.",
                    "COMPLAINT", complaintId);
        }
        return escalatedCount;
    }

    /**
     * Gửi thông báo yêu cầu / đôn đốc Chủ Shop giải trình khiếu nại.
     */
    @Transactional
    public void requestShopResponse(Long complaintId, Long csUserId, String requestNote) {
        Complaint c = complaintRepository.findLockedById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu khiếu nại #" + complaintId));

        String status = c.getStatus();
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
        int nextRemindCount = (c.getShopRemindCount() != null ? c.getShopRemindCount() : 0) + 1;

        if (c.getAssignedTo() == null) {
            c.setAssignedTo(csUserId);
        }
        c.setShopRemindCount(nextRemindCount);
        c.setLastRemindedAt(now);
        c.setCsShopRequestNote(cleanNote);
        c.setUpdatedAt(now);
        complaintRepository.saveAndFlush(c);

        Long shopOwnerId = shopRepository.findById(c.getShopId()).map(Shop::getOwnerId).orElse(null);
        Long orderId = c.getOrderId();
        String complaintCode = c.getComplaintCode();
        String orderCode = orderRepository.findById(orderId).map(Order::getOrderCode).orElse("#" + orderId);
        LocalDateTime slaDueAt = c.getSlaDueAt();
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
        if (customerId == null) return List.of();
        List<Complaint> complaints;
        if (statusFilter == null || statusFilter.isBlank() || "all".equalsIgnoreCase(statusFilter)) {
            complaints = complaintRepository.findByUserIdOrderByCreatedAtDescIdDesc(customerId);
        } else {
            List<String> statuses = resolveStatusFilterList(statusFilter);
            complaints = complaintRepository.findByUserIdAndStatusInOrderByCreatedAtDescIdDesc(customerId, statuses);
        }
        return mapComplaintsToViewRows(complaints);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countCustomerComplaintsByStatus(Long customerId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", complaintRepository.countByUserId(customerId));
        counts.put("processing", complaintRepository.countByUserIdAndStatusIn(customerId, ACTIVE_COMPLAINT_STATUSES));
        counts.put("processed", complaintRepository.countByUserIdAndStatusIn(customerId, List.of(STATUS_RESOLVED, STATUS_REJECTED)));
        counts.put("cancelled", complaintRepository.countByUserIdAndStatus(customerId, STATUS_CANCELLED));
        return counts;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCsComplaints(
            String statusFilter,
            String priorityFilter,
            String reasonFilter,
            Boolean overdueOnly,
            String keyword) {

        boolean filterByStatus = statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter);
        List<String> statuses = filterByStatus ? resolveStatusFilterList(statusFilter) : List.of(STATUS_PENDING_OPERATIONS);
        String priority = (priorityFilter != null && !priorityFilter.isBlank() && !"ALL".equalsIgnoreCase(priorityFilter))
                ? priorityFilter.trim().toUpperCase(Locale.ROOT) : "";
        String reason = (reasonFilter != null && !reasonFilter.isBlank() && !"ALL".equalsIgnoreCase(reasonFilter))
                ? reasonFilter.trim().toUpperCase(Locale.ROOT) : "";
        String kw = keyword != null ? keyword.trim() : "";

        List<Complaint> complaints = complaintRepository.searchCsComplaints(filterByStatus, statuses, priority, reason, kw);
        List<Map<String, Object>> rows = mapComplaintsToViewRows(complaints);
        if (Boolean.TRUE.equals(overdueOnly)) {
            rows.removeIf(r -> !"OVERDUE".equals(r.get("sla_state")) && !"BREACHED_SLA".equals(r.get("sla_state")));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listComplaintsByOrder(Long orderId) {
        if (orderId == null) return List.of();
        List<Complaint> complaints = complaintRepository.findByOrderIdOrderByCreatedAtDescIdDesc(orderId);
        return mapComplaintsToViewRows(complaints);
    }

    private List<String> resolveStatusFilterList(String rawFilter) {
        String norm = rawFilter.trim().toUpperCase(Locale.ROOT);
        return switch (norm) {
            case "ACTIVE", "PROCESSING" -> ACTIVE_COMPLAINT_STATUSES;
            case "PENDING_OPERATIONS", "PENDING_OPS" -> List.of(STATUS_PENDING_OPERATIONS, "PENDING_OPS");
            case "PENDING_SHOP" -> List.of(STATUS_PENDING_SHOP);
            case "PENDING" -> List.of(STATUS_PENDING_OPERATIONS, "PENDING_OPS", STATUS_PENDING_SHOP, "PENDING");
            case "IN_PROGRESS", "ESCALATED" -> List.of(STATUS_ESCALATED, "IN_PROGRESS");
            case "PROCESSED" -> List.of(STATUS_RESOLVED, STATUS_REJECTED);
            default -> List.of(norm);
        };
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
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu khiếu nại #" + complaintId));

        Map<String, Object> raw = buildComplaintDetailMap(c);
        Map<String, Object> detail = enrichSingleComplaintRow(raw);
        Long orderId = c.getOrderId();
        Long shopId = c.getShopId();
        Long customerId = c.getUserId();
        Long orderItemId = c.getOrderItemId();

        long actualPaidAmount = calculateActualPaidAmount(orderId, orderItemId);
        detail.put("actual_paid_amount", actualPaidAmount);

        if (customerId != null) {
            customerBankAccountRepository.findById(customerId).ifPresent(b -> {
                detail.put("customer_bank_name", b.getBankName());
                detail.put("customer_bank_account_no", b.getBankAccountNo());
                detail.put("customer_bank_account_holder", b.getBankAccountHolder());
            });
        }

        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
        List<Map<String, Object>> orderItems = new ArrayList<>(items.size());
        for (OrderItem oi : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", oi.getId());
            m.put("variant_id", oi.getVariantId());
            m.put("product_name", oi.getProductName());
            m.put("variant_name", oi.getVariantName());
            m.put("sku", oi.getSku());
            m.put("image_url", oi.getImageUrl());
            m.put("base_unit_name", oi.getBaseUnitName());
            m.put("quantity", oi.getQuantity());
            m.put("unit_price", oi.getUnitPrice());
            m.put("sale_unit_price", oi.getSaleUnitPrice());
            m.put("line_total", oi.getLineTotal());
            orderItems.add(m);
        }
        detail.put("orderItems", orderItems);

        long shopTotalComplaints = complaintRepository.countByShopId(shopId);
        long shopFaultComplaints = complaintRepository.countByShopIdAndIsShopFault(shopId, 1);
        long shopLowReviews = reviewRepository.countByShopIdAndMaxRating(shopId, 2);

        detail.put("shopTotalComplaints", shopTotalComplaints);
        detail.put("shopFaultComplaints", shopFaultComplaints);
        detail.put("shopLowReviews", shopLowReviews);

        return detail;
    }

    /**
     * Chủ động gửi cảnh báo vi phạm của Shop lên bộ phận Vận hành (Operations Manager).
     */
    @Transactional
    public void escalateShopToOperations(Long shopId, String reason) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cửa hàng #" + shopId));

        String shopName = shop.getName();
        String cleanReason = (reason == null || reason.isBlank())
                ? "Gian hàng có nhiều khiếu nại chất lượng nông sản và đánh giá tiêu cực từ người mua."
                : reason.trim();

        complaintRepository.markEscalatedToOpsByShopId(shopId);

        notifyRoleUsers("OPERATIONS_MANAGER",
                "MANUAL_ESCALATE_SHOP_" + shopId + "_" + System.currentTimeMillis(),
                "VIOLATION_ALERT",
                "🚨 Đề nghị Hậu kiểm khẩn cấp Shop: " + shopName,
                "Hồ sơ cảnh báo vi phạm của shop '" + shopName + "' đã được chuyển sang Ban Vận hành. Lý do: " + cleanReason,
                "SHOP", shopId);
    }

    public Map<String, Long> getComplaintStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("countPendingOps", complaintRepository.countByStatusIn(List.of(STATUS_PENDING_OPERATIONS, "PENDING_OPS")));
        counts.put("countPendingShop", complaintRepository.countByStatus(STATUS_PENDING_SHOP));
        counts.put("countEscalated", complaintRepository.countByStatusIn(List.of(STATUS_ESCALATED, "IN_PROGRESS")));
        counts.put("countResolved", complaintRepository.countByStatus(STATUS_RESOLVED));
        counts.put("countRejected", complaintRepository.countByStatus(STATUS_REJECTED));
        counts.put("countAll", complaintRepository.countByStatusNot(STATUS_CANCELLED));
        return counts;
    }

    // =========================================================================
    // 6. BÁO CÁO & GIÁM SÁT (OPERATIONS & CS DASHBOARD - JDBCTEMPLATE)
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getAnalyticsAndReports() {
        Map<String, Object> result = new HashMap<>();
        result.put("disputeReport", buildDisputeReport());
        result.put("csSlaReport", buildCsSlaReport());
        result.put("violationAlerts", buildViolationAlerts());
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
        long distinctDisputedOrders = queryCount("SELECT COUNT(DISTINCT order_id) FROM complaints WHERE status <> 'CANCELLED'");

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

    public Map<String, Object> getContentModerationMetrics() {
        return buildContentModerationMetrics();
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

    // =========================================================================
    // HELPER METHODS (JPA ENTITY -> VIEW MAP & ENRICHMENT)
    // =========================================================================

    private List<Map<String, Object>> mapComplaintsToViewRows(List<Complaint> complaints) {
        if (complaints == null || complaints.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> orderIds = new HashSet<>();
        Set<Long> shopIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();

        for (Complaint c : complaints) {
            if (c.getOrderId() != null) orderIds.add(c.getOrderId());
            if (c.getShopId() != null) shopIds.add(c.getShopId());
            if (c.getUserId() != null) userIds.add(c.getUserId());
            if (c.getResolvedBy() != null) userIds.add(c.getResolvedBy());
            if (c.getAssignedTo() != null) userIds.add(c.getAssignedTo());
            if (c.getOrderItemId() != null) itemIds.add(c.getOrderItemId());
        }

        Map<Long, Order> orderMap = new HashMap<>();
        orderRepository.findAllById(orderIds).forEach(o -> orderMap.put(o.getId(), o));

        Map<Long, Shop> shopMap = new HashMap<>();
        shopRepository.findAllById(shopIds).forEach(s -> shopMap.put(s.getId(), s));

        Map<Long, User> userMap = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> userMap.put(u.getId(), u));

        Map<Long, OrderItem> itemMap = new HashMap<>();
        if (!itemIds.isEmpty()) {
            orderItemRepository.findAllById(itemIds).forEach(oi -> itemMap.put(oi.getId(), oi));
        }

        List<Map<String, Object>> result = new ArrayList<>(complaints.size());
        for (Complaint c : complaints) {
            Map<String, Object> raw = toBaseComplaintMap(c);
            Order o = orderMap.get(c.getOrderId());
            if (o != null) {
                raw.put("order_code", o.getOrderCode());
                raw.put("total_amount", o.getTotalAmount());
                raw.put("order_status", o.getStatus());
                raw.put("delivery_status", o.getDeliveryStatus());
            }
            User u = userMap.get(c.getUserId());
            if (u != null) {
                raw.put("customer_name", u.getFullName());
                raw.put("customer_phone", u.getPhone());
                raw.put("customer_email", u.getEmail());
            }
            Shop s = shopMap.get(c.getShopId());
            if (s != null) {
                raw.put("shop_name", s.getName());
                raw.put("shop_phone", s.getPhone());
            }
            OrderItem oi = c.getOrderItemId() != null ? itemMap.get(c.getOrderItemId()) : null;
            if (oi != null) {
                raw.put("item_product_name", oi.getProductName());
                raw.put("item_variant_name", oi.getVariantName());
            }
            Long staffId = c.getResolvedBy() != null ? c.getResolvedBy() : c.getAssignedTo();
            User staff = staffId != null ? userMap.get(staffId) : null;
            raw.put("resolved_by_name", staff != null ? staff.getFullName() : null);

            result.add(enrichSingleComplaintRow(raw));
        }
        return result;
    }

    private Map<String, Object> buildComplaintDetailMap(Complaint c) {
        Map<String, Object> raw = toBaseComplaintMap(c);

        orderRepository.findById(c.getOrderId()).ifPresent(o -> {
            raw.put("order_code", o.getOrderCode());
            raw.put("total_amount", o.getTotalAmount());
            raw.put("subtotal", o.getSubtotal());
            raw.put("voucher_discount", o.getVoucherDiscount());
            raw.put("points_discount", o.getPointsDiscount());
            raw.put("shipping_fee", o.getShippingFee());
            raw.put("order_status", o.getStatus());
            raw.put("payment_method", o.getPaymentMethod());
            raw.put("payment_status", o.getPaymentStatus());
            raw.put("delivery_status", o.getDeliveryStatus());
            raw.put("delivery_code", o.getDeliveryCode());
            raw.put("recipient_name", o.getRecipientName());
            raw.put("recipient_phone", o.getRecipientPhone());
            raw.put("delivery_address", o.getDeliveryAddress());
            raw.put("delivery_ward_name", o.getDeliveryWardName());
            raw.put("delivery_province_name", o.getDeliveryProvinceName());
            raw.put("order_created_at", o.getCreatedAt());
            raw.put("order_delivered_at", o.getDeliveredAt());
            raw.put("order_completed_at", o.getCompletedAt());
        });

        userRepository.findById(c.getUserId()).ifPresent(u -> {
            raw.put("customer_name", u.getFullName());
            raw.put("customer_email", u.getEmail());
            raw.put("customer_phone", u.getPhone());
        });

        shopRepository.findById(c.getShopId()).ifPresent(s -> {
            raw.put("shop_name", s.getName());
            raw.put("shop_phone", s.getPhone());
            raw.put("shop_operating_status", s.getOperatingStatus());
            raw.put("shop_owner_id", s.getOwnerId());
            if (s.getOwnerId() != null) {
                userRepository.findById(s.getOwnerId())
                        .ifPresent(owner -> raw.put("shop_owner_name", owner.getFullName()));
            }
        });

        if (c.getOrderItemId() != null) {
            orderItemRepository.findById(c.getOrderItemId()).ifPresent(oi -> {
                raw.put("item_product_name", oi.getProductName());
                raw.put("item_variant_name", oi.getVariantName());
                raw.put("item_sku", oi.getSku());
                raw.put("item_quantity", oi.getQuantity());
                raw.put("item_line_total", oi.getLineTotal());
            });
        }

        if (c.getResolvedBy() != null) {
            userRepository.findById(c.getResolvedBy())
                    .ifPresent(staff -> raw.put("resolved_by_name", staff.getFullName()));
        }
        if (c.getAssignedTo() != null) {
            userRepository.findById(c.getAssignedTo())
                    .ifPresent(assignee -> raw.put("assigned_to_name", assignee.getFullName()));
        }

        return raw;
    }

    private Map<String, Object> toBaseComplaintMap(Complaint c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("complaint_code", c.getComplaintCode());
        m.put("order_id", c.getOrderId());
        m.put("order_item_id", c.getOrderItemId());
        m.put("user_id", c.getUserId());
        m.put("shop_id", c.getShopId());
        m.put("reason_type", c.getReasonType());
        m.put("title", c.getTitle());
        m.put("description", c.getDescription());
        m.put("evidence_image_url", c.getEvidenceImageUrl());
        m.put("priority", c.getPriority());
        m.put("status", c.getStatus());
        m.put("source_type", c.getSourceType());
        m.put("desired_resolution", c.getDesiredResolution());
        m.put("requested_refund_amount", c.getRequestedRefundAmount());
        m.put("final_refund_amount", c.getFinalRefundAmount());
        m.put("replacement_order_id", c.getReplacementOrderId());
        m.put("voucher_id", c.getVoucherId());
        m.put("voucher_code", c.getVoucherCode());
        m.put("shop_decision", c.getShopDecision());
        m.put("shop_response", c.getShopResponse());
        m.put("shop_responded_at", c.getShopRespondedAt());
        m.put("operations_decision", c.getOperationsDecision());
        m.put("operations_note", c.getOperationsNote());
        m.put("operations_reviewed_at", c.getOperationsReviewedAt());
        m.put("escalated_at", c.getEscalatedAt());
        m.put("shop_remind_count", c.getShopRemindCount());
        m.put("last_reminded_at", c.getLastRemindedAt());
        m.put("cs_shop_request_note", c.getCsShopRequestNote());
        m.put("resolution_type", c.getResolutionType());
        m.put("resolution_note", c.getResolutionNote());
        m.put("is_shop_fault", c.getIsShopFault());
        m.put("fault_party", c.getFaultParty());
        m.put("refund_method", c.getRefundMethod());
        m.put("refund_status", c.getRefundStatus());
        m.put("refund_completed_at", c.getRefundCompletedAt());
        m.put("escalated_to_ops", c.getEscalatedToOps());
        m.put("sla_due_at", c.getSlaDueAt());
        m.put("assigned_to", c.getAssignedTo());
        m.put("resolved_by", c.getResolvedBy());
        m.put("resolved_at", c.getResolvedAt());
        m.put("created_at", c.getCreatedAt());
        m.put("updated_at", c.getUpdatedAt());
        return m;
    }

    private Map<String, Object> enrichSingleComplaintRow(Map<String, Object> raw) {
        Map<String, Object> row = new LinkedHashMap<>(raw);
        String reasonType = String.valueOf(row.getOrDefault("reason_type", "QUALITY_ISSUE"));
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

        row.put("reason_label", REASON_LABELS.getOrDefault(reasonType, reasonType));
        row.put("status_label", STATUS_LABELS.getOrDefault(status, status));
        row.put("priority_label", PRIORITY_LABELS.getOrDefault(priority, priority));

        // Trạng thái rút gọn cho Khách hàng (Customer Facing)
        String customerStatus;
        if (STATUS_PENDING_OPERATIONS.equals(status)) {
            customerStatus = "Chờ Vận hành duyệt";
        } else if (STATUS_PENDING_SHOP.equals(status) || STATUS_ESCALATED.equals(status)) {
            customerStatus = "Chờ Shop xử lý";
        } else if (STATUS_RESOLVED.equals(status)) {
            customerStatus = "Đã xử lý (Thành công)";
        } else if (STATUS_REJECTED.equals(status)) {
            customerStatus = "Đã xử lý (Bác bỏ)";
        } else if (STATUS_CANCELLED.equals(status)) {
            customerStatus = "Đã rút khiếu nại";
        } else {
            customerStatus = STATUS_LABELS.getOrDefault(status, status);
        }
        row.put("customer_status_label", customerStatus);

        String desiredRes = row.get("desired_resolution") != null ? String.valueOf(row.get("desired_resolution")) : null;
        row.put("desired_resolution", desiredRes);
        row.put("desired_resolution_label", desiredRes != null ? DESIRED_RESOLUTION_LABELS.getOrDefault(desiredRes, desiredRes) : "Giao bù hàng");

        if (row.get("replacement_order_id") != null) {
            Long rOrderId = ((Number) row.get("replacement_order_id")).longValue();
            orderRepository.findById(rOrderId).ifPresent(rOrder -> {
                row.put("replacement_order_code", rOrder.getOrderCode());
                row.put("replacement_order_status", rOrder.getStatus());
                row.put("replacement_delivery_status", rOrder.getDeliveryStatus());
            });
        }
        row.put("fault_party", row.getOrDefault("fault_party", "SHOP"));
        row.put("resolution_label", resolutionType != null ? RESOLUTION_LABELS.getOrDefault(resolutionType, resolutionType) : "Chưa có kết luận");

        long requestedRefund = toLong(row.get("requested_refund_amount"));
        long finalRefund = toLong(row.get("final_refund_amount"));
        row.put("requested_refund_amount", requestedRefund);
        row.put("final_refund_amount", finalRefund);

        LocalDateTime opsReviewedAt = toLocalDateTime(row.get("operations_reviewed_at"));
        if (opsReviewedAt != null) {
            row.put("operations_reviewed_at_fmt", opsReviewedAt.format(DT_FMT));
        }

        LocalDateTime createdAt = toLocalDateTime(row.get("created_at"));
        LocalDateTime slaDueAt = toLocalDateTime(row.get("sla_due_at"));
        LocalDateTime resolvedAt = toLocalDateTime(row.get("resolved_at"));
        LocalDateTime shopRespondedAt = toLocalDateTime(row.get("shop_responded_at"));
        LocalDateTime now = LocalDateTime.now();

        row.put("created_at_fmt", createdAt != null ? createdAt.format(DT_FMT) : "—");
        row.put("sla_due_at_fmt", STATUS_PENDING_OPERATIONS.equals(status) ? "Bắt đầu sau khi Vận hành duyệt" : (slaDueAt != null ? slaDueAt.format(DT_FMT) : "—"));
        row.put("resolved_at_fmt", resolvedAt != null ? resolvedAt.format(DT_FMT) : "—");
        row.put("shop_responded_at_fmt", shopRespondedAt != null ? shopRespondedAt.format(DT_FMT) : "—");

        long remindCount = toLong(row.get("shop_remind_count"));
        row.put("shop_remind_count", remindCount);

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
        if ("QUALITY_ISSUE".equals(reasonType) || "QUANTITY_SHORTAGE".equals(reasonType)) {
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
            if (!readers.hasNext()) return false;
            String format = readers.next().getFormatName();
            return format != null && (format.equalsIgnoreCase("JPEG")
                    || format.equalsIgnoreCase("JPG")
                    || format.equalsIgnoreCase("PNG"));
        } catch (IOException ex) {
            return false;
        }
    }

    private void createNotificationSafe(Long userId, String eventKey, String type, String title, String content, String targetType, Long targetId) {
        if (userId == null) return;
        try {
            User user = userRepository.getReferenceById(userId);
            Notification n = new Notification(user, eventKey, type, title, content, targetType, targetId);
            notificationRepository.save(n);
        } catch (Exception ignored) {
        }
    }

    private void notifyRoleUsers(String roleCode, String eventKeyPrefix, String type, String title, String content, String targetType, Long targetId) {
        try {
            List<User> users = userRoleRepository.findUsersByRoleCode(roleCode);
            for (User u : users) {
                if ("ACTIVE".equalsIgnoreCase(u.getStatus())) {
                    createNotificationSafe(u.getId(), eventKeyPrefix + "_U" + u.getId(), type, title, content, targetType, targetId);
                }
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
