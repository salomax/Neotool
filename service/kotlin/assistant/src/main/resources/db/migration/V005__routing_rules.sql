-- ============================================================================
-- Assistant Service - Provider Routing Rules
-- Version: V005__routing_rules.sql
-- Purpose:
--   - Define model routing rules for deterministic traffic shifting
-- ============================================================================

SET search_path TO assistant, public;

-- ============================================================================
-- Table: model_routing_rules
-- Purpose: Weighted routing preferences for active models
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.model_routing_rules (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    priority INTEGER NOT NULL,
    conditions JSONB NOT NULL DEFAULT '{}',
    target_model_id UUID NOT NULL,
    weight_percent INTEGER NOT NULL CHECK (weight_percent >= 0 AND weight_percent <= 100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_model_routing_rules_target_model
        FOREIGN KEY (target_model_id)
        REFERENCES assistant.llm_models(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_model_routing_rules_enabled_priority
    ON assistant.model_routing_rules(enabled, priority ASC);

CREATE INDEX IF NOT EXISTS idx_model_routing_rules_target_model
    ON assistant.model_routing_rules(target_model_id);
