CREATE TABLE IF NOT EXISTS banking.purchase_documents (
    id                UUID PRIMARY KEY DEFAULT uuidv7(),
    transaction_id    UUID NOT NULL REFERENCES banking.transactions(id),
    account_id        UUID NOT NULL,
    access_key        TEXT NOT NULL,
    issuer_cnpj       TEXT NOT NULL,
    issuer_name       TEXT NOT NULL,
    issuer_trade_name TEXT,
    issuer_state_registration TEXT,
    issuer_address    TEXT,
    issuer_postal_code TEXT,
    emission_date     TIMESTAMPTZ NOT NULL,
    total_value       NUMERIC(18,4) NOT NULL,
    total_tax         NUMERIC(18,4),
    buyer_cpf         TEXT,
    municipality_code TEXT,
    municipality      TEXT,
    state             TEXT NOT NULL,
    country_code      CHAR(2) NOT NULL DEFAULT 'BR',
    source_data       JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_purchase_documents_account_key UNIQUE (account_id, access_key)
);

CREATE TABLE IF NOT EXISTS banking.products (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id   UUID NOT NULL,
    name         TEXT NOT NULL,
    ncm_code     TEXT,
    default_unit TEXT,
    category     TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_products_account_name UNIQUE (account_id, name)
);

CREATE TABLE IF NOT EXISTS banking.product_variants (
    id                        UUID PRIMARY KEY DEFAULT uuidv7(),
    product_id                UUID NOT NULL REFERENCES banking.products(id),
    account_id                UUID NOT NULL,
    name                      TEXT NOT NULL,
    brand                     TEXT,
    ean                       TEXT,
    quantity_value            NUMERIC(12,4),
    quantity_unit             TEXT,
    normalized_quantity_value NUMERIC(12,4),
    normalized_quantity_unit  TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_variants_ean UNIQUE (ean)
);

CREATE TABLE IF NOT EXISTS banking.product_alias (
    id                 UUID PRIMARY KEY DEFAULT uuidv7(),
    product_variant_id UUID NOT NULL REFERENCES banking.product_variants(id),
    account_id         UUID NOT NULL,
    raw_description    TEXT NOT NULL,
    source             TEXT NOT NULL
                           CHECK (source IN ('MANUAL', 'AI_SUGGESTED', 'EAN_MATCH', 'HEURISTIC')),
    confidence         NUMERIC(3,2),
    confirmed          BOOLEAN NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_alias_account_description UNIQUE (account_id, raw_description)
);

CREATE TABLE IF NOT EXISTS banking.purchase_items (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    transaction_id      UUID NOT NULL REFERENCES banking.transactions(id),
    account_id          UUID NOT NULL,
    product_variant_id  UUID REFERENCES banking.product_variants(id),
    sequence            INT NOT NULL,
    raw_description     TEXT NOT NULL,
    ncm_code            TEXT,
    ean                 TEXT,
    issuer_product_code TEXT,
    quantity            NUMERIC(12,4) NOT NULL,
    unit                TEXT NOT NULL,
    unit_price          NUMERIC(18,4) NOT NULL,
    total_price         NUMERIC(18,4) NOT NULL,
    discount            NUMERIC(18,4),
    effective_price     NUMERIC(18,4) NOT NULL,
    tax_amount          NUMERIC(18,4),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS banking.ncm_codes (
    code        TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    depth       INT NOT NULL CHECK (depth BETWEEN 1 AND 4)
);

CREATE INDEX IF NOT EXISTS idx_purchase_documents_transaction
    ON banking.purchase_documents(transaction_id);

CREATE INDEX IF NOT EXISTS idx_purchase_documents_account_date
    ON banking.purchase_documents(account_id, emission_date DESC);

CREATE INDEX IF NOT EXISTS idx_purchase_items_transaction
    ON banking.purchase_items(transaction_id);

CREATE INDEX IF NOT EXISTS idx_purchase_items_account
    ON banking.purchase_items(account_id);

CREATE INDEX IF NOT EXISTS idx_purchase_items_variant_date
    ON banking.purchase_items(product_variant_id, created_at DESC)
    WHERE product_variant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_purchase_items_unmapped
    ON banking.purchase_items(account_id)
    WHERE product_variant_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_purchase_items_ncm
    ON banking.purchase_items(account_id, ncm_code)
    WHERE ncm_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_product_alias_variant
    ON banking.product_alias(product_variant_id);

CREATE INDEX IF NOT EXISTS idx_product_alias_description
    ON banking.product_alias(raw_description);

CREATE INDEX IF NOT EXISTS idx_product_variants_product
    ON banking.product_variants(product_id);

CREATE INDEX IF NOT EXISTS idx_product_variants_ean
    ON banking.product_variants(ean)
    WHERE ean IS NOT NULL;
