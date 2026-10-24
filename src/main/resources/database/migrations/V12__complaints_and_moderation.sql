-- =============================================================================
-- Migration V12: Order Complaints (Disputes), CS SLA & Content Moderation
-- =============================================================================

-- 1. Bổ sung các cột kiểm duyệt nội dung (Content Moderation) cho bảng reviews
SET @has_mod_status = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'reviews'
      AND COLUMN_NAME = 'moderation_status'
);
SET @alter_reviews_sql = IF(@has_mod_status > 0, 'SELECT 1',
    'ALTER TABLE reviews
        ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT ''PENDING'',
        ADD COLUMN moderation_reason VARCHAR(500) NULL,
        ADD COLUMN moderated_by BIGINT NULL,
        ADD COLUMN moderated_at DATETIME(3) NULL');
PREPARE alter_reviews_stmt FROM @alter_reviews_sql;
EXECUTE alter_reviews_stmt;
DEALLOCATE PREPARE alter_reviews_stmt;

-- 2. Tạo bảng complaints (Quản lý yêu cầu khiếu nại / tranh chấp & SLA CSKH)
CREATE TABLE IF NOT EXISTS complaints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_code VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    reason_type VARCHAR(40) NOT NULL
        CHECK (reason_type IN ('QUALITY_ISSUE','WEIGHT_OR_QUANTITY_SHORTAGE','WRONG_SPECIFICATION','PACKAGING_DAMAGE','DELIVERY_OR_SERVICE','OTHER')),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    evidence_image_url VARCHAR(1000) NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('HIGH','NORMAL','LOW')),
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','IN_PROGRESS','RESOLVED','REJECTED','CANCELLED')),
    source_type VARCHAR(25) NOT NULL DEFAULT 'CUSTOMER'
        CHECK (source_type IN ('CUSTOMER','CS_MANUAL','AUTO_SYSTEM')),
    shop_response VARCHAR(1500) NULL,
    shop_responded_at DATETIME(3) NULL,
    resolution_type VARCHAR(40) NULL
        CHECK (resolution_type IS NULL OR resolution_type IN ('REPLACEMENT_OR_RESEND','COMPENSATE_POINTS','SHOP_WARNING_ESCALATE','EXPLANATION_ONLY','REJECTED')),
    resolution_note VARCHAR(1500) NULL,
    compensated_points BIGINT NOT NULL DEFAULT 0 CHECK (compensated_points >= 0),
    is_shop_fault TINYINT(1) NOT NULL DEFAULT 0,
    escalated_to_ops TINYINT(1) NOT NULL DEFAULT 0,
    sla_due_at DATETIME(3) NOT NULL,
    assigned_to BIGINT NULL,
    resolved_by BIGINT NULL,
    resolved_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (shop_id) REFERENCES shops(id),
    FOREIGN KEY (assigned_to) REFERENCES users(id),
    FOREIGN KEY (resolved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX IX_complaints_user ON complaints(user_id, created_at DESC);
CREATE INDEX IX_complaints_shop ON complaints(shop_id, status, created_at DESC);
CREATE INDEX IX_complaints_status_sla ON complaints(status, priority, sla_due_at);
CREATE INDEX IX_complaints_order ON complaints(order_id);
