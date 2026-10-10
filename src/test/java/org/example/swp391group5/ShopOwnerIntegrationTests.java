package org.example.swp391group5;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import service.ShopProductService;
import service.ShopOrderService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ShopOwnerIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShopProductService products;
    @Autowired ShopOrderService orders;

    @Test
    void sessionNavigatesShopPagesAndProtectsOtherOwners() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("INSERT INTO users(full_name,email,password_hash,email_verified_at) VALUES ('Owner test','shop-flow@test.example',?,CURRENT_TIMESTAMP)", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Test123!"));
        long owner = jdbc.queryForObject("SELECT id FROM users WHERE email='shop-flow@test.example'", Long.class);
        jdbc.update("INSERT INTO roles(code,name) VALUES ('SHOP_OWNER','Shop Owner') ON DUPLICATE KEY UPDATE code=code");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='SHOP_OWNER'", owner);
        var login = mvc.perform(post("/login").param("email","shop-flow@test.example").param("password","Test123!"))
                .andExpect(redirectedUrl("/shopowner/home")).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/shopowner/home").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Tài khoản chưa có cửa hàng")));
        mvc.perform(get("/shop/products").session(session)).andExpect(redirectedUrl("/shopowner/home"));
        jdbc.update("INSERT INTO provinces(code,name) VALUES ('P-SHOP-TEST','Test province')");
        jdbc.update("INSERT INTO shops(owner_id,name,phone,province_code,ward_name,address_detail) VALUES (?,'Owner shop','0912345678','P-SHOP-TEST','Ward','Address')", owner);
        for (String path : java.util.List.of("/shopowner/home", "/shop/products", "/shopowner/products", "/shop/products/add", "/shopowner/product-variants", "/shopowner/orders")) {
            mvc.perform(get(path).session(session)).andExpect(status().isOk());
            mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
        }
        assertEquals(0L, products.shopDashboardStats(owner).get("variants"));
        assertTrue(orders.list(owner).isEmpty());
        mvc.perform(get("/shopowner/orders/999999").session(session)).andExpect(status().isNotFound());
        mvc.perform(post("/shopowner/orders/999999/advance").session(session)).andExpect(status().isForbidden());
        session.setAttribute("role", "CUSTOMER");
        mvc.perform(get("/shop/products").session(session)).andExpect(status().isForbidden());
    }
}
