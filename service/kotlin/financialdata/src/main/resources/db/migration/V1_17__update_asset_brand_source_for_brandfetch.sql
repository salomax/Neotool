SET search_path TO financial_data, public;

ALTER TABLE financial_data.asset_brand
    DROP CONSTRAINT IF EXISTS ck_asset_brand_source;

ALTER TABLE financial_data.asset_brand
    ADD CONSTRAINT ck_asset_brand_source
        CHECK (source IN ('LOGO_DEV_SEARCH', 'BRANDFETCH_ISIN', 'MANUAL'));

ALTER TABLE financial_data.asset_brand
    ALTER COLUMN source SET DEFAULT 'BRANDFETCH_ISIN';

ALTER TABLE financial_data.investment_asset_brand_match
    DROP CONSTRAINT IF EXISTS ck_investment_asset_brand_match_source;

ALTER TABLE financial_data.investment_asset_brand_match
    ADD CONSTRAINT ck_investment_asset_brand_match_source
        CHECK (match_source IN ('LOGO_DEV_SEARCH', 'BRANDFETCH_ISIN', 'MANUAL'));

ALTER TABLE financial_data.investment_asset_brand_match
    ALTER COLUMN match_source SET DEFAULT 'BRANDFETCH_ISIN';
