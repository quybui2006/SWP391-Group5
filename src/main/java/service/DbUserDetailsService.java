//package service;
//
//import entity.User;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.userdetails.*;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import repository.UserRepository;
//import repository.UserRoleRepository;
//
//@Service
//public class DbUserDetailsService implements UserDetailsService {
//    private final UserRepository users;
//    private final UserRoleRepository userRoles;
//
//    public DbUserDetailsService(UserRepository users, UserRoleRepository userRoles) {
//        this.users = users;
//        this.userRoles = userRoles;
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public UserDetails loadUserByUsername(String email) {
//        User account = users.findByEmailIgnoreCase(email.trim())
//                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
//
//        if (!"ACTIVE".equals(account.getStatus())
//                || account.getEmailVerifiedAt() == null
//                || account.getPasswordHash() == null) {
//            throw new UsernameNotFoundException("Account unavailable");
//        }
//
//        var authorities = userRoles.findByUser_Id(account.getId()).stream()
//                .map(ur -> new SimpleGrantedAuthority(
//                        "ROLE_" + ur.getRole().getCode()))
//                .toList();
//
//        return org.springframework.security.core.userdetails.User
//                .withUsername(account.getEmail())
//                .password(account.getPasswordHash())
//                .authorities(authorities)
//                .build();
//    }
//}