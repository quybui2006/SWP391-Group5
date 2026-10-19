package dto.seller;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SellerCreateShopDto {

    @NotBlank(message = "Vui lòng nhập tên cửa hàng.")
    @Size(min = 3, max = 160, message = "Tên cửa hàng phải từ 3 đến 160 ký tự.")
    private String name;

    @Size(max = 2000, message = "Mô tả cửa hàng không quá 2000 ký tự.")
    private String description;

    @NotBlank(message = "Vui lòng nhập số điện thoại hotline gian hàng.")
    @Pattern(regexp = "0[35789][0-9]{8}", message = "Số điện thoại hotline không hợp lệ (gồm 10 số).")
    private String phone;

    @NotBlank(message = "Vui lòng chọn Tỉnh/Thành phố kho lấy hàng.")
    private String provinceCode;

    @NotBlank(message = "Vui lòng nhập Phường/Xã kho lấy hàng.")
    @Size(max = 150, message = "Phường/Xã không quá 150 ký tự.")
    private String wardName;

    @NotBlank(message = "Vui lòng nhập địa chỉ kho lấy hàng chi tiết.")
    @Size(max = 500, message = "Địa chỉ kho không quá 500 ký tự.")
    private String addressDetail;

    private boolean shippingFast = true;
    private boolean shippingExpress = true;
    private boolean shippingStandard = true;

    @AssertTrue(message = "Bạn cần đồng ý với Quy chế hoạt động của FreshFruit.")
    private boolean agreeTerms = true;

    public SellerCreateShopDto() {
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

    public boolean isShippingFast() {
        return shippingFast;
    }

    public void setShippingFast(boolean shippingFast) {
        this.shippingFast = shippingFast;
    }

    public boolean isShippingExpress() {
        return shippingExpress;
    }

    public void setShippingExpress(boolean shippingExpress) {
        this.shippingExpress = shippingExpress;
    }

    public boolean isShippingStandard() {
        return shippingStandard;
    }

    public void setShippingStandard(boolean shippingStandard) {
        this.shippingStandard = shippingStandard;
    }

    public boolean isAgreeTerms() {
        return agreeTerms;
    }

    public void setAgreeTerms(boolean agreeTerms) {
        this.agreeTerms = agreeTerms;
    }
}
