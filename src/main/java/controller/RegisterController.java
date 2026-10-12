package controller;

import dto.RegisterRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import service.RegistrationService;
import org.springframework.mail.MailException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.UserRepository;
import service.EmailService;
import service.OtpService;


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
        String email=user.getEmail();
        session.setAttribute("pendingRegistrationEmail", email);
        String code=otpService.createRegistrationOtp(user);
        try {
            emailService.sendEmail(email,code);
        }catch(MailException ex){
            redirect.addFlashAttribute("error", "Chưa gửi được mã. Vui lòng thử lại");
        }
        return "redirect:/register/verify";
    }
    @GetMapping("/register/verify")
    public String verifyPage(HttpSession session){
        if(session.getAttribute("pendingRegistrationEmail")==null){
            return "redirect:/register";
        }
        return "register-verify";
    }
    @PostMapping("/register/verify")
    public String verify(@RequestParam String code, HttpSession session,
                         Model model,
                         RedirectAttributes redirect) {
        String email=(String) session.getAttribute("pendingRegistrationEmail");
        if(email==null) return "redirect:/register";
        if(!code.matches("\\d{6}")||
        !otpService.verifyRegistrationOtp(email,code)){
        model.addAttribute("error", "Mã sai hoặc đã hết hạn");
        return "register-verify";
        }
        session.removeAttribute("pendingRegistrationEmail");
        redirect.addFlashAttribute("success", "Xác minh thành công. Bạn có thể đăng nhập");
        return "redirect:/login";
    }


}
