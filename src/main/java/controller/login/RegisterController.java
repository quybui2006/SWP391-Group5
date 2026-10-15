package controller.login;

import dto.login.RegisterRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import service.login.RegistrationService;
import org.springframework.mail.MailException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.UserRepository;
import service.login.EmailService;
import service.login.OtpService;


@Controller
public class RegisterController {
    private final RegistrationService registrationService;
    private final UserRepository users;
    private final OtpService otpService;
    private final EmailService emailService;

    public RegisterController(RegistrationService registrationService, UserRepository users, OtpService otpService, EmailService emailService) {
        this.registrationService = registrationService;
        this.users = users;
        this.otpService = otpService;
        this.emailService = emailService;
    }
    @GetMapping("/register")
    public String showForm(org.springframework.ui.Model model) {
        model.addAttribute("form", new RegisterRequest());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterRequest form,
                           BindingResult errors,
                           HttpSession session,
                           RedirectAttributes redirect) {
        if(form.getPassword()!=null
        && !form.getPassword().equals(form.getConfirmPassword())){
            errors.rejectValue("confirmPassword", "misMatch", "Mật khẩu xác nhận không khớp");
        }
        if(errors.hasErrors()){return "register";}
        Long userId;
        try{
            userId = registrationService.register(form);
        }catch(IllegalArgumentException ex){
            if("Email already exists".equalsIgnoreCase(ex.getMessage())){
                errors.rejectValue("email", "duplicate", "Email đã được sử dụng");
                return "register";
            }
            throw ex;
        }
        var user = users.findById(userId).orElseThrow();
        String email = user.getEmail();
        session.setAttribute("pendingRegistrationEmail", email);
        String code = otpService.createRegistrationOtp(user);
        try {
            emailService.sendEmail(email, code);
            redirect.addFlashAttribute("message", "Mã xác minh OTP đã được gửi tới email của bạn. Vui lòng kiểm tra hộp thư đến (hoặc hòm thư Spam).");
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Không thể gửi email OTP: " + ex.getMessage());
        }
        return "redirect:/register/verify";
    }

    @GetMapping("/register/verify")
    public String verifyPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute("pendingRegistrationEmail");
        if (email == null) {
            return "redirect:/register";
        }
        model.addAttribute("email", email);
        return "register-verify";
    }

    @PostMapping("/register/resend-otp")
    public String resendOtp(HttpSession session, RedirectAttributes redirect) {
        String email = (String) session.getAttribute("pendingRegistrationEmail");
        if (email == null) {
            redirect.addFlashAttribute("error", "Phiên xác minh đã hết hạn. Vui lòng đăng ký lại.");
            return "redirect:/register";
        }

        var userOpt = users.findByEmailIgnoreCase(email.trim());
        if (userOpt.isEmpty()) {
            redirect.addFlashAttribute("error", "Không tìm thấy thông tin tài khoản.");
            return "redirect:/register";
        }

        var user = userOpt.get();
        if (user.getEmailVerifiedAt() != null) {
            redirect.addFlashAttribute("message", "Email của bạn đã được xác minh trước đó. Vui lòng đăng nhập.");
            return "redirect:/login";
        }

        String code = otpService.createRegistrationOtp(user);
        try {
            emailService.sendEmail(email, code);
            redirect.addFlashAttribute("message", "Mã xác minh mới đã được gửi tới email của bạn. Vui lòng kiểm tra hộp thư.");
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Không thể gửi email OTP: " + ex.getMessage());
        }

        return "redirect:/register/verify";
    }

    @GetMapping("/register/resend-otp")
    public String resendOtpGet(HttpSession session, RedirectAttributes redirect) {
        return resendOtp(session, redirect);
    }

    @PostMapping("/register/verify")
    public String verify(@RequestParam String code, HttpSession session,
                         Model model,
                         RedirectAttributes redirect) {
        String email = (String) session.getAttribute("pendingRegistrationEmail");
        if (email == null) return "redirect:/register";
        model.addAttribute("email", email);
        if (!code.matches("\\d{6}") ||
            !otpService.verifyRegistrationOtp(email, code)) {
            model.addAttribute("error", "Mã sai hoặc đã hết hạn");
            return "register-verify";
        }
        session.removeAttribute("pendingRegistrationEmail");
        redirect.addFlashAttribute("success", "Xác minh thành công. Bạn có thể đăng nhập");
        return "redirect:/login";
    }


}
