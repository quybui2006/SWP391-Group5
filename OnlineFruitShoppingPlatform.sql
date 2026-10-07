CREATE DATABASE IF NOT EXISTS freshfruit_v5 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE freshfruit_v5;

CREATE TABLE membership_tiers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE CHECK (code IN ('BRONZE','SILVER','GOLD','DIAMOND')),
    name VARCHAR(80) NOT NULL UNIQUE,
    rank_order TINYINT NOT NULL UNIQUE CHECK (rank_order BETWEEN 1 AND 4),
    min_spending DECIMAL(19,0) NULL CHECK (min_spending >= 0),
    is_active TINYINT(1) NOT NULL DEFAULT 0,
    CHECK (is_active = 0 OR min_spending IS NOT NULL)
);
-- Note: MySQL does not support filtered indexes natively like SQL Server. 
-- UX_tiers_threshold logic (UNIQUE on min_spending WHERE min_spending IS NOT NULL) 
-- is partially handled by MySQL automatically allowing multiple NULLs in UNIQUE columns.
CREATE UNIQUE INDEX UX_tiers_threshold ON membership_tiers(min_spending);

CREATE TABLE provinces (
    code VARCHAR(20) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    is_active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NULL, 
    phone VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','INACTIVE','LOCKED')),
    membership_tier_id BIGINT NULL,
    points_balance BIGINT NOT NULL DEFAULT 0 CHECK (points_balance >= 0),
    total_qualifying_spending DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (total_qualifying_spending >= 0),
    email_verified_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    FOREIGN KEY (membership_tier_id) REFERENCES membership_tiers(id)
);

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    assigned_by BIGINT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id),
    FOREIGN KEY (assigned_by) REFERENCES users(id)
);

CREATE TABLE user_addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recipient_name VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    province_code VARCHAR(20) NOT NULL,
    ward_name VARCHAR(150) NOT NULL,
    address_detail VARCHAR(500) NOT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (province_code) REFERENCES provinces(code)
);
-- App-level enforcement recommended for uniqueness of is_default = 1 per user_id
CREATE INDEX IX_addresses_default ON user_addresses(user_id, is_default);

CREATE TABLE email_otps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('REGISTER','LOGIN','RESET_PASSWORD','VERIFY_EMAIL')),
    otp_hash VARCHAR(255) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    CHECK (expires_at > created_at)
);

CREATE TABLE shops (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NULL,
    phone VARCHAR(20) NOT NULL,
    province_code VARCHAR(20) NOT NULL,
    ward_name VARCHAR(150) NOT NULL,
    address_detail VARCHAR(500) NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (approval_status IN ('PENDING','APPROVED','REJECTED')),
    operating_status VARCHAR(20) NOT NULL DEFAULT 'CLOSED'
        CHECK (operating_status IN ('OPEN','CLOSED','SUSPENDED')),
    reviewed_at DATETIME(3) NULL,
    suspension_reason VARCHAR(1000) NULL,
    rejection_reason VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE (owner_id),
    FOREIGN KEY (owner_id) REFERENCES users(id),
    FOREIGN KEY (province_code) REFERENCES provinces(code),
    CHECK (operating_status <> 'OPEN' OR approval_status = 'APPROVED'),
    CHECK (approval_status = 'PENDING' OR reviewed_at IS NOT NULL),
    CHECK (approval_status <> 'REJECTED' OR (rejection_reason IS NOT NULL AND CHAR_LENGTH(TRIM(rejection_reason)) > 0)),
    CHECK (operating_status <> 'SUSPENDED' OR (suspension_reason IS NOT NULL AND CHAR_LENGTH(TRIM(suspension_reason)) > 0))
);

CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    is_active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    description LONGTEXT NULL,
    origin VARCHAR(150) NULL,
    batch_code VARCHAR(60) NOT NULL,
    received_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (approval_status IN ('DRAFT','PENDING','APPROVED','REJECTED')),
    selling_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (selling_status IN ('DRAFT','ACTIVE','PAUSED','ARCHIVED')),
    submitted_at DATETIME(3) NULL,
    reviewed_at DATETIME(3) NULL,
    rejection_reason VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE (id, shop_id),
    UNIQUE (shop_id, batch_code),
    FOREIGN KEY (shop_id) REFERENCES shops(id),
    CHECK (expiry_date >= received_date),
    CHECK (approval_status <> 'PENDING' OR submitted_at IS NOT NULL),
    CHECK (approval_status NOT IN ('APPROVED','REJECTED') OR reviewed_at IS NOT NULL),
    CHECK (approval_status <> 'REJECTED' OR (rejection_reason IS NOT NULL AND CHAR_LENGTH(TRIM(rejection_reason)) > 0)),
    CHECK (selling_status <> 'ACTIVE' OR approval_status = 'APPROVED')
);

CREATE TABLE product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    UNIQUE (product_id, sort_order),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE product_categories (
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, category_id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE product_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    sku VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    specification VARCHAR(1000) NULL,
    image_url VARCHAR(1000) NULL,
    base_unit_id BIGINT NOT NULL,
    price DECIMAL(19,0) NOT NULL CHECK (price > 0),
    min_order_quantity INT NOT NULL DEFAULT 1 CHECK (min_order_quantity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','INACTIVE')),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE (shop_id, sku),
    UNIQUE (product_id, id),
    UNIQUE (id, shop_id),
    FOREIGN KEY (base_unit_id) REFERENCES units(id),
    FOREIGN KEY (product_id, shop_id) REFERENCES products(id, shop_id)
);

CREATE TABLE variant_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    initial_quantity INT NOT NULL CHECK (initial_quantity > 0),
    quantity_on_hand INT NOT NULL CHECK (quantity_on_hand >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    available_quantity INT GENERATED ALWAYS AS (quantity_on_hand - reserved_quantity) STORED,
    low_stock_threshold_pct DECIMAL(5,2) NOT NULL DEFAULT 10 CHECK (low_stock_threshold_pct BETWEEN 0 AND 100),
    cost_per_base_unit DECIMAL(19,0) NULL CHECK (cost_per_base_unit >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','BLOCKED','CLOSED')),
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE (variant_id),
    UNIQUE (id, variant_id),
    FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    CHECK (reserved_quantity <= quantity_on_hand)
);

CREATE TABLE promotional_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    promotional_price DECIMAL(19,0) NOT NULL CHECK (promotional_price > 0),
    starts_at DATETIME(3) NOT NULL,
    ends_at DATETIME(3) NOT NULL,
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE (id, variant_id),
    FOREIGN KEY (inventory_id, variant_id) REFERENCES variant_inventory(id, variant_id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    CHECK (ends_at > starts_at)
);

CREATE TABLE carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE (cart_id, variant_id),
    FOREIGN KEY (cart_id) REFERENCES carts(id),
    FOREIGN KEY (variant_id) REFERENCES product_variants(id)
);

CREATE TABLE shop_monthly_fees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    revenue_month DATE NOT NULL CHECK (DAY(revenue_month) = 1),
    revenue_amount DECIMAL(19,0) NOT NULL CHECK (revenue_amount >= 0),
    fee_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0500 CHECK (fee_rate = 0.0500),
    fee_amount DECIMAL(19,0) NOT NULL CHECK (fee_amount >= 0),
    due_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNPAID' CHECK (status IN ('UNPAID','PAID')),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    paid_at DATETIME(3) NULL,
    UNIQUE (shop_id, revenue_month),
    UNIQUE (id, shop_id),
    FOREIGN KEY (shop_id) REFERENCES shops(id),
    CHECK (fee_amount = ROUND(revenue_amount * fee_rate, 0)),
    CHECK (due_at = CAST(DATE_ADD(LAST_DAY(revenue_month), INTERVAL '8 -7' DAY_HOUR) AS DATETIME)),
    CHECK ((status = 'UNPAID' AND paid_at IS NULL) OR (status = 'PAID' AND paid_at IS NOT NULL))
);

