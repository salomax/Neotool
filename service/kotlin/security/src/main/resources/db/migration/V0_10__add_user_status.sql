-- Set search path to security schema
SET search_path TO security, public;

-- Add enabled column to users table
-- Default to true for existing users (backward compatible)
ALTER TABLE security.users ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT true;

-- Change id column to use uuidv7() for new users
ALTER TABLE security.users ALTER COLUMN id SET DEFAULT uuidv7();

-- Create index on enabled column for efficient filtering
CREATE INDEX IF NOT EXISTS idx_users_enabled ON security.users(enabled);

