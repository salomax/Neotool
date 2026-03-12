-- Auto-categorization rules: pattern -> category mapping.

SET search_path TO banking, public;

CREATE TABLE banking.category_rules (
  id              UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  -- Tenant account owning the rule. NULL = system/global rule.
  -- Intentionally not a foreign key to avoid cross-service coupling.
  account_id      UUID,
  category_id     UUID          NOT NULL REFERENCES banking.transaction_categories(id),
  pattern         TEXT          NOT NULL,
  pattern_type    TEXT          NOT NULL
    CHECK (pattern_type IN ('CONTAINS', 'STARTS_WITH', 'EXACT', 'REGEX')),
  applicable_to   TEXT          NOT NULL
    CHECK (applicable_to IN ('INCOME', 'EXPENSE', 'TRANSFER', 'ANY')),
  priority        INT           NOT NULL DEFAULT 100,
  is_system       BOOLEAN       NOT NULL DEFAULT false,
  is_implicit     BOOLEAN       NOT NULL DEFAULT false,
  match_count     INT           NOT NULL DEFAULT 0,
  deleted_at      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_category_rules_account_id ON banking.category_rules (account_id)
  WHERE deleted_at IS NULL;
CREATE INDEX idx_category_rules_system ON banking.category_rules (is_system)
  WHERE is_system = true AND deleted_at IS NULL;

-- Seed common Brazilian system rules (is_system = true, account_id = NULL).
-- Each INSERT is conditional on the category existing, so tests or partial
-- seeds that omit some system categories won't fail this migration.
INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'netflix', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Streaming' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'spotify', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Streaming' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'prime video', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Streaming' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'ifood', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Delivery' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'rappi', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Delivery' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'uber', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Aplicativos (Uber)' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, '99pop', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Aplicativos (Uber)' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'posto', 'CONTAINS', 'EXPENSE', 20, true
FROM banking.transaction_categories
WHERE name = 'Combustível' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'shell', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Combustível' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'ipiranga', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Combustível' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'drogasil', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Farmácia' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'droga raia', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Farmácia' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'ultrafarma', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Farmácia' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'carrefour', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Supermercado' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'extra supermercado', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Supermercado' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'pao de acucar', 'CONTAINS', 'EXPENSE', 10, true
FROM banking.transaction_categories
WHERE name = 'Supermercado' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'salario', 'CONTAINS', 'INCOME', 10, true
FROM banking.transaction_categories
WHERE name = 'Salário' AND is_system = true
LIMIT 1;

INSERT INTO banking.category_rules (account_id, category_id, pattern, pattern_type, applicable_to, priority, is_system)
SELECT NULL, id, 'folha pagamento', 'CONTAINS', 'INCOME', 10, true
FROM banking.transaction_categories
WHERE name = 'Salário' AND is_system = true
LIMIT 1;
