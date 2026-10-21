package controller.admin;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.admin.AdminCategoryService;

@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final AdminCategoryService categories;

    public AdminCategoryController(AdminCategoryService categories) {
        this.categories = categories;
    }

    private boolean checkAdmin(HttpSession session) {
        return session != null && "ADMIN".equals(session.getAttribute("role"));
    }

    @GetMapping
    public String list(Model model, HttpSession session) {
        if (!checkAdmin(session)) return "redirect:/login";
        model.addAttribute("categories", categories.findAll());
        return "admin/categories";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name, HttpSession session, RedirectAttributes redirect) {
        if (!checkAdmin(session)) return "redirect:/login";
        try {
            categories.create(name);
            redirect.addFlashAttribute("success", "Đã thêm danh mục mới.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
        if (!checkAdmin(session)) return "redirect:/login";
        try {
            categories.toggleActive(id);
            redirect.addFlashAttribute("success", "Đã cập nhật trạng thái danh mục.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }
}
