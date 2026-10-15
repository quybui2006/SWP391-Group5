package controller;

import dto.UserCreateRequest;
import dto.UserDetailDto;
import dto.UserListItemDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import repository.RoleRepository;
import service.AdminUserService;

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
        return "user-list";
    }

    @GetMapping("/{id}")
    public String viewDetail(@PathVariable Long id, Model model, HttpSession session) {
        if (!checkAdmin(session)) return "redirect:/login";

        try {
            UserDetailDto user = adminUserService.getUserDetail(id);
            model.addAttribute("user", user);
            model.addAttribute("allRoles", roleRepository.findAll());
            return "user-detail";
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
        return "user-create";
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
            return "user-create";
        }

        try {
            Long adminId = (Long) session.getAttribute("userId");
            adminUserService.createUser(form, adminId);
            ra.addFlashAttribute("message", "Thêm người dùng mới thành công!");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("allRoles", roleRepository.findAll());
            model.addAttribute("error", e.getMessage());
            return "user-create";
        }
    }
}