-- Set search path to security schema
SET search_path TO security, public;

-- Insert banking issuer view permission
INSERT INTO security.permissions (name) VALUES
    ('banking:issuer:view')
ON CONFLICT (name) DO NOTHING;
