-- ─── Monthly Balance Snapshots ────────────────────────────────────────────────
-- Read-cache for the monthly summary header (entradas, saídas, saldo total).
-- Source of truth is always banking.transactions — this table is a derived view
-- materialised per (financial_account, year, month) and updated synchronously
-- on every transaction write.
--
-- Design rules:
--   • opening_balance = prior month's closing_balance, or 0 at account genesis.
--   • closing_balance = opening_balance + SUM(CREDIT) - SUM(DEBIT) for the month.
--   • total_income    = SUM(CREDIT) WHERE type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT').
--   • total_expenses  = SUM(DEBIT)  WHERE type NOT IN ('TRANSFER','BALANCE_ADJUSTMENT').
--   • FUTURE-status transactions are excluded from all balance fields.
--   • is_dirty = true signals a failed mid-chain recompute; read path heals on demand.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.monthly_balance_snapshots (
    financial_account_id UUID        NOT NULL
                             REFERENCES banking.financial_accounts(id) ON DELETE CASCADE,
    account_id           UUID        NOT NULL,
    year                 INT         NOT NULL,
    month                INT         NOT NULL,

    opening_balance      NUMERIC(18,4) NOT NULL,
    closing_balance      NUMERIC(18,4) NOT NULL,
    total_income         NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_expenses       NUMERIC(18,4) NOT NULL DEFAULT 0,

    -- Safety net: set when a synchronous recompute fails partway through.
    -- The read path (monthSummary query) recomputes dirty rows on demand.
    is_dirty             BOOLEAN     NOT NULL DEFAULT false,
    computed_at          TIMESTAMPTZ,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_monthly_balance_snapshots
        PRIMARY KEY (financial_account_id, year, month),

    CONSTRAINT chk_month_range
        CHECK (month BETWEEN 1 AND 12)
);

-- Fast lookup for the sweeper job (heals dirty rows)
CREATE INDEX idx_monthly_balance_snapshots_dirty
    ON banking.monthly_balance_snapshots (financial_account_id, year, month)
    WHERE is_dirty = true;
