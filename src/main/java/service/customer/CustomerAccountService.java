package service.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import repository.UserRepository;

@Service
public class CustomerAccountService {
    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerAccountService(JdbcTemplate jdbc, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        ensureFavoritesTable();
    }

    private void ensureFavoritesTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS customer_profiles (user_id BIGINT PRIMARY KEY, gender VARCHAR(10), birth_date DATE, avatar MEDIUMBLOB, avatar_type VARCHAR(30), FOREIGN KEY(user_id) REFERENCES users(id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS customer_favorites (user_id BIGINT NOT NULL, variant_id BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY(user_id, variant_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(variant_id) REFERENCES product_variants(id))");
    }

    public Map<String,Object> profile(Long userId) {
        return jdbc.queryForMap("SELECT u.id,u.full_name,u.email,u.phone,u.status,p.gender,p.birth_date,(p.avatar IS NOT NULL) AS has_avatar FROM users u LEFT JOIN customer_profiles p ON p.user_id=u.id WHERE u.id=? AND u.status='ACTIVE'", userId);
    }
    public List<Map<String,Object>> orders(Long userId) {
        return jdbc.query("""
            SELECT o.id,o.order_code,o.total_amount,o.status,o.payment_status,o.delivery_status,o.created_at,
                   s.name AS shop_name
            FROM orders o JOIN checkouts c ON c.id=o.checkout_id
            JOIN shops s ON s.id=o.shop_id
            WHERE c.user_id=? ORDER BY o.created_at DESC,o.id DESC
            """, (rs, row) -> {
                Map<String,Object> order = new java.util.LinkedHashMap<>();
                for (String column : List.of("id", "order_code", "total_amount", "status", "payment_status", "delivery_status", "shop_name"))
                    order.put(column, rs.getObject(column));
                var timestamp = rs.getTimestamp("created_at");
                order.put("created_at", timestamp == null ? "—" : timestamp.toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                return order;
            }, userId);
    }
    public List<Map<String,Object>> favorites(Long userId) {
        return jdbc.queryForList("""
            SELECT pv.id AS variant_id,pv.name AS variant_name,pv.sku,pv.price,pv.image_url,
                   p.id AS product_id,p.name AS product_name,u.name AS unit_name
            FROM customer_favorites f JOIN product_variants pv ON pv.id=f.variant_id
            JOIN products p ON p.id=pv.product_id JOIN units u ON u.id=pv.base_unit_id
            WHERE f.user_id=? AND pv.status='ACTIVE' AND pv.approval_status IN ('APPROVED','PENDING') ORDER BY f.created_at DESC
            """, userId);
    }
    @Transactional
    public void updateProfile(Long userId, String fullName, String phone) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 150) throw new IllegalArgumentException("Họ tên không hợp lệ.");
        if (phone != null && phone.trim().length() > 20) throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
        jdbc.update("UPDATE users SET full_name=?, phone=? WHERE id=? AND status='ACTIVE' AND approval_status='APPROVED'", fullName.trim(), phone == null || phone.isBlank() ? null : phone.trim(), userId);
    }
    @Transactional
    public void saveProfile(Long userId, String fullName, String phone, String gender, String birthDate,
                            org.springframework.web.multipart.MultipartFile image) {
        if (gender != null && !gender.isBlank() && !List.of("MALE", "FEMALE", "OTHER").contains(gender))
            throw new IllegalArgumentException("Giới tính không hợp lệ.");
        java.time.LocalDate date = null;
        try { if (birthDate != null && !birthDate.isBlank()) date = java.time.LocalDate.parse(birthDate); }
        catch (java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Ngày sinh không hợp lệ."); }
        if (date != null && (date.isAfter(java.time.LocalDate.now()) || date.getYear() < 1900))
            throw new IllegalArgumentException("Ngày sinh phải từ năm 1900 đến hôm nay.");
        if (phone != null && !phone.isBlank() && !phone.trim().matches("\\+?[0-9]{9,15}"))
            throw new IllegalArgumentException("Số điện thoại cần có từ 9 đến 15 chữ số.");
        byte[] bytes = null;
        String type = null;
        if (image != null && !image.isEmpty()) {
            if (image.getSize() > 1024 * 1024) throw new IllegalArgumentException("Ảnh đại diện không được vượt quá 1 MB.");
            try (var stream = javax.imageio.ImageIO.createImageInputStream(image.getInputStream())) {
                var readers = javax.imageio.ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IllegalArgumentException("Vui lòng chọn ảnh JPEG hoặc PNG hợp lệ.");
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if (!List.of("jpeg", "jpg", "png").contains(format) || (long) reader.getWidth(0) * reader.getHeight(0) > 20000000)
                        throw new IllegalArgumentException("Vui lòng chọn ảnh JPEG hoặc PNG tối đa 20 megapixel.");
                    type = format.equals("png") ? "image/png" : "image/jpeg";
                    bytes = image.getBytes();
                } finally { reader.dispose(); }
            } catch (java.io.IOException e) { throw new IllegalArgumentException("Không đọc được ảnh đại diện."); }
        }
        updateProfile(userId, fullName, phone);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM customer_profiles WHERE user_id=?", Integer.class, userId) == 0)
            jdbc.update("INSERT INTO customer_profiles(user_id) VALUES (?)", userId);
        jdbc.update("UPDATE customer_profiles SET gender=?,birth_date=? WHERE user_id=?", gender == null || gender.isBlank() ? null : gender, date, userId);
        if (bytes != null) jdbc.update("UPDATE customer_profiles SET avatar=?,avatar_type=? WHERE user_id=?", bytes, type, userId);
    }
    public Map<String,Object> avatar(Long userId) {
        var rows = jdbc.queryForList("SELECT avatar,avatar_type FROM customer_profiles WHERE user_id=? AND avatar IS NOT NULL", userId);
        return rows.isEmpty() ? null : rows.get(0);
    }
    @Transactional
    public void toggleFavorite(Long userId, Long variantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM customer_favorites WHERE user_id=? AND variant_id=?", Integer.class, userId, variantId);
        if (count != null && count > 0) jdbc.update("DELETE FROM customer_favorites WHERE user_id=? AND variant_id=?", userId, variantId);
        else jdbc.update("INSERT INTO customer_favorites(user_id,variant_id) SELECT ?,pv.id FROM product_variants pv WHERE pv.id=? AND pv.status='ACTIVE' AND pv.approval_status IN ('APPROVED','PENDING')", userId, variantId);
    }
    public List<Long> favoriteIds(Long userId) {
        return jdbc.queryForList("SELECT variant_id FROM customer_favorites WHERE user_id=?", Long.class, userId);
    }
    @Transactional
    public boolean setFavorite(Long userId, Long variantId, boolean favorite) {
        // Serialize changes for this customer, including repeated clicks from other tabs.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, userId);
        if (!favorite) {
            jdbc.update("DELETE FROM customer_favorites WHERE user_id=? AND variant_id=?", userId, variantId);
            return false;
        }
        if (!isFavorite(userId, variantId)) {
            int inserted = jdbc.update("INSERT INTO customer_favorites(user_id,variant_id) SELECT ?,pv.id FROM product_variants pv WHERE pv.id=? AND pv.status='ACTIVE' AND pv.approval_status IN ('APPROVED','PENDING')", userId, variantId);
            if (inserted == 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Sản phẩm không còn khả dụng.");
        }
        return true;
    }
    public boolean isFavorite(Long userId, Long variantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM customer_favorites WHERE user_id=? AND variant_id=?", Integer.class, userId, variantId) > 0;
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu không được để trống.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại."));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu cũ không chính xác.");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu cũ.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
