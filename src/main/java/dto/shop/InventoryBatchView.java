package dto.shop;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record InventoryBatchView(Long id, String code, int receivedQuantity, int onHand, int reserved,
                                 int available, int sold, LocalDateTime receivedAt,
                                 LocalDate expiresAt, String status, String note) {
    public InventoryBatchView {
        code = InventoryCodeFormatter.compact(code);
    }
}
