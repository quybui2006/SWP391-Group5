package entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Bảng shops - cửa hàng của Shop Owner.
 *
 * Schema có UNIQUE (owner_id): mỗi tài khoản tối đa một cửa hàng.
 * Ràng buộc CHECK:
 *   - operating_status = 'OPEN' thì approval_status phải là 'APPROVED'
 *   - approval_status khác 'PENDING' thì reviewed_at phải khác null
 */
@Entity
@Table(name = "shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, unique = true)
    private Long ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "province_code", nullable = false)
    private String provinceCode;

    @Column(name = "ward_name", nullable = false)
    private String wardName;

    @Column(name = "address_detail", nullable = false)
    private String addressDetail;

    /** PENDING / APPROVED / REJECTED */
    @Column(name = "approval_status", nullable = false)
    private String approvalStatus = "PENDING";

    /** OPEN / CLOSED / SUSPENDED */
    @Column(name = "operating_status", nullable = false)
    private String operatingStatus = "CLOSED";

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "suspension_reason", length = 1000)
    private String suspensionReason;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "id_card_number", length = 50)
    private String idCardNumber;

    @Column(name = "id_card_name", length = 150)
    private String idCardName;

    @Column(name = "id_card_date")
    private java.time.LocalDate idCardDate;

    @Column(name = "id_card_place", length = 150)
    private String idCardPlace;

    @Column(name = "business_type", length = 50)
    private String businessType = "INDIVIDUAL";

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "tax_name", length = 150)
    private String taxName;

    @Column(name = "tax_email", length = 254)
    private String taxEmail;

    @Column(name = "shipping_methods", length = 255)
    private String shippingMethods;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_name", length = 150)
    private String bankAccountName;

    @Column(name = "bank_branch", length = 150)
    private String bankBranch;

    /** UNCONFIGURED / PENDING / APPROVED / REJECTED */
    @Column(name = "payment_approval_status", length = 50, nullable = false)
    private String paymentApprovalStatus = "UNCONFIGURED";

    @Column(name = "bank_rejection_reason", length = 1000)
    private String bankRejectionReason;

    @Column(name = "payment_reviewed_at")
    private LocalDateTime paymentReviewedAt;

    /** PENDING / APPROVED / REJECTED */
    @Column(name = "seller_approval_status", length = 50, nullable = false)
    private String sellerApprovalStatus = "PENDING";

    @Column(name = "seller_rejection_reason", length = 1000)
    private String sellerRejectionReason;

    @Column(name = "seller_reviewed_at")
    private LocalDateTime sellerReviewedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public String getWardName() { return wardName; }
    public void setWardName(String wardName) { this.wardName = wardName; }
    public String getAddressDetail() { return addressDetail; }
    public void setAddressDetail(String addressDetail) { this.addressDetail = addressDetail; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getOperatingStatus() { return operatingStatus; }
    public void setOperatingStatus(String operatingStatus) { this.operatingStatus = operatingStatus; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getSuspensionReason() { return suspensionReason; }
    public void setSuspensionReason(String suspensionReason) { this.suspensionReason = suspensionReason; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getIdCardNumber() { return idCardNumber; }
    public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber; }
    public String getIdCardName() { return idCardName; }
    public void setIdCardName(String idCardName) { this.idCardName = idCardName; }
    public java.time.LocalDate getIdCardDate() { return idCardDate; }
    public void setIdCardDate(java.time.LocalDate idCardDate) { this.idCardDate = idCardDate; }
    public String getIdCardPlace() { return idCardPlace; }
    public void setIdCardPlace(String idCardPlace) { this.idCardPlace = idCardPlace; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public String getTaxName() { return taxName; }
    public void setTaxName(String taxName) { this.taxName = taxName; }
    public String getTaxEmail() { return taxEmail; }
    public void setTaxEmail(String taxEmail) { this.taxEmail = taxEmail; }
    public String getShippingMethods() { return shippingMethods; }
    public void setShippingMethods(String shippingMethods) { this.shippingMethods = shippingMethods; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }

    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String bankAccountName) { this.bankAccountName = bankAccountName; }

    public String getBankBranch() { return bankBranch; }
    public void setBankBranch(String bankBranch) { this.bankBranch = bankBranch; }

    public String getPaymentApprovalStatus() { return paymentApprovalStatus; }
    public void setPaymentApprovalStatus(String paymentApprovalStatus) { this.paymentApprovalStatus = paymentApprovalStatus; }

    public String getBankRejectionReason() { return bankRejectionReason; }
    public void setBankRejectionReason(String bankRejectionReason) { this.bankRejectionReason = bankRejectionReason; }

    public LocalDateTime getPaymentReviewedAt() { return paymentReviewedAt; }
    public void setPaymentReviewedAt(LocalDateTime paymentReviewedAt) { this.paymentReviewedAt = paymentReviewedAt; }

    public String getSellerApprovalStatus() { return sellerApprovalStatus; }
    public void setSellerApprovalStatus(String sellerApprovalStatus) { this.sellerApprovalStatus = sellerApprovalStatus; }

    public String getSellerRejectionReason() { return sellerRejectionReason; }
    public void setSellerRejectionReason(String sellerRejectionReason) { this.sellerRejectionReason = sellerRejectionReason; }

    public LocalDateTime getSellerReviewedAt() { return sellerReviewedAt; }
    public void setSellerReviewedAt(LocalDateTime sellerReviewedAt) { this.sellerReviewedAt = sellerReviewedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
