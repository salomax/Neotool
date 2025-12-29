-- Set search path to security schema
SET search_path TO security, public;

-- Insert assets permissions
INSERT INTO security.permissions (name) VALUES 
    ('assets:asset:view'),
    ('assets:asset:upload'),
    ('assets:asset:delete')
ON CONFLICT (name) DO NOTHING;

