package dto.shop;

import java.time.LocalDateTime;

public record InventoryBatchView(Long id, String code, int receivedQuantity, int onHand, int reserved,
                                 int available, int sold, LocalDateTime receivedAt,
                                 String status, String note) { }
