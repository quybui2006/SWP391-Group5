package service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dữ liệu không hợp lệ khi lưu, gắn với từng ô trong form để hiển thị
 * ngay bên dưới trường nhập.
 */
public class ProductValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ProductValidationException(String field, String message) {
        super(message);
        this.fieldErrors = new LinkedHashMap<>();
        this.fieldErrors.put(field, message);
    }

    public ProductValidationException(Map<String, String> fieldErrors) {
        super("Dữ liệu chưa hợp lệ.");
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
