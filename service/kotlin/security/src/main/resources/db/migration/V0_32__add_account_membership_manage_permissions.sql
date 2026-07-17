SET search_path TO security, public;

INSERT INTO security.permissions (name) VALUES
    ('security:account:invite'),
    ('security:account-membership:manage')
ON CONFLICT (name) DO NOTHING;

INSERT INTO security.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM security.roles r
JOIN security.permissions p
  ON p.name IN ('security:account:invite', 'security:account-membership:manage')
WHERE r.name = 'Banking User'
ON CONFLICT DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uq_memberships_invitation_token
    ON security.account_memberships(invitation_token)
    WHERE invitation_token IS NOT NULL;
