CREATE TABLE banking.recurring_payments (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id           UUID NOT NULL,
    financial_account_id UUID REFERENCES banking.financial_accounts(id),
    name                 VARCHAR(255) NOT NULL,
    credit_debit_type    VARCHAR(10)  NOT NULL CHECK (credit_debit_type IN ('CREDIT', 'DEBIT')),
    type                 VARCHAR(50),
    expected_amount      NUMERIC(19,4),
    currency_code        CHAR(3)      NOT NULL DEFAULT 'BRL',
    category_id          UUID REFERENCES banking.transaction_categories(id),
    merchant_id          UUID REFERENCES banking.merchants(id) ON DELETE SET NULL,
    frequency            VARCHAR(20)  NOT NULL CHECK (frequency IN ('MONTHLY', 'WEEKLY', 'YEARLY', 'CUSTOM')),
    day_of_month         SMALLINT,
    interval_days        INT,
    remind_days_before   SMALLINT     NOT NULL DEFAULT 3,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'PAUSED')),
    next_due_date        DATE         NOT NULL,
    last_paid_date       DATE,
    notes                TEXT,
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_payments_account
    ON banking.recurring_payments(account_id);

CREATE INDEX idx_recurring_payments_financial_account
    ON banking.recurring_payments(financial_account_id)
    WHERE financial_account_id IS NOT NULL;

CREATE INDEX idx_recurring_payments_status_due
    ON banking.recurring_payments(status, next_due_date)
    WHERE deleted_at IS NULL;

ALTER TABLE banking.transactions
    ADD COLUMN recurring_payment_id UUID REFERENCES banking.recurring_payments(id);

CREATE INDEX idx_transactions_recurring_payment
    ON banking.transactions(recurring_payment_id)
    WHERE recurring_payment_id IS NOT NULL AND deleted_at IS NULL;
