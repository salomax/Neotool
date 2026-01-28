-- Migration: Seed rating issuers
-- Version: V1_7
-- Description: Insert initial rating issuer records (Fitch, S&P Global, Moody's)
--
-- Phase 1: Only Fitch is enabled
-- Future phases will enable S&P Global and Moody's

SET search_path TO financial_data, public;

-- ============================================================================
-- Seed Data: Rating Issuers
-- ============================================================================

-- Fitch Ratings (Phase 1 - enabled)
INSERT INTO financial_data.rating_issuer (code, display_name, website, config, enabled)
VALUES (
    'FITCH',
    'Fitch Ratings',
    'https://www.fitchratings.com',
    '{
        "api_url": "https://api.fitchratings.com/",
        "url_base": "https://www.fitchratings.com/entity/",
        "throttle_ms": 5000,
        "graphql_operation": "Entity",
        "identifier_field": "slug"
    }'::jsonb,
    true
)
ON CONFLICT (code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    website = EXCLUDED.website,
    config = EXCLUDED.config,
    enabled = EXCLUDED.enabled,
    updated_at = NOW();

-- S&P Global Ratings (Future - disabled)
INSERT INTO financial_data.rating_issuer (code, display_name, website, config, enabled)
VALUES (
    'SP_GLOBAL',
    'S&P Global Ratings',
    'https://www.spglobal.com/ratings',
    '{
        "identifier_field": "entity_id"
    }'::jsonb,
    false
)
ON CONFLICT (code) DO NOTHING;

-- Moody's Investors Service (Future - disabled)
INSERT INTO financial_data.rating_issuer (code, display_name, website, config, enabled)
VALUES (
    'MOODYS',
    'Moody''s Investors Service',
    'https://www.moodys.com',
    '{
        "identifier_field": "org_id"
    }'::jsonb,
    false
)
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- Comments
-- ============================================================================

-- Note: To add institution-to-issuer mappings, use the following pattern:
--
-- INSERT INTO financial_data.financial_institution_rating_issuer
--     (rating_issuer_id, financial_institution_id, config, enabled)
-- SELECT
--     ri.id,
--     fi.id,
--     '{"slug": "banco-do-brasil-sa-80089721"}'::jsonb,
--     true
-- FROM financial_data.rating_issuer ri
-- CROSS JOIN financial_data.financial_institution fi
-- WHERE ri.code = 'FITCH'
--   AND fi.parent_cnpj = '00000000000191';  -- Banco do Brasil
--
-- The mapping data is pre-loaded manually since discovering issuer-specific
-- identifiers (slugs, IDs) requires manual lookup on each issuer's website.
