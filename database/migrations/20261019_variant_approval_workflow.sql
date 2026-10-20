-- FreshFruit: move Operations approval from parent products to product variants.
-- Existing-data migration for MySQL 8.0.16+. Take a database backup before running.
-- This migration does NOT drop tables or delete product, variant, inventory, order, or review data.
-- Run once while connected to the FreshFruit database.

-- Remove the parent-level sale/approval constraint; parent products become management-only records.
ALTER TABLE products DROP CHECK chk_products_active_approved;

ALTER TABLE product_variants
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN submitted_at DATETIME(3) NULL,
    ADD COLUMN reviewed_at DATETIME(3) NULL,
    ADD COLUMN rejection_reason VARCHAR(1000) NULL;

-- Preserve old approval decisions where the parent product had already been approved.
-- Products previously waiting for review move their variants to the new variant queue.
UPDATE product_variants pv
JOIN products p ON p.id = pv.product_id
SET pv.approval_status = CASE
        WHEN p.approval_status = 'APPROVED' THEN 'APPROVED'
        WHEN p.approval_status = 'PENDING' THEN 'PENDING'
        WHEN p.approval_status = 'REJECTED' THEN 'REJECTED'
        ELSE 'DRAFT'
    END,
    pv.submitted_at = CASE
        WHEN p.approval_status IN ('PENDING','APPROVED','REJECTED')
            THEN COALESCE(p.submitted_at, p.created_at, pv.created_at)
        ELSE NULL
    END,
    pv.reviewed_at = CASE
        WHEN p.approval_status IN ('APPROVED','REJECTED')
            THEN COALESCE(p.reviewed_at, p.updated_at, CURRENT_TIMESTAMP(3))
        ELSE NULL
    END,
    pv.rejection_reason = CASE
        WHEN p.approval_status = 'REJECTED'
            THEN COALESCE(NULLIF(TRIM(p.rejection_reason), ''), 'Cần gửi duyệt lại theo quy trình duyệt biến thể.')
        ELSE NULL
    END,
    pv.status = CASE
        WHEN p.approval_status IN ('PENDING','REJECTED') THEN 'DRAFT'
        ELSE pv.status
    END;

ALTER TABLE product_variants
    ADD CONSTRAINT chk_product_variants_approval_status
        CHECK (approval_status IN ('DRAFT','PENDING','APPROVED','REJECTED')),
    ADD CONSTRAINT chk_product_variants_submitted
        CHECK (approval_status <> 'PENDING' OR submitted_at IS NOT NULL),
    ADD CONSTRAINT chk_product_variants_reviewed
        CHECK (approval_status NOT IN ('APPROVED','REJECTED') OR reviewed_at IS NOT NULL),
    ADD CONSTRAINT chk_product_variants_rejection
        CHECK (approval_status <> 'REJECTED' OR (rejection_reason IS NOT NULL AND CHAR_LENGTH(TRIM(rejection_reason)) > 0));

-- Parent Product is only a grouping/metadata record; clear its legacy review data.
-- Its selling status is kept non-archived so approved variants remain available.
UPDATE products
SET approval_status = 'DRAFT',
    selling_status = CASE WHEN selling_status = 'ARCHIVED' THEN 'ARCHIVED' ELSE 'DRAFT' END,
    submitted_at = NULL,
    reviewed_at = NULL,
    rejection_reason = NULL;

-- Older variants may not have their own image. Copy the product's primary image
-- as a one-time fallback so each migrated variant is reviewable in Operations.
UPDATE product_variants pv
JOIN products p ON p.id = pv.product_id
JOIN product_images pi ON pi.product_id = p.id AND pi.sort_order = 0
SET pv.image_url = pi.image_url
WHERE pv.image_url IS NULL OR CHAR_LENGTH(TRIM(pv.image_url)) = 0;

-- After migration: customer catalog/cart/checkout code requires variant approval_status='APPROVED'.
