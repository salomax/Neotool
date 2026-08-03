---
id: feature.transactions.ledger-transactions.mcp-change-log
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/McpChangeLogService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/McpChangeLogEntry.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/McpChangeLogAction.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionChangeSnapshot.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionRuleService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultMcpChangeLogRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_43__add_mcp_origin_columns.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_44__create_mcp_change_log.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/McpChangeLogServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/McpChangeLogGraphQLIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/McpWriteMutationsGraphQLIT.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.ledger-transactions.idempotency-and-bulk-caps
  - feature.transactions.ledger-transactions.create-update-delete
schema: spec-metadata-v1
---

# Ledger Transactions — MCP Change Log

## Glossary

- `accountId` — tenant used to scope list/revert via join to `banking.transactions`.
- Provenance / origin — `origin_channel` (`UI`|`MCP`), `origin_client_id`, `origin_tool_call_id` on transaction (and rules) rows.
- Change log — append-only `banking.mcp_change_log` audit of agent categorization-style updates.

## Behavior

### Provenance on ledger rows

- V0_43 adds origin columns; create/update/soft-delete paths set `ChangeOrigin` from `agentCallContext.toChangeOriginOrUi()` when present (`TransactionService`).
- Channel defaults to `UI` when no agent context.

### Change log persistence

- Table `mcp_change_log`: `tool_call_id`, optional `client_id`/`token_id`, `resource_type` (`TRANSACTION` only), `resource_id`, `action` (`UPDATE`|`REVERT`), JSONB `before`/`after`, `created_at`.
- Immutable: UPDATE/DELETE trigger raises; privileges revoked for mutation.
- **Write paths verified today**:
  - agent `bulkUpdateCategory` in `DefaultTransactionRepository` inserts `UPDATE` entries with before/after `TransactionChangeSnapshot` when `agentCallContext != null`.
  - agent `applyTransactionRuleToHistorical` in `TransactionRuleService` inserts `UPDATE` entries for matched transactions when `agentCallContext != null`.
- Snapshot fields: `categoryId`, `categorySource`, `merchantId`, `type`, `description`, `tags`, origin channel/client/toolCallId — **not** amount/date/financialAccountId.
- Create/update/delete of a single transaction set origin columns but do **not** (in current repo code) append full mcp_change_log rows for arbitrary field edits.

### Query

- GraphQL `mcpChangeLog(clientId, tokenId, toolCallId, limit=50, offset=0)` requires at least one of clientId/tokenId/toolCallId; scoped by joining resource → transaction `account_id`.
- Permission: `banking:transaction:view`.

### Revert

- Mutation `revertToolCallChanges(toolCallId)` (`banking:transaction:update`): loads original `UPDATE` entries for that tool call + tenant; skips rows missing or edited after the log entry; applies snapshot `before` onto current tx; appends `REVERT` entries; marks monthly snapshots dirty from earliest affected month.
- Returns `BulkOperationResult`-shaped counts/errors (same DTO family as bulk categorize).

## Invariants

- Change log rows are append-only.
- Revert is best-effort per resource; later user edits block that resource.
- Tenant isolation via transaction join, not a denormalized `account_id` column on the log table.

## Permissions

Backend enforcement: view for query; update for revert.

UX hints: no web operations found for `mcpChangeLog` / `revertToolCallChanges` (generated schema types only).

## Data And Contracts

- GraphQL `type McpChangeLogEntry`, query `mcpChangeLog`, mutation `revertToolCallChanges`.
- Migrations V0_43, V0_44.
- Tests: `McpChangeLogServiceTest`, `McpChangeLogGraphQLIT`, agent write IT origin assertions.

## Impact Surface

- Backend: McpChangeLogService/repos, bulk category update path, TransactionResolver
- GraphQL banking schema
- Cross: categorization bulk (writer); summaries via dirty snapshots on revert

## Decisions

- Change log is MCP/agent audit for categorization/rule-shaped updates, not a general ledger CDC.
- Revert restores snapshot fields only.

## Known Gaps

- No web/Bruno consumers for query/revert.
- Single-tx MCP create/update may stamp origin without change-log rows — agents must not assume every MCP write is revertible via this log.
- Resource type CHECK only allows `TRANSACTION` (rules origin columns exist separately without log resource type yet).

## Legacy Notes

- None used as authority.
