-- Tags for manual categorization and filtering of transactions.
-- A transaction can have many tags, and tags are account-scoped for listing/filtering.

CREATE TABLE banking.transaction_tags (
    transaction_id UUID NOT NULL
        REFERENCES banking.transactions(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    tag TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_transaction_tags PRIMARY KEY (transaction_id, tag),
    CONSTRAINT chk_transaction_tags_tag_not_blank CHECK (length(btrim(tag)) > 0)
);

CREATE INDEX idx_transaction_tags_account_tag
    ON banking.transaction_tags(account_id, tag);

CREATE INDEX idx_transaction_tags_transaction
    ON banking.transaction_tags(transaction_id);
