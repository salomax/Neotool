UPDATE assistant.tools
SET
    description = 'DEPRECATED: legacy MCP transaction listing tool superseded by list_transactions.',
    mcp_exposed = FALSE
WHERE name = 'finance.transactions.list';

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
        'list_transactions',
        'Inspect individual transactions for the authenticated account. Use this when you need transaction-level detail; do not use it to compute totals when a summary tool can answer directly.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "financialAccountId": { "type": "string", "format": "uuid" },
            "search": { "type": "string" },
            "categoryId": { "type": "string", "format": "uuid" },
            "uncategorized": { "type": "boolean" },
            "tags": { "type": "array", "items": { "type": "string" } },
            "creditDebitType": { "type": "string", "enum": ["CREDIT", "DEBIT"] },
            "types": { "type": "array", "items": { "type": "string" } },
            "month": { "type": "integer", "minimum": 1, "maximum": 12 },
            "year": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "first": { "type": "integer", "minimum": 1, "maximum": 100 },
            "after": { "type": "string" }
          },
          "additionalProperties": false
        }$$::jsonb,
        $${
          "type": "object",
          "properties": {
            "transactions": { "type": "object" }
          }
        }$$::jsonb,
        'mcp',
        'list_transactions',
        30000,
        NULL,
        TRUE,
        'finance:transactions:read',
        'ACTIVE'
    ),
    (
        'get_transaction',
        'Fetch one transaction by id for the authenticated account, including categorization, merchant, notes, and timeline fields.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "id": { "type": "string", "format": "uuid" }
          },
          "required": ["id"],
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "transaction": { "type": "object" } } }$$::jsonb,
        'mcp',
        'get_transaction',
        30000,
        NULL,
        TRUE,
        'finance:transactions:read',
        'ACTIVE'
    ),
    (
        'get_month_summary',
        'Answer cash-flow questions for one calendar month. Use this for monthly income, expenses, closing balance, transfers, and top category or tag breakdowns.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "financialAccountId": { "type": "string", "format": "uuid" },
            "year": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "month": { "type": "integer", "minimum": 1, "maximum": 12 }
          },
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "monthSummary": { "type": "object" } } }$$::jsonb,
        'mcp',
        'get_month_summary',
        30000,
        NULL,
        TRUE,
        'finance:cashflow:read',
        'ACTIVE'
    ),
    (
        'get_period_summary',
        'Answer arbitrary date-range summary questions. Prefer this over get_month_summary when the user asks about a span of months rather than one calendar month.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "financialAccountId": { "type": "string", "format": "uuid" },
            "startYear": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "startMonth": { "type": "integer", "minimum": 1, "maximum": 12 },
            "endYear": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "endMonth": { "type": "integer", "minimum": 1, "maximum": 12 }
          },
          "required": ["startYear", "startMonth", "endYear", "endMonth"],
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "periodSummary": { "type": "object" } } }$$::jsonb,
        'mcp',
        'get_period_summary',
        30000,
        NULL,
        TRUE,
        'finance:cashflow:read',
        'ACTIVE'
    ),
    (
        'get_cash_flow_series',
        'Return one row per month for a date range, with income, expenses, net cash flow, and closing balance for each month.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "financialAccountId": { "type": "string", "format": "uuid" },
            "startYear": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "startMonth": { "type": "integer", "minimum": 1, "maximum": 12 },
            "endYear": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "endMonth": { "type": "integer", "minimum": 1, "maximum": 12 }
          },
          "required": ["startYear", "startMonth", "endYear", "endMonth"],
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "cashFlowSeries": { "type": "array" } } }$$::jsonb,
        'mcp',
        'get_cash_flow_series',
        30000,
        NULL,
        TRUE,
        'finance:cashflow:read',
        'ACTIVE'
    ),
    (
        'get_spending_by_category',
        'Break spending or income down by category. Use month and year for one calendar month, or use startYear/startMonth/endYear/endMonth for a date range. Do not send both shapes together.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "financialAccountId": { "type": "string", "format": "uuid" },
            "search": { "type": "string" },
            "categoryId": { "type": "string", "format": "uuid" },
            "uncategorized": { "type": "boolean" },
            "tags": { "type": "array", "items": { "type": "string" } },
            "month": { "type": "integer", "minimum": 1, "maximum": 12 },
            "year": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "startYear": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "startMonth": { "type": "integer", "minimum": 1, "maximum": 12 },
            "endYear": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "endMonth": { "type": "integer", "minimum": 1, "maximum": 12 },
            "applicableTo": { "type": "string", "enum": ["EXPENSE", "INCOME"] }
          },
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "categoryTotals": { "type": "array" } } }$$::jsonb,
        'mcp',
        'get_spending_by_category',
        30000,
        NULL,
        TRUE,
        'finance:cashflow:read',
        'ACTIVE'
    ),
    (
        'get_spending_by_tag',
        'Break transactions down by tag for a month and optional filters. Prefer this when the user asks about tagged spending rather than category totals.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "financialAccountId": { "type": "string", "format": "uuid" },
            "search": { "type": "string" },
            "categoryId": { "type": "string", "format": "uuid" },
            "uncategorized": { "type": "boolean" },
            "tags": { "type": "array", "items": { "type": "string" } },
            "month": { "type": "integer", "minimum": 1, "maximum": 12 },
            "year": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "applicableTo": { "type": "string", "enum": ["EXPENSE", "INCOME"] }
          },
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "tagTotals": { "type": "array" } } }$$::jsonb,
        'mcp',
        'get_spending_by_tag',
        30000,
        NULL,
        TRUE,
        'finance:cashflow:read',
        'ACTIVE'
    ),
    (
        'get_top_merchants',
        'Return the top merchants for a month, with count, total spend, and last-used date. Use this instead of listing transactions when the question is merchant-focused.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "financialAccountId": { "type": "string", "format": "uuid" },
            "year": { "type": "integer", "minimum": 1900, "maximum": 2200 },
            "month": { "type": "integer", "minimum": 1, "maximum": 12 },
            "limit": { "type": "integer", "minimum": 1, "maximum": 50 }
          },
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "topMerchants": { "type": "array" } } }$$::jsonb,
        'mcp',
        'get_top_merchants',
        30000,
        NULL,
        TRUE,
        'finance:cashflow:read',
        'ACTIVE'
    ),
    (
        'list_financial_accounts',
        'List the authenticated account''s financial accounts and cards, including account metadata, issuer information, and display flags.',
        'finance',
        $${ "type": "object", "properties": {}, "additionalProperties": false }$$::jsonb,
        $${ "type": "object", "properties": { "financialAccounts": { "type": "array" } } }$$::jsonb,
        'mcp',
        'list_financial_accounts',
        30000,
        NULL,
        TRUE,
        'finance:accounts:read',
        'ACTIVE'
    ),
    (
        'list_credit_card_bills',
        'List credit card bills for one financial account. financialAccountId is required and must point to the card account whose bills you need.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "financialAccountId": { "type": "string", "format": "uuid" }
          },
          "required": ["financialAccountId"],
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "creditCardBills": { "type": "array" } } }$$::jsonb,
        'mcp',
        'list_credit_card_bills',
        30000,
        NULL,
        TRUE,
        'finance:bills:read',
        'ACTIVE'
    ),
    (
        'get_credit_card_bill',
        'Fetch one credit card bill for a financial account. financialAccountId is required; referenceMonth is optional and uses YYYY-MM-DD.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "financialAccountId": { "type": "string", "format": "uuid" },
            "referenceMonth": { "type": "string", "format": "date" }
          },
          "required": ["financialAccountId"],
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "creditCardBill": { "type": "object" } } }$$::jsonb,
        'mcp',
        'get_credit_card_bill',
        30000,
        NULL,
        TRUE,
        'finance:bills:read',
        'ACTIVE'
    ),
    (
        'list_categories',
        'List transaction categories available to the authenticated account, including system and user-defined categories.',
        'finance',
        $${ "type": "object", "properties": {}, "additionalProperties": false }$$::jsonb,
        $${ "type": "object", "properties": { "categories": { "type": "array" } } }$$::jsonb,
        'mcp',
        'list_categories',
        30000,
        NULL,
        TRUE,
        'finance:categories:read',
        'ACTIVE'
    ),
    (
        'list_transaction_rules',
        'List transaction auto-categorization rules. Use search to filter by pattern text and isSystem to focus on system or user rules.',
        'finance',
        $${
          "type": "object",
          "properties": {
            "isSystem": { "type": "boolean" },
            "search": { "type": "string" },
            "first": { "type": "integer", "minimum": 1, "maximum": 100 },
            "after": { "type": "string" }
          },
          "additionalProperties": false
        }$$::jsonb,
        $${ "type": "object", "properties": { "transactionRules": { "type": "object" } } }$$::jsonb,
        'mcp',
        'list_transaction_rules',
        30000,
        NULL,
        TRUE,
        'finance:rules:read',
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
    retry_policy = EXCLUDED.retry_policy,
    mcp_exposed = EXCLUDED.mcp_exposed,
    mcp_required_scope = EXCLUDED.mcp_required_scope,
    status = EXCLUDED.status;
