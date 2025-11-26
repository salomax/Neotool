-- Set search path to security schema
SET search_path TO security, public;

-- Add password reset token columns to users table
ALTER TABLE security.users ADD COLUMN IF NOT EXISTS password_reset_token VARCHAR(255);
ALTER TABLE security.users ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMP;
ALTER TABLE security.users ADD COLUMN IF NOT EXISTS password_reset_used_at TIMESTAMP;

-- Add index on password_reset_token for faster lookups
CREATE INDEX IF NOT EXISTS idx_users_password_reset_token ON security.users(password_reset_token);

-- Create password reset attempts table for rate limiting
CREATE TABLE IF NOT EXISTS security.password_reset_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    window_start TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Add index on email and window_start for faster lookups
CREATE INDEX IF NOT EXISTS idx_password_reset_attempts_email_window ON security.password_reset_attempts(email, window_start);

-- Add index on created_at for cleanup queries
CREATE INDEX IF NOT EXISTS idx_password_reset_attempts_created_at ON security.password_reset_attempts(created_at);

