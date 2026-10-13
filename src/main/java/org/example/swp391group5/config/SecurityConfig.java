package org.example.swp391group5.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
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
                        // Tài nguyên tĩnh công khai (CSS, JS, Ảnh, Data JSON)
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/data/**", "/favicon.ico").permitAll()

                        // Các trang công khai cho khách xem sản phẩm, cửa hàng và giỏ hàng
                        .requestMatchers("/", "/home", "/search/**", "/product/**", "/cart/**", "/shops/**", "/shop/{id:[0-9]+}").permitAll()

                        // Luồng xác thực tài khoản: Đăng nhập, Đăng ký, OTP, Quên & Đặt lại mật khẩu
                        .requestMatchers("/login", "/register/**", "/forgot-password/**", "/reset-password/**").permitAll()

                        // Phân quyền cho từng khu vực
//                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // ---> SỬA DÒNG NÀY: Đổi từ .hasRole("OPERATIONS_MANAGER") thành .permitAll() để bypass
                        .requestMatchers("/operations/**").permitAll()

                        .requestMatchers("/shopowner/**", "/shop-owner/**").hasAnyRole("SHOP_OWNER", "ADMIN")
                        .requestMatchers("/customer/**").hasAnyRole("CUSTOMER", "ADMIN")
                        /////////////////////////////////////////////////////////////////////////////////////

                        .requestMatchers("/operations/**").hasRole("OPERATIONS_MANAGER")
                        .requestMatchers("/shop/**", "/shopowner/**", "/shop-owner/**").hasAnyRole("SHOP_OWNER", "ADMIN")
                        .requestMatchers("/customer/**").hasAnyRole("CUSTOMER", "ADMIN")

                        // Các request còn lại
                        .anyRequest().permitAll()
                )

                // 3. Cấu hình Form Login
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login") // Spring Security tự động xử lý POST /login
                        .usernameParameter("email")   // Form dùng name="email"
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // 4. Cấu hình Logout
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