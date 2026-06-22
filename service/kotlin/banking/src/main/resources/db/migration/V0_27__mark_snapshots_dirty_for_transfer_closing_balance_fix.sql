-- Mark all monthly balance snapshots dirty so the sweeper recomputes them
-- with the corrected closingBalance formula (includes TRANSFER and BILL_PAYMENT).
UPDATE banking.monthly_balance_snapshots
SET is_dirty = TRUE;
