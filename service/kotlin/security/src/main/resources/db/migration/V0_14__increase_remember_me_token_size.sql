-- Set search path to security schema
SET search_path TO security, public;

-- Change remember_me_token column from VARCHAR(255) to TEXT to support longer JWT tokens
ALTER TABLE security.users ALTER COLUMN remember_me_token TYPE TEXT;

