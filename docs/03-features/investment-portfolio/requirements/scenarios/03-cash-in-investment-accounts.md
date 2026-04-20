# Test Scenarios: Cash in Investment Accounts

**Requirements ref:** `03-cash-in-investment-accounts.md`
**Document date:** 2026-04-20

---

## Manual Cash Transactions — Happy Paths

### SC-01: Record a manual deposit

**Setup:** `RecordCashDrawer` open, INVESTMENT account selected

| Field       | Value          |
|---|---|
| Type        | Depósito       |
| Amount      | 50 000,00      |
| Date        | 2026-04-01     |
| Description | Transferência TED |

**Asserts:**
- `banking.transaction` created: `type=TRANSFER, creditDebitType=CREDIT, amount=50000`
- Cash balance increases by R$ 50 000
- Entry appears in `CashMovementsSection`

---

### SC-02: Record a manual withdrawal

| Field  | Value      |
|---|---|
| Type   | Saque      |
| Amount | 5 000,00   |
| Date   | 2026-04-10 |

**Asserts:**
- `banking.transaction` created: `type=TRANSFER, creditDebitType=DEBIT, amount=5000`
- Cash balance decreases by R$ 5 000

---

### SC-03: Record a balance adjustment — positive (credit)

| Field  | Value      |
|---|---|
| Type   | Ajuste     |
| Amount | +200,00    |

**Asserts:** `type=BALANCE_ADJUSTMENT, creditDebitType=CREDIT, amount=200`

---

### SC-04: Record a balance adjustment — negative (debit)

| Field  | Value   |
|---|---|
| Type   | Ajuste  |
| Amount | −200,00 |

**Asserts:** `type=BALANCE_ADJUSTMENT, creditDebitType=DEBIT, amount=200`

---

### SC-05: Edit a cash transaction (MANUAL source only)

**Setup:** existing MANUAL deposit of R$ 50 000

**Action:** open inline `EditCashForm`, change amount to R$ 45 000

**Asserts:**
- Transaction updated with `amount=45000`
- Cash balance recalculated
- Edit option NOT available for `source=OPEN_FINANCE` or `FILE_IMPORT` entries

---

### SC-06: Delete a cash transaction — soft delete

**Setup:** MANUAL deposit of R$ 50 000

**Action:** click delete → confirm dialog → confirm

**Asserts:**
- Transaction `deleted_at` set (soft delete)
- Cash balance recalculated (deposit removed)
- Row disappears from `CashMovementsSection`

---

## Auto-Generated Cash Entries (Side Effects)

### SC-07: PURCHASE → auto DEBIT

**Trigger:** user records any PURCHASE or SUBSCRIPTION

**Asserts:**
- `CustodianCashLedgerHandler` creates: `creditDebitType=DEBIT, amount=netValue`
- Cash balance decreases by `netValue`

---

### SC-08: SALE / MATURITY → auto CREDIT

**Trigger:** user records SALE or confirms MATURITY

**Asserts:**
- `CustodianCashLedgerHandler` creates: `creditDebitType=CREDIT, amount=netValue`
- Cash balance increases by `netValue`

---

### SC-09: Income events → auto CREDIT

| Transaction type   | Cash effect                        |
|---|---|
| DIVIDEND           | CREDIT `netValue`                  |
| INTEREST_PAYMENT   | CREDIT `netValue`                  |
| AMORTIZATION       | CREDIT `netValue`                  |
| STOCK_LENDING      | CREDIT `netValue`                  |

---

### SC-10: WITHHOLDING_TAX → NO cash entry

**Trigger:** come-cotas recorded (`type=WITHHOLDING_TAX`)

**Asserts:**
- `CustodianCashLedgerHandler` does NOT create any DEBIT or CREDIT
- Account cash balance UNCHANGED

---

### SC-11: Auto-generated entries are idempotent (no duplicates)

**Setup:** PURCHASE transaction with `id=tx-123` already has a linked banking transaction

**Trigger:** lifecycle orchestrator processes `tx-123` again (retry scenario)

**Asserts:**
- `CustodianCashLedgerHandler` checks for existing entry with `investmentTransactionId=tx-123`
- No duplicate banking transaction created

---

### SC-12: Deleting an investment transaction soft-deletes its linked cash entry

**Setup:** PURCHASE with linked DEBIT banking transaction

**Action:** user deletes the PURCHASE investment transaction

**Asserts:**
- Linked DEBIT banking transaction `deleted_at` set
- Cash balance restored (DEBIT removed)

---

## Cash Balance Calculation

### SC-13: Cash balance = net ledger balance (CREDIT − DEBIT)

**Setup:** account with:
- Deposit R$ 50 000 (CREDIT)
- PURCHASE R$ 10 000 (DEBIT — auto)
- Dividend R$ 500 (CREDIT — auto)

**Expected cash balance:** `50000 − 10000 + 500 = R$ 40 500`

---

### SC-14: Cash balance can be negative (no floor)

**Setup:** user records R$ 10 000 PURCHASE without any prior deposit

**Asserts:**
- Auto DEBIT R$ 10 000 created
- Cash balance = −R$ 10 000 (negative is valid)
- Portfolio total = holding value − R$ 10 000 (negative cash correctly reflected)
- `CASH_DEPOSIT_SUGGESTED` pending action created to remind user

---

### SC-15: Consolidated view aggregates cash from all accounts

**Setup:** Account A cashBalance=30 000; Account B cashBalance=−5 000

**Asserts:** consolidated `cashBalance = 25 000`

---

## Cash Destination on Transactions

### SC-16: SALE / income events can credit a different account

**Setup:** recording SALE on "XP Corretora", user selects "Conta Corrente Nubank" as cash destination

**Asserts:**
- CREDIT R$ netValue created on "Conta Corrente Nubank" (NOT on XP)
- XP cash balance unchanged

---

### SC-17: Default cash destination is the same custodian account

**Setup:** recording SALE without selecting a destination

**Asserts:** CREDIT created on same account as the holding

---

## Account Validation

### SC-18: Cash transactions rejected for BANK accounts

**Setup:** attempt to call `InvestmentCashService` with a BANK account `financialAccountId`

**Asserts:** error `INVALID_INVESTMENT_ACCOUNT_TYPE` returned

---

### SC-19: Cash transactions accepted for BENEFIT accounts

**Setup:** FGTS account (`type=BENEFIT`)

**Asserts:** deposit/withdrawal accepted; cash balance updated

---

## Validation — Negative Cases

| SC    | Trigger                        | Expected error                             | Saved? |
|---|---|---|---|
| SC-20 | Amount = 0                    | "Valor deve ser maior que zero"             | No     |
| SC-21 | Date in the future            | "Data não pode ser futura"                  | No     |
| SC-22 | Edit non-MANUAL transaction   | Edit option not available                   | N/A    |
| SC-23 | Delete non-MANUAL transaction | Delete option not available                 | N/A    |
