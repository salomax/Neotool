---
title: Phase 5 — Future Transactions & Reconciliation
type: feature-spec
category: personal-finance
status: planned
version: 1.0.0
tags: [feature, personal-finance, future-transactions, reconciliation, import, overdue, planning]
ai_optimized: true
search_keywords: [future transaction, FUTURE status, overdue, unconfirmed, reconciliation, import match, planned transaction, projectedClosingBalance, closingBalance divergence]
related:
  - 03-features/personal-finance/phase-2-monthly-summary-snapshots.md
  - 03-features/personal-finance/phase-2-5-transaction-imports.md
  - 03-features/personal-finance/personal-finance-overview.md
last_updated: 2026-03-10
---

# Phase 5 — Future Transactions & Reconciliation

> **Purpose**: Define the complete lifecycle of `FUTURE`-status transactions — how they are created, how they interact with the balance model, what happens when their date passes without confirmation, and how they relate to imported bank statements. Establishes the reconciliation contract so the user always understands what is confirmed vs. planned.

---

## Overview

Invistus supports `TransactionStatus.FUTURE` as a first-class status for planned transactions. A user can create a FUTURE transaction to represent a known upcoming payment or income — the app shows it in the transaction list and factors it into the `projectedClosingBalance`.

This feature spec addresses three interrelated problems:

1. **The overdue gap** — a FUTURE transaction whose `date` has passed is no longer in the future, but it remains unconfirmed. The `closingBalance` does not reflect it, creating a divergence from the user's real bank account.
2. **The import collision** — when the user imports a bank statement, the real occurrence of a planned transaction arrives as a new POSTED entry. Without reconciliation, both exist simultaneously: the original FUTURE (unconfirmed) and the new POSTED (real).
3. **The variability problem** — planned transactions often differ slightly from reality: the amount may vary (e.g., utility bills) and the date may shift (early or late payment). Automatic matching is unreliable.

---

## Problem Statement

### Balance divergence after date passes

```
April 15 — User created: Aluguel FUTURE DEBIT R$2.500 date=2026-04-15
April 20 — User opens the app

monthSummary(april):
  closingBalance          = R$10.000  ← does NOT include the rent (it's still FUTURE)
  projectedClosingBalance = R$ 7.500  ← correct projection

Reality: the rent was paid on April 14. The bank account has R$7.487,50.
The app is displaying a closingBalance that is R$2.500 too high.
```

The divergence grows every day the FUTURE transaction remains unresolved. If the user has several overdue FUTURE entries, their apparent balance is materially wrong.

### Import collision creates duplicates

```
April 20 — User imports OFX from their bank.
Import processor creates:
  → Aluguel POSTED DEBIT R$2.487,50 date=2026-04-14  (real, from bank)

FUTURE entry still exists:
  → Aluguel FUTURE DEBIT R$2.500,00 date=2026-04-15  (unresolved)

Transaction list now shows both. User must resolve manually.
```

### Why automatic matching is unreliable

| Attribute | Planned (FUTURE) | Reality (imported POSTED) |
|---|---|---|
| Amount | R$2.500,00 | R$2.487,50 (discount applied) |
| Date | 2026-04-15 | 2026-04-14 (paid one day early) |
| Description | "Aluguel" (user-written) | "TED IMOB SILVA LTDA" (bank string) |

Matching by exact `(amount, date)` misses most real-world cases. Fuzzy matching (±5% amount, ±3 days) would produce false positives between similar recurring payments (e.g., two utility bills in the same month). Matching by description requires ML-level normalization of bank strings.

---

## Industry Research

### How major PFM apps handle planned transactions and reconciliation

| App | FUTURE transactions | Overdue signal | Import reconciliation |
|---|---|---|---|
| **YNAB** | No FUTURE status. All transactions are budgeted. Past uncleared = unreconciled indicator in account register. | Transactions stay "uncleared" with a visual badge until user clears them. Balance shows separate "cleared" vs "working" totals. | Import suggests matches based on amount+date; user confirms or dismisses. No auto-apply. |
| **Organizze** | Supports "lançamentos futuros" (future entries). Shown in the month they are scheduled. | Overdue entries highlighted in the transaction list with a warning color. Balance shows "realizado" vs "previsto". | Import does not touch existing future entries. User reconciles manually. |
| **Mobills** | Future entries supported. Color-coded by status (green=confirmed, gray=future, red=overdue). | Past-due futures show a red badge. Separate "previsto" balance shown alongside real balance. | No automatic reconciliation. Import creates new POSTED entry; user deletes the FUTURE manually. |
| **Quicken** | Full reconciliation workflow: user matches imported transactions to existing register entries one-by-one. Unmatched imports and unmatched register entries are highlighted. | "Uncleared" register entries past their due date shown with a flag. | Best-in-class: shows import candidates alongside existing entries; user clicks to match. Matched entry inherits the real amount/date. |
| **Money by Moneywiz** | Future transactions (called "scheduled") shown in register. Clear distinction between cleared and uncleared. | Badge count on overdue uncleared entries. | Semi-automatic: suggests matches; user confirms. |
| **Nubank** (native) | No manual future entries. Open Finance syncs scheduled payments. | Scheduled PIX/TED shown in feed before they occur; auto-confirmed when bank processes them. Infrastructure-driven, not applicable for multi-bank PFM. |
| **Conta Azul** (B2B PFM) | First-class "contas a pagar/receber" (payables/receivables) model. Separate from transactions until confirmed. | Overdue payables highlighted with explicit "overdue" status and aging count. | Confirmation workflow: user confirms payable → creates transaction. No import auto-match. |

