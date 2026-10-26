
ALTER TABLE orders DROP CHECK orders_chk_21;

ALTER TABLE orders ADD CONSTRAINT orders_chk_21
CHECK (
    (payment_status = 'UNPAID' AND paid_at IS NULL) OR
    (payment_status = 'PAID' AND paid_at IS NOT NULL) OR
    (payment_status = 'REFUNDED' AND paid_at IS NOT NULL)
);