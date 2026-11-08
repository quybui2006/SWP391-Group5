ALTER TABLE membership_tiers
    ADD COLUMN cashback_percent DECIMAL(5,2) NOT NULL DEFAULT 0;

UPDATE membership_tiers
SET min_spending = CASE code
        WHEN 'BRONZE' THEN 0
        WHEN 'SILVER' THEN 1000000
        WHEN 'GOLD' THEN 5000000
        WHEN 'DIAMOND' THEN 15000000
        ELSE 0
    END,
    cashback_percent = CASE code
        WHEN 'BRONZE' THEN 1.00
        WHEN 'SILVER' THEN 2.00
        WHEN 'GOLD' THEN 3.00
        WHEN 'DIAMOND' THEN 5.00
        ELSE 0
    END;

ALTER TABLE membership_tiers
    DROP COLUMN required_points,
    DROP COLUMN benefit_limit;
