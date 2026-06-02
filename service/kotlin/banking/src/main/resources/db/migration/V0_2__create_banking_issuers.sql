-- Final issuers catalog shape.
-- Seed data lives in 11_seed_issuers.sqlfrag.

CREATE TABLE banking.issuers (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    name         TEXT NOT NULL,
    domain       TEXT,
    compe_code   CHAR(3),
    ispb         CHAR(8),
    logo_key     TEXT,
    country_code CHAR(2) NOT NULL DEFAULT 'BR',
    category     TEXT NOT NULL DEFAULT 'BANK'
        CHECK (category IN ('BANK', 'BROKERAGE', 'CRYPTO_EXCHANGE', 'PAYMENT', 'BENEFIT')),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_banking_issuers_compe
    ON banking.issuers(compe_code)
    WHERE compe_code IS NOT NULL;

CREATE INDEX idx_banking_issuers_ispb
    ON banking.issuers(ispb)
    WHERE ispb IS NOT NULL;

CREATE INDEX idx_issuers_category
    ON banking.issuers(category)
    WHERE is_active = TRUE;

-- Seed the curated issuer catalog.
-- The base table defaults category to BANK; category-specific updates and extra
-- inserts keep parity with the historical V0_3 + V0_27 chain.

INSERT INTO banking.issuers (name, domain, compe_code, ispb) VALUES
    ('Nubank',                  'nubank.com.br',         '260', '18236120'),
    ('Mercado Pago',            'mercadopago.com.br',    '323', '10573521'),
    ('Banco Inter',             'inter.co',              '077', '00416968'),
    ('PicPay',                  'picpay.com',            '380', '22896431'),
    ('99Pay',                   '99app.com',          '769', '24313102'),
    ('Banco do Brasil',         'bb.com.br',             '001', '00000000'),
    ('Caixa Econômica Federal', 'caixa.gov.br',          '104', '00360305'),
    ('Agibank',                 'agibank.com.br',        '121', '10664513'),
    ('Stone',                   'stone.com.br',          '197', '16501555'),
    ('C6 Bank',                 'c6bank.com.br',         '336', '31872495'),
    ('Santander',               'santander.com.br',      '033', '90400888'),
    ('Itaú',                    'itau.com.br',           '341', '60701190'),
    ('BRB',                     'brb.com.br',            '070', '00000208'),
    ('Bradesco',                'bradesco.com.br',       '237', '60746948'),
    ('PagBank',                 'pagbank.com.br',        '290', '08561701'),
    ('BTG Pactual',             'btgpactual.com',        '208', '30306294'),
    ('Safra',                   'safra.com.br',          '422', '58160789'),
    ('Citibank Brasil',         'citibank.com.br',       '745', '33479023'),
    ('Banco BV',                'bv.com.br',             '655', '59588111'),
    ('Neon',                    'neon.com.br',           '536', '20855875'),
    ('Next',                    'next.me',               '237', '60746948'),
    ('Banco Original',          'original.com.br',       '212', '92894922'),
    ('Will Bank',               'willbank.com.br',       '280', '23862762'),
    ('Nomad',                   'nomadglobal.com',       NULL,  NULL),
    ('Wise',                    'wise.com',              NULL,  '40571694'),
    ('InfinitePay',             'infinitepay.io',        '360', '08357240'),
    ('Sicoob',                  'sicoob.com.br',         '756', '02038232'),
    ('Sicredi',                 'sicredi.com.br',        '748', '01181521'),
    ('Unicred',                 'unicred.com.br',        '136', '00315557'),
    ('XP Investimentos',        'xpi.com.br',            '102', '02332886'),
    ('Banco XP',                'xpi.com.br',            '348', '33264668'),
    ('Rico',                    'rico.com.vc',           '127', '97684813'),
    ('Clear',                   'clear.com.br',          '388', '51430792'),
    ('VR Benefícios',           'vr.com.br',             NULL,  '02812218'),
    ('Alelo',                   'alelo.com.br',          NULL,  '04740876'),
    ('Sodexo',                  'sodexo.com.br',         NULL,  '04346339'),
    ('Ticket',                  'ticket.com.br',         NULL,  '13347116'),
    ('Caju',                    'caju.com.br',           NULL,  '34065479'),
    ('Flash Benefícios',        'flashapp.com.br',       NULL,  '32014165'),
    ('Swile',                   'swile.com.br',          NULL,  '30537096');

UPDATE banking.issuers SET category = 'PAYMENT' WHERE name IN ('99Pay');

UPDATE banking.issuers SET category = 'BROKERAGE' WHERE name IN (
    'XP Investimentos', 'Rico', 'Clear'
);

UPDATE banking.issuers SET category = 'BENEFIT' WHERE name IN (
    'VR Benefícios', 'Alelo', 'Sodexo', 'Ticket', 'Caju', 'Flash Benefícios', 'Swile'
);

INSERT INTO banking.issuers (name, domain, compe_code, ispb, category) VALUES
    ('NuInvest',             'nuinvest.com.br',            '140', '62169875', 'BROKERAGE'),
    ('Toro Investimentos',   'toroinvestimentos.com.br',   NULL,  NULL,       'BROKERAGE'),
    ('Warren',               'warren.com.br',              NULL,  NULL,       'BROKERAGE'),
    ('Órama',                'orama.com.br',               NULL,  NULL,       'BROKERAGE'),
    ('Genial Investimentos', 'genialinvestimentos.com.br', NULL,  NULL,       'BROKERAGE'),
    ('Guide Investimentos',  'guideinvestimentos.com.br',  NULL,  NULL,       'BROKERAGE'),
    ('Modal Mais',           'modal.com.br',               '394', '04257795', 'BROKERAGE'),
    ('Ativa Investimentos',  'ativainvestimentos.com.br',  '188', '33775974', 'BROKERAGE'),
    ('Mirae Asset',          'miraeasset.com.br',          '447', NULL,       'BROKERAGE'),
    ('Avenue',               'avenue.us',                  NULL,  NULL,       'BROKERAGE'),
    ('Mercado Bitcoin',      'mercadobitcoin.com.br',      NULL,  NULL,       'CRYPTO_EXCHANGE'),
    ('Foxbit',               'foxbit.com.br',              NULL,  NULL,       'CRYPTO_EXCHANGE'),
    ('Novadax',              'novadax.com',                NULL,  NULL,       'CRYPTO_EXCHANGE'),
    ('BitPreço',             'bitpreco.com.br',            NULL,  NULL,       'CRYPTO_EXCHANGE'),
    ('Ripio',                'ripio.com',                  NULL,  NULL,       'CRYPTO_EXCHANGE'),
    ('Binance',              'binance.com',                NULL,  NULL,       'CRYPTO_EXCHANGE'),
    ('Coinbase',             'coinbase.com',               NULL,  NULL,       'CRYPTO_EXCHANGE'),
    ('Kraken',               'kraken.com',                 NULL,  NULL,       'CRYPTO_EXCHANGE'),
    ('Bybit',                'bybit.com',                  NULL,  NULL,       'CRYPTO_EXCHANGE'),
    ('OKX',                  'okx.com',                    NULL,  NULL,       'CRYPTO_EXCHANGE');
