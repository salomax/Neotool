---
id: feature.transactions.summaries.month-summary-query
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/MonthSummaryQueryService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/MonthSummary.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/account/FinancialAccountEnums.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database: []
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/MonthSummaryQueryServiceTest.kt
    - web/tests/e2e/personal-finance/monthly-summary.e2e.spec.ts
  frontend:
    - web/src/lib/graphql/operations/banking/queries.ts
    - web/src/app/personal-finance/finance-transactions/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.summaries.monthly-balance-snapshots
  - feature.transactions.summaries.dashboard-and-cashflow
  - feature.transactions.ledger-transactions.transaction-model
schema: spec-metadata-v1
---

# Summaries — Month Summary Query

## Glossary

- `accountId` — tenant from JWT.
- `financialAccountId` — optional FA scope; omit → aggregate cashflow accounts for tenant.

## Behavior

### GraphQL `monthSummary(financialAccountId, year, month)`

- Permission: `banking:transaction:view`.
- Single FA: ownership via `FinancialAccountGuard`.
- Past/current: resolve snapshot (clean use / dirty or missing → `recomputeChain` heal) → header fields from snapshot; **on-demand** `transfersIn`/`transfersOut` (`getMonthTransferTotals`); `projectedClosingBalance` if any FUTURE credit/debit; category/tag breakdowns for EXPENSE/INCOME/all tags.
- Future month: **no snapshot write**; anchor = current month closing; walk intermediate months with POSTED/PENDING + FUTURE nets; income/expenses combine real + future buckets; `isFutureMonth=true`; category/tag lists empty for future path.

### Aggregate (no `financialAccountId`)

- FAs from `findFinancialAccountIdsByAccount`: non-deleted, `include_in_net_worth=true`, type **not in** `NON_CASHFLOW_ACCOUNT_TYPES` (`INVESTMENT`, `BENEFIT`).
- Sum per-account summaries; category/tag breakdowns recomputed across included FAs.
- Internal transfers cancel in income/expense (excluded types) but appear in per-account transfer fields when summed.

### What counts

| Metric | Includes | Excludes |
|---|---|---|
| closingBalance | POSTED/PENDING all types | FUTURE; soft-deleted |
| totalIncome/Expenses | POSTED/PENDING CREDIT/DEBIT | TRANSFER, BALANCE_ADJUSTMENT, BILL_PAYMENT, FUTURE |
| transfersIn/Out | TRANSFER POSTED/PENDING | other types; FUTURE |
| projectedClosing | closing + FUTURE nets | — |
| category/tag | POSTED/PENDING expense or income side | TRANSFER / BALANCE_ADJUSTMENT / BILL_PAYMENT when filtering by creditDebit |

## Invariants

- Summary never stores balance on transaction rows.
- Heal-on-read for past/current dirty/missing snapshots.
- Aggregate skips investment/benefit account types.

## Permissions

Backend: `banking:transaction:view`. UX: finance-transactions month header / widgets.

## Data And Contracts

- GraphQL `MonthSummary`, `CategoryTotal`, `TagTotal`.
- Bruno: `contracts/api-collections/bruno/collections/supergraph/Banking/Transactions/Get Month Summary.bru` (omits `isFutureMonth` / category/tag fields in selection).
- E2E: `monthly-summary.e2e.spec.ts`.

## Impact Surface

- Backend: MonthSummaryQueryService, resolver wiring
- GraphQL + Bruno + web monthSummary query + E2E
- Cross: snapshots; bills only indirectly via installment due-date attribution

## Decisions

- Transfers kept out of income/expense and exposed separately.
- Future months are projection-only.

## Known Gaps

- Bruno selection incomplete vs full GraphQL type.
- Aggregate path depends on FAs that already have transactions (`findFinancialAccountIdsByAccount` joins txs).

## Legacy Notes

- None used as authority.
