-- Migration: Create financial_data schema
-- Version: V1_1
-- Description: Create initial financial_data schema for BACEN IFData ingestion
-- 
-- This migration creates the financial_data schema that will contain all tables
-- for the BACEN IFData ingestion feature. The schema is created with IF NOT EXISTS
-- to ensure idempotency.

SET search_path TO financial_data, public;

-- Create the financial_data schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS financial_data;
