-- The application no longer exposes or writes to the legacy customer money wallet.
-- Retain existing wallet tables/data for audit/backward compatibility; do not drop financial history.
SELECT 1;