CREATE TABLE checkouts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    checkout_code VARCHAR(40) NOT NULL UNIQUE,
    request_key VARCHAR(100) NOT NULL UNIQUE,
    user_id BIGINT NULL,
    contact_email VARCHAR(254) NOT NULL,
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('COD','MOCK_ONLINE')),
    total_amount DECIMAL(19,0) NOT NULL CHECK (total_amount >= 0),
    points_used BIGINT NOT NULL DEFAULT 0 CHECK (points_used >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PARTIAL','CONFIRMED','EXPIRED','CANCELLED')),
    guest_access_token_hash BINARY(32) NULL,
    guest_access_expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    FOREIGN KEY (user_id) REFERENCES users(id),
    CHECK (user_id IS NOT NULL OR points_used = 0),
    CHECK ((user_id IS NOT NULL AND guest_access_token_hash IS NULL AND guest_access_expires_at IS NULL)
        OR (user_id IS NULL AND guest_access_token_hash IS NOT NULL AND guest_access_expires_at IS NOT NULL AND guest_access_expires_at > created_at))
);
CREATE UNIQUE INDEX UX_guest_token ON checkouts(guest_access_token_hash);

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code VARCHAR(40) NOT NULL UNIQUE,
    checkout_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    monthly_fee_id BIGINT NULL,
    recipient_name VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    delivery_province_code VARCHAR(20) NOT NULL,
    delivery_province_name VARCHAR(100) NOT NULL,
    delivery_ward_name VARCHAR(150) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    shop_province_code VARCHAR(20) NOT NULL,
    customer_note VARCHAR(1000) NULL,
    subtotal DECIMAL(19,0) NOT NULL CHECK (subtotal >= 0),
    points_used BIGINT NOT NULL DEFAULT 0 CHECK (points_used >= 0),
    points_discount DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (points_discount >= 0),
    points_subsidy_amount DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (points_subsidy_amount >= 0),
    qualifying_amount DECIMAL(19,0) NOT NULL CHECK (qualifying_amount >= 0),
    points_earned BIGINT NOT NULL DEFAULT 0 CHECK (points_earned >= 0),
    shipping_fee DECIMAL(19,0) NOT NULL DEFAULT 20000 CHECK (shipping_fee = 20000),
    total_amount DECIMAL(19,0) NOT NULL CHECK (total_amount >= 0),
    shop_revenue_amount DECIMAL(19,0) NOT NULL CHECK (shop_revenue_amount >= 0),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('COD','MOCK_ONLINE')),
    payment_expires_at DATETIME(3) NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','CONFIRMED','PREPARING','SHIPPING','DELIVERED','COMPLETED','CANCELLED')),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID','PAID')),
    paid_at DATETIME(3) NULL,
    delivery_code VARCHAR(60) NOT NULL UNIQUE,
    delivery_status VARCHAR(25) NOT NULL DEFAULT 'NOT_STARTED'
        CHECK (delivery_status IN ('NOT_STARTED','READY','IN_TRANSIT','DELIVERED','FAILED','CANCELLED')),
    delivery_updated_by BIGINT NULL,
    delivery_updated_at DATETIME(3) NULL,
    delivery_failure_reason VARCHAR(1000) NULL,
    estimated_delivery_at DATETIME(3) NULL,
    shipped_at DATETIME(3) NULL,
    delivered_at DATETIME(3) NULL,
    customer_confirmed_at DATETIME(3) NULL,
    auto_complete_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    cancellation_reason VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE (checkout_id, shop_id),
    UNIQUE (id, shop_id),
    FOREIGN KEY (checkout_id) REFERENCES checkouts(id),
    FOREIGN KEY (shop_id) REFERENCES shops(id),
    FOREIGN KEY (monthly_fee_id, shop_id) REFERENCES shop_monthly_fees(id, shop_id),
    FOREIGN KEY (delivery_province_code) REFERENCES provinces(code),
    FOREIGN KEY (shop_province_code) REFERENCES provinces(code),
    FOREIGN KEY (delivery_updated_by) REFERENCES users(id),
    CHECK (points_discount = points_used AND points_discount <= subtotal),
    CHECK (points_subsidy_amount = points_discount),
    CHECK (qualifying_amount = subtotal - points_discount),
    CHECK (total_amount = subtotal - points_discount + shipping_fee),
    CHECK (shop_revenue_amount = subtotal),
    CHECK (shop_revenue_amount = total_amount - shipping_fee + points_subsidy_amount),
    CHECK ((payment_method = 'MOCK_ONLINE' AND payment_expires_at IS NOT NULL
            AND payment_expires_at = DATE_ADD(created_at, INTERVAL 15 MINUTE))
        OR (payment_method = 'COD' AND payment_expires_at IS NULL)),
    CHECK ((payment_status = 'UNPAID' AND paid_at IS NULL) OR (payment_status = 'PAID' AND paid_at IS NOT NULL)),
    CHECK (status <> 'COMPLETED' OR (completed_at IS NOT NULL AND payment_status = 'PAID' AND delivery_status = 'DELIVERED')),
    CHECK (monthly_fee_id IS NULL OR (status = 'COMPLETED' AND payment_status = 'PAID')),
    CHECK (estimated_delivery_at IS NULL OR estimated_delivery_at >= created_at),
    CHECK (status <> 'CANCELLED' OR (cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)),
    CHECK (delivery_status <> 'FAILED' OR (delivery_failure_reason IS NOT NULL AND CHAR_LENGTH(TRIM(delivery_failure_reason)) > 0)),
    CHECK (delivery_status <> 'DELIVERED' OR delivered_at IS NOT NULL),
    CHECK ((delivered_at IS NULL AND auto_complete_at IS NULL)
        OR (delivered_at IS NOT NULL AND auto_complete_at IS NOT NULL AND auto_complete_at = DATE_ADD(delivered_at, INTERVAL 3 DAY)))
);

CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    inventory_id BIGINT NOT NULL,
    allocation_status VARCHAR(20) NOT NULL DEFAULT 'RESERVED'
        CHECK (allocation_status IN ('RESERVED','CONSUMED','RELEASED')),
    promotion_id BIGINT NULL,
    product_name VARCHAR(200) NOT NULL,
    variant_name VARCHAR(150) NOT NULL,
    sku VARCHAR(80) NOT NULL,
    image_url VARCHAR(1000) NULL,
    base_unit_name VARCHAR(50) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(19,0) NOT NULL CHECK (unit_price > 0),
    sale_unit_price DECIMAL(19,0) NOT NULL CHECK (sale_unit_price > 0),
    line_total DECIMAL(19,0) NOT NULL CHECK (line_total >= 0),
    expiry_date DATE NOT NULL,
    reviewed_at DATETIME(3) NULL,
    UNIQUE (id, inventory_id),
    FOREIGN KEY (inventory_id, variant_id) REFERENCES variant_inventory(id, variant_id),
    FOREIGN KEY (order_id, shop_id) REFERENCES orders(id, shop_id),
    FOREIGN KEY (variant_id, shop_id) REFERENCES product_variants(id, shop_id),
    FOREIGN KEY (promotion_id, variant_id) REFERENCES promotional_prices(id, variant_id),
    CHECK (sale_unit_price <= unit_price),
    CHECK (line_total = ROUND(quantity * sale_unit_price, 0))
);

CREATE TABLE inventory_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT NOT NULL,
    order_item_id BIGINT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('RECEIVE','ADJUST','RESERVE','RELEASE','SALE','DISCARD')),
    on_hand_delta INT NOT NULL,
    reserved_delta INT NOT NULL,
    on_hand_after INT NOT NULL CHECK (on_hand_after >= 0),
    reserved_after INT NOT NULL CHECK (reserved_after >= 0),
    event_key VARCHAR(150) NOT NULL UNIQUE,
    reason VARCHAR(1000) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (inventory_id) REFERENCES variant_inventory(id),
    FOREIGN KEY (order_item_id, inventory_id) REFERENCES order_items(id, inventory_id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    CHECK (reserved_after <= on_hand_after),
    CHECK (type NOT IN ('ADJUST','DISCARD') OR (reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0)),
    CHECK ((type = 'RECEIVE' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = 'ADJUST' AND on_hand_delta <> 0 AND reserved_delta = 0)
        OR (type = 'RESERVE' AND on_hand_delta = 0 AND reserved_delta > 0 AND order_item_id IS NOT NULL)
        OR (type = 'RELEASE' AND on_hand_delta = 0 AND reserved_delta < 0 AND order_item_id IS NOT NULL)
        OR (type = 'SALE' AND on_hand_delta < 0 AND reserved_delta = on_hand_delta AND order_item_id IS NOT NULL)
        OR (type = 'DISCARD' AND on_hand_delta < 0 AND reserved_delta = 0))
);

CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_code VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    request_key VARCHAR(100) NOT NULL UNIQUE,
    method VARCHAR(20) NOT NULL CHECK (method IN ('COD','MOCK_ONLINE')),
    amount DECIMAL(19,0) NOT NULL CHECK (amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','SUCCEEDED','FAILED','EXPIRED','CANCELLED')),
    provider_reference VARCHAR(150) NULL,
    failure_reason VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    paid_at DATETIME(3) NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    CHECK (status <> 'SUCCEEDED' OR paid_at IS NOT NULL)
);
CREATE UNIQUE INDEX UX_payments_success ON payments(order_id, (CASE WHEN status = 'SUCCEEDED' THEN 1 ELSE NULL END));
CREATE UNIQUE INDEX UX_payments_provider ON payments(provider_reference);

CREATE TABLE loyalty_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('EARN','REDEEM','RESTORE')),
    points_change BIGINT NOT NULL,
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    event_key VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE (order_id, type),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    CHECK ((type = 'REDEEM' AND points_change < 0) OR (type IN ('EARN','RESTORE') AND points_change > 0))
);

CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL UNIQUE,
    rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (order_item_id) REFERENCES order_items(id)
);

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_key VARCHAR(150) NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    target_type VARCHAR(40) NULL,
    target_id BIGINT NULL,
    UNIQUE (user_id, event_key),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE platform_wallet_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(25) NOT NULL CHECK (type IN ('SHOP_FEE','POINT_SUBSIDY','TOP_UP')),
    amount DECIMAL(19,0) NOT NULL CHECK (amount > 0),
    monthly_fee_id BIGINT NULL,
    order_id BIGINT NULL,
    event_key VARCHAR(150) NOT NULL UNIQUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (monthly_fee_id) REFERENCES shop_monthly_fees(id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    CHECK ((type = 'SHOP_FEE' AND monthly_fee_id IS NOT NULL AND order_id IS NULL)
        OR (type = 'POINT_SUBSIDY' AND order_id IS NOT NULL AND monthly_fee_id IS NULL)
        OR (type = 'TOP_UP' AND order_id IS NULL AND monthly_fee_id IS NULL))
);
CREATE UNIQUE INDEX UX_wallet_fee ON platform_wallet_transactions(monthly_fee_id);
CREATE UNIQUE INDEX UX_wallet_subsidy ON platform_wallet_transactions(order_id);

CREATE INDEX IX_wallet_history ON platform_wallet_transactions(created_at DESC);
CREATE INDEX IX_fee_overdue ON shop_monthly_fees(shop_id, status, due_at);
CREATE INDEX IX_item_inventory ON order_items(inventory_id, allocation_status);
CREATE INDEX IX_users_tier ON users(membership_tier_id);
CREATE INDEX IX_user_roles_role ON user_roles(role_id, user_id);
CREATE INDEX IX_addresses_user ON user_addresses(user_id);
CREATE INDEX IX_otps_lookup ON email_otps(email, purpose, created_at DESC);
CREATE INDEX IX_shops_approval ON shops(approval_status, created_at);
CREATE INDEX IX_products_shop ON products(shop_id, selling_status);
CREATE INDEX IX_products_approval ON products(approval_status, submitted_at);
CREATE INDEX IX_products_expiry ON products(selling_status, expiry_date);
CREATE INDEX IX_product_categories_category ON product_categories(category_id, product_id);
CREATE INDEX IX_variants_product ON product_variants(product_id, status);
CREATE INDEX IX_promotions_batch ON promotional_prices(inventory_id, is_enabled, starts_at, ends_at);
CREATE INDEX IX_checkouts_user ON checkouts(user_id, created_at DESC);
CREATE INDEX IX_orders_shop_status ON orders(shop_id, status, created_at DESC);
CREATE INDEX IX_orders_auto_complete ON orders(status, auto_complete_at);
CREATE INDEX IX_items_order ON order_items(order_id);
CREATE INDEX IX_items_variant ON order_items(variant_id);
CREATE INDEX IX_inventory_batch ON inventory_transactions(inventory_id, created_at DESC);
CREATE INDEX IX_loyalty_user ON loyalty_transactions(user_id, created_at DESC);
CREATE INDEX IX_shops_province ON shops(province_code, operating_status);
CREATE INDEX IX_addresses_province ON user_addresses(province_code, user_id);
CREATE INDEX IX_orders_expiry ON orders(payment_method, payment_status, status, payment_expires_at);
CREATE INDEX IX_payments_order ON payments(order_id, created_at DESC);
CREATE INDEX IX_monthly_fees_status ON shop_monthly_fees(status, revenue_month);
CREATE INDEX IX_notifications_inbox ON notifications(user_id, id DESC);

