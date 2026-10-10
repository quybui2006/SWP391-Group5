package org.example.swp391group5;

import dto.AddressForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import service.AddressBookService;
import service.AddressLocationService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AddressBookIntegrationTests {
    @Autowired AddressBookService service;
    @Autowired AddressLocationService locations;
    @Autowired JdbcTemplate jdbc;

    private Long customer(String suffix) {
        String email = "address-test-" + suffix + "@freshfruit.example";
        jdbc.update("INSERT INTO users(full_name,email) VALUES ('Address integration test',?)", email);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        jdbc.update("INSERT INTO roles(code,name) VALUES ('CUSTOMER','Khách hàng') ON DUPLICATE KEY UPDATE code=code");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='CUSTOMER'", id);
        return id;
    }
    private AddressForm form() {
        var p = locations.getLocations().get(0);
        var d = p.districts().get(0);
        var f = new AddressForm();
        f.setRecipientName("Người nhận kiểm thử"); f.setRecipientPhone("+84912345678");
        f.setProvinceCode(String.valueOf(p.code())); f.setDistrictName(d.name());
        f.setWardName(d.wards().get(0).name()); f.setAddressDetail("18 Nguyễn Trãi");
        return f;
    }
    @Test
    void persistsEditsDefaultsDeletesAndRejectsOtherCustomers() {
        Long owner = customer("owner"), other = customer("other");
        var f = form(); service.save(owner, null, f);
        var first = service.list(owner).get(0);
        Long firstId = first.getId();
        assertTrue(first.isDefaultAddress());
        assertEquals("0912345678", first.getRecipientPhone());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_addresses WHERE user_id=?", Integer.class, owner));
        service.save(owner, null, f);
        Long second = service.list(owner).stream().filter(a -> !a.getId().equals(firstId)).findFirst().orElseThrow().getId();
        service.setDefault(owner, second);
        assertEquals(second, service.list(owner).get(0).getId());
        assertEquals(1, service.list(owner).stream().filter(a -> a.isDefaultAddress()).count());
        f.setAddressDetail("Địa chỉ đã cập nhật"); service.save(owner, second, f);
        assertEquals("Địa chỉ đã cập nhật", service.owned(second, owner).getAddressDetail());
        assertTrue(service.owned(second, owner).isDefaultAddress());
        assertThrows(IllegalArgumentException.class, () -> service.owned(firstId, other));
        assertThrows(IllegalArgumentException.class, () -> service.delete(owner, second));
        assertEquals(2, service.list(owner).size());
        service.setDefault(owner, firstId);
        service.delete(owner, second);
        assertTrue(service.list(owner).get(0).isDefaultAddress());
        assertThrows(IllegalArgumentException.class, () -> service.delete(owner, firstId));
        assertEquals(1, service.list(owner).size());
    }
    @Test
    void rejectsInvalidPhoneAndLocation() {
        Long owner = customer("validation");
        var f = form(); f.setRecipientPhone("012345");
        assertThrows(IllegalArgumentException.class, () -> service.save(owner, null, f));
        f.setRecipientPhone("0912345678"); f.setDistrictName("Không thuộc tỉnh");
        assertThrows(IllegalArgumentException.class, () -> service.save(owner, null, f));
        assertTrue(service.list(owner).isEmpty());
        assertEquals(63, locations.getLocations().size());
    }
}
