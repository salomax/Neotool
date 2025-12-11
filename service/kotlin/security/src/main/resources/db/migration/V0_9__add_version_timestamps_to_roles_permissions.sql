-- Set search path to security schema
SET search_path TO security, public;

-- Add version, created_at, and updated_at columns to roles table
ALTER TABLE security.roles ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE security.roles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE security.roles ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add version, created_at, and updated_at columns to permissions table
ALTER TABLE security.permissions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE security.permissions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE security.permissions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;








