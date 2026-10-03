/*
FRESHFRUIT - FINAL REVIEW - SQL SERVER - 28 TABLES
Run the WHOLE file in SSMS. Creates freshfruit_v5; does NOT migrate/delete old DBs.
Existing nonempty freshfruit_v5 is refused. Schema, seeds, views and procedures
are created atomically. All timestamps UTC; business dates/months Vietnam UTC+7.
No rowversion. Backend must use guarded updates/transaction locks where noted.

AGREED MODEL AND REQUIRED BACKEND RULES
1. Account has CUSTOMER role and one persistent cart; guest cart is session data.
   One shop per owner; owner can buy. Passwords/OTPs/guest tokens are hashed.
   Admin alone approves shops/products; no reviewed_by or products.created_by.
   Keep approval status, reviewed_at and rejection reason. Backend updates updated_at.
2. Provinces are shared reference data: populate from one consistent source before
   registering addresses. Selected recipient province != shop province -> warning,
   not a sales ban. No distance coordinates/API. Pickup address is read from shop;
   orders snapshot recipient address and shop province. No separate pickup field.
3. One product = one received lot; new lot creates new listing. All variants share
   lot expiry. Content/variant edits pause listing and reset approval; PENDING is
   locked until withdrawn. Lot dates/identity immutable after submission. Admin
   review and edits must serialize. Approved status is required before selling.
4. Shop creates arbitrary prepared packs (e.g. 0.5kg PACK, 1kg PACK, 6-fruit BOX).
   quantity is an integer COUNT of prepared sale units. Each variant has physically
   separate inventory. Never count the same fruit twice. No actual-weight adjustment
   or cross-variant stock conversion. price is per sale unit; choose PACK/BOX/PIECE
   and place weight in specification. Names must identify the purchased pack clearly.
5. Sale/dispatch requires product expiry_date >= local_today + 5 calendar days.
   Fixed rule in code, no expiry_alert_days. Keep estimated_delivery_at; estimate
   must land before expiry, and updates may require customer notification.
   Never mark expired goods as successfully delivered. Workers hide ineligible lots;
   checkout/dispatch enforce the rule even if a worker has not run.
6. variant_inventory has one row per variant. available = on_hand - reserved.
   Reserve atomically with order creation and verify available >= quantity. Allocation
   lives in order_items (inventory_id/allocation_status); no extra allocation table.
   SALE at dispatch reduces on_hand and reserved equally. RELEASE is only for an
   unconsumed reservation. DISCARD only removes unreserved damaged/expired stock,
   with reason/ledger. After dispatch failure, reconcile physical returned/discarded
   stock explicitly; do not release consumed reservations. Retain historical records.
7. Inventory ledger/event_key records one operation once. Backend must update stock,
   order allocation and ledger in one transaction. Sale variants cannot change units
   or pack size after stock/orders exist; create a new variant instead.
8. Shop controls price/promotion schedule freely. Reject overlap of enabled schedules
   per variant, invalid promotional price and schedules inconsistent with current
   regular price. Cart stores variant/quantity, not promotion. Checkout revalidates.
   Orders snapshot applied price/promotion/name/SKU/unit/expiry. Detailed specification
   stays on variant; no duplicated variant_specification in order_items.
9. Checkout splits into one order per shop. Each shipping_fee = 20000 VND; no config
   table. Shipping belongs to Delivery Management, excluded from shop revenue and
   platform fee wallet. Payment attempts belong to orders, at most one successful.
   MOCK_ONLINE deadline = created_at + 15 min; COD has no deadline. One failed/expired
   shop order must not cancel paid orders at another shop. Apply payment and expiry
   once with locks. Derive checkout aggregate status; placement totals remain snapshots.
   Verify payment amount/method/owner; backend receives authenticated actor IDs, not
   trusted user-supplied IDs. provider_reference is optional mock transaction metadata.
10. subtotal = goods after shop promotion, before points. points_discount = points_used;
    platform subsidy = points_discount; customer total = subtotal - points_discount +
    20000; shop revenue = subtotal. Example: 200k goods, 10k points -> customer pays
    210k including ship, platform funds 10k, shop receives 200k. Snapshot all totals,
    validate sum of lines/order totals/point allocations in one transaction.
11. Wallet: ONE ledger table, no separate wallet balance table. Balance is SUM(income
    - subsidy), exposed by v_platform_wallet_balance. SHOP_FEE is income and refers
    to one monthly bill; POINT_SUBSIDY is expense and refers to one successfully paid
    order. Unique filtered indexes prevent posting these twice. TOP_UP supports initial
    platform marketing funds, no invented money seed. Serialize wallet postings with
    a transaction app lock (resource FreshFruit.Wallet) and validate sufficient funds
    before spending subsidy; successful payment/subsidy posting commit atomically.
    TOP_UP is an Admin-only simulated operation; stable event_key deduplicates retry.
    Do not book subsidy on unpaid/cancelled orders. No real payment/refund integration.
12. Points: 1000 VND eligible spend -> 1 point, 1 point -> 1 VND; never expire.
    qualifying_amount = subtotal - point discount, excludes shipping. Earn once per
    COMPLETED order; restore redeemed points once on eligible unpaid cancellation.
    Lock balance + ledger together; verify owner; guests cannot earn/redeem. Rank is
    lifetime qualifying spend, not balance. Bronze/Silver/Gold/Diamond seeded inactive
    with NULL thresholds pending agreement; configure increasing thresholds first.
13. Monthly revenue is sum of PAID+COMPLETED shop orders in the local completion month,
    including point subsidy, excluding shipping/cancellation. Chart uses view/queries,
    no chart table. usp_generate_monthly_fees creates bill once; fee is 5% of month
    revenue, not profit, not a per-order deduction. No subscriptions or sales settlement.
    Deadline: pay through day 7 of following month; due_at is EXCLUSIVE instant day 8
    00:00 Vietnam stored UTC. At/after due_at, overdue positive unpaid bills block new
    sales via v_shop_sales_access. Zero-fee bills are automatically paid, no wallet row.
    Schedule generation on day 1 and retry missed months before allowing new sales;
    SQL does not schedule itself. Never backdate completions or change billed totals.
14. usp_pay_shop_fee simulates owner payment: marks PAID and credits fee wallet atomically,
    retries do not double-credit. Owner can still log in, pay and fulfill existing orders
    while overdue; customer role is unaffected. Debt blocking is derived from bills,
    not stored over Admin suspension. Paying all overdue bills removes only debt block;
    does not reopen CLOSED/Admin-SUSPENDED/unapproved shops. Check v_shop_sales_access
    inside checkout transaction; login/page checks alone are insufficient. Manual
    Admin suspensions retain suspension_reason. Backend role checks protect all calls.
15. Shop marks READY; Delivery Manager simulates IN_TRANSIT -> DELIVERED/FAILED.
    delivered_at and auto_complete_at=delivered_at+3 days are set together. Customer
    may confirm earlier; worker completes PAID+DELIVERED orders once. Failure never
    auto-completes. Keep estimated delivery; no routes/driver/payroll tables.
16. Reviews only completed account-owned lines; 1..5 stars, one review; retain reviewed_at
    if moderator deletes review. Notification bell lists latest IDs: no read/send-time
    columns; deduplicate user/event_key. Chat button disabled, no chat DB/backend.
17. Remaining cross-table/workflow checks require backend transactions: ledger totals,
    full stock reservation, promotion validity, successful payment vs wallet subsidy,
    state transitions, account tier changes and scheduled expiry/auto-completion.
    Tables/constraints/views/fee procedures do not implement a complete application.
*/

