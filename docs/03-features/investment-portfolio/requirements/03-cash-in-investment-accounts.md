# Cash in Investment Accounts — Requirements Document

**Service:** Invistus Banking Service
**Module:** Investment Portfolio — Cash in Investment Accounts
**Document date:** 2026-04-20

---

## 1. Overview

Investment and benefit custody accounts hold two types of value:
1. **Holdings** — positions in financial instruments (shares, fixed income contracts, fund quotas, crypto).
2. **Cash** — free cash balance available in the account, not yet allocated to a position.

Cash in investment accounts is represented by `banking.transactions` entries on the `FinancialAccount` (same ledger used for bank accounts). There is no separate cash model.

The user can manually record:
- **Deposits** (`DEPOSIT`) — incoming cash to the account (e.g., a transfer from a bank account, dividends credited by the broker).
- **Withdrawals** (`WITHDRAWAL`) — outgoing cash from the account (e.g., a transfer to a bank account).
- **Adjustments** (`ADJUSTMENT`) — balance corrections.

Investment transaction side effects also generate cash ledger entries automatically (see `04-investment-performance.md` — FR-3.1).

Backend service: `InvestmentCashService.kt`
Frontend: `RecordCashDrawer.tsx`, `EditCashForm.tsx`, `CashMovementsSection.tsx`

---

## 2. Cash Balance Calculation

The cash balance of an investment account is the **net ledger balance** of all non-deleted `banking.transactions` on that account:

```
cashBalance = Σ CREDIT transactions − Σ DEBIT transactions
```

This includes:
- Manual cash deposits/withdrawals (created by `InvestmentCashService`).
- Auto-generated investment side effects (created by `CustodianCashLedgerHandler`):
  - PURCHASE/SUBSCRIPTION → DEBIT (cash leaves account to buy asset)
  - SALE/MATURITY → CREDIT (cash enters account from sale/redemption)
  - DIVIDEND/INTEREST_PAYMENT/AMORTIZATION/STOCK_LENDING → CREDIT

Cash balance can be **negative** when investment transactions are recorded without first recording an incoming deposit (common in manual entry flows where the user tracks positions but not cash movements).

---

## 3. Transaction Type Mapping

| Operation | `InvestmentCashTransactionType` | `Transaction.type` | `creditDebitType` | Category |
|---|---|---|---|---|
| Manual deposit | `DEPOSIT` | `TRANSFER` | `CREDIT` | Transferência recebida |
| Manual withdrawal | `WITHDRAWAL` | `TRANSFER` | `DEBIT` | Transferência enviada |
| Balance adjustment | `ADJUSTMENT` | `BALANCE_ADJUSTMENT` | `CREDIT` or `DEBIT` | Ajuste de saldo |
| Asset purchase (auto) | — | `TRANSFER` | `DEBIT` | Investimento aplicado > {subcategoria} |
| Asset sale/redemption (auto) | — | `INVESTMENT_RETURN` | `CREDIT` | Investimentos > Resgate |
| Dividend received (auto) | — | `INVESTMENT_RETURN` | `CREDIT` | Renda Extra > Dividendos |
| Coupon payment (auto) | — | `INVESTMENT_RETURN` | `CREDIT` | Investimentos > Pagamento de Juros |
| Amortization (auto) | — | `INVESTMENT_RETURN` | `CREDIT` | Investimentos > Amortização |
| Stock lending income (auto) | — | `INVESTMENT_RETURN` | `CREDIT` | Renda Extra > Aluguel de Ações |

---

## 4. Business Rules

### 4.1 Account Validation

- The `financialAccountId` must belong to the authenticated user's account.
- The account must have `type = INVESTMENT` or `type = BENEFIT`.
- Attempting to create a cash transaction on a non-investment account (e.g., BANK) is rejected with `INVALID_INVESTMENT_ACCOUNT_TYPE`.

### 4.2 Amount Sign

