ALTER TABLE shop_payouts DROP CHECK chk_shop_payout_status;
ALTER TABLE shop_payouts ADD CONSTRAINT chk_shop_payout_status CHECK (status IN ('PENDING','EXPORTED','PAID'));