USE [master];
GO
IF DB_ID(N'freshfruit_v5') IS NULL
    EXEC(N'CREATE DATABASE [freshfruit_v5]');
GO
USE [freshfruit_v5];
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

IF DB_NAME() <> N'freshfruit_v5'
    THROW 50000, 'Wrong database context. Stop and verify CREATE DATABASE / USE succeeded.', 1;

IF EXISTS (SELECT 1 FROM sys.tables WHERE is_ms_shipped = 0)
BEGIN
    PRINT N'Database is not empty. No changes made. Replace all freshfruit_v5 names to use another database.';
    RETURN;
END;

BEGIN TRY
BEGIN TRANSACTION;

-- Defer schema compilation until the empty-database guard has passed.
-- Filtered indexes also compile separately after CREATE TABLE.
EXEC sys.sp_executesql N'CREATE TABLE dbo.membership_tiers (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE CHECK (code IN (''BRONZE'',''SILVER'',''GOLD'',''DIAMOND'')),
    name NVARCHAR(80) NOT NULL UNIQUE,
    rank_order TINYINT NOT NULL UNIQUE CHECK (rank_order BETWEEN 1 AND 4),
    min_spending DECIMAL(19,0) NULL CHECK (min_spending >= 0),
    is_active BIT NOT NULL DEFAULT 0,
    CHECK (is_active = 0 OR min_spending IS NOT NULL)
);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_tiers_threshold ON dbo.membership_tiers(min_spending) WHERE min_spending IS NOT NULL;'';

CREATE TABLE dbo.provinces (
    code VARCHAR(20) NOT NULL PRIMARY KEY,
    name NVARCHAR(100) NOT NULL UNIQUE,
    is_active BIT NOT NULL DEFAULT 1
);

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
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);

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

