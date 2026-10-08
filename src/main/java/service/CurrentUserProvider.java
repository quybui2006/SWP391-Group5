package service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import repository.ShopRepository;

/**
 * Cầu nối tạm với người dùng đang đăng nhập.
 *
 * Phần Authentication (User Login / Register - Sheet2 No.1, No.2) do
 * Nguyễn Hồng Hà phụ trách, chưa merge nên chưa có Spring Security.
 * Tạm lấy chủ của cửa hàng đầu tiên trong DB để chạy được demo.
 *
 * TODO(auth): khi nhánh Auth xong, thay phần requireUserId bằng
 *   SecurityContextHolder.getContext().getAuthentication().getName()
 *   rồi tra cứu user theo email, và bỏ hết class này.
 *
 * LƯU Ý: cách làm tạm này KHÔNG an toàn. Mọi shop đều thấy cùng một danh
 * sách sản phẩm. Phải thay trước khi nộp bài.
 */
@Service
public class CurrentUserProvider {

    @Autowired
    private ShopRepository shopRepository;

    public Long requireUserId() {
        return shopRepository.findAll().stream()
                .map(shop -> shop.getOwnerId())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Chưa có cửa hàng nào trong hệ thống. Hãy chạy seed-local.sql."));
    }
}
