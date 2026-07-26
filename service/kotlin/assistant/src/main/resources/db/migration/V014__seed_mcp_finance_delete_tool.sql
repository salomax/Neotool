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
VALUES
    (
        'delete_transaction',
        'Delete one transaction for the authenticated account. The caller must provide an idempotency_key so deferred agent writes can replay safely without duplicating pending confirmation requests.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "id": { "type": "string", "format": "uuid" },
            "idempotency_key": { "type": "string" }
          },
          "required": ["id", "idempotency_key"],
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "deleteTransaction": { "type": "object" } } }$$::jsonb,
        'mcp',
        'delete_transaction',
        30000,
        NULL,
        TRUE,
        'finance:transactions:write',
        'ACTIVE'
    );
