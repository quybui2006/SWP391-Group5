package dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Data cần thiết để Operations Manager duyệt sản phẩm. */
public class PendingProductApprovalDto {
    private final Long id;
    private final String name;
    private final String batchCode;
    private final String shopName;
    private final String origin;
    private final LocalDate receivedDate;
    private final LocalDate expiryDate;
    private final LocalDateTime submittedAt;

    public PendingProductApprovalDto(Long id, String name, String batchCode, String shopName,
                                     String origin, LocalDate receivedDate, LocalDate expiryDate,
                                     LocalDateTime submittedAt) {
        this.id = id;
        this.name = name;
        this.batchCode = batchCode;
        this.shopName = shopName;
        this.origin = origin;
        this.receivedDate = receivedDate;
        this.expiryDate = expiryDate;
        this.submittedAt = submittedAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBatchCode() { return batchCode; }
    public String getShopName() { return shopName; }
    public String getOrigin() { return origin; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
