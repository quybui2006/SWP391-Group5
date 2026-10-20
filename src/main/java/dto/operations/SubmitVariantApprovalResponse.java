package dto.operations;

/** Kết quả Shop Owner gửi một biến thể vào hàng đợi duyệt. */
public record SubmitVariantApprovalResponse(boolean success, String message, String approvalStatus) {
}
