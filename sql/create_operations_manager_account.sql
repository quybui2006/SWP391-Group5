-- Demo account for local development only.
-- Run this against the same database configured in application.properties (FreshFruit).
-- Email: operations.manager@freshfruit.local
-- Password: Ops@123456

USE FreshFruit;
START TRANSACTION;

INSERT INTO roles (code, name)
VALUES ('OPERATIONS_MANAGER', 'Operations Manager')
ON DUPLICATE KEY UPDATE name = 'Operations Manager';

-- Create the account only when the email does not already exist.
-- BCrypt hash corresponds to password: Ops@123456
INSERT INTO users (
    full_name,
    email,
    password_hash,
    phone,
    status,
    email_verified_at
)
SELECT
    'Operations Manager',
    'operations.manager@freshfruit.local',
    '$2a$10$0JS9J7orb9nN.9cy6KaW6.qQ4TIpH2.Tu0l9PbdYxlH/hUsZrKhx2',
    NULL,
    'ACTIVE',
    CURRENT_TIMESTAMP(3)
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'operations.manager@freshfruit.local'
);

-- Attach the role. Re-running this script will not create a duplicate role assignment.
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'OPERATIONS_MANAGER'
WHERE u.email = 'operations.manager@freshfruit.local';

COMMIT;

-- Verify account and role assignment.
SELECT u.id, u.full_name, u.email, u.status, u.email_verified_at, r.code AS role_code
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
WHERE u.email = 'operations.manager@freshfruit.local'
  AND r.code = 'OPERATIONS_MANAGER';
