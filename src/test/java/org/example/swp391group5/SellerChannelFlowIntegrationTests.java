package org.example.swp391group5;

import dto.seller.SellerAccountActivationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import service.operations.OperationsShopApprovalService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class SellerChannelFlowIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OperationsShopApprovalService operationsShopService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private Long createTestUser(String email, String fullName) {
        jdbc.update("INSERT INTO users(full_name, email, password_hash) VALUES (?, ?, 'hashed_pass')", fullName, email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private void ensureProvince(String code, String name) {
        jdbc.update("INSERT INTO provinces(code, name, is_active) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE name=VALUES(name)", code, name);
    }

    @Test
    void unauthenticatedUser_accessSellerChannel_redirectsToLogin() throws Exception {
        mvc.perform(get("/seller/channel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?continueTo=/seller/channel"));
    }

    @Test
    void authenticatedUser_withoutShop_accessSellerChannel_redirectsToActivate() throws Exception {
        Long userId = createTestUser("new-seller@example.com", "Nguyễn Hoàng Nam");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);

        mvc.perform(get("/seller/channel").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/activate"));
    }

    @Test
    void legacyRoutes_redirectsAwayFromCustomer() throws Exception {
        mvc.perform(get("/customer/shop-registration"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/channel"));

        mvc.perform(get("/customer/shop-registration/status"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/status"));

        mvc.perform(get("/customer/shop-registration/payment"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/payment"));
    }

    @Test
    void accessCreateShop_withoutActivation_redirectsToActivate() throws Exception {
        Long userId = createTestUser("seller-no-act@example.com", "Lê Văn Hùng");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);

        mvc.perform(get("/seller/create-shop").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/activate"));
    }

    @Test
    void fullSellerChannelFlow_activationThenCreateShopThenApprovalAndPayment() throws Exception {
        ensureProvince("P-HN-TEST", "Hà Nội");
        Long userId = createTestUser("complete-seller@example.com", "Phạm Văn Đồng");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);

        // 1. Visit /seller/channel -> redirects to /seller/activate
        mvc.perform(get("/seller/channel").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/activate"));

        // 2. View /seller/activate -> 200 OK
        mvc.perform(get("/seller/activate").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/activate"))
                .andExpect(model().attribute("activeStep", 1));

        // 3. Submit /seller/activate -> Step 1 success, stores in session, redirects to /seller/create-shop
        mvc.perform(post("/seller/activate").session(session)
                        .param("phone", "0988112233")
                        .param("email", "complete-seller@example.com")
                        .param("idCardName", "PHẠM VĂN ĐỒNG")
                        .param("idCardNumber", "001203009988")
                        .param("idCardDate", "2022-01-15")
                        .param("idCardPlace", "Cục Cảnh sát QLHC về TTXH")
                        .param("businessType", "INDIVIDUAL")
                        .param("taxCode", "8012345678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/create-shop"));

        assertNotNull(session.getAttribute("sellerActivation"));
        SellerAccountActivationDto activation = (SellerAccountActivationDto) session.getAttribute("sellerActivation");
        assertEquals("PHẠM VĂN ĐỒNG", activation.getIdCardName());

        // Test: Giả lập đăng xuất và đăng nhập lại (session mới hoàn toàn không có sellerActivation)
        MockHttpSession newSession = new MockHttpSession();
        newSession.setAttribute("userId", userId);
        newSession.setAttribute("user", "complete-seller@example.com");

        // Đăng xuất xong truy cập /seller/channel vẫn nhớ đã kích hoạt và chuyển thẳng sang /seller/create-shop
        mvc.perform(get("/seller/channel").session(newSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/create-shop"));

        // 4. View /seller/create-shop -> 200 OK (tự khôi phục activation từ database)
        mvc.perform(get("/seller/create-shop").session(newSession))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/create-shop"))
                .andExpect(model().attribute("activeStep", 2))
                .andExpect(model().attributeExists("activation"))
                .andExpect(model().attributeExists("provinces"));

        // Tiếp tục dùng newSession cho bước tiếp theo
        session = newSession;

        // 5. Submit /seller/create-shop -> Step 2 success, creates Shop, redirects to /seller/status
        mvc.perform(post("/seller/create-shop").session(session)
                        .param("name", "Nông Trại Trái Cây Ba Vì")
                        .param("description", "Hoa quả sạch chuẩn VietGAP Ba Vì")
                        .param("phone", "0988112233")
                        .param("provinceCode", "P-HN-TEST")
                        .param("wardName", "Xã Ba Trại")
                        .param("addressDetail", "Thôn 2, Ba Trại")
                        .param("shippingFast", "true")
                        .param("shippingExpress", "true")
                        .param("shippingStandard", "true")
                        .param("agreeTerms", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/status"));

        // Check Shop created
        Long shopId = jdbc.queryForObject("SELECT id FROM shops WHERE owner_id=?", Long.class, userId);
        assertNotNull(shopId);

        // 6. View /seller/status -> 200 OK (PENDING)
        mvc.perform(get("/seller/status").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/status"))
                .andExpect(model().attribute("status", "PENDING"));

        // 7. Operator trực tiếp phê duyệt mở Shop
        operationsShopService.approveShop(shopId);

        // 8. Now /seller/channel should redirect to /seller/payment
        mvc.perform(get("/seller/channel").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/payment"));

        // 9. View /seller/payment -> 200 OK
        mvc.perform(get("/seller/payment").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/payment"))
                .andExpect(model().attribute("activeStep", 3));

        // 10. Submit payment info matching CCCD name
        mvc.perform(post("/seller/payment").session(session)
                        .param("bankName", "Vietcombank (VCB)")
                        .param("bankAccountNumber", "098811223344")
                        .param("bankAccountName", "PHAM VAN DONG")
                        .param("bankBranch", "Chi nhánh Ba Đình"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/payment"));

        // 11. Operator approves payment
        operationsShopService.approvePayment(shopId);

        // 12. Finally, /seller/channel redirects to /shopowner/home
        mvc.perform(get("/seller/channel").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/shopowner/home"));
        assertEquals("SHOP_OWNER", session.getAttribute("role"));
    }
}
