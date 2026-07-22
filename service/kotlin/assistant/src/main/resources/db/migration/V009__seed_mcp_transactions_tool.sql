INSERT INTO assistant.tools (
    name,
    description,
    category,
    input_schema,
    output_schema,
    executor_service,
    executor_endpoint,
    timeout_ms,
    retry_policy,
    mcp_exposed,
    mcp_required_scope,
    status
)
VALUES (
    'finance.transactions.list',
    'List recent financial transactions for the authenticated account.',
    'finance',
    '{
      "type": "object",
      "properties": {
        "first": { "type": "integer", "minimum": 1, "maximum": 50 },
        "month": { "type": "integer", "minimum": 1, "maximum": 12 },
        "year": { "type": "integer", "minimum": 1900, "maximum": 2200 },
        "search": { "type": "string" }
      },
      "additionalProperties": false
    }'::jsonb,
    '{
      "type": "object",
      "properties": {
        "transactions": { "type": "object" },
        "requestedFirst": { "type": "integer" }
      }
    }'::jsonb,
    'mcp',
    'finance.transactions.list',
    30000,
    NULL,
    TRUE,
    'finance:transactions:read',
    'ACTIVE'
)
ON CONFLICT (name) DO UPDATE
SET
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    executor_service = EXCLUDED.executor_service,
    executor_endpoint = EXCLUDED.executor_endpoint,
    timeout_ms = EXCLUDED.timeout_ms,
    mcp_exposed = EXCLUDED.mcp_exposed,
    mcp_required_scope = EXCLUDED.mcp_required_scope,
    status = EXCLUDED.status;
