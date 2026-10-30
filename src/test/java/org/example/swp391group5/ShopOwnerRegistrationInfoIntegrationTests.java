package org.example.swp391group5;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ShopOwnerRegistrationInfoIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void testRegistrationInfoAccessControlAndRendering() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();

        // 1. Chưa đăng nhập -> redirect sang /login
        mvc.perform(get("/shopowner/registration-info"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mvc.perform(get("/seller/registration-info"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // 2. Đăng nhập nhưng role CUSTOMER -> 403 Forbidden bởi Interceptor
        var customerSession = new MockHttpSession();
        customerSession.setAttribute("userId", 888881L);
        customerSession.setAttribute("role", "CUSTOMER");
        customerSession.setAttribute("fullName", "Nguyen Van Customer");

        mvc.perform(get("/shopowner/registration-info").session(customerSession))
                .andExpect(status().isForbidden());

        // 3. Đã đăng nhập và đã có Shop -> 200 OK với đầy đủ thông tin
        jdbc.update("INSERT INTO provinces(code, name) VALUES ('P-REG-TEST', 'Tỉnh Nông Sản Xanh') ON DUPLICATE KEY UPDATE name=name");
        jdbc.update("""
            INSERT INTO users(id, full_name, email, password_hash, email_verified_at)
            VALUES (888882, 'Chủ Vườn Tươi', 'reg-test-owner@example.com', 'hash', CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE full_name=full_name
        """);

        jdbc.update("""
            INSERT INTO shops(owner_id, name, description, phone, province_code, ward_name, address_detail,
                              approval_status, operating_status, id_card_number, id_card_name, id_card_place,
                              business_type, tax_code, tax_name, shipping_methods, bank_name, bank_account_number, bank_account_name, payment_approval_status)
            VALUES (888882, 'Vườn Trái Cây Miền Tây', 'Hoa quả sạch chứng nhận VietGAP', '0988776655',
                    'P-REG-TEST', 'Phường An Bình', 'Số 99 Đường Bờ Sông',
                    'APPROVED', 'OPEN', '079203009999', 'CHỦ VƯỜN TƯƠI', 'Cục CSQLHC về TTXH',
                    'INDIVIDUAL', '8899776655', 'CHỦ VƯỜN TƯƠI', 'FAST,EXPRESS,STANDARD',
                    'Vietcombank', '998877665544', 'CHU VUON TUOI', 'APPROVED')
        """);

        var ownerSession = new MockHttpSession();
        ownerSession.setAttribute("userId", 888882L);
        ownerSession.setAttribute("role", "SHOP_OWNER");
        ownerSession.setAttribute("fullName", "Chủ Vườn Tươi");

        mvc.perform(get("/shopowner/registration-info").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("shopowner/registration-info"))
                .andExpect(model().attributeExists("shop"))
                .andExpect(model().attributeExists("provinceName"))
                .andExpect(model().attribute("provinceName", "Tỉnh Nông Sản Xanh"))
                .andExpect(model().attribute("shippingFast", true))
                .andExpect(model().attribute("shippingExpress", true))
                .andExpect(model().attribute("shippingStandard", true))
                .andExpect(content().string(containsString("Vườn Trái Cây Miền Tây")))
                .andExpect(content().string(containsString("0988776655")))
                .andExpect(content().string(containsString("079203009999")))
                .andExpect(content().string(containsString("Vietcombank")))
                .andExpect(content().string(containsString("998877665544")))
                .andExpect(content().string(containsString("Thông tin đăng ký")));

        // Test alias /seller/registration-info
        mvc.perform(get("/seller/registration-info").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("shopowner/registration-info"))
                .andExpect(content().string(containsString("Vườn Trái Cây Miền Tây")));
    }
}
