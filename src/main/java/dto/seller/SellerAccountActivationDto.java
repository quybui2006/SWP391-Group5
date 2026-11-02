package dto.seller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SellerAccountActivationDto {

    @NotBlank(message = "Vui lòng nhập số điện thoại liên hệ.")
    @Pattern(regexp = "0[35789][0-9]{8}", message = "Số điện thoại không hợp lệ (gồm 10 số, bắt đầu bằng 03, 05, 07, 08, 09).")
    private String phone;

    @NotBlank(message = "Vui lòng nhập email liên hệ.")
    private String email;

    @NotBlank(message = "Vui lòng nhập số CCCD / Hộ chiếu.")
    @Size(min = 9, max = 20, message = "Số CCCD / Hộ chiếu từ 9 đến 20 ký tự.")
    private String idCardNumber;

    @NotBlank(message = "Vui lòng nhập họ và tên trên CCCD.")
    @Size(min = 2, max = 150, message = "Họ và tên từ 2 đến 150 ký tự.")
    private String idCardName;

    @NotBlank(message = "Vui lòng nhập ngày cấp CCCD.")
    private String idCardDate;

    @NotBlank(message = "Vui lòng nhập nơi cấp CCCD.")
    @Size(max = 150, message = "Nơi cấp không quá 150 ký tự.")
    private String idCardPlace;

    @NotBlank(message = "Vui lòng chọn loại hình kinh doanh.")
    private String businessType = "INDIVIDUAL";

    private String taxCode;
    private String taxName;
    private String taxEmail;

    public SellerAccountActivationDto() {
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIdCardNumber() {
        return idCardNumber;
    }

    public void setIdCardNumber(String idCardNumber) {
        this.idCardNumber = idCardNumber;
    }

    public String getIdCardName() {
        return idCardName;
    }

    public void setIdCardName(String idCardName) {
        this.idCardName = idCardName;
    }

    public String getIdCardDate() {
        return idCardDate;
    }

    public void setIdCardDate(String idCardDate) {
        this.idCardDate = idCardDate;
    }

    public String getIdCardPlace() {
        return idCardPlace;
    }

    public void setIdCardPlace(String idCardPlace) {
        this.idCardPlace = idCardPlace;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    public String getTaxName() {
        return taxName;
    }

    public void setTaxName(String taxName) {
        this.taxName = taxName;
    }

    public String getTaxEmail() {
        return taxEmail;
    }

    public void setTaxEmail(String taxEmail) {
        this.taxEmail = taxEmail;
    }
}
