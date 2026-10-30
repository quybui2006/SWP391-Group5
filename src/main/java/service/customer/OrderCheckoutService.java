package service.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import service.vnpay.VNPayService;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final VNPayService vnPayService; // Khai báo VNPayService

    // Tiêm VNPayService vào constructor
    public OrderCheckoutService(JdbcTemplate jdbc, VNPayService vnPayService) {
        this.jdbc = jdbc;
        this.vnPayService = vnPayService;
    }

    public List<Map<String, Object>> items(Long userId, List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) return List.of();
        String marks = String.join(",", java.util.Collections.nCopies(variantIds.size(), "?"));
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Object[] args = new Object[variantIds.size() + 3]; args[0] = now; args[1] = now; args[2] = userId;
        for (int i = 0; i < variantIds.size(); i++) args[i + 3] = variantIds.get(i);
        return jdbc.queryForList("SELECT ci.variant_id,ci.quantity,pv.shop_id,s.name AS shop_name,p.name AS product_name,pv.name AS variant_name,pv.name AS name,pv.image_url,pv.price AS original_price, " +
                "COALESCE((SELECT pp.promotional_price FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 AND pp.starts_at <= ? AND pp.ends_at > ? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1),pv.price) AS price " +
                "FROM carts c JOIN cart_items ci ON ci.cart_id=c.id JOIN product_variants pv ON pv.id=ci.variant_id JOIN products p ON p.id=pv.product_id JOIN shops s ON s.id=pv.shop_id WHERE c.user_id=? AND ci.variant_id IN (" + marks + ") AND p.is_visible=1 AND pv.status='ACTIVE' AND pv.approval_status IN ('APPROVED','PENDING') AND s.approval_status='APPROVED' AND s.operating_status='OPEN'", args);
    }

    public List<Map<String, Object>> guestItems(Map<Long, Integer> guestCart, List<Long> variantIds) {
        if (guestCart == null || variantIds == null || variantIds.isEmpty()) return List.of();
        List<Long> selected = variantIds.stream().distinct().filter(guestCart::containsKey).toList();
        if (selected.isEmpty()) return List.of();
        String marks = String.join(",", java.util.Collections.nCopies(selected.size(), "?"));
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Object[] args = new Object[selected.size() + 2];
        args[0] = now; args[1] = now;
        for (int i = 0; i < selected.size(); i++) args[i + 2] = selected.get(i);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT pv.id AS variant_id,pv.shop_id,s.name AS shop_name,p.name AS product_name,pv.name AS variant_name,pv.name AS name,pv.image_url,pv.price AS original_price, " +
                "COALESCE((SELECT pp.promotional_price FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 AND pp.starts_at<=? AND pp.ends_at>? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1),pv.price) AS price,vi.available_quantity " +
                "FROM product_variants pv JOIN products p ON p.id=pv.product_id JOIN shops s ON s.id=pv.shop_id JOIN variant_inventory vi ON vi.variant_id=pv.id " +
                "WHERE pv.id IN (" + marks + ") AND p.is_visible=1 AND pv.status='ACTIVE' AND pv.approval_status IN ('APPROVED','PENDING') AND s.approval_status='APPROVED' AND s.operating_status='OPEN'", args);
        rows.forEach(row -> row.put("quantity", guestCart.get(((Number) row.get("variant_id")).longValue())));
        return rows;
    }

    public record VoucherQuote(Long voucherId, String code, BigDecimal discount, String error) {}

    public VoucherQuote previewVoucher(Long shopId, String rawCode, BigDecimal shopSubtotal) {
        if (rawCode == null || rawCode.isBlank()) return new VoucherQuote(null, "", BigDecimal.ZERO, null);
        try {
            return quoteVoucher(shopId, rawCode, shopSubtotal, false);
        } catch (IllegalArgumentException ex) {
            return new VoucherQuote(null, rawCode.trim().toUpperCase(java.util.Locale.ROOT), BigDecimal.ZERO, ex.getMessage());
        }
    }

    private VoucherQuote quoteVoucher(Long shopId, String rawCode, BigDecimal shopSubtotal, boolean lock) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isBlank()) return new VoucherQuote(null, "", BigDecimal.ZERO, null);
        String lockClause = lock ? " FOR UPDATE" : "";
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,code,discount_type,discount_value,max_discount_amount,min_order_value,max_usages,used_count,status,starts_at,ends_at " +
                "FROM vouchers WHERE shop_id=? AND code=? AND is_public=1" + lockClause, shopId, code);
        if (rows.isEmpty()) throw new IllegalArgumentException("Mã voucher không thuộc cửa hàng này.");
        Map<String, Object> v = rows.get(0);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime startsAt = toLocalDateTime(v.get("starts_at"));
        LocalDateTime endsAt = toLocalDateTime(v.get("ends_at"));
        if (!"ACTIVE".equals(v.get("status")) || startsAt.isAfter(now) || !endsAt.isAfter(now)) {
            throw new IllegalArgumentException("Voucher chưa có hiệu lực hoặc đã hết hạn.");
        }
        if (((Number) v.get("used_count")).intValue() >= ((Number) v.get("max_usages")).intValue())
            throw new IllegalArgumentException("Voucher đã hết lượt sử dụng.");
        if (shopSubtotal.compareTo((BigDecimal) v.get("min_order_value")) < 0)
            throw new IllegalArgumentException("Đơn hàng của shop chưa đạt giá trị tối thiểu của voucher.");
        BigDecimal discountValue = (BigDecimal) v.get("discount_value");
        BigDecimal discount = "PERCENTAGE".equals(v.get("discount_type"))
                ? shopSubtotal.multiply(discountValue).divide(new BigDecimal("100"), 0, RoundingMode.DOWN)
                : discountValue;
        BigDecimal maxDiscount = (BigDecimal) v.get("max_discount_amount");
        if (maxDiscount != null) discount = discount.min(maxDiscount);
        discount = discount.min(shopSubtotal).max(BigDecimal.ZERO).setScale(0, RoundingMode.DOWN);
        return new VoucherQuote(((Number) v.get("id")).longValue(), code, discount, null);
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
        return placeOrder(userId, variantIds, addressId, recipientName, recipientPhone, provinceCode, wardName, addressDetail, note, "COD").checkoutCode();
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

    public record CheckoutResult(String checkoutCode, BigDecimal totalAmount, List<String> orderCodes, String paymentMethod) {}
    public record GuestCheckoutResult(CheckoutResult checkout, String guestAccessToken) {}

    @Transactional
    public CheckoutResult placeOrder(Long userId, List<Long> variantIds, Long addressId, String recipientName,
                                     String recipientPhone, String provinceCode, String wardName, String addressDetail,
                                     String note, String paymentMethod) {
        return placeOrder(userId, variantIds, addressId, recipientName, recipientPhone,
                provinceCode, wardName, addressDetail, note, paymentMethod, Map.of());
    }

    @Transactional
    public CheckoutResult placeOrder(Long userId, List<Long> variantIds, Long addressId, String recipientName,
                                     String recipientPhone, String provinceCode, String wardName, String addressDetail,
                                     String note, String paymentMethod, Map<Long, String> voucherCodes) {
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

        List<Map<String, Object>> lines = jdbc.queryForList("SELECT ci.variant_id,ci.quantity,pv.shop_id,pv.sku,pv.name AS variant_name,pv.price AS original_price, " +
                "COALESCE((SELECT pp.promotional_price FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 AND pp.starts_at <= ? AND pp.ends_at > ? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1),pv.price) AS price, " +
                "(SELECT pp.id FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 AND pp.starts_at <= ? AND pp.ends_at > ? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1) AS promotion_id, " +
                "pv.image_url,p.name AS product_name,u.name AS unit_name,vi.id AS inventory_id,vi.quantity_on_hand,vi.available_quantity,s.province_code AS shop_province_code " +
                "FROM carts c JOIN cart_items ci ON ci.cart_id=c.id JOIN product_variants pv ON pv.id=ci.variant_id JOIN products p ON p.id=pv.product_id JOIN units u ON u.id=pv.base_unit_id JOIN variant_inventory vi ON vi.variant_id=pv.id JOIN shops s ON s.id=pv.shop_id WHERE c.user_id=? AND ci.variant_id IN (" + marks + ") AND p.is_visible=1 AND pv.status='ACTIVE' AND pv.approval_status IN ('APPROVED','PENDING') AND s.approval_status='APPROVED' AND s.operating_status='OPEN' FOR UPDATE", args);

        if (lines.size() != variantIds.stream().distinct().count()) throw new IllegalArgumentException("Một số sản phẩm không còn trong giỏ hoặc không mở bán.");

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            int quantity = ((Number) line.get("quantity")).intValue();
            if (quantity > ((Number) line.get("available_quantity")).intValue()) throw new IllegalArgumentException("Số lượng tồn kho không đủ cho " + line.get("product_name") + ".");
            subtotal = subtotal.add(((BigDecimal) line.get("price")).multiply(BigDecimal.valueOf(quantity)));
        }

        Map<Long, List<Map<String, Object>>> shopLines = lines.stream().collect(java.util.stream.Collectors.groupingBy(
                line -> ((Number) line.get("shop_id")).longValue(), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
        Map<Long, VoucherQuote> voucherQuotes = new java.util.LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var entry : shopLines.entrySet()) {
            BigDecimal shopSubtotal = sumLines(entry.getValue());
            VoucherQuote quote = quoteVoucher(entry.getKey(), voucherCodes == null ? null : voucherCodes.get(entry.getKey()), shopSubtotal, true);
            voucherQuotes.put(entry.getKey(), quote);
            total = total.add(shopSubtotal.subtract(quote.discount()).add(SHIPPING_FEE));
        }

        String checkoutCode = code("CK-");
        String checkoutStatus = "VNPAY".equals(method) ? "PENDING" : "CONFIRMED";
        Long checkoutId = insertId("INSERT INTO checkouts(checkout_code,request_key,user_id,contact_email,payment_method,total_amount,status) VALUES(?,?,?,?,?,?,?)",
                checkoutCode, UUID.randomUUID().toString(), userId, user.get("email"), method, total, checkoutStatus);

        String insertOrder = "INSERT INTO orders(order_code,checkout_id,shop_id,recipient_name,recipient_phone,delivery_province_code,delivery_province_name,delivery_ward_name,delivery_address,shop_province_code,customer_note,subtotal,points_used,points_discount,points_subsidy_amount,qualifying_amount,points_earned,shipping_fee,total_amount,shop_revenue_amount,voucher_id,voucher_code,voucher_discount,payment_method,payment_expires_at,delivery_code,status,payment_status,delivery_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0,0,0,?,0,?,?,?,?,?,?,?,CASE WHEN ? IN ('VNPAY','MOCK_ONLINE') THEN DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 15 MINUTE) ELSE NULL END,?,'PENDING','UNPAID','NOT_STARTED')";

        java.util.List<String> orderCodes = new java.util.ArrayList<>();
        for (var shopEntry : shopLines.entrySet()) {
            long shopId = shopEntry.getKey();
            List<Map<String, Object>> shopItems = shopEntry.getValue();
            BigDecimal shopSubtotal = sumLines(shopItems);
            VoucherQuote voucher = voucherQuotes.get(shopId);
            BigDecimal shopTotal = shopSubtotal.subtract(voucher.discount()).add(SHIPPING_FEE);
            String orderCode = code("FF-");

            Long orderId = insertId(insertOrder, orderCode, checkoutId, shopId, address.get("recipient_name"), address.get("recipient_phone"), address.get("province_code"), address.get("province_name"), address.get("ward_name"), address.get("address_detail"), shopItems.get(0).get("shop_province_code"), note, shopSubtotal, shopSubtotal, SHIPPING_FEE, shopTotal, shopSubtotal.subtract(voucher.discount()), voucher.voucherId(), voucher.voucherId() == null ? null : voucher.code(), voucher.discount(), method, method, code("DL-"));
            countVoucherUse(voucher);
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
                Long itemId = insertId("INSERT INTO order_items(order_id,shop_id,variant_id,inventory_id,promotion_id,product_name,variant_name,sku,image_url,base_unit_name,quantity,unit_price,sale_unit_price,line_total,allocation_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", orderId, shopId, variantId, inventoryId, line.get("promotion_id"), line.get("product_name"), line.get("variant_name"), line.get("sku"), line.get("image_url"), line.get("unit_name"), quantity, line.get("original_price"), price, price.multiply(BigDecimal.valueOf(quantity)), reserveNow ? "RESERVED" : "UNRESERVED");
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

    @Transactional
    public GuestCheckoutResult placeGuestOrder(Map<Long, Integer> guestCart, List<Long> variantIds, String email,
                                               String recipientName, String recipientPhone, String provinceCode,
                                               String wardName, String addressDetail, String note,
                                               Map<Long, String> voucherCodes) {
        return placeGuestOrder(guestCart, variantIds, email, recipientName, recipientPhone, provinceCode,
                wardName, addressDetail, note, voucherCodes, Map.of());
    }

    @Transactional
    public GuestCheckoutResult placeGuestOrder(Map<Long, Integer> guestCart, List<Long> variantIds, String email,
                                               String recipientName, String recipientPhone, String provinceCode,
                                               String wardName, String addressDetail, String note,
                                               Map<Long, String> voucherCodes, Map<Long, String> cashbackCodes) {
        if (guestCart == null || variantIds == null || variantIds.isEmpty()) throw new IllegalArgumentException("Chưa chọn sản phẩm để đặt hàng.");
        String contactEmail = required(email, 254, "Email").toLowerCase(java.util.Locale.ROOT);
        if (!contactEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Email không hợp lệ.");
        String name = required(recipientName, 150, "Tên người nhận");
        String phone = required(recipientPhone, 20, "Số điện thoại").replaceAll("[\\s.-]", "");
        String ward = required(wardName, 150, "Phường/xã");
        String detail = required(addressDetail, 500, "Địa chỉ cụ thể");
        if (!phone.matches("0[35789][0-9]{8}")) throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
        Map<String, Object> province = jdbc.queryForMap("SELECT code,name FROM provinces WHERE code=? AND is_active=1", provinceCode);

        List<Long> selected = variantIds.stream().distinct().filter(guestCart::containsKey).toList();
        if (selected.size() != variantIds.stream().distinct().count()) throw new IllegalArgumentException("Một số sản phẩm không còn trong giỏ. Vui lòng tải lại.");
        String marks = String.join(",", java.util.Collections.nCopies(selected.size(), "?"));
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        Object[] args = new Object[selected.size() + 4]; args[0] = now; args[1] = now; args[2] = now; args[3] = now;
        for (int i = 0; i < selected.size(); i++) args[i + 4] = selected.get(i);
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT pv.id AS variant_id,pv.shop_id,pv.sku,pv.name AS variant_name,pv.price AS original_price, " +
                "COALESCE((SELECT pp.promotional_price FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 AND pp.starts_at<=? AND pp.ends_at>? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1),pv.price) AS price, " +
                "(SELECT pp.id FROM promotional_prices pp WHERE pp.variant_id=pv.id AND pp.is_enabled=1 AND pp.starts_at<=? AND pp.ends_at>? ORDER BY pp.starts_at DESC,pp.id DESC LIMIT 1) AS promotion_id, " +
                "pv.image_url,p.name AS product_name,u.name AS unit_name,vi.id AS inventory_id,vi.quantity_on_hand,vi.available_quantity,s.province_code AS shop_province_code,s.name AS shop_name " +
                "FROM product_variants pv JOIN products p ON p.id=pv.product_id JOIN units u ON u.id=pv.base_unit_id JOIN variant_inventory vi ON vi.variant_id=pv.id JOIN shops s ON s.id=pv.shop_id " +
                "WHERE pv.id IN (" + marks + ") AND p.is_visible=1 AND pv.status='ACTIVE' AND pv.approval_status IN ('APPROVED','PENDING') AND s.approval_status='APPROVED' AND s.operating_status='OPEN' FOR UPDATE", args);
        if (lines.size() != selected.size()) throw new IllegalArgumentException("Một số sản phẩm đã ngừng bán hoặc bị ẩn.");
        for (Map<String, Object> line : lines) {
            int quantity = guestCart.get(((Number) line.get("variant_id")).longValue());
            if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("Số lượng trong giỏ không hợp lệ.");
            if (quantity > ((Number) line.get("available_quantity")).intValue()) throw new IllegalArgumentException("Số lượng tồn kho không đủ cho " + line.get("product_name") + ".");
            line.put("quantity", quantity);
        }
        Map<Long, List<Map<String, Object>>> shopLines = lines.stream().collect(java.util.stream.Collectors.groupingBy(
                line -> ((Number) line.get("shop_id")).longValue(), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
        Map<Long, VoucherQuote> voucherQuotes = new java.util.LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var entry : shopLines.entrySet()) {
            BigDecimal shopSubtotal = sumLines(entry.getValue());
            VoucherQuote quote = quoteVoucher(entry.getKey(), voucherCodes == null ? null : voucherCodes.get(entry.getKey()), shopSubtotal, true);
            voucherQuotes.put(entry.getKey(), quote);
            total = total.add(shopSubtotal.subtract(quote.discount()).add(SHIPPING_FEE));
        }

        String checkoutCode = code("CK-");
        String accessToken = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
        Long checkoutId = insertId("INSERT INTO checkouts(checkout_code,request_key,user_id,contact_email,payment_method,total_amount,status,guest_access_token_hash,guest_access_expires_at) " +
                        "VALUES(?,?,NULL,?,'COD',?,'CONFIRMED',UNHEX(SHA2(?,256)),DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY))",
                checkoutCode, UUID.randomUUID().toString(), contactEmail, total, accessToken);
        String insertOrder = "INSERT INTO orders(order_code,checkout_id,shop_id,recipient_name,recipient_phone,delivery_province_code,delivery_province_name,delivery_ward_name,delivery_address,shop_province_code,customer_note,subtotal,points_used,points_discount,points_subsidy_amount,qualifying_amount,points_earned,shipping_fee,total_amount,shop_revenue_amount,voucher_id,voucher_code,voucher_discount,payment_method,payment_expires_at,delivery_code,status,payment_status,delivery_status) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?, ?,0,0,0,?,0,?,?,?,?,?,?,'COD',NULL,?,'PENDING','UNPAID','NOT_STARTED')";
        List<String> orderCodes = new java.util.ArrayList<>();
        for (var entry : shopLines.entrySet()) {
            long shopId = entry.getKey();
            List<Map<String, Object>> shopItems = entry.getValue();
            BigDecimal shopSubtotal = sumLines(shopItems);
            VoucherQuote voucher = voucherQuotes.get(shopId);
            BigDecimal shopTotal = shopSubtotal.subtract(voucher.discount()).add(SHIPPING_FEE);
            String orderCode = code("FF-");
            Long orderId = insertId(insertOrder, orderCode, checkoutId, shopId, name, phone, province.get("code"), province.get("name"), ward,
                    detail, shopItems.get(0).get("shop_province_code"), note, shopSubtotal, shopSubtotal, SHIPPING_FEE, shopTotal,
                    shopSubtotal.subtract(voucher.discount()), voucher.voucherId(), voucher.voucherId() == null ? null : voucher.code(),
                    voucher.discount(), code("DL-"));
            countVoucherUse(voucher);
            orderCodes.add(orderCode);
            jdbc.update("INSERT INTO payments(payment_code,order_id,request_key,method,amount,status) VALUES(?,?,?,'COD',?,'PENDING')",
                    code("PAY-"), orderId, UUID.randomUUID().toString(), shopTotal);
            notifyShopOwner(shopId, "ORDER_NEW_SHOP_" + orderId, "Có đơn hàng mới", "Đơn " + orderCode + " chờ bạn xác nhận.", orderId);
            for (Map<String, Object> line : shopItems) {
                Long variantId = ((Number) line.get("variant_id")).longValue();
                Long inventoryId = ((Number) line.get("inventory_id")).longValue();
                int quantity = ((Number) line.get("quantity")).intValue();
                BigDecimal price = (BigDecimal) line.get("price");
                insertId("INSERT INTO order_items(order_id,shop_id,variant_id,inventory_id,promotion_id,product_name,variant_name,sku,image_url,base_unit_name,quantity,unit_price,sale_unit_price,line_total,allocation_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'UNRESERVED')",
                        orderId, shopId, variantId, inventoryId, line.get("promotion_id"), line.get("product_name"), line.get("variant_name"), line.get("sku"),
                        line.get("image_url"), line.get("unit_name"), quantity, line.get("original_price"), price, price.multiply(BigDecimal.valueOf(quantity)));
            }
        }
        return new GuestCheckoutResult(new CheckoutResult(checkoutCode, total, orderCodes, "COD"), accessToken);
    }

    private BigDecimal sumLines(List<Map<String, Object>> lines) {
        return lines.stream().map(line -> ((BigDecimal) line.get("price"))
                        .multiply(BigDecimal.valueOf(((Number) line.get("quantity")).longValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalStateException("Kiểu dữ liệu thời gian voucher không được hỗ trợ: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private void countVoucherUse(VoucherQuote quote) {
        if (quote.voucherId() != null && jdbc.update("UPDATE vouchers SET used_count=used_count+1 WHERE id=? AND status='ACTIVE' AND used_count<max_usages", quote.voucherId()) != 1) {
            throw new IllegalArgumentException("Voucher vừa hết lượt sử dụng. Hãy thử mã khác.");
        }
    }

    @Transactional
    public void creditCashbackPoints(Long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT cashback_points FROM orders WHERE id=? AND status='COMPLETED' AND cashback_points>0 AND cashback_credited_at IS NULL FOR UPDATE",
                orderId);
        if (rows.isEmpty()) return;

        long points = ((Number) rows.get(0).get("cashback_points")).longValue();
        Long userId = jdbc.queryForObject(
                "SELECT c.user_id FROM orders o JOIN checkouts c ON c.id=o.checkout_id WHERE o.id=? AND c.user_id IS NOT NULL",
                Long.class, orderId);
        if (userId != null) {
            jdbc.update("UPDATE users SET points_balance=COALESCE(points_balance,0)+? WHERE id=?", points, userId);
            jdbc.update("INSERT INTO customer_coin_transactions(user_id,amount,type,status,source_type,source_id,event_key,description,expires_at) VALUES(?,?,'RECEIVED','ACTIVE','CASHBACK_VOUCHER',?,?,?,DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 MONTH))",
                    userId, points, orderId, "cashback-order-" + orderId, "Hoàn xu từ đơn hàng " + orderId);
            jdbc.update("UPDATE orders SET cashback_credited_at=CURRENT_TIMESTAMP(3) WHERE id=? AND cashback_credited_at IS NULL", orderId);
        }
    }

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
            for (Map<String, Object> order : orderRows) {
                recordConfirmedSales(((Number) order.get("id")).longValue(), String.valueOf(order.get("order_code")));
            }
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

    // =========================================================================================
    // KHÁCH HÀNG TỰ HỦY ĐƠN (CÓ HỖ TRỢ HOÀN TIỀN VNPAY)
    // =========================================================================================
    @Transactional
    public void cancelMyOrder(Long userId, Long orderId, String reason) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT o.*, c.user_id 
            FROM orders o 
            JOIN checkouts c ON o.checkout_id = c.id 
            WHERE o.id = ? AND c.user_id = ? FOR UPDATE
        """, orderId, userId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng hoặc bạn không có quyền thao tác.");
        }

        Map<String, Object> order = rows.get(0);
        String currentStatus = order.get("status") != null ? String.valueOf(order.get("status")) : "";
        String paymentMethod = order.get("payment_method") != null ? String.valueOf(order.get("payment_method")) : "COD";
        String paymentStatus = order.get("payment_status") != null ? String.valueOf(order.get("payment_status")) : "UNPAID";

        if (!"PENDING".equals(currentStatus)) {
            throw new IllegalStateException("Bạn chỉ có thể tự hủy đơn khi Shop chưa xác nhận.");
        }

        String cleanReason = (reason == null || reason.isBlank()) ? "Khách hàng đổi ý / Tự hủy đơn" : reason.trim();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);

        // 1. Nhả tồn kho (Sử dụng luôn hàm có sẵn trong class)
        releaseReservedItems(orderId, "customer-cancel");

        // 2. Xử lý VNPay Refund
        if ("VNPAY".equalsIgnoreCase(paymentMethod) && "PAID".equalsIgnoreCase(paymentStatus)) {
            BigDecimal refundAmount = order.get("total_amount") != null ? new BigDecimal(order.get("total_amount").toString()) : BigDecimal.ZERO;
            String orderCode = String.valueOf(order.get("order_code"));

            String txnRef = orderCode;
            String transactionNo = "";
            String transDateStr = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            boolean isFullRefund = true;

            Long checkoutId = ((Number) order.get("checkout_id")).longValue();
            Integer orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE checkout_id = ?", Integer.class, checkoutId);
            if (orderCount != null && orderCount > 1) {
                isFullRefund = false;
            }

            try {
                List<Map<String, Object>> payRows = jdbc.queryForList("SELECT * FROM payments WHERE order_id = ? ORDER BY id DESC LIMIT 1", orderId);
                if (!payRows.isEmpty()) {
                    Map<String, Object> pay = payRows.get(0);
                    txnRef = pay.get("provider_txn_ref") != null ? String.valueOf(pay.get("provider_txn_ref")) : (pay.get("txn_ref") != null ? String.valueOf(pay.get("txn_ref")) : orderCode);
                    transactionNo = pay.get("provider_transaction_no") != null ? String.valueOf(pay.get("provider_transaction_no")) : "";
                    if (pay.get("paid_at") instanceof java.sql.Timestamp ts) {
                        transDateStr = ts.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                    }
                }
            } catch (Exception ignored) {}

            var refundRes = vnPayService.refundTransaction(
                    txnRef, transactionNo, refundAmount, isFullRefund, transDateStr,
                    "Customer_" + userId, "127.0.0.1", "Khach hang tu huy don " + orderCode
            );

            String refundNote = cleanReason + " [Hoàn tiền VNPAY: " + refundAmount.toPlainString() + "đ - GD: " + (refundRes.refundTransactionNo() != null ? refundRes.refundTransactionNo() : "SANDBOX") + "]";

            try {
                jdbc.update("UPDATE orders SET status='CANCELLED', payment_status='REFUNDED', cancelled_at=?, cancellation_reason=? WHERE id=?", now, refundNote, orderId);
                jdbc.update("UPDATE payments SET status='REFUNDED', failure_reason=? WHERE order_id=?", refundNote, orderId);
            } catch (Exception e) {
                // Fallback nếu thiếu Check Constraint
                jdbc.update("UPDATE orders SET status='CANCELLED', cancelled_at=?, cancellation_reason=? WHERE id=?", now, refundNote, orderId);
                jdbc.update("UPDATE payments SET status='CANCELLED', failure_reason=? WHERE order_id=?", refundNote, orderId);
            }
        } else {
            // NẾU LÀ ĐƠN COD HOẶC CHƯA THANH TOÁN
            jdbc.update("UPDATE orders SET status='CANCELLED', cancelled_at=?, cancellation_reason=? WHERE id=?", now, cleanReason, orderId);
            jdbc.update("UPDATE payments SET status='CANCELLED', failure_reason=? WHERE order_id=?", cleanReason, orderId);
        }

        // Cập nhật lại Checkouts (Dùng hàm có sẵn)
        Long checkoutId = ((Number) order.get("checkout_id")).longValue();
        updateCheckoutAfterCancel(checkoutId);

        // Gửi thông báo cho Shop Owner
        Long shopId = ((Number) order.get("shop_id")).longValue();
        notifyShopOwner(shopId, "ORDER_CANCELLED_BY_CUST_" + orderId, "Khách hàng đã hủy đơn", "Đơn hàng " + order.get("order_code") + " đã bị khách hủy. Lý do: " + cleanReason, orderId);
    }
    // =========================================================================================

    /**
     * Scheduled Job tự động chuyển đơn hàng từ DELIVERED sang COMPLETED sau 24h.
     * Bắt buộc bỏ qua (skip) các đơn hàng đang có khiếu nại ở trạng thái PENDING_SHOP hoặc ESCALATED.
     */
    @Scheduled(fixedDelayString = "${orders.auto-complete-scan-ms:300000}")
    @Transactional
    public int autoCompleteDeliveredOrders() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime deliveredCutoff = now.minusHours(24);
        List<Long> candidateIds = jdbc.queryForList("""
            SELECT o.id
            FROM orders o
            WHERE o.status = 'DELIVERED'
              AND COALESCE(o.delivered_at, o.created_at) <= ?
              AND NOT EXISTS (
                  SELECT 1 FROM complaints c
                  WHERE c.order_id = o.id AND c.status IN ('PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS')
              )
            ORDER BY o.id
        """, Long.class, deliveredCutoff);

        int completedCount = 0;
        for (Long orderId : candidateIds) {
            List<Map<String, Object>> locked = jdbc.queryForList("""
                SELECT id, order_code, status
                FROM orders
                WHERE id = ? AND status = 'DELIVERED' AND COALESCE(delivered_at, created_at) <= ?
                FOR UPDATE
            """, orderId, deliveredCutoff);
            if (locked.isEmpty()) {
                continue;
            }
            Integer activeComplaints = jdbc.queryForObject("""
                SELECT COUNT(*) FROM complaints
                WHERE order_id = ? AND status IN ('PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS')
            """, Integer.class, orderId);
            if (activeComplaints != null && activeComplaints > 0) {
                continue;
            }
            int updated = jdbc.update("""
                UPDATE orders
                SET status = 'COMPLETED',
                    payment_status = 'PAID',
                    paid_at = COALESCE(paid_at, ?),
                    delivery_status = 'DELIVERED',
                    completed_at = ?
                WHERE id = ? AND status = 'DELIVERED'
            """, now, now, orderId);
            if (updated == 1) {
                completedCount++;
                try {
                    creditCashbackPoints(orderId);
                } catch (Exception ignored) {
                }
            }
        }
        return completedCount;
    }

    /**
     * Khách hàng được hủy đơn khi Shop chưa xác nhận (trạng thái PENDING).
     * Việc kiểm tra quyền và trạng thái được thực hiện trong cùng một transaction
     * để không thể hủy đơn sau khi Shop vừa xác nhận ở một luồng khác.
     */
    @Transactional
    public void cancelOrderByCustomer(Long userId, Long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT o.id, o.order_code, o.status, o.payment_status, o.checkout_id
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            WHERE o.id = ? AND c.user_id = ?
            FOR UPDATE
        """, orderId, userId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng hoặc bạn không có quyền thao tác.");
        }
        Map<String, Object> order = rows.get(0);
        if (!"PENDING".equals(order.get("status")) || "PAID".equals(order.get("payment_status"))) {
            throw new IllegalArgumentException("Shop đã xác nhận hoặc đơn đã thanh toán, bạn không thể hủy đơn từ phía khách hàng.");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String reason = "Khách hàng hủy đơn trước khi Shop xác nhận";
        if (jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=?,cancellation_reason=? WHERE id=? AND status='PENDING'",
                now, reason, orderId) != 1) {
            throw new IllegalArgumentException("Đơn hàng vừa được Shop xác nhận, vui lòng tải lại trang.");
        }

        releaseReservedItems(orderId, "customer-cancel");
        jdbc.update("UPDATE order_items SET allocation_status='RELEASED' WHERE order_id=? AND allocation_status='UNRESERVED'", orderId);
        jdbc.update("UPDATE payments SET status='CANCELLED',failure_reason=? WHERE order_id=? AND status='PENDING'", reason, orderId);
        updateCheckoutAfterCancel(((Number) order.get("checkout_id")).longValue());
        notifyOrderCustomer(orderId, "ORDER_CANCELLED_BY_CUSTOMER", "Đã hủy đơn hàng", "Bạn đã hủy đơn " + order.get("order_code") + " trước khi Shop xác nhận.");
    }

    /**
     * Khách hàng bấm "Đã nhận hàng / Hoàn tất đơn hàng" khi đơn đang ở trạng thái DELIVERED.
     * Chặn hoàn tất nếu đơn đang có khiếu nại ở trạng thái PENDING_SHOP hoặc ESCALATED.
     */
    @Transactional
    public void completeOrderByCustomer(Long userId, Long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT o.id, o.order_code, o.status, o.payment_status, o.delivery_status
            FROM orders o
            JOIN checkouts c ON c.id = o.checkout_id
            WHERE o.id = ? AND c.user_id = ?
            FOR UPDATE
        """, orderId, userId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng hoặc bạn không có quyền thao tác.");
        }

        Map<String, Object> order = rows.get(0);
        String currentStatus = String.valueOf(order.get("status"));
        if (!"DELIVERED".equals(currentStatus)) {
            throw new IllegalArgumentException("Chỉ có thể xác nhận hoàn tất khi đơn hàng đang ở trạng thái Đã giao (DELIVERED).");
        }

        Integer activeComplaints = jdbc.queryForObject("""
            SELECT COUNT(*) FROM complaints
            WHERE order_id = ? AND status IN ('PENDING_SHOP', 'ESCALATED', 'PENDING', 'IN_PROGRESS')
        """, Integer.class, orderId);
        if (activeComplaints != null && activeComplaints > 0) {
            throw new IllegalArgumentException("Đơn hàng đang có khiếu nại chờ xử lý. Vui lòng chờ giải quyết xong hoặc hủy khiếu nại trước khi hoàn tất.");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        int updated = jdbc.update("""
            UPDATE orders
            SET status = 'COMPLETED',
                payment_status = 'PAID',
                paid_at = COALESCE(paid_at, ?),
                delivery_status = 'DELIVERED',
                completed_at = ?
            WHERE id = ? AND status = 'DELIVERED'
        """, now, now, orderId);
        if (updated == 0) {
            throw new IllegalArgumentException("Trạng thái đơn hàng vừa thay đổi, vui lòng tải lại trang.");
        }
        try {
            jdbc.update("UPDATE orders SET customer_confirmed_at = ? WHERE id = ?", now, orderId);
        } catch (Exception ignored) {
        }
        try {
            creditCashbackPoints(orderId);
        } catch (Exception ignored) {
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

    private void recordConfirmedSales(Long orderId, String orderCode) {
        List<Map<String, Object>> items = jdbc.queryForList("SELECT id,inventory_id,quantity FROM order_items WHERE order_id=? AND allocation_status='RESERVED' ORDER BY inventory_id FOR UPDATE", orderId);
        for (Map<String, Object> item : items) {
            long itemId = ((Number) item.get("id")).longValue();
            long inventoryId = ((Number) item.get("inventory_id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();
            if (jdbc.update("UPDATE variant_inventory SET quantity_on_hand=quantity_on_hand-?,reserved_quantity=reserved_quantity-? WHERE id=? AND quantity_on_hand>=? AND reserved_quantity>=?", quantity, quantity, inventoryId, quantity, quantity) != 1)
                throw new IllegalStateException("Không thể ghi nhận số lượng đã bán cho mặt hàng " + itemId + ".");
            jdbc.update("UPDATE order_items SET allocation_status='CONSUMED' WHERE id=? AND allocation_status='RESERVED'", itemId);
            int onHand = jdbc.queryForObject("SELECT quantity_on_hand FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            int reserved = jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE id=?", Integer.class, inventoryId);
            jdbc.update("INSERT INTO inventory_transactions(inventory_id,order_item_id,type,on_hand_delta,reserved_delta,on_hand_after,reserved_after,event_key,reason,created_by) VALUES(?,?,'SALE',?,?,?,?,?,?,NULL)", inventoryId, itemId, -quantity, -quantity, onHand, reserved, "sale-confirm-" + itemId, "Bán theo đơn " + orderCode);
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