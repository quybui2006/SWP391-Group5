package org.example.swp391group5;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
        Long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_code = ?", Long.class, orderCode);

        // Add 1 test order item
        jdbc.update("""
            INSERT INTO order_items (order_id, shop_id, variant_id, inventory_id, product_name, variant_name, sku, base_unit_name, quantity, unit_price, sale_unit_price, line_total, allocation_status)
            VALUES (?, ?, 1, 1, 'Dâu tây Đà Lạt', 'Hộp 500g', 'DAU-500G', 'Hộp', 2, 250000, 250000, 500000, 'CONSUMED')
        """, orderId, shopId);

        return orderId;
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
    void testShopSoleDecision_ResendItemsWorkflow() {
        Long customerId = createUser("buyer-resend@test.local", "Nguyễn Văn Mua", "CUSTOMER");
        Long sellerId = createUser("seller-resend@test.local", "Trần Chủ Vựa", "SHOP_OWNER");
        Long opsId = createUser("ops-resend@test.local", "Hoàng Quản Trị Vận Hành", "OPERATIONS_MANAGER");

        Long shopId = createTestShop(sellerId, "Nông Sản Tươi Đà Lạt");
        Long orderId = createTestOrder(customerId, shopId, "ORD-RESEND-01", "DELIVERED", LocalDateTime.now().minusHours(2));

        MockMultipartFile evidenceImage = createTestPngImage();

        // 1. Customer submits complaint selecting desired resolution: RESEND_ITEMS
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "DELIVERY_ISSUE",
                "Dâu tây bị dập nát do vận chuyển giao chậm",
                "Khi mở hộp ra có nhiều quả bị chảy nước do shipper giao chậm, mong shop giao bù hàng mới.",
                evidenceImage,
                "RESEND_ITEMS",
                0L
        );
        assertNotNull(complaintId);

        // 2. Verify complaint status is PENDING_OPERATIONS initially
        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertNotNull(detail);
        assertEquals("PENDING_OPERATIONS", detail.get("status"));
        assertEquals("RESEND_ITEMS", detail.get("desired_resolution"));

        // 3. Shop CANNOT process yet when in PENDING_OPERATIONS
        IllegalArgumentException shopBlockedEx = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitShopDecision(
                        sellerId,
                        complaintId,
                        "RESEND_ITEMS",
                        "Shop muốn xử lý sớm",
                        null,
                        null
                )
        );
        assertTrue(shopBlockedEx.getMessage().contains("Chờ Shop phản hồi") || shopBlockedEx.getMessage().contains("Chờ Vận hành duyệt"));

        // 4. Operations Manager approves the complaint (Step 1)
        complaintService.approveComplaintByOperations(opsId, complaintId, "Đã kiểm tra bằng chứng, khiếu nại hợp lệ.");

        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("PENDING_SHOP", detail.get("status"));
        assertEquals("APPROVED", detail.get("operations_decision"));

        // 5. Shop Owner chooses RESEND_ITEMS (Step 2) -> generates 0đ replacement order
        complaintService.submitShopDecision(
                sellerId,
                complaintId,
                "RESEND_ITEMS",
                "Shop thành thật xin lỗi, shop gửi ngay đơn giao bù trái cây mới tinh cho quý khách.",
                null,
                null
        );

        // 6. Verify complaint is marked RESOLVED and replacement order is linked
        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals("RESEND_ITEMS", detail.get("resolution_type"));
        Long replacementOrderId = ((Number) detail.get("replacement_order_id")).longValue();
        assertNotNull(replacementOrderId);

        // 6. Verify replacement order properties
        Map<String, Object> replacementOrder = jdbc.queryForMap("SELECT * FROM orders WHERE id = ?", replacementOrderId);
        assertEquals("RESEND", replacementOrder.get("order_type"));
        assertEquals(orderId, ((Number) replacementOrder.get("original_order_id")).longValue());
        assertEquals("CONFIRMED", replacementOrder.get("status"));
        assertEquals("PAID", replacementOrder.get("payment_status"));
        assertEquals("RESEND", replacementOrder.get("payment_method"));
        assertEquals(0, new java.math.BigDecimal("0").compareTo((java.math.BigDecimal) replacementOrder.get("total_amount")));
        assertEquals(0, new java.math.BigDecimal("0").compareTo((java.math.BigDecimal) replacementOrder.get("shipping_fee")));
        assertEquals(0, new java.math.BigDecimal("0").compareTo((java.math.BigDecimal) replacementOrder.get("shop_revenue_amount")));

        // Verify replacement order items were copied with line_total = 0
        List<Map<String, Object>> repItems = jdbc.queryForList("SELECT * FROM order_items WHERE order_id = ?", replacementOrderId);
        assertFalse(repItems.isEmpty());
        assertEquals(0, new java.math.BigDecimal("0").compareTo((java.math.BigDecimal) repItems.get(0).get("line_total")));

        // 7. Verify original order revenue is NOT deducted (no financial deduction on complaint)
        java.math.BigDecimal origRevenue = jdbc.queryForObject("SELECT shop_revenue_amount FROM orders WHERE id = ?", java.math.BigDecimal.class, orderId);
        assertNotNull(origRevenue);
        assertEquals(0, new java.math.BigDecimal("500000").compareTo(origRevenue));

        // 8. Verify shop reputation score was NOT deducted
        Integer repScore = jdbc.queryForObject("SELECT reputation_score FROM shops WHERE id = ?", Integer.class, shopId);
        assertNotNull(repScore);
        assertEquals(100, repScore.intValue());
    }

    @Test
    void testShopSoleDecision_IssueVoucherWorkflow() {
        Long customerId = createUser("buyer-voucher@test.local", "Trần Khách Voucher", "CUSTOMER");
        Long sellerId = createUser("seller-voucher@test.local", "Chủ Vựa Voucher", "SHOP_OWNER");
        Long shopId = createTestShop(sellerId, "Vựa Trái Cây Miền Tây");
        Long orderId = createTestOrder(customerId, shopId, "ORD-VOUCHER-01", "DELIVERED", LocalDateTime.now().minusHours(1));

        MockMultipartFile evidenceImage = createTestPngImage();

        Long opsId = createUser("ops-voucher@test.local", "Vũ Vận Hành", "OPERATIONS_MANAGER");

        // Customer submits complaint requesting ISSUE_VOUCHER
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "QUANTITY_SHORTAGE",
                "Thiếu 500g cam sành",
                "Đơn giao thiếu 500g cam, vui lòng đền voucher giúp tôi.",
                evidenceImage,
                "ISSUE_VOUCHER",
                0L
        );

        // 1. Operations Manager approves the complaint (Step 1)
        complaintService.approveComplaintByOperations(opsId, complaintId, "Chấp thuận chuyển Shop đền bù.");

        // 2. Shop resolves by issuing a 60,000đ voucher (Step 2)
        complaintService.submitShopDecision(
                sellerId,
                complaintId,
                "ISSUE_VOUCHER",
                "Shop gửi tặng quý khách voucher 60k cho đơn hàng tiếp theo.",
                null,
                60000L
        );

        // Verify complaint is RESOLVED and voucher is created
        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals("ISSUE_VOUCHER", detail.get("resolution_type"));
        Long voucherId = ((Number) detail.get("voucher_id")).longValue();
        String voucherCode = (String) detail.get("voucher_code");
        assertNotNull(voucherId);
        assertNotNull(voucherCode);

        // Verify voucher is saved in customer_saved_vouchers
        Integer countSaved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_saved_vouchers WHERE user_id = ? AND voucher_id = ?",
                Integer.class, customerId, voucherId);
        assertEquals(1, countSaved);

        // Verify voucher discount amount is 60000
        java.math.BigDecimal discountAmount = jdbc.queryForObject(
                "SELECT discount_value FROM vouchers WHERE id = ?",
                java.math.BigDecimal.class, voucherId);
        assertNotNull(discountAmount);
        assertEquals(0, new java.math.BigDecimal("60000").compareTo(discountAmount));
    }

    @Test
    void testOperationsRejectWorkflowAndShopCannotReject() {
        Long customerId = createUser("buyer-reject@test.local", "Lê Người Mua", "CUSTOMER");
        Long sellerId = createUser("seller-reject@test.local", "Chủ Shop Nghiêm Túc", "SHOP_OWNER");
        Long opsId = createUser("ops-reject@test.local", "Phạm Quản Trị Viên", "OPERATIONS_MANAGER");
        Long shopId = createTestShop(sellerId, "Shop Hoa Quả Sạch");
        Long orderId = createTestOrder(customerId, shopId, "ORD-REJECT-01", "DELIVERED", LocalDateTime.now().minusHours(3));

        MockMultipartFile evidenceImage = createTestPngImage();

        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "WRONG_PRODUCT",
                "Giao sai táo gala thành táo fuji",
                "Tôi đặt táo gala nhưng shop giao nhầm loại khác.",
                evidenceImage,
                "RESEND_ITEMS",
                0L
        );

        // 1. Operations rejecting without reason or with reason < 5 chars must fail
        IllegalArgumentException shortReasonEx = assertThrows(IllegalArgumentException.class, () ->
                complaintService.rejectComplaintByOperations(
                        opsId,
                        complaintId,
                        "Sai"
                )
        );
        assertTrue(shortReasonEx.getMessage().contains("tối thiểu 5 ký tự") || shortReasonEx.getMessage().contains("từ 5 đến 1500"));

        // 2. Operations approves first complaint
        complaintService.approveComplaintByOperations(opsId, complaintId, "Duyệt yêu cầu.");

        // 3. Shop CANNOT reject once Operations approved!
        IllegalArgumentException shopRejectBlocked = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitShopDecision(
                        sellerId,
                        complaintId,
                        "REJECTED",
                        "Shop không muốn bồi thường",
                        null,
                        null
                )
        );
        assertTrue(shopRejectBlocked.getMessage().contains("không có quyền từ chối") || shopRejectBlocked.getMessage().contains("không thể từ chối"));

        // 4. Test Operations rejecting a different complaint directly
        Long orderId2 = createTestOrder(customerId, shopId, "ORD-REJECT-02", "DELIVERED", LocalDateTime.now().minusHours(1));
        Long complaintId2 = complaintService.submitCustomerComplaint(
                customerId,
                orderId2,
                null,
                "WRONG_PRODUCT",
                "Lỗi không có cơ sở",
                "Không đồng ý chất lượng",
                evidenceImage,
                "RESEND_ITEMS",
                0L
        );
        complaintService.rejectComplaintByOperations(opsId, complaintId2, "Hình ảnh chụp không chứng minh được sản phẩm bị giao nhầm loại.");

        Map<String, Object> detail2 = complaintService.getComplaintDetail(complaintId2);
        assertEquals("REJECTED", detail2.get("status"));
        assertEquals("REJECTED", detail2.get("resolution_type"));
        assertNull(detail2.get("replacement_order_id"));
        assertNull(detail2.get("voucher_id"));
        assertEquals("Hình ảnh chụp không chứng minh được sản phẩm bị giao nhầm loại.", detail2.get("resolution_note"));
    }

    @Test
    void testComplaintValidationMatrixAndConstraints() {
        Long customerId = createUser("buyer-matrix@test.local", "Phan Kiểm Tra", "CUSTOMER");
        Long sellerId = createUser("seller-matrix@test.local", "Vựa Kiểm Tra", "SHOP_OWNER");
        Long shopId = createTestShop(sellerId, "Vựa Test");

        // 1. Order status CONFIRMED cannot create complaint (only DELIVERED allowed)
        Long confirmedOrderId = createTestOrder(customerId, shopId, "ORD-CONFIRMED-01", "CONFIRMED", null);
        IllegalArgumentException blockedConfirmed = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, confirmedOrderId, null, "QUALITY_ISSUE", "Giao lâu", "Chưa thấy giao", null, 0L
                )
        );
        assertTrue(blockedConfirmed.getMessage().contains("DELIVERED"));

        // 2. Order status SHIPPING cannot create complaint
        Long shippingOrderId = createTestOrder(customerId, shopId, "ORD-SHIPPING-01", "SHIPPING", null);
        IllegalArgumentException blockedShipping = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, shippingOrderId, null, "QUALITY_ISSUE", "Shipper thái độ", "Giao hàng cộc lốc", null, 0L
                )
        );
        assertTrue(blockedShipping.getMessage().contains("DELIVERED"));

        // 3. Order status DELIVERED exceeding 24h is blocked
        Long oldDeliveredOrderId = createTestOrder(customerId, shopId, "ORD-OLD-01", "DELIVERED", LocalDateTime.now().minusHours(25));
        IllegalArgumentException blockedOver24h = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, oldDeliveredOrderId, null, "QUALITY_ISSUE", "Trái cây thối", "Dập mốc", null, 0L
                )
        );
        assertTrue(blockedOver24h.getMessage().contains("quá thời hạn 24 giờ"));

        // 4. Within 24h: product defect WITHOUT evidence image is blocked
        Long deliveredOrderId = createTestOrder(customerId, shopId, "ORD-VALID-01", "DELIVERED", LocalDateTime.now().minusHours(2));
        IllegalArgumentException blockedNoImage = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Trái cây thối", "Dập mốc nhiều", null, 0L
                )
        );
        assertTrue(blockedNoImage.getMessage().contains("bắt buộc phải đính kèm ít nhất 1 hình ảnh"));

        // 5. Valid complaint with photo
        MockMultipartFile img = createTestPngImage();
        Long cId = complaintService.submitCustomerComplaint(
                customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Dập nát 20%", "Hộp bị nén", img, "RESEND_ITEMS", 0L
        );
        assertNotNull(cId);

        // 6. Max 1 active complaint per order constraint
        IllegalArgumentException blockedDuplicate = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, deliveredOrderId, null, "WRONG_PRODUCT", "Thêm lỗi khác", "Mô tả", img, "RESEND_ITEMS", 0L
                )
        );
        assertTrue(blockedDuplicate.getMessage().contains("chỉ được phép gửi khiếu nại tối đa 1 lần"));

        // 7. Customer cancels active complaint -> status becomes CANCELLED
        complaintService.cancelCustomerComplaint(customerId, cId);
        Map<String, Object> cancelledDetail = complaintService.getComplaintDetail(cId);
        assertEquals("CANCELLED", cancelledDetail.get("status"));

        // 8. Recreating is allowed once previous complaint was CANCELLED and order is still within 24h
        Long cId2 = complaintService.submitCustomerComplaint(
                customerId, deliveredOrderId, null, "QUALITY_ISSUE", "Dập nát tạo lại", "Hộp bị nén", img, "RESEND_ITEMS", 0L
        );
        assertNotNull(cId2);
    }

    @Test
    void testOrderLocksDuringComplaint() {
        Long customerId = createUser("buyer-lock@test.local", "Võ Khóa Đơn", "CUSTOMER");
        Long sellerId = createUser("seller-lock@test.local", "Shop Khóa Đơn", "SHOP_OWNER");
        Long shopId = createTestShop(sellerId, "Shop Lock");

        Long orderId = createTestOrder(customerId, shopId, "ORD-LOCK-01", "DELIVERED", LocalDateTime.now().minusHours(2));
        MockMultipartFile img = createTestPngImage();

        Long complaintId = complaintService.submitCustomerComplaint(
                customerId, orderId, null, "QUALITY_ISSUE", "Bị dập", "Bị dập", img, "RESEND_ITEMS", 0L
        );
        assertNotNull(complaintId);

        // Customer tries to manually complete order -> BLOCKED due to active complaint
        IllegalArgumentException blockedCustomerComplete = assertThrows(IllegalArgumentException.class, () ->
                orderCheckoutService.completeOrderByCustomer(customerId, orderId)
        );
        assertTrue(blockedCustomerComplete.getMessage().contains("đang có khiếu nại"));

        // Customer withdraws complaint -> complaint is CANCELLED
        complaintService.cancelCustomerComplaint(customerId, complaintId);

        // Customer can now manually complete order
        orderCheckoutService.completeOrderByCustomer(customerId, orderId);
        String currentOrderStatus = jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertEquals("COMPLETED", currentOrderStatus);
    }
}
