package service;

import dto.UserCreateRequest;
import dto.UserDetailDto;
import dto.UserListItemDto;
import entity.Role;
import entity.User;
import entity.UserRole;
import entity.UserRoleId;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.RoleRepository;
import repository.UserAddressesRepository;
import repository.UserRepository;
import repository.UserRoleRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserAddressesRepository userAddressesRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            UserRoleRepository userRoleRepository,
                            UserAddressesRepository userAddressesRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userAddressesRepository = userAddressesRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserListItemDto> getAllUsers(String keyword) {
        List<User> users = userRepository.findAll();
        List<UserRole> allUserRoles = userRoleRepository.findAll();

        Map<Long, List<String>> userRolesMap = allUserRoles.stream()
                .collect(Collectors.groupingBy(
                        ur -> ur.getUser().getId(),
                        Collectors.mapping(ur -> ur.getRole().getCode(), Collectors.toList())
                ));

        List<UserListItemDto> result = new ArrayList<>();
        for (User u : users) {
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim().toLowerCase();
                boolean matchName = u.getFullName() != null && u.getFullName().toLowerCase().contains(kw);
                boolean matchEmail = u.getEmail() != null && u.getEmail().toLowerCase().contains(kw);
                boolean matchPhone = u.getPhone() != null && u.getPhone().contains(kw);
                if (!matchName && !matchEmail && !matchPhone) {
                    continue;
                }
            }
            List<String> roles = userRolesMap.getOrDefault(u.getId(), List.of());
            result.add(new UserListItemDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getStatus(), roles));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public UserDetailDto getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng ID: " + userId));

        List<String> roleCodes = userRoleRepository.findByUser_Id(userId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toList());

        var addresses = userAddressesRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId);

        return new UserDetailDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getEmailVerifiedAt(),
                roleCodes,
                addresses
        );
    }

    @Transactional
    public Long createUser(UserCreateRequest request, Long adminId) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw new IllegalArgumentException("Email này đã được sử dụng trong hệ thống.");
        }

        Role role = roleRepository.findByCode(request.getInitialRole())
                .orElseThrow(() -> new IllegalArgumentException("Vai trò không hợp lệ: " + request.getInitialRole()));

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null);
        user.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        user.setEmailVerifiedAt(LocalDateTime.now()); // Tạo bởi Admin nên tự động verify

        User savedUser = userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(savedUser.getId(), role.getId()));
        userRole.setUser(savedUser);
        userRole.setRole(role);
        userRoleRepository.save(userRole);

        return savedUser.getId();
    }
}