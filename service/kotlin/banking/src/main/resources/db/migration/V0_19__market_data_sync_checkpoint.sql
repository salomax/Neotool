ALTER TABLE banking.market_data_sync_control
    ADD COLUMN IF NOT EXISTS last_cursor TEXT;
