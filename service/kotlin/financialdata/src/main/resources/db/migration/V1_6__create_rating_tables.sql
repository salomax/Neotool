-- Migration: Create financial institution rating tables
-- Version: V1_6
-- Description: Create tables for financial institution ratings from external issuers (Fitch, S&P Global, Moody's)
--
-- This migration creates:
-- 1. rating_issuer: Rating agency metadata and configuration
-- 2. financial_institution_rating_issuer: Maps institutions to issuer-specific identifiers
-- 3. rating_source_evidence: Raw API responses for audit compliance
-- 4. financial_institution_rating: Current ratings for each institution
-- 5. financial_institution_rating_history: Audit trail for all rating changes
--
-- Key features:
-- - Idempotent processing with content_hash for change detection
-- - Evidence storage for audit compliance
-- - Per-institution issuer configuration (slugs, IDs)
-- - Full history tracking for rating changes
-- - JSONB storage for issuer-specific custom parameters

SET search_path TO financial_data, public;

-- ============================================================================
-- Table: rating_issuer
-- Purpose: Rating agencies (Fitch, S&P Global, Moody's, etc.)
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.rating_issuer (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Unique identifier code (e.g., FITCH, SP_GLOBAL, MOODYS)
    code TEXT NOT NULL UNIQUE,

    -- Display name
    display_name TEXT NOT NULL,

    -- Issuer website
    website TEXT,

    -- Issuer-specific configuration (api_url, throttle_ms, identifier_field, etc.)
    config JSONB NOT NULL DEFAULT '{}',

    -- Logo asset reference (via GraphQL federation)
    logo_asset_id UUID,

    -- Whether this issuer is enabled for ingestion
    enabled BOOLEAN NOT NULL DEFAULT true,

    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Comments for rating_issuer
COMMENT ON TABLE financial_data.rating_issuer IS
    'Rating agencies (Fitch, S&P Global, Moody''s, etc.) with their configuration for API access.';

COMMENT ON COLUMN financial_data.rating_issuer.code IS
    'Unique identifier code (e.g., FITCH, SP_GLOBAL, MOODYS). Used as reference in code.';

COMMENT ON COLUMN financial_data.rating_issuer.config IS
    'Issuer-specific configuration as JSONB. Example: {"api_url": "https://api.fitchratings.com/", "throttle_ms": 15000, "identifier_field": "slug"}';

COMMENT ON COLUMN financial_data.rating_issuer.logo_asset_id IS
    'Asset ID (UUID) referencing the Asset entity via GraphQL federation. Used to resolve logoAsset field in GraphQL queries.';

-- ============================================================================
-- Table: financial_institution_rating_issuer
-- Purpose: Maps financial institutions to issuer-specific identifiers
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.financial_institution_rating_issuer (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Foreign key to rating_issuer
    rating_issuer_id UUID NOT NULL,

    -- Foreign key to financial_institution
    financial_institution_id UUID NOT NULL,

    -- Issuer-specific identifiers (e.g., {"slug": "banco-do-brasil-sa-80089721"} for Fitch)
    config JSONB,

    -- Whether ingestion is enabled for this institution/issuer combination
    enabled BOOLEAN NOT NULL DEFAULT true,

    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_fi_rating_issuer_rating_issuer
        FOREIGN KEY (rating_issuer_id)
        REFERENCES financial_data.rating_issuer(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_fi_rating_issuer_financial_institution
        FOREIGN KEY (financial_institution_id)
        REFERENCES financial_data.financial_institution(id)
        ON DELETE CASCADE,

    -- Unique constraint: one mapping per institution/issuer pair
    CONSTRAINT uq_fi_rating_issuer_institution_issuer
        UNIQUE (rating_issuer_id, financial_institution_id)
);

-- Indexes for financial_institution_rating_issuer
CREATE INDEX IF NOT EXISTS idx_fi_rating_issuer_issuer
    ON financial_data.financial_institution_rating_issuer(rating_issuer_id);

CREATE INDEX IF NOT EXISTS idx_fi_rating_issuer_institution
    ON financial_data.financial_institution_rating_issuer(financial_institution_id);

CREATE INDEX IF NOT EXISTS idx_fi_rating_issuer_enabled
    ON financial_data.financial_institution_rating_issuer(enabled)
    WHERE enabled = true;

-- Comments for financial_institution_rating_issuer
COMMENT ON TABLE financial_data.financial_institution_rating_issuer IS
    'Maps financial institutions to rating issuers with issuer-specific identifiers. Each institution can have different identifiers per issuer (e.g., Fitch slug, S&P entity_id).';

COMMENT ON COLUMN financial_data.financial_institution_rating_issuer.config IS
    'Issuer-specific identifiers as JSONB. Example for Fitch: {"slug": "banco-do-brasil-sa-80089721"}. Example for S&P: {"entity_id": "349026"}.';

-- ============================================================================
-- Table: rating_source_evidence
-- Purpose: Raw API responses stored for audit compliance
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.rating_source_evidence (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Foreign key to rating_issuer
    issuer_id UUID NOT NULL,

    -- When the data was fetched
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Request/response metadata (url, http_status, content_type, request_id)
    metadata JSONB NOT NULL DEFAULT '{}',

    -- Raw API response payload (JSON or HTML)
    raw_payload TEXT NOT NULL,

    -- SHA-256 hash of raw_payload for deduplication
    content_hash CHAR(64) NOT NULL,

    -- Audit timestamp
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_rating_source_evidence_issuer
        FOREIGN KEY (issuer_id)
        REFERENCES financial_data.rating_issuer(id)
        ON DELETE CASCADE
);

-- Indexes for rating_source_evidence
CREATE INDEX IF NOT EXISTS idx_rating_source_evidence_issuer
    ON financial_data.rating_source_evidence(issuer_id);


CREATE INDEX IF NOT EXISTS idx_rating_source_evidence_hash
    ON financial_data.rating_source_evidence(content_hash);

-- Comments for rating_source_evidence
COMMENT ON TABLE financial_data.rating_source_evidence IS
    'Stores raw API responses from rating issuers for audit compliance and troubleshooting. Each fetch is stored with its metadata and content hash.';

COMMENT ON COLUMN financial_data.rating_source_evidence.metadata IS
    'Request/response metadata as JSONB. Example: {"url": "https://api.fitchratings.com/", "http_status": 200, "content_type": "application/json", "request_id": "..."}';

COMMENT ON COLUMN financial_data.rating_source_evidence.raw_payload IS
    'Raw API response payload. For APIs: JSON string. For scrapers: rendered HTML without scripts.';

COMMENT ON COLUMN financial_data.rating_source_evidence.content_hash IS
    'SHA-256 hash of raw_payload for deduplication. Prevents storing duplicate evidence.';

-- ============================================================================
-- Table: financial_institution_rating
-- Purpose: Current ratings for financial institutions
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.financial_institution_rating (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Foreign key to financial_institution
    financial_institution_id UUID NOT NULL,

    -- Foreign key to rating_issuer
    issuer_id UUID NOT NULL,

    -- Rating type in UPPER_SNAKE_CASE (e.g., LONG_TERM_ISSUER_DEFAULT_RATING)
    rating_name VARCHAR(255) NOT NULL,

    -- Rating value/label (e.g., AAA(bra), BB, F1+)
    rating_label VARCHAR(100) NOT NULL,

    -- Effective date of the rating
    rating_date DATE NOT NULL,

    -- Issuer-specific fields (action, alert_code, fitch_id, etc.)
    custom_params JSONB NOT NULL DEFAULT '{}',

    -- Reference to the source evidence
    source_evidence_id UUID,

    -- SHA-256 hash for idempotency detection
    content_hash CHAR(64) NOT NULL,

    -- Audit timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_fi_rating_financial_institution
        FOREIGN KEY (financial_institution_id)
        REFERENCES financial_data.financial_institution(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_fi_rating_issuer
        FOREIGN KEY (issuer_id)
        REFERENCES financial_data.rating_issuer(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_fi_rating_source_evidence
        FOREIGN KEY (source_evidence_id)
        REFERENCES financial_data.rating_source_evidence(id)
        ON DELETE SET NULL,

    -- Unique constraint: one rating per institution/issuer/rating_name combination
    CONSTRAINT uq_fi_rating_institution_issuer_rating_name
        UNIQUE (financial_institution_id, issuer_id, rating_name)
);

-- Indexes for financial_institution_rating
CREATE INDEX IF NOT EXISTS idx_fi_rating_institution
    ON financial_data.financial_institution_rating(financial_institution_id);

CREATE INDEX IF NOT EXISTS idx_fi_rating_issuer
    ON financial_data.financial_institution_rating(issuer_id);

-- Comments for financial_institution_rating
COMMENT ON TABLE financial_data.financial_institution_rating IS
    'Current ratings for financial institutions from various issuers. Each rating type is unique per institution/issuer combination.';

COMMENT ON COLUMN financial_data.financial_institution_rating.rating_name IS
    'Rating type in UPPER_SNAKE_CASE. Examples: LONG_TERM_ISSUER_DEFAULT_RATING, NATIONAL_SHORT_TERM_RATING, VIABILITY_RATING.';

COMMENT ON COLUMN financial_data.financial_institution_rating.rating_label IS
    'Rating value/label as provided by the issuer. Examples: AAA(bra), BB, F1+, bb.';

COMMENT ON COLUMN financial_data.financial_institution_rating.custom_params IS
    'Issuer-specific fields as JSONB. For Fitch: {"fitch_id": "129697998", "action": "Affirmed", "alert_code": "RO:Sta", "local_value": "BB"}';

COMMENT ON COLUMN financial_data.financial_institution_rating.content_hash IS
    'SHA-256 hash of normalized rating content for idempotency. Hash includes: issuer_id + name + label + rating_date + custom_params.';

-- ============================================================================
-- Table: financial_institution_rating_history
-- Purpose: Audit trail for all rating changes
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.financial_institution_rating_history (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Foreign key to financial_institution
    financial_institution_id UUID NOT NULL,

    -- Foreign key to rating_issuer
    issuer_id UUID NOT NULL,

    -- Rating type in UPPER_SNAKE_CASE
    rating_name VARCHAR(255) NOT NULL,

    -- Rating value/label
    rating_label VARCHAR(100) NOT NULL,

    -- Effective date of the rating
    rating_date DATE NOT NULL,

    -- Issuer-specific fields
    custom_params JSONB NOT NULL DEFAULT '{}',

    -- Reference to the source evidence
    source_evidence_id UUID,

    -- SHA-256 hash for reference
    content_hash CHAR(64) NOT NULL,

    -- When this change was recorded
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_fi_rating_history_financial_institution
        FOREIGN KEY (financial_institution_id)
        REFERENCES financial_data.financial_institution(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_fi_rating_history_issuer
        FOREIGN KEY (issuer_id)
        REFERENCES financial_data.rating_issuer(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_fi_rating_history_source_evidence
        FOREIGN KEY (source_evidence_id)
        REFERENCES financial_data.rating_source_evidence(id)
        ON DELETE SET NULL
);

-- Indexes for financial_institution_rating_history
CREATE INDEX IF NOT EXISTS idx_fi_rating_history_institution
    ON financial_data.financial_institution_rating_history(financial_institution_id);

CREATE INDEX IF NOT EXISTS idx_fi_rating_history_issuer
    ON financial_data.financial_institution_rating_history(issuer_id);

CREATE INDEX IF NOT EXISTS idx_fi_rating_history_changed
    ON financial_data.financial_institution_rating_history(changed_at);

CREATE INDEX IF NOT EXISTS idx_fi_rating_history_rating_name
    ON financial_data.financial_institution_rating_history(rating_name);

CREATE INDEX IF NOT EXISTS idx_fi_rating_history_institution_rating_name
    ON financial_data.financial_institution_rating_history(financial_institution_id, rating_name, changed_at DESC);

-- Comments for financial_institution_rating_history
COMMENT ON TABLE financial_data.financial_institution_rating_history IS
    'Historical record of all rating changes. A new record is created whenever a rating is created or updated.';

COMMENT ON COLUMN financial_data.financial_institution_rating_history.changed_at IS
    'Timestamp when this rating change was recorded by the ingestion process.';

-- ============================================================================
-- Triggers: Auto-update updated_at timestamp
-- ============================================================================

-- Function to update timestamp (if not already exists)
CREATE OR REPLACE FUNCTION financial_data.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for rating_issuer
DROP TRIGGER IF EXISTS tr_rating_issuer_updated ON financial_data.rating_issuer;
CREATE TRIGGER tr_rating_issuer_updated
    BEFORE UPDATE ON financial_data.rating_issuer
    FOR EACH ROW EXECUTE FUNCTION financial_data.update_updated_at_column();

-- Trigger for financial_institution_rating_issuer
DROP TRIGGER IF EXISTS tr_fi_rating_issuer_updated ON financial_data.financial_institution_rating_issuer;
CREATE TRIGGER tr_fi_rating_issuer_updated
    BEFORE UPDATE ON financial_data.financial_institution_rating_issuer
    FOR EACH ROW EXECUTE FUNCTION financial_data.update_updated_at_column();

-- Trigger for financial_institution_rating
DROP TRIGGER IF EXISTS tr_fi_rating_updated ON financial_data.financial_institution_rating;
CREATE TRIGGER tr_fi_rating_updated
    BEFORE UPDATE ON financial_data.financial_institution_rating
    FOR EACH ROW EXECUTE FUNCTION financial_data.update_updated_at_column();
