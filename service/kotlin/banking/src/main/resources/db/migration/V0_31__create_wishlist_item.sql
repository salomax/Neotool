CREATE TABLE banking.wishlist_item (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id      UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    estimated_price NUMERIC(19,4),
    currency_code   CHAR(3)      NOT NULL DEFAULT 'BRL',
    category        VARCHAR(20)  NOT NULL DEFAULT 'PRODUCT'
                        CHECK (category IN ('PRODUCT', 'SERVICE', 'EXPERIENCE')),
    priority        VARCHAR(10)  NOT NULL
                        CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    notes           TEXT,
    target_date     DATE,
    product_url     TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'PURCHASED')),
    sort_order      INT          NOT NULL DEFAULT 0,
    purchased_at    DATE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wishlist_item_account_status
    ON banking.wishlist_item(account_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_wishlist_item_account_sort
    ON banking.wishlist_item(account_id, sort_order)
    WHERE deleted_at IS NULL;
