package controller;

import dto.ProductDetailView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Màn hình Shop Owner - Xem chi tiết sản phẩm (Sheet2 No.7).
 *
 * Giao diện dùng chung CSS với trang chi tiết của khách hàng
 * (customerproductdetail.css) để cả hai trang nhìn giống nhau, nhưng nội dung
 * khác: Shop Owner xem trạng thái duyệt, tồn kho và có nút quản lý sản phẩm.
 *
 * Giai đoạn làm giao diện: dữ liệu lấy từ sampleData(), chưa truy vấn database.
 */
@Controller
public class ShopProductDetailController {

    @GetMapping("/shop/product/{id}")
    public String productDetail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("product", sampleData(id));
        return "shopproductdetail";
    }

    private ProductDetailView sampleData(Long id) {
        ProductDetailView p = new ProductDetailView();
        p.setId(id);
        p.setName("Táo Fuji Thượng Hạng");
        p.setCategoryName("Trái cây");
        p.setOrigin("Nhật Bản");
        p.setBatchCode("LOT-2026-001");
        p.setShopName("FreshFruit Store");
        p.setDescription("Táo Fuji giòn ngọt, nhập khẩu trực tiếp từ Nhật Bản. "
                + "Phơi nắng tự nhiên, không thuốc bảo quản. "
                + "Giàu chất xơ và Vitamin C, phù hợp ăn trực tiếp hoặc làm bánh.");

        p.setApprovalStatus("APPROVED");
        p.setSellingStatus("ACTIVE");

        p.setReceivedDate(LocalDate.now().minusDays(2));
        p.setExpiryDate(LocalDate.now().plusDays(25));

        long days = ChronoUnit.DAYS.between(LocalDate.now(), p.getExpiryDate());
        p.setDaysUntilExpiry(days);
        p.setExpiringSoon(days >= 0 && days <= 3);

        p.setPrice(new BigDecimal("49000"));
        p.setUnitName("Gói");

        p.setVariants(List.of(
                new ProductDetailView.VariantRow(1L, "FF-TAO-FUJI-05",
                        "Táo Fuji 0.5kg PACK", "Khoảng 0.5kg, 5-6 quả",
                        "ACTIVE", new BigDecimal("49000"), "Gói", 50, 0, 50, false),
                new ProductDetailView.VariantRow(2L, "FF-TAO-FUJI-1KG",
                        "Táo Fuji 1kg PACK", "Khoảng 1kg, 9-10 quả",
                        "ACTIVE", new BigDecimal("89000"), "Gói", 12, 0, 12, true)));

        return p;
    }
}
