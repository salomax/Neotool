-- Set search path to security schema
SET search_path TO security, public;

-- Ensure assets permissions exist
INSERT INTO security.permissions (name) VALUES
    ('assets:asset:view'),
    ('assets:asset:upload'),
    ('assets:asset:delete')
ON CONFLICT (name) DO NOTHING;

-- Create roles
INSERT INTO security.roles (name) VALUES
    ('Assets Manager'),
    ('Assets Viewer')
ON CONFLICT (name) DO NOTHING;

-- Link Assets Manager role to permissions
INSERT INTO security.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM security.roles r
JOIN security.permissions p ON p.name IN ('assets:asset:view', 'assets:asset:upload', 'assets:asset:delete')
WHERE r.name = 'Assets Manager'
ON CONFLICT DO NOTHING;

-- Link Assets Viewer role to view permission
INSERT INTO security.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM security.roles r
JOIN security.permissions p ON p.name = 'assets:asset:view'
WHERE r.name = 'Assets Viewer'
ON CONFLICT DO NOTHING;

-- Assign Assets Manager role to Administrators group
INSERT INTO security.group_role_assignments (id, group_id, role_id, valid_from, valid_until, created_at, updated_at, version)
SELECT
    uuidv7(),
    g.id,
    r.id,
    NULL,
    NULL,
    NOW(),
    NOW(),
    0
FROM security.groups g
CROSS JOIN security.roles r
WHERE g.name = 'Administrators'
  AND r.name = 'Assets Manager'
ON CONFLICT DO NOTHING;

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
