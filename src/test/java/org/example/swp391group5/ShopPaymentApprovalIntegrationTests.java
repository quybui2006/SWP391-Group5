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
    private repository.ProductVariantRepository productVariantRepository;

    @Autowired
    private service.customer.CartService cartService;

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

        // 2. Operator trực tiếp phê duyệt mở Shop
        operationsShopService.approveShop(registeredShop.getId());

        ShopApprovalDetailDto detailAfterStep1 = operationsShopService.getShopDetail(registeredShop.getId());
        assertEquals("APPROVED", detailAfterStep1.getSellerApprovalStatus());
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

    @Test
    void testDirectShopApproval() {
        String code = "P-STAGE-CHECK";
        ensureProvince(code, "Hải Phòng");

        Long ownerId = createTestUser("seller-stage-test@example.com", "Vũ Hoàng");

        ShopRegistrationDto regDto = new ShopRegistrationDto();
        regDto.setName("Hải Phòng Fruit");
        regDto.setPhone("0933445566");
        regDto.setProvinceCode(code);
        regDto.setWardName("Lê Chân");
        regDto.setAddressDetail("Số 10 Lạch Tray");
        regDto.setIdCardNumber("031195007890");
        regDto.setIdCardName("Vũ Hoàng");
        regDto.setAgreeTerms(true);

        Shop shop = customerShopService.registerShop(ownerId, regDto);

        // Operations trực tiếp duyệt shop mà không cần duyệt tài khoản riêng biệt
        operationsShopService.approveShop(shop.getId());
        ShopApprovalDetailDto detail = operationsShopService.getShopDetail(shop.getId());
        assertEquals("APPROVED", detail.getApprovalStatus());
        assertEquals("APPROVED", detail.getSellerApprovalStatus());
    }

    @Test
    void testSellingPrincipleOnlyPushedToMarketplaceAfterPaymentApproval() {
        String code = "P-SELL-PRINCIPLE";
        ensureProvince(code, "Cần Thơ");

        Long ownerId = createTestUser("seller-principle@example.com", "Lê Thị Bích");
        Long customerId = createTestUser("buyer-principle@example.com", "Nguyễn Khách Hàng");

        // 1. Đăng ký shop
        ShopRegistrationDto regDto = new ShopRegistrationDto();
        regDto.setName("Cửa Hàng Trái Cây Miền Tây");
        regDto.setPhone("0977889900");
        regDto.setProvinceCode(code);
        regDto.setWardName("Ninh Kiều");
        regDto.setAddressDetail("Số 88 30 Tháng 4");
        regDto.setIdCardNumber("092198005678");
        regDto.setIdCardName("Lê Thị Bích");
        regDto.setAgreeTerms(true);

        Shop shop = customerShopService.registerShop(ownerId, regDto);
        Long shopId = shop.getId();

        // 2. Shop được duyệt (Step 1 hoàn thành: approval_status = 'APPROVED', operating_status = 'CLOSED', payment_approval_status = 'UNCONFIGURED')
        operationsShopService.approveShop(shopId);

        // Shop chủ động thêm sản phẩm và biến thể nội bộ của cửa hàng
        jdbc.update("INSERT INTO units(code, name) VALUES ('U-KG-TEST', 'Kg') ON DUPLICATE KEY UPDATE name=VALUES(name)");
        long unitId = jdbc.queryForObject("SELECT id FROM units WHERE code='U-KG-TEST'", Long.class);

        jdbc.update("INSERT INTO products(name, selling_status, approval_status, shop_id, description, origin, batch_code, is_visible) " +
                "VALUES ('Xoài Cát Hòa Lộc', 'ACTIVE', 'APPROVED', ?, 'Xoài cát ngọt đậm chuẩn loại 1', 'Tiền Giang', 'BATCH-XOAI-01', 1)", shopId);
        long productId = jdbc.queryForObject("SELECT id FROM products WHERE batch_code='BATCH-XOAI-01'", Long.class);

        jdbc.update("INSERT INTO product_variants(product_id, name, image_url, price, status, approval_status, reviewed_at, base_unit_id, shop_id, sku, specification, min_order_quantity) " +
                "VALUES (?, 'Xoài Cát Hòa Lộc Hộp 2kg', 'https://example.com/xoai.jpg', 150000, 'ACTIVE', 'APPROVED', CURRENT_TIMESTAMP, ?, ?, 'SKU-XOAI-01', 'Hộp 2kg', 1)",
                productId, unitId, shopId);
        long variantId = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-XOAI-01'", Long.class);

        jdbc.update("INSERT INTO variant_inventory(variant_id, initial_quantity, quantity_on_hand, low_stock_threshold_pct, created_by) VALUES (?, 100, 100, 10, ?)", variantId, ownerId);

        // NGUYÊN TẮC BÁN HÀNG: Khi chưa được duyệt thông tin tài khoản ngân hàng, sản phẩm CHƯA ĐƯỢC ĐẨY LÊN SÀN BÁN HÀNG
        // 1. Không xuất hiện trong danh sách hiển thị sàn bán hàng (catalog)
        boolean inCatalogBefore = productVariantRepository.findApprovedActiveForCatalog().stream().anyMatch(v -> v.getId().equals(variantId));
        assertFalse(inCatalogBefore, "Sản phẩm chưa được đẩy lên sàn bán hàng khi shop chưa hoàn tất duyệt thông tin ngân hàng!");

        // 2. Không thể tìm mua công khai
        assertTrue(productVariantRepository.findSellableById(variantId).isEmpty(), "Không được phép tìm mua biến thể khi shop chưa duyệt thông tin ngân hàng!");

        // 3. Khách hàng không thể thêm vào giỏ hàng
        assertThrows(IllegalArgumentException.class, () -> cartService.add(customerId, variantId, 1),
                "Phải báo lỗi khi thêm vào giỏ hàng vì shop chưa hoàn tất thông tin tài khoản ngân hàng");

        // 3. Shop nộp thông tin tài khoản ngân hàng và được Admin duyệt
        ShopPaymentSetupDto payDto = new ShopPaymentSetupDto();
        payDto.setBankName("MB Bank");
        payDto.setBankAccountNumber("092198005678");
        payDto.setBankAccountName("LE THI BICH");
        payDto.setBankBranch("Chi nhánh Cần Thơ");
        customerShopService.submitPaymentInfo(ownerId, payDto);

        operationsShopService.approvePayment(shopId);

        // SAU KHI ĐƯỢC DUYỆT ĐỦ THỦ TỤC: Sản phẩm mới chính thức được đẩy lên sàn bán hàng
        boolean inCatalogAfter = productVariantRepository.findApprovedActiveForCatalog().stream().anyMatch(v -> v.getId().equals(variantId));
        assertTrue(inCatalogAfter, "Sản phẩm phải được đẩy lên sàn bán hàng sau khi shop duyệt thông tin tài khoản ngân hàng thành công!");

        assertTrue(productVariantRepository.findSellableById(variantId).isPresent(), "Biến thể phải mở bán sau khi duyệt thông tin ngân hàng!");

        // Khách hàng có thể thêm vào giỏ hàng và mua hàng bình thường
        assertDoesNotThrow(() -> cartService.add(customerId, variantId, 1));
        assertEquals(1, cartService.itemCount(customerId));
    }

    @Test
    void testShopRegistrationWithBusinessLicenseAndFoodSafetyCert() {
        String code = "P-CERT-TEST";
        ensureProvince(code, "Lâm Đồng");

        Long ownerId = createTestUser("seller-cert@example.com", "Hoàng Văn Tuấn");

        ShopRegistrationDto regDto = new ShopRegistrationDto();
        regDto.setName("Nông Trại Trái Cây Đà Lạt");
        regDto.setDescription("Chuyên dâu tây và bơ sáp tươi ngon");
        regDto.setPhone("0944556677");
        regDto.setProvinceCode(code);
        regDto.setWardName("Phường 1");
        regDto.setAddressDetail("Số 12 Mai Anh Đào");
        regDto.setIdCardNumber("068195009876");
        regDto.setIdCardName("Hoàng Văn Tuấn");
        regDto.setAgreeTerms(true);

        // Giấy phép kinh doanh
        regDto.setBusinessLicenseNumber("5801234567");
        regDto.setBusinessLicenseDate("2022-06-10");
        regDto.setBusinessLicensePlace("Sở Kế hoạch và Đầu tư tỉnh Lâm Đồng");
        regDto.setBusinessLicenseUrl("/uploads/licenses/gpkd-dalat.png");

        // Giấy chứng nhận vệ sinh an toàn thực phẩm
        regDto.setFoodSafetyCertNumber("105/2023/ATTP-LĐ");
        regDto.setFoodSafetyCertDate("2023-01-20");
        regDto.setFoodSafetyCertExpiry("2026-01-20");
        regDto.setFoodSafetyCertPlace("Chi cục Quản lý Chất lượng Nông lâm sản và Thủy sản Lâm Đồng");
        regDto.setFoodSafetyCertUrl("/uploads/licenses/attp-dalat.png");

        Shop shop = customerShopService.registerShop(ownerId, regDto);
        assertNotNull(shop.getId());

        // Kiểm tra database lưu trữ đúng và Operations xem được
        ShopApprovalDetailDto detail = operationsShopService.getShopDetail(shop.getId());
        assertEquals("5801234567", detail.getBusinessLicenseNumber());
        assertEquals("2022-06-10", detail.getBusinessLicenseDate());
        assertEquals("Sở Kế hoạch và Đầu tư tỉnh Lâm Đồng", detail.getBusinessLicensePlace());
        assertEquals("/uploads/licenses/gpkd-dalat.png", detail.getBusinessLicenseUrl());

        assertEquals("105/2023/ATTP-LĐ", detail.getFoodSafetyCertNumber());
        assertEquals("2023-01-20", detail.getFoodSafetyCertDate());
        assertEquals("2026-01-20", detail.getFoodSafetyCertExpiry());
        assertEquals("Chi cục Quản lý Chất lượng Nông lâm sản và Thủy sản Lâm Đồng", detail.getFoodSafetyCertPlace());
        assertEquals("/uploads/licenses/attp-dalat.png", detail.getFoodSafetyCertUrl());
    }

}
