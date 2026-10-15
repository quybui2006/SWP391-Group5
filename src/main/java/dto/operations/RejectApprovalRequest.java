package dto.operations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectApprovalRequest {
    @NotBlank(message = "Vui lòng nhập lý do từ chối.")
    @Size(max = 1000, message = "Lý do từ chối không được vượt quá 1000 ký tự.")
    private String rejectionReason;

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
