package service.shop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VoucherUsageService {
    private final JdbcTemplate jdbc;

    /** Dùng chung truy vấn cập nhật số lượt voucher giữa các luồng hủy đơn. */
    public VoucherUsageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Trả lại một lượt voucher giảm tiền sau khi đơn đã chuyển sang CANCELLED. */
    public void returnDiscountUse(Long orderId) {
        jdbc.update("UPDATE vouchers SET used_count = used_count - 1 " +
                "WHERE id = (SELECT voucher_id FROM orders WHERE id = ? AND status = 'CANCELLED') " +
                "AND voucher_type = 'DISCOUNT' AND used_count > 0", orderId);
    }
}
