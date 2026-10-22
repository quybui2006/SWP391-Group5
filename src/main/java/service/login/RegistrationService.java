package service.login;

import dto.login.RegisterRequest;
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
        String targetRoleCode = "SHOP_OWNER".equalsIgnoreCase(request.getRole()) ? "SHOP_OWNER" : "CUSTOMER";

        var existingUser = users.findByEmailIgnoreCase(email);
        if(existingUser.isPresent()){
            User user = existingUser.get();
            if(user.getEmailVerifiedAt() != null){
                throw new IllegalArgumentException("Email already exists");
            }
            user.setFullName(request.getFullName().trim());
            user.setPhone(request.getPhone());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            User saved = users.save(user);

            userRoles.deleteAll(userRoles.findByUser_Id(saved.getId()));
            assignRoles(saved, targetRoleCode);
            return saved.getId();
        }

        User user=new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone());
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword()));
        User saved=users.save(user);

        assignRoles(saved, targetRoleCode);
        return saved.getId();
    }

    private void assignRoles(User user, String targetRoleCode) {
        if ("SHOP_OWNER".equalsIgnoreCase(targetRoleCode)) {
            Role shopOwnerRole = roles.findByCode("SHOP_OWNER")
                    .orElseThrow(() -> new IllegalArgumentException("Shop Owner Role not found"));
            UserRole urOwner = new UserRole();
            urOwner.setId(new UserRoleId(user.getId(), shopOwnerRole.getId()));
            urOwner.setUser(user);
            urOwner.setRole(shopOwnerRole);
            userRoles.save(urOwner);

            Role customerRole = roles.findByCode("CUSTOMER")
                    .orElseThrow(() -> new IllegalArgumentException("Customer Role not found"));
            UserRole urCust = new UserRole();
            urCust.setId(new UserRoleId(user.getId(), customerRole.getId()));
            urCust.setUser(user);
            urCust.setRole(customerRole);
            userRoles.save(urCust);
        } else {
            Role customerRole = roles.findByCode("CUSTOMER")
                    .orElseThrow(() -> new IllegalArgumentException("Customer Role not found"));
            UserRole urCust = new UserRole();
            urCust.setId(new UserRoleId(user.getId(), customerRole.getId()));
            urCust.setUser(user);
            urCust.setRole(customerRole);
            userRoles.save(urCust);
        }
    }
}
