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
    private final String paymentApprovalStatus;
    private final String sellerApprovalStatus;
    private final String bankName;
    private final String bankAccountNumber;
    private final String bankAccountName;
    private final String idCardName;

    public ShopApprovalItemDto(
            Long id, String name, String description, String ownerName, String ownerEmail,
            String phone, String provinceCode, String wardName, String addressDetail,
            String approvalStatus, String operatingStatus, LocalDateTime createdAt,
            LocalDateTime reviewedAt, String rejectionReason) {
        this(id, name, description, ownerName, ownerEmail, phone, provinceCode, wardName, addressDetail,
                approvalStatus, operatingStatus, createdAt, reviewedAt, rejectionReason, "UNCONFIGURED", "PENDING", null, null, null, null);
    }

    public ShopApprovalItemDto(
            Long id, String name, String description, String ownerName, String ownerEmail,
            String phone, String provinceCode, String wardName, String addressDetail,
            String approvalStatus, String operatingStatus, LocalDateTime createdAt,
            LocalDateTime reviewedAt, String rejectionReason, String paymentApprovalStatus) {
        this(id, name, description, ownerName, ownerEmail, phone, provinceCode, wardName, addressDetail,
                approvalStatus, operatingStatus, createdAt, reviewedAt, rejectionReason, paymentApprovalStatus, "PENDING", null, null, null, null);
    }

    public ShopApprovalItemDto(
            Long id, String name, String description, String ownerName, String ownerEmail,
            String phone, String provinceCode, String wardName, String addressDetail,
            String approvalStatus, String operatingStatus, LocalDateTime createdAt,
            LocalDateTime reviewedAt, String rejectionReason, String paymentApprovalStatus,
            String sellerApprovalStatus) {
        this(id, name, description, ownerName, ownerEmail, phone, provinceCode, wardName, addressDetail,
                approvalStatus, operatingStatus, createdAt, reviewedAt, rejectionReason, paymentApprovalStatus, sellerApprovalStatus, null, null, null, null);
    }

    public ShopApprovalItemDto(
            Long id, String name, String description, String ownerName, String ownerEmail,
            String phone, String provinceCode, String wardName, String addressDetail,
            String approvalStatus, String operatingStatus, LocalDateTime createdAt,
            LocalDateTime reviewedAt, String rejectionReason, String paymentApprovalStatus,
            String sellerApprovalStatus, String bankName, String bankAccountNumber,
            String bankAccountName, String idCardName) {
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
        this.paymentApprovalStatus = paymentApprovalStatus != null ? paymentApprovalStatus : "UNCONFIGURED";
        this.sellerApprovalStatus = sellerApprovalStatus != null ? sellerApprovalStatus : "PENDING";
        this.bankName = bankName;
        this.bankAccountNumber = bankAccountNumber;
        this.bankAccountName = bankAccountName;
        this.idCardName = idCardName;
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
    public String getPaymentApprovalStatus() { return paymentApprovalStatus; }
    public String getSellerApprovalStatus() { return sellerApprovalStatus; }
    public String getBankName() { return bankName; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public String getBankAccountName() { return bankAccountName; }
    public String getIdCardName() { return idCardName; }

    public boolean isBankAccountMatchesIdCard() {
        if (idCardName == null || bankAccountName == null) return false;
        String cccdClean = removeAccents(idCardName.trim().toLowerCase());
        String bankClean = removeAccents(bankAccountName.trim().toLowerCase());
        return !cccdClean.isBlank() && cccdClean.equals(bankClean);
    }

    private static String removeAccents(String str) {
        if (str == null) return "";
        String normalized = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
                .replace("đ", "d").replace("Đ", "D")
                .trim().toLowerCase();
    }
}
