/*
FRESHFRUIT - SQL SERVER - 33 TABLES (CHAT DEFERRED)
Run the WHOLE file in SSMS / Azure Data Studio; GO is a client batch separator.
New database: freshfruit_v2. Replace ALL database-name occurrences to rename it.
No DROP, DELETE, or migration of the existing freshfruit database is performed.
An existing EMPTY freshfruit_v2 can be used; a nonempty one is refused.
DDL + seed data run in one transaction. Database creation itself is separate.
All datetime2 values use UTC. Money is VND; quantities use the variant base unit.

APPLICATION CONFIG (not a settings table):
  ONLINE_PAYMENT_TTL_MINUTES = 15
  EARN_VND_PER_POINT = 1000; REDEEM_VND_PER_POINT = 1; POINTS_NEVER_EXPIRE = true
  EXPIRED_SUBSCRIPTION_COMMISSION_RATE = 0.1000
  AUTO_COMPLETE_AFTER_DAYS = configurable (duration not yet agreed)
  ROLE_PERMISSIONS = fixed mappings in application code

IMPORTANT SERVICE TRANSACTIONS / RULES (not automatically enforced by DDL):
1. Create account + CUSTOMER role + one persistent cart atomically. Add roles,
   not replace CUSTOMER, when opening a shop. Guest cart is client/session data.
2. OP alone approves shops and revisions. Publish only APPROVED revision and its
   variant content atomically. Old published revision remains visible meanwhile.
   Published/approved content is immutable: edits create a new revision.
   Every published variant must have content in the published product revision.
3. Check shop approval, subscription policy, published content and variant status
   before sale. A base-unit/pack-size change creates a new variant if stock or
   orders exist. Conversion is within one variant, never between separate stock.
4. Reserve batches with locks or guarded UPDATE (quantity_on_hand - reserved >= q)
   in the SAME transaction as order creation/allocation. Consume/release once.
   inventory_transactions is the append-only ledger of those changes.
   Expired batches cannot be sold. Low stock = available_quantity / initial_quantity.
5. Promotions: prevent overlapping active periods for the same batch; validate
   price <= current regular price. Discount dates are chosen by the shop.
   A promotional line can allocate ONLY its promotion's batch. Normal lines can
   allocate several batches. Allocation sum must equal line quantity.
6. One checkout -> one order per shop. Snapshot all names, addresses, prices,
   shipping formula inputs, conversion factors and commission into the order.
   Validate totals against lines and across checkout; never trust client totals.
   Shipping distance is an API snapshot; secret API keys stay outside the DB.
7. ONLINE: one successful full-checkout payment; retry uses a new attempt key.
   Callback/expiry worker must lock checkout, verify method/amount/expiry and
   apply exactly once. Expiry releases stock and returns redeemed points once.
   Late success requires reconciliation, never silently revive an expired order.
   COD: collection is tracked per order; checkout payment becomes SUCCEEDED only
   when the whole checkout is collected. Failed/partial collection is not a full
   checkout payment; no partial online payments/refunds are in this scope.
8. Points: lock user balance, debit with ledger on placement, earn once per order
   upon completion, restore once on eligible cancellation. floor(qualifying_amount
   / 1000); qualifying_amount excludes shipping, redeemed points and discounts.
   Verify ledger user_id is the checkout owner. Sum per-order point redemption
   must equal checkout.points_used; enforce quantity minimum/step during checkout.
   Guest orders cannot earn/redeem account points. Reconcile cached user balances.
9. Commission: snapshot 0 while subscription active at placement, otherwise 10%.
   Later subscription expiry/config changes must not recalculate old orders.
   Settlement includes completed, collected, unsettled orders of the same shop.
   Recommended formula: gross = merchandise after shop discount, before points;
   points_subsidy = points redeemed (included in gross, NOT added again);
   net = gross - commission. Shipping is excluded from shop settlement.
10. Validate status transitions/actor roles in service. Customer confirms receipt;
    scheduled worker completes delivered orders at auto_complete_at, idempotently.
    Delivery Manager controls shipping config and simulated delivery transitions.
11. Reviews: completed account-owned orders only, rating 1..5, no edits. Moderation
    can delete review; retain order_items.reviewed_at to prohibit resubmission.
12. Store only hashed passwords, OTPs and guest tokens. Rate-limit OTP/guest access;
    verify token expiry. Persist old snapshots/ledger rows; deactivate master data.
13. Notification workers deduplicate by event_key and avoid repeated alert spam.
    Header/line totals, approval roles, revision immutability, ledger consistency,
    promotion overlap, settlement sums and membership upgrades require services.
This is schema + reference seeds, NOT a full implementation of these workflows.
*/

