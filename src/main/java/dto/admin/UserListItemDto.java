package dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserListItemDto {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String status;
    private List<String> roleCodes;
}
