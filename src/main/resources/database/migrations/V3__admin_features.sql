-- V3: Cấu hình hệ thống và moderation cho Review

-- Bảng cấu hình phí sàn và cấu hình khác
CREATE TABLE IF NOT EXISTS platform_config (
    config_key VARCHAR(50) PRIMARY KEY,
    config_value VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Giá trị mặc định của nhóm
INSERT INTO platform_config (config_key, config_value, description, updated_by)
VALUES ('commission_rate', '0.0500', 'Tỷ lệ phí sàn (0.00 - 0.15)', NULL)
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- Thêm trạng thái kiểm duyệt đánh giá (hậu kỳ)
ALTER TABLE reviews ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' AFTER content;
ALTER TABLE reviews ADD COLUMN moderation_reason VARCHAR(500) NULL AFTER moderation_status;
ALTER TABLE reviews ADD COLUMN moderated_by BIGINT NULL AFTER moderation_reason;
ALTER TABLE reviews ADD COLUMN moderated_at DATETIME(3) NULL AFTER moderated_by;

CREATE INDEX IX_reviews_moderation ON reviews(moderation_status, created_at DESC);
