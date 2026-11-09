ALTER TABLE membership_tiers
    ADD COLUMN required_points BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN benefit_limit DECIMAL(19,0) NOT NULL DEFAULT 0;

UPDATE membership_tiers
SET required_points = CASE code
    WHEN 'BRONZE' THEN 0
    WHEN 'SILVER' THEN 500
    WHEN 'GOLD' THEN 1500
    WHEN 'DIAMOND' THEN 5000
    ELSE 0
END;
