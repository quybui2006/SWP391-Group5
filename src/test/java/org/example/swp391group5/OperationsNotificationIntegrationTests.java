package org.example.swp391group5;

import dto.customer.NotificationDto;
import entity.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import service.customer.NotificationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OperationsNotificationIntegrationTests {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long createTestUserWithRole(String email, String fullName, String roleCode) {
        jdbc.update("INSERT INTO users(full_name, email, password_hash) VALUES (?, ?, 'hashed_pass')", fullName, email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);

        jdbc.update("MERGE INTO roles(code, name) KEY(code) VALUES(?, ?)", roleCode, roleCode);
        Long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code=?", Long.class, roleCode);

        jdbc.update("INSERT INTO user_roles(user_id, role_id) VALUES(?, ?)", userId, roleId);
        return userId;
    }

    @Test
    void testOperationsNotificationsIncludeShopApprovalAndPostAuditOnly() {
        Long opUserId = createTestUserWithRole("op-test-notif@freshfruit.local", "Test Operations Manager", "OPERATIONS_MANAGER");

        notificationService.ensureSampleNotificationsIfEmpty(opUserId);

        List<NotificationDto> notifs = notificationService.getAllNotifications(opUserId);
        assertFalse(notifs.isEmpty(), "Operations manager should receive notifications");

        for (NotificationDto n : notifs) {
            // Must be SHOP_APPROVAL or POST_AUDIT
            assertTrue("SHOP_APPROVAL".equalsIgnoreCase(n.getType()) || "POST_AUDIT".equalsIgnoreCase(n.getType()),
                    "Operations notification type should be SHOP_APPROVAL or POST_AUDIT but was: " + n.getType());
            // Must NOT be personal promotional voucher or customer order
            assertFalse("PROMOTION".equalsIgnoreCase(n.getType()), "Operations manager should not have PROMOTION notifications");
            assertFalse("ORDER".equalsIgnoreCase(n.getType()), "Operations manager should not have ORDER notifications");

            if ("POST_AUDIT".equalsIgnoreCase(n.getType())) {
                assertEquals("/operations/post-audit", n.getTargetUrl());
            } else if ("SHOP_APPROVAL".equalsIgnoreCase(n.getType())) {
                assertTrue(n.getTargetUrl().startsWith("/operations/shops"));
            }
        }
    }

    @Test
    void testNotifyOperationsWhenNewShopSubmitted() {
        Long opUserId = createTestUserWithRole("op-shop-alert@freshfruit.local", "Ops Lead", "OPERATIONS_MANAGER");

        notificationService.notifyOperationsNewShopRegistration(999L, "Shop Hoa Quả Sạch Tây Bắc", "Hoàng Văn Tuấn");

        List<NotificationDto> notifs = notificationService.getAllNotifications(opUserId);
        boolean foundNewShopNotif = notifs.stream().anyMatch(n ->
                n.getTitle().contains("Shop Hoa Quả Sạch Tây Bắc") &&
                "SHOP_APPROVAL".equalsIgnoreCase(n.getType()) &&
                "/operations/shops/999".equals(n.getTargetUrl())
        );
        assertTrue(foundNewShopNotif, "Operations user should receive notification about new shop registration");
    }

    @Test
    void testNotifyOperationsPostAuditExplanation() {
        Long opUserId = createTestUserWithRole("op-audit-alert@freshfruit.local", "Ops Auditor", "OPERATIONS_MANAGER");

        notificationService.notifyOperationsPostAuditExplanation(888L, "Vựa Dưa Hấu Long An", "Cửa hàng đã nộp giấy kiểm dịch an toàn thực phẩm đợt hậu kiểm.");

        List<NotificationDto> notifs = notificationService.getAllNotifications(opUserId);
        boolean foundAuditNotif = notifs.stream().anyMatch(n ->
                n.getTitle().contains("Vựa Dưa Hấu Long An") &&
                "POST_AUDIT".equalsIgnoreCase(n.getType()) &&
                "/operations/post-audit".equals(n.getTargetUrl())
        );
        assertTrue(foundAuditNotif, "Operations user should receive notification about post-audit explanation");
    }
}
