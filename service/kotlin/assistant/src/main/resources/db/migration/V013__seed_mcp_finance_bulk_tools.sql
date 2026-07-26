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
        'categorize_transactions',
        'Apply one category to multiple transactions in the authenticated account. The caller must provide an idempotency_key. Banking computes the affected count server-side and returns CONFIRMATION_REQUIRED instead of mutating when the bulk cap is exceeded.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "idempotency_key": { "type": "string" },
            "transactionIds": {
              "type": "array",
              "items": { "type": "string", "format": "uuid" }
            },
            "categoryId": { "type": "string", "format": "uuid" }
          },
          "required": ["idempotency_key", "transactionIds", "categoryId"],
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "bulkCategorize": { "type": "object" } } }$$::jsonb,
        'mcp',
        'categorize_transactions',
        30000,
        NULL,
        TRUE,
        'finance:transactions:write',
        'ACTIVE'
    ),
    (
        'apply_rule_to_historical',
        'Apply one transaction rule to historical transactions in the authenticated account. The caller must provide the rule id and an idempotency_key. Banking computes the affected count server-side and returns CONFIRMATION_REQUIRED instead of mutating when the bulk cap is exceeded.',
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
        $${ "type": "object", "properties": { "applyTransactionRuleToHistorical": { "type": "object" } } }$$::jsonb,
        'mcp',
        'apply_rule_to_historical',
        30000,
        NULL,
        TRUE,
        'finance:rules:write',
        'ACTIVE'
    );
