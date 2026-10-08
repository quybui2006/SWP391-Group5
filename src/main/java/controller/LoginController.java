package controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import service.LoginService;

@Controller
public class LoginController {
    private final LoginService loginService;
    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }
    @GetMapping("/login")
    public String loginPage(){
        return "login";
    }
    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpServletRequest request,
                        Model model){
        var result = loginService.login(email, password);
        if(result.isEmpty()){
            model.addAttribute("error", "Emmail invalid.");
            return "login";
        }
        HttpSession oldSession = request.getSession(false);
        if(oldSession!=null) oldSession.invalidate();
        var account=result.get();
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", account.userId());
        session.setAttribute("fullName", account.fullName());
        session.setAttribute("role", account.role());

        return switch (account.role()){
            case "ADMIN" -> "redirect:/admin";
            case "SHOP_OWNER" -> "redirect:/shop-owner";
            default -> "redirect:/customer";
        };

    }
    @PostMapping("/logout")
    public String logout(HttpServletRequest request){
        HttpSession session = request.getSession(false);
        if(session!=null) session.invalidate();
        return "redirect:/login";
    }
    @GetMapping("/customer")
    public String customer(HttpServletRequest request){
        return pageForRole(request, "CUSTOMER", "customer");
    }


    @GetMapping("/shop-owner")
    public String shopOwner(HttpServletRequest request) {
        return pageForRole(request, "SHOP_OWNER", "shop-owner");
    }

    @GetMapping("/admin")
    public String admin(HttpServletRequest request) {
        return pageForRole(request, "ADMIN", "admin");
    }

    private String pageForRole(HttpServletRequest request, String requiredRole, String view){
        HttpSession session = request.getSession(false);
        if(session==null||!requiredRole.equals(session.getAttribute("role"))){
            return "redirect:/login";
        }
        return view;
    }
}