USE [master];
GO
IF DB_ID(N'freshfruit_v2') IS NULL
    EXEC(N'CREATE DATABASE [freshfruit_v2]');
GO
USE [freshfruit_v2];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF DB_NAME() <> N'freshfruit_v2'
    THROW 50000, 'Wrong database context. Stop and verify CREATE DATABASE / USE succeeded.', 1;

IF EXISTS (SELECT 1 FROM sys.tables WHERE is_ms_shipped = 0)
BEGIN
    PRINT N'Database is not empty. No changes made. Replace all freshfruit_v2 names to use another database.';
    RETURN;
END;

BEGIN TRY
BEGIN TRANSACTION;

-- Defer schema compilation until the empty-database guard has passed.
-- Filtered indexes also compile separately after CREATE TABLE.
EXEC sys.sp_executesql N'-- 01. Tier thresholds are configured by Operations Manager; no invented seeds.
CREATE TABLE dbo.membership_tiers (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name NVARCHAR(80) NOT NULL UNIQUE,
    min_spending DECIMAL(19,0) NOT NULL CHECK (min_spending >= 0),
    is_active BIT NOT NULL DEFAULT 1,
    UNIQUE (min_spending)
);

-- 02. Email is the login identifier. Normalize/trim email in service.
CREATE TABLE dbo.users (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    full_name NVARCHAR(150) NOT NULL,
    email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL UNIQUE,
    password_hash VARCHAR(255) NULL, -- nullable for OTP-only accounts
    phone VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE''
        CHECK (status IN (''ACTIVE'',''INACTIVE'',''LOCKED'')),
    membership_tier_id BIGINT NULL REFERENCES dbo.membership_tiers(id),
    points_balance BIGINT NOT NULL DEFAULT 0 CHECK (points_balance >= 0),
    total_qualifying_spending DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (total_qualifying_spending >= 0),
    email_verified_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    row_version ROWVERSION
);

-- 03-04. Multiple roles per user; permission mappings live in code.
CREATE TABLE dbo.roles (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name NVARCHAR(80) NOT NULL
);
CREATE TABLE dbo.user_roles (
    user_id BIGINT NOT NULL REFERENCES dbo.users(id),
    role_id BIGINT NOT NULL REFERENCES dbo.roles(id),
    assigned_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    assigned_by BIGINT NULL REFERENCES dbo.users(id),
    PRIMARY KEY (user_id, role_id)
);

-- 05. Any number of addresses, at most one default address per account.
CREATE TABLE dbo.user_addresses (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES dbo.users(id),
    recipient_name NVARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    address_detail NVARCHAR(500) NOT NULL,
    latitude DECIMAL(9,6) NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DECIMAL(9,6) NULL CHECK (longitude BETWEEN -180 AND 180),
    is_default BIT NOT NULL DEFAULT 0,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL))
);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_addresses_default ON dbo.user_addresses(user_id) WHERE is_default = 1;'';

-- 06. Store hash only; short-lived OTPs require rate limiting in service.
CREATE TABLE dbo.email_otps (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NULL REFERENCES dbo.users(id),
    email NVARCHAR(254) NOT NULL,
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN (''REGISTER'',''LOGIN'',''RESET_PASSWORD'',''VERIFY_EMAIL'')),
    otp_hash VARCHAR(255) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    expires_at DATETIME2(3) NOT NULL,
    used_at DATETIME2(3) NULL,
    CHECK (expires_at > created_at)
);

-- 07. One shop per owner; account can still purchase.
CREATE TABLE dbo.shops (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES dbo.users(id),
    name NVARCHAR(160) NOT NULL,
    description NVARCHAR(2000) NULL,
    phone VARCHAR(20) NOT NULL,
    address_detail NVARCHAR(500) NOT NULL,
    latitude DECIMAL(9,6) NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DECIMAL(9,6) NULL CHECK (longitude BETWEEN -180 AND 180),
    approval_status VARCHAR(20) NOT NULL DEFAULT ''PENDING''
        CHECK (approval_status IN (''PENDING'',''APPROVED'',''REJECTED'')),
    operating_status VARCHAR(20) NOT NULL DEFAULT ''CLOSED''
        CHECK (operating_status IN (''OPEN'',''CLOSED'',''SUSPENDED'')),
    reviewed_by BIGINT NULL REFERENCES dbo.users(id),
    reviewed_at DATETIME2(3) NULL,
    rejection_reason NVARCHAR(1000) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (owner_id),
    CHECK (operating_status <> ''OPEN'' OR approval_status = ''APPROVED''),
    CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL))
);

