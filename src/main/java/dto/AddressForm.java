package dto;

public class AddressForm {
    private String recipientName;
    private String recipientPhone;
    private String provinceCode;
    private String districtName;
    private String wardName;
    private String addressDetail;
    private boolean defaultAddress;

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String value) { recipientName = value; }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String value) { recipientPhone = value; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String value) { provinceCode = value; }
    public String getDistrictName() { return districtName; }
    public void setDistrictName(String value) { districtName = value; }
    public String getWardName() { return wardName; }
    public void setWardName(String value) { wardName = value; }
    public String getAddressDetail() { return addressDetail; }
    public void setAddressDetail(String value) { addressDetail = value; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(boolean value) { defaultAddress = value; }
}
