package service;

import entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.UserRepository;
import repository.UserRoleRepository;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LoginService {
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;
    public LoginService(UserRepository users, UserRoleRepository userRoles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
    }
    @Transactional(readOnly = true)
    public Optional<LoginResult> login(String email, String password){
        if(email==null||email.isBlank()||password==null){
            return Optional.empty();
        }
        Optional<User> found= users.findByEmailIgnoreCase(email.trim());
        if(found.isEmpty()) return Optional.empty();
        User user = found.get();
        if(!"ACTIVE".equals(user.getStatus())||user.getEmailVerifiedAt()==null
                ||user.getPasswordHash()==null||!passwordEncoder.matches(password, user.getPasswordHash())){
            return Optional.empty();
        }
        Set<String> roles=userRoles.findByUser_Id(user.getId()).stream()
                .map(userRole -> userRole.getRole().getCode()).collect(Collectors.toSet());
        String role=roles.contains("ADMIN")?"ADMIN"
                :roles.contains("SHOP_OWNER")?"SHOP_OWNER"
                :roles.contains("CUSTOMER")?"CUSTOMER":null;
        if(role==null) return Optional.empty();

        return Optional.of(new LoginResult(user.getId(),user.getFullName(),role));

    }
    public record  LoginResult(Long userId, String fullName, String role) {
    }
}