-- 08-09. Seller subscriptions, separate from customer membership.
CREATE TABLE dbo.shop_plans (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name NVARCHAR(100) NOT NULL UNIQUE,
    duration_months INT NOT NULL CHECK (duration_months > 0),
    price DECIMAL(19,0) NOT NULL CHECK (price >= 0),
    is_active BIT NOT NULL DEFAULT 1
);
CREATE TABLE dbo.subscriptions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    subscription_code VARCHAR(40) NOT NULL UNIQUE,
    shop_id BIGINT NOT NULL REFERENCES dbo.shops(id),
    plan_id BIGINT NOT NULL REFERENCES dbo.shop_plans(id),
    plan_name NVARCHAR(100) NOT NULL,
    duration_months INT NOT NULL CHECK (duration_months > 0),
    paid_amount DECIMAL(19,0) NOT NULL CHECK (paid_amount >= 0),
    payment_status VARCHAR(20) NOT NULL DEFAULT ''PENDING''
        CHECK (payment_status IN (''PENDING'',''PAID'',''FAILED'',''CANCELLED'')),
    payment_reference VARCHAR(100) NULL,
    paid_at DATETIME2(3) NULL,
    starts_at DATETIME2(3) NOT NULL,
    ends_at DATETIME2(3) NOT NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CHECK (ends_at > starts_at),
    CHECK (payment_status <> ''PAID'' OR paid_at IS NOT NULL)
);

-- 10. Single-level categories; a product may have several categories.
CREATE TABLE dbo.categories (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name NVARCHAR(100) NOT NULL UNIQUE,
    is_active BIT NOT NULL DEFAULT 1
);

-- 11-12. Stable identity + moderated snapshots. Cyclic FK is added afterwards.
CREATE TABLE dbo.products (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    shop_id BIGINT NOT NULL REFERENCES dbo.shops(id),
    published_revision_id BIGINT NULL,
    selling_status VARCHAR(20) NOT NULL DEFAULT ''DRAFT''
        CHECK (selling_status IN (''DRAFT'',''ACTIVE'',''PAUSED'',''ARCHIVED'')),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (id, shop_id),
    CHECK (selling_status <> ''ACTIVE'' OR published_revision_id IS NOT NULL)
);
CREATE TABLE dbo.product_revisions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES dbo.products(id),
    revision_number INT NOT NULL CHECK (revision_number > 0),
    name NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NULL,
    origin NVARCHAR(150) NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT ''DRAFT''
        CHECK (approval_status IN (''DRAFT'',''PENDING'',''APPROVED'',''REJECTED'')),
    created_by BIGINT NOT NULL REFERENCES dbo.users(id),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    submitted_at DATETIME2(3) NULL,
    reviewed_by BIGINT NULL REFERENCES dbo.users(id),
    reviewed_at DATETIME2(3) NULL,
    rejection_reason NVARCHAR(1000) NULL,
    UNIQUE (product_id, revision_number),
    UNIQUE (product_id, id),
    CHECK (approval_status NOT IN (''APPROVED'',''REJECTED'') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);
ALTER TABLE dbo.products ADD CONSTRAINT FK_products_published_revision
    FOREIGN KEY (id, published_revision_id) REFERENCES dbo.product_revisions(product_id, id);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_revisions_pending ON dbo.product_revisions(product_id) WHERE approval_status = ''''PENDING'''';'';

-- 13-14. Images and categories belong to the content revision, not the live row.
CREATE TABLE dbo.product_revision_images (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    product_revision_id BIGINT NOT NULL REFERENCES dbo.product_revisions(id),
    image_url NVARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    UNIQUE (product_revision_id, sort_order)
);
CREATE TABLE dbo.product_revision_categories (
    product_revision_id BIGINT NOT NULL REFERENCES dbo.product_revisions(id),
    category_id BIGINT NOT NULL REFERENCES dbo.categories(id),
    PRIMARY KEY (product_revision_id, category_id)
);

