-- Keep the demo shop usable under the bank-approval-before-selling rule.
UPDATE shops s
JOIN users u ON u.id = s.owner_id
SET s.bank_name = COALESCE(NULLIF(s.bank_name, ''), 'Demo Bank'),
    s.bank_account_number = COALESCE(NULLIF(s.bank_account_number, ''), '0000000000'),
    s.bank_account_name = COALESCE(NULLIF(s.bank_account_name, ''), 'DEMO SHOP OWNER'),
    s.bank_branch = COALESCE(NULLIF(s.bank_branch, ''), 'Demo Branch'),
    s.payment_approval_status = 'APPROVED',
    s.payment_reviewed_at = COALESCE(s.payment_reviewed_at, CURRENT_TIMESTAMP(3))
WHERE u.email = 'owner.demo@freshfruit.example'
  AND s.approval_status = 'APPROVED';
