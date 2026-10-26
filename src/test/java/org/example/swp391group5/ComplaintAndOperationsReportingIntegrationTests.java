package org.example.swp391group5;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import service.complaint.ComplaintService;
import service.customer.OrderCheckoutService;
import service.operations.OperationsDashboardService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ComplaintAndOperationsReportingIntegrationTests {

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private OperationsDashboardService operationsDashboardService;

    @Autowired
    private OrderCheckoutService orderCheckoutService;

    @Autowired
    private service.shop.ShopOrderService shopOrderService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long createUser(String email, String name, String roleCode) {
        jdbc.update("INSERT INTO users(full_name, email, password_hash, points_balance) VALUES (?, ?, 'hash', 100)", name, email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        jdbc.update("MERGE INTO roles(code, name) KEY(code) VALUES(?, ?)", roleCode, roleCode);
        Long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbc.update("INSERT INTO user_roles(user_id, role_id) VALUES(?, ?)", userId, roleId);
        return userId;
    }

    private Long createTestShop(Long sellerId, String shopName) {
        jdbc.update("""
            INSERT INTO shops(owner_id, name, phone, province_code, ward_name, address_detail, approval_status, operating_status)
            VALUES (?, ?, '0911222333', '68', 'Phường 1', 'Đà Lạt, Lâm Đồng', 'APPROVED', 'OPEN')
            """, sellerId, shopName);
        return jdbc.queryForObject("SELECT id FROM shops WHERE owner_id = ?", Long.class, sellerId);
    }

    private Long createTestOrder(Long customerId, Long shopId, String orderCode, String status, LocalDateTime deliveredAt) {
        String checkoutCode = "CHK-" + orderCode;
        jdbc.update("""
            INSERT INTO checkouts(user_id, checkout_code, request_key, contact_email, payment_method, total_amount, status)
            VALUES (?, ?, ?, 'test@test.local', 'COD', 530000, 'CONFIRMED')
            """, customerId, checkoutCode, "REQ-" + orderCode);
        Long checkoutId = jdbc.queryForObject("SELECT id FROM checkouts WHERE checkout_code = ?", Long.class, checkoutCode);

        Timestamp delTs = deliveredAt != null ? Timestamp.valueOf(deliveredAt) : null;
        String deliveryCode = "DEL-" + orderCode;
        jdbc.update("""
            INSERT INTO orders(checkout_id, shop_id, order_code, recipient_name, recipient_phone,
                               delivery_province_code, delivery_province_name, delivery_ward_name, delivery_address,
                               shop_province_code, subtotal, points_used, points_discount, points_subsidy_amount,
                               qualifying_amount, points_earned, shipping_fee, total_amount, shop_revenue_amount,
                               payment_method, delivery_code, status, payment_status, delivery_status, delivered_at)
            VALUES (?, ?, ?, 'Nguyễn Khách Hàng', '0909123456',
                    '79', 'TP. Hồ Chí Minh', 'Phường Bến Nghé', '123 Lê Lợi, Q1',
                    '68', 500000, 0, 0, 0,
                    500000, 50, 30000, 530000, 500000,
                    'COD', ?, ?, 'PAID', 'DELIVERED', ?)
            """, checkoutId, shopId, orderCode, deliveryCode, status, delTs);
        return jdbc.queryForObject("SELECT id FROM orders WHERE order_code = ?", Long.class, orderCode);
    }

    private MockMultipartFile createTestPngImage() {
        try {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return new MockMultipartFile("evidenceImage", "test.png", "image/png", baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testEndToEndComplaintWorkflowAndOperationsReports() {
        Long customerId = createUser("buyer-complaint@test.local", "Nguyễn Văn Mua", "CUSTOMER");
        Long sellerId = createUser("seller-complaint@test.local", "Trần Chủ Vựa", "SHOP_OWNER");
        Long csId = createUser("cs-complaint@test.local", "Lê CSKH", "CS_STAFF");
        createUser("ops-complaint@test.local", "Phạm Vận Hành", "OPERATIONS_MANAGER");

        Long shopId = createTestShop(sellerId, "Nông Sản Tươi Đà Lạt");
        // DELIVERED just 2 hours ago
        Long orderId = createTestOrder(customerId, shopId, "ORD-TEST-CMP-01", "DELIVERED", LocalDateTime.now().minusHours(2));

        MockMultipartFile evidenceImage = createTestPngImage();

        // 1. Customer creates complaint (QUALITY_ISSUE -> auto HIGH priority, 6h SLA)
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "QUALITY_ISSUE",
                "Dâu tây bị dập nát khoảng 30% hộp",
                "Khi mở hộp ra có nhiều quả bị chảy nước và dập nát, mong shop đền bù điểm thưởng.",
                evidenceImage,
                50000L
        );
        assertNotNull(complaintId);

        // 2. Verify status is PENDING_SHOP and priority is HIGH
        List<Map<String, Object>> customerComplaints = complaintService.listCustomerComplaints(customerId, "ALL");
        assertEquals(1, customerComplaints.size());

        // Verify Shop Order list shows complaint badge and pending count
        Map<String, Object> counts = shopOrderService.countComplaints(sellerId);
        assertEquals(1L, ((Number) counts.get("pending_shop_complaints")).longValue());
        List<Map<String, Object>> shopOrders = shopOrderService.list(sellerId, "complaint");
        assertEquals(1, shopOrders.size());
        assertEquals("PENDING_SHOP", shopOrders.get(0).get("complaint_status"));
        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertNotNull(detail);
        assertEquals("HIGH", detail.get("priority"));
        assertEquals("PENDING_SHOP", detail.get("status"));
        assertEquals(50000L, ((Number) detail.get("requested_refund_amount")).longValue());

        // 2b. CS cannot resolve directly while ticket is in PENDING_SHOP
        IllegalArgumentException csBlockedEx = assertThrows(IllegalArgumentException.class, () ->
                complaintService.resolveComplaint(
                        complaintId, csId, "RESOLVED", "COMPENSATE_POINTS", "CS can thiệp", 50000L, true, false
                )
        );
        assertTrue(csBlockedEx.getMessage().contains("PENDING_SHOP"));

        // 3. Shop rejects complaint with explanation -> escalates to ESCALATED
        complaintService.submitShopDecision(
                sellerId,
                complaintId,
                "REJECT",
                "Shop đã đóng thùng xốp lót mút cẩn thận trước khi giao, đề nghị CSKH phân xử đối chiếu.",
                null
        );
        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("ESCALATED", detail.get("status"));
        assertEquals("REJECT", detail.get("shop_decision"));
        assertNotNull(detail.get("shop_response"));

        // 4. CS Staff arbitrates escalated ticket: resolves with adjusted finalRefundAmount = 30,000đ
        complaintService.resolveComplaint(
                complaintId,
                csId,
                "RESOLVED",
                "COMPENSATE_POINTS",
                "Xác nhận dập hỏng cục bộ, duyệt đền bù 30,000 điểm thiện chí cho khách và ghi nhận lỗi Shop.",
                30000L,
                true,
                true
        );

        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals(30000L, ((Number) detail.get("final_refund_amount")).longValue());

        // Verify customer points balance increased from 100 to 30100
        Long updatedPoints = jdbc.queryForObject("SELECT points_balance FROM users WHERE id = ?", Long.class, customerId);
        assertEquals(30100L, updatedPoints);

        // Verify shop order revenue was deducted by 30,000 (from 500,000 to 470,000)
        java.math.BigDecimal updatedRevenue = jdbc.queryForObject("SELECT shop_revenue_amount FROM orders WHERE id = ?", java.math.BigDecimal.class, orderId);
        assertNotNull(updatedRevenue);
        assertEquals(0, new java.math.BigDecimal("470000").compareTo(updatedRevenue));

        // Verify shop reputation was deducted by 5 (from 100 to 95)
        Integer shopReputation = jdbc.queryForObject("SELECT reputation_score FROM shops WHERE id = ?", Integer.class, shopId);
        assertNotNull(shopReputation);
        assertEquals(95, shopReputation.intValue());

        // 5. Verify CS Staff Analytics Reports (Reports & Alerts moved to CS Staff)
        Map<String, Object> csAnalytics = complaintService.getAnalyticsAndReports();
        assertNotNull(csAnalytics.get("disputeReport"));
        assertNotNull(csAnalytics.get("csSlaReport"));
        assertNotNull(csAnalytics.get("violationAlerts"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> violationAlerts = (List<Map<String, Object>>) csAnalytics.get("violationAlerts");
        boolean shopAlertFound = violationAlerts.stream()
                .anyMatch(a -> shopId.equals(((Number) a.get("shop_id")).longValue()));
        assertTrue(shopAlertFound, "Shop with fault complaint should appear in Violation Alerts");

        // Verify Operations Dashboard focus remains on core Operations KPIs (Shop, Approval SLA, Delivery SLA)
        Map<String, Object> opsData = operationsDashboardService.getDashboardData();
        assertNotNull(opsData.get("shopPerformance"));
        assertNotNull(opsData.get("approvalSla"));
        assertNotNull(opsData.get("deliverySla"));
    }

    @Test
    void testShopAcceptAutoResolvesAndCreditsPoints() {
        Long customerId = createUser("buyer-accept@test.local", "Trần Mua Hàng", "CUSTOMER");
        Long sellerId = createUser("seller-accept@test.local", "Chủ Shop Thân Thiện", "SHOP_OWNER");
        Long shopId = createTestShop(sellerId, "Shop Hoa Quả Sạch");
        Long orderId = createTestOrder(customerId, shopId, "ORD-ACCEPT-01", "DELIVERED", LocalDateTime.now().minusHours(1));

        MockMultipartFile evidenceImage = createTestPngImage();

        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "WEIGHT_OR_QUANTITY_SHORTAGE",
                "Giao thiếu 500g nho xanh",
                "Đặt 2kg nhưng cân chỉ có 1.5kg, mong shop hoàn điểm tương ứng.",
                evidenceImage,
                40000L
        );

        // Shop ACCEPTS complaint -> system sets finalRefundAmount = requestedRefundAmount and auto-credits points
        complaintService.submitShopDecision(
                sellerId,
                complaintId,
                "ACCEPT",
                "Shop kiểm tra lại thấy nhân viên đóng thiếu nửa cân nho. Shop thành thật xin lỗi và đồng ý hoàn đủ 40,000 điểm.",
                null
        );

        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals("ACCEPT", detail.get("shop_decision"));
        assertEquals(40000L, ((Number) detail.get("final_refund_amount")).longValue());
        assertEquals(40000L, ((Number) detail.get("compensated_points")).longValue());

        // Points balance: 100 + 40000 = 40100
        Long points = jdbc.queryForObject("SELECT points_balance FROM users WHERE id = ?", Long.class, customerId);
        assertEquals(40100L, points);

        // Verify shop order revenue was deducted by 40,000 (from 500,000 to 460,000)
        java.math.BigDecimal shopRevenueAfterAccept = jdbc.queryForObject("SELECT shop_revenue_amount FROM orders WHERE id = ?", java.math.BigDecimal.class, orderId);
        assertNotNull(shopRevenueAfterAccept);
        assertEquals(0, new java.math.BigDecimal("460000").compareTo(shopRevenueAfterAccept));
    }

    @Test
    void testComplaintValidationMatrixAndConstraints() {
        Long customerId = createUser("buyer-matrix@test.local", "Phan Kiểm Tra", "CUSTOMER");
        Long sellerId = createUser("seller-matrix@test.local", "Vựa Kiểm Tra", "SHOP_OWNER");
        Long shopId = createTestShop(sellerId, "Vựa Test");

        // 1. Order status CONFIRMED or COMPLETED cannot create complaint
        Long confirmedOrderId = createTestOrder(customerId, shopId, "ORD-CONFIRMED-01", "CONFIRMED", null);
        IllegalArgumentException blockedConfirmed = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, confirmedOrderId, null, "DELIVERY_OR_SERVICE", "Giao lâu", "Chưa thấy giao", null, 0L
                )
        );
        assertTrue(blockedConfirmed.getMessage().contains("DELIVERED"));

        // 2. Order status SHIPPING is completely blocked from complaints (complaints only allowed when DELIVERED)
        Long shippingOrderId = createTestOrder(customerId, shopId, "ORD-SHIPPING-01", "SHIPPING", null);
        IllegalArgumentException blockedShipping = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, shippingOrderId, null, "DELIVERY_OR_SERVICE", "Shipper thái độ", "Giao hàng cộc lốc", null, 0L
                )
        );
        assertTrue(blockedShipping.getMessage().contains("DELIVERED"));

        // 3. Order status DELIVERED:
        // Exceeding 24h is blocked
        Long oldDeliveredOrderId = createTestOrder(customerId, shopId, "ORD-OLD-01", "DELIVERED", LocalDateTime.now().minusHours(25));
        IllegalArgumentException blockedOver24h = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, oldDeliveredOrderId, null, "QUALITY_ISSUE", "Trái cây thối", "Dập mốc", null, 10000L
                )
        );
        assertTrue(blockedOver24h.getMessage().contains("quá thời hạn 24 giờ"));

        // Within 24h: product defect WITHOUT evidence image is blocked
        Long deliveredOrderId = createTestOrder(customerId, shopId, "ORD-VALID-01", "DELIVERED", LocalDateTime.now().minusHours(2));
        IllegalArgumentException blockedNoImage = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Trái cây thối", "Dập mốc nhiều", null, 10000L
                )
        );
        assertTrue(blockedNoImage.getMessage().contains("bắt buộc phải đính kèm ít nhất 1 hình ảnh"));

        // Requested refund amount exceeding actualPaidAmount (500,000đ goods subtotal) is blocked
        MockMultipartFile img = createTestPngImage();
        IllegalArgumentException blockedExceedRefund = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Dập nát", "Hư hết trơn", img, 600000L
                )
        );
        assertTrue(blockedExceedRefund.getMessage().contains("không được vượt quá"));

        // Valid complaint within 24h with photo & refund <= 500,000
        Long cId = complaintService.submitCustomerComplaint(
                customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Dập nát 20%", "Hộp bị nén", img, 100000L
        );
        assertNotNull(cId);

        // Max 1 complaint per order constraint: cannot create second complaint
        IllegalArgumentException blockedDuplicate = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderId, null, "DELIVERY_OR_SERVICE", "Thêm lỗi khác", "Mô tả", null, 0L
                )
        );
        assertTrue(blockedDuplicate.getMessage().contains("chỉ được phép gửi khiếu nại tối đa 1 lần"));

        // Customer cancels active complaint -> status becomes CANCELLED
        complaintService.cancelCustomerComplaint(customerId, cId);
        Map<String, Object> cancelledDetail = complaintService.getComplaintDetail(cId);
        assertEquals("CANCELLED", cancelledDetail.get("status"));

        // Recreating is allowed now that the previous complaint was CANCELLED and order is still within 24h
        Long cId2 = complaintService.submitCustomerComplaint(
                customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Dập nát tạo lại", "Hộp bị nén", img, 80000L
        );
        assertNotNull(cId2);
    }

    @Test
    void testPlatformFaultCompensationAndFreeShippingVoucher() {
        Long customerId = createUser("buyer-platform@test.local", "Khách Hàng Nền Tảng", "CUSTOMER");
        Long sellerId = createUser("seller-platform@test.local", "Shop Bị Oan", "SHOP_OWNER");
        Long csId = createUser("cs-platform@test.local", "Nhân Viên CS Phân Xử", "CS_STAFF");
        Long shopId = createTestShop(sellerId, "Shop Hoa Quả Uy Tín");
        Long orderId = createTestOrder(customerId, shopId, "ORD-PLATFORM-01", "DELIVERED", LocalDateTime.now().minusHours(2));

        MockMultipartFile img = createTestPngImage();
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "DELIVERY_OR_SERVICE",
                "Hàng bị móp do đơn vị vận chuyển",
                "Thùng hàng bị chèn ép trong quá trình giao của đơn vị vận chuyển.",
                img,
                100000L
        );

        // Shop rejects with explanation
        complaintService.submitShopDecision(
                sellerId,
                complaintId,
                "REJECT",
                "Shop đã quấn bọt khí rất dày, lỗi dập thùng do shipper làm rơi vỡ.",
                null
        );

        // CS staff arbitrates with PLATFORM fault (quỹ sàn tài trợ 50% = 50,000đ + tặng mã freeship)
        complaintService.resolveComplaint(
                complaintId,
                csId,
                "RESOLVED",
                "COMPENSATE_POINTS",
                "Xác định lỗi do khâu vận chuyển của Nền tảng. Quỹ Sàn tài trợ đền bù điểm và gửi tặng Voucher FreeShip.",
                50000L,
                false, // isShopFault = false
                "PLATFORM",
                true, // giftFreeShipping = true
                false
        );

        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals("PLATFORM", detail.get("fault_party"));
        assertNotNull(detail.get("gifted_freeship_voucher"));
        assertTrue(String.valueOf(detail.get("gifted_freeship_voucher")).startsWith("FS-"));

        // Customer receives points: initial 100 + 50000 = 50100
        Long custPoints = jdbc.queryForObject("SELECT points_balance FROM users WHERE id = ?", Long.class, customerId);
        assertEquals(50100L, custPoints);

        // Shop revenue remains UNTOUCHED (500,000)
        java.math.BigDecimal shopRev = jdbc.queryForObject("SELECT shop_revenue_amount FROM orders WHERE id = ?", java.math.BigDecimal.class, orderId);
        assertNotNull(shopRev);
        assertEquals(0, new java.math.BigDecimal("500000").compareTo(shopRev));

        // Shop reputation remains 100 (NOT penalized)
        Integer repScore = jdbc.queryForObject("SELECT reputation_score FROM shops WHERE id = ?", Integer.class, shopId);
        assertNotNull(repScore);
        assertEquals(100, repScore.intValue());

        // Customer status label in customer view is "Đã xử lý"
        List<Map<String, Object>> customerComplaints = complaintService.listCustomerComplaints(customerId, "all");
        assertFalse(customerComplaints.isEmpty());
        assertTrue(String.valueOf(customerComplaints.get(0).get("customer_status_label")).contains("Đã xử lý"));
    }

    @Test
    void testOrderAutoCompleteAndCustomerCompletionLocksDuringComplaint() {
        Long customerId = createUser("buyer-lock@test.local", "Võ Khóa Đơn", "CUSTOMER");
        Long sellerId = createUser("seller-lock@test.local", "Shop Khóa Đơn", "SHOP_OWNER");
        Long shopId = createTestShop(sellerId, "Shop Lock");

        // Order DELIVERED 25 hours ago with active complaint
        Long orderId = createTestOrder(customerId, shopId, "ORD-LOCK-01", "DELIVERED", LocalDateTime.now().minusHours(25));
        MockMultipartFile img = createTestPngImage();

        // Artificially allow complaint submission by adjusting delivered_at briefly
        jdbc.update("UPDATE orders SET delivered_at = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusHours(2)), orderId);
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId, orderId, null, "QUALITY_ISSUE", "Bị dập", "Bị dập", img, 20000L
        );
        // Reset delivered_at to 25 hours ago so it qualifies for auto-complete time
        jdbc.update("UPDATE orders SET delivered_at = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusHours(25)), orderId);

        // 1. Customer tries to manually complete order -> BLOCKED due to active complaint
        IllegalArgumentException blockedCustomerComplete = assertThrows(IllegalArgumentException.class, () ->
                orderCheckoutService.completeOrderByCustomer(customerId, orderId)
        );
        assertTrue(blockedCustomerComplete.getMessage().contains("đang có khiếu nại"));

        // 2. Scheduled Job runs auto-complete -> MUST SKIP this order
        int completedCount = orderCheckoutService.autoCompleteDeliveredOrders();
        assertEquals(0, completedCount);
        String currentOrderStatus = jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertEquals("DELIVERED", currentOrderStatus);

        // 3. Customer withdraws complaint -> complaint is CANCELLED
        complaintService.cancelCustomerComplaint(customerId, complaintId);

        // 4. Customer can now manually complete order
        orderCheckoutService.completeOrderByCustomer(customerId, orderId);
        currentOrderStatus = jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertEquals("COMPLETED", currentOrderStatus);
    }

    @Test
    void testSlaAutoEscalateOverdueShopComplaints() {
        Long customerId = createUser("buyer-sla@test.local", "Hoàng Quá Hạn", "CUSTOMER");
        Long sellerId = createUser("seller-sla@test.local", "Shop Bận Quá", "SHOP_OWNER");
        Long shopId = createTestShop(sellerId, "Shop Bận");
        Long orderId = createTestOrder(customerId, shopId, "ORD-SLA-01", "DELIVERED", LocalDateTime.now().minusHours(1));

        MockMultipartFile img = createTestPngImage();
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId, orderId, null, "QUALITY_ISSUE", "Quả úng", "Úng nước", img, 25000L
        );

        // Artificially backdate sla_due_at to 1 hour ago
        jdbc.update("UPDATE complaints SET sla_due_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(1)), complaintId);

        // Run scheduled SLA job
        int escalatedCount = complaintService.autoEscalateOverdueShopComplaints();
        assertTrue(escalatedCount >= 1);

        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("ESCALATED", detail.get("status"));
        assertNotNull(detail.get("escalated_at"));
    }
}
