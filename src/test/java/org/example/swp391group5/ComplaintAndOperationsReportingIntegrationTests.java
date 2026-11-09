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
import java.math.BigDecimal;
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
        Long opsId = createUser("ops-complaint@test.local", "Phạm Vận Hành", "OPERATIONS_MANAGER");

        Long shopId = createTestShop(sellerId, "Nông Sản Tươi Đà Lạt");
        Long orderId = createTestOrder(customerId, shopId, "ORD-TEST-CMP-01", "DELIVERED", LocalDateTime.now().minusHours(2));

        MockMultipartFile evidenceImage = createTestPngImage();

        // 1. Customer creates complaint -> status starts at PENDING_OPERATIONS
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "QUALITY_ISSUE",
                "Dâu tây bị dập nát khoảng 30% hộp",
                "Khi mở hộp ra có nhiều quả bị chảy nước và dập nát, mong được hoàn tiền.",
                evidenceImage,
                "RETURN_REFUND"
        );
        assertNotNull(complaintId);

        List<Map<String, Object>> customerComplaints = complaintService.listCustomerComplaints(customerId, "ALL");
        assertEquals(1, customerComplaints.size());

        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertNotNull(detail);
        assertEquals("HIGH", detail.get("priority"));
        assertEquals("PENDING_OPERATIONS", detail.get("status"));
        assertEquals(500000L, ((Number) detail.get("requested_refund_amount")).longValue());

        // 2. Operations Manager approves complaint for Shop handling -> status becomes PENDING_SHOP
        complaintService.approveComplaintByOperations(opsId, complaintId, "Bằng chứng rõ ràng, chuyển Shop xử lý.");
        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("PENDING_SHOP", detail.get("status"));

        Map<String, Object> counts = shopOrderService.countComplaints(sellerId);
        assertEquals(1L, ((Number) counts.get("pending_shop_complaints")).longValue());
        List<Map<String, Object>> shopOrders = shopOrderService.list(sellerId, "complaint");
        assertEquals(1, shopOrders.size());
        assertEquals("PENDING_SHOP", shopOrders.get(0).get("complaint_status"));

        // 3. Operations Manager directly approves bank refund -> status becomes RESOLVED
        complaintService.approveComplaintRefundByOperations(
                opsId,
                complaintId,
                "Xác nhận dập hỏng, duyệt hoàn tiền 100% về tài khoản ngân hàng cho khách."
        );

        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals("RETURN_REFUND", detail.get("resolution_type"));
        assertEquals(500000L, ((Number) detail.get("final_refund_amount")).longValue());

        // 4. Verify Analytics Reports
        Map<String, Object> csAnalytics = complaintService.getAnalyticsAndReports();
        assertNotNull(csAnalytics.get("disputeReport"));
        assertNotNull(csAnalytics.get("csSlaReport"));
        assertNotNull(csAnalytics.get("violationAlerts"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> violationAlerts = (List<Map<String, Object>>) csAnalytics.get("violationAlerts");
        boolean shopAlertFound = violationAlerts.stream()
                .anyMatch(a -> shopId.equals(((Number) a.get("shop_id")).longValue()));
        assertTrue(shopAlertFound, "Shop with fault complaint should appear in Violation Alerts");

        Map<String, Object> opsData = operationsDashboardService.getDashboardData();
        assertNotNull(opsData.get("shopPerformance"));
        assertNotNull(opsData.get("approvalSla"));
        assertNotNull(opsData.get("deliverySla"));
    }

    @Test
    void testShopIssueVoucherResolutionAfterOperationsApproval() {
        Long customerId = createUser("buyer-accept@test.local", "Trần Mua Hàng", "CUSTOMER");
        Long sellerId = createUser("seller-accept@test.local", "Chủ Shop Thân Thiện", "SHOP_OWNER");
        Long opsId = createUser("ops-accept@test.local", "Vận Hành Duyệt", "OPERATIONS_MANAGER");
        Long shopId = createTestShop(sellerId, "Shop Hoa Quả Sạch");
        Long orderId = createTestOrder(customerId, shopId, "ORD-ACCEPT-01", "DELIVERED", LocalDateTime.now().minusHours(1));

        MockMultipartFile evidenceImage = createTestPngImage();

        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "QUANTITY_SHORTAGE",
                "Giao thiếu 500g nho xanh",
                "Đặt 2kg nhưng cân chỉ có 1.5kg, mong shop tặng voucher đền bù.",
                evidenceImage,
                "ISSUE_VOUCHER"
        );

        // Operations Manager approves first -> PENDING_SHOP
        complaintService.approveComplaintByOperations(opsId, complaintId, "Chuyển Shop phát hành voucher đền bù.");

        // Shop issues compensation voucher -> RESOLVED
        Long voucherId = complaintService.submitShopDecisionIssueVoucherCustom(
                sellerId,
                complaintId,
                "VC-TEST-40K",
                "Voucher đền bù thiếu nho",
                "FIXED_AMOUNT",
                BigDecimal.valueOf(40000),
                null,
                BigDecimal.ZERO,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30),
                "Shop thành thật xin lỗi và gửi tặng quý khách voucher 40,000đ."
        );
        assertNotNull(voucherId);

        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals("ISSUE_VOUCHER", detail.get("shop_decision"));
        assertEquals("VC-TEST-40K", detail.get("voucher_code"));
    }

    @Test
    void testComplaintValidationMatrixAndConstraints() {
        Long customerId = createUser("buyer-matrix@test.local", "Phan Kiểm Tra", "CUSTOMER");
        Long sellerId = createUser("seller-matrix@test.local", "Vựa Kiểm Tra", "SHOP_OWNER");
        Long shopId = createTestShop(sellerId, "Vựa Test");

        // 1. Order status CONFIRMED cannot create complaint
        Long confirmedOrderId = createTestOrder(customerId, shopId, "ORD-CONFIRMED-01", "CONFIRMED", null);
        IllegalArgumentException blockedConfirmed = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, confirmedOrderId, null, "DELIVERY_ISSUE", "Giao lâu", "Chưa thấy giao", null, "RESEND_ITEMS"
                )
        );
        assertTrue(blockedConfirmed.getMessage().contains("DELIVERED"));

        // 2. Order status SHIPPING is completely blocked for ALL reasons (including DELIVERY_ISSUE)
        Long shippingOrderId = createTestOrder(customerId, shopId, "ORD-SHIPPING-01", "SHIPPING", null);
        MockMultipartFile img = createTestPngImage();
        IllegalArgumentException blockedShippingQuality = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, shippingOrderId, null, "QUALITY_ISSUE", "Trái cây hư", "Trái cây dập nát", img, "RESEND_ITEMS"
                )
        );
        assertTrue(blockedShippingQuality.getMessage().contains("DELIVERED"));

        IllegalArgumentException blockedShippingDelivery = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, shippingOrderId, null, "DELIVERY_ISSUE", "Đang giao bị trễ", "Shipper chưa tới", img, "RESEND_ITEMS"
                )
        );
        assertTrue(blockedShippingDelivery.getMessage().contains("DELIVERED"));
        assertFalse((Boolean) complaintService.getOrderComplaintEligibility(customerId, shippingOrderId).get("canCreateComplaint"));

        // 3. Order status CANCELLED with delivery_status = FAILED is completely blocked
        Long failedDeliveryOrderId = createTestOrder(customerId, shopId, "ORD-FAILED-01", "CANCELLED", null);
        jdbc.update("UPDATE orders SET delivery_status = 'FAILED', cancelled_at = CURRENT_TIMESTAMP WHERE id = ?", failedDeliveryOrderId);
        IllegalArgumentException blockedFailedDelivery = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, failedDeliveryOrderId, null, "DELIVERY_ISSUE", "Giao thất bại", "Không nhận được hàng", img, "RESEND_ITEMS"
                )
        );
        assertTrue(blockedFailedDelivery.getMessage().contains("DELIVERED"));
        assertFalse((Boolean) complaintService.getOrderComplaintEligibility(customerId, failedDeliveryOrderId).get("canCreateComplaint"));

        // 4. Removed legacy reasons (DELIVERY_OR_SERVICE, OTHER) must be rejected
        Long deliveredOrderForReasonCheck = createTestOrder(customerId, shopId, "ORD-REASON-CHK-01", "DELIVERED", LocalDateTime.now().minusHours(1));
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderForReasonCheck, null, "DELIVERY_OR_SERVICE", "Thái độ", "Shipper khó chịu", img, "RESEND_ITEMS"
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderForReasonCheck, null, "OTHER", "Lý do khác", "Không vừa ý", img, "RESEND_ITEMS"
                )
        );

        // 5. Order status COMPLETED within 24h of delivered_at is allowed, exceeding 24h is blocked
        Long completedRecentOrderId = createTestOrder(customerId, shopId, "ORD-COMPLETED-OK-01", "COMPLETED", LocalDateTime.now().minusHours(5));
        assertTrue((Boolean) complaintService.getOrderComplaintEligibility(customerId, completedRecentOrderId).get("canCreateComplaint"));
        Long completedComplaintId = complaintService.submitCustomerComplaint(
                customerId, completedRecentOrderId, null, "WRONG_PRODUCT", "Giao nhầm loại táo", "Đặt táo Envy nhưng giao táo Gala", img, "RESEND_ITEMS"
        );
        assertNotNull(completedComplaintId);

        Long completedOldOrderId = createTestOrder(customerId, shopId, "ORD-COMPLETED-OLD-01", "COMPLETED", LocalDateTime.now().minusHours(26));
        assertFalse((Boolean) complaintService.getOrderComplaintEligibility(customerId, completedOldOrderId).get("canCreateComplaint"));
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, completedOldOrderId, null, "WRONG_PRODUCT", "Giao nhầm", "Quá hạn 24h", img, "RESEND_ITEMS"
                )
        );

        // 6. Order status DELIVERED:
        // Exceeding 24h is blocked
        Long oldDeliveredOrderId = createTestOrder(customerId, shopId, "ORD-OLD-01", "DELIVERED", LocalDateTime.now().minusHours(25));
        IllegalArgumentException blockedOver24h = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, oldDeliveredOrderId, null, "QUALITY_ISSUE", "Trái cây thối", "Dập mốc", null, "RETURN_REFUND"
                )
        );
        assertTrue(blockedOver24h.getMessage().contains("quá thời hạn 24 giờ"));

        // Within 24h: product defect WITHOUT evidence image is blocked
        Long deliveredOrderId = createTestOrder(customerId, shopId, "ORD-VALID-01", "DELIVERED", LocalDateTime.now().minusHours(2));
        IllegalArgumentException blockedNoImage = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Trái cây thối", "Dập mốc nhiều", null, "RETURN_REFUND"
                )
        );
        assertTrue(blockedNoImage.getMessage().contains("bắt buộc phải đính kèm ít nhất 1 hình ảnh"));

        // Fake image content with .png extension is blocked
        MockMultipartFile fakeImage = new MockMultipartFile("evidenceImage", "fake.png", "image/png", "not-an-actual-image".getBytes());
        IllegalArgumentException blockedFakeImage = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Trái cây thối", "Dập mốc nhiều", fakeImage, "RETURN_REFUND"
                )
        );
        assertTrue(blockedFakeImage.getMessage().contains("JPG hoặc PNG hợp lệ"));

        // Valid complaint within 24h with photo -> requested_refund_amount automatically saved as 100% actualPaidAmount (500,000đ)
        Long cId = complaintService.submitCustomerComplaint(
                customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Dập nát 20%", "Hộp bị nén", img, "RETURN_REFUND"
        );
        assertNotNull(cId);
        assertEquals(500000L, ((Number) complaintService.getComplaintDetail(cId).get("requested_refund_amount")).longValue());

        // Max 1 complaint per order constraint: cannot create second complaint
        IllegalArgumentException blockedDuplicate = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderId, null, "DELIVERY_ISSUE", "Thêm lỗi khác", "Mô tả chi tiết lỗi", img, "RESEND_ITEMS"
                )
        );
        assertTrue(blockedDuplicate.getMessage().contains("chỉ được phép gửi khiếu nại tối đa 1 lần"));

        // Customer cancels active complaint -> status becomes CANCELLED
        complaintService.cancelCustomerComplaint(customerId, cId);
        Map<String, Object> cancelledDetail = complaintService.getComplaintDetail(cId);
        assertEquals("CANCELLED", cancelledDetail.get("status"));

        // Recreating is allowed now that the previous complaint was CANCELLED and order is still within 24h
        Long cId2 = complaintService.submitCustomerComplaint(
                customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Dập nát tạo lại", "Hộp bị nén", img, "RESEND_ITEMS"
        );
        assertNotNull(cId2);
    }

    @Test
    void testOperationsRejectionWorkflow() {
        Long customerId = createUser("buyer-reject@test.local", "Khách Bị Từ Chối", "CUSTOMER");
        Long sellerId = createUser("seller-reject@test.local", "Shop Đúng Chuẩn", "SHOP_OWNER");
        Long opsId = createUser("ops-reject@test.local", "Vận Hành Thẩm Định", "OPERATIONS_MANAGER");
        Long shopId = createTestShop(sellerId, "Shop Hoa Quả Sạch");
        Long orderId = createTestOrder(customerId, shopId, "ORD-REJECT-01", "DELIVERED", LocalDateTime.now().minusHours(2));

        MockMultipartFile img = createTestPngImage();
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "DELIVERY_ISSUE",
                "Hàng giao hơi trễ",
                "Đơn hàng giao trễ 15 phút so với dự kiến.",
                img,
                "ISSUE_VOUCHER"
        );

        complaintService.rejectComplaintByOperations(
                opsId,
                complaintId,
                "Hình ảnh sản phẩm vẫn nguyên vẹn, thời gian giao nằm trong khung giờ cam kết."
        );

        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("REJECTED", detail.get("status"));
        assertEquals("REJECTED", detail.get("operations_decision"));

        List<Map<String, Object>> customerComplaints = complaintService.listCustomerComplaints(customerId, "all");
        assertFalse(customerComplaints.isEmpty());
        assertTrue(String.valueOf(customerComplaints.get(0).get("customer_status_label")).contains("Bác bỏ"));
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
                customerId, orderId, null, "QUALITY_ISSUE", "Bị dập", "Bị dập", img, "RETURN_REFUND"
        );
        // Reset delivered_at to 25 hours ago so it qualifies for auto-complete time
        jdbc.update("UPDATE orders SET delivered_at = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusHours(25)), orderId);

        // 1. Customer tries to manually complete order -> BLOCKED due to active complaint
        Map<String, Object> eligibility = complaintService.getOrderComplaintEligibility(customerId, orderId);
        assertTrue((Boolean) eligibility.get("hasActiveComplaint"));
        assertFalse((Boolean) eligibility.get("canCompleteOrder"));

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
        Long opsId = createUser("ops-sla@test.local", "Vận Hành SLA", "OPERATIONS_MANAGER");
        Long shopId = createTestShop(sellerId, "Shop Bận");
        Long orderId = createTestOrder(customerId, shopId, "ORD-SLA-01", "DELIVERED", LocalDateTime.now().minusHours(1));

        MockMultipartFile img = createTestPngImage();
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId, orderId, null, "QUALITY_ISSUE", "Quả úng", "Úng nước", img, "RETURN_REFUND"
        );

        // Operations Manager approves first so status transitions to PENDING_SHOP
        complaintService.approveComplaintByOperations(opsId, complaintId, "Chuyển Shop xử lý.");

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