CREATE TABLE dbo.user_addresses (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES dbo.users(id),
    recipient_name NVARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    province_code VARCHAR(20) NOT NULL REFERENCES dbo.provinces(code),
    ward_name NVARCHAR(150) NOT NULL,
    address_detail NVARCHAR(500) NOT NULL,
    is_default BIT NOT NULL DEFAULT 0,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_addresses_default ON dbo.user_addresses(user_id) WHERE is_default = 1;'';

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

CREATE TABLE dbo.shops (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES dbo.users(id),
    name NVARCHAR(160) NOT NULL,
    description NVARCHAR(2000) NULL,
    phone VARCHAR(20) NOT NULL,
    province_code VARCHAR(20) NOT NULL REFERENCES dbo.provinces(code),
    ward_name NVARCHAR(150) NOT NULL,
    address_detail NVARCHAR(500) NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT ''PENDING''
        CHECK (approval_status IN (''PENDING'',''APPROVED'',''REJECTED'')),
    operating_status VARCHAR(20) NOT NULL DEFAULT ''CLOSED''
        CHECK (operating_status IN (''OPEN'',''CLOSED'',''SUSPENDED'')),
    reviewed_at DATETIME2(3) NULL,
    suspension_reason NVARCHAR(1000) NULL, -- manual Admin suspension only
    rejection_reason NVARCHAR(1000) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (owner_id),
    CHECK (operating_status <> ''OPEN'' OR approval_status = ''APPROVED''),
    CHECK (approval_status = ''PENDING'' OR reviewed_at IS NOT NULL),
    CHECK (approval_status <> ''REJECTED'' OR (rejection_reason IS NOT NULL AND LEN(LTRIM(RTRIM(rejection_reason))) > 0)),
    CHECK (operating_status <> ''SUSPENDED'' OR (suspension_reason IS NOT NULL AND LEN(LTRIM(RTRIM(suspension_reason))) > 0))
);

CREATE TABLE dbo.categories (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name NVARCHAR(100) NOT NULL UNIQUE,
    is_active BIT NOT NULL DEFAULT 1
);

CREATE TABLE dbo.products (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    shop_id BIGINT NOT NULL REFERENCES dbo.shops(id),
    name NVARCHAR(200) NOT NULL CHECK (LEN(LTRIM(RTRIM(name))) > 0),
    description NVARCHAR(MAX) NULL,
    origin NVARCHAR(150) NULL,
    batch_code VARCHAR(60) NOT NULL,
    received_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT ''DRAFT''
        CHECK (approval_status IN (''DRAFT'',''PENDING'',''APPROVED'',''REJECTED'')),
    selling_status VARCHAR(20) NOT NULL DEFAULT ''DRAFT''
        CHECK (selling_status IN (''DRAFT'',''ACTIVE'',''PAUSED'',''ARCHIVED'')),
    submitted_at DATETIME2(3) NULL,
    reviewed_at DATETIME2(3) NULL,
    rejection_reason NVARCHAR(1000) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (id, shop_id),
    UNIQUE (shop_id, batch_code),
    CHECK (expiry_date >= received_date),
    CHECK (approval_status <> ''PENDING'' OR submitted_at IS NOT NULL),
    CHECK (approval_status NOT IN (''APPROVED'',''REJECTED'') OR reviewed_at IS NOT NULL),
    CHECK (approval_status <> ''REJECTED'' OR (rejection_reason IS NOT NULL AND LEN(LTRIM(RTRIM(rejection_reason))) > 0)),
    CHECK (selling_status <> ''ACTIVE'' OR approval_status = ''APPROVED'')
);

CREATE TABLE dbo.product_images (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES dbo.products(id),
    image_url NVARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    UNIQUE (product_id, sort_order)
);
CREATE TABLE dbo.product_categories (
    product_id BIGINT NOT NULL REFERENCES dbo.products(id),
    category_id BIGINT NOT NULL REFERENCES dbo.categories(id),
    PRIMARY KEY (product_id, category_id)
);

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
    name NVARCHAR(150) NOT NULL CHECK (LEN(LTRIM(RTRIM(name))) > 0),
    specification NVARCHAR(1000) NULL,
    image_url NVARCHAR(1000) NULL,
    base_unit_id BIGINT NOT NULL REFERENCES dbo.units(id),
    price DECIMAL(19,0) NOT NULL CHECK (price > 0),
    min_order_quantity INT NOT NULL DEFAULT 1 CHECK (min_order_quantity > 0),
    status VARCHAR(20) NOT NULL DEFAULT ''DRAFT'' CHECK (status IN (''DRAFT'',''ACTIVE'',''INACTIVE'')),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (shop_id, sku),
    UNIQUE (product_id, id),
    UNIQUE (id, shop_id),
    FOREIGN KEY (product_id, shop_id) REFERENCES dbo.products(id, shop_id)
);

