-- Align wards.province_code with provinces.code.
-- The provinces.code column currently uses utf8mb4_0900_ai_ci.
-- This prevents MySQL error 1267 when joining the two columns.

ALTER TABLE wards
    MODIFY COLUMN province_code VARCHAR(10)
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci
    NOT NULL;
