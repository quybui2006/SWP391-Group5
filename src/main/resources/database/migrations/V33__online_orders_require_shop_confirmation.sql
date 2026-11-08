-- Keep online orders in PENDING after a successful payment. Shop confirmation remains required.
-- No schema change is needed; the existing payment_status=PAID and status=PENDING combination is valid.
SELECT 1;
