-- ─── Issuer category ──────────────────────────────────────────────────────────
-- Adds a category column to banking.issuers to enable context-based filtering.
-- Classifies existing institutions and inserts brokerages + crypto exchanges.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Add column (nullable first so UPDATE can run)
ALTER TABLE banking.issuers
    ADD COLUMN category TEXT
        CHECK (category IN ('BANK', 'BROKERAGE', 'CRYPTO_EXCHANGE', 'PAYMENT', 'BENEFIT'));

-- 2. Classify existing institutions

-- Banks (full banking license, COMPE-registered)
UPDATE banking.issuers SET category = 'BANK' WHERE name IN (
    'Nubank', 'Mercado Pago', 'Banco Inter', 'PicPay', 'Banco do Brasil',
    'Caixa Econômica Federal', 'Agibank', 'Stone', 'C6 Bank', 'Santander',
    'Itaú', 'BRB', 'Bradesco', 'PagBank', 'BTG Pactual',
    'Safra', 'Citibank Brasil', 'Banco Votorantim (BV)',
    'Neon', 'Next', 'Banco Original', 'Will Bank', 'InfinitePay',
    'Sicoob', 'Sicredi', 'Unicred',
    'Nomad', 'Wise'
);

-- Payment-focused (no full banking license)
UPDATE banking.issuers SET category = 'PAYMENT' WHERE name IN (
    '99Pay'
);

-- Brokerages (investment-focused, CVM/ANBIMA regulated)
UPDATE banking.issuers SET category = 'BROKERAGE' WHERE name IN (
    'XP Investimentos', 'Rico', 'Clear'
);

-- Benefit / voucher issuers
UPDATE banking.issuers SET category = 'BENEFIT' WHERE name IN (
    'VR Benefícios', 'Alelo', 'Sodexo', 'Ticket', 'Caju', 'Flash Benefícios', 'Swile'
);

-- 3. Make not null with default BANK
ALTER TABLE banking.issuers
    ALTER COLUMN category SET NOT NULL,
    ALTER COLUMN category SET DEFAULT 'BANK';

-- 4. Index for filtered queries
CREATE INDEX idx_issuers_category ON banking.issuers(category) WHERE is_active = TRUE;

-- ─── New brokerages ───────────────────────────────────────────────────────────

INSERT INTO banking.issuers (name, domain, compe_code, ispb, category) VALUES
('NuInvest',              'nuinvest.com.br',            '140', '62169875', 'BROKERAGE'),
('Toro Investimentos',    'toroinvestimentos.com.br',   NULL,  NULL,       'BROKERAGE'),
('Warren',                'warren.com.br',              NULL,  NULL,       'BROKERAGE'),
('Órama',                 'orama.com.br',               NULL,  NULL,       'BROKERAGE'),
('Genial Investimentos',  'genialinvestimentos.com.br', NULL,  NULL,       'BROKERAGE'),
('Guide Investimentos',   'guideinvestimentos.com.br',  NULL,  NULL,       'BROKERAGE'),
('Modal Mais',            'modal.com.br',               '394', '04257795', 'BROKERAGE'),
('Ativa Investimentos',   'ativainvestimentos.com.br',  '188', '33775974', 'BROKERAGE'),
('Mirae Asset',           'miraeasset.com.br',          '447', NULL,       'BROKERAGE'),
('Avenue',                'avenue.us',                  NULL,  NULL,       'BROKERAGE');

-- ─── Crypto exchanges ─────────────────────────────────────────────────────────

INSERT INTO banking.issuers (name, domain, compe_code, ispb, category) VALUES
('Mercado Bitcoin',       'mercadobitcoin.com.br',      NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Foxbit',                'foxbit.com.br',              NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Novadax',               'novadax.com',                NULL,  NULL,       'CRYPTO_EXCHANGE'),
('BitPreço',              'bitpreco.com.br',            NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Ripio',                 'ripio.com',                  NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Binance',               'binance.com',                NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Coinbase',              'coinbase.com',               NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Kraken',                'kraken.com',                 NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Bybit',                 'bybit.com',                  NULL,  NULL,       'CRYPTO_EXCHANGE'),
('OKX',                   'okx.com',                    NULL,  NULL,       'CRYPTO_EXCHANGE');
