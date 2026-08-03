---
id: feature.transactions.categorization.bulk-and-similar-edit
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionRuleService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionCategoryResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database: []
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/McpWriteMutationsGraphQLIT.kt
    - web/tests/e2e/personal-finance/transactions-advanced.e2e.spec.ts
  frontend:
    - web/src/lib/graphql/operations/banking/mutations.ts
    - web/src/lib/graphql/operations/banking/queries.ts
    - web/src/app/personal-finance/finance-transactions/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.categorization.rule-engine
  - feature.transactions.categorization.category-catalog
  - feature.transactions.ledger-transactions.idempotency-and-bulk-caps
  - feature.transactions.ledger-transactions.mcp-change-log
schema: spec-metadata-v1
---

# Categorization — Bulk And Similar Edit

## Glossary

- Bulk categorize — set the same `categoryId` on many transactions for tenant `accountId`.
- Similar edit — propagate selected fields from one non-manual transaction to other similar non-manual rows (and optionally learn a future rule).

## Behavior

### `bulkCategorize`

- Permission: `banking:transaction:update`.
- Service: validates category accessible (system or same tenant); `bulkUpdateCategory` sets `categorySource=MANUAL`; **does not** call `learnFromManualAssignment`.
- Fields changed: `category_id`, `category_source`, `updated_at`; agent also stamps origin columns.
- **Web/manual**: runs immediately (no bulk-cap gate).
- **Agent path** (Batch 2): `executeIfAgent` + idempotency key required; `BulkWriteCapGuard` on `countCategorizableTransactions`; over cap → `CONFIRMATION_REQUIRED` + pending action; under cap → update writes **mcp_change_log** UPDATE snapshots (category-shaped) — revertible via `revertToolCallChanges`.

### `applyTransactionRuleToHistorical`

- Permission: `banking:category:manage`.
- Applies one **user-owned** rule to matching non-MANUAL, non–BALANCE_ADJUSTMENT txs.
- Fields that may change: category (+ `CategorySource.RULE`) only if current `categorySource != MANUAL`; merchant; type (via typeOverride); description; tags; appends `appliedRuleHistory`; origin from agent/UI.
- Agent path: same Batch 2 idempotency + bulk-cap + pending confirmation; writes mcp_change_log when agent context present.
- Marks monthly snapshots dirty for affected financial accounts.

### Similar edit (`similarTransactionEditPreview` / `applyTransactionEditToSimilar`)

- Preview (`banking:transaction:view`): for MANUAL source → `similarCount=0`, `supportsFuturePropagation=false`. Else count similar non-manual by base description + creditDebitType.
- Apply (`banking:transaction:update`): BALANCE_ADJUSTMENT rejected; MANUAL source → no-op (`affectedCount=0`).
- Similar rows updated fields (only if provided on the **current** tx after edit): `description`, `tags`, `categoryId` (+ `categorySource` MANUAL or clear). **Does not** propagate amount/date/merchant/type via this path.
- Optional `applyToFuture`: may `learnFromManualAssignment` when user `RuleCreationMode` is ALWAYS or ALWAYS_PROMPT and command warrants a future rule.
- **No** `idempotencyKey`, **no** BulkWriteCapGuard, **no** mcp_change_log writer on this mutation.

## Invariants

- All bulk/similar updates are tenant-scoped by `accountId`.
- Bulk categorize always MANUAL category source.
- Historical rule apply respects prior MANUAL category assignments.
- Similar propagation never targets MANUAL-source siblings.

## Permissions

Backend enforcement as above. UX: similar-edit dialog / bulk UI — not enforcement.

## Data And Contracts

- GraphQL: `bulkCategorize`, `applyTransactionRuleToHistorical`, `similarTransactionEditPreview`, `applyTransactionEditToSimilar`.
- Web ops present in banking mutations/queries.

## Impact Surface

- Backend: TransactionService, TransactionRuleService, DefaultTransactionRepository bulkUpdateCategory
- GraphQL resolvers + MCP write ITs + advanced E2E similar dialog
- Cross: Batch 2 caps/change-log for agent bulk/historical only

## Decisions

- Similar edit is a human UX path without MCP safeguards.
- Bulk categorize intentionally skips implicit rule learning.

## Known Gaps

- No Bruno for bulk/similar/historical.
- Similar edit lacks agent bulk-cap even if large fan-out.
- Dedicated unit coverage for similar-edit field matrix may be thinner than E2E exploratory TX-A-14.

## Legacy Notes

- None used as authority.
