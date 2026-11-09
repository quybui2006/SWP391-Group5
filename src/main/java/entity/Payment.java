package entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_code", nullable = false, unique = true, length = 40)
    private String paymentCode;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "request_key", nullable = false, unique = true, length = 100)
    private String requestKey;

    @Column(name = "method", nullable = false, length = 20)
    private String method;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUCCEEDED";

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    public Payment() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPaymentCode() { return paymentCode; }
    public void setPaymentCode(String paymentCode) { this.paymentCode = paymentCode; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
