# Brazilian Economic Indicators - Database Schema

**Type**: Database Model  
**Module**: financial_data  
**Status**: Draft

## Overview

Database model for **national economic indicators**, prepared for future multi-country expansion.
All labels use **SNAKE_CASE_UPPER**.

---

## Tables

### 1) `financial_data.indicator`

Represents the indicator itself (e.g., `IPCA`, `SELIC`, `GDP`).

**Columns**:
- `id` (UUID, PK, default `uuidv7()`)
- `country_code` (CHAR(2), NOT NULL) — ISO-3166-1 alpha-2
- `label` (TEXT, NOT NULL) — SNAKE_CASE_UPPER
- `issuer` (TEXT, NOT NULL) — e.g., IBGE, BCB, FGV
- `description` (TEXT, nullable)
- `frequency` (TEXT, NOT NULL) — daily, weekly, monthly, quarterly, annual, event
- `unit` (TEXT, NOT NULL) — %, INDEX, BRL, USD, etc.
- `is_active` (BOOLEAN, NOT NULL, default true)
- `created_at` (TIMESTAMPTZ, NOT NULL, default now())
- `updated_at` (TIMESTAMPTZ, NOT NULL, default now())

**Constraints**:
- Unique: `(country_code, label)`

---

### 2) `financial_data.indicator_variation`

Represents a measure of an indicator (e.g., `MONTHLY_CHANGE`, `YTD`, `ROLLING_12M`, `TARGET_RATE`).

**Columns**:
- `id` (UUID, PK, default `uuidv7()`)
- `indicator_id` (UUID, FK → `financial_data.indicator(id)`, ON DELETE CASCADE)
- `label` (TEXT, NOT NULL) — SNAKE_CASE_UPPER
- `unit` (TEXT, nullable) — override when different from indicator
- `is_default` (BOOLEAN, NOT NULL, default false)
- `incremental_months` (INT, nullable) — override for incremental window (SIDRA)
- `incremental_years` (INT, nullable) — override for incremental window (annual series)
- `incremental_days` (INT, nullable) — override for incremental window (daily series)
- `created_at` (TIMESTAMPTZ, NOT NULL, default now())
- `updated_at` (TIMESTAMPTZ, NOT NULL, default now())

**Constraints**:
- Unique: `(indicator_id, label)`

---

### 3) `financial_data.indicator_value`

Stores time-series values by indicator variation.

**Columns**:
- `indicator_variation_id` (UUID, FK → `financial_data.indicator_variation(id)`, ON DELETE CASCADE)
- `reference_date` (DATE, NOT NULL)
- `publication_date` (DATE, nullable)
- `value` (NUMERIC(18,6), NOT NULL)
- `source_meta` (JSONB, nullable) — evidence (URL, retrieval timestamp, params)
- `created_at` (TIMESTAMPTZ, NOT NULL, default now())
- `updated_at` (TIMESTAMPTZ, NOT NULL, default now())

**Constraints**:
- Primary Key: `(indicator_variation_id, reference_date)`

**Indexes**:
- `idx_indicator_value_variation_date` on `(indicator_variation_id, reference_date DESC)`

---

### 4) `financial_data.indicator_load_control`

Tracks async job execution per indicator variation.

**Columns**:
- `id` (UUID, PK, default `uuidv7()`)
- `indicator_variation_id` (UUID, FK → `financial_data.indicator_variation(id)`, ON DELETE CASCADE)
- `run_type` (TEXT, NOT NULL) — FULL, INCREMENTAL, BACKFILL
- `status` (TEXT, NOT NULL) — PENDING, RUNNING, SUCCESS, FAILED, SKIPPED
- `scheduled_at` (TIMESTAMPTZ, nullable)
- `started_at` (TIMESTAMPTZ, nullable)
- `finished_at` (TIMESTAMPTZ, nullable)
- `last_reference_date` (DATE, nullable)
- `last_publication_date` (DATE, nullable)
- `cursor` (JSONB, nullable)
- `error_message` (TEXT, nullable)
- `attempts` (INT, NOT NULL, default 0)
- `created_at` (TIMESTAMPTZ, NOT NULL, default now())
- `updated_at` (TIMESTAMPTZ, NOT NULL, default now())

**Indexes**:
- `idx_indicator_load_control_variation` on `(indicator_variation_id)`
- `idx_indicator_load_control_status` on `(status)`

---

## DDL (Migration-Friendly)

