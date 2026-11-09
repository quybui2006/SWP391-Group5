-- Preserve each shop's existing threshold as a count in the variant's own unit.
ALTER TABLE variant_inventory
    ADD COLUMN low_stock_threshold_quantity INT NOT NULL DEFAULT 0;

UPDATE variant_inventory
SET low_stock_threshold_quantity = CEIL(initial_quantity * low_stock_threshold_pct / 100);

-- The old check is unnamed in V1; MySQL assigns this name by table definition order.
ALTER TABLE variant_inventory
    DROP CHECK variant_inventory_chk_4,
    DROP COLUMN low_stock_threshold_pct,
    ADD CONSTRAINT CK_variant_inventory_low_stock_threshold
        CHECK (low_stock_threshold_quantity >= 0);
