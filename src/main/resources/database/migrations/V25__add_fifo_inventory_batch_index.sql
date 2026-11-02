-- Keep the legacy index because it also supports the inventory_id foreign key.
CREATE INDEX IX_inventory_batches_fifo_received
    ON inventory_batches (inventory_id, received_at, id);
