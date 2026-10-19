package org.example.swp391group5;

import dto.customer.ShopPaymentSetupDto;
import dto.customer.ShopRegistrationDto;
import dto.operations.ShopApprovalDetailDto;
import entity.Shop;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import service.customer.CustomerShopRegistrationService;
import service.operations.OperationsShopApprovalService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ShopPaymentApprovalIntegrationTests {

    @Autowired
    private CustomerShopRegistrationService customerShopService;

    @Autowired
    private OperationsShopApprovalService operationsShopService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long createTestUser(String email, String fullName) {
        jdbc.update("INSERT INTO users(full_name, email, password_hash) VALUES (?, ?, 'hashed_pass')", fullName, email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private void ensureProvince(String code, String name) {
        jdbc.update("INSERT INTO provinces(code, name, is_active) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE name=VALUES(name)", code, name);
    }

    @Test
    void testCompletePaymentVerificationFlow() {
        String code = "P-PAY-TEST";
        ensureProvince(code, "Hà Nội");

        Long ownerId = createTestUser("seller-pay-test@example.com", "Nguyễn Văn An");

        // 1. Customer registers shop with CCCD
        ShopRegistrationDto regDto = new ShopRegistrationDto();
        regDto.setName("Cửa Hàng Hoa Quả An Tâm");
        regDto.setDescription("Trái cây tươi chuẩn GlobalGAP");
        regDto.setPhone("0988776655");
        regDto.setProvinceCode(code);
        regDto.setWardName("Dịch Vọng Hậu");
        regDto.setAddressDetail("Số 10 Phạm Hùng");
        regDto.setIdCardNumber("001203004567");
        regDto.setIdCardName("Nguyễn Văn An");
        regDto.setIdCardDate("2021-05-15");
        regDto.setIdCardPlace("Cục CS QLHC về TTXH");
        regDto.setAgreeTerms(true);

        Shop registeredShop = customerShopService.registerShop(ownerId, regDto);
        assertNotNull(registeredShop.getId());
        assertEquals("PENDING", registeredShop.getApprovalStatus());
        assertEquals("CLOSED", registeredShop.getOperatingStatus());

        // 2. Operator reviews and approves shop registration (Step 1)
        operationsShopService.approveShop(registeredShop.getId());

        ShopApprovalDetailDto detailAfterStep1 = operationsShopService.getShopDetail(registeredShop.getId());
        assertEquals("APPROVED", detailAfterStep1.getApprovalStatus());
        assertEquals("CLOSED", detailAfterStep1.getOperatingStatus());
        assertEquals("UNCONFIGURED", detailAfterStep1.getPaymentApprovalStatus());

        // 3. Customer submits payment info matching CCCD (Step 2)
        ShopPaymentSetupDto payDto = new ShopPaymentSetupDto();
        payDto.setBankName("Vietcombank (VCB)");
        payDto.setBankAccountNumber("01234567899");
        payDto.setBankAccountName("NGUYEN VAN AN");
        payDto.setBankBranch("Chi nhánh Thăng Long, Hà Nội");

        customerShopService.submitPaymentInfo(ownerId, payDto);

        // 4. Operator inspects bank info and CCCD cross-check
        ShopApprovalDetailDto detailForReview = operationsShopService.getShopDetail(registeredShop.getId());
        assertEquals("PENDING", detailForReview.getPaymentApprovalStatus());
        assertEquals("Vietcombank (VCB)", detailForReview.getBankName());
        assertEquals("01234567899", detailForReview.getBankAccountNumber());
        assertEquals("NGUYEN VAN AN", detailForReview.getBankAccountName());
        assertTrue(detailForReview.isBankAccountMatchesIdCard(), "Họ tên tài khoản NGUYEN VAN AN phải khớp với CCCD Nguyễn Văn An");

        // 5. Operator approves payment -> Shop becomes OPEN!
        operationsShopService.approvePayment(registeredShop.getId());

        ShopApprovalDetailDto finalDetail = operationsShopService.getShopDetail(registeredShop.getId());
        assertEquals("APPROVED", finalDetail.getPaymentApprovalStatus());
        assertEquals("OPEN", finalDetail.getOperatingStatus());
        assertNotNull(finalDetail.getPaymentReviewedAt());
    }

    @Test
    void testPaymentRejectionAndResubmission() {
        String code = "P-PAY-REJ";
        ensureProvince(code, "Đà Nẵng");

        Long ownerId = createTestUser("seller-rej-test@example.com", "Trần Thị Mai");

        ShopRegistrationDto regDto = new ShopRegistrationDto();
        regDto.setName("Đà Nẵng Fruit Farm");
        regDto.setPhone("0911223344");
        regDto.setProvinceCode(code);
        regDto.setWardName("Hải Châu 1");
        regDto.setAddressDetail("Số 25 Lê Duẩn");
        regDto.setIdCardNumber("048195001234");
        regDto.setIdCardName("Trần Thị Mai");
        regDto.setAgreeTerms(true);

        Shop shop = customerShopService.registerShop(ownerId, regDto);
        operationsShopService.approveShop(shop.getId());

        // Submit mismatched bank info (Account belongs to someone else)
        ShopPaymentSetupDto mismatchDto = new ShopPaymentSetupDto();
        mismatchDto.setBankName("Techcombank (TCB)");
        mismatchDto.setBankAccountNumber("19033445566");
        mismatchDto.setBankAccountName("LÊ VĂN HÙNG");

        customerShopService.submitPaymentInfo(ownerId, mismatchDto);

        ShopApprovalDetailDto review = operationsShopService.getShopDetail(shop.getId());
        assertFalse(review.isBankAccountMatchesIdCard(), "Tên LÊ VĂN HÙNG không được khớp với Trần Thị Mai");

        // Operator rejects payment
        operationsShopService.rejectPayment(shop.getId(), "Tên chủ tài khoản (LÊ VĂN HÙNG) không trùng khớp với CCCD đã xác thực (Trần Thị Mai).");

        ShopApprovalDetailDto afterReject = operationsShopService.getShopDetail(shop.getId());
        assertEquals("REJECTED", afterReject.getPaymentApprovalStatus());
        assertEquals("CLOSED", afterReject.getOperatingStatus());
        assertTrue(afterReject.getBankRejectionReason().contains("không trùng khớp với CCCD"));

        // Seller corrects bank info with correct name
        ShopPaymentSetupDto correctedDto = new ShopPaymentSetupDto();
        correctedDto.setBankName("Techcombank (TCB)");
        correctedDto.setBankAccountNumber("19088776655");
        correctedDto.setBankAccountName("TRAN THI MAI");

        customerShopService.submitPaymentInfo(ownerId, correctedDto);

        ShopApprovalDetailDto reReview = operationsShopService.getShopDetail(shop.getId());
        assertEquals("PENDING", reReview.getPaymentApprovalStatus());
        assertTrue(reReview.isBankAccountMatchesIdCard());

        // Operator approves corrected payment info
        operationsShopService.approvePayment(shop.getId());
        ShopApprovalDetailDto approvedDetail = operationsShopService.getShopDetail(shop.getId());
        assertEquals("APPROVED", approvedDetail.getPaymentApprovalStatus());
        assertEquals("OPEN", approvedDetail.getOperatingStatus());
    }
}
