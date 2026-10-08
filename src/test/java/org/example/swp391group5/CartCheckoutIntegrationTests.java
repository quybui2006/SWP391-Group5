package org.example.swp391group5;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import service.CartService;
import service.OrderCheckoutService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:freshfruit_cart_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class CartCheckoutIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartService cart;
    @Autowired OrderCheckoutService checkout;
    @Autowired MockMvc mvc;

    private long seed() {
        jdbc.update("INSERT INTO provinces(code,name) VALUES ('P-CHECKOUT','Province checkout')");
        jdbc.update("INSERT INTO users(full_name,email) VALUES ('Checkout user','checkout@test.example')");
        long user = jdbc.queryForObject("SELECT id FROM users WHERE email='checkout@test.example'", Long.class);
        jdbc.update("INSERT INTO shops(owner_id,name,phone,province_code,ward_name,address_detail,approval_status,operating_status) VALUES (?,?,?,?,?,?,?,?)", user, "Test shop", "0912345678", "P-CHECKOUT", "Ward", "Shop address", "APPROVED", "OPEN");
        long shop = jdbc.queryForObject("SELECT id FROM shops WHERE province_code='P-CHECKOUT'", Long.class);
        jdbc.update("INSERT INTO units(code,name) VALUES ('UNIT-CHECKOUT','kg')");
        long unit = jdbc.queryForObject("SELECT id FROM units WHERE code='UNIT-CHECKOUT'", Long.class);
        jdbc.update("INSERT INTO products(shop_id,name,batch_code,received_date,expiry_date,approval_status,selling_status) VALUES (?,?,?,?,?,'APPROVED','ACTIVE')", shop, "Apple", "BATCH-CHECKOUT", java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(10));
        long product = jdbc.queryForObject("SELECT id FROM products WHERE batch_code='BATCH-CHECKOUT'", Long.class);
        jdbc.update("INSERT INTO product_variants(product_id,shop_id,sku,name,base_unit_id,price,status) VALUES (?,?,?,?,?,?,'ACTIVE')", product, shop, "SKU-CHECKOUT", "1kg", unit, 50000);
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        jdbc.update("INSERT INTO variant_inventory(variant_id,initial_quantity,quantity_on_hand,available_quantity,created_by) VALUES (?,?,?, ?,?)", variant, 20, 20, 20, user);
        jdbc.update("INSERT INTO user_addresses(user_id,recipient_name,recipient_phone,province_code,ward_name,address_detail,is_default) VALUES (?,?,?,?,?,?,TRUE)", user, "Recipient", "0912345678", "P-CHECKOUT", "Ward", "1 Test St");
        return user;
    }

    @Test
    void persistsCartAndCreatesOrderWhileReservingStockAtomically() {
        long user = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, user);
        cart.add(user, variant, 2);
        assertEquals(1, cart.items(user).size());
        String code = checkout.placeOrder(user, List.of(variant), address, null, null, null, null, null, "Ring bell");
        assertTrue(code.startsWith("FF-"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_code=?", Integer.class, code));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transactions", Integer.class));
        assertTrue(cart.items(user).isEmpty());
    }

    @Test
    void rejectsQuantityAboveAvailableStockWithoutCreatingOrder() {
        long user = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, user);
        cart.add(user, variant, 2);
        jdbc.update("UPDATE variant_inventory SET available_quantity=1 WHERE variant_id=?", variant);
        assertThrows(IllegalArgumentException.class, () -> checkout.placeOrder(user, List.of(variant), address, null, null, null, null, null, null));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
    }

    @Test
    void customerCanRenderCartCheckoutAndSubmitOrderThroughWebFlow() throws Exception {
        long user = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, user);
        cart.add(user, variant, 1);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user);
        session.setAttribute("role", "CUSTOMER");

        mvc.perform(get("/cart").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Giỏ hàng")));
        mvc.perform(post("/checkout").session(session).param("selectedVariantIds", String.valueOf(variant)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Xác nhận đơn hàng")));
        mvc.perform(post("/order/submit").session(session).param("selectedVariantIds", String.valueOf(variant))
                        .param("addressId", String.valueOf(address)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    void productScreensPersistCartItemsAndReadTheDatabaseCartCount() throws Exception {
        long user = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user);
        session.setAttribute("role", "CUSTOMER");

        mvc.perform(post("/cart/items").session(session)
                        .param("variantId", String.valueOf(variant)).param("quantity", "3"))
                .andExpect(status().isOk()).andExpect(content().string("3"));
        mvc.perform(get("/cart/count").session(session))
                .andExpect(status().isOk()).andExpect(content().string("3"));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/cart-integration.js")));
        mvc.perform(get("/search")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/cart-integration.js")));
    }

    @Test
    void addressBookUsesTheAuthenticatedCustomerAndLinksBackToCart() throws Exception {
        long customerId = seed();
        jdbc.update("INSERT INTO roles(code,name) VALUES ('CUSTOMER','Customer')");
        long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code='CUSTOMER'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", customerId, roleId);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", customerId);
        session.setAttribute("role", "CUSTOMER");

        mvc.perform(get("/customer/account/address").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("checkout@test.example")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/cart\"")));
        mvc.perform(get("/customer/account/address"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?continueTo=/customer/account/address"));
    }

    @Test
    void checkoutAllowsAFirstOrderWithANewAddress() throws Exception {
        long user = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        cart.add(user, variant, 1);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user);
        session.setAttribute("role", "CUSTOMER");

        mvc.perform(post("/checkout").session(session).param("selectedVariantIds", String.valueOf(variant)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"recipientName\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhập địa chỉ giao hàng")));
        mvc.perform(post("/order/submit").session(session)
                        .param("selectedVariantIds", String.valueOf(variant)).param("addressId", "0")
                        .param("recipientName", "New recipient").param("recipientPhone", "0912345678")
                        .param("provinceCode", "P-CHECKOUT").param("wardName", "Ward 2")
                        .param("addressDetail", "2 Test St"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_addresses WHERE user_id=?", Integer.class, user));
    }

    @Test
    void checkoutSplitsMultipleShopsAndSavesNewDeliveryAddress() {
        long user = seed();
        long owner = seedOwnerForSecondShop();
        long secondShop = jdbc.queryForObject("SELECT id FROM shops WHERE owner_id=?", Long.class, owner);
        long unit = jdbc.queryForObject("SELECT id FROM units WHERE code='UNIT-CHECKOUT'", Long.class);
        jdbc.update("INSERT INTO products(shop_id,name,batch_code,received_date,expiry_date,approval_status,selling_status) VALUES (?,?,?,?,?,'APPROVED','ACTIVE')", secondShop, "Pear", "BATCH-CHECKOUT-2", java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(10));
        long product = jdbc.queryForObject("SELECT id FROM products WHERE batch_code='BATCH-CHECKOUT-2'", Long.class);
        jdbc.update("INSERT INTO product_variants(product_id,shop_id,sku,name,base_unit_id,price,status) VALUES (?,?,?,?,?,?,'ACTIVE')", product, secondShop, "SKU-CHECKOUT-2", "500g", unit, 30000);
        long secondVariant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT-2'", Long.class);
        jdbc.update("INSERT INTO variant_inventory(variant_id,initial_quantity,quantity_on_hand,available_quantity,created_by) VALUES (?,?,?, ?,?)", secondVariant, 20, 20, 20, owner);
        long firstVariant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        cart.add(user, firstVariant, 1);
        cart.add(user, secondVariant, 1);

        String codes = checkout.placeOrder(user, List.of(firstVariant, secondVariant), 0L,
                "New recipient", "0912345678", "P-CHECKOUT", "Ward 2", "2 Test St", null);
        assertEquals(2, codes.split(", ").length);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
        assertEquals(120000, jdbc.queryForObject("SELECT total_amount FROM checkouts", Long.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_addresses WHERE user_id=?", Integer.class, user));
    }

    private long seedOwnerForSecondShop() {
        jdbc.update("INSERT INTO users(full_name,email) VALUES ('Second shop owner','second-owner@test.example')");
        long owner = jdbc.queryForObject("SELECT id FROM users WHERE email='second-owner@test.example'", Long.class);
        jdbc.update("INSERT INTO shops(owner_id,name,phone,province_code,ward_name,address_detail,approval_status,operating_status) VALUES (?,?,?,?,?,?,?,?)", owner, "Second shop", "0912345678", "P-CHECKOUT", "Ward", "Shop address", "APPROVED", "OPEN");
        return owner;
    }

    @Test
    void primaryPagesRenderAndProtectedFlowsRequireLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/search")).andExpect(status().isOk());
        mvc.perform(get("/cart")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/shop/products")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void shopOwnerProductScreensUseDashboardNavigationAndRealRoutes() throws Exception {
        long owner = seed();
        long productId = jdbc.queryForObject("SELECT id FROM products WHERE batch_code='BATCH-CHECKOUT'", Long.class);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", owner);
        session.setAttribute("role", "SHOP_OWNER");

        mvc.perform(get("/shopowner/home"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/shop/products\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("href=\"#\">◫ Products"))));
        mvc.perform(get("/shop/products").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/shop/products/add")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/shop/product/")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("site-header"))));
        mvc.perform(get("/shop/products/add").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"name\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"sku\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/shop/products/add\"")));
        mvc.perform(get("/shop/product/" + productId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/shop/products\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/shop/product/" + productId + "/edit")));
        mvc.perform(get("/shop/product/" + productId + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/shop/product/" + productId + "/edit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/shop/products\"")));
        mvc.perform(get("/shopowner/product-variants").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SKU-CHECKOUT")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/shopowner/product-variants\"")));
        mvc.perform(get("/shopowner/product-variants").session(session).param("keyword", "SKU-CHECKOUT"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SKU-CHECKOUT")));
        mvc.perform(get("/shopowner/product-variants").session(session).param("keyword", "no-such-sku"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Không tìm thấy biến thể phù hợp")));
        mvc.perform(get("/shopowner/product-variants"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
