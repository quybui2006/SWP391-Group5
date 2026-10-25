package org.example.swp391group5;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import service.complaint.ComplaintService;
import service.operations.OperationsDashboardService;

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
    private JdbcTemplate jdbc;

    private Long createUser(String email, String name, String roleCode) {
        jdbc.update("INSERT INTO users(full_name, email, password_hash, points_balance) VALUES (?, ?, 'hash', 100)", name, email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        jdbc.update("MERGE INTO roles(code, name) KEY(code) VALUES(?, ?)", roleCode, roleCode);
        Long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbc.update("INSERT INTO user_roles(user_id, role_id) VALUES(?, ?)", userId, roleId);
        return userId;
    }

    @Test
    void testEndToEndComplaintWorkflowAndOperationsReports() {
        Long customerId = createUser("buyer-complaint@test.local", "Nguyễn Văn Mua", "CUSTOMER");
        Long sellerId = createUser("seller-complaint@test.local", "Trần Chủ Vựa", "SHOP_OWNER");
        Long csId = createUser("cs-complaint@test.local", "Lê CSKH", "CS_STAFF");
        createUser("ops-complaint@test.local", "Phạm Vận Hành", "OPERATIONS_MANAGER");

        // Create shop matching test-schema.sql
        jdbc.update("""
            INSERT INTO shops(owner_id, name, phone, province_code, ward_name, address_detail, approval_status, operating_status)
            VALUES (?, 'Nông Sản Tươi Đà Lạt', '0911222333', '68', 'Phường 1', 'Đà Lạt, Lâm Đồng', 'APPROVED', 'OPEN')
            """, sellerId);
        Long shopId = jdbc.queryForObject("SELECT id FROM shops WHERE owner_id = ?", Long.class, sellerId);

        // Create checkout & order in COMPLETED status
        jdbc.update("""
            INSERT INTO checkouts(user_id, checkout_code, request_key, contact_email, payment_method, total_amount, status)
            VALUES (?, 'CHK-TEST-CMP-01', 'REQ-TEST-CMP-01', 'buyer-complaint@test.local', 'COD', 680000, 'CONFIRMED')
            """, customerId);
        Long checkoutId = jdbc.queryForObject("SELECT id FROM checkouts WHERE checkout_code = 'CHK-TEST-CMP-01'", Long.class);

        jdbc.update("""
            INSERT INTO orders(checkout_id, shop_id, order_code, recipient_name, recipient_phone,
                               delivery_province_code, delivery_province_name, delivery_ward_name, delivery_address,
                               shop_province_code, subtotal, points_used, points_discount, points_subsidy_amount,
                               qualifying_amount, points_earned, shipping_fee, total_amount, shop_revenue_amount,
                               payment_method, delivery_code, status, payment_status, delivery_status)
            VALUES (?, ?, 'ORD-TEST-CMP-01', 'Nguyễn Văn Mua', '0909123456',
                    '79', 'TP. Hồ Chí Minh', 'Phường Bến Nghé', '123 Lê Lợi, Q1',
                    '68', 650000, 0, 0, 0,
                    650000, 65, 30000, 680000, 650000,
                    'COD', 'DEL-TEST-CMP-01', 'COMPLETED', 'PAID', 'DELIVERED')
            """, checkoutId, shopId);
        Long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_code = 'ORD-TEST-CMP-01'", Long.class);

        // 1. Customer submits complaint (QUALITY_ISSUE -> auto HIGH priority, 12h SLA)
        Long complaintId = complaintService.submitCustomerComplaint(
                customerId,
                orderId,
                null,
                "QUALITY_ISSUE",
                "Dâu tây bị dập nát khoảng 30% hộp",
                "Khi mở hộp ra có nhiều quả bị chảy nước và mốc nhẹ ở đáy hộp, mong shop và sàn hỗ trợ.",
                null
        );
        assertNotNull(complaintId);

        // 2. Verify Customer complaint list & auto priority
        List<Map<String, Object>> customerComplaints = complaintService.listCustomerComplaints(customerId, "ALL");
        assertEquals(1, customerComplaints.size());
        Map<String, Object> detail = complaintService.getComplaintDetail(complaintId);
        assertNotNull(detail);
        assertEquals("HIGH", detail.get("priority"));
        assertEquals("PENDING", detail.get("status"));

        // 3. Shop Owner submits 3-party explanation
        complaintService.submitShopResponse(
                sellerId,
                complaintId,
                "Shop xin lỗi quý khách do khâu vận chuyển đường xa bị xóc. Shop đồng ý đền bù hoặc gửi bù ạ."
        );
        detail = complaintService.getComplaintDetail(complaintId);
        assertNotNull(detail.get("shop_response"));

        // 4. CS Staff starts review & resolves with COMPENSATE_POINTS + shop fault + escalate to Operations
        complaintService.startProcessing(complaintId, csId);
        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("IN_PROGRESS", detail.get("status"));

        complaintService.resolveComplaint(
                complaintId,
                csId,
                "RESOLVED",
                "COMPENSATE_POINTS",
                "Xác nhận hình ảnh hợp lệ, đền bù 500 điểm thưởng cho khách và ghi nhận lỗi đóng gói của Shop.",
                500L,
                true,
                true
        );

        detail = complaintService.getComplaintDetail(complaintId);
        assertEquals("RESOLVED", detail.get("status"));
        assertEquals("COMPENSATE_POINTS", detail.get("resolution_type"));
        assertEquals(500L, ((Number) detail.get("compensated_points")).longValue());

        // Verify customer points_balance increased from 100 to 600
        Long updatedPoints = jdbc.queryForObject("SELECT points_balance FROM users WHERE id = ?", Long.class, customerId);
        assertEquals(600L, updatedPoints);

        // 5. Verify Operations Dashboard & CS Analytics Reports
        Map<String, Object> dashboardData = operationsDashboardService.getDashboardData();
        assertNotNull(dashboardData.get("disputeReport"));
        assertNotNull(dashboardData.get("csSlaReport"));
        assertNotNull(dashboardData.get("moderationMetrics"));
        assertNotNull(dashboardData.get("violationAlerts"));

        @SuppressWarnings("unchecked")
        Map<String, Object> csSlaReport = (Map<String, Object>) dashboardData.get("csSlaReport");
        assertTrue(((Number) csSlaReport.get("totalTickets")).intValue() >= 1);
        assertTrue(((Number) csSlaReport.get("processedTickets")).intValue() >= 1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> violationAlerts = (List<Map<String, Object>>) dashboardData.get("violationAlerts");
        boolean shopAlertFound = violationAlerts.stream()
                .anyMatch(a -> shopId.equals(((Number) a.get("shop_id")).longValue()));
        assertTrue(shopAlertFound, "Shop with fault complaint should appear in Violation Alerts");
    }
}
