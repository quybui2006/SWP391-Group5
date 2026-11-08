-- Migration V24: Quản lý nhiều tài khoản ngân hàng cho Shop và chọn tài khoản mặc định
CREATE TABLE IF NOT EXISTS shop_bank_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    bank_account_number VARCHAR(50) NOT NULL,
    bank_account_name VARCHAR(150) NOT NULL,
    bank_branch VARCHAR(150) NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_shop_bank_accounts_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
);

-- Chuyển thông tin tài khoản ngân hàng đã có trước đó sang bảng mới và đánh dấu mặc định (chỉ insert nếu shop chưa có tài khoản trong shop_bank_accounts)
INSERT INTO shop_bank_accounts (shop_id, bank_name, bank_account_number, bank_account_name, bank_branch, is_default, created_at, updated_at)
SELECT s.id, s.bank_name, s.bank_account_number, s.bank_account_name, s.bank_branch, TRUE, NOW(3), NOW(3)
FROM shops s
WHERE s.bank_account_number IS NOT NULL AND s.bank_account_number <> ''
  AND s.id NOT IN (SELECT ba.shop_id FROM shop_bank_accounts ba);
