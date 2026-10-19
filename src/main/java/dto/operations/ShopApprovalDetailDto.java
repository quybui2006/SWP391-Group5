package dto.operations;

import java.time.LocalDateTime;

public class ShopApprovalDetailDto {
    private Long id;
    private String name;
    private String description;
    private String phone;
    private String provinceCode;
    private String provinceName;
    private String wardName;
    private String addressDetail;
    private String fullAddress;
    private String approvalStatus;
    private String operatingStatus;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private String suspensionReason;

    // Seller Activation & Onboarding Info
    private String idCardNumber;
    private String idCardName;
    private String idCardDate;
    private String idCardPlace;
    private String businessType;
    private String taxCode;
    private String taxName;
    private String taxEmail;
    private String shippingMethods;

    // Owner Info
    private Long ownerId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private LocalDateTime ownerCreatedAt;
    private String ownerStatus;

    // Stats
    private long productCount;
    private long orderCount;
    private long totalRevenue;

    public ShopApprovalDetailDto() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; }
    public String getWardName() { return wardName; }
    public void setWardName(String wardName) { this.wardName = wardName; }
    public String getAddressDetail() { return addressDetail; }
    public void setAddressDetail(String addressDetail) { this.addressDetail = addressDetail; }
    public String getFullAddress() { return fullAddress; }
    public void setFullAddress(String fullAddress) { this.fullAddress = fullAddress; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getOperatingStatus() { return operatingStatus; }
    public void setOperatingStatus(String operatingStatus) { this.operatingStatus = operatingStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getSuspensionReason() { return suspensionReason; }
    public void setSuspensionReason(String suspensionReason) { this.suspensionReason = suspensionReason; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public LocalDateTime getOwnerCreatedAt() { return ownerCreatedAt; }
    public void setOwnerCreatedAt(LocalDateTime ownerCreatedAt) { this.ownerCreatedAt = ownerCreatedAt; }
    public String getOwnerStatus() { return ownerStatus; }
    public void setOwnerStatus(String ownerStatus) { this.ownerStatus = ownerStatus; }
    public long getProductCount() { return productCount; }
    public void setProductCount(long productCount) { this.productCount = productCount; }
    public long getOrderCount() { return orderCount; }
    public void setOrderCount(long orderCount) { this.orderCount = orderCount; }
    public long getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(long totalRevenue) { this.totalRevenue = totalRevenue; }

    public String getIdCardNumber() { return idCardNumber; }
    public void setIdCardNumber(String idCardNumber) { this.idCardNumber = idCardNumber; }
    public String getIdCardName() { return idCardName; }
    public void setIdCardName(String idCardName) { this.idCardName = idCardName; }
    public String getIdCardDate() { return idCardDate; }
    public void setIdCardDate(String idCardDate) { this.idCardDate = idCardDate; }
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
}
