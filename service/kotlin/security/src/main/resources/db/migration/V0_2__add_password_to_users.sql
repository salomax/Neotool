-- Set search path to security schema
SET search_path TO security, public;

-- Add password_hash column to users table
ALTER TABLE security.users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Add remember_me_token column for session persistence
ALTER TABLE security.users ADD COLUMN IF NOT EXISTS remember_me_token VARCHAR(255);

-- Add index on remember_me_token for faster lookups
CREATE INDEX IF NOT EXISTS idx_users_remember_me_token ON security.users(remember_me_token);

-- Add index on email for faster authentication lookups (if not already exists)
CREATE INDEX IF NOT EXISTS idx_users_email ON security.users(email);

-- Insert admin user (admin/admin)
INSERT INTO security.users (id, email, display_name, password_hash)
VALUES (
    uuidv7(),
    'admin@invistus.com.br',
    'Admin',
    '$argon2id$v=19$m=48128,t=1,p=1$as0B8ZdUaNBUkfYAw43txQ$zZQ3q2POS2q8481zRH/FnxayzbtRY+LezdBYLb5kx+A'
)
ON CONFLICT (email) DO NOTHING;
