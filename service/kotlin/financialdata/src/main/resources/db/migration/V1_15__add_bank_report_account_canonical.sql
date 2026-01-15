-- Add canonical account support for bacen bank report accounts

-- Helper to normalize account names consistently
CREATE OR REPLACE FUNCTION financial_data.normalize_report_account_name(input TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT CASE
        WHEN input IS NULL THEN NULL
        ELSE TRIM('_' FROM UPPER(
            regexp_replace(
                regexp_replace(
                    regexp_replace(
                        regexp_replace(financial_data.unaccent(input), '[\n\r].*', '', 'g'),
                        '\(([^)]+)\)', '\1', 'g'
                    ),
                    '\s+', '_', 'g'
                ),
                '[/-]_', '', 'g'
            )
        ))
    END;
$$;

-- Helper to clean display name while preserving human-readable form
CREATE OR REPLACE FUNCTION financial_data.clean_report_account_display_name(input TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT CASE
        WHEN input IS NULL THEN NULL
        ELSE TRIM(regexp_replace(input, '[\n\r].*', '', 'g'))
    END;
$$;

-- Canonical account table
CREATE TABLE IF NOT EXISTS financial_data.bacen_bank_report_account_canonical (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    normalized_name TEXT NOT NULL UNIQUE,
    display_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bacen_bank_report_account_canonical_normalized_name
    ON financial_data.bacen_bank_report_account_canonical(normalized_name);

-- Add FK from account to canonical
ALTER TABLE financial_data.bacen_bank_report_account
    ADD COLUMN IF NOT EXISTS canonical_account_id UUID;

ALTER TABLE financial_data.bacen_bank_report_account
    ADD CONSTRAINT fk_bacen_bank_report_account_canonical
    FOREIGN KEY (canonical_account_id)
    REFERENCES financial_data.bacen_bank_report_account_canonical(id)
    ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_bacen_bank_report_account_canonical_account_id
    ON financial_data.bacen_bank_report_account(canonical_account_id);
    