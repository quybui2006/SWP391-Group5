-- Migration V25: Thêm trạng thái thẩm định cho từng tài khoản ngân hàng của Shop
SET @has_appr = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_bank_accounts'
      AND COLUMN_NAME = 'approval_status'
);
SET @sql_v25 = IF(@has_appr > 0, 'SELECT 1',
    'ALTER TABLE shop_bank_accounts
     ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' AFTER is_default,
     ADD COLUMN rejection_reason VARCHAR(255) NULL AFTER approval_status,
     ADD COLUMN reviewed_at DATETIME(3) NULL AFTER rejection_reason'
);
PREPARE stmt_v25 FROM @sql_v25;
EXECUTE stmt_v25;
DEALLOCATE PREPARE stmt_v25;

-- Đồng bộ trạng thái từ bảng shops:
-- Tài khoản mặc định của shop đã APPROVED thì được APPROVED, nếu shop REJECTED thì REJECTED
UPDATE shop_bank_accounts ba
JOIN shops s ON ba.shop_id = s.id
SET ba.approval_status = CASE 
    WHEN s.payment_approval_status = 'APPROVED' AND ba.is_default = TRUE THEN 'APPROVED'
    WHEN s.payment_approval_status = 'REJECTED' AND ba.is_default = TRUE THEN 'REJECTED'
    ELSE 'PENDING'
END,
ba.rejection_reason = CASE 
    WHEN s.payment_approval_status = 'REJECTED' AND ba.is_default = TRUE THEN s.bank_rejection_reason
    ELSE NULL
END,
ba.reviewed_at = CASE
    WHEN ba.is_default = TRUE THEN s.payment_reviewed_at
    ELSE NULL
END;
