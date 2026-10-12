package controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.CustomerAccountService;
import java.util.Map;

@Controller
@RequestMapping("/customer/account")
public class CustomerAccountHomeController {
    private final CustomerAccountService account;
    public CustomerAccountHomeController(CustomerAccountService account) { this.account = account; }
    private Long current(HttpSession s) {
        if (!"CUSTOMER".equals(s.getAttribute("role"))) return null;
        Object id=s.getAttribute("userId"); return id instanceof Number n ? n.longValue() : null;
    }
    private String login(Long id, String path) { return id == null ? "redirect:/login?continueTo=" + path : null; }

    @GetMapping({"", "/"})
    public String account(Model model, HttpSession session) {
        Long id=current(session); String redirect=login(id,"/customer/account"); if(redirect!=null)return redirect;
        model.addAttribute("profile", account.profile(id));
        model.addAttribute("activePage","profile");
        return "customer/account";
    }
    @PostMapping("/profile")
    public String update(@RequestParam String fullName,@RequestParam(required=false) String phone,
                         @RequestParam(required=false) String gender,@RequestParam(required=false) String birthDate,
                         @RequestParam(required=false) org.springframework.web.multipart.MultipartFile avatar,
                         HttpSession session,RedirectAttributes ra){
        Long id=current(session); String redirect=login(id,"/customer/account"); if(redirect!=null)return redirect;
        try { account.saveProfile(id,fullName,phone,gender,birthDate,avatar); session.setAttribute("fullName",fullName.trim()); ra.addFlashAttribute("message","Đã cập nhật thông tin cá nhân."); }
        catch(IllegalArgumentException e){ra.addFlashAttribute("error",e.getMessage());} return "redirect:/customer/account";
    }
    @GetMapping("/avatar")
    @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> avatar(HttpSession session) {
        Long id = current(session);
        if (id == null) return org.springframework.http.ResponseEntity.status(401).build();
        var image = account.avatar(id);
        if (image == null) return org.springframework.http.ResponseEntity.notFound().build();
        return org.springframework.http.ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .contentType(org.springframework.http.MediaType.parseMediaType((String) image.get("avatar_type")))
            .body((byte[]) image.get("avatar"));
    }
    @GetMapping("/orders")
    public String orders(Model model,HttpSession session){ Long id=current(session);String r=login(id,"/customer/account/orders");if(r!=null)return r;model.addAttribute("profile",account.profile(id));model.addAttribute("orders",account.orders(id));model.addAttribute("activePage","orders");return "customer/orders"; }
    @GetMapping("/favorites")
    public String favorites(Model model,HttpSession session){ Long id=current(session);String r=login(id,"/customer/account/favorites");if(r!=null)return r;model.addAttribute("profile",account.profile(id));model.addAttribute("favorites",account.favorites(id));model.addAttribute("activePage","favorites");return "customer/favorites"; }
    @PostMapping("/favorites/toggle")
    // HTML form used by the saved-products list.
    public String toggle(@RequestParam Long variantId,@RequestParam(defaultValue="/customer/account/favorites") String redirect,HttpSession session){Long id=current(session);if(id==null)return "redirect:/login";account.toggleFavorite(id,variantId);return "redirect:"+(redirect.startsWith("/")?redirect:"/customer/account/favorites");}
    @GetMapping("/favorites/ids")
    @ResponseBody
    public java.util.List<Long> favoriteIds(HttpSession session) {
        return account.favoriteIds(requireCustomer(session));
    }
    @PostMapping("/favorites/save")
    @ResponseBody
    public Map<String,Boolean> saveFavorite(@RequestParam Long variantId, @RequestParam boolean favorite, HttpSession session) {
        return Map.of("favorite", account.setFavorite(requireCustomer(session), variantId, favorite));
    }
    private Long requireCustomer(HttpSession session) {
        Long id = current(session);
        if (id == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        return id;
    }
}