CREATE TABLE dbo.variant_inventory (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    variant_id BIGINT NOT NULL REFERENCES dbo.product_variants(id),
    initial_quantity INT NOT NULL CHECK (initial_quantity > 0),
    quantity_on_hand INT NOT NULL CHECK (quantity_on_hand >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    available_quantity AS (quantity_on_hand - reserved_quantity) PERSISTED,
    low_stock_threshold_pct DECIMAL(5,2) NOT NULL DEFAULT 10 CHECK (low_stock_threshold_pct BETWEEN 0 AND 100),
    cost_per_base_unit DECIMAL(19,0) NULL CHECK (cost_per_base_unit >= 0),
    status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'' CHECK (status IN (''ACTIVE'',''BLOCKED'',''CLOSED'')),
    created_by BIGINT NOT NULL REFERENCES dbo.users(id),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (variant_id),
    UNIQUE (id, variant_id),
    CHECK (reserved_quantity <= quantity_on_hand)
);

CREATE TABLE dbo.promotional_prices (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    inventory_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    promotional_price DECIMAL(19,0) NOT NULL CHECK (promotional_price > 0),
    starts_at DATETIME2(3) NOT NULL,
    ends_at DATETIME2(3) NOT NULL,
    is_enabled BIT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL REFERENCES dbo.users(id),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (id, variant_id),
    FOREIGN KEY (inventory_id, variant_id) REFERENCES dbo.variant_inventory(id, variant_id),
    CHECK (ends_at > starts_at)
);

CREATE TABLE dbo.carts (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES dbo.users(id),
    UNIQUE (user_id)
);
CREATE TABLE dbo.cart_items (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES dbo.carts(id),
    variant_id BIGINT NOT NULL REFERENCES dbo.product_variants(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (cart_id, variant_id)
);

CREATE TABLE dbo.shop_monthly_fees (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    shop_id BIGINT NOT NULL REFERENCES dbo.shops(id),
    revenue_month DATE NOT NULL CHECK (DAY(revenue_month) = 1),
    revenue_amount DECIMAL(19,0) NOT NULL CHECK (revenue_amount >= 0),
    fee_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0500 CHECK (fee_rate = 0.0500),
    fee_amount DECIMAL(19,0) NOT NULL CHECK (fee_amount >= 0),
    due_at DATETIME2(3) NOT NULL, -- EXCLUSIVE UTC deadline: day 8 at 00:00 Vietnam
    status VARCHAR(20) NOT NULL DEFAULT ''UNPAID'' CHECK (status IN (''UNPAID'',''PAID'')),
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    paid_at DATETIME2(3) NULL,
    UNIQUE (shop_id, revenue_month),
    UNIQUE (id, shop_id),
    CHECK (fee_amount = ROUND(revenue_amount * fee_rate, 0)),
    CHECK (due_at = DATEADD(HOUR, -7, CAST(DATEADD(DAY, 8, EOMONTH(revenue_month)) AS DATETIME2(3)))) ,
    CHECK ((status = ''UNPAID'' AND paid_at IS NULL) OR (status = ''PAID'' AND paid_at IS NOT NULL))
);

CREATE TABLE dbo.checkouts (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    checkout_code VARCHAR(40) NOT NULL UNIQUE,
    request_key VARCHAR(100) NOT NULL UNIQUE,
    user_id BIGINT NULL REFERENCES dbo.users(id),
    contact_email NVARCHAR(254) NOT NULL,
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN (''COD'',''MOCK_ONLINE'')),
    total_amount DECIMAL(19,0) NOT NULL CHECK (total_amount >= 0), -- original order totals
    points_used BIGINT NOT NULL DEFAULT 0 CHECK (points_used >= 0),
    status VARCHAR(20) NOT NULL DEFAULT ''PENDING''
        CHECK (status IN (''PENDING'',''PARTIAL'',''CONFIRMED'',''EXPIRED'',''CANCELLED'')),
    guest_access_token_hash BINARY(32) NULL,
    guest_access_expires_at DATETIME2(3) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CHECK (user_id IS NOT NULL OR points_used = 0),
    CHECK ((user_id IS NOT NULL AND guest_access_token_hash IS NULL AND guest_access_expires_at IS NULL)
        OR (user_id IS NULL AND guest_access_token_hash IS NOT NULL AND guest_access_expires_at IS NOT NULL AND guest_access_expires_at > created_at))
);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_guest_token ON dbo.checkouts(guest_access_token_hash) WHERE guest_access_token_hash IS NOT NULL;'';

CREATE TABLE dbo.orders (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_code VARCHAR(40) NOT NULL UNIQUE,
    checkout_id BIGINT NOT NULL REFERENCES dbo.checkouts(id),
    shop_id BIGINT NOT NULL REFERENCES dbo.shops(id),
    monthly_fee_id BIGINT NULL,
    recipient_name NVARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    delivery_province_code VARCHAR(20) NOT NULL REFERENCES dbo.provinces(code),
    delivery_province_name NVARCHAR(100) NOT NULL, -- address snapshot
    delivery_ward_name NVARCHAR(150) NOT NULL,
    delivery_address NVARCHAR(500) NOT NULL,
    shop_province_code VARCHAR(20) NOT NULL REFERENCES dbo.provinces(code), -- placement snapshot
    customer_note NVARCHAR(1000) NULL,
    subtotal DECIMAL(19,0) NOT NULL CHECK (subtotal >= 0), -- after shop discounts, before points
    points_used BIGINT NOT NULL DEFAULT 0 CHECK (points_used >= 0),
    points_discount DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (points_discount >= 0),
    points_subsidy_amount DECIMAL(19,0) NOT NULL DEFAULT 0 CHECK (points_subsidy_amount >= 0),
    qualifying_amount DECIMAL(19,0) NOT NULL CHECK (qualifying_amount >= 0),
    points_earned BIGINT NOT NULL DEFAULT 0 CHECK (points_earned >= 0),
    shipping_fee DECIMAL(19,0) NOT NULL DEFAULT 20000 CHECK (shipping_fee = 20000),
    total_amount DECIMAL(19,0) NOT NULL CHECK (total_amount >= 0), -- customer pays, includes shipping
    shop_revenue_amount DECIMAL(19,0) NOT NULL CHECK (shop_revenue_amount >= 0),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN (''COD'',''MOCK_ONLINE'')),
    payment_expires_at DATETIME2(3) NULL,
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
    estimated_delivery_at DATETIME2(3) NULL, -- optional simulation estimate, never beyond expiry
    shipped_at DATETIME2(3) NULL,
    delivered_at DATETIME2(3) NULL,
    customer_confirmed_at DATETIME2(3) NULL,
    auto_complete_at DATETIME2(3) NULL,
    completed_at DATETIME2(3) NULL,
    cancelled_at DATETIME2(3) NULL,
    cancellation_reason NVARCHAR(1000) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (checkout_id, shop_id),
    UNIQUE (id, shop_id),
    FOREIGN KEY (monthly_fee_id, shop_id) REFERENCES dbo.shop_monthly_fees(id, shop_id),
    CHECK (points_discount = points_used AND points_discount <= subtotal),
    CHECK (points_subsidy_amount = points_discount),
    CHECK (qualifying_amount = subtotal - points_discount),
    CHECK (total_amount = subtotal - points_discount + shipping_fee),
    CHECK (shop_revenue_amount = subtotal),
    CHECK (shop_revenue_amount = total_amount - shipping_fee + points_subsidy_amount),
    CHECK ((payment_method = ''MOCK_ONLINE'' AND payment_expires_at IS NOT NULL
            AND payment_expires_at = DATEADD(MINUTE, 15, created_at))
        OR (payment_method = ''COD'' AND payment_expires_at IS NULL)),
    CHECK ((payment_status = ''UNPAID'' AND paid_at IS NULL) OR (payment_status = ''PAID'' AND paid_at IS NOT NULL)),
    CHECK (status <> ''COMPLETED'' OR (completed_at IS NOT NULL AND payment_status = ''PAID'' AND delivery_status = ''DELIVERED'')),
    CHECK (monthly_fee_id IS NULL OR (status = ''COMPLETED'' AND payment_status = ''PAID'')),
    CHECK (estimated_delivery_at IS NULL OR estimated_delivery_at >= created_at),
    CHECK (status <> ''CANCELLED'' OR (cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)),
    CHECK (delivery_status <> ''FAILED'' OR (delivery_failure_reason IS NOT NULL AND LEN(LTRIM(RTRIM(delivery_failure_reason))) > 0)),
    CHECK (delivery_status <> ''DELIVERED'' OR delivered_at IS NOT NULL),
    CHECK ((delivered_at IS NULL AND auto_complete_at IS NULL)
        OR (delivered_at IS NOT NULL AND auto_complete_at IS NOT NULL AND auto_complete_at = DATEADD(DAY, 3, delivered_at)))
);

