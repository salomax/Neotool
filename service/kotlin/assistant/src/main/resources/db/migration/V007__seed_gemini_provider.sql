-- ============================================================================
-- Assistant Service - Seed Gemini Provider and Default Model
-- Version: V007__seed_gemini_provider.sql
-- Purpose:
--   - Ensure Gemini is available as ACTIVE provider
--   - Seed default ACTIVE model gemini-2.5-flash-lite
--   - Make Gemini the first provider for default routing
--   - Apply pricing precision for very low per-token values
-- Source:
--   - https://ai.google.dev/gemini-api/docs/pricing?hl=pt-br
-- ============================================================================

SET search_path TO assistant, public;

-- ============================================================================
-- Seed provider: gemini (Google)
-- ============================================================================

INSERT INTO assistant.llm_providers (
    name,
    type,
    base_url,
    api_key_ref,
    status,
    priority
)
VALUES (
    'gemini',
    'GOOGLE',
    'https://generativelanguage.googleapis.com',
    'GEMINI_API_KEY',
    'ACTIVE',
    0
)
ON CONFLICT (name)
DO UPDATE SET
    type = EXCLUDED.type,
    base_url = EXCLUDED.base_url,
    api_key_ref = EXCLUDED.api_key_ref,
    status = 'ACTIVE',
    priority = EXCLUDED.priority,
    updated_at = NOW();

-- Keep non-Gemini providers behind Gemini in default ordering.
UPDATE assistant.llm_providers
SET priority = GREATEST(priority, 10),
    updated_at = NOW()
WHERE name <> 'gemini';

-- ============================================================================
-- Gemini 2.5 Flash-Lite (Standard, text/image/video):
-- Input  = $0.10 / 1,000,000 tokens = 0.000000100 per token
-- Output = $0.40 / 1,000,000 tokens = 0.000000400 per token
-- ============================================================================

ALTER TABLE assistant.llm_models
    ALTER COLUMN cost_per_input_token TYPE NUMERIC(14,9),
    ALTER COLUMN cost_per_output_token TYPE NUMERIC(14,9);

-- ============================================================================
-- Seed model: gemini-2.5-flash-lite
-- ============================================================================

INSERT INTO assistant.llm_models (
    provider_id,
    model_name,
    display_name,
    capabilities,
    context_window,
    max_output_tokens,
    cost_per_input_token,
    cost_per_output_token,
    status
)
SELECT
    p.id,
    'gemini-2.5-flash-lite',
    'Gemini 2.5 Flash-Lite',
    ARRAY['STREAMING', 'TOOLS', 'JSON_MODE', 'VISION']::TEXT[],
    1048576,
    65536,
    0.000000100,
    0.000000400,
    'ACTIVE'
FROM assistant.llm_providers p
WHERE p.name = 'gemini'
ON CONFLICT (provider_id, model_name)
DO UPDATE SET
    display_name = EXCLUDED.display_name,
    capabilities = EXCLUDED.capabilities,
    context_window = EXCLUDED.context_window,
    max_output_tokens = EXCLUDED.max_output_tokens,
    cost_per_input_token = EXCLUDED.cost_per_input_token,
    cost_per_output_token = EXCLUDED.cost_per_output_token,
    status = 'ACTIVE',
    updated_at = NOW();
