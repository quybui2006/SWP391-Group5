package dto.operations;

import java.time.LocalDateTime;

public class ShopApprovalItemDto {
    private final Long id;
    private final String name;
    private final String description;
    private final String ownerName;
    private final String ownerEmail;
    private final String phone;
    private final String provinceCode;
    private final String wardName;
    private final String addressDetail;
    private final String approvalStatus;
    private final String operatingStatus;
    private final LocalDateTime createdAt;
    private final LocalDateTime reviewedAt;
    private final String rejectionReason;

    public ShopApprovalItemDto(
            Long id, String name, String description, String ownerName, String ownerEmail,
            String phone, String provinceCode, String wardName, String addressDetail,
            String approvalStatus, String operatingStatus, LocalDateTime createdAt,
            LocalDateTime reviewedAt, String rejectionReason) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ownerName = ownerName;
        this.ownerEmail = ownerEmail;
        this.phone = phone;
        this.provinceCode = provinceCode;
        this.wardName = wardName;
        this.addressDetail = addressDetail;
        this.approvalStatus = approvalStatus;
        this.operatingStatus = operatingStatus;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getPhone() { return phone; }
    public String getProvinceCode() { return provinceCode; }
    public String getWardName() { return wardName; }
    public String getAddressDetail() { return addressDetail; }
    public String getApprovalStatus() { return approvalStatus; }
    public String getOperatingStatus() { return operatingStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
}
