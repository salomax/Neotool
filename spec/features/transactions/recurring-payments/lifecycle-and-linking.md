---
id: feature.transactions.recurring-payments.lifecycle-and-linking
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/RecurringPaymentCommandService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/RecurringPaymentService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/RecurringMatchingService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/transaction/RecurringReminderJob.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_20__create_recurring_payments.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/RecurringPaymentCommandServiceIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/RecurringPaymentServiceTest.kt
  frontend:
    - web/src/app/personal-finance/recurring-payments/
    - web/src/app/personal-finance/finance-transactions/transaction/TransactionActionMenu.tsx
    - web/src/lib/hooks/useRecurringSuggestion.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.recurring-payments.recurring-model
  - feature.transactions.ledger-transactions.create-update-delete
schema: spec-metadata-v1
---

# Recurring Payments — Lifecycle And Linking

## Glossary

- `accountId` — tenant on all command paths (JWT).
- `financialAccountId` — optional on template; linked txs keep their own FA.
- Confirm — advances template dates; **does not** create a ledger transaction.
- Link — attaches an existing POSTED txn to a template, then confirms using the txn date.

## Behavior

### Status transitions (`RecurringPaymentCommandService`)

| Action | Effect |
|---|---|
| Create | Status `ACTIVE` |
| Pause | `PAUSED`; deletes FUTURE txs tied to template |
| Resume | `ACTIVE`; does **not** recreate FUTURE rows |
| Soft-delete | Delete FUTURE rows; clear `recurring_payment_id` on linked txs; dismiss open due pending actions; soft-delete template |
| Confirm (`paymentDate`) | ACTIVE only; set `lastPaidDate` / advance `nextDueDate` (may skip multiple cycles); dismiss pending; delete FUTURE for that date |
| Link txn | ACTIVE template; POSTED txn; not `BALANCE_ADJUSTMENT`; not already linked elsewhere → set FK → `confirmPayment(tx.date)` |
| Create-from-txn | Seed template from txn (defaults MONTHLY, `dayOfMonth`=tx day, remind=3) → link |

### Suggestion UX

- `suggestRecurringForTransaction` + web `useRecurringSuggestion` / action menu “link to suggested recurring”.

### Automation reality

- Reminder job only (pending actions). **No scheduler/job found that inserts FUTURE projection rows** from templates — only cleanup deletes exist (`deleteFutureByRecurringPaymentId`). If FUTURE rows appear, they are not produced by a discovered recurring materializer (gap / possible leftover path).

## Invariants

- Confirm ≠ payBill / ≠ createTransaction.
- Linking is tenant-scoped via `transactionService.findById(..., accountId)`.
- Paused templates cannot be confirmed/linked.

## Permissions

Same as model: view / create / update family (see recurring-model). Frontend is UX only.

## Data And Contracts

- Mutations: `pauseRecurringPayment`, `resumeRecurringPayment`, `deleteRecurringPayment`, `confirmRecurringPayment`, `linkTransactionToRecurring`, `createRecurringPaymentFromTransaction`.
- Query: `suggestRecurringForTransaction`.

## Impact Surface

- Command service + repos + IT
- Web list/dialogs/action menu + pending-actions navigation to recurring page
- Cross: transactions FK; pending actions; snapshots only if linked/create paths create txs elsewhere (confirm alone does not)

## Decisions

- User (or link) acknowledges payment; system advances schedule.
- Pause cleans FUTURE projections even though materializer is absent today.

## Known Gaps

- No FUTURE materialization job — pause/confirm FUTURE deletes are defensive.
- No reminder-job dedicated tests / no E2E confirm/link flows / no Bruno.
- Matching ignores FA on suggestion (account-wide).

## Legacy Notes

- None used as authority.
