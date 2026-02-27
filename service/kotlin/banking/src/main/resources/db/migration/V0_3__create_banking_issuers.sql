-- ─── Issuers (financial institutions) ───────────────────────────────────────
-- Seed data: Tier 1 curated list of Brazilian institutions.
-- logo_key is NULL until the hydration job runs (fetches from logo.dev and
-- uploads to the public assets bucket).

CREATE TABLE banking.issuers (
    id           UUID    PRIMARY KEY DEFAULT uuidv7(),
    name         TEXT    NOT NULL,
    domain       TEXT,           -- used by logo.dev hydration job
    compe_code   CHAR(3),        -- 3-digit COMPE/STR code (Brazilian central bank)
    ispb         CHAR(8),        -- 8-digit ISPB code (used in PIX / SPB)
    logo_key     TEXT,           -- asset key after hydration: 'issuers/<id>.png'
    country_code CHAR(2)  NOT NULL DEFAULT 'BR',
    is_active    BOOLEAN  NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_banking_issuers_compe ON banking.issuers(compe_code) WHERE compe_code IS NOT NULL;
CREATE INDEX idx_banking_issuers_ispb  ON banking.issuers(ispb)       WHERE ispb IS NOT NULL;

-- ─── Add optional FK to financial_accounts ───────────────────────────────────
-- issuer_id is denormalized at account creation (service copies name/logo_key
-- into the inline fields). The FK is nullable: NULL = custom/unlisted issuer.

ALTER TABLE banking.financial_accounts
    ADD COLUMN issuer_id UUID REFERENCES banking.issuers(id);

-- ─── Seed: Top banks by customer acquisition (2025) ─────────────────────────

INSERT INTO banking.issuers (name, domain, compe_code, ispb) VALUES

-- ── Tier 1: Top 15 por aquisição de clientes (2025) ─────────────────────────
('Nubank',                  'nubank.com.br',         '260', '18236120'),
('Mercado Pago',            'mercadopago.com.br',    '323', '10573521'),
('Banco Inter',             'inter.co',              '077', '00416968'),
('PicPay',                  'picpay.com',            '380', '22896431'),
('99Pay',                   '99app.com.br',          NULL,  NULL),
('Banco do Brasil',         'bb.com.br',             '001', '00000000'),
('Caixa Econômica Federal', 'caixa.gov.br',          '104', '36122022'),
('Agibank',                 'agibank.com.br',        '121', '10664513'),
('Stone',                   'stone.com.br',          '197', '16501555'),
('C6 Bank',                 'c6bank.com.br',         '336', '31872495'),
('Santander',               'santander.com.br',      '033', '90400888'),
('Itaú',                    'itau.com.br',           '341', '60701190'),
('BRB',                     'brb.com.br',            '070', '00000208'),
('Bradesco',                'bradesco.com.br',       '237', '60746948'),
('PagBank',                 'pagbank.com.br',        '290', '08561701'),

-- ── Tier 2: Bancos tradicionais relevantes ───────────────────────────────────
('BTG Pactual',             'btgpactual.com',        '208', '30306294'),
('Safra',                   'safra.com.br',          '422', '58160789'),
('Citibank Brasil',         'citibank.com.br',       '745', '33479023'),
('Banco Votorantim (BV)',   'bv.com.br',             '655', '59588111'),

-- ── Tier 3: Fintechs e digitais ──────────────────────────────────────────────
('Neon',                    'neon.com.br',           '536', '20855875'),
('Next',                    'next.me',               '237', '60746948'),
('Banco Original',          'original.com.br',       '212', '92894922'),
('Will Bank',               'willbank.com.br',       '473', '27652684'),
('Nomad',                   'nomadglobal.com',       NULL,  NULL),
('Wise',                    'wise.com',              NULL,  NULL),
('InfinitePay',             'infinitepay.io',        '360', '08357240'),

-- ── Tier 4: Cooperativas ─────────────────────────────────────────────────────
('Sicoob',                  'sicoob.com.br',         '756', '02038232'),
('Sicredi',                 'sicredi.com.br',        '748', '01181521'),
('Unicred',                 'unicred.com.br',        '136', '00315557'),

-- ── Tier 5: Investimentos ────────────────────────────────────────────────────
('XP Investimentos',        'xp.com.br',             '102', '02332886'),
('Rico',                    'rico.com.vc',           '127', '97684813'),
('Clear',                   'clear.com.br',          '388', '51430792'),

-- ── Tier 6: Benefícios / Vale ────────────────────────────────────────────────
('VR Benefícios',           'vr.com.br',             NULL,  NULL),
('Alelo',                   'alelo.com.br',          NULL,  NULL),
('Sodexo',                  'sodexo.com.br',         NULL,  NULL),
('Ticket',                  'ticket.com.br',         NULL,  NULL),
('Caju',                    'caju.com.br',           NULL,  NULL),
('Flash Benefícios',        'flashapp.com.br',       NULL,  NULL),
('Swile',                   'swile.co',              NULL,  NULL);
