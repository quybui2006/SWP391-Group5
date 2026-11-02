package controller.shop;

import dto.shop.VoucherRequest;
import entity.Voucher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.shop.VoucherService;
import java.math.BigDecimal;

@Controller
@RequestMapping({"/shop/vouchers", "/shop/vouchers/cashback"})
public class ShopVoucherController {
    private final VoucherService service;
    public ShopVoucherController(VoucherService service){this.service=service;}

    @GetMapping
    public String list(@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,
                       HttpServletRequest request,Model model){
        Long owner=ownerId(request); if(owner==null)return "redirect:/login";
        String type = request.getRequestURI().contains("/cashback") ? "CASHBACK" : "DISCOUNT";
        java.util.List<Voucher> list;
        try { list = service.list(owner, keyword, status, type); }
        catch (IllegalArgumentException ex) { model.addAttribute("serviceError", ex.getMessage()); list = java.util.List.of(); }
        model.addAttribute("vouchers", list);
        model.addAttribute("voucherType", type);
        model.addAttribute("totalCount", list.size());
        model.addAttribute("activeCount", list.stream().filter(v -> "ACTIVE".equals(v.getStatus())).count());
        model.addAttribute("pausedCount", list.stream().filter(v -> "PAUSED".equals(v.getStatus())).count());
        model.addAttribute("expiredCount", list.stream().filter(v -> "EXPIRED".equals(v.getStatus())).count());
        model.addAttribute("keyword", keyword); model.addAttribute("selectedStatus", status);
        return "shopowner/voucher-list";
    }

    @GetMapping("/create")
    public String createForm(HttpServletRequest request,Model model){
        if(ownerId(request)==null)return "redirect:/login";
        String type = request.getRequestURI().contains("/cashback") ? "CASHBACK" : "DISCOUNT";
        VoucherRequest form=new VoucherRequest(); form.setMinOrderValue(BigDecimal.ZERO); form.setIsPublic(true); form.setDiscountType("PERCENTAGE"); form.setVoucherType(type);
        model.addAttribute("voucherRequest",form); model.addAttribute("voucherId",null); model.addAttribute("voucherType",type);
        return "shopowner/voucher-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,HttpServletRequest request,Model model,RedirectAttributes redirect){
        Long owner=ownerId(request); if(owner==null)return "redirect:/login";
        try { Voucher v=service.getForEdit(owner,id); String type = request.getRequestURI().contains("/cashback") ? "CASHBACK" : "DISCOUNT";
            if (!type.equals(v.getVoucherType())) return "redirect:" + ("CASHBACK".equals(v.getVoucherType()) ? "/shop/vouchers/cashback" : "/shop/vouchers");
            model.addAttribute("voucherRequest",toRequest(v)); model.addAttribute("voucherId",id); model.addAttribute("voucherType",type); return "shopowner/voucher-form";
        } catch(IllegalArgumentException ex){redirect.addFlashAttribute("error",ex.getMessage()); return "redirect:" + (request.getRequestURI().contains("/cashback") ? "/shop/vouchers/cashback" : "/shop/vouchers");}
    }

    @PostMapping("/save")
    public String save(@RequestParam(required=false) Long id,@Valid @ModelAttribute("voucherRequest") VoucherRequest form,BindingResult binding,HttpServletRequest request,Model model,RedirectAttributes redirect){
        Long owner=ownerId(request); if(owner==null)return "redirect:/login";
        String type = request.getRequestURI().contains("/cashback") ? "CASHBACK" : "DISCOUNT"; form.setVoucherType(type);
        if(binding.hasErrors()){model.addAttribute("voucherId",id); model.addAttribute("voucherType",type); return "shopowner/voucher-form";}
        try{service.save(owner,id,form); redirect.addFlashAttribute("success",id==null?"Tạo voucher thành công.":"Cập nhật voucher thành công."); return "redirect:" + ("CASHBACK".equals(type)?"/shop/vouchers/cashback":"/shop/vouchers");}
        catch(IllegalArgumentException ex){binding.reject("voucher.save",ex.getMessage());model.addAttribute("voucherId",id);model.addAttribute("voucherType",type);return "shopowner/voucher-form";}
        catch(DataIntegrityViolationException ex){binding.reject("voucher.save","Không thể lưu voucher do dữ liệu bị trùng hoặc vi phạm ràng buộc.");model.addAttribute("voucherId",id);model.addAttribute("voucherType",type);return "shopowner/voucher-form";}
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id,HttpServletRequest request,RedirectAttributes redirect,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status){
        Long owner=ownerId(request);if(owner==null)return "redirect:/login";String suffix=request.getRequestURI().contains("/cashback")?"/cashback":"";
        try{String next=service.toggleVoucherStatus(owner,id);redirect.addFlashAttribute("success","Đã chuyển voucher sang trạng thái "+next+".");}catch(IllegalArgumentException ex){redirect.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/shop/vouchers"+suffix+"?keyword="+enc(keyword)+"&status="+enc(status);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,HttpServletRequest request,RedirectAttributes redirect,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status){
        Long owner=ownerId(request);if(owner==null)return "redirect:/login";String suffix=request.getRequestURI().contains("/cashback")?"/cashback":"";
        try{service.deleteUnusedVoucher(owner,id);redirect.addFlashAttribute("success","Đã xóa voucher chưa được sử dụng.");}catch(IllegalArgumentException ex){redirect.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/shop/vouchers"+suffix+"?keyword="+enc(keyword)+"&status="+enc(status);
    }

    private VoucherRequest toRequest(Voucher v){VoucherRequest f=new VoucherRequest();f.setCode(v.getCode());f.setName(v.getName());f.setVoucherType(v.getVoucherType());f.setDiscountType(v.getDiscountType());f.setDiscountValue(v.getDiscountValue());f.setMaxDiscountAmount(v.getMaxDiscountAmount());f.setMinOrderValue(v.getMinOrderValue());f.setMaxUsages(v.getMaxUsages());f.setIsPublic(v.getIsPublic());f.setStartsAt(v.getStartsAt());f.setEndsAt(v.getEndsAt());f.setStatus(v.getStatus());return f;}
    private Long ownerId(HttpServletRequest request){HttpSession session=request.getSession(false);if(session==null||!"SHOP_OWNER".equals(session.getAttribute("role")))return null;Object id=session.getAttribute("userId");return id instanceof Number n?n.longValue():null;}
    private String enc(String s){return java.net.URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8);}
}
