package controller.login;

import dto.login.PasswordResetRequest;
import entity.User;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.UserRepository;
import service.login.EmailService;
import service.login.OtpService;

import java.util.Optional;

@Controller
public class ForgotPasswordController {
    private static final String RESET_EMAIL_SESSION_KEY = "pendingResetPasswordEmail";

    private final UserRepository users;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public ForgotPasswordController(UserRepository userRepository,
                                    OtpService otpService,
                                    EmailService emailService,
                                    PasswordEncoder passwordEncoder) {
        this.users = userRepository;
        this.otpService = otpService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "login/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String proccessForgotPassword(@RequestParam(required = false) String email,
                                         HttpSession session,
                                         Model model,
                                         RedirectAttributes redirect) {
        if (email == null || email.isBlank()) {
            model.addAttribute("error", "Vui lòng nhập email của bạn.");
            return "login/forgot-password";
        }
        Optional<User> userOpt = users.findByEmailIgnoreCase(email.trim());
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User này chưa được đăng ký trên hệ thống.");
            return "login/forgot-password";
        }
        User user = userOpt.get();
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            model.addAttribute("error", "Tài khoản của bạn bị khóa hoặc chưa kích hoạt.");
            return "login/forgot-password";
        }
        String code = otpService.createPasswordResetOtp(user);
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), code);
            session.setAttribute(RESET_EMAIL_SESSION_KEY, user.getEmail());
            redirect.addFlashAttribute("message", "Mã OTP đặt lại mật khẩu đã được gửi tới " + user.getEmail() + ". Vui lòng kiểm tra hộp thư.");
            return "redirect:/reset-password";
        } catch (Exception e) {
            model.addAttribute("error", "Không thể gửi email OTP: " + e.getMessage());
            return "login/forgot-password";
        }
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute(RESET_EMAIL_SESSION_KEY);
        if (email == null) {
            return "redirect:/forgot-password";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PasswordResetRequest());
        }
        model.addAttribute("email", email);
        return "login/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@Valid @ModelAttribute("form") PasswordResetRequest form,
                                       BindingResult errors,
                                       HttpSession session,
                                       Model model,
                                       RedirectAttributes redirect) {
        String email = (String) session.getAttribute(RESET_EMAIL_SESSION_KEY);
        if (email == null) {
            return "redirect:/forgot-password";
        }
        model.addAttribute("email", email);

        if (form.getNewPassword() != null && !form.getNewPassword().equals(form.getConfirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "Mật khẩu xác nhận không khớp.");
        }
        if (errors.hasErrors()) {
            return "login/reset-password";
        }

        boolean verified = otpService.verifyPasswordResetOtp(email, form.getCode());
        if (!verified) {
            model.addAttribute("error", "Mã OTP không hợp lệ, đã hết hạn hoặc bạn đã thử sai quá 5 lần.");
            return "login/reset-password";
        }

        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return "redirect:/forgot-password";
        }

        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        users.save(user);

        session.removeAttribute(RESET_EMAIL_SESSION_KEY);
        redirect.addFlashAttribute("success", "Đặt lại mật khẩu thành công! Vui lòng đăng nhập mật khẩu mới.");
        return "redirect:/login";
    }
}
