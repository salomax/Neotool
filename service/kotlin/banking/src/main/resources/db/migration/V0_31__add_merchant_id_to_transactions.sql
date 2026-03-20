-- V0_31__add_merchant_id_to_transactions.sql
-- Adds merchant_id to banking.transactions (nullable FK to banking.merchants).
-- Creates the v_top_merchants view for per-account merchant analytics.

SET search_path TO banking, public;

-- ─────────────────────────────────────────────────────────────────────────────
-- merchant_id column on transactions
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE banking.transactions
    ADD COLUMN merchant_id UUID REFERENCES banking.merchants (id) ON DELETE SET NULL;

CREATE INDEX idx_transactions_merchant_id
    ON banking.transactions (merchant_id)
    WHERE merchant_id IS NOT NULL AND deleted_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- Top-merchants analytics view
-- Groups non-deleted transactions by (account, financial_account, merchant).
-- Useful for "top merchants this month" dashboards.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE VIEW banking.v_top_merchants AS
SELECT
    t.account_id,
    t.financial_account_id,
    m.id          AS merchant_id,
    m.name        AS merchant_name,
    m.domain      AS merchant_domain,
    m.sector      AS merchant_sector,
    COUNT(*)               AS transaction_count,
    SUM(t.amount)          AS total_amount,
    MAX(t.date)            AS last_used_at
FROM banking.transactions t
JOIN banking.merchants m ON m.id = t.merchant_id
WHERE t.deleted_at IS NULL
GROUP BY
    t.account_id,
    t.financial_account_id,
    m.id,
    m.name,
    m.domain,
    m.sector;
