package dto.operations;

public record SubmitProductApprovalResponse(boolean success, String message, String approvalStatus) {
}
