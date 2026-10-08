package service;

/**
 * Sản phẩm không tồn tại, hoặc không thuộc shop của người đang đăng nhập.
 *
 * Trường hợp thứ hai trả về 404 chứ không phải 403, để không lộ ra
 * việc sản phẩm đó tồn tại.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long productId) {
        super("Không tìm thấy sản phẩm với id=" + productId);
    }

    public ProductNotFoundException() {
        super("Không tìm thấy sản phẩm");
    }
}
