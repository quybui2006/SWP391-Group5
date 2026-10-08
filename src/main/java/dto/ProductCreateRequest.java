package dto;

import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(
            min = 5,
            max = 100,
            message = "Tên sản phẩm phải từ 5-100 ký tự"
    )
    private String name;

    private String description;

    @Size(
            max = 150,
            message = "Xuất xứ không được vượt quá 150 ký tự"
    )
    private String origin;

    @NotBlank(message = "Mã lô không được để trống")
    @Size(
            max = 60,
            message = "Mã lô không được vượt quá 60 ký tự"
    )
    private String batchCode;

    @NotNull(message = "Vui lòng nhập ngày nhập")
    private LocalDate receivedDate;

    @NotNull(message = "Vui lòng nhập ngày hết hạn")
    private LocalDate expiryDate;

    @NotNull(message = "Giá sản phẩm không được để trống")
    @Min(
            value = 1000,
            message = "Giá tối thiểu là 1000 VNĐ"
    )
    private BigDecimal price;

    @NotNull(message = "Vui lòng chọn danh mục")
    private Long categoryId;

    @NotBlank(message = "SKU không được để trống")
    @Size(
            max = 80,
            message = "SKU không được vượt quá 80 ký tự"
    )
    private String sku;

    @NotBlank(message = "Tên biến thể không được để trống")
    @Size(
            max = 150,
            message = "Tên biến thể không được vượt quá 150 ký tự"
    )
    private String variantName;

    @NotNull(message = "Vui lòng chọn đơn vị")
    private Long unitId;

    @NotNull(message = "Vui lòng nhập số lượng tồn kho")
    @Min(
            value = 1,
            message = "Số lượng tồn kho phải lớn hơn 0"
    )
    private Integer stockQuantity;

    @NotNull(message = "Vui lòng nhập ngưỡng tồn kho")
    @DecimalMin(
            value = "0.0",
            message = "Ngưỡng tồn kho không được âm"
    )
    @DecimalMax(
            value = "100.0",
            message = "Ngưỡng tồn kho không được lớn hơn 100%"
    )
    private BigDecimal lowStockThresholdPct;

    /*
     * Không dùng @NotNull cho MultipartFile.
     *
     * ServiceImpl sẽ kiểm tra:
     * - file null
     * - file rỗng
     * - dung lượng < 5MB
     * - jpg/png
     */
    private MultipartFile imageFile;
}