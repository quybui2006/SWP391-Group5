package controller;

import dto.AddressForm;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DataAccessException;
import repository.ProvinceRepository;
import service.AddressBookService;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/customer/account/address")
public class CustomerAccountController {
    private final AddressBookService service;
    private final ProvinceRepository provinces;
    public CustomerAccountController(AddressBookService service, ProvinceRepository provinces) {
        this.service = service; this.provinces = provinces;
    }
    // Tan PTH integration: keep address ownership aligned with checkout's authenticated userId.
    private Long current(HttpSession session) {
        if (!"CUSTOMER".equals(session.getAttribute("role"))) return null;
        Object id = session.getAttribute("userId");
        return id instanceof Number number ? number.longValue() : null;
    }
    @GetMapping
    public String addressBook(Model model, HttpSession session) {
        Long id = current(session);
        if (id == null) return "redirect:/login?continueTo=/customer/account/address";
        if (session.getAttribute("addressToken") == null) session.setAttribute("addressToken", UUID.randomUUID().toString());
        var customer = service.customers().stream().filter(u -> u.getId().equals(id)).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        model.addAttribute("customer", customer);
        model.addAttribute("addresses", service.list(id));
        model.addAttribute("provinceNames", provinces.findAll().stream().collect(Collectors.toMap(p -> p.getCode(), p -> p.getName())));
        model.addAttribute("token", session.getAttribute("addressToken"));
        return "customer/address-book";
    }
    @PostMapping("/{action}")
    public String change(@PathVariable String action, @RequestParam String token,
                         @RequestParam(required = false) Long id, @ModelAttribute AddressForm form,
                         HttpSession session, RedirectAttributes redirect) {
        if (current(session) == null) return "redirect:/login?continueTo=/customer/account/address";
        if (!token.equals(session.getAttribute("addressToken"))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        try {
            Long userId = current(session);
            switch (action) {
                case "save" -> service.save(userId, id, form);
                case "default" -> service.setDefault(userId, id);
                case "delete" -> service.delete(userId, id);
                default -> throw new IllegalArgumentException("Thao tác không hợp lệ.");
            }
            redirect.addFlashAttribute("message", "Đã cập nhật thành công.");
        } catch (IllegalArgumentException | DataAccessException ex) {
            redirect.addFlashAttribute("error", ex instanceof IllegalArgumentException ? ex.getMessage() : "Không thể lưu thay đổi. Vui lòng thử lại.");
            if ("save".equals(action)) {
                redirect.addFlashAttribute("failedForm", form);
                redirect.addFlashAttribute("failedId", id);
            }
        }
        return "redirect:/customer/account/address";
    }
}
