package dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ShopPaymentSetupDto {

    @NotBlank(message = "Vui lòng chọn ngân hàng.")
    private String bankName;

    @NotBlank(message = "Vui lòng nhập số tài khoản ngân hàng.")
    @Size(min = 6, max = 30, message = "Số tài khoản từ 6 đến 30 ký tự.")
    private String bankAccountNumber;

    @NotBlank(message = "Vui lòng nhập tên chủ tài khoản ngân hàng.")
    @Size(min = 3, max = 150, message = "Tên chủ tài khoản từ 3 đến 150 ký tự.")
    private String bankAccountName;

    @Size(max = 150, message = "Chi nhánh không quá 150 ký tự.")
    private String bankBranch;

    public ShopPaymentSetupDto() {
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getBankAccountName() {
        return bankAccountName;
    }

    public void setBankAccountName(String bankAccountName) {
        this.bankAccountName = bankAccountName;
    }

    public String getBankBranch() {
        return bankBranch;
    }

    public void setBankBranch(String bankBranch) {
        this.bankBranch = bankBranch;
    }
}
