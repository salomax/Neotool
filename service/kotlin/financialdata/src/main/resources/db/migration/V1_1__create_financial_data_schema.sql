-- Migration: Create financial_data schema
-- Version: V1_1
-- Description: Create initial financial_data schema for BACEN IFData ingestion
-- 
-- This migration creates the financial_data schema that will contain all tables
-- for the BACEN IFData ingestion feature. The schema is created with IF NOT EXISTS
-- to ensure idempotency.
--
-- Note: This migration consolidates the pg_trgm extension installation that was
-- previously in V1_8 (now removed as duplicate).

SET search_path TO financial_data, public;

-- Create the financial_data schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS financial_data;

-- Add extension for case insensitive
CREATE EXTENSION IF NOT EXISTS unaccent SCHEMA financial_data;

-- Add extension for text similarity (trigram) functions
-- Required for GIN indexes with gin_trgm_ops used in financial_institution table
CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA financial_data;
