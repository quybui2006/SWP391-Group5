package dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ShopRegistrationDto {

    @NotBlank(message = "Vui lòng nhập tên cửa hàng.")
    @Size(min = 3, max = 160, message = "Tên cửa hàng phải từ 3 đến 160 ký tự.")
    private String name;

    @Size(max = 2000, message = "Mô tả cửa hàng không quá 2000 ký tự.")
    private String description;

    @NotBlank(message = "Vui lòng nhập số điện thoại liên hệ.")
    @Pattern(regexp = "0[35789][0-9]{8}", message = "Số điện thoại không hợp lệ (gồm 10 số, bắt đầu bằng 03, 05, 07, 08, 09).")
    private String phone;

    @NotBlank(message = "Vui lòng chọn Tỉnh/Thành phố.")
    private String provinceCode;

    @NotBlank(message = "Vui lòng nhập Phường/Xã/Thị trấn.")
    @Size(max = 150, message = "Phường/Xã không quá 150 ký tự.")
    private String wardName;

    @NotBlank(message = "Vui lòng nhập địa chỉ cụ thể.")
    @Size(max = 500, message = "Địa chỉ cụ thể không quá 500 ký tự.")
    private String addressDetail;

    // --- Thông tin định danh người bán (CCCD / Hộ chiếu) ---
    private String idCardNumber;
    private String idCardName;
    private String idCardDate;
    private String idCardPlace;

    // --- Thông tin thuế ---
    private String businessType = "INDIVIDUAL";
    private String taxCode;
    private String taxName;
    private String taxEmail;

    // --- Cài đặt vận chuyển ---
    private boolean shippingFast = true;
    private boolean shippingExpress = true;
    private boolean shippingStandard = true;

    private boolean agreeTerms;

    public ShopRegistrationDto() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode;
    }

    public String getWardName() {
        return wardName;
    }

    public void setWardName(String wardName) {
        this.wardName = wardName;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public void setAddressDetail(String addressDetail) {
        this.addressDetail = addressDetail;
    }

    public boolean isAgreeTerms() {
        return agreeTerms;
    }

    public void setAgreeTerms(boolean agreeTerms) {
        this.agreeTerms = agreeTerms;
    }

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

    public boolean isShippingFast() { return shippingFast; }
    public void setShippingFast(boolean shippingFast) { this.shippingFast = shippingFast; }

    public boolean isShippingExpress() { return shippingExpress; }
    public void setShippingExpress(boolean shippingExpress) { this.shippingExpress = shippingExpress; }

    public boolean isShippingStandard() { return shippingStandard; }
    public void setShippingStandard(boolean shippingStandard) { this.shippingStandard = shippingStandard; }
}
