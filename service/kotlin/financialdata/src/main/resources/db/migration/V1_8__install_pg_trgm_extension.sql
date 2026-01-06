-- Migration: Install pg_trgm extension
-- Version: V1_8
-- Description: Install pg_trgm extension for text similarity functions
-- 
-- This migration installs the pg_trgm (trigram) extension which provides
-- text similarity functions like similarity() and word_similarity().
-- These functions are used for relevance scoring in institution search queries.
--
-- The extension is installed in the financial_data schema to follow
-- the project's pattern of avoiding the public schema.
-- Note: This requires CREATE EXTENSION privileges. If the migration fails due to
-- insufficient privileges, the extension must be installed manually by a database administrator.

SET search_path TO financial_data, public;

-- Install pg_trgm extension in the financial_data schema
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA financial_data;

