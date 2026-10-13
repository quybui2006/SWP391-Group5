package dto;

import java.time.LocalDateTime;

/** Data cần thiết để Operations Manager duyệt yêu cầu mở Shop. */
public class PendingShopApprovalDto {
    private final Long id;
    private final String name;
    private final String ownerName;
    private final String ownerEmail;
    private final String phone;
    private final String provinceCode;
    private final String wardName;
    private final String addressDetail;
    private final String description;
    private final LocalDateTime createdAt;

    public PendingShopApprovalDto(Long id, String name, String ownerName, String ownerEmail,
                                  String phone, String provinceCode, String wardName,
                                  String addressDetail, String description, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.ownerName = ownerName;
        this.ownerEmail = ownerEmail;
        this.phone = phone;
        this.provinceCode = provinceCode;
        this.wardName = wardName;
        this.addressDetail = addressDetail;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getPhone() { return phone; }
    public String getProvinceCode() { return provinceCode; }
    public String getWardName() { return wardName; }
    public String getAddressDetail() { return addressDetail; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
