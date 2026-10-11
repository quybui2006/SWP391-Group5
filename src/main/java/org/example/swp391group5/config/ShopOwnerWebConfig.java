package org.example.swp391group5.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import repository.ShopRepository;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ShopOwnerWebConfig implements WebMvcConfigurer {
    private final ShopRepository shops;
    private final JdbcTemplate jdbc;
    public ShopOwnerWebConfig(ShopRepository shops, JdbcTemplate jdbc) { this.shops = shops; this.jdbc = jdbc; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, org.springframework.web.servlet.ModelAndView view) {
                if (view != null && view.getViewName() != null && !view.getViewName().startsWith("redirect:")) {
                    view.addObject("ownerName", request.getAttribute("ownerName"));
                    view.addObject("shopName", request.getAttribute("shopName"));
                }
            }
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                var session = request.getSession(false);
                if (session == null || !(session.getAttribute("userId") instanceof Number)) {
                    response.sendRedirect(request.getContextPath() + "/login");
                    return false;
                }
                long id = ((Number) session.getAttribute("userId")).longValue();
                if (!"SHOP_OWNER".equals(session.getAttribute("role"))) {
                    response.sendError(403, "Tài khoản không có quyền Shop Owner.");
                    return false;
                }
                var shop = shops.findByOwnerId(id);
                request.setAttribute("ownerName", jdbc.queryForObject("SELECT full_name FROM users WHERE id=?", String.class, id));
                request.setAttribute("shopName", shop.map(s -> s.getName()).orElse("Chưa đăng ký cửa hàng"));
                if (shop.isEmpty() && !request.getRequestURI().equals(request.getContextPath() + "/shopowner/home")) {
                    response.sendRedirect(request.getContextPath() + "/shopowner/home");
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/shopowner/**", "/shop/**");
    }
}