CREATE TABLE dbo.order_items (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    inventory_id BIGINT NOT NULL,
    allocation_status VARCHAR(20) NOT NULL DEFAULT ''RESERVED''
        CHECK (allocation_status IN (''RESERVED'',''CONSUMED'',''RELEASED'')),
    promotion_id BIGINT NULL,
    product_name NVARCHAR(200) NOT NULL,
    variant_name NVARCHAR(150) NOT NULL,
    sku VARCHAR(80) NOT NULL,
    image_url NVARCHAR(1000) NULL,
    base_unit_name NVARCHAR(50) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(19,0) NOT NULL CHECK (unit_price > 0), -- regular snapshot
    sale_unit_price DECIMAL(19,0) NOT NULL CHECK (sale_unit_price > 0),
    line_total DECIMAL(19,0) NOT NULL CHECK (line_total >= 0),
    expiry_date DATE NOT NULL, -- immutable lot expiry snapshot
    reviewed_at DATETIME2(3) NULL,
    UNIQUE (id, inventory_id),
    FOREIGN KEY (inventory_id, variant_id) REFERENCES dbo.variant_inventory(id, variant_id),
    FOREIGN KEY (order_id, shop_id) REFERENCES dbo.orders(id, shop_id),
    FOREIGN KEY (variant_id, shop_id) REFERENCES dbo.product_variants(id, shop_id),
    FOREIGN KEY (promotion_id, variant_id) REFERENCES dbo.promotional_prices(id, variant_id),
    CHECK (sale_unit_price <= unit_price),
    CHECK (line_total = ROUND(quantity * sale_unit_price, 0))
);

