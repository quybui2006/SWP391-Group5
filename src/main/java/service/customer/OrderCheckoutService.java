package service.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class OrderCheckoutService {
    private static final BigDecimal SHIPPING_FEE = new BigDecimal("20000");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    public OrderCheckoutService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> items(Long userId, List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) return List.of();
        String marks = String.join(",", java.util.Collections.nCopies(variantIds.size(), "?"));
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Object[] args = new Object[variantIds.size() + 3]; args[0] = now; args[1] = now; args[2] = userId;
        for (int i = 0; i < variantIds.size(); i++) args[i + 3] = variantIds.get(i);
        return jdbc.queryForList("SELECT ci.variant_id,ci.quantity,pv.shop_id,s.name AS shop_name,p.name AS product_name,pv.name AS variant_name,pv.image_url,pv.price AS original_price, " +
                "COALESCE((SELECT pp.promotional_price FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 AND pp.starts_at <= ? AND pp.ends_at > ? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1),pv.price) AS price " +
                "FROM carts c JOIN cart_items ci ON ci.cart_id=c.id JOIN product_variants pv ON pv.id=ci.variant_id JOIN products p ON p.id=pv.product_id JOIN shops s ON s.id=pv.shop_id WHERE c.user_id=? AND ci.variant_id IN (" + marks + ")", args);
    }

    public List<Map<String, Object>> addresses(Long userId) {
        return jdbc.queryForList("SELECT ua.id,ua.recipient_name,ua.recipient_phone,ua.province_code,p.name AS province_name,ua.ward_name,ua.address_detail,ua.is_default FROM user_addresses ua JOIN provinces p ON p.code=ua.province_code WHERE ua.user_id=? ORDER BY ua.is_default DESC,ua.created_at DESC", userId);
    }

    public List<Map<String, Object>> provinces() {
        return jdbc.queryForList("SELECT code,name FROM provinces WHERE is_active=1 ORDER BY name");
    }

    @Transactional
    public String placeOrder(Long userId, List<Long> variantIds, Long addressId, String recipientName,
                             String recipientPhone, String provinceCode, String wardName, String addressDetail, String note) {
        if (variantIds == null || variantIds.isEmpty()) throw new IllegalArgumentException("Chưa chọn sản phẩm để đặt hàng.");
        Map<String, Object> address;
        if (addressId != null && addressId > 0) {
            List<Map<String, Object>> savedAddresses = jdbc.queryForList("SELECT ua.*,p.name AS province_name FROM user_addresses ua JOIN provinces p ON p.code=ua.province_code WHERE ua.id=? AND ua.user_id=?", addressId, userId);
            if (savedAddresses.isEmpty()) throw new IllegalArgumentException("Địa chỉ không tồn tại trong tài khoản này.");
            address = savedAddresses.get(0);
        } else {
            String name = required(recipientName, 150, "Tên người nhận");
            String phone = required(recipientPhone, 20, "Số điện thoại").replaceAll("[\\s.-]", "");
            String ward = required(wardName, 150, "Phường/xã");
            String detail = required(addressDetail, 500, "Địa chỉ cụ thể");
            if (!phone.matches("0[35789][0-9]{8}")) throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM provinces WHERE code=? AND is_active=1", Integer.class, provinceCode) == 0) throw new IllegalArgumentException("Hãy chọn tỉnh/thành hợp lệ.");
            addressId = insertId("INSERT INTO user_addresses(user_id,recipient_name,recipient_phone,province_code,ward_name,address_detail,is_default) VALUES(?,?,?,?,?,?,FALSE)", userId, name, phone, provinceCode, ward, detail);
            address = jdbc.queryForMap("SELECT ua.*,p.name AS province_name FROM user_addresses ua JOIN provinces p ON p.code=ua.province_code WHERE ua.id=? AND ua.user_id=?", addressId, userId);
        }
        Map<String, Object> user = jdbc.queryForMap("SELECT email FROM users WHERE id=?", userId);
        String marks = String.join(",", java.util.Collections.nCopies(variantIds.size(), "?"));
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Object[] args = new Object[variantIds.size() + 5]; args[0] = now; args[1] = now; args[2] = now; args[3] = now; args[4] = userId;
        for (int i = 0; i < variantIds.size(); i++) args[i + 5] = variantIds.get(i);
        // HẬU KIỂM: lấy cả sản phẩm đã bị tạm ngừng/từ chối để có thể chặn checkout
        // bằng tên cụ thể. FOR UPDATE khóa các dòng liên quan trong suốt lúc chốt đơn,
        // tránh Operations Manager từ chối đúng giữa bước kiểm tra và tạo đơn.
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT ci.variant_id,ci.quantity,pv.shop_id,pv.sku,pv.name AS variant_name,pv.status AS variant_status,pv.approval_status AS variant_approval_status,pv.price AS original_price, " +
                "COALESCE((SELECT pp.promotional_price FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 AND pp.starts_at <= ? AND pp.ends_at > ? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1),pv.price) AS price, " +
                "(SELECT pp.id FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 AND pp.starts_at <= ? AND pp.ends_at > ? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1) AS promotion_id, " +
                "pv.image_url,p.expiry_date,p.name AS product_name,u.name AS unit_name,vi.id AS inventory_id,vi.quantity_on_hand,vi.available_quantity, " +
                "s.province_code AS shop_province_code,s.name AS shop_name,s.approval_status AS shop_approval_status,s.operating_status AS shop_operating_status " +
                "FROM carts c JOIN cart_items ci ON ci.cart_id=c.id JOIN product_variants pv ON pv.id=ci.variant_id JOIN products p ON p.id=pv.product_id JOIN units u ON u.id=pv.base_unit_id JOIN variant_inventory vi ON vi.variant_id=pv.id JOIN shops s ON s.id=pv.shop_id WHERE c.user_id=? AND ci.variant_id IN (" + marks + ") FOR UPDATE", args);
        if (lines.size() != variantIds.stream().distinct().count()) throw new IllegalArgumentException("Một số sản phẩm không còn trong giỏ hàng. Vui lòng tải lại giỏ hàng.");
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            String productName = String.valueOf(line.get("product_name"));
            if (!("APPROVED".equals(line.get("variant_approval_status")) || "PENDING".equals(line.get("variant_approval_status")))) {
                throw new IllegalArgumentException("Biến thể của " + productName + " không còn đủ điều kiện bán.");
            }
            if (!"ACTIVE".equals(line.get("variant_status"))) {
                throw new IllegalArgumentException("Phân loại của sản phẩm " + productName + " hiện không còn mở bán.");
            }
            if (!"APPROVED".equals(line.get("shop_approval_status")) || !"OPEN".equals(line.get("shop_operating_status"))) {
                throw new IllegalArgumentException("Cửa hàng " + line.get("shop_name") + " hiện không hoạt động.");
            }
            int quantity = ((Number) line.get("quantity")).intValue();
            if (quantity > ((Number) line.get("available_quantity")).intValue()) throw new IllegalArgumentException("Số lượng tồn kho không đủ cho " + productName + ".");
            subtotal = subtotal.add(((BigDecimal) line.get("price")).multiply(BigDecimal.valueOf(quantity)));
        }
        Map<Long, List<Map<String, Object>>> shopLines = lines.stream().collect(java.util.stream.Collectors.groupingBy(
                line -> ((Number) line.get("shop_id")).longValue(), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
        BigDecimal total = subtotal.add(SHIPPING_FEE.multiply(BigDecimal.valueOf(shopLines.size())));
        String checkoutCode = code("CK-");
        Long checkoutId = insertId("INSERT INTO checkouts(checkout_code,request_key,user_id,contact_email,payment_method,total_amount,status) VALUES(?,?,?,?,'COD',?,'CONFIRMED')", checkoutCode, UUID.randomUUID().toString(), userId, user.get("email"), total);
        String insertOrder = "INSERT INTO orders(order_code,checkout_id,shop_id,recipient_name,recipient_phone,delivery_province_code,delivery_province_name,delivery_ward_name,delivery_address,shop_province_code,customer_note,subtotal,points_used,points_discount,points_subsidy_amount,qualifying_amount,points_earned,shipping_fee,total_amount,shop_revenue_amount,payment_method,delivery_code,status,payment_status,delivery_status) VALUES(?,?,?,?,?,?,?,?,?,?,?, ?,0,0,0,?,0,?,?,?,'COD',?,'PENDING','UNPAID','NOT_STARTED')";
        java.util.List<String> orderCodes = new java.util.ArrayList<>();
        for (var shopEntry : shopLines.entrySet()) {
            long shopId = shopEntry.getKey();
            List<Map<String, Object>> shopItems = shopEntry.getValue();
            BigDecimal shopSubtotal = shopItems.stream().map(line -> ((BigDecimal) line.get("price")).multiply(BigDecimal.valueOf(((Number) line.get("quantity")).longValue()))).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal shopTotal = shopSubtotal.add(SHIPPING_FEE);
            String orderCode = code("FF-");
            Long orderId = insertId(insertOrder, orderCode, checkoutId, shopId, address.get("recipient_name"), address.get("recipient_phone"), address.get("province_code"), address.get("province_name"), address.get("ward_name"), address.get("address_detail"), shopItems.get(0).get("shop_province_code"), note, shopSubtotal, shopSubtotal, SHIPPING_FEE, shopTotal, shopSubtotal, code("DL-"));
            orderCodes.add(orderCode);
            jdbc.update("INSERT INTO payments(payment_code,order_id,request_key,method,amount,status) VALUES(?,?,?,'COD',?,'PENDING')", code("PAY-"), orderId, UUID.randomUUID().toString(), shopTotal);
            for (Map<String, Object> line : shopItems) {
                Long variantId = ((Number) line.get("variant_id")).longValue();
                Long inventoryId = ((Number) line.get("inventory_id")).longValue();
                int quantity = ((Number) line.get("quantity")).intValue();
                BigDecimal price = (BigDecimal) line.get("price");
                insertId("INSERT INTO order_items(order_id,shop_id,variant_id,inventory_id,promotion_id,product_name,variant_name,sku,image_url,base_unit_name,quantity,unit_price,sale_unit_price,line_total,expiry_date,allocation_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'UNRESERVED')", orderId, shopId, variantId, inventoryId, line.get("promotion_id"), line.get("product_name"), line.get("variant_name"), line.get("sku"), line.get("image_url"), line.get("unit_name"), quantity, line.get("original_price"), price, price.multiply(BigDecimal.valueOf(quantity)), line.get("expiry_date"));
            }
        }
        Object[] deleteArgs = new Object[variantIds.size() + 1];
        deleteArgs[0] = userId;
        for (int i = 0; i < variantIds.size(); i++) {
            deleteArgs[i + 1] = variantIds.get(i);
        }


        jdbc.update("DELETE FROM cart_items WHERE cart_id=(SELECT id FROM carts WHERE user_id=?) AND variant_id IN (" + marks + ")", deleteArgs);

        return String.join(", ", orderCodes);
    }

    private String code(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(); }

    private String required(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) throw new IllegalArgumentException(label + " không được để trống.");
        return value.trim();
    }

    private Long insertId(String sql, Object... values) {
        org.springframework.jdbc.support.KeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            return ps;
        }, keys);


        if (!keys.getKeyList().isEmpty()) {
            java.util.Map<String, Object> firstRow = keys.getKeyList().get(0);
            for (java.util.Map.Entry<String, Object> entry : firstRow.entrySet()) {
                if ("id".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number n) {
                    return n.longValue();
                }
            }
            for (Object val : firstRow.values()) {
                if (val instanceof Number n) {
                    return n.longValue();
                }
            }
        }

        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Không lấy được mã bản ghi vừa tạo.");
        }
        return key.longValue();
    }
    // Thêm Record này vào trong class OrderCheckoutService để gói dữ liệu trả về
    public record CheckoutResult(String checkoutCode, BigDecimal totalAmount, List<String> orderCodes, String paymentMethod) {}

    @Transactional
    public CheckoutResult placeOrder(Long userId, List<Long> variantIds, Long addressId, String recipientName,
                                     String recipientPhone, String provinceCode, String wardName, String addressDetail,
                                     String note, String paymentMethod) {
        if (variantIds == null || variantIds.isEmpty()) throw new IllegalArgumentException("Chưa chọn sản phẩm để đặt hàng.");

        // Chuẩn hóa phương thức thanh toán
        String method = ("VNPAY".equalsIgnoreCase(paymentMethod)) ? "VNPAY" : "COD";

        Map<String, Object> address;
        if (addressId != null && addressId > 0) {
            List<Map<String, Object>> savedAddresses = jdbc.queryForList("SELECT ua.*,p.name AS province_name FROM user_addresses ua JOIN provinces p ON p.code=ua.province_code WHERE ua.id=? AND ua.user_id=?", addressId, userId);
            if (savedAddresses.isEmpty()) throw new IllegalArgumentException("Địa chỉ không tồn tại trong tài khoản này.");
            address = savedAddresses.get(0);
        } else {
            String name = required(recipientName, 150, "Tên người nhận");
            String phone = required(recipientPhone, 20, "Số điện thoại").replaceAll("[\\s.-]", "");
            String ward = required(wardName, 150, "Phường/xã");
            String detail = required(addressDetail, 500, "Địa chỉ cụ thể");
            if (!phone.matches("0[35789][0-9]{8}")) throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM provinces WHERE code=? AND is_active=1", Integer.class, provinceCode) == 0) throw new IllegalArgumentException("Hãy chọn tỉnh/thành hợp lệ.");
            addressId = insertId("INSERT INTO user_addresses(user_id,recipient_name,recipient_phone,province_code,ward_name,address_detail,is_default) VALUES(?,?,?,?,?,?,FALSE)", userId, name, phone, provinceCode, ward, detail);
            address = jdbc.queryForMap("SELECT ua.*,p.name AS province_name FROM user_addresses ua JOIN provinces p ON p.code=ua.province_code WHERE ua.id=? AND ua.user_id=?", addressId, userId);
        }

        Map<String, Object> user = jdbc.queryForMap("SELECT email FROM users WHERE id=?", userId);
        String marks = String.join(",", java.util.Collections.nCopies(variantIds.size(), "?"));
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Object[] args = new Object[variantIds.size() + 5]; args[0] = now; args[1] = now; args[2] = now; args[3] = now; args[4] = userId;
        for (int i = 0; i < variantIds.size(); i++) args[i + 5] = variantIds.get(i);

        List<Map<String, Object>> lines = jdbc.queryForList(
                "SELECT ci.variant_id, ci.quantity, pv.shop_id, pv.sku, " +
                        "pv.name AS variant_name, pv.price AS original_price, " +
                        "COALESCE((SELECT pp.promotional_price " +
                        "FROM promotional_prices pp " +
                        "WHERE pp.variant_id = pv.id AND pp.is_enabled = 1 " +
                        "AND pp.starts_at <= ? AND pp.ends_at > ? " +
                        "ORDER BY pp.starts_at DESC, pp.id DESC LIMIT 1), pv.price) AS price, " +
                        "(SELECT pp.id FROM promotional_prices pp " +
                        "WHERE pp.variant_id = pv.id AND pp.is_enabled = 1 " +
                        "AND pp.starts_at <= ? AND pp.ends_at > ? " +
                        "ORDER BY pp.starts_at DESC, pp.id DESC LIMIT 1) AS promotion_id, " +
                        "pv.image_url, p.expiry_date, p.name AS product_name, " +
                        "u.name AS unit_name, vi.id AS inventory_id, " +
                        "vi.quantity_on_hand, vi.available_quantity, " +
                        "s.province_code AS shop_province_code " +
                        "FROM carts c " +
                        "JOIN cart_items ci ON ci.cart_id = c.id " +
                        "JOIN product_variants pv ON pv.id = ci.variant_id " +
                        "JOIN products p ON p.id = pv.product_id " +
                        "JOIN units u ON u.id = pv.base_unit_id " +
                        "JOIN variant_inventory vi ON vi.variant_id = pv.id " +
                        "JOIN shops s ON s.id = pv.shop_id " +
                        "WHERE c.user_id = ? " +
                        "AND ci.variant_id IN (" + marks + ") " +
                        "AND pv.status = 'ACTIVE' " +
                        "AND pv.approval_status IN ('APPROVED', 'PENDING') " +
                        "AND s.approval_status = 'APPROVED' " +
                        "AND s.operating_status = 'OPEN' " +
                        "FOR UPDATE",
                args
        );

        if (lines.size() != variantIds.stream().distinct().count()) throw new IllegalArgumentException("Một số sản phẩm không còn trong giỏ hoặc không mở bán.");

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            int quantity = ((Number) line.get("quantity")).intValue();
            if (quantity > ((Number) line.get("available_quantity")).intValue()) throw new IllegalArgumentException("Số lượng tồn kho không đủ cho " + line.get("product_name") + ".");
            subtotal = subtotal.add(((BigDecimal) line.get("price")).multiply(BigDecimal.valueOf(quantity)));
        }

        Map<Long, List<Map<String, Object>>> shopLines = lines.stream().collect(java.util.stream.Collectors.groupingBy(
                line -> ((Number) line.get("shop_id")).longValue(), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
        BigDecimal total = subtotal.add(SHIPPING_FEE.multiply(BigDecimal.valueOf(shopLines.size())));

        String checkoutCode = code("CK-");
        String checkoutStatus = "VNPAY".equals(method) ? "PENDING" : "CONFIRMED";
        Long checkoutId = insertId("INSERT INTO checkouts(checkout_code,request_key,user_id,contact_email,payment_method,total_amount,status) VALUES(?,?,?,?,?,?,?)",
                checkoutCode, UUID.randomUUID().toString(), userId, user.get("email"), method, total, checkoutStatus);

        String insertOrder = "INSERT INTO orders(order_code,checkout_id,shop_id,recipient_name,recipient_phone,delivery_province_code,delivery_province_name,delivery_ward_name,delivery_address,shop_province_code,customer_note,subtotal,points_used,points_discount,points_subsidy_amount,qualifying_amount,points_earned,shipping_fee,total_amount,shop_revenue_amount,payment_method,payment_expires_at,delivery_code,status,payment_status,delivery_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0,0,0,?,0,?,?,?, ?,CASE WHEN ? IN ('VNPAY','MOCK_ONLINE') THEN DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 15 MINUTE) ELSE NULL END,?,'PENDING','UNPAID','NOT_STARTED')";

        java.util.List<String> orderCodes = new java.util.ArrayList<>();
        for (var shopEntry : shopLines.entrySet()) {
            long shopId = shopEntry.getKey();
            List<Map<String, Object>> shopItems = shopEntry.getValue();
            BigDecimal shopSubtotal = shopItems.stream().map(line -> ((BigDecimal) line.get("price")).multiply(BigDecimal.valueOf(((Number) line.get("quantity")).longValue()))).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal shopTotal = shopSubtotal.add(SHIPPING_FEE);
            String orderCode = code("FF-");

            Long orderId = insertId(insertOrder, orderCode, checkoutId, shopId, address.get("recipient_name"), address.get("recipient_phone"), address.get("province_code"), address.get("province_name"), address.get("ward_name"), address.get("address_detail"), shopItems.get(0).get("shop_province_code"), note, shopSubtotal, shopSubtotal, SHIPPING_FEE, shopTotal, shopSubtotal, method, method, code("DL-"));
            orderCodes.add(orderCode);

            jdbc.update("INSERT INTO payments(payment_code,order_id,request_key,method,amount,status) VALUES(?,?,?,?,?,'PENDING')",
                    code("PAY-"), orderId, UUID.randomUUID().toString(), method, shopTotal);
            notifyShopOwner(shopId, "ORDER_NEW_SHOP_" + orderId, "Có đơn hàng mới",
                    "Đơn " + orderCode + ("COD".equals(method) ? " chờ bạn xác nhận." : " đang chờ thanh toán online."), orderId);

            for (Map<String, Object> line : shopItems) {
                Long variantId = ((Number) line.get("variant_id")).longValue();
                Long inventoryId = ((Number) line.get("inventory_id")).longValue();
                int quantity = ((Number) line.get("quantity")).intValue();
                BigDecimal price = (BigDecimal) line.get("price");
                boolean reserveNow = "VNPAY".equals(method);
                if (reserveNow && jdbc.update("UPDATE variant_inventory SET reserved_quantity=reserved_quantity+? WHERE id=? AND quantity_on_hand-reserved_quantity>=?", quantity, inventoryId, quantity) != 1) throw new IllegalArgumentException("Tồn kho vừa thay đổi. Vui lòng kiểm tra lại giỏ hàng.");
                Long itemId = insertId("INSERT INTO order_items(order_id,shop_id,variant_id,inventory_id,promotion_id,product_name,variant_name,sku,image_url,base_unit_name,quantity,unit_price,sale_unit_price,line_total,expiry_date,allocation_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", orderId, shopId, variantId, inventoryId, line.get("promotion_id"), line.get("product_name"), line.get("variant_name"), line.get("sku"), line.get("image_url"), line.get("unit_name"), quantity, line.get("original_price"), price, price.multiply(BigDecimal.valueOf(quantity)), line.get("expiry_date"), reserveNow ? "RESERVED" : "UNRESERVED");
                if (reserveNow) {
                    int reservedAfter = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
                    int onHand = ((Number) line.get("quantity_on_hand")).intValue();
                    jdbc.update("INSERT INTO inventory_transactions(inventory_id,order_item_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,created_by) VALUES(?,?,'RESERVE',0,?,?,?,?,?)", inventoryId, itemId, quantity, onHand, reservedAfter, "reserve-" + itemId, userId);
                }
            }
        }

        Object[] deleteArgs = new Object[variantIds.size() + 1];
        deleteArgs[0] = userId;
        for (int i = 0; i < variantIds.size(); i++) {
            deleteArgs[i + 1] = variantIds.get(i);
        }
        jdbc.update("DELETE FROM cart_items WHERE cart_id=(SELECT id FROM carts WHERE user_id=?) AND variant_id IN (" + marks + ")", deleteArgs);

        return new CheckoutResult(checkoutCode, total, orderCodes, method);
    }

    /**
     * Hàm xử lý khi VNPay redirect kết quả về
     */
    @Transactional
    public List<String> handleVnPayCallback(String checkoutCode, boolean isSuccess) {
        List<Map<String, Object>> checkouts = jdbc.queryForList("SELECT id,payment_method FROM checkouts WHERE checkout_code=? FOR UPDATE", checkoutCode);
        if (checkouts.isEmpty()) throw new IllegalArgumentException("Không tìm thấy phiên thanh toán.");
        Long checkoutId = ((Number) checkouts.get(0).get("id")).longValue();
        if (!"VNPAY".equals(checkouts.get(0).get("payment_method"))) throw new IllegalArgumentException("Phiên này không sử dụng VNPay.");
        List<Map<String, Object>> orderRows = jdbc.queryForList("SELECT id,order_code,status,payment_status FROM orders WHERE checkout_id=? ORDER BY id FOR UPDATE", checkoutId);
        List<String> orderCodes = orderRows.stream().map(row -> String.valueOf(row.get("order_code"))).toList();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);

        if (isSuccess) {
            if (orderRows.stream().allMatch(row -> "CONFIRMED".equals(row.get("status")) && "PAID".equals(row.get("payment_status")))) return orderCodes;
            if (orderRows.isEmpty() || orderRows.stream().anyMatch(row -> !"PENDING".equals(row.get("status")) || !"UNPAID".equals(row.get("payment_status"))))
                throw new IllegalArgumentException("Đơn đã hết hạn hoặc đã được xử lý; không thể xác nhận thanh toán này.");
            jdbc.update("UPDATE checkouts SET status='CONFIRMED' WHERE id=? AND status='PENDING'", checkoutId);
            jdbc.update("UPDATE orders SET status='CONFIRMED',payment_status='PAID',paid_at=? WHERE checkout_id=? AND status='PENDING' AND payment_status='UNPAID'", now, checkoutId);
            jdbc.update("UPDATE payments SET status='SUCCEEDED',paid_at=? WHERE status='PENDING' AND order_id IN (SELECT id FROM orders WHERE checkout_id=?)", now, checkoutId);
            notifyCheckoutParties(checkoutId, "ORDER_PAID", "Đơn online đã thanh toán", "Thanh toán VNPay thành công; đơn đã được xác nhận.");
        } else {
            if (orderRows.isEmpty() || orderRows.stream().allMatch(row -> "CANCELLED".equals(row.get("status")))) return orderCodes;
            if (orderRows.stream().anyMatch(row -> !"PENDING".equals(row.get("status")) || !"UNPAID".equals(row.get("payment_status")))) return orderCodes;
            for (Map<String, Object> order : orderRows) {
                long orderId = ((Number) order.get("id")).longValue();
                releaseReservedItems(orderId, "vnpay-failed");
                jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=?,cancellation_reason='Thanh toán VNPay thất bại hoặc bị hủy' WHERE id=? AND status='PENDING'", now, orderId);
                jdbc.update("UPDATE payments SET status='FAILED',failure_reason='Khách hủy hoặc giao dịch thất bại' WHERE order_id=? AND status='PENDING'", orderId);
                notifyOrderCustomer(orderId, "ORDER_PAYMENT_FAILED", "Thanh toán không thành công", "Thanh toán cho đơn " + order.get("order_code") + " không thành công.");
            }
            jdbc.update("UPDATE checkouts SET status='CANCELLED' WHERE id=? AND status='PENDING'", checkoutId);
        }
        return orderCodes;
    }

    @Scheduled(fixedDelayString = "${orders.expiry-scan-ms:300000}")
    @Transactional
    public void expirePendingOrders() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime codCutoff = now.minusDays(3);
        List<Long> codIds = jdbc.queryForList("SELECT id FROM orders WHERE status='PENDING' AND payment_method='COD' AND created_at<=? ORDER BY id", Long.class, codCutoff);
        for (Long orderId : codIds) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.id,o.order_code,o.checkout_id FROM orders o WHERE o.id=? AND o.status='PENDING' AND o.payment_method='COD' AND o.created_at<=? FOR UPDATE", orderId, codCutoff);
            if (rows.isEmpty()) continue;
            Map<String, Object> order = rows.get(0);
            jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=?,cancellation_reason='Shop không xác nhận trong thời hạn 3 ngày' WHERE id=? AND status='PENDING'", now, orderId);
            jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE order_id=? AND allocation_status='UNRESERVED'", orderId);
            jdbc.update("UPDATE payments SET status='CANCELLED',failure_reason='Shop không xác nhận trong thời hạn 3 ngày' WHERE order_id=? AND status='PENDING'", orderId);
            updateCheckoutAfterCancel(((Number) order.get("checkout_id")).longValue());
            notifyOrderCustomer(orderId, "ORDER_COD_EXPIRED", "Đơn hàng đã tự hủy", "Shop chưa xác nhận đơn " + order.get("order_code") + " trong 3 ngày.");
        }

        List<Long> onlineIds = jdbc.queryForList("SELECT id FROM orders WHERE status='PENDING' AND payment_method IN ('VNPAY','MOCK_ONLINE') AND payment_status='UNPAID' AND payment_expires_at<=? ORDER BY id", Long.class, now);
        for (Long orderId : onlineIds) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,order_code,checkout_id FROM orders WHERE id=? AND status='PENDING' AND payment_method IN ('VNPAY','MOCK_ONLINE') AND payment_status='UNPAID' AND payment_expires_at<=? FOR UPDATE", orderId, now);
            if (rows.isEmpty()) continue;
            Map<String, Object> order = rows.get(0);
            releaseReservedItems(orderId, "payment-expired");
            jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=?,cancellation_reason='Phiên thanh toán online hết hạn' WHERE id=? AND status='PENDING'", now, orderId);
            jdbc.update("UPDATE payments SET status='EXPIRED',failure_reason='Phiên thanh toán hết hạn' WHERE order_id=? AND status='PENDING'", orderId);
            updateCheckoutAfterCancel(((Number) order.get("checkout_id")).longValue());
            notifyOrderCustomer(orderId, "ORDER_PAYMENT_EXPIRED", "Đơn hàng đã hết hạn thanh toán", "Phiên thanh toán của đơn " + order.get("order_code") + " đã hết hạn; tồn kho được trả lại.");
        }
    }

    private void releaseReservedItems(Long orderId, String eventPrefix) {
        List<Map<String, Object>> items = jdbc.queryForList("SELECT id,inventory_id,quantity FROM order_items WHERE order_id=? AND allocation_status='RESERVED' ORDER BY inventory_id FOR UPDATE", orderId);
        for (Map<String, Object> item : items) {
            long itemId = ((Number) item.get("id")).longValue();
            long inventoryId = ((Number) item.get("inventory_id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();
            if (jdbc.update("UPDATE variant_inventory SET reserved_quantity=reserved_quantity-? WHERE id=? AND reserved_quantity>=?", quantity, inventoryId, quantity) != 1)
                throw new IllegalStateException("Không thể trả tồn kho đã giữ cho mặt hàng " + itemId + ".");
            jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE id=? AND allocation_status='RESERVED'", itemId);
            int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            jdbc.update("INSERT IGNORE INTO inventory_transactions(inventory_id,order_item_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,created_by) VALUES(?,?,'RELEASE',0,?,?,?,?,NULL)", inventoryId, itemId, -quantity, onHand, reserved, eventPrefix + "-" + itemId);
        }
    }

    private void updateCheckoutAfterCancel(Long checkoutId) {
        Integer remaining = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE checkout_id=? AND status<>'CANCELLED'", Integer.class, checkoutId);
        jdbc.update("UPDATE checkouts SET status=? WHERE id=? AND status IN ('PENDING','CONFIRMED','PARTIAL')", remaining != null && remaining == 0 ? "CANCELLED" : "PARTIAL", checkoutId);
    }

    private void notifyOrderCustomer(Long orderId, String eventSuffix, String title, String content) {
        jdbc.update("INSERT IGNORE INTO notifications(user_id,event_key,type,title,content,target_type,target_id) SELECT c.user_id,CONCAT(?, '_',o.id),'ORDER',?,?,'ORDER',o.id FROM orders o JOIN checkouts c ON c.id=o.checkout_id WHERE o.id=? AND c.user_id IS NOT NULL", eventSuffix, title, content, orderId);
    }

    private void notifyCheckoutParties(Long checkoutId, String eventSuffix, String title, String content) {
        jdbc.update("INSERT IGNORE INTO notifications(user_id,event_key,type,title,content,target_type,target_id) SELECT c.user_id,CONCAT(?, '_',o.id),'ORDER',?,?,'ORDER',o.id FROM orders o JOIN checkouts c ON c.id=o.checkout_id WHERE o.checkout_id=? AND c.user_id IS NOT NULL", eventSuffix, title, content, checkoutId);
        jdbc.update("INSERT IGNORE INTO notifications(user_id,event_key,type,title,content,target_type,target_id) SELECT s.owner_id,CONCAT(?, '_',o.id),'ORDER',?,?,'ORDER',o.id FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.checkout_id=?", eventSuffix + "_SHOP", title, content, checkoutId);
    }

    private void notifyShopOwner(Long shopId, String eventKey, String title, String content, Long orderId) {
        jdbc.update("INSERT IGNORE INTO notifications(user_id,event_key,type,title,content,target_type,target_id) SELECT owner_id,?,'ORDER',?,?,'ORDER',? FROM shops WHERE id=?", eventKey, title, content, orderId, shopId);
    }
}
