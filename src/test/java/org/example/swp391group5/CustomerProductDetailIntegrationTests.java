package org.example.swp391group5;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:freshfruit_cart_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class CustomerProductDetailIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Long seedProduct() {
        jdbc.update("INSERT INTO provinces(code, name) VALUES ('P-SHOPEE', 'Hà Nội - Shopee')");
        jdbc.update("INSERT INTO users(full_name, email) VALUES ('Shop Shopee Owner', 'shopee.owner@freshfruit.example')");
        long ownerId = jdbc.queryForObject("SELECT id FROM users WHERE email='shopee.owner@freshfruit.example'", Long.class);

        jdbc.update("INSERT INTO shops(owner_id, name, phone, province_code, ward_name, address_detail, approval_status, operating_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", ownerId, "FreshFruit Official Store", "0988776655", "P-SHOPEE", "Phường 1", "123 Đường Láng", "APPROVED", "OPEN");
        long shopId = jdbc.queryForObject("SELECT id FROM shops WHERE owner_id=?", Long.class, ownerId);

        jdbc.update("INSERT INTO units(code, name) VALUES ('U-HOP', 'Hộp')");
        long unitId = jdbc.queryForObject("SELECT id FROM units WHERE code='U-HOP'", Long.class);

        jdbc.update("INSERT INTO products(name, selling_status, approval_status, shop_id, description, origin, batch_code) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "Táo Envy Shopee Cao Cấp", "ACTIVE", "APPROVED", shopId, "Táo Envy nhập khẩu New Zealand tươi ngon, giòn ngọt.",
                "New Zealand", "BATCH-SHOPEE-01");
        long productId = jdbc.queryForObject("SELECT id FROM products WHERE batch_code='BATCH-SHOPEE-01'", Long.class);

        jdbc.update("INSERT INTO product_variants(product_id, name, image_url, price, status, approval_status, reviewed_at, base_unit_id, shop_id, sku, specification, min_order_quantity) " +
                        "VALUES (?, ?, ?, ?, 'ACTIVE', 'APPROVED', CURRENT_TIMESTAMP, ?, ?, ?, ?, ?)",
                productId, "Táo Envy New Zealand Hộp 1kg", "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6",
                new BigDecimal("129000"), unitId, shopId, "SKU-SHOPEE-ENVY", "Hộp 1kg (4-5 quả)", 1);
        return jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-SHOPEE-ENVY'", Long.class);
    }

    @Test
    void productDetailPage_RendersShopeeLayoutSuccessfully() throws Exception {
        Long variantId = seedProduct();

        mvc.perform(get("/product/" + variantId))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/customerproductdetail"))
                .andExpect(model().attributeExists("variant"))
                .andExpect(model().attributeExists("shop"))
                .andExpect(model().attributeExists("reviews"))
                .andExpect(model().attributeExists("reviewStats"))
                .andExpect(model().attributeExists("relatedProducts"))
                .andExpect(model().attribute("isCustomer", false))
                // Kiểm tra các phần bố cục Shopee đã dựng
                .andExpect(content().string(containsString("Yêu thích+")))
                .andExpect(content().string(containsString("CHI TIẾT SẢN PHẨM")))
                .andExpect(content().string(containsString("MÔ TẢ SẢN PHẨM")))
                .andExpect(content().string(containsString("ĐÁNH GIÁ SẢN PHẨM")))
                .andExpect(content().string(containsString("Có thể bạn cũng thích")))
                .andExpect(content().string(containsString("Thêm Vào Giỏ Hàng")))
                .andExpect(content().string(containsString("Mua Ngay")))
                .andExpect(content().string(containsString("FreshFruit Đảm Bảo")));
    }

    @Test
    void productDetailPage_DoesNotOfferAlreadySavedVoucher() throws Exception {
        Long variantId = seedProduct();
        long customerId = jdbc.queryForObject("SELECT id FROM users WHERE email='shopee.owner@freshfruit.example'", Long.class);
        long shopId = jdbc.queryForObject("SELECT shop_id FROM product_variants WHERE id=?", Long.class, variantId);
        jdbc.update("INSERT INTO vouchers(shop_id,code,name,discount_type,discount_value,max_usages,starts_at,ends_at) VALUES (?,?,'Đã lưu','PERCENTAGE',10,10,CURRENT_TIMESTAMP(3),DATEADD('DAY',1,CURRENT_TIMESTAMP(3)))", shopId, "SAVED10");
        long voucherId = jdbc.queryForObject("SELECT id FROM vouchers WHERE shop_id=? AND code='SAVED10'", Long.class, shopId);
        jdbc.update("INSERT INTO customer_saved_vouchers(user_id,voucher_id) VALUES (?,?)", customerId, voucherId);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", customerId);
        session.setAttribute("role", "CUSTOMER");

        mvc.perform(get("/product/" + variantId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SAVED10")))
                .andExpect(content().string(containsString("Đã lưu")))
                .andExpect(content().string(not(containsString("data-voucher-id=\"" + voucherId + "\" onclick"))));
    }

    @Test
    void guestSubmittingReview_RedirectsToLogin() throws Exception {
        Long variantId = seedProduct();

        mvc.perform(post("/product/" + variantId + "/review")
                        .param("rating", "5")
                        .param("content", "Trái cây rất ngon!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?continueTo=/product/" + variantId));
    }

    @Test
    void customerSubmittingReview_SavesAndRedirects() throws Exception {
        Long variantId = seedProduct();

        jdbc.update("INSERT INTO users(full_name, email) VALUES ('Khách Mua Hàng', 'buyer.shopee@test.com')");
        long customerId = jdbc.queryForObject("SELECT id FROM users WHERE email='buyer.shopee@test.com'", Long.class);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", customerId);
        session.setAttribute("role", "CUSTOMER");
        session.setAttribute("fullName", "Khách Mua Hàng");

        mvc.perform(post("/product/" + variantId + "/review")
                        .session(session)
                        .param("rating", "5")
                        .param("content", "Trái cây rất giòn và ngọt, đóng gói chuẩn chỉnh!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/product/" + variantId + "#reviews-section"));
    }
}