### Key takeaways

1. **Dual balance display is the universal pattern**: all major apps show both a "confirmed/cleared" balance and a "projected/budgeted/previsto" balance. Users learn to interpret both. The confirmed balance is always the source of truth.
2. **Overdue visual signal without altering the balance**: no app auto-converts overdue FUTURE to POSTED or adjusts the confirmed balance. The signal is purely visual — the user decides when and how to confirm.
3. **Import reconciliation is never fully automatic**: even Quicken (the gold standard) presents suggestions that the user must confirm. The risk of false positives is too high, especially with variable amounts and shifted dates.
4. **FUTURE entries are planning tools, not ledger entries**: the confirmed balance (`closingBalance`) reflects only POSTED/PENDING reality. FUTURE entries influence only the projected balance.
5. **Manual confirmation is the correct reconciliation action**: the user sees the overdue FUTURE, opens the import, and either (a) confirms the FUTURE with the real values, or (b) deletes the FUTURE and lets the imported POSTED stand alone.

### Chosen approach

- **`closingBalance` = confirmed reality only** (POSTED + PENDING). Never modified by FUTURE status.
- **`projectedClosingBalance` = confirmed + FUTURE planned**. Displayed alongside closingBalance for the current month.
- **Overdue FUTURE = visual flag, no balance impact**. A FUTURE transaction whose `date < today` and `status = FUTURE` is labeled "Não confirmado" in the transaction list with a distinct visual treatment. The user is prompted to confirm or delete it.
- **Import creates POSTED entries without touching FUTURE entries**. No automatic matching. The user resolves collisions manually.
- **Optional reconciliation suggestion** (future enhancement): at import time, if a candidate FUTURE transaction exists for the same month, account, and approximate amount, the UI may surface a non-blocking suggestion — not an automatic action.

---

## Data Model

### No schema changes required

The `FUTURE` status already exists in `TransactionStatus`. No new columns or tables are needed for this feature.

The domain change is behavioral: the system gains a concept of an **overdue FUTURE** — a FUTURE transaction whose `date` is strictly before today.

```kotlin
// Derived property — not persisted
val isOverdue: Boolean
    get() = status == TransactionStatus.FUTURE && date.isBefore(LocalDate.now())
```

### `Transaction` domain — added derived field

| Field | Type | Source | Description |
|---|---|---|---|
| `isOverdue` | Boolean | Derived (`status == FUTURE && date < today`) | True when a planned transaction was expected to occur but has not been confirmed |

---

## Business Rules

### 1. FUTURE transactions and the balance model

| Scenario | `closingBalance` | `projectedClosingBalance` |
|---|---|---|
| Month with no FUTURE transactions | Confirmed total | `null` |
| Month with FUTURE transactions, none overdue | Confirmed total | Confirmed + planned |
| Month with FUTURE transactions, some overdue | Confirmed total (unchanged) | Confirmed + all FUTURE (including overdue) |

Overdue FUTURE transactions continue to contribute to `projectedClosingBalance` until the user resolves them. This is intentional: the projection should reflect everything unconfirmed, so the user sees the maximum expected balance impact.

### 2. Overdue FUTURE — visual signal only

A FUTURE transaction is visually overdue when:
```
transaction.status == FUTURE  AND  transaction.date < today
```

No automatic status change occurs. The system never silently converts FUTURE → POSTED, PENDING, or deleted. The user is always in control of confirmation.

Visual treatment:
- **Transaction list row**: badge "Não confirmado" with warning color (e.g., amber/yellow).
- **Badge count**: the month carousel tab (or the current month header) may show a count of overdue FUTURE entries as a prompt to review.
- **Sorting**: overdue FUTURE entries appear in their natural date position in the list, not floated to the top — they are part of the history of that date.

### 3. Confirmation flow (manual)

The user confirms a FUTURE transaction by updating it. Two paths:

**Path A — Exact match**: the planned amount and date were correct.
```
updateTransaction(id, { status: "POSTED" })
→ snapshot recomputed
→ closingBalance now includes the transaction
→ isOverdue = false
```