-- 15-18. Stock belongs to each distinct variant; conversions are variant-specific.
CREATE TABLE dbo.units (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name NVARCHAR(50) NOT NULL,
    is_active BIT NOT NULL DEFAULT 1
);
CREATE TABLE dbo.product_variants (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    sku VARCHAR(80) NOT NULL,
    base_unit_id BIGINT NOT NULL REFERENCES dbo.units(id),
    price DECIMAL(19,0) NOT NULL CHECK (price > 0),
    shipping_weight_kg DECIMAL(12,6) NOT NULL CHECK (shipping_weight_kg > 0),
    min_order_quantity DECIMAL(18,3) NOT NULL DEFAULT 1 CHECK (min_order_quantity > 0),
    quantity_step DECIMAL(18,3) NOT NULL DEFAULT 1 CHECK (quantity_step > 0),
    status VARCHAR(20) NOT NULL DEFAULT ''DRAFT'' CHECK (status IN (''DRAFT'',''ACTIVE'',''INACTIVE'')),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    row_version ROWVERSION,
    UNIQUE (shop_id, sku),
    UNIQUE (product_id, id),
    UNIQUE (id, shop_id),
    FOREIGN KEY (product_id, shop_id) REFERENCES dbo.products(id, shop_id)
);
CREATE TABLE dbo.variant_revisions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    product_revision_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    name NVARCHAR(150) NOT NULL,
    specification NVARCHAR(1000) NULL,
    image_url NVARCHAR(1000) NULL,
    is_listed BIT NOT NULL DEFAULT 1,
    UNIQUE (product_revision_id, variant_id),
    FOREIGN KEY (product_id, product_revision_id) REFERENCES dbo.product_revisions(product_id, id),
    FOREIGN KEY (product_id, variant_id) REFERENCES dbo.product_variants(product_id, id)
);
CREATE TABLE dbo.variant_unit_conversions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    variant_id BIGINT NOT NULL REFERENCES dbo.product_variants(id),
    unit_id BIGINT NOT NULL REFERENCES dbo.units(id),
    factor_to_base DECIMAL(18,6) NOT NULL CHECK (factor_to_base > 0),
    UNIQUE (variant_id, unit_id)
);

-- 19. initial_quantity is immutable input quantity; available is derived.
CREATE TABLE dbo.product_batches (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    variant_id BIGINT NOT NULL REFERENCES dbo.product_variants(id),
    batch_code VARCHAR(60) NOT NULL,
    received_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    initial_quantity DECIMAL(18,3) NOT NULL CHECK (initial_quantity > 0),
    quantity_on_hand DECIMAL(18,3) NOT NULL CHECK (quantity_on_hand >= 0),
    reserved_quantity DECIMAL(18,3) NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    available_quantity AS (quantity_on_hand - reserved_quantity) PERSISTED,
    low_stock_threshold_pct DECIMAL(5,2) NOT NULL DEFAULT 10 CHECK (low_stock_threshold_pct BETWEEN 0 AND 100),
    expiry_alert_days INT NOT NULL DEFAULT 3 CHECK (expiry_alert_days >= 0),
    cost_per_base_unit DECIMAL(19,0) NULL CHECK (cost_per_base_unit >= 0),
    status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'' CHECK (status IN (''ACTIVE'',''BLOCKED'',''CLOSED'')),
    created_by BIGINT NOT NULL REFERENCES dbo.users(id),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    row_version ROWVERSION,
    UNIQUE (variant_id, batch_code),
    UNIQUE (id, variant_id),
    CHECK (expiry_date >= received_date),
    CHECK (reserved_quantity <= quantity_on_hand)
);

-- 20. Each record is one scheduled batch promotion. Price is per base unit.
CREATE TABLE dbo.promotional_prices (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    promotional_price DECIMAL(19,0) NOT NULL CHECK (promotional_price > 0),
    starts_at DATETIME2(3) NOT NULL,
    ends_at DATETIME2(3) NOT NULL,
    is_enabled BIT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL REFERENCES dbo.users(id),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (id, variant_id),
    FOREIGN KEY (batch_id, variant_id) REFERENCES dbo.product_batches(id, variant_id),
    CHECK (ends_at > starts_at)
);

-- 21-22. A persistent cart per account; guest cart lives in session/client.
CREATE TABLE dbo.carts (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES dbo.users(id),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (user_id)
);
CREATE TABLE dbo.cart_items (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES dbo.carts(id),
    variant_id BIGINT NOT NULL REFERENCES dbo.product_variants(id),
    batch_promotion_id BIGINT NULL,
    quantity DECIMAL(18,3) NOT NULL CHECK (quantity > 0),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (batch_promotion_id, variant_id) REFERENCES dbo.promotional_prices(id, variant_id)
);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_cart_normal ON dbo.cart_items(cart_id, variant_id) WHERE batch_promotion_id IS NULL;'';
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_cart_promo ON dbo.cart_items(cart_id, variant_id, batch_promotion_id) WHERE batch_promotion_id IS NOT NULL;'';

-- 23. Delivery Manager versions shipping formulas; used versions are immutable.
CREATE TABLE dbo.shipping_rate_configs (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    base_fee DECIMAL(19,0) NOT NULL CHECK (base_fee >= 0),
    price_per_km DECIMAL(19,0) NOT NULL CHECK (price_per_km >= 0),
    price_per_kg DECIMAL(19,0) NOT NULL CHECK (price_per_kg >= 0),
    effective_from DATETIME2(3) NOT NULL UNIQUE,
    created_by BIGINT NOT NULL REFERENCES dbo.users(id),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);

