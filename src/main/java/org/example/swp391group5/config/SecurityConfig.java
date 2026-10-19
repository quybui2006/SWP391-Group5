package org.example.swp391group5.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler successHandler;

    public SecurityConfig(CustomAuthenticationSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Tắt CSRF để tránh lỗi 403 Forbidden cho form và test của các thành viên khác
                .csrf(csrf -> csrf.disable())

                // 2. Phân quyền truy cập các đường dẫn
                .authorizeHttpRequests(auth -> auth
                        // Tài nguyên tĩnh công khai (CSS, JS, Ảnh, Data JSON, Favicon)
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/data/**", "/favicon.ico").permitAll()

                        // Các trang công khai cho khách xem sản phẩm, cửa hàng, thông báo
                        .requestMatchers("/", "/home", "/search/**", "/product/**", "/cart/**", "/shops/**", "/shop/{id:[0-9]+}", "/notifications", "/api/notifications/**").permitAll()

                        // Trang lỗi công khai
                        .requestMatchers("/403", "/error", "/error/**").permitAll()

                        // Luồng xác thực tài khoản: Đăng nhập, Đăng ký, OTP, Quên & Đặt lại mật khẩu
                        .requestMatchers("/login", "/register/**", "/forgot-password/**", "/reset-password/**").permitAll()

                        // Phân quyền chi tiết cho từng khu vực:
                        // Khu vực Admin: Chỉ ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Khu vực Quản lý vận hành (Duyệt shop, duyệt sản phẩm): OPERATIONS_MANAGER và ADMIN
                        .requestMatchers("/operations/**").hasAnyRole("OPERATIONS_MANAGER", "ADMIN")

                        // Khu vực Chăm sóc khách hàng (CSKH): CS_STAFF và ADMIN
                        .requestMatchers("/cs/**", "/cs-staff/**").hasAnyRole("CS_STAFF", "ADMIN")

                        // Khu vực Chủ cửa hàng: SHOP_OWNER và ADMIN
                        .requestMatchers("/shopowner/**", "/shop/**").hasAnyRole("SHOP_OWNER", "ADMIN")

                        // Khu vực Khách hàng: CUSTOMER và ADMIN
                        .requestMatchers("/customer/**").hasAnyRole("CUSTOMER", "ADMIN")

                        // Tất cả các request còn lại yêu cầu phải đăng nhập
                        .anyRequest().authenticated()
                )

                // 3. Cấu hình xử lý khi bị từ chối quyền (403 Access Denied)
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/403")
                )

                // 4. Cấu hình Form Login
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login") // Spring Security tự động xử lý POST /login
                        .usernameParameter("email")   // Form dùng name="email"
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // 5. Cấu hình Logout
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        return http.build();
    }
}
