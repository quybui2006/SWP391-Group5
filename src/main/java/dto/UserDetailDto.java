package dto;

import entity.UserAddresses;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailDto {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String status;
    private LocalDateTime emailVerifiedAt;
    private List<String> currentRoleCodes;
    private List<UserAddresses> addresses;
}