package service.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class CustomerRefundService {
    private final JdbcTemplate jdbc;

    public CustomerRefundService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void queuePaidOrderRefund(Long orderId, String reason) {
        jdbc.update("""
            INSERT IGNORE INTO customer_refunds(order_id,customer_id,refund_type,reason,refund_amount,bank_name,bank_account_no,bank_account_holder)
            SELECT o.id,c.user_id,'CANCEL_ORDER',?,o.total_amount,b.bank_name,b.bank_account_no,b.bank_account_holder
            FROM orders o
            JOIN checkouts c ON c.id=o.checkout_id
            LEFT JOIN customer_bank_accounts b ON b.user_id=c.user_id
            WHERE o.id=? AND o.payment_status='PAID' AND c.user_id IS NOT NULL
        """, reason, orderId);
        jdbc.update("""
            INSERT IGNORE INTO notifications(user_id,event_key,type,title,content,target_type,target_id)
            SELECT u.id,CONCAT('OP-CUSTOMER-REFUND-',o.id,'-',u.id),'CUSTOMER_REFUND',
                   'Yêu cầu hoàn tiền khách hàng',
                   CONCAT('Đơn ',o.order_code,' đã hủy sau khi thanh toán. Cần hoàn ',o.total_amount,' VND. Lý do: ',?),
                   'CUSTOMER_REFUND',o.id
            FROM orders o
            JOIN checkouts c ON c.id=o.checkout_id
            JOIN users op ON op.status='ACTIVE'
            JOIN user_roles ur ON ur.user_id=op.id
            JOIN roles r ON r.id=ur.role_id AND r.code IN ('OPERATIONS_MANAGER','ADMIN')
            JOIN users u ON u.id=op.id
            WHERE o.id=? AND o.payment_status='PAID' AND c.user_id IS NOT NULL
        """, reason, orderId);
    }

    /**
     * Ghi nhận khoản hoàn tiền khiếu nại (RETURN_REFUND) vào bảng sổ cái customer_refunds.
     * Chống double-refund: Kiểm tra bản ghi tồn tại với khóa (order_id, 'RETURN_REFUND') trong cùng transaction.
     */
    @Transactional
    public void queueComplaintReturnRefund(Long orderId, Long customerId, BigDecimal refundAmount, String reason) {
        if (orderId == null || customerId == null || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Thông tin hoàn tiền khiếu nại không hợp lệ.");
        }

        // Kiểm tra chống double-refund cho cùng đơn hàng với loại RETURN_REFUND
        Long existingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_refunds WHERE order_id = ? AND refund_type = 'RETURN_REFUND'",
                Long.class, orderId);
        if (existingCount != null && existingCount > 0) {
            throw new IllegalArgumentException("Đơn hàng này đã được ghi nhận lệnh hoàn tiền khiếu nại trước đó.");
        }

        String bankName = null;
        String bankAccountNo = null;
        String bankAccountHolder = null;
        try {
            List<Map<String, Object>> banks = jdbc.queryForList(
                    "SELECT bank_name, bank_account_no, bank_account_holder FROM customer_bank_accounts WHERE user_id = ?",
                    customerId);
            if (!banks.isEmpty()) {
                Map<String, Object> b = banks.get(0);
                bankName = b.get("bank_name") != null ? String.valueOf(b.get("bank_name")) : null;
                bankAccountNo = b.get("bank_account_no") != null ? String.valueOf(b.get("bank_account_no")) : null;
                bankAccountHolder = b.get("bank_account_holder") != null ? String.valueOf(b.get("bank_account_holder")) : null;
            }
        } catch (Exception ignored) {
        }

        String cleanReason = (reason == null || reason.isBlank()) ? "Hoàn tiền giải quyết khiếu nại đơn hàng" : reason.trim();
        if (cleanReason.length() > 1000) {
            cleanReason = cleanReason.substring(0, 1000);
        }

        int inserted = jdbc.update("""
            INSERT INTO customer_refunds(order_id, customer_id, refund_type, reason, refund_amount, bank_name, bank_account_no, bank_account_holder, status)
            VALUES (?, ?, 'RETURN_REFUND', ?, ?, ?, ?, ?, 'PENDING')
        """, orderId, customerId, cleanReason, refundAmount, bankName, bankAccountNo, bankAccountHolder);

        if (inserted != 1) {
            throw new IllegalStateException("Không thể khởi tạo bản ghi hoàn tiền cho đơn hàng #" + orderId);
        }
    }
}
