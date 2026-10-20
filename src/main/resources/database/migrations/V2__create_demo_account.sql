-- =============================================================================
-- Migration V2: Create Demo Accounts, Assign Roles & Demo Shop
-- =============================================================================

INSERT INTO roles (code, name) VALUES
    ('CUSTOMER', 'Customer'),
    ('SHOP_OWNER', 'Shop Owner'),
    ('DELIVERY_MANAGER', 'Delivery Manager'),
    ('CS_STAFF', 'CS Staff'),
    ('OPERATIONS_MANAGER', 'Operations Manager'),
    ('ADMIN', 'Admin')
ON DUPLICATE KEY UPDATE code = VALUES(code);

INSERT INTO users (full_name, email, password_hash, status, email_verified_at) VALUES
    ('Demo Customer', 'customer.demo@freshfruit.example', '$2a$10$rIlKKPRMvCm.IsbMAO3klOn.Np80TLYifmLsRoHEEPtR0uwRScHzK', 'ACTIVE', NOW(3)),
    ('Demo Shop Owner', 'owner.demo@freshfruit.example', '$2a$10$rIlKKPRMvCm.IsbMAO3klOn.Np80TLYifmLsRoHEEPtR0uwRScHzK', 'ACTIVE', NOW(3)),
    ('Demo Admin', 'admin.demo@freshfruit.example', '$2a$10$rIlKKPRMvCm.IsbMAO3klOn.Np80TLYifmLsRoHEEPtR0uwRScHzK', 'ACTIVE', NOW(3)),
    ('CS Staff', 'cs.demo@freshfruit.example', '$2a$10$rIlKKPRMvCm.IsbMAO3klOn.Np80TLYifmLsRoHEEPtR0uwRScHzK', 'ACTIVE', NOW(3)),
    ('Delivery Manager', 'delivery.demo@freshfruit.example', '$2a$10$rIlKKPRMvCm.IsbMAO3klOn.Np80TLYifmLsRoHEEPtR0uwRScHzK', 'ACTIVE', NOW(3)),
    ('Operations Manager', 'op.demo@freshfruit.example', '$2a$10$rIlKKPRMvCm.IsbMAO3klOn.Np80TLYifmLsRoHEEPtR0uwRScHzK', 'ACTIVE', NOW(3))
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    status = VALUES(status),
    email_verified_at = VALUES(email_verified_at);

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r
WHERE (u.email = 'customer.demo@freshfruit.example' AND r.code = 'CUSTOMER')
   OR (u.email = 'owner.demo@freshfruit.example' AND r.code IN ('CUSTOMER', 'SHOP_OWNER'))
   OR (u.email = 'admin.demo@freshfruit.example' AND r.code = 'ADMIN')
   OR (u.email = 'cs.demo@freshfruit.example' AND r.code = 'CS_STAFF')
   OR (u.email = 'delivery.demo@freshfruit.example' AND r.code = 'DELIVERY_MANAGER')
   OR (u.email = 'op.demo@freshfruit.example' AND r.code = 'OPERATIONS_MANAGER');

INSERT INTO provinces (code, name, is_active)
VALUES ('DEMO', 'Demo Province', 1)
ON DUPLICATE KEY UPDATE code = VALUES(code);

INSERT INTO shops (owner_id, name, phone, province_code, ward_name, address_detail, approval_status, operating_status, reviewed_at)
SELECT id, 'Demo Fresh Shop', '0900000000', 'DEMO', 'Demo Ward', 'Demo Address', 'APPROVED', 'OPEN', NOW(3)
FROM users WHERE email = 'owner.demo@freshfruit.example'
ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id);
