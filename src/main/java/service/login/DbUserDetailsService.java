package service.login;

import entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.UserRepository;
import repository.UserRoleRepository;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DbUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    private final UserRoleRepository userRoles;

    public DbUserDetailsService(UserRepository users, UserRoleRepository userRoles) {
        this.users = users;
        this.userRoles = userRoles;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if(email == null||email.isBlank()) {
            throw new UsernameNotFoundException("Email không được để trống");
        }
        User account = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));

        if (!"ACTIVE".equals(account.getStatus())
                || account.getEmailVerifiedAt() == null
                || account.getPasswordHash() == null) {
            throw new UsernameNotFoundException("Tài khoản chưa kích hoạt.");
        }

        Set<String> roleCodes=userRoles.findByUser_Id(account.getId()).stream().
                map(ur->ur.getRole().getCode())
                .collect(Collectors.toSet());

        String primaryRole = roleCodes.contains("ADMIN") ? "ADMIN"
                : roleCodes.contains("OPERATIONS_MANAGER") ? "OPERATIONS_MANAGER"
                : roleCodes.contains("CS_STAFF") ? "CS_STAFF"
                : roleCodes.contains("DELIVERY_MANAGER") ? "DELIVERY_MANAGER"
                : roleCodes.contains("SHOP_OWNER") ? "SHOP_OWNER"
                : roleCodes.contains("CUSTOMER") ? "CUSTOMER" : "CUSTOMER";

        var authorities = roleCodes.stream()
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code)).toList();

        return new CustomerDetailsService(account, primaryRole, authorities);
    }
}