-- 24. No invented subscription/tier/shipping prices are seeded below.
CREATE TABLE dbo.settlements (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    settlement_code VARCHAR(40) NOT NULL UNIQUE,
    shop_id BIGINT NOT NULL REFERENCES dbo.shops(id),
    gross_amount DECIMAL(19,0) NOT NULL CHECK (gross_amount >= 0),
    points_subsidy_amount DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (points_subsidy_amount >= 0),
    commission_amount DECIMAL(19,0) NOT NULL CHECK (commission_amount >= 0),
    net_amount DECIMAL(19,0) NOT NULL CHECK (net_amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' CHECK (status IN (''PENDING'',''PAID'')),
    payment_reference VARCHAR(100) NULL,
    created_by BIGINT NOT NULL REFERENCES dbo.users(id),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    paid_at DATETIME2(3) NULL,
    UNIQUE (id, shop_id),
    CHECK (points_subsidy_amount <= gross_amount),
    CHECK (net_amount = gross_amount - commission_amount),
    CHECK (status <> ''PAID'' OR paid_at IS NOT NULL)
);

-- 25. Header for one checkout across shops; at most one current guest token.
CREATE TABLE dbo.checkouts (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    checkout_code VARCHAR(40) NOT NULL UNIQUE,
    request_key VARCHAR(100) NOT NULL UNIQUE, -- idempotency key from checkout request
    user_id BIGINT NULL REFERENCES dbo.users(id),
    contact_email NVARCHAR(254) NOT NULL,
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN (''COD'',''MOCK_ONLINE'')),
    total_amount DECIMAL(19,0) NOT NULL CHECK (total_amount >= 0),
    points_used BIGINT NOT NULL DEFAULT 0 CHECK (points_used >= 0),
    status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' CHECK (status IN (''PENDING'',''CONFIRMED'',''EXPIRED'',''CANCELLED'')),
    payment_expires_at DATETIME2(3) NULL,
    guest_access_token_hash BINARY(32) NULL,
    guest_access_expires_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    row_version ROWVERSION,
    CHECK (user_id IS NOT NULL OR points_used = 0),
    CHECK ((payment_method = ''MOCK_ONLINE'' AND payment_expires_at IS NOT NULL AND payment_expires_at > created_at)
        OR (payment_method = ''COD'' AND payment_expires_at IS NULL)),
    CHECK ((user_id IS NOT NULL AND guest_access_token_hash IS NULL AND guest_access_expires_at IS NULL)
        OR (user_id IS NULL AND guest_access_token_hash IS NOT NULL AND guest_access_expires_at IS NOT NULL AND guest_access_expires_at > created_at))
);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_guest_token ON dbo.checkouts(guest_access_token_hash) WHERE guest_access_token_hash IS NOT NULL;'';

