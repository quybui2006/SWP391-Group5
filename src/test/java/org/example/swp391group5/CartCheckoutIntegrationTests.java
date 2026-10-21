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
import service.customer.CartService;
import service.customer.OrderCheckoutService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import service.shop.ShopOrderService;
import service.delivery.DeliveryOrderService;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:freshfruit_cart_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class CartCheckoutIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartService cart;
    @Autowired OrderCheckoutService checkout;
    @Autowired MockMvc mvc;
    @Autowired service.shop.ShopOrderService shopOrders;
    @Autowired DeliveryOrderService deliveryOrders;

    @Test
    void shopOwnerConfirmsOnlyTheirOwnCodOrders() throws Exception {
        long owner = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, owner);
        cart.add(owner, variant, 1);
        String code = checkout.placeOrder(owner, List.of(variant), address, null, null, null, null, null, null);
        long id = jdbc.queryForObject("SELECT id FROM orders WHERE order_code=?", Long.class, code);
        var session = new MockHttpSession();
        session.setAttribute("userId", owner);
        session.setAttribute("role", "SHOP_OWNER");
        mvc.perform(get("/shopowner/orders").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(code)));
        mvc.perform(get("/shopowner/orders/export.xlsx").session(session)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("shop-orders.xlsx")));
        mvc.perform(get("/shopowner/orders/" + id).session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("SKU-CHECKOUT")));
        String token = (String) session.getAttribute("shopOrderToken");
        mvc.perform(post("/shopowner/orders/" + id + "/confirm").session(session).param("token", token))
                .andExpect(redirectedUrl("/shopowner/orders/" + id));
        mvc.perform(post("/shopowner/orders/" + id + "/advance").session(session).param("token", token))
                .andExpect(status().isNotFound());
        assertEquals("CONFIRMED", shopOrders.detail(owner, id).get("status"));
        assertEquals(1, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
        assertThrows(IllegalArgumentException.class, () -> shopOrders.confirmCod(owner, id));
        assertTrue(shopOrders.list(-1L).isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> shopOrders.detail(-1L, id));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> shopOrders.confirmCod(-1L, id));
    }

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
        jdbc.update("INSERT INTO product_variants(product_id,shop_id,sku,name,base_unit_id,price,status,approval_status,reviewed_at) VALUES (?,?,?,?,?,?,'ACTIVE','APPROVED',CURRENT_TIMESTAMP)", product, shop, "SKU-CHECKOUT", "1kg", unit, 50000);
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        jdbc.update("INSERT INTO variant_inventory(variant_id,initial_quantity,quantity_on_hand,created_by) VALUES (?,?,?,?)", variant, 20, 20, user);
        jdbc.update("INSERT INTO user_addresses(user_id,recipient_name,recipient_phone,province_code,ward_name,address_detail,is_default) VALUES (?,?,?,?,?,?,TRUE)", user, "Recipient", "0912345678", "P-CHECKOUT", "Ward", "1 Test St");
        return user;
    }

    @Test
    void customerProfilePersistsAndAccountPagesRender() throws Exception {
        long user = seed();
        var session = new MockHttpSession();
        session.setAttribute("userId", user);
        session.setAttribute("role", "CUSTOMER");
        mvc.perform(get("/customer/account")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/customer/account/profile").session(session)
            .param("fullName", "Khách hàng Việt").param("phone", "0912345678")
            .param("gender", "FEMALE").param("birthDate", "2000-01-02"))
            .andExpect(redirectedUrl("/customer/account"));
        assertEquals("Khách hàng Việt", jdbc.queryForObject("SELECT full_name FROM users WHERE id=?", String.class, user));
        assertEquals("FEMALE", jdbc.queryForObject("SELECT gender FROM customer_profiles WHERE user_id=?", String.class, user));
        var imageBytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", imageBytes);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/customer/account/profile")
            .file(new org.springframework.mock.web.MockMultipartFile("avatar", "avatar.png", "image/png", imageBytes.toByteArray()))
            .session(session).param("fullName", "Khách hàng Việt").param("gender", "FEMALE"))
            .andExpect(redirectedUrl("/customer/account"));
        mvc.perform(get("/customer/account/avatar").session(session)).andExpect(status().isOk())
            .andExpect(content().bytes(imageBytes.toByteArray()));
        mvc.perform(get("/customer/account/avatar")).andExpect(status().isUnauthorized());
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        mvc.perform(post("/customer/account/favorites/save").param("variantId", String.valueOf(variant)).param("favorite", "true"))
            .andExpect(status().isUnauthorized());
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/customer/account/favorites/save").session(session)
                .param("variantId", String.valueOf(variant)).param("favorite", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.favorite").value(true));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM customer_favorites WHERE user_id=?", Integer.class, user));
        mvc.perform(get("/customer/account/favorites/ids").session(session))
            .andExpect(status().isOk()).andExpect(content().json("[" + variant + "]"));
        var otherSession = new MockHttpSession();
        otherSession.setAttribute("userId", -1L);
        otherSession.setAttribute("role", "CUSTOMER");
        mvc.perform(get("/customer/account/favorites/ids").session(otherSession))
            .andExpect(content().json("[]"));
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, user);
        cart.add(user, variant, 1);
        checkout.placeOrder(user, List.of(variant), address, null, null, null, null, null, null);
        for (String path : List.of("/customer/account", "/customer/account/orders", "/customer/account/favorites"))
            mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Khách hàng Việt")));
        mvc.perform(post("/customer/account/favorites/save").session(session)
            .param("variantId", String.valueOf(variant)).param("favorite", "false"))
            .andExpect(jsonPath("$.favorite").value(false));
        mvc.perform(get("/customer/account/favorites/ids").session(session))
            .andExpect(content().json("[]"));
        mvc.perform(post("/customer/account/profile").session(session)
            .param("fullName", "Invalid").param("birthDate", "2999-01-01"))
            .andExpect(flash().attributeExists("error"));
        assertEquals("Khách hàng Việt", jdbc.queryForObject("SELECT full_name FROM users WHERE id=?", String.class, user));
    }

    @Test
    void codCheckoutWaitsForShopConfirmationBeforeReservingStock() {
        long user = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, user);
        cart.add(user, variant, 2);
        assertEquals(1, cart.items(user).size());
        String code = checkout.placeOrder(user, List.of(variant), address, null, null, null, null, null, "Ring bell");
        assertTrue(code.startsWith("FF-"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_code=?", Integer.class, code));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
        assertEquals("UNRESERVED", jdbc.queryForObject("SELECT allocation_status FROM order_items", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transactions", Integer.class));
        long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_code=?", Long.class, code);
        shopOrders.confirmCod(user, orderId);
        assertEquals("CONFIRMED", shopOrders.detail(user, orderId).get("status"));
        assertEquals(2, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
        assertEquals("RESERVED", jdbc.queryForObject("SELECT allocation_status FROM order_items", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transactions", Integer.class));
        assertTrue(cart.items(user).isEmpty());
    }

    @Test
    void rejectsQuantityAboveAvailableStockWithoutCreatingOrder() {
        long user = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, user);
        cart.add(user, variant, 2);
        jdbc.update("UPDATE variant_inventory SET quantity_on_hand=1 WHERE variant_id=?", variant);
        assertThrows(IllegalArgumentException.class, () -> checkout.placeOrder(user, List.of(variant), address, null, null, null, null, null, null));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
    }

    @Test
    void shopRejectionCancelsCodAndNotifiesCustomerWithoutTakingStock() {
        long owner = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, owner);
        cart.add(owner, variant, 2);
        String code = checkout.placeOrder(owner, List.of(variant), address, null, null, null, null, null, null);
        long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_code=?", Long.class, code);

        shopOrders.rejectCod(owner, orderId, "Tồn thực tế không đủ");

        assertEquals("CANCELLED", shopOrders.detail(owner, orderId).get("status"));
        assertEquals("Tồn thực tế không đủ", shopOrders.detail(owner, orderId).get("cancellation_reason"));
        assertEquals("RELEASED", jdbc.queryForObject("SELECT allocation_status FROM order_items WHERE order_id=?", String.class, orderId));
        assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND target_id=? AND title='Shop từ chối đơn hàng'", Integer.class, owner, orderId));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?", String.class, orderId));
    }

    @Test
    void successfulVnPayOrderIsConfirmedAndDeliveryConsumesHeldStock() {
        long customer = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, customer);
        cart.add(customer, variant, 2);
        var result = checkout.placeOrder(customer, List.of(variant), address, null, null, null, null, null, null, "VNPAY");
        long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_code=?", Long.class, result.orderCodes().get(0));
        String checkoutCode = result.checkoutCode();

        assertEquals(2, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
        assertEquals("RESERVED", jdbc.queryForObject("SELECT allocation_status FROM order_items WHERE order_id=?", String.class, orderId));
        checkout.handleVnPayCallback(checkoutCode, true);
        checkout.handleVnPayCallback(checkoutCode, true);
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId));
        assertEquals("PAID", jdbc.queryForObject("SELECT payment_status FROM orders WHERE id=?", String.class, orderId));
        assertThrows(IllegalArgumentException.class, () -> shopOrders.rejectCod(customer, orderId, "Không thể từ chối đơn online đã trả tiền"));
        assertEquals(2, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));

        deliveryOrders.updateDeliveryStatus(orderId, "SHIPPING", null);
        deliveryOrders.updateDeliveryStatus(orderId, "COMPLETED", null);
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId));
        assertEquals("CONSUMED", jdbc.queryForObject("SELECT allocation_status FROM order_items WHERE order_id=?", String.class, orderId));
        assertEquals(18, jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
        assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
        assertEquals("SALE", jdbc.queryForObject("SELECT type FROM inventory_transactions WHERE order_item_id=(SELECT id FROM order_items WHERE order_id=?) ORDER BY id DESC FETCH FIRST 1 ROW ONLY", String.class, orderId));
    }

    @Test
    void failedOnlinePaymentReleasesReservationAndCannotBeReversedByLateSuccess() {
        long customer = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, customer);
        cart.add(customer, variant, 2);
        var result = checkout.placeOrder(customer, List.of(variant), address, null, null, null, null, null, null, "VNPAY");
        long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_code=?", Long.class, result.orderCodes().get(0));

        checkout.handleVnPayCallback(result.checkoutCode(), false);

        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId));
        assertEquals("RELEASED", jdbc.queryForObject("SELECT allocation_status FROM order_items WHERE order_id=?", String.class, orderId));
        assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variant));
        assertThrows(IllegalArgumentException.class, () -> checkout.handleVnPayCallback(result.checkoutCode(), true));
        assertEquals("UNPAID", jdbc.queryForObject("SELECT payment_status FROM orders WHERE id=?", String.class, orderId));
    }

    @Test
    void unconfirmedCodOrderExpiresAfterThreeDaysAndReturnsToCustomer() {
        long customer = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, customer);
        cart.add(customer, variant, 1);
        String code = checkout.placeOrder(customer, List.of(variant), address, null, null, null, null, null, null);
        long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_code=?", Long.class, code);
        jdbc.update("UPDATE orders SET created_at=DATEADD('DAY',-4,CURRENT_TIMESTAMP) WHERE id=?", orderId);

        checkout.expirePendingOrders();

        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId));
        assertEquals("RELEASED", jdbc.queryForObject("SELECT allocation_status FROM order_items WHERE order_id=?", String.class, orderId));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?", String.class, orderId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND target_id=? AND title='Đơn hàng đã tự hủy'", Integer.class, customer, orderId));
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
        jdbc.update("INSERT INTO product_variants(product_id,shop_id,sku,name,base_unit_id,price,status,approval_status,reviewed_at) VALUES (?,?,?,?,?,?,'ACTIVE','APPROVED',CURRENT_TIMESTAMP)", product, secondShop, "SKU-CHECKOUT-2", "500g", unit, 30000);
        long secondVariant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT-2'", Long.class);
        jdbc.update("INSERT INTO variant_inventory(variant_id,initial_quantity,quantity_on_hand,created_by) VALUES (?,?,?,?)", secondVariant, 20, 20, owner);
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
        mvc.perform(get("/cart")).andExpect(status().isOk());
        mvc.perform(get("/forgot-password")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
        mvc.perform(get("/register/verify").sessionAttr("pendingRegistrationEmail", "user@test.example")).andExpect(status().isOk());
        mvc.perform(get("/shop/products")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void synchronizedUIAndBusinessFlowsValidation() throws Exception {
        long user = seed();
        long variant = jdbc.queryForObject("SELECT id FROM product_variants WHERE sku='SKU-CHECKOUT'", Long.class);
        long address = jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id=?", Long.class, user);

        // 1. Guest empty cart renders empty state
        mvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Giỏ hàng của bạn đang trống")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/css/cart.css\"")));

        // 2. Customer adds item to cart & views cart
        cart.add(user, variant, 2);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user);
        session.setAttribute("role", "CUSTOMER");
        session.setAttribute("fullName", "Checkout User");

        mvc.perform(get("/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Apple")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1kg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("btnCheckout")));

        // 3. Checkout page loads addresses, subtotal, and shipping fee
        mvc.perform(post("/checkout").session(session).param("selectedVariantIds", String.valueOf(variant)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/css/checkout.css\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Địa chỉ nhận hàng")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Phí vận chuyển")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("btnPlaceOrder")));

        // 4. Auth pages render with green brand styles
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/css/login.css\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Quên mật khẩu?")));

        mvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gửi mã OTP xác minh")));
    }

    @Test
    void shopOwnerProductScreensUseDashboardNavigationAndRealRoutes() throws Exception {
        long owner = seed();
        long productId = jdbc.queryForObject("SELECT id FROM products WHERE batch_code='BATCH-CHECKOUT'", Long.class);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", owner);
        session.setAttribute("role", "SHOP_OWNER");

        mvc.perform(get("/shopowner/home").session(session))
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"batchCode\"")))
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
