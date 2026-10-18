package controller;

import dto.ApprovalActionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {
        OperationsShopApprovalController.class,
        OperationsProductApprovalController.class
})
public class OperationsApprovalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(OperationsApprovalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApprovalActionResponse> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "Không thể xử lý yêu cầu." : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApprovalActionResponse(false, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApprovalActionResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage())
                .orElse("Dữ liệu gửi lên không hợp lệ.");
        return ResponseEntity.badRequest().body(new ApprovalActionResponse(false, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApprovalActionResponse> handleUnreadableBody() {
        return ResponseEntity.badRequest()
                .body(new ApprovalActionResponse(false, "Dữ liệu gửi lên không hợp lệ."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApprovalActionResponse> handleDatabaseError(DataAccessException ex) {
        log.error("Lỗi database trong luồng duyệt Operations Manager", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApprovalActionResponse(false,
                        "Không thể cập nhật trạng thái lúc này. Vui lòng thử lại sau."));
    }
}
