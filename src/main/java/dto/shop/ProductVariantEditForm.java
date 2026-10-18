package dto.shop;

import java.math.BigDecimal;
import java.util.List;

/** Dữ liệu Shop Owner được phép cập nhật cho một biến thể và tồn kho của nó. */
public class ProductVariantEditForm {
    private Long id;
    private String productName;
    private boolean productApproved;
    private String variantName;
    private String sku;
    private String specification;
    private Long unitId;
    private BigDecimal price;
    private String status;
    private Integer quantityOnHand;
    private BigDecimal lowStockThresholdPct;
    private List<UnitOption> units;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getProductName() { return productName; } public void setProductName(String productName) { this.productName = productName; }
    public boolean isProductApproved() { return productApproved; } public void setProductApproved(boolean productApproved) { this.productApproved = productApproved; }
    public String getVariantName() { return variantName; } public void setVariantName(String variantName) { this.variantName = variantName; }
    public String getSku() { return sku; } public void setSku(String sku) { this.sku = sku; }
    public String getSpecification() { return specification; } public void setSpecification(String specification) { this.specification = specification; }
    public Long getUnitId() { return unitId; } public void setUnitId(Long unitId) { this.unitId = unitId; }
    public BigDecimal getPrice() { return price; } public void setPrice(BigDecimal price) { this.price = price; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public Integer getQuantityOnHand() { return quantityOnHand; } public void setQuantityOnHand(Integer quantityOnHand) { this.quantityOnHand = quantityOnHand; }
    public BigDecimal getLowStockThresholdPct() { return lowStockThresholdPct; } public void setLowStockThresholdPct(BigDecimal value) { this.lowStockThresholdPct = value; }
    public List<UnitOption> getUnits() { return units; } public void setUnits(List<UnitOption> units) { this.units = units; }

    public record UnitOption(Long id, String name) { }
}
