CREATE TABLE IF NOT EXISTS customer_coin_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    type VARCHAR(20) NOT NULL CHECK (type IN ('RECEIVED','USED','EXPIRED','ADJUSTMENT')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED')),
    source_type VARCHAR(40) NULL,
    source_id BIGINT NULL,
    event_key VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500) NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX IX_customer_coins_user_status ON customer_coin_transactions(user_id, status, created_at DESC);
CREATE INDEX IX_customer_coins_expiry ON customer_coin_transactions(status, expires_at);

CREATE TABLE IF NOT EXISTS customer_wallets (
    user_id BIGINT PRIMARY KEY,
    balance DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    total_deposited DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (total_deposited >= 0),
    total_refunded DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (total_refunded >= 0),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS customer_wallet_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('DEPOSIT','REFUND','WITHDRAWAL','PAYMENT')),
    amount DECIMAL(19,0) NOT NULL CHECK (amount > 0),
    description VARCHAR(500) NULL,
    event_key VARCHAR(150) NOT NULL UNIQUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX IX_customer_wallet_tx_user ON customer_wallet_transactions(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS customer_saved_vouchers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    voucher_id BIGINT NOT NULL,
    saved_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_customer_saved_voucher (user_id, voucher_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (voucher_id) REFERENCES vouchers(id)
);
CREATE INDEX IX_customer_saved_vouchers_user ON customer_saved_vouchers(user_id, saved_at DESC);
