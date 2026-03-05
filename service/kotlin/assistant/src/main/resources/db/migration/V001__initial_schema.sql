-- ============================================================================
-- Assistant Service - Initial Database Schema
-- Version: V001__initial_schema.sql
-- Purpose:
--   - Create assistant schema
--   - Define core tables for prompts, providers/models, completions, and usage
--   - Apply constraints and indexes aligned with domain model and standards
-- ============================================================================

-- Ensure schema exists
CREATE SCHEMA IF NOT EXISTS assistant;

-- Set search path to assistant schema (while keeping public available)
SET search_path TO assistant, public;

-- ============================================================================
-- Table: prompts
-- Purpose: Versioned prompt templates with metadata and tags
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.prompts (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Business fields
    name        TEXT NOT NULL,
    category    TEXT NOT NULL,
    description TEXT,

    -- Tags for filtering (e.g. ["billing", "support"])
    tags TEXT[] NOT NULL DEFAULT '{}',

    -- Audit / ownership
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_prompts_created_by_user
        FOREIGN KEY (created_by)
        REFERENCES security.users(id)
        ON DELETE RESTRICT
);

-- Indexes for prompts
CREATE INDEX IF NOT EXISTS idx_prompts_category
    ON assistant.prompts(category);

CREATE INDEX IF NOT EXISTS idx_prompts_tags_gin
    ON assistant.prompts
    USING GIN (tags);

-- ============================================================================
-- Table: prompt_versions
-- Purpose: Versioned prompt content and configuration
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.prompt_versions (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Foreign key to prompts
    prompt_id UUID NOT NULL,

    -- Version number per prompt (1, 2, 3, ...)
    version INTEGER NOT NULL,

    -- Status: DRAFT, ACTIVE, DEPRECATED
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED')),

    -- Template content and configuration
    template          TEXT  NOT NULL,
    variables_schema  JSONB NOT NULL DEFAULT '{}',
    output_format     TEXT  NOT NULL CHECK (output_format IN ('TEXT', 'JSON')),
    model_requirements JSONB,
    performance_metrics JSONB,

    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_prompt_versions_prompt
        FOREIGN KEY (prompt_id)
        REFERENCES assistant.prompts(id)
        ON DELETE CASCADE,

    -- Each prompt/version pair must be unique
    CONSTRAINT uq_prompt_versions_prompt_version
        UNIQUE (prompt_id, version)
);

-- At most one ACTIVE version per prompt
CREATE UNIQUE INDEX IF NOT EXISTS uq_prompt_versions_prompt_active
    ON assistant.prompt_versions(prompt_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_prompt_versions_status
    ON assistant.prompt_versions(status);

-- ============================================================================
-- Table: llm_providers
-- Purpose: External LLM providers (OpenAI, Anthropic, Google, etc.)
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.llm_providers (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Provider identification
    name TEXT NOT NULL,

    -- Provider type enumeration
    -- OPENAI, ANTHROPIC, GOOGLE, AZURE, OTHER
    type TEXT NOT NULL CHECK (type IN ('OPENAI', 'ANTHROPIC', 'GOOGLE', 'AZURE', 'OTHER')),

    -- HTTP configuration
    base_url    TEXT NOT NULL,
    api_key_ref TEXT NOT NULL,

    -- Status and routing priority
    status   TEXT    NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    priority INTEGER NOT NULL DEFAULT 100,

    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT uq_llm_providers_name
        UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_llm_providers_status
    ON assistant.llm_providers(status);

CREATE INDEX IF NOT EXISTS idx_llm_providers_type
    ON assistant.llm_providers(type);

-- ============================================================================
-- Table: llm_models
-- Purpose: Catalog of models per provider with capabilities and pricing
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.llm_models (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Provider relationship
    provider_id UUID NOT NULL,

    -- Identifiers and display
    model_name   TEXT NOT NULL,
    display_name TEXT NOT NULL,

    -- Capabilities (e.g. ["STREAMING","TOOLS","JSON_MODE","VISION"])
    capabilities TEXT[] NOT NULL DEFAULT '{}',

    -- Context window and output limits (tokens)
    context_window    INTEGER NOT NULL,
    max_output_tokens INTEGER NOT NULL,

    -- Pricing (per 1K tokens or similar unit)
    cost_per_input_token  NUMERIC(12,6) NOT NULL,
    cost_per_output_token NUMERIC(12,6) NOT NULL,

    -- Status: ACTIVE, DEPRECATED, DISABLED
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'DEPRECATED', 'DISABLED')),

    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_llm_models_provider
        FOREIGN KEY (provider_id)
        REFERENCES assistant.llm_providers(id)
        ON DELETE CASCADE,

    -- One row per provider/model_name
    CONSTRAINT uq_llm_models_provider_model_name
        UNIQUE (provider_id, model_name)
);

CREATE INDEX IF NOT EXISTS idx_llm_models_status
    ON assistant.llm_models(status);

CREATE INDEX IF NOT EXISTS idx_llm_models_capabilities_gin
    ON assistant.llm_models
    USING GIN (capabilities);

