CREATE TABLE inventory_batches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inventory_id BIGINT NOT NULL,
    batch_code VARCHAR(40) NULL,
    received_quantity INT NOT NULL,
    quantity_on_hand INT NOT NULL,
    reserved_quantity INT NOT NULL DEFAULT 0,
    available_quantity INT GENERATED ALWAYS AS (quantity_on_hand - reserved_quantity) STORED,
    received_at DATETIME(3) NOT NULL,
    expires_at DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    note VARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY UX_inventory_batches_code (batch_code),
    KEY IX_inventory_batches_fifo (inventory_id, expires_at, received_at, id),
    CONSTRAINT FK_inventory_batches_inventory FOREIGN KEY (inventory_id) REFERENCES variant_inventory(id),
    CONSTRAINT FK_inventory_batches_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT CK_inventory_batches_received CHECK (received_quantity > 0),
    CONSTRAINT CK_inventory_batches_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT CK_inventory_batches_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity_on_hand),
    CONSTRAINT CK_inventory_batches_dates CHECK (expires_at IS NULL OR expires_at >= DATE(received_at)),
    CONSTRAINT CK_inventory_batches_status CHECK (status IN ('ACTIVE', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_item_batch_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_item_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY UX_order_item_batch (order_item_id, batch_id),
    KEY IX_batch_allocations_status (batch_id, status),
    CONSTRAINT FK_batch_allocations_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT FK_batch_allocations_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),
    CONSTRAINT CK_batch_allocations_quantity CHECK (quantity > 0),
    CONSTRAINT CK_batch_allocations_status CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_batch_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    order_item_id BIGINT NULL,
    type VARCHAR(20) NOT NULL,
    on_hand_delta INT NOT NULL,
    reserved_delta INT NOT NULL,
    on_hand_after INT NOT NULL,
    reserved_after INT NOT NULL,
    event_key VARCHAR(150) NOT NULL,
    reason VARCHAR(1000) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY UX_inventory_batch_event (event_key),
    KEY IX_inventory_batch_history (batch_id, created_at DESC),
    CONSTRAINT FK_inventory_batch_tx_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),
    CONSTRAINT FK_inventory_batch_tx_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT FK_inventory_batch_tx_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT CK_inventory_batch_tx_type CHECK (type IN ('OPENING', 'RECEIVE', 'ADJUST', 'RESERVE', 'RELEASE', 'SALE', 'DISCARD')),
    CONSTRAINT CK_inventory_batch_tx_on_hand CHECK (on_hand_after >= 0),
    CONSTRAINT CK_inventory_batch_tx_reserved CHECK (reserved_after >= 0 AND reserved_after <= on_hand_after),
    CONSTRAINT CK_inventory_batch_tx_delta CHECK (
        (type = 'OPENING' AND on_hand_delta >= 0 AND reserved_delta >= 0)
        OR (type = 'RECEIVE' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = 'ADJUST' AND on_hand_delta <> 0 AND reserved_delta = 0 AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0)
        OR (type = 'RESERVE' AND on_hand_delta = 0 AND reserved_delta > 0 AND order_item_id IS NOT NULL)
        OR (type = 'RELEASE' AND on_hand_delta = 0 AND reserved_delta < 0 AND order_item_id IS NOT NULL)
        OR (type = 'SALE' AND on_hand_delta < 0 AND reserved_delta IN (0, on_hand_delta) AND order_item_id IS NOT NULL)
        OR (type = 'DISCARD' AND on_hand_delta < 0 AND reserved_delta = 0 AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO inventory_batches (inventory_id, batch_code, received_quantity, quantity_on_hand, reserved_quantity,
                               received_at, expires_at, status, note, created_by, created_at)
SELECT vi.id, CONCAT('LEGACY-', LPAD(vi.id, 8, '0')), GREATEST(vi.quantity_on_hand, 1), vi.quantity_on_hand,
       vi.reserved_quantity, vi.created_at, NULL, IF(vi.quantity_on_hand = 0, 'CLOSED', 'ACTIVE'),
       'Tồn kho chuyển đổi từ dữ liệu cũ', vi.created_by, vi.created_at
FROM variant_inventory vi;

INSERT INTO inventory_batch_transactions (batch_id, type, on_hand_delta, reserved_delta, on_hand_after,
                                         reserved_after, event_key, reason, created_by, created_at)
SELECT b.id, 'OPENING', b.quantity_on_hand, b.reserved_quantity, b.quantity_on_hand, b.reserved_quantity,
       CONCAT('opening-', b.id), 'Chuyển tồn kho hiện có sang quản lý theo batch', b.created_by, b.created_at
FROM inventory_batches b;

INSERT INTO order_item_batch_allocations (order_item_id, batch_id, quantity, status)
SELECT oi.id, b.id, oi.quantity, 'RESERVED'
FROM order_items oi
JOIN inventory_batches b ON b.inventory_id = oi.inventory_id
WHERE oi.allocation_status = 'RESERVED';

INSERT INTO inventory_batch_transactions (batch_id, order_item_id, type, on_hand_delta, reserved_delta,
                                         on_hand_after, reserved_after, event_key, reason, created_at)
SELECT b.id, oi.id, 'RESERVE', 0, oi.quantity, b.quantity_on_hand, b.reserved_quantity,
       CONCAT('legacy-reserve-', oi.id), CONCAT('Đang giữ cho đơn ', o.order_code), CURRENT_TIMESTAMP(3)
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
JOIN inventory_batches b ON b.inventory_id = oi.inventory_id
WHERE oi.allocation_status = 'RESERVED';
