package org.example.swp391group5.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import repository.UserRepository;

import java.io.IOException;
import java.util.Collection;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public CustomAuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        HttpSession session = request.getSession();
        String email = authentication.getName();

        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            session.setAttribute("userId", user.getId());
            session.setAttribute("fullName", user.getFullName());
            session.setAttribute("email", user.getEmail());
        });

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String targetUrl = "/";
        String sessionRole = "CUSTOMER";

        for (GrantedAuthority auth : authorities) {
            String role = auth.getAuthority().toUpperCase();
            if (role.contains("DELIVERY") || role.contains("SHIPPER")) {
                sessionRole = "DELIVERY_MANAGER";
                targetUrl = "/delivery/orders";
                break;
            } else if (role.contains("ADMIN")) {
                sessionRole = "ADMIN";
                targetUrl = "/admin";
                break;
            } else if (role.contains("OPERATIONS")) {
                sessionRole = "OPERATIONS_MANAGER";
                targetUrl = "/operations/dashboard";
                break;
            } else if (role.contains("SHOP_OWNER")) {
                sessionRole = "SHOP_OWNER";
                targetUrl = "/shopowner/home";
                break;
            } else if (role.contains("CS_STAFF")) {
                sessionRole = "CS_STAFF";
                targetUrl = "/cs/dashboard";
                break;
            }
        }

        session.setAttribute("role", sessionRole);

        if ("CUSTOMER".equals(sessionRole)) {
            String continueTo = request.getParameter("continueTo");
            if (continueTo != null && continueTo.startsWith("/") && !continueTo.startsWith("//") && !continueTo.equals("/")) {
                targetUrl = continueTo;
            }
        }

        response.sendRedirect(request.getContextPath() + targetUrl);
    }
}