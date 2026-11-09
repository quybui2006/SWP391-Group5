package entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "checkouts")
public class Checkout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checkout_code", nullable = false, unique = true, length = 40)
    private String checkoutCode;

    @Column(name = "request_key", nullable = false, unique = true, length = 100)
    private String requestKey;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod = "COD";

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "CONFIRMED";

    public Checkout() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCheckoutCode() { return checkoutCode; }
    public void setCheckoutCode(String checkoutCode) { this.checkoutCode = checkoutCode; }

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
