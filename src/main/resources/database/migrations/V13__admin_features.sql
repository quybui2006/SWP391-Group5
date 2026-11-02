-- V13: Cấu hình hệ thống Admin
-- Phần moderation review đã được tạo ở V12.

CREATE TABLE IF NOT EXISTS platform_config (
    config_key VARCHAR(50) PRIMARY KEY,
    config_value VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO platform_config (config_key, config_value, description, updated_by)
VALUES ('commission_rate', '0.0500', 'Tỷ lệ phí sàn (0.00 - 0.15)', NULL)
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
