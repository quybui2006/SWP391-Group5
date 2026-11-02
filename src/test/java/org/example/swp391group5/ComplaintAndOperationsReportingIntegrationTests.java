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
import service.delivery.DeliveryOrderService;
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
    private DeliveryOrderService deliveryOrderService;

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

    private Long[] createTestVariantAndInventory(Long shopId, Long sellerId, String sku, int onHand, int reserved) {
        jdbc.update("MERGE INTO units(code, name, is_active) KEY(code) VALUES('HOP', 'Hộp', TRUE)");
        Long unitId = jdbc.queryForObject("SELECT id FROM units WHERE code = 'HOP'", Long.class);
        String batchCode = "BATCH-" + sku;
        jdbc.update("""
            INSERT INTO products(shop_id, name, batch_code, is_visible, approval_status, selling_status)
            VALUES (?, ?, ?, TRUE, 'APPROVED', 'ACTIVE')
        """, shopId, "Sản phẩm " + sku, batchCode);
        Long productId = jdbc.queryForObject("SELECT id FROM products WHERE shop_id = ? AND batch_code = ?", Long.class, shopId, batchCode);

        jdbc.update("""
            INSERT INTO product_variants(product_id, shop_id, base_unit_id, sku, name, price, status, approval_status)
            VALUES (?, ?, ?, ?, 'Hộp 500g', 250000, 'ACTIVE', 'APPROVED')
        """, productId, shopId, unitId, sku);
        Long variantId = jdbc.queryForObject("SELECT id FROM product_variants WHERE shop_id = ? AND sku = ?", Long.class, shopId, sku);

        jdbc.update("""
            INSERT INTO variant_inventory(variant_id, initial_quantity, quantity_on_hand, reserved_quantity, status, created_by)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?)
        """, variantId, Math.max(onHand, 10), onHand, reserved, sellerId);
        Long inventoryId = jdbc.queryForObject("SELECT id FROM variant_inventory WHERE variant_id = ?", Long.class, variantId);
        return new Long[]{variantId, inventoryId};
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

        // 7. Verify replacement order properties
        Map<String, Object> replacementOrder = jdbc.queryForMap("SELECT * FROM orders WHERE id = ?", replacementOrderId);
        assertEquals("RESEND", replacementOrder.get("order_type"));
        assertEquals(orderId, ((Number) replacementOrder.get("original_order_id")).longValue());
        assertEquals("CONFIRMED", replacementOrder.get("status"));
        assertEquals("PAID", replacementOrder.get("payment_status"));
        assertEquals("RESEND", replacementOrder.get("payment_method"));
        assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) replacementOrder.get("total_amount")));
        assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) replacementOrder.get("shipping_fee")));
        assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) replacementOrder.get("shop_revenue_amount")));

        // Verify replacement order items were copied with line_total = 0
        List<Map<String, Object>> repItems = jdbc.queryForList("SELECT * FROM order_items WHERE order_id = ?", replacementOrderId);
        assertFalse(repItems.isEmpty());
        assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) repItems.get(0).get("line_total")));

        // 8. Verify original order revenue is NOT deducted (no financial deduction on complaint)
        BigDecimal origRevenue = jdbc.queryForObject("SELECT shop_revenue_amount FROM orders WHERE id = ?", BigDecimal.class, orderId);
        assertNotNull(origRevenue);
        assertEquals(0, new BigDecimal("500000").compareTo(origRevenue));

        // 9. Verify shop reputation score was NOT deducted
        Integer repScore = jdbc.queryForObject("SELECT reputation_score FROM shops WHERE id = ?", Integer.class, shopId);
        assertNotNull(repScore);
        assertEquals(100, repScore.intValue());
    }

    @Test
    void testShopSoleDecision_IssueVoucherWorkflowAndCustomerCheckout() {
        Long customerId = createUser("buyer-voucher@test.local", "Trần Khách Voucher", "CUSTOMER");
        Long otherCustomerId = createUser("other-buyer-voucher@test.local", "Khách Khác", "CUSTOMER");
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

        // 2. Test voucher validation (HIGH-07): percentage > 100 must fail
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitShopDecisionIssueVoucherCustom(
                        sellerId, complaintId, "VC-INVALID-PCT", "Voucher lỗi",
                        "PERCENTAGE", new BigDecimal("150"), null, BigDecimal.ZERO,
                        LocalDateTime.now(), LocalDateTime.now().plusDays(30), "Ghi chú"
                )
        );

        // 3. Shop resolves by issuing a 60,000đ custom compensation voucher (Step 2)
        String customCode = "VC-COMP-60K";
        Long voucherId = complaintService.submitShopDecisionIssueVoucherCustom(
                sellerId, complaintId, customCode, "Voucher đền bù 60k",
                "FIXED_AMOUNT", new BigDecimal("60000"), null, BigDecimal.ZERO,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusDays(30),
                "Shop gửi tặng quý khách voucher 60k cho đơn hàng tiếp theo."
        );
        assertNotNull(voucherId);

        // Verify complaint is RESOLVED and voucher is linked
        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals("ISSUE_VOUCHER", detail.get("resolution_type"));
        assertEquals(customCode, detail.get("voucher_code"));

        // Verify voucher is saved in customer_saved_vouchers and is private (is_public = false)
        Integer countSaved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_saved_vouchers WHERE user_id = ? AND voucher_id = ?",
                Integer.class, customerId, voucherId);
        assertEquals(1, countSaved);

        // 4. CRIT-01: Customer uses the private compensation voucher at Checkout -> MUST SUCCEED
        OrderCheckoutService.VoucherQuote previewQuote = orderCheckoutService.previewVoucher(
                customerId, shopId, customCode, new BigDecimal("200000"));
        assertNotNull(previewQuote);
        assertNotNull(previewQuote.voucherId());
        assertNull(previewQuote.error());
        assertEquals(customCode, previewQuote.code());
        assertEquals(0, new BigDecimal("60000").compareTo(previewQuote.discount()));

        // 5. Another customer who was NOT issued this private voucher CANNOT apply it
        OrderCheckoutService.VoucherQuote otherQuote = orderCheckoutService.previewVoucher(
                otherCustomerId, shopId, customCode, new BigDecimal("200000"));
        assertNull(otherQuote.voucherId(), "Another customer must not be able to use private compensation voucher");
        assertNotNull(otherQuote.error());
        assertEquals(0, BigDecimal.ZERO.compareTo(otherQuote.discount()));
    }

    @Test
    void testResendCustomInventoryDeductionAndCancelRestoresStockAccurately() {
        Long customerId = createUser("buyer-inv@test.local", "Lê Khách Kho", "CUSTOMER");
        Long sellerId = createUser("seller-inv@test.local", "Chủ Shop Kho", "SHOP_OWNER");
        Long opsId = createUser("ops-inv@test.local", "Quản Trị Kho", "OPERATIONS_MANAGER");
        Long shopId = createTestShop(sellerId, "Shop Kho Chuẩn");

        // Create a variant with onHand = 5, reserved = 1 => available = 4
        Long[] varAndInv = createTestVariantAndInventory(shopId, sellerId, "TAO-ENVY-1KG", 5, 1);
        Long variantId = varAndInv[0];
        Long inventoryId = varAndInv[1];

        Long orderId = createTestOrder(customerId, shopId, "ORD-INV-01", "DELIVERED", LocalDateTime.now().minusHours(1));
        MockMultipartFile evidenceImage = createTestPngImage();

        Long complaintId = complaintService.submitCustomerComplaint(
                customerId, orderId, null, "QUALITY_ISSUE", "Táo dập", "Hộp táo bị dập", evidenceImage, "RESEND_ITEMS", 0L
        );
        complaintService.approveComplaintByOperations(opsId, complaintId, "Duyệt giao bù.");

        // 1. CRIT-02: Requesting quantity = 5 (> available 4) MUST FAIL
        IllegalArgumentException outOfStockEx = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitShopDecisionResendCustom(
                        sellerId, complaintId, List.of(variantId), List.of(5), "Giao bù 5 hộp"
                )
        );
        assertTrue(outOfStockEx.getMessage().contains("Tồn kho khả dụng không đủ"));

        // 2. Requesting quantity = 3 (<= available 4) MUST SUCCEED and deduct quantity_on_hand from 5 -> 2
        Long replacementOrderId = complaintService.submitShopDecisionResendCustom(
                sellerId, complaintId, List.of(variantId), List.of(3), "Giao bù 3 hộp"
        );
        assertNotNull(replacementOrderId);

        Integer onHandAfterResend = jdbc.queryForObject(
                "SELECT quantity_on_hand FROM variant_inventory WHERE id = ?", Integer.class, inventoryId);
        assertEquals(2, onHandAfterResend);

        // Verify SALE inventory transaction was recorded
        Integer txCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_transactions WHERE inventory_id = ? AND type = 'SALE' AND on_hand_delta = -3",
                Integer.class, inventoryId);
        assertEquals(1, txCount);

        // 3. CRIT-02 & CRIT-03: If the RESEND order fails delivery (CANCELLED), stock is restored back to 5 (no phantom stock inflation)
        deliveryOrderService.updateDeliveryStatus(replacementOrderId, "CANCELLED", "Không liên lạc được người nhận");

        Map<String, Object> repOrderAfterCancel = jdbc.queryForMap("SELECT * FROM orders WHERE id = ?", replacementOrderId);
        assertEquals("CANCELLED", repOrderAfterCancel.get("status"));
        assertEquals("FAILED", repOrderAfterCancel.get("delivery_status"));

        Integer onHandAfterCancel = jdbc.queryForObject(
                "SELECT quantity_on_hand FROM variant_inventory WHERE id = ?", Integer.class, inventoryId);
        assertEquals(5, onHandAfterCancel, "Stock must be restored back to initial 5, not inflated above 5");
    }

    @Test
    void testRefuseDeliveryAndDeliveryIssueComplaintFlow() {
        Long customerId = createUser("buyer-refuse@test.local", "Ngô Từ Chối Nhận", "CUSTOMER");
        Long sellerId = createUser("seller-refuse@test.local", "Shop Giao Hàng", "SHOP_OWNER");
        Long opsId = createUser("ops-refuse@test.local", "Vận Hành Giao Hàng", "OPERATIONS_MANAGER");
        Long shopId = createTestShop(sellerId, "Shop Giao Nhanh");

        Long[] varAndInv = createTestVariantAndInventory(shopId, sellerId, "NHO-XANH-1KG", 10, 0);
        Long variantId = varAndInv[0];
        Long inventoryId = varAndInv[1];

        // Create a VNPAY PAID order currently in SHIPPING state (stock was CONSUMED: onHand deducted by 2 -> 8)
        jdbc.update("UPDATE variant_inventory SET quantity_on_hand = 8 WHERE id = ?", inventoryId);
        Long orderId = createTestOrder(customerId, shopId, "ORD-SHIP-REFUSE-01", "SHIPPING", null);
        jdbc.update("UPDATE orders SET payment_method = 'VNPAY', payment_status = 'PAID', delivery_status = 'IN_TRANSIT' WHERE id = ?", orderId);
        jdbc.update("UPDATE order_items SET variant_id = ?, inventory_id = ?, quantity = 2, allocation_status = 'CONSUMED' WHERE order_id = ?",
                variantId, inventoryId, orderId);

        // 1. CRIT-03: Customer refuses delivery on SHIPPING order -> order becomes CANCELLED + FAILED, stock restored 8 -> 10
        orderCheckoutService.refuseDeliveryByCustomer(customerId, orderId, "Shipper giao hàng trễ hẹn và thùng hàng bị ướt");

        Map<String, Object> refusedOrder = jdbc.queryForMap("SELECT * FROM orders WHERE id = ?", orderId);
        assertEquals("CANCELLED", refusedOrder.get("status"));
        assertEquals("FAILED", refusedOrder.get("delivery_status"));

        Integer restoredOnHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id = ?", Integer.class, inventoryId);
        assertEquals(10, restoredOnHand);

        // 2. CRIT-03: Eligibility check allows creating complaint on refused/failed delivery order
        Map<String, Object> eligibility = complaintService.getOrderComplaintEligibility(customerId, orderId);
        assertEquals(Boolean.TRUE, eligibility.get("canCreateComplaint"));

        // 3. Product defect reason on CANCELLED/FAILED order is still blocked (must use DELIVERY_ISSUE / DELIVERY_OR_SERVICE / OTHER)
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, orderId, null, "QUALITY_ISSUE", "Hàng hỏng", "Mô tả lỗi", createTestPngImage(), "ISSUE_VOUCHER", 0L
                )
        );

        // 4. Customer submits DELIVERY_ISSUE complaint on refused order (no photo required) -> succeeds!
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId, orderId, null, "DELIVERY_ISSUE",
                "Giao hàng trễ làm lỡ việc, phải từ chối nhận",
                "Đơn giao trễ nhiều giờ và thùng bị móp méo khi đồng kiểm nên tôi từ chối nhận.",
                null, "ISSUE_VOUCHER", 0L
        );
        assertNotNull(complaintId);

        // 5. Operations approves -> Shop issues compensation voucher
        complaintService.approveComplaintByOperations(opsId, complaintId, "Đơn giao hàng thất bại do lỗi vận chuyển, duyệt hỗ trợ voucher.");
        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("PENDING_SHOP", detail.get("status"));
    }

    @Test
    void testSlaStartsAtPendingShopAndCsNoDeadlockAndAuthorization() {
        Long customerId = createUser("buyer-sla@test.local", "Đặng Kiểm Tra SLA", "CUSTOMER");
        Long sellerId = createUser("seller-sla@test.local", "Chủ Shop SLA", "SHOP_OWNER");
        Long otherSellerId = createUser("other-seller@test.local", "Chủ Shop Khác", "SHOP_OWNER");
        Long opsId = createUser("ops-sla@test.local", "Quản Trị SLA", "OPERATIONS_MANAGER");
        Long csId = createUser("cs-sla@test.local", "Nhân Viên CSKH", "CS_STAFF");

        Long shopId = createTestShop(sellerId, "Shop SLA");
        createTestShop(otherSellerId, "Shop Khác");
        createTestOrder(customerId, shopId, "ORD-SLA-01", "DELIVERED", LocalDateTime.now().minusHours(2));

        // 1. CRIT-04: CS creates ticket on behalf of customer -> status MUST be PENDING_OPERATIONS (not deadlocked ESCALATED)
        Long complaintId = complaintService.createTicketByCs(
                csId, "ORD-SLA-01", "DELIVERY_ISSUE", "Khách báo lỗi giao hàng", "Khách gọi hotline báo shipper giao thiếu túi đá giữ lạnh", "HOTLINE"
        );
        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("PENDING_OPERATIONS", detail.get("status"));
        assertEquals("WAITING_OPERATIONS", detail.get("sla_state"));
        assertEquals("Chờ Vận hành duyệt", detail.get("sla_badge_text"));

        // Simulate backdated created_at / sla_due_at while still PENDING_OPERATIONS -> must NOT count as Shop SLA overdue
        jdbc.update("UPDATE complaints SET sla_due_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(10)), complaintId);
        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("WAITING_OPERATIONS", detail.get("sla_state"));
        assertNotEquals("OVERDUE", detail.get("sla_state"));

        // 2. CRIT-04: CS calling startProcessing only assigns assigned_to and does NOT mutate status to ESCALATED
        complaintService.startProcessing(complaintId, csId);
        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("PENDING_OPERATIONS", detail.get("status"));
        assertEquals(csId, ((Number) detail.get("assigned_to")).longValue());

        // 3. HIGH-01: Operations approves -> transitions to PENDING_SHOP and resets 6-hour SLA timer
        complaintService.approveComplaintByOperations(opsId, complaintId, "Chuyển Shop xử lý.");
        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("PENDING_SHOP", detail.get("status"));
        assertEquals("ON_TIME", detail.get("sla_state"));

        // 4. HIGH-02: Another shop owner (IDOR attempt) CANNOT resolve this complaint
        assertThrows(ResponseStatusException.class, () ->
                complaintService.submitShopDecision(otherSellerId, complaintId, "RESEND_ITEMS", "Hack", null, null)
        );
        assertThrows(ResponseStatusException.class, () ->
                complaintService.submitShopDecisionIssueVoucherCustom(
                        otherSellerId, complaintId, "VC-HACK", "Hack", "FIXED_AMOUNT",
                        new BigDecimal("50000"), null, BigDecimal.ZERO,
                        LocalDateTime.now(), LocalDateTime.now().plusDays(10), "Hack"
                )
        );

        // 5. Real shop owner resolves -> RESOLVED, and duplicate resolution attempt is blocked
        complaintService.submitShopDecision(sellerId, complaintId, "ISSUE_VOUCHER", "Tặng voucher", null, 30000L);
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitShopDecision(sellerId, complaintId, "RESEND_ITEMS", "Làm lần 2", null, null)
        );
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

        // 1. Order status CONFIRMED cannot create complaint
        Long confirmedOrderId = createTestOrder(customerId, shopId, "ORD-CONFIRMED-01", "CONFIRMED", null);
        IllegalArgumentException blockedConfirmed = assertThrows(IllegalArgumentException.class, () ->
                complaintService.submitCustomerComplaint(
                        customerId, confirmedOrderId, null, "QUALITY_ISSUE", "Giao lâu", "Chưa thấy giao", null, 0L
                )
        );
        assertTrue(blockedConfirmed.getMessage().contains("DELIVERED"));

        // 2. Order status SHIPPING cannot create product defect complaint (QUALITY_ISSUE requires DELIVERED)
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

    @Test
    void testOperationsApproveComplaintRefundAndPreventDoubleRefund() {
        Long customerId = createUser("buyer-refund@test.local", "Hoàng Nhận Hoàn Tiền", "CUSTOMER");
        Long sellerId = createUser("seller-refund@test.local", "Shop Hoàn Tiền", "SHOP_OWNER");
        Long opsId = createUser("ops-refund@test.local", "Quản Trị Hoàn Tiền", "OPERATIONS_MANAGER");
        Long shopId = createTestShop(sellerId, "Shop Trái Cây Hoàn Tiền");

        // Link customer bank account
        jdbc.update("""
            INSERT INTO customer_bank_accounts(user_id, bank_name, bank_account_no, bank_account_holder)
            VALUES (?, 'Vietcombank', '0123456789', 'HOANG NHAN HOAN TIEN')
        """, customerId);

        Long orderId = createTestOrder(customerId, shopId, "ORD-REFUND-01", "DELIVERED", LocalDateTime.now().minusHours(2));
        MockMultipartFile img = createTestPngImage();

        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "QUALITY_ISSUE",
                "Trái cây hư hỏng nặng cần hoàn tiền",
                "Toàn bộ 2 hộp dâu tây bị mốc không thể sử dụng.",
                img,
                "RETURN_REFUND",
                350000L
        );
        assertNotNull(complaintId);

        // 1. Validate refund amount exceeding actual paid amount (500,000đ) is rejected
        assertThrows(IllegalArgumentException.class, () ->
                complaintService.approveComplaintRefundByOperations(opsId, complaintId, 600000L, "Hoàn quá mức")
        );

        // 2. Operations approves refund of 350,000đ
        complaintService.approveComplaintRefundByOperations(
                opsId,
                complaintId,
                350000L,
                "Xác nhận hàng hỏng 70%, duyệt hoàn tiền 350.000đ về tài khoản khách."
        );

        // Verify complaint state
        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals("RETURN_REFUND", detail.get("resolution_type"));
        assertEquals(350000L, ((Number) detail.get("final_refund_amount")).longValue());
        assertEquals("COMPLETED", detail.get("refund_status"));

        // Verify customer_refunds ledger record was created with bank info
        Map<String, Object> refundRecord = jdbc.queryForMap(
                "SELECT * FROM customer_refunds WHERE order_id = ? AND refund_type = 'RETURN_REFUND'", orderId);
        assertEquals(customerId, ((Number) refundRecord.get("customer_id")).longValue());
        assertEquals(0, new BigDecimal("350000").compareTo((BigDecimal) refundRecord.get("refund_amount")));
        assertEquals("Vietcombank", refundRecord.get("bank_name"));
        assertEquals("0123456789", refundRecord.get("bank_account_no"));
        assertEquals("HOANG NHAN HOAN TIEN", refundRecord.get("bank_account_holder"));
        assertEquals("PENDING", refundRecord.get("status"));

        // 3. Double-refund protection: clicking approve refund a second time MUST throw IllegalArgumentException and NOT create a second record
        IllegalArgumentException doubleRefundEx = assertThrows(IllegalArgumentException.class, () ->
                complaintService.approveComplaintRefundByOperations(opsId, complaintId, 350000L, "Nhấn duyệt lần 2")
        );
        assertTrue(doubleRefundEx.getMessage().contains("đã được giải quyết hoặc đã hoàn tiền"));

        Long refundCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_refunds WHERE order_id = ? AND refund_type = 'RETURN_REFUND'",
                Long.class, orderId);
        assertEquals(1L, refundCount);
    }
}
