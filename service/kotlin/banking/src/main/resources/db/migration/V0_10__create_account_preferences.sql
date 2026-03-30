CREATE TABLE banking.account_preferences (
    account_id              UUID        PRIMARY KEY,
    rule_application        TEXT NOT NULL DEFAULT 'ALWAYS_PROMPT'
                            CHECK (rule_application IN ('NEVER', 'ALWAYS', 'ALWAYS_PROMPT')),
    default_txn_status      TEXT NOT NULL DEFAULT 'POSTED'
                            CHECK (default_txn_status IN ('POSTED', 'PENDING')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
