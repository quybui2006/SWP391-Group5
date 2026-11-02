package org.example.swp391group5;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class CustomerShopIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Test
    void customerCanAccessShopProfileAndCategories() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        // 1. Truy cập /shops/1
        mvc.perform(get("/shops/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/customershop"))
                .andExpect(model().attributeExists("shop"))
                .andExpect(model().attributeExists("categories"))
                .andExpect(model().attributeExists("products"))
                .andExpect(model().attributeExists("shopVouchers"));

        // 2. Truy cập /shop/1
        mvc.perform(get("/shop/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/customershop"));

        // 3. Lọc theo categoryId và keyword
        mvc.perform(get("/shops/1").param("keyword", "Táo").param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/customershop"))
                .andExpect(model().attribute("keyword", "Táo"))
                .andExpect(model().attribute("sort", "price_asc"));
    }
}