- `amount` is always stored as an absolute value (positive).
- `creditDebitType` determines whether it is a credit or debit on the ledger.
- For `DEPOSIT`: `creditDebitType = CREDIT`.
- For `WITHDRAWAL`: `creditDebitType = DEBIT`.
- For `ADJUSTMENT`: `creditDebitType` is derived from the sign of the input amount.

### 4.3 Idempotency of Auto-Generated Entries

Auto-generated cash ledger entries (from investment transaction side effects) are idempotent. Before creating a new ledger entry, `CustodianCashLedgerHandler` checks whether a transaction with `investmentTransactionId = <id>` already exists. If found, no duplicate is created.

### 4.4 Deletion Cascade

When a manual cash transaction (`source = MANUAL`) is deleted:
- The transaction is soft-deleted (`deleted_at` is set).
- If the transaction has a `transferPeerTransactionId` (linked peer in a bank-to-investment transfer), the peer is also soft-deleted.

When an investment transaction is deleted (triggering `onDelete` in `CustodianCashLedgerHandler`):
- The auto-generated banking transaction linked via `investmentTransactionId` is soft-deleted.

### 4.5 Pending Action for Cash Deposit

When an investment account has a negative cash balance (indicating investment transactions were recorded without a matching deposit), the system creates a `CASH_DEPOSIT_SUGGESTED` pending action to remind the user to record the incoming transfer.

---

## 5. User Scenarios

**Scenario 1 — Record a manual deposit**
User opens `RecordCashDrawer`, selects type "Depósito", enters an amount (e.g., R$ 50.000) and a date. Optionally adds a description. On submit, a CREDIT transaction is created on the investment account. The cash balance increases by R$ 50.000.

**Scenario 2 — Record a manual withdrawal**
User opens `RecordCashDrawer`, selects type "Saque", enters amount and date. A DEBIT transaction is created. Cash balance decreases.

**Scenario 3 — Record a balance adjustment**
User selects type "Ajuste de saldo", enters a positive or negative amount. A `BALANCE_ADJUSTMENT` transaction is created with the appropriate `creditDebitType`.

**Scenario 4 — Edit a cash transaction**
User clicks the edit icon on a cash row in `CashMovementsSection`. `EditCashForm` opens inline. The user modifies amount or description and saves. Only `source = MANUAL` transactions are editable.

**Scenario 5 — Delete a cash transaction**
User clicks the delete icon on a cash row. A confirmation dialog appears. On confirm, the transaction is soft-deleted. The cash balance is recalculated.

**Scenario 6 — Negative cash balance after recording a purchase**
User records a PURCHASE of R$ 10.000 without a prior deposit. The `CustodianCashLedgerHandler` auto-creates a DEBIT of R$ 10.000. Cash balance goes to −R$ 10.000. The portfolio total value correctly reflects the holding + negative cash. A pending action suggests the user record the incoming transfer.

**Scenario 7 — Cash destination on sale**
When recording a SALE, the user can optionally select a `cashDestinationAccountId` (a different investment account or bank account). If provided, the CREDIT is created on the destination account instead of the source account. If not provided, the CREDIT defaults to the same account as the holding.

---

## 6. Validations

| Field | Rule | Enforced in |
|---|---|---|
| `financialAccountId` | Must exist and belong to the user's account | Service layer |
| Account `type` | Must be `INVESTMENT` or `BENEFIT` | `InvestmentCashService` |
| `amount` | Must be > 0 | Service layer |
| `tradeDate` | Must not be in the future | Frontend validation |
| `source` | Only `MANUAL` transactions are editable/deletable | Service layer |

---

## 7. Open Questions / Gaps

**7.1 Inter-account transfer creation.**
When a user deposits from a bank account to an investment account, ideally both sides of the transfer are linked via `transferPeerTransactionId`. Currently the user must manually record the DEBIT in the bank account separately, or the system does not link them. A "transfer from bank account" flow with automatic peer creation is not yet implemented.

**7.2 Cash balance displayed in `InvestmentSummary` may lag.**
`cashBalance` is computed at query time by summing the ledger balance. With large transaction volumes this may become slow. No snapshot materialization exists yet for investment cash balances.
