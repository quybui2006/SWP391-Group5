package service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderCheckoutService {
    private static final BigDecimal SHIPPING_FEE = new BigDecimal("20000");
    private final JdbcTemplate jdbc;
    public OrderCheckoutService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> items(Long userId, List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) return List.of();
        String marks = String.join(",", java.util.Collections.nCopies(variantIds.size(), "?"));
        Object[] args = new Object[variantIds.size() + 1]; args[0] = userId;
        for (int i = 0; i < variantIds.size(); i++) args[i + 1] = variantIds.get(i);
        return jdbc.queryForList("SELECT ci.variant_id,ci.quantity,pv.shop_id,s.name AS shop_name,p.name AS product_name,pv.name AS variant_name,pv.image_url,pv.price FROM carts c JOIN cart_items ci ON ci.cart_id=c.id JOIN product_variants pv ON pv.id=ci.variant_id JOIN products p ON p.id=pv.product_id JOIN shops s ON s.id=pv.shop_id WHERE c.user_id=? AND ci.variant_id IN (" + marks + ")", args);
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
        Object[] args = new Object[variantIds.size() + 1]; args[0] = userId;
        for (int i = 0; i < variantIds.size(); i++) args[i + 1] = variantIds.get(i);
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT ci.variant_id,ci.quantity,pv.shop_id,pv.sku,pv.name AS variant_name,pv.price,pv.image_url,p.expiry_date,p.name AS product_name,u.name AS unit_name,vi.id AS inventory_id,vi.quantity_on_hand,vi.available_quantity,s.province_code AS shop_province_code FROM carts c JOIN cart_items ci ON ci.cart_id=c.id JOIN product_variants pv ON pv.id=ci.variant_id JOIN products p ON p.id=pv.product_id JOIN units u ON u.id=pv.base_unit_id JOIN variant_inventory vi ON vi.variant_id=pv.id JOIN shops s ON s.id=pv.shop_id WHERE c.user_id=? AND ci.variant_id IN (" + marks + ") AND pv.status='ACTIVE' AND p.selling_status='ACTIVE' AND s.approval_status='APPROVED' AND s.operating_status='OPEN' FOR UPDATE", args);
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
                if (jdbc.update("UPDATE variant_inventory SET reserved_quantity=reserved_quantity+? WHERE id=? AND quantity_on_hand-reserved_quantity>=?", quantity, inventoryId, quantity) != 1) throw new IllegalArgumentException("Tồn kho vừa thay đổi. Vui lòng kiểm tra lại giỏ hàng.");
                Long itemId = insertId("INSERT INTO order_items(order_id,shop_id,variant_id,inventory_id,product_name,variant_name,sku,image_url,base_unit_name,quantity,unit_price,sale_unit_price,line_total,expiry_date,allocation_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'RESERVED')", orderId, shopId, variantId, inventoryId, line.get("product_name"), line.get("variant_name"), line.get("sku"), line.get("image_url"), line.get("unit_name"), quantity, price, price, price.multiply(BigDecimal.valueOf(quantity)), line.get("expiry_date"));
                int reservedAfter = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
                int onHand = ((Number) line.get("quantity_on_hand")).intValue();
                jdbc.update("INSERT INTO inventory_transactions(inventory_id,order_item_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,created_by) VALUES(?,?,'RESERVE',0,?,?,?,?,?)", inventoryId, itemId, quantity, onHand, reservedAfter, "reserve-" + itemId, userId);
            }
        }
        jdbc.update("DELETE FROM cart_items WHERE cart_id=(SELECT id FROM carts WHERE user_id=?) AND variant_id IN (" + marks + ")", args);
        return String.join(", ", orderCodes);
    }

    private String code(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(); }

    private String required(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) throw new IllegalArgumentException(label + " không được để trống.");
        return value.trim();
    }

    private Long insertId(String sql, Object... values) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
            return ps;
        }, keys);
        Object generatedId = keys.getKeyList().get(0).entrySet().stream()
                .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        Number key = generatedId instanceof Number number ? number : null;
        if (key == null) throw new IllegalStateException("Không lấy được mã bản ghi vừa tạo.");
        return key.longValue();
    }
}
