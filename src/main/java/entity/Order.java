package entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 40)
    private String orderCode;

    @Column(name = "checkout_id", nullable = false)
    private Long checkoutId;

    @Column(name = "original_order_id")
    private Long originalOrderId;

    @Column(name = "order_type", length = 30)
    private String orderType = "STANDARD";

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "delivery_province_code", nullable = false, length = 20)
    private String deliveryProvinceCode;

    @Column(name = "delivery_province_name", nullable = false, length = 100)
    private String deliveryProvinceName;

    @Column(name = "delivery_ward_name", nullable = false, length = 150)
    private String deliveryWardName;

    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;

    @Column(name = "shop_province_code", nullable = false, length = 20)
    private String shopProvinceCode;

    @Column(name = "customer_note", length = 1000)
    private String customerNote;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 0)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "points_used", nullable = false)
    private Long pointsUsed = 0L;

    @Column(name = "points_discount", nullable = false, precision = 19, scale = 0)
    private BigDecimal pointsDiscount = BigDecimal.ZERO;

    @Column(name = "points_subsidy_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal pointsSubsidyAmount = BigDecimal.ZERO;

    @Column(name = "qualifying_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal qualifyingAmount = BigDecimal.ZERO;

    @Column(name = "points_earned", nullable = false)
    private Long pointsEarned = 0L;

    @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 0)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "shop_revenue_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal shopRevenueAmount = BigDecimal.ZERO;

    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "voucher_code", length = 50)
    private String voucherCode;

    @Column(name = "voucher_discount", nullable = false, precision = 19, scale = 0)
    private BigDecimal voucherDiscount = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod = "COD";

    @Column(name = "delivery_code", nullable = false, unique = true, length = 60)
    private String deliveryCode;

    @Column(name = "status", nullable = false, length = 25)
    private String status = "PENDING";

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus = "UNPAID";

    @Column(name = "delivery_status", nullable = false, length = 25)
    private String deliveryStatus = "NOT_STARTED";

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "is_settled", nullable = false)
    private Integer isSettled = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Order() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public Long getCheckoutId() { return checkoutId; }
    public void setCheckoutId(Long checkoutId) { this.checkoutId = checkoutId; }

    public Long getOriginalOrderId() { return originalOrderId; }
    public void setOriginalOrderId(Long originalOrderId) { this.originalOrderId = originalOrderId; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getDeliveryProvinceCode() { return deliveryProvinceCode; }
    public void setDeliveryProvinceCode(String deliveryProvinceCode) { this.deliveryProvinceCode = deliveryProvinceCode; }

    public String getDeliveryProvinceName() { return deliveryProvinceName; }
    public void setDeliveryProvinceName(String deliveryProvinceName) { this.deliveryProvinceName = deliveryProvinceName; }

    public String getDeliveryWardName() { return deliveryWardName; }
    public void setDeliveryWardName(String deliveryWardName) { this.deliveryWardName = deliveryWardName; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public String getShopProvinceCode() { return shopProvinceCode; }
    public void setShopProvinceCode(String shopProvinceCode) { this.shopProvinceCode = shopProvinceCode; }

    public String getCustomerNote() { return customerNote; }
    public void setCustomerNote(String customerNote) { this.customerNote = customerNote; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public Long getPointsUsed() { return pointsUsed; }
    public void setPointsUsed(Long pointsUsed) { this.pointsUsed = pointsUsed; }

    public BigDecimal getPointsDiscount() { return pointsDiscount; }
    public void setPointsDiscount(BigDecimal pointsDiscount) { this.pointsDiscount = pointsDiscount; }

    public BigDecimal getPointsSubsidyAmount() { return pointsSubsidyAmount; }
    public void setPointsSubsidyAmount(BigDecimal pointsSubsidyAmount) { this.pointsSubsidyAmount = pointsSubsidyAmount; }

    public BigDecimal getQualifyingAmount() { return qualifyingAmount; }
    public void setQualifyingAmount(BigDecimal qualifyingAmount) { this.qualifyingAmount = qualifyingAmount; }

    public Long getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(Long pointsEarned) { this.pointsEarned = pointsEarned; }

    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getShopRevenueAmount() { return shopRevenueAmount; }
    public void setShopRevenueAmount(BigDecimal shopRevenueAmount) { this.shopRevenueAmount = shopRevenueAmount; }

    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }

    public BigDecimal getVoucherDiscount() { return voucherDiscount; }
    public void setVoucherDiscount(BigDecimal voucherDiscount) { this.voucherDiscount = voucherDiscount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getDeliveryCode() { return deliveryCode; }
    public void setDeliveryCode(String deliveryCode) { this.deliveryCode = deliveryCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public Integer getIsSettled() { return isSettled; }
    public void setIsSettled(Integer isSettled) { this.isSettled = isSettled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