CREATE TABLE dbo.inventory_transactions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    inventory_id BIGINT NOT NULL REFERENCES dbo.variant_inventory(id),
    order_item_id BIGINT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN (''RECEIVE'',''ADJUST'',''RESERVE'',''RELEASE'',''SALE'',''DISCARD'')),
    on_hand_delta INT NOT NULL,
    reserved_delta INT NOT NULL,
    on_hand_after INT NOT NULL CHECK (on_hand_after >= 0),
    reserved_after INT NOT NULL CHECK (reserved_after >= 0),
    event_key VARCHAR(150) NOT NULL UNIQUE,
    reason NVARCHAR(1000) NULL,
    created_by BIGINT NULL REFERENCES dbo.users(id), -- NULL for system workers
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (order_item_id, inventory_id) REFERENCES dbo.order_items(id, inventory_id),
    CHECK (reserved_after <= on_hand_after),
    CHECK (type NOT IN (''ADJUST'',''DISCARD'') OR (reason IS NOT NULL AND LEN(LTRIM(RTRIM(reason))) > 0)),
    CHECK ((type = ''RECEIVE'' AND on_hand_delta > 0 AND reserved_delta = 0)
        OR (type = ''ADJUST'' AND on_hand_delta <> 0 AND reserved_delta = 0)
        OR (type = ''RESERVE'' AND on_hand_delta = 0 AND reserved_delta > 0 AND order_item_id IS NOT NULL)
        OR (type = ''RELEASE'' AND on_hand_delta = 0 AND reserved_delta < 0 AND order_item_id IS NOT NULL)
        OR (type = ''SALE'' AND on_hand_delta < 0 AND reserved_delta = on_hand_delta AND order_item_id IS NOT NULL)
        OR (type = ''DISCARD'' AND on_hand_delta < 0 AND reserved_delta = 0))
);

CREATE TABLE dbo.payments (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    payment_code VARCHAR(40) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL REFERENCES dbo.orders(id),
    request_key VARCHAR(100) NOT NULL UNIQUE,
    method VARCHAR(20) NOT NULL CHECK (method IN (''COD'',''MOCK_ONLINE'')),
    amount DECIMAL(19,0) NOT NULL CHECK (amount >= 0), -- customer payment only, not subsidy
    status VARCHAR(20) NOT NULL DEFAULT ''PENDING''
        CHECK (status IN (''PENDING'',''SUCCEEDED'',''FAILED'',''EXPIRED'',''CANCELLED'')),
    provider_reference VARCHAR(150) NULL, -- optional simulated external transaction code
    failure_reason NVARCHAR(1000) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    paid_at DATETIME2(3) NULL,
    CHECK (status <> ''SUCCEEDED'' OR paid_at IS NOT NULL)
);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_payments_success ON dbo.payments(order_id) WHERE status = ''''SUCCEEDED'''';'';
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_payments_provider ON dbo.payments(provider_reference) WHERE provider_reference IS NOT NULL;'';

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

CREATE TABLE dbo.reviews (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    order_item_id BIGINT NOT NULL REFERENCES dbo.order_items(id),
    rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content NVARCHAR(2000) NULL,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    UNIQUE (order_item_id)
);

CREATE TABLE dbo.notifications (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES dbo.users(id),
    event_key VARCHAR(150) NOT NULL,
    type VARCHAR(40) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    content NVARCHAR(2000) NOT NULL,
    target_type VARCHAR(40) NULL,
    target_id BIGINT NULL,
    UNIQUE (user_id, event_key)
);

CREATE TABLE dbo.platform_wallet_transactions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    type VARCHAR(25) NOT NULL CHECK (type IN (''SHOP_FEE'',''POINT_SUBSIDY'',''TOP_UP'')),
    amount DECIMAL(19,0) NOT NULL CHECK (amount > 0),
    monthly_fee_id BIGINT NULL REFERENCES dbo.shop_monthly_fees(id),
    order_id BIGINT NULL REFERENCES dbo.orders(id),
    event_key VARCHAR(150) NOT NULL UNIQUE,
    created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CHECK ((type = ''SHOP_FEE'' AND monthly_fee_id IS NOT NULL AND order_id IS NULL)
        OR (type = ''POINT_SUBSIDY'' AND order_id IS NOT NULL AND monthly_fee_id IS NULL)
        OR (type = ''TOP_UP'' AND order_id IS NULL AND monthly_fee_id IS NULL))
);
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_wallet_fee ON dbo.platform_wallet_transactions(monthly_fee_id) WHERE monthly_fee_id IS NOT NULL;'';
EXEC sys.sp_executesql N''CREATE UNIQUE INDEX UX_wallet_subsidy ON dbo.platform_wallet_transactions(order_id) WHERE order_id IS NOT NULL;'';
CREATE INDEX IX_wallet_history ON dbo.platform_wallet_transactions(created_at DESC);
CREATE INDEX IX_fee_overdue ON dbo.shop_monthly_fees(shop_id, status, due_at);
CREATE INDEX IX_item_inventory ON dbo.order_items(inventory_id, allocation_status);

