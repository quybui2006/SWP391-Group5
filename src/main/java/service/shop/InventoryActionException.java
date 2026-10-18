package service.shop;

/** Lỗi nghiệp vụ có thể hiển thị an toàn trên giao diện quản lý kho. */
public class InventoryActionException extends RuntimeException {
    public InventoryActionException(String message) {
        super(message);
    }
}
