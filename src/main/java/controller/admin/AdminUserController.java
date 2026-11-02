package controller.admin;

import dto.admin.UserCreateRequest;
import dto.admin.UserDetailDto;
import dto.admin.UserListItemDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.RoleRepository;
import service.admin.AdminUserService;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final RoleRepository roleRepository;

    public AdminUserController(AdminUserService adminUserService, RoleRepository roleRepository) {
        this.adminUserService = adminUserService;
        this.roleRepository = roleRepository;
    }

    private boolean checkAdmin(HttpSession session) {
        return session != null && "ADMIN".equals(session.getAttribute("role"));
    }

    @GetMapping
    public String listUsers(@RequestParam(required = false) String keyword, Model model, HttpSession session) {
        if (!checkAdmin(session)) return "redirect:/login";

        List<UserListItemDto> users = adminUserService.getAllUsers(keyword);
        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        return "admin/user-list";
    }

    @GetMapping("/{id}")
    public String viewDetail(@PathVariable Long id, Model model, HttpSession session) {
        if (!checkAdmin(session)) return "redirect:/login";

        try {
            UserDetailDto user = adminUserService.getUserDetail(id);
            model.addAttribute("user", user);
            model.addAttribute("allRoles", roleRepository.findAll());
            return "admin/user-detail";
        } catch (IllegalArgumentException e) {
            return "redirect:/admin/users";
        }
    }

    @GetMapping("/create")
    public String showCreateForm(Model model, HttpSession session) {
        if (!checkAdmin(session)) return "redirect:/login";

        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UserCreateRequest());
        }
        model.addAttribute("allRoles", roleRepository.findAll());
        return "admin/user-create";
    }

    @PostMapping("/create")
    public String processCreateUser(@Valid @ModelAttribute("form") UserCreateRequest form,
                                    BindingResult errors,
                                    HttpSession session,
                                    Model model,
                                    RedirectAttributes ra) {
        if (!checkAdmin(session)) return "redirect:/login";

        if (errors.hasErrors()) {
            model.addAttribute("allRoles", roleRepository.findAll());
            return "admin/user-create";
        }

        try {
            Object uid = session.getAttribute("userId");
            Long adminId = uid instanceof Number num ? num.longValue() : null;
            adminUserService.createUser(form, adminId);
            ra.addFlashAttribute("message", "Thêm người dùng mới thành công!");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("allRoles", roleRepository.findAll());
            model.addAttribute("error", e.getMessage());
            return "admin/user-create";
        }
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        if (!checkAdmin(session)) return "redirect:/login";
        try {
            Object uid = session.getAttribute("userId");
            Long adminId = uid instanceof Number num ? num.longValue() : null;
            String newStatus = adminUserService.toggleUserStatus(id, adminId);
            ra.addFlashAttribute("message",
                    "LOCKED".equals(newStatus) ? "Đã khóa tài khoản." : "Đã mở khóa tài khoản.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
