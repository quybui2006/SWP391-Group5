package service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Component
public class ProductImageStorage {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final Path uploadDirectory;

    public ProductImageStorage(@Value("${app.upload.product-dir:uploads/products}") String directory) {
        uploadDirectory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ProductValidationException("imageFile", "Vui lòng chọn ảnh sản phẩm.");
        if (file.getSize() > MAX_BYTES) throw new ProductValidationException("imageFile", "Ảnh không được vượt quá 5 MB.");
        String type = file.getContentType();
        String extension = "image/jpeg".equalsIgnoreCase(type) ? ".jpg"
                : "image/png".equalsIgnoreCase(type) ? ".png" : null;
        if (extension == null || !isDecodableImage(file)) {
            throw new ProductValidationException("imageFile", "Chỉ chấp nhận ảnh JPG hoặc PNG hợp lệ.");
        }
        String fileName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory);
            Path target = uploadDirectory.resolve(fileName).normalize();
            if (!target.startsWith(uploadDirectory)) throw new IOException("Invalid image path");
            file.transferTo(target);
            return "/uploads/products/" + fileName;
        } catch (IOException ex) {
            throw new IllegalStateException("Không lưu được ảnh sản phẩm.", ex);
        }
    }

    public void delete(String imageUrl) {
        String prefix = "/uploads/products/";
        if (imageUrl == null || !imageUrl.startsWith(prefix)) return;
        Path target = uploadDirectory.resolve(imageUrl.substring(prefix.length())).normalize();
        if (!target.startsWith(uploadDirectory)) return;
        try { Files.deleteIfExists(target); } catch (IOException ignored) { }
    }

    private boolean isDecodableImage(MultipartFile file) {
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) return false;
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                return width > 0 && height > 0 && (long) width * height <= 20_000_000L
                        && (format.equals("jpeg") || format.equals("jpg") || format.equals("png"));
            } finally { reader.dispose(); }
        } catch (IOException ex) { return false; }
    }
}
