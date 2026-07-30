CREATE TABLE banking.come_cotas_checkpoint (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    holding_id      UUID NOT NULL REFERENCES banking.investment_holdings(id) ON DELETE CASCADE,
    checkpoint_date DATE NOT NULL,
    computed_amount NUMERIC(18,4) NOT NULL CHECK (computed_amount >= 0),
    withheld_amount NUMERIC(18,4) NOT NULL CHECK (withheld_amount >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_come_cotas_checkpoint_holding_date UNIQUE (holding_id, checkpoint_date)
);
