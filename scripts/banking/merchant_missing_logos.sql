-- Identify merchants that currently resolve to no logo.
--
-- A merchant has no logo when either:
-- 1. its domain has no row in banking.merchant_logo_cache,
-- 2. its cache row exists but asset_id is NULL, or
-- 3. it has no usable domain (edge case).
--
-- Usage:
--   psql "$DATABASE_URL" -f scripts/banking/merchant_missing_logos.sql

WITH merchants_without_logo AS (
    SELECT
        m.id,
        m.merchant_name,
        m.display_name,
        m.domain,
        m.sector,
        m.is_system,
        m.account_id,
        m.created_at,
        m.updated_at,
        CASE
            WHEN m.domain IS NULL OR btrim(m.domain) = '' THEN 'missing_domain'
            WHEN mlc.domain IS NULL THEN 'missing_logo_cache'
            WHEN mlc.asset_id IS NULL THEN 'missing_logo_asset_id'
            ELSE 'missing_logo_cache'
        END AS missing_logo_reason
    FROM banking.merchants m
    LEFT JOIN banking.merchant_logo_cache mlc
        ON mlc.domain = m.domain
    WHERE mlc.domain IS NULL
       OR mlc.asset_id IS NULL
       OR m.domain IS NULL
       OR btrim(m.domain) = ''
)
SELECT
    id,
    merchant_name,
    display_name,
    domain,
    sector,
    is_system,
    account_id,
    missing_logo_reason,
    created_at,
    updated_at
FROM merchants_without_logo
ORDER BY
    missing_logo_reason ASC,
    display_name ASC,
    id ASC;
