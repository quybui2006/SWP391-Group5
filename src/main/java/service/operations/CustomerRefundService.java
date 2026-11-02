package service.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CustomerRefundService {
    private final JdbcTemplate jdbc;

    public CustomerRefundService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
}
