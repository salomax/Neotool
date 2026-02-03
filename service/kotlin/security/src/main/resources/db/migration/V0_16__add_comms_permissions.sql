-- Set search path to security schema
SET search_path TO security, public;

-- Insert comms permissions
INSERT INTO security.permissions (name) VALUES
    ('comms:email:send'),
    ('comms:template:view')
ON CONFLICT (name) DO NOTHING;

-- Tie the comms permissions to the Authorization Manager role
INSERT INTO security.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM security.roles r
CROSS JOIN security.permissions p
WHERE r.name = 'Authorization Manager'
  AND p.name IN ('comms:email:send', 'comms:template:view')
ON CONFLICT DO NOTHING;