-- 26. Every monetary column is an immutable pricing snapshot after placement.
CREATE TABLE dbo.orders (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_code VARCHAR(40) NOT NULL UNIQUE,
    checkout_id BIGINT NOT NULL REFERENCES dbo.checkouts(id),
    shop_id BIGINT NOT NULL REFERENCES dbo.shops(id),
    settlement_id BIGINT NULL,
    recipient_name NVARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    delivery_address NVARCHAR(500) NOT NULL,
    delivery_latitude DECIMAL(9,6) NULL CHECK (delivery_latitude BETWEEN -90 AND 90),
    delivery_longitude DECIMAL(9,6) NULL CHECK (delivery_longitude BETWEEN -180 AND 180),
    pickup_address NVARCHAR(500) NOT NULL,
    customer_note NVARCHAR(1000) NULL,
    subtotal DECIMAL(19,0) NOT NULL CHECK (subtotal >= 0), -- after shop discounts, before points
    points_used BIGINT NOT NULL DEFAULT 0 CHECK (points_used >= 0),
    points_discount DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (points_discount >= 0),
    qualifying_amount DECIMAL(19,0) NOT NULL CHECK (qualifying_amount >= 0),
    points_earned BIGINT NOT NULL DEFAULT 0 CHECK (points_earned >= 0),
    shipping_rate_config_id BIGINT NOT NULL REFERENCES dbo.shipping_rate_configs(id),
    shipping_distance_km DECIMAL(12,3) NOT NULL CHECK (shipping_distance_km >= 0),
    shipping_weight_kg DECIMAL(12,6) NOT NULL CHECK (shipping_weight_kg > 0),
    shipping_base_fee DECIMAL(19,0) NOT NULL CHECK (shipping_base_fee >= 0),
    shipping_price_per_km DECIMAL(19,0) NOT NULL CHECK (shipping_price_per_km >= 0),
    shipping_price_per_kg DECIMAL(19,0) NOT NULL CHECK (shipping_price_per_kg >= 0),
    shipping_fee DECIMAL(19,0) NOT NULL CHECK (shipping_fee >= 0),
    total_amount DECIMAL(19,0) NOT NULL CHECK (total_amount >= 0),
    commission_rate DECIMAL(5,4) NOT NULL CHECK (commission_rate BETWEEN 0 AND 1),
    commission_amount DECIMAL(19,0) NOT NULL CHECK (commission_amount >= 0),
    shop_net_amount DECIMAL(19,0) NOT NULL CHECK (shop_net_amount >= 0),
    status VARCHAR(25) NOT NULL DEFAULT ''PENDING''
        CHECK (status IN (''PENDING'',''CONFIRMED'',''PREPARING'',''SHIPPING'',''DELIVERED'',''COMPLETED'',''CANCELLED'')),
    payment_status VARCHAR(20) NOT NULL DEFAULT ''UNPAID'' CHECK (payment_status IN (''UNPAID'',''PAID'')),
    paid_at DATETIME2(3) NULL,
    delivery_code VARCHAR(60) NOT NULL UNIQUE,
    delivery_status VARCHAR(25) NOT NULL DEFAULT ''NOT_STARTED''
        CHECK (delivery_status IN (''NOT_STARTED'',''READY'',''IN_TRANSIT'',''DELIVERED'',''FAILED'',''CANCELLED'')),
    delivery_updated_by BIGINT NULL REFERENCES dbo.users(id),
    delivery_updated_at DATETIME2(3) NULL,
    delivery_failure_reason NVARCHAR(1000) NULL,
    shipped_at DATETIME2(3) NULL,
    delivered_at DATETIME2(3) NULL,
    customer_confirmed_at DATETIME2(3) NULL,
    auto_complete_at DATETIME2(3) NULL,
    completed_at DATETIME2(3) NULL,
    cancelled_at DATETIME2(3) NULL,
    cancellation_reason NVARCHAR(1000) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    row_version ROWVERSION,
    UNIQUE (checkout_id, shop_id),
    UNIQUE (id, shop_id),
    FOREIGN KEY (settlement_id, shop_id) REFERENCES dbo.settlements(id, shop_id),
    CHECK (points_discount = points_used AND points_discount <= subtotal),
    CHECK (qualifying_amount = subtotal - points_discount),
    CHECK (total_amount = subtotal - points_discount + shipping_fee),
    CHECK (commission_amount = ROUND(subtotal * commission_rate, 0)),
    CHECK (shop_net_amount = subtotal - commission_amount),
    CHECK (payment_status <> ''PAID'' OR paid_at IS NOT NULL),
    CHECK (status <> ''COMPLETED'' OR (completed_at IS NOT NULL AND payment_status = ''PAID'' AND delivery_status = ''DELIVERED'')),
    CHECK ((delivery_latitude IS NULL AND delivery_longitude IS NULL) OR (delivery_latitude IS NOT NULL AND delivery_longitude IS NOT NULL))
);

-- 27. shop_id is repeated intentionally for same-shop composite FK enforcement.
CREATE TABLE dbo.order_items (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    promotion_id BIGINT NULL,
    product_name NVARCHAR(200) NOT NULL,
    variant_name NVARCHAR(150) NOT NULL,
    variant_specification NVARCHAR(1000) NULL,
    sku VARCHAR(80) NOT NULL,
    image_url NVARCHAR(1000) NULL,
    base_unit_name NVARCHAR(50) NOT NULL,
    quantity DECIMAL(18,3) NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(19,0) NOT NULL CHECK (unit_price > 0), -- regular snapshot
    sale_unit_price DECIMAL(19,0) NOT NULL CHECK (sale_unit_price > 0),
    line_total DECIMAL(19,0) NOT NULL CHECK (line_total >= 0),
    shipping_weight_kg_per_unit DECIMAL(12,6) NOT NULL CHECK (shipping_weight_kg_per_unit > 0),
    reviewed_at DATETIME2(3) NULL,
    UNIQUE (id, variant_id),
    FOREIGN KEY (order_id, shop_id) REFERENCES dbo.orders(id, shop_id),
    FOREIGN KEY (variant_id, shop_id) REFERENCES dbo.product_variants(id, shop_id),
    FOREIGN KEY (promotion_id, variant_id) REFERENCES dbo.promotional_prices(id, variant_id),
    CHECK (sale_unit_price <= unit_price),
    CHECK (line_total = ROUND(quantity * sale_unit_price, 0))
);

