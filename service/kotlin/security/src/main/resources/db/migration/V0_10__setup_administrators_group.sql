-- Set search path to security schema
SET search_path TO security, public;

-- Create "Administrators" group
INSERT INTO security.groups (id, name, description, created_at, updated_at, version)
VALUES (
    uuidv7(),
    'Administrators',
    'System administrators group with full authorization management permissions',
    NOW(),
    NOW(),
    0
)
ON CONFLICT (name) DO NOTHING;

-- Link "Administrators" group to "Authorization Manager" role
INSERT INTO security.group_role_assignments (id, group_id, role_id, valid_from, valid_until, created_at, updated_at, version)
SELECT 
    uuidv7(),
    g.id,
    r.id,
    NULL, -- valid_from: null means valid from creation
    NULL, -- valid_until: null means no expiry
    NOW(),
    NOW(),
    0
FROM security.groups g
CROSS JOIN security.roles r
WHERE g.name = 'Administrators'
  AND r.name = 'Authorization Manager'
ON CONFLICT DO NOTHING;

-- Link admin user to "Administrators" group
INSERT INTO security.group_memberships (id, user_id, group_id, membership_type, valid_until, created_at, updated_at, version)
SELECT 
    uuidv7(),
    u.id,
    g.id,
    'member', -- membership_type
    NULL, -- valid_until: null means no expiry
    NOW(),
    NOW(),
    0
FROM security.users u
CROSS JOIN security.groups g
WHERE u.email = 'admin@example.com'
  AND g.name = 'Administrators'
ON CONFLICT (user_id, group_id) DO NOTHING;
