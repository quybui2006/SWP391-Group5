package service;

import dto.RegisterRequest;
import entity.Role;
import entity.User;
import entity.UserRole;
import entity.UserRoleId;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import repository.RoleRepository;
import repository.UserRepository;
import repository.UserRoleRepository;

import java.util.Locale;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;
    public RegistrationService(UserRepository users, RoleRepository roles,
                               UserRoleRepository userRoles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
    }
    @Transactional
    public Long register(RegisterRequest request){
        if(!request.getPassword().equals(request.getConfirmPassword())){
            throw new IllegalArgumentException("Passwords do not match");

        }
        String email=request.getEmail().trim().toLowerCase(Locale.ROOT);
        if(users.existsByEmailIgnoreCase(email)){
            throw new IllegalArgumentException("Email already exists");
        }
        Role customerRole=roles.findByCode("CUSTOMER")
                .orElseThrow(()->new IllegalArgumentException("Customer Role not found"));
        User user=new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone());
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword()));
        User saved=users.save(user);
        UserRole userRole=new UserRole();
        userRole.setId(new UserRoleId(saved.getId(), customerRole.getId()));
        userRole.setUser(saved);
        userRole.setRole(customerRole);
        userRoles.save(userRole);
        return saved.getId();
    }
}
