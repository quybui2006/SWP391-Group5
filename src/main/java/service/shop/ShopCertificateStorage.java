package service.shop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu trữ ảnh và tài liệu chứng nhận pháp lý của gian hàng (GPKD, Giấy ATTP).
 */
@Component
public class ShopCertificateStorage {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".pdf");

    private final Path uploadDirectory;

    public ShopCertificateStorage(@Value("${app.upload.license-dir:uploads/licenses}") String directory) {
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Tệp tải lên không được vượt quá 10 MB.");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        String extension = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx >= 0) {
            extension = originalName.substring(dotIdx);
        }

        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (".jpg".equals(extension) || ".jpeg".equals(extension) || "image/jpeg".equals(contentType)) {
            extension = ".jpg";
        } else if (".png".equals(extension) || "image/png".equals(contentType)) {
            extension = ".png";
        } else if (".webp".equals(extension) || "image/webp".equals(contentType)) {
            extension = ".webp";
        } else if (".pdf".equals(extension) || "application/pdf".equals(contentType)) {
            extension = ".pdf";
        } else {
            throw new IllegalArgumentException("Định dạng tệp không hợp lệ. Vui lòng tải lên ảnh JPG, PNG, WEBP hoặc tài liệu PDF.");
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            Files.createDirectories(uploadDirectory);
            Path target = uploadDirectory.resolve(fileName).normalize();
            if (!target.startsWith(uploadDirectory)) {
                throw new IOException("Đường dẫn tệp không hợp lệ.");
            }
            file.transferTo(target);
            return "/uploads/licenses/" + fileName;
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể lưu tệp chứng nhận.", ex);
        }
    }
}

