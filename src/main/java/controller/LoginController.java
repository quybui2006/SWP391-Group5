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
    // Tan PTH integration: preserve the requested customer page through login.
    public String loginPage(@RequestParam(required = false) String continueTo, Model model){
        model.addAttribute("continueTo", safeContinueTo(continueTo));
        return "login";
    }
    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        @RequestParam(required = false) String continueTo,
                        HttpServletRequest request,
                        Model model){
        var result = loginService.login(email, password);
        if(result.isEmpty()){
            model.addAttribute("error", "Emmail invalid.");
            model.addAttribute("continueTo", safeContinueTo(continueTo));
            return "login";
        }
        HttpSession oldSession = request.getSession(false);
        if(oldSession!=null) oldSession.invalidate();
        var account=result.get();
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", account.userId());
        session.setAttribute("fullName", account.fullName());
        session.setAttribute("role", account.role());

        if ("CUSTOMER".equals(account.role()) && continueTo != null) {
            return "redirect:" + safeContinueTo(continueTo);
        }

        return switch (account.role()){
            case "ADMIN" -> "redirect:/admin";
            case "SHOP_OWNER" -> "redirect:/shopowner/home";
            default -> "redirect:/";
        };

    }

    public String login(String email, String password, HttpServletRequest request, Model model) {
        return login(email, password, null, request, model);
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
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) {
            return "redirect:/login";
        }
        return "redirect:/shopowner/home";
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

    private String safeContinueTo(String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\")
                ? path : "/";
    }
}
