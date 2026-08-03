---
id: feature.transactions.ledger-transactions.idempotency-and-bulk-caps
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/IdempotencyService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/BulkWriteCapGuard.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultMcpWriteIdempotencyRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionCategoryResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/pendingaction/AgentWriteConfirmationService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
    - service/kotlin/banking/src/main/resources/application.yml
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_45__create_mcp_write_idempotency.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/IdempotencyServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/BulkWriteCapGuardTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/McpWriteMutationsGraphQLIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/McpWriteIdempotencyServiceIT.kt
  frontend:
    - web/src/lib/graphql/operations/banking/mutations.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.ledger-transactions.create-update-delete
  - feature.transactions.ledger-transactions.mcp-change-log
schema: spec-metadata-v1
---

# Ledger Transactions — Idempotency And Bulk Caps

## Glossary

- `accountId` — tenant key for idempotency rows and pending confirmations.
- Agent path — GraphQL call with `AgentCallContext` from `trustedAgentCallContext` (MCP/connected agent).
- Manual/web path — human UI JWT without agent context.

## Behavior

### Idempotency (`IdempotencyService`)

- Table `banking.mcp_write_idempotency`: unique `(account_id, idempotency_key)`; stores `operation` + JSON `result`.
- `execute(accountId, key, operation, block)`: requires non-blank key (max 255); claim-or-replay; same key with different `operation` → validation error; missing key → `MCP_IDEMPOTENCY_KEY_REQUIRED`.
- **`executeIfAgent`**: if `agentCallContext == null`, runs `block()` **without** idempotency (key ignored). If agent context present, always goes through `execute` (key required).
- Resolvers wrap agent-capable mutations with `executeIfAgent` + operation names such as `create_transaction`, `update_transaction`, `delete_transaction`, `bulk_categorize`, rule create/apply-historical (see `TransactionResolver` / `TransactionCategoryResolver`).
- Wiring passes `idempotencyKey` argument + `trustedAgentCallContext(env, principal)`.
- Config: retention default `PT48H` + eviction job (`banking.mcp-write-idempotency` in `application.yml`).

### Manual web vs agent

| Surface | Uses `idempotencyKey`? |
|---|---|
| Web mutations (`mutations.ts`) | No — omit key; `executeIfAgent` short-circuits |
| Bruno Transactions Create/Update/Delete | No key observed |
| Agent/MCP GraphQL | Key **required**; replay returns stored result JSON |

### Bulk caps (`BulkWriteCapGuard`)

- Config `banking.mcp-write-bulk.max-affected-records` default **50** (`McpWriteBulkConfiguration`).
- `evaluate(affectedCount)`: `Proceed` if `affectedCount <= max`; else `ConfirmationRequired(affectedCount)`.
- Applied **only when agent context is present**:
  - `bulkCategorize`: counts categorizable ids, then proceed or `AgentWriteConfirmationService.requestBulkCategorize` → `BulkOperationOutcome.CONFIRMATION_REQUIRED` + `pendingAction`.
  - `applyTransactionRuleToHistorical`: same pattern via `countHistoricalMatches` / `requestApplyRuleToHistorical`.
- Manual/web bulk paths call service directly (no cap gate in resolver when `agentCallContext == null`).
- Agent **deleteTransaction** does not soft-delete immediately: returns `deleted: false` + pending delete confirmation (`requestDeleteTransaction`) — related agent-write gate, not the numeric bulk cap.

### Confirm path

- User confirms via `confirmPendingAction` (USER principal only) — pending-action machinery; categorization/delete actually applied after confirm.

## Invariants

- Idempotency uniqueness is per tenant `accountId` + key.
- Agent writes without key fail before mutation side effects (IT coverage).
- Bulk cap threshold is exclusive: count **greater than** max requires confirmation.

## Permissions

Backend enforcement: underlying mutation permissions + agent context trust; confirm pending requires user principal.

UX hints: web does not surface idempotency keys or bulk-cap pending UX for MCP.

## Data And Contracts

- GraphQL optional `idempotencyKey` on create/update/delete transaction, bulkCategorize, rule mutations.
- `BulkOperationResult` / `DeleteTransactionResult` may carry `pendingAction`.
- Migration V0_45.

## Impact Surface

- Backend: IdempotencyService, BulkWriteCapGuard, resolvers, AgentWriteConfirmationService
- GraphQL + ITs (`McpWriteMutationsGraphQLIT`, idempotency IT)
- Cross: categorization bulk/rule historical (pointer only)

## Decisions

- Idempotency and bulk caps are MCP/agent controls; UI remains fire-and-forget.
- Stored idempotent result must be a stable DTO shape for replay.

## Known Gaps

- No web E2E for agent idempotency / bulk confirmation.
- Bruno does not exercise agent + key path.
- Similar-edit / other multi-row updates may not share the same bulk-cap gate (only bulkCategorize + applyRuleToHistorical verified here).

## Legacy Notes

- None used as authority.
