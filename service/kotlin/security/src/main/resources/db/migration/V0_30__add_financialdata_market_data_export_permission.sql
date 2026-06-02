-- ─── Financial Data service-to-service permissions ──────────────────────────
-- Registers the permission required to access internal bulk market-data export
-- endpoints exposed by the financialdata service.

INSERT INTO security.permissions (name) VALUES
    ('financialdata:market-data:export')
ON CONFLICT DO NOTHING;
