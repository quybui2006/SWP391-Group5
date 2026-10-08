package service;

import dto.AddressForm;
import entity.User;
import entity.UserAddresses;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.UserAddressesRepository;
import repository.UserRepository;
import java.util.List;

@Service
public class AddressBookService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final UserAddressesRepository addresses;
    private final AddressLocationService locations;

    public AddressBookService(JdbcTemplate jdbc, UserRepository users, UserAddressesRepository addresses, AddressLocationService locations) {
        this.jdbc = jdbc; this.users = users; this.addresses = addresses; this.locations = locations;
    }

    public List<User> customers() {
        return jdbc.queryForList("SELECT DISTINCT u.id FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE r.code='CUSTOMER' AND u.status='ACTIVE' ORDER BY u.id", Long.class)
                .stream().map(id -> users.findById(id).orElseThrow()).toList();
    }

    @Transactional
    public List<User> ensureCustomers() {
        var existing = customers();
        if (!existing.isEmpty()) return existing;
        jdbc.update("INSERT INTO roles(code,name) VALUES ('CUSTOMER','Khách hàng') ON DUPLICATE KEY UPDATE code=code");
        var customer = users.findByEmail("customer.demo@freshfruit.example").orElseGet(() -> {
            var user = new User(); user.setFullName("Khách hàng FreshFruit");
            user.setEmail("customer.demo@freshfruit.example"); return users.saveAndFlush(user);
        });
        if (!"ACTIVE".equals(customer.getStatus())) throw new IllegalArgumentException("Tài khoản demo không hoạt động; vui lòng tạo customer khác.");
        jdbc.update("INSERT IGNORE INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='CUSTOMER'", customer.getId());
        return customers();
    }

    private void lockCustomer(Long userId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, userId);
        if (customers().stream().noneMatch(user -> user.getId().equals(userId))) throw new IllegalArgumentException("Customer không hợp lệ.");
    }

    public List<UserAddresses> list(Long userId) { return addresses.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId); }
    public UserAddresses owned(Long id, Long userId) {
        return addresses.findByIdAndUserId(id, userId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ của tài khoản này."));
    }
    private String text(String value, int max, String label) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw new IllegalArgumentException(label + " không được trống và tối đa " + max + " ký tự.");
        return value.trim();
    }

    @Transactional
    public void save(Long userId, Long id, AddressForm form) {
        lockCustomer(userId);
        var name = text(form.getRecipientName(), 150, "Họ tên");
        var phone = text(form.getRecipientPhone(), 20, "Số điện thoại").replaceAll("[\\s.-]", "");
        if (phone.startsWith("+84")) phone = "0" + phone.substring(3);
        if (!phone.matches("0[35789][0-9]{8}")) throw new IllegalArgumentException("Số di động Việt Nam phải có 10 số, bắt đầu bằng 03, 05, 07, 08 hoặc 09 (cũng chấp nhận +84).");
        var location = locations.validate(form.getProvinceCode(), form.getDistrictName(), form.getWardName());
        // Existing schema has no district column: preserve both names in ward_name.
        var ward = text(form.getWardName() + ", " + form.getDistrictName(), 150, "Phường xã, quận huyện");
        var detail = text(form.getAddressDetail(), 500, "Địa chỉ cụ thể");
        var all = list(userId);
        var address = id == null ? new UserAddresses() : owned(id, userId);
        boolean makeDefault = form.isDefaultAddress() || address.isDefaultAddress() || all.isEmpty();
        // Resolve an existing province by name too, retaining its original code/FK.
        var codes = jdbc.queryForList("SELECT code FROM provinces WHERE name=?", String.class, location.name());
        String code = codes.isEmpty() ? "legacy-" + location.code() : codes.get(0);
        if (codes.isEmpty()) jdbc.update("INSERT INTO provinces(code,name,is_active) VALUES (?,?,1) ON DUPLICATE KEY UPDATE code=code", code, location.name());
        if (makeDefault) { all.forEach(a -> a.setDefaultAddress(false)); addresses.flush(); }
        address.setUser(users.getReferenceById(userId));
        address.setRecipientName(name); address.setRecipientPhone(phone); address.setProvinceCode(code);
        address.setWardName(ward); address.setAddressDetail(detail); address.setDefaultAddress(makeDefault);
        addresses.save(address);
    }

    @Transactional
    public void setDefault(Long userId, Long id) {
        lockCustomer(userId); var selected = owned(id, userId);
        list(userId).forEach(a -> a.setDefaultAddress(false)); addresses.flush();
        selected.setDefaultAddress(true);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        lockCustomer(userId); var address = owned(id, userId);
        boolean wasDefault = address.isDefaultAddress();
        addresses.delete(address); addresses.flush();
        var remaining = list(userId);
        if (wasDefault && !remaining.isEmpty()) remaining.get(0).setDefaultAddress(true);
    }
}
