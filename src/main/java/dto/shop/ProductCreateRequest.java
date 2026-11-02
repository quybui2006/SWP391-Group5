package dto.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Dữ liệu tạo sản phẩm, bao gồm ảnh chính bắt buộc. */
public class ProductCreateRequest {
    @NotBlank @Size(max = 200) private String name;
    @Size(max = 150) private String origin;
    @Size(max = 2000) private String description;
    @NotBlank @Size(max = 60) private String batchCode;
    private MultipartFile imageFile;
    private List<Long> categoryIds;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public MultipartFile getImageFile() { return imageFile; }
    public void setImageFile(MultipartFile imageFile) { this.imageFile = imageFile; }
    public List<Long> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(List<Long> categoryIds) { this.categoryIds = categoryIds; }
}
