CREATE INDEX IX_inventory_batches_fifo
    ON inventory_batches (inventory_id, status, expires_at, received_at, id);