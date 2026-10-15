package org.example.swp391group5.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import service.CustomerDetailsService;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        HttpSession session = request.getSession(true);
        if (authentication.getPrincipal() instanceof CustomerDetailsService userDetails) {
            session.setAttribute("userId", userDetails.getId());
            session.setAttribute("fullName", userDetails.getFullname());
            session.setAttribute("role", userDetails.getPrimaryRole());

            String continueTo = request.getParameter("continueTo");
            if ("CUSTOMER".equals(userDetails.getPrimaryRole())) {
                if (isValidRedirect(continueTo)) {
                    response.sendRedirect(continueTo);
                } else {
                    response.sendRedirect("/");
                }
                return;
            }

            switch (userDetails.getPrimaryRole()) {
                case "ADMIN" -> response.sendRedirect("/admin");
                case "OPERATIONS_MANAGER" -> response.sendRedirect("/operations/shops");
                case "SHOP_OWNER" -> response.sendRedirect("/shopowner/home");
                default -> response.sendRedirect("/");
            }
        } else {
            response.sendRedirect("/");
        }
    }

    private boolean isValidRedirect(String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\") && !"/".equals(path);
    }
}
