package controller;

import dto.ProductEditView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.util.List;

/**
 * Màn hình Shop Owner - Edit Product (Sheet2 No.8).
 *
 * Giai đoạn làm giao diện: dữ liệu lấy từ sampleData() để dựng giao diện,
 * chưa truy vấn database. Sau này thay bằng service thật.
 */
@Controller
public class ShopProductEditController {

    @GetMapping("/shop/product/{id}/edit")
    public String editProduct(@PathVariable("id") Long id, Model model) {
        model.addAttribute("form", sampleData(id));
        return "shopproductedit";
    }

    private ProductEditView sampleData(Long id) {
        ProductEditView form = new ProductEditView();
        form.setId(id);
        form.setName("Táo Fuji Thượng Hạng");
        form.setOrigin("Nhật Bản");
        form.setBatchCode("LOT-2026-001");
        form.setDescription("Táo Fuji giòn ngọt, nhập khẩu trực tiếp từ Nhật Bản. "
                + "Phơi nắng tự nhiên, không thuốc bảo quản.");
        form.setApprovalStatus("APPROVED");
        form.setSellingStatus("ACTIVE");
        form.setShopName("FreshFruit Store");
        form.setReceivedDate(LocalDate.now().minusDays(2));
        form.setExpiryDate(LocalDate.now().plusDays(25));

        form.setCategories(List.of(
                new ProductEditView.CategoryOption(1L, "Táo"),
                new ProductEditView.CategoryOption(2L, "Chuối"),
                new ProductEditView.CategoryOption(3L, "Cam"),
                new ProductEditView.CategoryOption(4L, "Xoài"),
                new ProductEditView.CategoryOption(5L, "Dưa hấu")));
        form.setCategoryId(1L);
        return form;
    }
}
