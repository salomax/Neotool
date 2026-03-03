-- Migration: Seed popular institutions for discovery dashboard
-- Version: V1_12
-- Description: Static list of the 20 most popular Brazilian financial institutions
--              for the discovery carousel. Updated manually, once a year.
--              Rankings are editorial, not derived from financial metrics.

SET search_path TO financial_data, public;

-- ============================================================================
-- Table: popular_institution
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.popular_institution (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    rank              SMALLINT     NOT NULL UNIQUE,
    parent_cnpj       TEXT         NOT NULL,
    display_name      TEXT         NOT NULL CHECK (length(display_name) <= 100),
    short_description TEXT         NOT NULL CHECK (length(short_description) <= 200),
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_popular_institution_rank
    ON financial_data.popular_institution(rank) WHERE active = true;

COMMENT ON TABLE financial_data.popular_institution IS
    'Static editorial list of the most popular Brazilian financial institutions '
    'shown in the discovery carousel. Updated manually once a year via migration.';

-- ============================================================================
-- Seed data (2025 – editorial ranking)
-- parent_cnpj values refer to the root CNPJ in the financial_institution table.
-- Validate the exact format against financial_institution.parent_cnpj before
-- applying to production (may be 8-digit root or full format XX.XXX.XXX/0001-YY).
-- ============================================================================

INSERT INTO financial_data.popular_institution (rank, parent_cnpj, display_name, short_description) VALUES
( 1, '18236120', 'Nubank', 'Digital leader focused on user experience, no-fee cards, and automatic yield.'),
( 2, '00360305', 'Caixa', 'Social bank focused on housing (Minha Casa Minha Vida), FGTS, and government programs.'),
( 3, '00000000', 'Banco do Brasil', 'Solid institution with a strong presence in agribusiness, rural credit, and institutional security.'),
( 4, '60872504', 'Itaú', 'Largest private bank in Latin America, a reference in technology, wealth management, and premium cards.'),
( 5, '60746948', 'Bradesco', 'Retail bank with a vast branch network, a tradition in insurance, and nationwide accessibility.'),
( 6, '90400888', 'Santander', 'Specialist in vehicle financing, global credit cards, and real estate credit.'),
( 7, '00416968', 'Inter', 'Financial super app with a free account, integrated shopping platform, and own brokerage.'),
( 8, '22896431', 'PicPay', 'Super app focused on fast payments, social transfers, and wallet yield.'),
( 9, '10573521', 'Mercado Pago', 'Financial arm of Mercado Livre; focused on seller credit and QR Code payments.'),
(10, '08561701', 'PagBank', 'Pioneer in payment terminals, offering a full digital account focused on small entrepreneurs.'),
(11, '30306294', 'BTG Pactual', 'Largest investment bank in Latin America, focused on high-net-worth clients, asset management, and financial strength.'),
(12, '31872495', 'C6 Bank', 'Digital bank focused on high-net-worth and business clients, with a points program (Atomos) and global account.'),
(13, '33264668', 'XP', 'Integrated with Brazil''s largest brokerage; focused on investment advisory and cashback cards.'),
(14, '58160789', 'Safra', 'One of the most conservative and safe banks, focused on high-net-worth (Private) and large corporations.'),
(15, '62232889', 'Daycoval', 'Reference in payroll credit and corporate financing, with very solid balance sheets.'),
(16, '59285411', 'Banco Pan', 'Specialist in credit for lower-income segments, motorcycle and used vehicle financing.'),
(17, '28195667', 'ABC Brasil', 'Corporate bank focused on large companies, known as a reliable dividend payer.'),
(18, '59588111', 'BV', 'Leader in vehicle financing and personal credit, with strong backing from Banco do Brasil.'),
(19, '61186680', 'BMG', 'Specialist in payroll credit for retirees and popular credit cards.'),
(20, '60889128', 'Sofisa Direto', 'Specialist in fixed income (CDBs) with daily liquidity and competitive rates for investors.')
ON CONFLICT (rank) DO UPDATE SET
    parent_cnpj       = EXCLUDED.parent_cnpj,
    display_name      = EXCLUDED.display_name,
    short_description = EXCLUDED.short_description,
    active            = EXCLUDED.active,
    updated_at        = NOW();
