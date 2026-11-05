package dto.shop;

import java.time.LocalDate;

public record InventoryBatchOption(Long id, String code, String productName, String variantName,
                                   String sku, String unitName, int available, LocalDate expiresAt) {
    public InventoryBatchOption {
        code = InventoryCodeFormatter.compact(code);
    }
}