CREATE INDEX IX_users_tier ON dbo.users(membership_tier_id);
CREATE INDEX IX_user_roles_role ON dbo.user_roles(role_id, user_id);
CREATE INDEX IX_addresses_user ON dbo.user_addresses(user_id);
CREATE INDEX IX_otps_lookup ON dbo.email_otps(email, purpose, created_at DESC);
CREATE INDEX IX_shops_approval ON dbo.shops(approval_status, created_at);
CREATE INDEX IX_products_shop ON dbo.products(shop_id, selling_status);
CREATE INDEX IX_products_approval ON dbo.products(approval_status, submitted_at);
CREATE INDEX IX_products_expiry ON dbo.products(selling_status, expiry_date);
CREATE INDEX IX_product_categories_category ON dbo.product_categories(category_id, product_id);
CREATE INDEX IX_variants_product ON dbo.product_variants(product_id, status);
CREATE INDEX IX_promotions_batch ON dbo.promotional_prices(inventory_id, is_enabled, starts_at, ends_at);
CREATE INDEX IX_checkouts_user ON dbo.checkouts(user_id, created_at DESC);
CREATE INDEX IX_orders_shop_status ON dbo.orders(shop_id, status, created_at DESC);
CREATE INDEX IX_orders_auto_complete ON dbo.orders(status, auto_complete_at);
CREATE INDEX IX_items_order ON dbo.order_items(order_id);
CREATE INDEX IX_items_variant ON dbo.order_items(variant_id);
CREATE INDEX IX_inventory_batch ON dbo.inventory_transactions(inventory_id, created_at DESC);
CREATE INDEX IX_loyalty_user ON dbo.loyalty_transactions(user_id, created_at DESC);

CREATE INDEX IX_shops_province ON dbo.shops(province_code, operating_status);
CREATE INDEX IX_addresses_province ON dbo.user_addresses(province_code, user_id);
CREATE INDEX IX_orders_expiry ON dbo.orders(payment_method, payment_status, status, payment_expires_at);
CREATE INDEX IX_payments_order ON dbo.payments(order_id, created_at DESC);
CREATE INDEX IX_orders_monthly_revenue ON dbo.orders(shop_id, status, completed_at) INCLUDE (payment_status, shop_revenue_amount, monthly_fee_id);
CREATE INDEX IX_monthly_fees_status ON dbo.shop_monthly_fees(status, revenue_month);
CREATE INDEX IX_notifications_inbox ON dbo.notifications(user_id, id DESC);

INSERT INTO dbo.roles(code, name) VALUES
(''CUSTOMER'', N''Customer''),
(''SHOP_OWNER'', N''Shop Owner''),
(''DELIVERY_MANAGER'', N''Delivery Manager''),
(''CS_STAFF'', N''CS Staff''),
(''OPERATIONS_MANAGER'', N''Operations Manager''),
(''ADMIN'', N''Admin'');

INSERT INTO dbo.units(code, name) VALUES
(''KG'', N''Kg''), (''GRAM'', N''Gram''), (''BOX'', N''Hộp''),
(''TRAY'', N''Khay''), (''CARTON'', N''Thùng''), (''PIECE'', N''Quả''), (''PACK'', N''Gói'');

INSERT INTO dbo.membership_tiers(code, name, rank_order) VALUES
(''BRONZE'', N''Đồng'', 1), (''SILVER'', N''Bạc'', 2), (''GOLD'', N''Vàng'', 3), (''DIAMOND'', N''Kim cương'', 4);

IF (SELECT COUNT(*) FROM sys.tables WHERE schema_id = SCHEMA_ID(N''dbo'') AND is_ms_shipped = 0) <> 28
    THROW 50001, ''Unexpected table count. Expected exactly 28 tables.'', 1;
';
EXEC sys.sp_executesql N'CREATE VIEW dbo.v_shop_sales_access AS
SELECT s.id AS shop_id, s.approval_status, s.operating_status,
       CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.shop_monthly_fees f
            WHERE f.shop_id = s.id AND f.status = ''UNPAID'' AND f.fee_amount > 0 AND f.due_at <= SYSUTCDATETIME())
            THEN 1 ELSE 0 END AS BIT) AS has_overdue_fee,
       CAST(CASE WHEN s.approval_status = ''APPROVED'' AND s.operating_status = ''OPEN''
            AND NOT EXISTS (SELECT 1 FROM dbo.shop_monthly_fees f
                WHERE f.shop_id = s.id AND f.status = ''UNPAID'' AND f.fee_amount > 0 AND f.due_at <= SYSUTCDATETIME())
            THEN 1 ELSE 0 END AS BIT) AS can_accept_orders
FROM dbo.shops s;';

EXEC sys.sp_executesql N'CREATE VIEW dbo.v_platform_wallet_balance AS
SELECT COALESCE(SUM(CASE WHEN type = ''POINT_SUBSIDY'' THEN -amount ELSE amount END), 0) AS balance
FROM dbo.platform_wallet_transactions;';

EXEC sys.sp_executesql N'CREATE VIEW dbo.v_shop_monthly_revenue AS
SELECT shop_id,
       DATEFROMPARTS(YEAR(DATEADD(HOUR, 7, completed_at)), MONTH(DATEADD(HOUR, 7, completed_at)), 1) AS revenue_month,
       SUM(shop_revenue_amount) AS revenue_amount,
       COUNT_BIG(*) AS completed_order_count
FROM dbo.orders
WHERE status = ''COMPLETED'' AND payment_status = ''PAID''
GROUP BY shop_id, DATEFROMPARTS(YEAR(DATEADD(HOUR, 7, completed_at)), MONTH(DATEADD(HOUR, 7, completed_at)), 1);';

