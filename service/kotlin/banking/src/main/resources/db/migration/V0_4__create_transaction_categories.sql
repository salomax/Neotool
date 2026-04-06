-- ─── Transaction categories ──────────────────────────────────────────────────
-- Two-level hierarchy: root categories (parent_id IS NULL) + subcategories.
-- System categories (is_system = true, account_id IS NULL) are seeded here and
-- cannot be deleted. User-defined categories (account_id != NULL) are created
-- at runtime and soft-deleted.
--
-- Category taxonomy is aligned with Pluggy's standard taxonomy, translated and
-- adapted for the Brazilian market. The pluggy_category_id column is reserved
-- for Phase 5 (Pluggy integration) and is left NULL in the seed.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.transaction_categories (
    id                 UUID    PRIMARY KEY DEFAULT uuidv7(),
    account_id         UUID,   -- NULL = system/global
    parent_id          UUID    REFERENCES banking.transaction_categories(id),
    name               TEXT    NOT NULL,
    applicable_to      TEXT    NOT NULL DEFAULT 'ANY'
                               CHECK (applicable_to IN ('INCOME', 'EXPENSE', 'TRANSFER', 'ANY')),
    icon               TEXT,        -- emoji or icon key
    color              TEXT,        -- hex color, e.g. '#4CAF50'
    pluggy_category_id TEXT,        -- Pluggy category ID (Phase 5)
    is_system          BOOLEAN NOT NULL DEFAULT false,
    sort_order         INT     NOT NULL DEFAULT 0,
    deleted_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_txn_categories_account ON banking.transaction_categories(account_id)
    WHERE account_id IS NOT NULL;
CREATE INDEX idx_txn_categories_parent  ON banking.transaction_categories(parent_id)
    WHERE parent_id IS NOT NULL;
CREATE INDEX idx_txn_categories_system  ON banking.transaction_categories(is_system, applicable_to)
    WHERE deleted_at IS NULL;

-- ─── Seed: root categories ───────────────────────────────────────────────────

INSERT INTO banking.transaction_categories
    (name, applicable_to, icon, color, is_system, sort_order)
VALUES
-- INCOME -----------------------------------------------------------------------
    ('Salário',                'INCOME',   '💰', '#2E7D32', true, 10),
    ('Renda Extra',            'INCOME',   '💼', '#388E3C', true, 20),
    ('Aposentadoria / Pensão', 'INCOME',   '🏛️', '#43A047', true, 30),
    ('Investimentos',          'INCOME',   '📈', '#00897B', true, 40),
    ('Transferência recebida', 'INCOME',   '↙️', '#26A69A', true, 50),
-- EXPENSE ----------------------------------------------------------------------
    ('Alimentação',            'EXPENSE',  '🍽️', '#F57C00', true, 100),
    ('Moradia',                'EXPENSE',  '🏠', '#5D4037', true, 110),
    ('Transporte',             'EXPENSE',  '🚗', '#1565C0', true, 120),
    ('Saúde',                  'EXPENSE',  '❤️', '#C62828', true, 130),
    ('Educação',               'EXPENSE',  '📚', '#6A1B9A', true, 140),
    ('Lazer e Entretenimento', 'EXPENSE',  '🎭', '#AD1457', true, 150),
    ('Compras',                'EXPENSE',  '🛍️', '#E91E63', true, 160),
    ('Serviços Digitais',      'EXPENSE',  '💻', '#0277BD', true, 170),
    ('Telecomunicações',       'EXPENSE',  '📱', '#00838F', true, 180),
    ('Seguros',                'EXPENSE',  '🛡️', '#558B2F', true, 190),
    ('Impostos',               'EXPENSE',  '🏛️', '#37474F', true, 200),
    ('Tarifas Bancárias',      'EXPENSE',  '🏦', '#455A64', true, 210),
    ('Empréstimos',            'EXPENSE',  '💸', '#BF360C', true, 220),
    ('Doações',                'EXPENSE',  '🤝', '#8E24AA', true, 230),
-- TRANSFER ---------------------------------------------------------------------
    ('Transferência enviada',  'TRANSFER', '↗️', '#1976D2', true, 1000),
    ('Investimento aplicado',  'TRANSFER', '💹', '#00796B', true, 1010),
-- ANY --------------------------------------------------------------------------
    ('Outros',                 'ANY',      '❓', '#9E9E9E', true, 9999);

-- ─── Seed: subcategories ─────────────────────────────────────────────────────

-- INCOME > Renda Extra
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'INCOME', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Freela',     10),
             ('Dividendos', 20),
             ('Cashback',   30),
             ('Outros',     40)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Renda Extra'
  AND is_system = true AND account_id IS NULL;

-- INCOME > Investimentos
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'INCOME', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Rendimento', 10),
             ('Resgate',    20)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Investimentos'
  AND is_system = true AND account_id IS NULL;

-- INCOME > Transferência recebida (includes "Entre contas próprias" to mirror TRANSFER > Transferência enviada)
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'INCOME', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('PIX',                   10),
             ('TED',                   20),
             ('Entre contas próprias', 25),
             ('Outros',                30)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Transferência recebida'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Alimentação
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Supermercado', 10),
             ('Restaurante',  20),
             ('Delivery',     30),
             ('Lanchonete',   40)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Alimentação'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Moradia
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Aluguel',    10),
             ('Condomínio', 20),
             ('IPTU',       30),
             ('Água',       40),
             ('Luz',        50),
             ('Gás',        60),
             ('Faxina',     70)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Moradia'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Transporte
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Combustível',        10),
             ('Pedágio',            20),
             ('Estacionamento',     30),
             ('Uber / Taxi',        40),
             ('Transporte Público', 50),
             ('IPVA',               60),
             ('Revisão',            70),
             ('Manutenção',         80)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Transporte'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Saúde
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Farmácia',         10),
             ('Médico / Consulta', 20),
             ('Dentista',          30),
             ('Plano de Saúde',    40),
             ('Academia',          50)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Saúde'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Educação
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Mensalidade',    10),
             ('Curso Online',   20),
             ('Material Escolar', 30)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Educação'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Lazer e Entretenimento
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Cinema / Teatro', 10),
             ('Streaming',        20),
             ('Games',            30),
             ('Viagem',           40)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Lazer e Entretenimento'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Compras
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Roupas',       10),
             ('Eletrônicos',  20),
             ('Casa',         30),
             ('Pet',          40)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Compras'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Serviços Digitais
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Streaming Vídeo',  10),
             ('Streaming Música', 20),
             ('Software / Apps',  30)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Serviços Digitais'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Telecomunicações
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Internet',   10),
             ('Celular',    20),
             ('TV a Cabo',  30)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Telecomunicações'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Seguros
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Seguro Auto',       10),
             ('Seguro Vida',       20),
             ('Seguro Residência', 30),
             ('Plano de Saúde',    40)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Seguros'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Impostos
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('IR',       10),
             ('IOF',      20),
             ('IPVA',     30),
             ('IPTU',     40),
             ('DAS (MEI)', 50)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Impostos'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Tarifas Bancárias
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Tarifa Conta',       10),
             ('Anuidade Cartão',    20),
             ('Tarifa Transferência', 30)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Tarifas Bancárias'
  AND is_system = true AND account_id IS NULL;

-- EXPENSE > Empréstimos
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'EXPENSE', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Empréstimo Pessoal',    10),
             ('Financiamento Imóvel',  20),
             ('Financiamento Veículo', 30)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Empréstimos'
  AND is_system = true AND account_id IS NULL;

-- TRANSFER > Transferência enviada
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'TRANSFER', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('PIX',                 10),
             ('TED',                 20),
             ('Entre contas próprias', 30),
             ('Pagamento de fatura', 40)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Transferência enviada'
  AND is_system = true AND account_id IS NULL;

-- TRANSFER > Investimento aplicado
INSERT INTO banking.transaction_categories (parent_id, name, applicable_to, is_system, sort_order)
SELECT id, sub.name, 'TRANSFER', true, sub.ord
FROM banking.transaction_categories,
     (VALUES ('Renda Fixa',  10),
             ('Fundos',      20),
             ('Ações',       30),
             ('Previdência', 40),
             ('Criptomoeda', 50)) AS sub(name, ord)
WHERE banking.transaction_categories.name = 'Investimento aplicado'
  AND is_system = true AND account_id IS NULL;

-- V0_24__update_category_icons_to_mui.sql
-- Update icon column for system seed categories from emoji to MUI icon names.
-- Uses name-based matching; applies only to system categories (is_system = true).

SET search_path TO banking, public;

UPDATE transaction_categories SET icon = 'payments'     WHERE name = 'Salário'                AND is_system = true;
UPDATE transaction_categories SET icon = 'work'         WHERE name = 'Renda Extra'            AND is_system = true;
UPDATE transaction_categories SET icon = 'elderly'      WHERE name = 'Aposentadoria / Pensão'          AND is_system = true;
UPDATE transaction_categories SET icon = 'trending_up'  WHERE name = 'Investimentos'          AND is_system = true;
UPDATE transaction_categories SET icon = 'call_received' WHERE name = 'Transferência recebida' AND is_system = true;
UPDATE transaction_categories SET icon = 'restaurant'   WHERE name = 'Alimentação'            AND is_system = true;
UPDATE transaction_categories SET icon = 'shopping_cart' WHERE name = 'Supermercado'          AND is_system = true;
UPDATE transaction_categories SET icon = 'ramen_dining' WHERE name = 'Restaurante'            AND is_system = true;
UPDATE transaction_categories SET icon = 'delivery_dining' WHERE name = 'Delivery'            AND is_system = true;
UPDATE transaction_categories SET icon = 'local_cafe'   WHERE name = 'Lanchonete'             AND is_system = true;
UPDATE transaction_categories SET icon = 'home'         WHERE name = 'Moradia'                AND is_system = true;
UPDATE transaction_categories SET icon = 'cleaning_services' WHERE name = 'Faxina'            AND is_system = true;
UPDATE transaction_categories SET icon = 'key'          WHERE name = 'Aluguel'                AND is_system = true;
UPDATE transaction_categories SET icon = 'apartment'    WHERE name = 'Condomínio'             AND is_system = true;
UPDATE transaction_categories SET icon = 'bolt'         WHERE name = 'Conta de luz'           AND is_system = true;
UPDATE transaction_categories SET icon = 'water_drop'   WHERE name = 'Água'                   AND is_system = true;
UPDATE transaction_categories SET icon = 'local_fire_department' WHERE name = 'Gás'           AND is_system = true;
UPDATE transaction_categories SET icon = 'directions_car' WHERE name = 'Transporte'           AND is_system = true;
UPDATE transaction_categories SET icon = 'local_gas_station' WHERE name = 'Combustível'       AND is_system = true;
UPDATE transaction_categories SET icon = 'directions_bus' WHERE name = 'Transporte público'   AND is_system = true;
UPDATE transaction_categories SET icon = 'smartphone'   WHERE name = 'Aplicativos (Uber)'     AND is_system = true;
UPDATE transaction_categories SET icon = 'local_hospital' WHERE name = 'Saúde'                AND is_system = true;
UPDATE transaction_categories SET icon = 'medication'   WHERE name = 'Farmácia'               AND is_system = true;
UPDATE transaction_categories SET icon = 'medical_services' WHERE name = 'Médico'             AND is_system = true;
UPDATE transaction_categories SET icon = 'health_and_safety' WHERE name = 'Plano de saúde'    AND is_system = true;
UPDATE transaction_categories SET icon = 'school'       WHERE name = 'Educação'               AND is_system = true;
UPDATE transaction_categories SET icon = 'account_balance' WHERE name = 'Mensalidade'         AND is_system = true;
UPDATE transaction_categories SET icon = 'menu_book'    WHERE name = 'Cursos'                 AND is_system = true;
UPDATE transaction_categories SET icon = 'edit'         WHERE name = 'Material escolar'       AND is_system = true;
UPDATE transaction_categories SET icon = 'celebration'  WHERE name = 'Lazer e Entretenimento'                  AND is_system = true;
UPDATE transaction_categories SET icon = 'movie'        WHERE name = 'Cinema / Teatro'         AND is_system = true;
UPDATE transaction_categories SET icon = 'sports_esports' WHERE name = 'Games'                AND is_system = true;
UPDATE transaction_categories SET icon = 'fitness_center' WHERE name = 'Academia'             AND is_system = true;
UPDATE transaction_categories SET icon = 'flight'       WHERE name = 'Viagem'                 AND is_system = true;
UPDATE transaction_categories SET icon = 'flight'       WHERE name = 'Viagens'                AND is_system = true;
UPDATE transaction_categories SET icon = 'shopping_bag' WHERE name = 'Compras'                AND is_system = true;
UPDATE transaction_categories SET icon = 'checkroom'    WHERE name = 'Roupas'                 AND is_system = true;
UPDATE transaction_categories SET icon = 'devices'      WHERE name = 'Eletrônicos'            AND is_system = true;
UPDATE transaction_categories SET icon = 'chair'        WHERE name = 'Casa / Decoração'       AND is_system = true;
UPDATE transaction_categories SET icon = 'live_tv'      WHERE name = 'Serviços Digitais'      AND is_system = true;
UPDATE transaction_categories SET icon = 'headphones'   WHERE name = 'Streaming'              AND is_system = true;
UPDATE transaction_categories SET icon = 'autorenew'    WHERE name = 'Assinaturas'            AND is_system = true;
UPDATE transaction_categories SET icon = 'cell_tower'   WHERE name = 'Telecomunicações'       AND is_system = true;
UPDATE transaction_categories SET icon = 'wifi'         WHERE name = 'Internet'               AND is_system = true;
UPDATE transaction_categories SET icon = 'phone_iphone' WHERE name = 'Celular'                AND is_system = true;
UPDATE transaction_categories SET icon = 'shield'       WHERE name = 'Seguros'                AND is_system = true;
UPDATE transaction_categories SET icon = 'account_balance' WHERE name = 'Impostos'            AND is_system = true;
UPDATE transaction_categories SET icon = 'receipt_long' WHERE name = 'IPTU / IPVA'            AND is_system = true;
UPDATE transaction_categories SET icon = 'bar_chart'    WHERE name = 'IR'                     AND is_system = true;
UPDATE transaction_categories SET icon = 'account_balance_wallet' WHERE name = 'Tarifas Bancárias' AND is_system = true;
UPDATE transaction_categories SET icon = 'credit_card'  WHERE name = 'Empréstimos'            AND is_system = true;
UPDATE transaction_categories SET icon = 'favorite'     WHERE name = 'Doações'                AND is_system = true;
UPDATE transaction_categories SET icon = 'call_made'    WHERE name = 'Transferência enviada'  AND is_system = true;
UPDATE transaction_categories SET icon = 'savings'      WHERE name = 'Investimento aplicado'  AND is_system = true;
UPDATE transaction_categories SET icon = 'help_outline' WHERE name = 'Outros'                 AND is_system = true;

-- System child categories without icon inherit the icon from their parent.
UPDATE transaction_categories child
SET icon = parent.icon, color = parent.color
FROM transaction_categories parent
WHERE child.parent_id = parent.id
  AND child.is_system = true
  AND parent.is_system = true
  AND child.icon IS NULL
  AND parent.icon IS NOT NULL;

UPDATE transaction_categories child
SET color = parent.color
FROM transaction_categories parent
WHERE child.parent_id = parent.id
  AND child.is_system = true
  AND parent.is_system = true
  AND child.color IS NULL
  AND parent.color IS NOT NULL;
