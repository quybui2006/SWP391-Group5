-- Migration: Add 3-stage seller account approval status columns to shops table
ALTER TABLE shops 
    ADD COLUMN seller_approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' AFTER payment_reviewed_at,
    ADD COLUMN seller_rejection_reason VARCHAR(1000) NULL AFTER seller_approval_status,
    ADD COLUMN seller_reviewed_at DATETIME NULL AFTER seller_rejection_reason;
