-- Migration: Add saldo_ajustado column to bacen_bank_report_data
-- Version: V1_14
-- Description: Add nullable saldo_ajustado column for quarter-adjusted balances
-- 
-- This migration adds the saldo_ajustado column to store adjusted balances for
-- accounts that report cumulative values in certain months (e.g., account 78187
-- in months 06 and 12). The adjusted value represents the quarter-only amount
-- calculated as saldo(current_quarter) - saldo(previous_quarter).
-- 
-- Key features:
-- - Nullable NUMERIC column (only populated for specific accounts/months)
-- - Original saldo column remains unchanged
-- - Consumers should use COALESCE(saldo_ajustado, saldo) for affected accounts

SET search_path TO financial_data, public;

-- Add saldo_ajustado column
ALTER TABLE financial_data.bacen_bank_report_data
    ADD COLUMN IF NOT EXISTS saldo_ajustado NUMERIC;