```sql
-- Schema bootstrap
CREATE SCHEMA IF NOT EXISTS financial_data;
SET search_path TO financial_data, public;

-- Indicator
CREATE TABLE IF NOT EXISTS financial_data.indicator (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    country_code CHAR(2) NOT NULL,
    label TEXT NOT NULL,
    issuer TEXT NOT NULL,
    description TEXT,
    frequency TEXT NOT NULL,
    unit TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_indicator_country_label UNIQUE (country_code, label)
);

-- Indicator variation
CREATE TABLE IF NOT EXISTS financial_data.indicator_variation (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    indicator_id UUID NOT NULL REFERENCES financial_data.indicator(id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    unit TEXT,
    is_default BOOLEAN NOT NULL DEFAULT false,
    incremental_months INT,
    incremental_years INT,
    incremental_days INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_indicator_variation UNIQUE (indicator_id, label)
);

-- Indicator values
CREATE TABLE IF NOT EXISTS financial_data.indicator_value (
    indicator_variation_id UUID NOT NULL REFERENCES financial_data.indicator_variation(id) ON DELETE CASCADE,
    reference_date DATE NOT NULL,
    publication_date DATE,
    value NUMERIC(18,6) NOT NULL,
    source_meta JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_indicator_value PRIMARY KEY (indicator_variation_id, reference_date)
);

CREATE INDEX IF NOT EXISTS idx_indicator_value_variation_date
    ON financial_data.indicator_value (indicator_variation_id, reference_date DESC);

-- Indicator load control
CREATE TABLE IF NOT EXISTS financial_data.indicator_load_control (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    indicator_variation_id UUID NOT NULL REFERENCES financial_data.indicator_variation(id) ON DELETE CASCADE,
    run_type TEXT NOT NULL,
    status TEXT NOT NULL,
    scheduled_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    last_reference_date DATE,
    last_publication_date DATE,
    cursor JSONB,
    error_message TEXT,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_indicator_load_control_variation
    ON financial_data.indicator_load_control (indicator_variation_id);

CREATE INDEX IF NOT EXISTS idx_indicator_load_control_status
    ON financial_data.indicator_load_control (status);
```

---

## Seed Data (Initial Indicators + Variations)

```sql
SET search_path TO financial_data, public;

-- Indicators (Brazil)
INSERT INTO financial_data.indicator
    (country_code, label, issuer, description, frequency, unit)
VALUES
    ('BR', 'IPCA', 'IBGE', 'Indice Nacional de Precos ao Consumidor Amplo', 'monthly', '%'),
    ('BR', 'IPCA_15', 'IBGE', 'Previa do IPCA', 'monthly', '%'),
    ('BR', 'INPC', 'IBGE', 'Indice Nacional de Precos ao Consumidor', 'monthly', '%'),
    ('BR', 'SELIC', 'BCB', 'Taxa Selic', 'daily', '%'),
    ('BR', 'GDP', 'IBGE', 'Produto Interno Bruto - valores correntes', 'annual', 'BRL'),
    ('BR', 'UNEMPLOYMENT', 'IBGE', 'Taxa de desocupacao (PNAD Continua)', 'monthly', '%'),
    ('BR', 'AVERAGE_INCOME', 'IBGE', 'Renda media do trabalho (PNAD Continua)', 'monthly', 'BRL')
ON CONFLICT (country_code, label) DO NOTHING;

-- Variations: IPCA
INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA'
ON CONFLICT (indicator_id, label) DO NOTHING;

-- Variations: IPCA-15
INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA_15'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA_15'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'IPCA_15'
ON CONFLICT (indicator_id, label) DO NOTHING;

-- Variations: INPC
INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'MONTHLY_CHANGE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'INPC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'YTD', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'INPC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'ROLLING_12M', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'INPC'
ON CONFLICT (indicator_id, label) DO NOTHING;

-- Variations: SELIC
INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'TARGET_RATE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'SELIC'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'EFFECTIVE_RATE', '%'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'SELIC'
ON CONFLICT (indicator_id, label) DO NOTHING;

-- Variations: GDP
INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'VALUE', 'BRL', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'GDP'
ON CONFLICT (indicator_id, label) DO NOTHING;

-- Variations: UNEMPLOYMENT
INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'RATE', '%', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'UNEMPLOYMENT'
ON CONFLICT (indicator_id, label) DO NOTHING;

-- Variations: AVERAGE_INCOME
INSERT INTO financial_data.indicator_variation (indicator_id, label, unit, is_default)
SELECT id, 'HABITUAL', 'BRL', true
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'AVERAGE_INCOME'
ON CONFLICT (indicator_id, label) DO NOTHING;

INSERT INTO financial_data.indicator_variation (indicator_id, label, unit)
SELECT id, 'EFFECTIVE', 'BRL'
FROM financial_data.indicator WHERE country_code = 'BR' AND label = 'AVERAGE_INCOME'
ON CONFLICT (indicator_id, label) DO NOTHING;
```

---

## source_meta Example

```json
{
  "source": "SIDRA",
  "url": "https://apisidra.ibge.gov.br/values/t/1737/n1/all/v/63/p/last%20120",
  "retrieved_at": "2026-01-26T12:34:56Z",
  "raw_period": "202512"
}
```