**Path B — Real values differ**: the import shows a different amount or date.
```
Option 1: Update the FUTURE with the real values, then mark POSTED.
  updateTransaction(id, { amount: 2487.50, date: "2026-04-14", status: "POSTED" })

Option 2: Delete the FUTURE; let the imported POSTED stand.
  deleteTransaction(futureId)
  (imported POSTED was already created by the import flow)
```

Both paths produce a clean state: one POSTED entry, no FUTURE duplicate.

### 4. Import behavior — no automatic reconciliation

The import processor (OFX, CSV, PDF) always creates new POSTED transactions. It does not:
- Look for existing FUTURE entries with matching criteria.
- Auto-convert FUTURE entries to POSTED.
- Delete or soft-delete FUTURE entries.

This is intentional. Automatic matching would require:
- Fuzzy amount comparison (±N%).
- Fuzzy date comparison (±N days).
- Description normalization (bank strings vs. user labels).

All three are unreliable enough to produce false positives that silently corrupt the user's planned data. The user must confirm explicitly.

### 5. Optional reconciliation suggestion (non-blocking)

As a UX enhancement (not blocking V1), after an import completes, the system may surface a notice:

> "Encontramos 2 lançamentos futuros que podem corresponder a movimentações importadas. Deseja revisar?"

Candidate criteria (all must match):
- Same `financialAccountId`
- Same `creditDebitType` (DEBIT or CREDIT)
- Same calendar month
- Amount within ±10% of the imported amount
- Date within ±5 calendar days

**Surfacing only** — the user clicks to review side-by-side and either confirms (Path A or B above) or dismisses. No automatic action taken.

### 6. FUTURE transactions in future months

For months strictly after today (`isFutureMonth = true`), FUTURE transactions are the **primary content** — they represent the user's planning for that month. No snapshot is persisted. The `closingBalance` equals `openingBalance` (no confirmed transactions). The `projectedClosingBalance` reflects all FUTURE entries for that month.

These FUTURE entries in future months are **not overdue** — `date >= today`. They have no warning badge.

```
today = 2026-04-20

Aluguel FUTURE DEBIT R$2.500 date=2026-04-15  → OVERDUE (date < today, same month)
Aluguel FUTURE DEBIT R$2.500 date=2026-05-15  → Not overdue (date in future month)
```

---

## UX Flows

### Current month — overdue FUTURE in transaction list

```
[April 2026 — Transaction list]

20/04  Netflix           DEBIT  R$  55,00  POSTED
17/04  Mercado           DEBIT  R$ 230,00  POSTED
15/04  Aluguel ⚠        DEBIT  R$2.500,00  [Não confirmado]  ← overdue FUTURE
05/04  Salário           CREDIT R$8.000,00  POSTED

Saldo confirmado:    R$10.714,00
Saldo projetado:     R$ 8.214,00  (after resolving the rent)
```

The user taps on "Aluguel ⚠" and sees two actions:
- **Confirmar como pago** → opens edit form pre-filled with current values; user adjusts amount/date if needed; submits with status POSTED.
- **Excluir planejamento** → deletes the FUTURE entry (use when an imported POSTED already covers it).

### Import flow — duplicate detection notice

```
[After OFX import — 12 transactions imported]

✓ 12 transações importadas com sucesso.

⚠ Atenção: encontramos 1 lançamento futuro que pode corresponder
  a uma transação importada:

  Planejado:  Aluguel   DEBIT  R$2.500,00  15/04
  Importado:  TED IMOB  DEBIT  R$2.487,50  14/04

  [Revisar →]  [Ignorar]
```

The user can:
- **Revisar**: opens a side-by-side comparison. Tapping "Confirmar correspondência" deletes the FUTURE entry (the imported POSTED stays). Tapping "Manter separados" keeps both.
- **Ignorar**: closes the notice. Both entries remain. The user resolves manually later.

---

## Summary: Confirmed vs. Projected balance semantics

| Field | Includes | Excludes | Label in UI |
|---|---|---|---|
| `closingBalance` | POSTED, PENDING | FUTURE (all) | "Saldo confirmado" |
| `projectedClosingBalance` | POSTED, PENDING, FUTURE | — | "Saldo projetado" |

The frontend should:
- Always display `closingBalance` as the primary balance figure.
- Display `projectedClosingBalance` alongside it (secondary, smaller) **only when it differs from `closingBalance`** (i.e., when FUTURE entries exist).
- Never hide the divergence. A large gap between the two is a prompt for the user to review their planned entries.

---

## Out of Scope (this phase)

- **RecurringRule entity**: repeating transactions with no end date are a separate domain (planned for Phase 6). They contribute to projections without creating FUTURE transaction rows.
- **Automatic FUTURE → POSTED conversion**: not implemented. No job or trigger auto-confirms past FUTURE entries.
- **Open Finance / Pluggy sync**: scheduled payments from Open Finance providers may eventually auto-confirm matched FUTURE entries. Handled in the Open Finance integration phase.
- **Period locking**: Quicken-style hard close of past months is not planned. All past months remain editable.
