package dto.shop;

import java.util.List;

public record InventoryProductView(Long id, String name, String imageUrl, String categories, int onHand,
                                   int reserved, int available, int sold, List<InventoryVariantView> variants) { }
