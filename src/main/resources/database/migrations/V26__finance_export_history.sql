CREATE TABLE IF NOT EXISTS finance_export_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    export_type VARCHAR(30) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    filter_from DATE NULL,
    filter_to DATE NULL,
    exported_by BIGINT NULL,
    exported_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    file_content LONGBLOB NOT NULL,
    CONSTRAINT fk_finance_export_user FOREIGN KEY (exported_by) REFERENCES users(id),
    CONSTRAINT chk_finance_export_type CHECK (export_type IN ('CUSTOMER_REFUNDS','SHOP_PAYOUTS'))
);
CREATE INDEX ix_finance_export_history ON finance_export_history(export_type, exported_at DESC);
