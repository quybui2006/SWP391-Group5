CREATE TABLE IF NOT EXISTS customer_bank_accounts (
    user_id BIGINT PRIMARY KEY,
    bank_name VARCHAR(100) NOT NULL,
    bank_account_no VARCHAR(50) NOT NULL,
    bank_account_holder VARCHAR(150) NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_customer_bank_account_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Backfill bank details into refunds already queued, when available from the customer's saved account.
UPDATE customer_refunds r
JOIN customer_bank_accounts b ON b.user_id = r.customer_id
SET r.bank_name = b.bank_name,
    r.bank_account_no = b.bank_account_no,
    r.bank_account_holder = b.bank_account_holder
WHERE r.status = 'PENDING';