-- 28. Multiple batches per line, but always the SAME variant.
CREATE TABLE dbo.order_item_batches (
    order_item_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity DECIMAL(18,3) NOT NULL CHECK (quantity > 0),
    allocation_status VARCHAR(20) NOT NULL DEFAULT ''RESERVED''
        CHECK (allocation_status IN (''RESERVED'',''CONSUMED'',''RELEASED'')),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    PRIMARY KEY (order_item_id, batch_id),
    FOREIGN KEY (order_item_id, variant_id) REFERENCES dbo.order_items(id, variant_id),
    FOREIGN KEY (batch_id, variant_id) REFERENCES dbo.product_batches(id, variant_id)
);

-- 29. on_hand_delta and reserved_delta maintain an auditable stock ledger.
CREATE TABLE dbo.inventory_transactions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES dbo.product_batches(id),
    order_item_id BIGINT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN (''RECEIVE'',''ADJUST'',''RESERVE'',''RELEASE'',''SALE'',''DISCARD'')),
    on_hand_delta DECIMAL(18,3) NOT NULL,
    reserved_delta DECIMAL(18,3) NOT NULL,
    on_hand_after DECIMAL(18,3) NOT NULL CHECK (on_hand_after >= 0),
    reserved_after DECIMAL(18,3) NOT NULL CHECK (reserved_after >= 0),
    event_key VARCHAR(150) NOT NULL UNIQUE,
    reason NVARCHAR(1000) NULL,
    created_by BIGINT NULL REFERENCES dbo.users(id), -- NULL for system workers
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (order_item_id, batch_id) REFERENCES dbo.order_item_batches(order_item_id, batch_id),
    CHECK (reserved_after <= on_hand_after),
    CHECK ((type = ''RECEIVE'' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = ''ADJUST'' AND on_hand_delta <> 0 AND reserved_delta = 0)
        OR (type = ''RESERVE'' AND on_hand_delta = 0 AND reserved_delta > 0 AND order_item_id IS NOT NULL)
        OR (type = ''RELEASE'' AND on_hand_delta = 0 AND reserved_delta < 0 AND order_item_id IS NOT NULL)
        OR (type = ''SALE'' AND on_hand_delta < 0 AND reserved_delta = on_hand_delta AND order_item_id IS NOT NULL)
        OR (type = ''DISCARD'' AND on_hand_delta < 0 AND reserved_delta = 0))
);

-- 30. One checkout, multiple attempts, at most one successful payment.
CREATE TABLE dbo.payments (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    payment_code VARCHAR(40) NOT NULL UNIQUE,
    checkout_id BIGINT NOT NULL REFERENCES dbo.checkouts(id),
    request_key VARCHAR(100) NOT NULL UNIQUE,
    method VARCHAR(20) NOT NULL CHECK (method IN (''COD'',''MOCK_ONLINE'')),
    amount DECIMAL(19,0) NOT NULL CHECK (amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' CHECK (status IN (''PENDING'',''SUCCEEDED'',''FAILED'',''EXPIRED'',''CANCELLED'')),
    provider_reference VARCHAR(150) NULL,
    failure_reason NVARCHAR(1000) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    paid_at DATETIME2(3) NULL,
    row_version ROWVERSION,
    CHECK (status <> ''SUCCEEDED'' OR paid_at IS NOT NULL)
);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_payments_success ON dbo.payments(checkout_id) WHERE status = ''''SUCCEEDED'''';'';
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_payments_provider ON dbo.payments(provider_reference) WHERE provider_reference IS NOT NULL;'';

-- 31. Append-only. Points_balance is a cache updated atomically with this ledger.
CREATE TABLE dbo.loyalty_transactions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES dbo.users(id),
    order_id BIGINT NOT NULL REFERENCES dbo.orders(id),
    type VARCHAR(20) NOT NULL CHECK (type IN (''EARN'',''REDEEM'',''RESTORE'')),
    points_change BIGINT NOT NULL,
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    event_key VARCHAR(150) NOT NULL UNIQUE,
    description NVARCHAR(500) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (order_id, type),
    CHECK ((type = ''REDEEM'' AND points_change < 0) OR (type IN (''EARN'',''RESTORE'') AND points_change > 0))
);

-- 32. No user_id duplication: reviewer is obtained through order -> checkout.
CREATE TABLE dbo.reviews (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_item_id BIGINT NOT NULL REFERENCES dbo.order_items(id),
    rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content NVARCHAR(2000) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (order_item_id)
);

