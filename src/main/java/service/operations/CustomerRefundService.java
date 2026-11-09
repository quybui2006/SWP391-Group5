package service.operations;

import entity.CustomerBankAccount;
import entity.CustomerRefund;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.CustomerBankAccountRepository;
import repository.CustomerRefundRepository;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class CustomerRefundService {
    private static final String REFUND_TYPE_RETURN_REFUND = "RETURN_REFUND";

    private final CustomerRefundRepository customerRefundRepository;
    private final CustomerBankAccountRepository customerBankAccountRepository;

    public CustomerRefundService(
            CustomerRefundRepository customerRefundRepository,
            CustomerBankAccountRepository customerBankAccountRepository) {
        this.customerRefundRepository = customerRefundRepository;
        this.customerBankAccountRepository = customerBankAccountRepository;
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
        if (customerRefundRepository.existsByOrderIdAndRefundType(orderId, REFUND_TYPE_RETURN_REFUND)) {
            throw new IllegalArgumentException("Đơn hàng này đã được ghi nhận lệnh hoàn tiền khiếu nại trước đó.");
        }

        String bankName = null;
        String bankAccountNo = null;
        String bankAccountHolder = null;
        Optional<CustomerBankAccount> bankOpt = customerBankAccountRepository.findById(customerId);
        if (bankOpt.isPresent()) {
            CustomerBankAccount b = bankOpt.get();
            bankName = b.getBankName();
            bankAccountNo = b.getBankAccountNo();
            bankAccountHolder = b.getBankAccountHolder();
        }

        String cleanReason = (reason == null || reason.isBlank()) ? "Hoàn tiền giải quyết khiếu nại đơn hàng" : reason.trim();
        if (cleanReason.length() > 1000) {
            cleanReason = cleanReason.substring(0, 1000);
        }

        CustomerRefund refund = new CustomerRefund();
        refund.setOrderId(orderId);
        refund.setCustomerId(customerId);
        refund.setRefundType(REFUND_TYPE_RETURN_REFUND);
        refund.setReason(cleanReason);
        refund.setRefundAmount(refundAmount);
        refund.setBankName(bankName);
        refund.setBankAccountNo(bankAccountNo);
        refund.setBankAccountHolder(bankAccountHolder);
        refund.setStatus("PENDING");

        customerRefundRepository.save(refund);
    }
}