EXEC sys.sp_executesql N'CREATE PROCEDURE dbo.usp_generate_monthly_fees @revenue_month DATE
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @revenue_month IS NULL OR DAY(@revenue_month) <> 1
        THROW 51000, ''Use the first date of the revenue month.'', 1;
    DECLARE @local_today DATE = CAST(DATEADD(HOUR, 7, SYSUTCDATETIME()) AS DATE);
    IF @revenue_month >= DATEFROMPARTS(YEAR(@local_today), MONTH(@local_today), 1)
        THROW 51001, ''Only a closed calendar month can be billed.'', 1;
    DECLARE @from_utc DATETIME2(3) = DATEADD(HOUR, -7, CAST(@revenue_month AS DATETIME2(3)));
    DECLARE @to_utc DATETIME2(3) = DATEADD(HOUR, -7, CAST(DATEADD(MONTH, 1, @revenue_month) AS DATETIME2(3)));
    DECLARE @due DATETIME2(3) = DATEADD(DAY, 7, @to_utc);
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock_result INT;
        EXEC @lock_result = sys.sp_getapplock @Resource = N''FreshFruit.MonthlyFees'',
            @LockMode = ''Exclusive'', @LockOwner = ''Transaction'', @LockTimeout = 10000;
        IF @lock_result < 0 THROW 51002, ''Monthly fee generation is busy; retry.'', 1;
        INSERT INTO dbo.shop_monthly_fees
            (shop_id, revenue_month, revenue_amount, fee_rate, fee_amount, due_at, status, paid_at)
        SELECT o.shop_id, @revenue_month, SUM(o.shop_revenue_amount), 0.05,
            ROUND(SUM(o.shop_revenue_amount) * 0.05, 0), @due,
            CASE WHEN ROUND(SUM(o.shop_revenue_amount) * 0.05, 0) = 0 THEN ''PAID'' ELSE ''UNPAID'' END,
            CASE WHEN ROUND(SUM(o.shop_revenue_amount) * 0.05, 0) = 0 THEN SYSUTCDATETIME() ELSE NULL END
        FROM dbo.orders o WITH (UPDLOCK, HOLDLOCK)
        WHERE o.status = ''COMPLETED'' AND o.payment_status = ''PAID''
            AND o.completed_at >= @from_utc AND o.completed_at < @to_utc
            AND NOT EXISTS (SELECT 1 FROM dbo.shop_monthly_fees f WITH (UPDLOCK, HOLDLOCK)
                WHERE f.shop_id = o.shop_id AND f.revenue_month = @revenue_month)
        GROUP BY o.shop_id;
        UPDATE o SET monthly_fee_id = f.id
        FROM dbo.orders o JOIN dbo.shop_monthly_fees f ON f.shop_id = o.shop_id AND f.revenue_month = @revenue_month
        WHERE o.status = ''COMPLETED'' AND o.payment_status = ''PAID''
            AND o.completed_at >= @from_utc AND o.completed_at < @to_utc AND o.monthly_fee_id IS NULL;
        IF EXISTS (SELECT 1 FROM dbo.shop_monthly_fees f
            WHERE f.revenue_month = @revenue_month AND f.revenue_amount <>
                (SELECT COALESCE(SUM(o.shop_revenue_amount), 0) FROM dbo.orders o WHERE o.monthly_fee_id = f.id))
            THROW 51003, ''Monthly totals changed. Do not backdate completed orders or alter finalized bills.'', 1;
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;';

EXEC sys.sp_executesql N'CREATE PROCEDURE dbo.usp_pay_shop_fee @fee_id BIGINT, @owner_user_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @amount DECIMAL(19,0), @status VARCHAR(20);
        DECLARE @wallet_lock INT;
        EXEC @wallet_lock = sys.sp_getapplock @Resource = N''FreshFruit.Wallet'',
            @LockMode = ''Exclusive'', @LockOwner = ''Transaction'', @LockTimeout = 10000;
        IF @wallet_lock < 0 THROW 51011, ''Wallet is busy; retry fee payment.'', 1;
        SELECT @amount = f.fee_amount, @status = f.status
        FROM dbo.shop_monthly_fees f WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.shops s ON s.id = f.shop_id
        WHERE f.id = @fee_id AND s.owner_id = @owner_user_id;
        IF @amount IS NULL THROW 51010, ''Fee not found or caller does not own this shop.'', 1;
        IF @status = ''UNPAID''
        BEGIN
            IF @amount > 0
                INSERT INTO dbo.platform_wallet_transactions(type, amount, monthly_fee_id, event_key)
                VALUES (''SHOP_FEE'', @amount, @fee_id, CONCAT(''SHOP_FEE:'', @fee_id));
            UPDATE dbo.shop_monthly_fees SET status = ''PAID'', paid_at = SYSUTCDATETIME() WHERE id = @fee_id;
        END;
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;';


COMMIT TRANSACTION;
PRINT N'FreshFruit schema created successfully: 28 tables. Configure provinces and membership thresholds before use.';
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