INSERT INTO roles(code, name) VALUES
('CUSTOMER', 'Customer'),
('SHOP_OWNER', 'Shop Owner'),
('DELIVERY_MANAGER', 'Delivery Manager'),
('CS_STAFF', 'CS Staff'),
('OPERATIONS_MANAGER', 'Operations Manager'),
('ADMIN', 'Admin');

INSERT INTO units(code, name) VALUES
('KG', 'Kg'), ('GRAM', 'Gram'), ('BOX', 'Hộp'),
('TRAY', 'Khay'), ('CARTON', 'Thùng'), ('PIECE', 'Quả'), ('PACK', 'Gói');

INSERT INTO membership_tiers(code, name, rank_order) VALUES
('BRONZE', 'Đồng', 1), ('SILVER', 'Bạc', 2), ('GOLD', 'Vàng', 3), ('DIAMOND', 'Kim cương', 4);

CREATE OR REPLACE VIEW v_shop_sales_access AS
SELECT s.id AS shop_id, s.approval_status, s.operating_status,
       CAST(CASE WHEN EXISTS (SELECT 1 FROM shop_monthly_fees f
            WHERE f.shop_id = s.id AND f.status = 'UNPAID' AND f.fee_amount > 0 AND f.due_at <= UTC_TIMESTAMP(3))
            THEN 1 ELSE 0 END AS UNSIGNED) AS has_overdue_fee,
       CAST(CASE WHEN s.approval_status = 'APPROVED' AND s.operating_status = 'OPEN'
            AND NOT EXISTS (SELECT 1 FROM shop_monthly_fees f
                WHERE f.shop_id = s.id AND f.status = 'UNPAID' AND f.fee_amount > 0 AND f.due_at <= UTC_TIMESTAMP(3))
            THEN 1 ELSE 0 END AS UNSIGNED) AS can_accept_orders
FROM shops s;

CREATE OR REPLACE VIEW v_platform_wallet_balance AS
SELECT COALESCE(SUM(CASE WHEN type = 'POINT_SUBSIDY' THEN -amount ELSE amount END), 0) AS balance
FROM platform_wallet_transactions;

CREATE OR REPLACE VIEW v_shop_monthly_revenue AS
SELECT shop_id,
       DATE_FORMAT(DATE_ADD(completed_at, INTERVAL 7 HOUR), '%Y-%m-01') AS revenue_month,
       SUM(shop_revenue_amount) AS revenue_amount,
       COUNT(*) AS completed_order_count
FROM orders
WHERE status = 'COMPLETED' AND payment_status = 'PAID'
GROUP BY shop_id, DATE_FORMAT(DATE_ADD(completed_at, INTERVAL 7 HOUR), '%Y-%m-01');