-- 33. Event key is stable across worker retries; target_type/id is a UI link.
CREATE TABLE dbo.notifications (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES dbo.users(id),
    event_key VARCHAR(150) NOT NULL,
    type VARCHAR(40) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    content NVARCHAR(2000) NOT NULL,
    target_type VARCHAR(40) NULL,
    target_id BIGINT NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    read_at DATETIME2(3) NULL,
    UNIQUE (user_id, event_key)
);

-- Query indexes. PK/UNIQUE constraints already create their own indexes.
CREATE INDEX IX_users_tier ON dbo.users(membership_tier_id);
CREATE INDEX IX_user_roles_role ON dbo.user_roles(role_id, user_id);
CREATE INDEX IX_addresses_user ON dbo.user_addresses(user_id);
CREATE INDEX IX_otps_lookup ON dbo.email_otps(email, purpose, created_at DESC);
CREATE INDEX IX_shops_approval ON dbo.shops(approval_status, created_at);
CREATE INDEX IX_subscriptions_active ON dbo.subscriptions(shop_id, payment_status, starts_at, ends_at);
CREATE INDEX IX_products_shop ON dbo.products(shop_id, selling_status);
CREATE INDEX IX_revisions_queue ON dbo.product_revisions(approval_status, submitted_at);
CREATE INDEX IX_revision_categories_category ON dbo.product_revision_categories(category_id, product_revision_id);
CREATE INDEX IX_variants_product ON dbo.product_variants(product_id, status);
CREATE INDEX IX_variant_revisions_variant ON dbo.variant_revisions(variant_id, product_revision_id);
CREATE INDEX IX_batches_fefo ON dbo.product_batches(variant_id, status, expiry_date)
    INCLUDE (quantity_on_hand, reserved_quantity, initial_quantity);
CREATE INDEX IX_promotions_batch ON dbo.promotional_prices(batch_id, is_enabled, starts_at, ends_at);
CREATE INDEX IX_checkouts_user ON dbo.checkouts(user_id, created_at DESC);
CREATE INDEX IX_checkouts_expiry ON dbo.checkouts(status, payment_method, payment_expires_at);
CREATE INDEX IX_orders_shop_status ON dbo.orders(shop_id, status, created_at DESC);
CREATE INDEX IX_orders_settlement ON dbo.orders(settlement_id, shop_id);
CREATE INDEX IX_orders_auto_complete ON dbo.orders(status, auto_complete_at);
CREATE INDEX IX_items_order ON dbo.order_items(order_id);
CREATE INDEX IX_items_variant ON dbo.order_items(variant_id);
CREATE INDEX IX_allocations_batch ON dbo.order_item_batches(batch_id, allocation_status);
CREATE INDEX IX_inventory_batch ON dbo.inventory_transactions(batch_id, created_at DESC);
CREATE INDEX IX_payments_checkout ON dbo.payments(checkout_id, created_at DESC);
CREATE INDEX IX_loyalty_user ON dbo.loyalty_transactions(user_id, created_at DESC);
CREATE INDEX IX_settlements_shop ON dbo.settlements(shop_id, status, created_at DESC);
CREATE INDEX IX_notifications_inbox ON dbo.notifications(user_id, created_at DESC);
EXEC sys.sp_executesql N''CREATE INDEX IX_notifications_unread ON dbo.notifications(user_id, created_at DESC) WHERE read_at IS NULL;'';

-- Only agreed role/unit reference data. No demo passwords or fake money values.
INSERT INTO dbo.roles(code, name) VALUES
(''CUSTOMER'', N''Customer''),
(''SHOP_OWNER'', N''Shop Owner''),
(''DELIVERY_MANAGER'', N''Delivery Manager''),
(''CS_STAFF'', N''CS Staff''),
(''OPERATIONS_MANAGER'', N''Operations Manager''),
(''ADMIN'', N''Admin'');

INSERT INTO dbo.units(code, name) VALUES
(''KG'', N''Kg''), (''GRAM'', N''Gram''), (''BOX'', N''Hộp''),
(''TRAY'', N''Khay''), (''CARTON'', N''Thùng''), (''PIECE'', N''Quả'');

IF (SELECT COUNT(*) FROM sys.tables WHERE schema_id = SCHEMA_ID(N''dbo'') AND is_ms_shipped = 0) <> 33
    THROW 50001, ''Unexpected table count. Expected exactly 33 tables.'', 1;
';

COMMIT TRANSACTION;
PRINT N'FreshFruit schema created successfully: 33 tables. Configure membership tiers, shop plans and shipping rates before use.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

SELECT t.name AS table_name
FROM sys.tables AS t
WHERE t.schema_id = SCHEMA_ID(N'dbo') AND t.is_ms_shipped = 0
ORDER BY t.name;
GO
