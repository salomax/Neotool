-- Set search path to security schema
SET search_path TO security, public;

-- 1. Add missing banking permissions
INSERT INTO security.permissions (name) VALUES
    ('banking:account:view'),
    ('banking:account:create')
ON CONFLICT (name) DO NOTHING;

-- 2. Create Banking User role (standard role for all app users)
INSERT INTO security.roles (name) VALUES
    ('Banking User')
ON CONFLICT (name) DO NOTHING;

-- 3. Assign all banking permissions (including issuer:view) to Banking User role
INSERT INTO security.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM security.roles r
CROSS JOIN security.permissions p
WHERE r.name = 'Banking User'
  AND p.name IN ('banking:issuer:view', 'banking:account:view', 'banking:account:create')
ON CONFLICT DO NOTHING;

-- 4. Create Personal Users group (retail segment: individuals and families)
INSERT INTO security.groups (id, name, description, created_at, updated_at, version) VALUES
    (uuidv7(), 'Personal Users', 'Default group for individual and family account users. Grants standard banking access.', NOW(), NOW(), 0)
ON CONFLICT (name) DO NOTHING;

-- 5. Create Advisors group (wholesale segment: financial advisors with delegated client access)
INSERT INTO security.groups (id, name, description, created_at, updated_at, version) VALUES
    (uuidv7(), 'Advisors', 'Financial advisors with delegated access to client accounts.', NOW(), NOW(), 0)
ON CONFLICT (name) DO NOTHING;

-- 6. Assign Banking User role to Personal Users group
INSERT INTO security.group_role_assignments (id, group_id, role_id, valid_from, valid_until, created_at, updated_at, version)
SELECT uuidv7(), g.id, r.id, NULL, NULL, NOW(), NOW(), 0
FROM security.groups g
CROSS JOIN security.roles r
WHERE g.name = 'Personal Users'
  AND r.name = 'Banking User'
ON CONFLICT DO NOTHING;

-- 7. Backfill: enroll all existing users in Personal Users group
INSERT INTO security.group_memberships (id, user_id, group_id, membership_type, valid_until, created_at, updated_at, version)
SELECT uuidv7(), u.id, g.id, 'MEMBER', NULL, NOW(), NOW(), 0
FROM security.users u
CROSS JOIN security.groups g
WHERE g.name = 'Personal Users'
ON CONFLICT (user_id, group_id) DO NOTHING;