DELIMITER $$
CREATE PROCEDURE usp_generate_monthly_fees (IN p_revenue_month DATE)
BEGIN
    DECLARE v_local_today DATE;
    DECLARE v_from_utc DATETIME(3);
    DECLARE v_to_utc DATETIME(3);
    DECLARE v_due DATETIME(3);
    DECLARE lock_status INT;

    IF p_revenue_month IS NULL OR DAY(p_revenue_month) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Use the first date of the revenue month.';
    END IF;
    
    SET v_local_today = DATE(DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 7 HOUR));
    IF p_revenue_month >= DATE_FORMAT(v_local_today, '%Y-%m-01') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only a closed calendar month can be billed.';
    END IF;

    SET v_from_utc = DATE_SUB(p_revenue_month, INTERVAL 7 HOUR);
    SET v_to_utc = DATE_SUB(DATE_ADD(p_revenue_month, INTERVAL 1 MONTH), INTERVAL 7 HOUR);
    SET v_due = DATE_ADD(v_to_utc, INTERVAL 7 DAY);

    SELECT GET_LOCK('FreshFruit.MonthlyFees', 10) INTO lock_status;
    IF lock_status = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Monthly fee generation is busy; retry.';
    END IF;

    START TRANSACTION;
    
    INSERT INTO shop_monthly_fees (shop_id, revenue_month, revenue_amount, fee_rate, fee_amount, due_at, status, paid_at)
    SELECT o.shop_id, p_revenue_month, SUM(o.shop_revenue_amount), 0.05,
        ROUND(SUM(o.shop_revenue_amount) * 0.05, 0), v_due,
        CASE WHEN ROUND(SUM(o.shop_revenue_amount) * 0.05, 0) = 0 THEN 'PAID' ELSE 'UNPAID' END,
        CASE WHEN ROUND(SUM(o.shop_revenue_amount) * 0.05, 0) = 0 THEN UTC_TIMESTAMP(3) ELSE NULL END
    FROM orders o 
    WHERE o.status = 'COMPLETED' AND o.payment_status = 'PAID'
        AND o.completed_at >= v_from_utc AND o.completed_at < v_to_utc
        AND NOT EXISTS (SELECT 1 FROM shop_monthly_fees f 
            WHERE f.shop_id = o.shop_id AND f.revenue_month = p_revenue_month)
    GROUP BY o.shop_id FOR UPDATE;

    UPDATE orders o 
    JOIN shop_monthly_fees f ON f.shop_id = o.shop_id AND f.revenue_month = p_revenue_month
    SET o.monthly_fee_id = f.id
    WHERE o.status = 'COMPLETED' AND o.payment_status = 'PAID'
        AND o.completed_at >= v_from_utc AND o.completed_at < v_to_utc AND o.monthly_fee_id IS NULL;

    COMMIT;
    DO RELEASE_LOCK('FreshFruit.MonthlyFees');
END$$
DELIMITER ;

DELIMITER $$
CREATE PROCEDURE usp_pay_shop_fee (IN p_fee_id BIGINT, IN p_owner_user_id BIGINT)
BEGIN
    DECLARE v_amount DECIMAL(19,0);
    DECLARE v_status VARCHAR(20);
    DECLARE lock_status INT;

    SELECT GET_LOCK('FreshFruit.Wallet', 10) INTO lock_status;
    IF lock_status = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wallet is busy; retry fee payment.';
    END IF;

    START TRANSACTION;

    SELECT f.fee_amount, f.status INTO v_amount, v_status
    FROM shop_monthly_fees f 
    JOIN shops s ON s.id = f.shop_id
    WHERE f.id = p_fee_id AND s.owner_id = p_owner_user_id
    FOR UPDATE;

    IF v_amount IS NULL THEN
        DO RELEASE_LOCK('FreshFruit.Wallet');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fee not found or caller does not own this shop.';
    END IF;

    IF v_status = 'UNPAID' THEN
        IF v_amount > 0 THEN
            INSERT INTO platform_wallet_transactions(type, amount, monthly_fee_id, event_key)
            VALUES ('SHOP_FEE', v_amount, p_fee_id, CONCAT('SHOP_FEE:', p_fee_id));
        END IF;
        UPDATE shop_monthly_fees SET status = 'PAID', paid_at = UTC_TIMESTAMP(3) WHERE id = p_fee_id;
    END IF;

    COMMIT;
    DO RELEASE_LOCK('FreshFruit.Wallet');
END$$
DELIMITER ;