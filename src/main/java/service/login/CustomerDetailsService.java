package service.login;

import entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomerDetailsService implements UserDetails {
    private final User user;
    private final String primaryRole;
    private Collection<? extends GrantedAuthority> authorities;
    public CustomerDetailsService(User user, String primaryRole, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.primaryRole = primaryRole;
        this.authorities = authorities;
    }
    public User getUser() {
        return user;
    }
    public Long getId() {
        return user.getId();
    }
    public String getFullname() {
        return user.getFullName();
    }
    public String getPrimaryRole() {
        return primaryRole;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"BLOCKED".equalsIgnoreCase(user.getStatus());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equalsIgnoreCase(user.getStatus());
    }

}
