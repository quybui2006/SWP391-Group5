package controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import service.ProductNotFoundException;

/**
 * Chuyển lỗi tầng service thành trang web gọn gàng.
 */
@ControllerAdvice
public class ShopExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ShopExceptionHandler.class);

    /**
     * Sản phẩm không tồn tại, hoặc không thuộc shop của người đang đăng nhập.
     * Cả hai trường hợp đều trả 404 để không lộ ra việc sản phẩm tồn tại.
     */
    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleProductNotFound(ProductNotFoundException ex) {
        log.info("Không tìm thấy sản phẩm: {}", ex.getMessage());
        return "error/404";
    }

    /**
     * Lỗi ràng buộc database (foreign key, CHECK, UNIQUE) mà Java chưa bắt
     * được. Trả trang lỗi thay vì màn hình 500 trần.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Vi phạm ràng buộc database: {}", ex.getMostSpecificCause().getMessage());
        return "error/400";
    }
}
