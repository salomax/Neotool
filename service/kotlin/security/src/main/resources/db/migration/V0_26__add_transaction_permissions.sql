-- ─── Transaction & Category permissions ──────────────────────────────────────
-- Adds Phase 2 (Transactions) permissions and assigns them to the
-- Banking User role so that all Personal Users can manage transactions
-- and categories from day one.
-- ─────────────────────────────────────────────────────────────────────────────

SET search_path TO security, public;

-- 1. Register new permissions
INSERT INTO security.permissions (name) VALUES
    ('banking:transaction:view'),
    ('banking:transaction:create'),
    ('banking:transaction:update'),
    ('banking:transaction:delete'),
    ('banking:category:view'),
    ('banking:category:manage')
ON CONFLICT (name) DO NOTHING;

-- 2. Grant all new permissions to the Banking User role
INSERT INTO security.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM security.roles r
CROSS JOIN security.permissions p
WHERE r.name = 'Banking User'
  AND p.name IN (
      'banking:transaction:view',
      'banking:transaction:create',
      'banking:transaction:update',
      'banking:transaction:delete',
      'banking:category:view',
      'banking:category:manage'
  )
ON CONFLICT DO NOTHING;
