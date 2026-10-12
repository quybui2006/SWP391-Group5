package service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Cầu nối tạm với người dùng đang đăng nhập.
 *
 * Phần Authentication (User Login / Register - Sheet2 No.1, No.2) do
 * Nguyễn Hồng Hà phụ trách, chưa merge nên chưa có Spring Security.
 * Reads the authenticated shop owner's user id from the login session.
 */
@Service
public class CurrentUserProvider {

    public Long requireUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !"SHOP_OWNER".equals(session.getAttribute("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vui lòng đăng nhập bằng tài khoản Shop Owner.");
        }
        Object userId = session.getAttribute("userId");
        if (!(userId instanceof Number number)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Phiên đăng nhập không hợp lệ.");
        }
        return number.longValue();
    }
}
