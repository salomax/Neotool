---
id: feature.transactions.recurring-payments.recurring-model
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/RecurringPayment.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/RecurringPaymentService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/RecurringMatchingService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/transaction/RecurringReminderJob.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/recurring/RecurringPaymentResolver.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_20__create_recurring_payments.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/RecurringPaymentServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/RecurringMatchingServiceTest.kt
  frontend:
    - web/src/app/personal-finance/recurring-payments/
    - web/src/lib/graphql/operations/banking/recurringPaymentOperations.ts
    - web/src/features/personal-finance/dashboard/widgets/UpcomingRecurringWidget.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.recurring-payments.lifecycle-and-linking
  - feature.transactions.ledger-transactions.transaction-model
schema: spec-metadata-v1
---

# Recurring Payments — Model

## Glossary

- `accountId` — tenant owning the recurring template (DB column; **not** on GraphQL type).
- `financialAccountId` — optional banking FA the template is associated with (nullable; list can filter by it).
- Template — scheduled expectation (rent, salary, …), not an auto-posted ledger row by itself.

## Behavior

### Persisted fields (`banking.recurring_payments`)

- Identity: `id`, `account_id`, optional `financial_account_id`.
- Money shape: `name`, `credit_debit_type`, optional `type` / `expected_amount`, `currency_code` (default BRL).
- Optional `category_id` / `merchant_id`.
- Schedule: `frequency` (`MONTHLY`|`WEEKLY`|`YEARLY`|`CUSTOM`), `day_of_month`, `interval_days`, `remind_days_before` (default 3), `next_due_date`, `last_paid_date`.
- Lifecycle: `status` (`ACTIVE`|`PAUSED` only), soft-delete `deleted_at`.
- Ledger link: `transactions.recurring_payment_id` FK.

### Validation / next due (`RecurringPaymentService`)

- MONTHLY requires `dayOfMonth` (clamped to month length when computing next due).
- CUSTOM requires positive `intervalDays`; WEEKLY defaults interval 7 when computing.
- `remindDaysBefore` ∈ 0..60; `expectedAmount` > 0 if set.

### Matching (suggestion, not create)

- `RecurringMatchingService`: score description×0.55 + merchant 0.25 + amount band 0.20; threshold default **0.80**; date gate ±**10** days (bypassed when past-due). Config: `banking.recurring.suggestion-threshold`, `date-gate-days`.
- Matching is **account-wide** (does not filter by template `financialAccountId`).

### Reminders

- `RecurringReminderJob` hourly (`banking.recurring-reminders.enabled`); opens `PendingActionType.RECURRING_PAYMENT_DUE` when due window reached. **Does not post transactions.**

## Invariants

- Status is only ACTIVE/PAUSED (no COMPLETED enum).
- Tenant scope always via `accountId`; GraphQL clients never send/receive tenant id on the type.
- Optional FA association does not replace tenant ownership.

## Permissions

| Op | Permission |
|---|---|
| `recurringPayments` / `recurringPayment` / `suggestRecurringForTransaction` | `banking:transaction:view` |
| `createRecurringPayment` / `createRecurringPaymentFromTransaction` | `banking:transaction:create` |
| update / pause / resume / delete / confirm / link | `banking:transaction:update` |

Delete uses **update**, not `banking:transaction:delete`.

## Data And Contracts

- GraphQL `RecurringPayment`, inputs create/update/from-transaction.
- No Bruno Banking requests found for recurring.

## Impact Surface

- Backend domain/service/matching/reminder job + resolver
- Web recurring-payments app + dashboard widget + ops
- Cross: lifecycle/linking; pending actions; ledger `recurringPaymentId`

## Decisions

- Templates are reminders/schedule metadata; posting remains a separate user/link action (see lifecycle).
- Soft-delete templates; indexes favor active status+due queries.

## Known Gaps

- Create/update does not explicitly call FA ownership guard (relies on FK + tenant filters) — [#600](https://github.com/invistus/invistus/issues/600).
- No Bruno / no Playwright E2E for recurring pages — [#599](https://github.com/invistus/invistus/issues/599).
- No mobile surface.

## Legacy Notes

- None used as authority.
