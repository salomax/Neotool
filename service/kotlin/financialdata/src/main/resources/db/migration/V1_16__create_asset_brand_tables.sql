-- Asset brand resolution tables
-- Purpose: keep logo discovery and manual correction separate from the
--          investment asset catalog itself.

SET search_path TO financial_data, public;

-- ============================================================================
-- Table: asset_brand
-- Purpose: canonical brand/domain/logo record discovered from logo search
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.asset_brand (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Canonical brand identity
    normalized_name TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    domain TEXT,
    logo_url TEXT,
    logo_asset_id UUID,

    -- Provenance and review metadata
    source TEXT NOT NULL DEFAULT 'BRANDFETCH_ISIN',
    confidence NUMERIC(5, 4),
    status TEXT NOT NULL DEFAULT 'PENDING',
    raw_search_payload TEXT,

    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_asset_brand_source
        CHECK (source IN ('LOGO_DEV_SEARCH', 'BRANDFETCH_ISIN', 'MANUAL')),
    CONSTRAINT ck_asset_brand_status
        CHECK (status IN ('PENDING', 'AUTO_APPROVED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_asset_brand_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX IF NOT EXISTS idx_asset_brand_domain
    ON financial_data.asset_brand (domain);

CREATE INDEX IF NOT EXISTS idx_asset_brand_status
    ON financial_data.asset_brand (status);

COMMENT ON TABLE financial_data.asset_brand IS
    'Canonical brand/domain/logo records discovered for investable assets, kept separate from the asset catalog for safe review and manual correction.';

COMMENT ON COLUMN financial_data.asset_brand.normalized_name IS
    'Normalized canonical brand name used for deduplication, e.g. PETROBRAS.';

COMMENT ON COLUMN financial_data.asset_brand.logo_url IS
    'External logo URL returned by the discovery provider. Suitable for review and later internalization.';

COMMENT ON COLUMN financial_data.asset_brand.logo_asset_id IS
    'Optional internal asset reference once the external logo has been imported into the assets service.';

COMMENT ON COLUMN financial_data.asset_brand.raw_search_payload IS
    'Raw provider payload used to create or refresh this brand record.';

-- ============================================================================
-- Table: investment_asset_brand_match
-- Purpose: per-asset brand candidates and review state
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.investment_asset_brand_match (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    investment_asset_id UUID NOT NULL,
    asset_brand_id UUID NOT NULL,

    query_used TEXT,
    candidate_rank INT,
    match_source TEXT NOT NULL DEFAULT 'BRANDFETCH_ISIN',
    match_confidence NUMERIC(5, 4),
    status TEXT NOT NULL DEFAULT 'PENDING',
    raw_match_payload TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_investment_asset_brand_match_asset
        FOREIGN KEY (investment_asset_id)
        REFERENCES financial_data.investment_asset(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_investment_asset_brand_match_brand
        FOREIGN KEY (asset_brand_id)
        REFERENCES financial_data.asset_brand(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_investment_asset_brand_match
        UNIQUE (investment_asset_id, asset_brand_id),

    CONSTRAINT ck_investment_asset_brand_match_source
        CHECK (match_source IN ('LOGO_DEV_SEARCH', 'BRANDFETCH_ISIN', 'MANUAL')),
    CONSTRAINT ck_investment_asset_brand_match_status
        CHECK (status IN ('PENDING', 'AUTO_APPROVED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_investment_asset_brand_match_confidence
        CHECK (match_confidence IS NULL OR (match_confidence >= 0 AND match_confidence <= 1))
);

CREATE INDEX IF NOT EXISTS idx_investment_asset_brand_match_asset
    ON financial_data.investment_asset_brand_match (investment_asset_id);

CREATE INDEX IF NOT EXISTS idx_investment_asset_brand_match_brand
    ON financial_data.investment_asset_brand_match (asset_brand_id);

CREATE INDEX IF NOT EXISTS idx_investment_asset_brand_match_status
    ON financial_data.investment_asset_brand_match (status);

-- Allow multiple candidates per asset, but only one accepted resolution.
CREATE UNIQUE INDEX IF NOT EXISTS uq_investment_asset_brand_match_resolved
    ON financial_data.investment_asset_brand_match (investment_asset_id)
    WHERE status IN ('AUTO_APPROVED', 'VERIFIED');

COMMENT ON TABLE financial_data.investment_asset_brand_match IS
    'Candidate brand matches for each investment asset, including provider rank, confidence, and review outcome.';

COMMENT ON COLUMN financial_data.investment_asset_brand_match.query_used IS
    'Search query sent to the logo discovery provider for this asset.';

COMMENT ON COLUMN financial_data.investment_asset_brand_match.raw_match_payload IS
    'Raw provider payload associated with this specific asset-brand candidate.';