-- ============================================================================
-- Table: completion_requests
-- Purpose: Incoming completion requests with full context and options
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.completion_requests (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Ownership and attribution
    account_id UUID NOT NULL,
    requested_by_service TEXT NOT NULL,
    user_id UUID,

    -- Prompt linkage
    prompt_id         UUID,
    prompt_version_id UUID,

    -- Request payload
    messages  JSONB NOT NULL,
    variables JSONB,
    options   JSONB,

    -- LGPD / audit metadata
    purpose  TEXT  NOT NULL,
    metadata JSONB,

    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_completion_requests_account
        FOREIGN KEY (account_id)
        REFERENCES security.accounts(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_completion_requests_user
        FOREIGN KEY (user_id)
        REFERENCES security.users(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_completion_requests_prompt
        FOREIGN KEY (prompt_id)
        REFERENCES assistant.prompts(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_completion_requests_prompt_version
        FOREIGN KEY (prompt_version_id)
        REFERENCES assistant.prompt_versions(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_completion_requests_account_created_at
    ON assistant.completion_requests(account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_completion_requests_requested_by_service
    ON assistant.completion_requests(requested_by_service);

-- ============================================================================
-- Table: completion_responses
-- Purpose: LLM responses, token usage, latency, and cost
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.completion_responses (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Relationships
    request_id UUID NOT NULL,
    account_id UUID NOT NULL,
    provider_id UUID NOT NULL,
    model_id    UUID NOT NULL,

    -- Response payload and metadata
    response      JSONB NOT NULL,
    finish_reason TEXT,

    -- Token usage
    prompt_tokens     INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens      INTEGER NOT NULL DEFAULT 0,

    -- Performance and cost
    latency_ms INTEGER,
    cost       NUMERIC(18,6) NOT NULL DEFAULT 0,
    escalate   BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Audit
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_completion_responses_request
        FOREIGN KEY (request_id)
        REFERENCES assistant.completion_requests(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_completion_responses_account
        FOREIGN KEY (account_id)
        REFERENCES security.accounts(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_completion_responses_provider
        FOREIGN KEY (provider_id)
        REFERENCES assistant.llm_providers(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_completion_responses_model
        FOREIGN KEY (model_id)
        REFERENCES assistant.llm_models(id)
        ON DELETE RESTRICT,

    -- One response per request (1:1 mapping)
    CONSTRAINT uq_completion_responses_request
        UNIQUE (request_id)
);

CREATE INDEX IF NOT EXISTS idx_completion_responses_account_completed_at
    ON assistant.completion_responses(account_id, completed_at DESC);

CREATE INDEX IF NOT EXISTS idx_completion_responses_provider_model
    ON assistant.completion_responses(provider_id, model_id);

-- ============================================================================
-- Table: usage_log
-- Purpose: Per-request usage and cost attribution
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.usage_log (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Ownership and attribution
    account_id UUID NOT NULL,
    request_id UUID,
    service    TEXT NOT NULL,
    user_id    UUID,
    feature    TEXT NOT NULL,
    purpose    TEXT NOT NULL,

    -- Token usage and cost
    prompt_tokens     INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens      INTEGER NOT NULL DEFAULT 0,
    cost              NUMERIC(18,6) NOT NULL DEFAULT 0,

    -- Model used
    model_id UUID,

    -- When usage was recorded
    logged_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_usage_log_account
        FOREIGN KEY (account_id)
        REFERENCES security.accounts(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_usage_log_request
        FOREIGN KEY (request_id)
        REFERENCES assistant.completion_requests(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_usage_log_user
        FOREIGN KEY (user_id)
        REFERENCES security.users(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_usage_log_model
        FOREIGN KEY (model_id)
        REFERENCES assistant.llm_models(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_usage_log_account_logged_at
    ON assistant.usage_log(account_id, logged_at DESC);

CREATE INDEX IF NOT EXISTS idx_usage_log_service_logged_at
    ON assistant.usage_log(service, logged_at DESC);

CREATE INDEX IF NOT EXISTS idx_usage_log_request
    ON assistant.usage_log(request_id);

-- ============================================================================
-- Table: usage_budgets
-- Purpose: Per-scope budgets and current usage for rate limiting and alerts
-- ============================================================================

CREATE TABLE IF NOT EXISTS assistant.usage_budgets (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    -- Scope: ACCOUNT, SERVICE, FEATURE
    scope   TEXT NOT NULL CHECK (scope IN ('ACCOUNT', 'SERVICE', 'FEATURE')),
    scope_id UUID NOT NULL,

    -- Period: DAILY, WEEKLY, MONTHLY
    period TEXT NOT NULL CHECK (period IN ('DAILY', 'WEEKLY', 'MONTHLY')),

    -- Budget configuration
    limit_amount   NUMERIC(18,6) NOT NULL,
    current_usage  NUMERIC(18,6) NOT NULL DEFAULT 0,
    alert_threshold NUMERIC(5,2) NOT NULL DEFAULT 0.80 CHECK (alert_threshold >= 0 AND alert_threshold <= 1),

    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT uq_usage_budgets_scope_scope_id_period
        UNIQUE (scope, scope_id, period)
);

CREATE INDEX IF NOT EXISTS idx_usage_budgets_scope_scope_id
    ON assistant.usage_budgets(scope, scope_id);

