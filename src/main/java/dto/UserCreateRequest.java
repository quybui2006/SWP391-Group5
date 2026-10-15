package dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCreateRequest {

    @NotBlank(message = "Họ và tên không được để trống.")
    @Size(max = 150, message = "Họ và tên tối đa 150 ký tự.")
    private String fullName;

    @NotBlank(message = "Email không được để trống.")
    @Email(message = "Email không đúng định dạng.")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống.")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự.")
    private String password;

    private String phone;

    @NotBlank(message = "Vui lòng chọn vai trò ban đầu.")
    private String initialRole = "CUSTOMER";

    private String status = "ACTIVE";
}