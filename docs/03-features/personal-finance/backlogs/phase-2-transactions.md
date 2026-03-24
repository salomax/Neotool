---
title: Phase 2 — Transactions
type: milestone
category: personal-finance
status: planned
version: 1.0.0
tags: [feature, personal-finance, transactions, categories, open-finance, brazil, parcelamento, fatura]
ai_optimized: true
search_keywords: [transactions, categories, spending, income, transfer, parcelamento, fatura, bill, credit card, pix, boleto, balance]
related:
  - 03-features/personal-finance/personal-finance-overview.md
  - 03-features/personal-finance/phase-1-accounts-cards.md
  - 03-features/personal-finance/market-research-2026.md
last_updated: 2026-02-27
---

# Phase 2 — Transactions

> **Purpose**: Deliver full transaction CRUD (income, expense, transfer) with manual entry, category management, credit card bill (fatura) grouping, and a real-time account balance derived from transaction history. The data model is aligned with Open Finance Brasil Phase 2 (`AccountTransactionsData` + `CreditCardAccountsTransaction`) and the Pluggy aggregator API to minimize future integration effort (Phase 5).

## Overview

Transactions are the core financial data of the personal finance module. Every account balance is derived from its transaction history — never stored as a mutable field. This design ensures ledger integrity and is the standard used by YNAB, Pluggy, and OFB.

Phase 2 delivers:
- **Transaction CRUD** — create, list, edit, delete for income, expenses, and transfers
- **Category management** — system seed categories (OFB/Pluggy-aligned taxonomy, BR-adapted) + user-defined subcategories
- **Credit card bill grouping** — linking transactions to their monthly statement (fatura)
- **Parcelamento tracking** — installment purchase tracking per card (uniquely Brazilian)
- **Balance computation** — real-time derived balance per account and aggregate across accounts
- **Balance adjustment transaction** — special neutral type (`BALANCE_ADJUSTMENT`) used both for setting an opening balance and for future account reconciliation; not classified as income, expense, or transfer

## Design References

- **Open Finance Brasil Phase 2** — `AccountTransactionsData` (payment accounts) and `CreditCardAccountsTransaction` (credit cards) schemas
- **Pluggy API** — category taxonomy, enrichment fields, and payment data model
- **Market research** — see [market-research-2026.md](market-research-2026.md) for full competitive analysis
- **Competitors analyzed**: Organizze, Mobills, Minhas Economias, YNAB, Monarch Money, Copilot Money, Nubank, Neon

## Scope

**In scope**
- Backend: transactions model and CRUD, category model and seed, credit card bills model
- Balance computation: derived from transaction history (no mutable balance field)
- Balance adjustment transaction: `BALANCE_ADJUSTMENT` type for opening balance and account reconciliation
- Category CRUD: system (seed) categories + user-defined custom categories and subcategories
- Parcelamento: installment number + total installments on credit card transactions
- Credit card bill (fatura): `credit_card_bills` table with closing/due dates; transactions linked to bill
- Transfer between own accounts: double-entry (two transaction rows) with peer linkage
- Frontend: transaction list, add/edit transaction form, category picker, account balance display
- GraphQL: full query and mutation surface for all new types

**Out of scope**
- Open Finance sync / Pluggy integration (Phase 5)
- AI auto-categorization (Phase 3)
- Recurring transactions / scheduled entries (Phase 3)
- Spending insights and reports (Phase 3)
- Budget / envelope tracking (Phase 4)
- Cash flow projections (Phase 3)
- Attachment / receipt upload (Phase 4)

---

## Data Model

### Naming Convention

| Domain concept | Brazilian term | Table column |
|---|---|---|
| Transaction | Transação / Lançamento | `banking.transactions` |
| Category | Categoria | `banking.transaction_categories` |
| Credit card bill | Fatura | `banking.credit_card_bills` |
| Installment | Parcela | `installment_number` / `total_installments` |
| Posting date | Data de lançamento | `date` |

---

### `banking.transaction_categories`

System-seed categories (from OFB/Pluggy taxonomy, BR-adapted) plus user-defined categories per account (tenant).

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID v7 PK | Yes | |
| `account_id` | UUID FK → security.accounts | No | NULL = global/system category |
| `parent_id` | UUID FK → self | No | NULL = root category |
| `name` | VARCHAR(100) | Yes | Display name |
| `applicable_to` | VARCHAR CHECK IN ('INCOME','EXPENSE','TRANSFER','ANY') | Yes | Filters category in income/expense forms |
| `icon` | VARCHAR(50) | No | Emoji or icon key |
| `color` | VARCHAR(7) | No | Hex color (e.g. `#4CAF50`) |
| `pluggy_category_id` | VARCHAR(100) | No | Pluggy category ID for future sync mapping |
| `is_system` | BOOLEAN NOT NULL DEFAULT false | Yes | True = seed; cannot be deleted by user |
| `sort_order` | INT NOT NULL DEFAULT 0 | Yes | Display order |
| `deleted_at` | TIMESTAMPTZ | No | Soft delete (user-created only) |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |

**Constraints:**
- System categories (`is_system = true`) may not be deleted
- User-created categories (`account_id IS NOT NULL`) may be soft-deleted
- `parent_id` may only reference categories with `parent_id IS NULL` (max 2 levels)

---

### `banking.credit_card_bills`

One row per monthly credit card statement (fatura). Transactions link to bills via `bill_id`.

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID v7 PK | Yes | |
| `financial_account_id` | UUID FK → banking.financial_accounts | Yes | Must be CREDIT/CHARGE_CARD subtype |
| `account_id` | UUID FK → security.accounts | Yes | Tenant |
| `closing_date` | DATE | Yes | Billing cycle close date (dia de fechamento) |
| `due_date` | DATE | Yes | Payment due date (dia de vencimento) |
| `total_amount` | NUMERIC(18,4) | No | Total statement balance |
| `minimum_payment` | NUMERIC(18,4) | No | Minimum payment amount |
| `status` | VARCHAR CHECK IN ('OPEN','CLOSED','PAID','OVERDUE') NOT NULL DEFAULT 'OPEN' | Yes | |
| `provider_bill_id` | VARCHAR(100) | No | OFB `billId` (Phase 5) |
| `deleted_at` | TIMESTAMPTZ | No | |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |

**Constraints:**
- Unique `(financial_account_id, closing_date)`
- `total_amount` is derived from transactions (may be materialized as a computed value)

---

### `banking.monthly_balance_snapshots`

Pre-computed monthly summary cache per financial account. Serves the monthly summary header (entradas, saídas, saldo total) without scanning all historical transactions. Updated lazily via dirty flag when past transactions change.

| Column | Type | Required | Notes |
|---|---|---|---|
| `financial_account_id` | UUID FK → banking.financial_accounts | Yes | PK component |
| `account_id` | UUID FK → security.accounts | Yes | Tenant |
| `year` | INT NOT NULL | Yes | Calendar year, e.g. 2026. PK component |
| `month` | INT NOT NULL CHECK (month BETWEEN 1 AND 12) | Yes | 1–12. PK component |
| `opening_balance` | NUMERIC(18,4) NOT NULL | Yes | Balance at start of month = prior month's `closing_balance` |
| `closing_balance` | NUMERIC(18,4) NOT NULL | Yes | Balance at end of month = `opening_balance` + net transactions |
| `total_income` | NUMERIC(18,4) NOT NULL DEFAULT 0 | Yes | SUM of CREDIT excl. TRANSFER & BALANCE_ADJUSTMENT |
| `total_expenses` | NUMERIC(18,4) NOT NULL DEFAULT 0 | Yes | SUM of DEBIT excl. TRANSFER & BALANCE_ADJUSTMENT |
| `is_dirty` | BOOLEAN NOT NULL DEFAULT false | Yes | True = needs recompute; set when a transaction in this month or any prior month changes |
| `computed_at` | TIMESTAMPTZ | No | Timestamp of last successful computation; NULL = never computed |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |

**Primary Key:** `(financial_account_id, year, month)`

**Constraints:**
- `month` must be between 1 and 12
- `is_dirty = true` acts as a cache invalidation signal; the next read triggers recomputation

> **Phase 2 note:** The snapshot table is defined and migrated in Phase 2 but population is via on-demand background computation. The API falls back to full on-demand aggregation when no snapshot exists (is_dirty or NULL). See [Balance Computation Strategy](#balance-computation-strategy) for the full lifecycle.

---

### `banking.transactions`

The main ledger table. Manual-first, OFB-ready. All Open Finance fields are nullable — zero migration cost on Phase 5 integration.

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID v7 PK | Yes | |
| `financial_account_id` | UUID FK → banking.financial_accounts | Yes | Which account this transaction belongs to |
| `account_id` | UUID FK → security.accounts | Yes | Tenant |
| `created_by_user_id` | UUID FK → security.users | Yes | Audit — who created this transaction |
| **— Classification —** | | | |
| `credit_debit_type` | VARCHAR CHECK IN ('CREDIT','DEBIT') NOT NULL | Yes | Direction. Maps to OFB `creditDebitType` |
| `type` | VARCHAR NOT NULL | Yes | See `TransactionType` enum below |
| `status` | VARCHAR CHECK IN ('POSTED','PENDING','FUTURE') NOT NULL DEFAULT 'POSTED' | Yes | Maps to OFB `completedAuthorisedPaymentType` |
| `source` | VARCHAR CHECK IN ('MANUAL','OPEN_FINANCE','PLUGGY') NOT NULL DEFAULT 'MANUAL' | Yes | Prevents re-import duplicates on Phase 5 |
| **— Amount —** | | | |
| `amount` | NUMERIC(18,4) NOT NULL | Yes | Always positive; direction from `credit_debit_type` |
| `currency_code` | VARCHAR(3) NOT NULL DEFAULT 'BRL' | Yes | ISO 4217 |
| `amount_brl` | NUMERIC(18,4) | No | BRL equivalent for FX; maps to OFB `brazilianAmount` |
| **— Dates —** | | | |
| `date` | DATE NOT NULL | Yes | Effective/post date shown to user |
| `transaction_datetime` | TIMESTAMPTZ | No | Precise timestamp; maps to OFB `transactionDateTime` |
| **— Description —** | | | |
| `description` | VARCHAR(500) NOT NULL | Yes | User-facing; editable |
| `description_raw` | VARCHAR(500) | No | Original from institution — immutable after OFB sync |
| `notes` | TEXT | No | User freeform notes |
| **— Category —** | | | |
| `category_id` | UUID FK → banking.transaction_categories | No | Nullable; populated by user or AI (Phase 3) |
| **— Counterparty (bank account transactions) —** | | | |
| `counterparty_name` | VARCHAR(200) | No | Payer/receiver name; maps to OFB `partieName` |
| `counterparty_document` | VARCHAR(14) | No | CPF (11 digits) or CNPJ (14 digits) |
| `counterparty_bank_code` | VARCHAR(3) | No | COMPE code; maps to OFB `partieCompeCode` |
| `counterparty_account_number` | VARCHAR(20) | No | Maps to OFB `partieNumber` |
| `payment_reason` | VARCHAR(500) | No | Payer motive (PIX `reason` / Pluggy `reason`) |
| **— Transfer between own accounts —** | | | |
| `transfer_peer_transaction_id` | UUID FK → banking.transactions | No | The other leg of an internal transfer; both rows reference each other |
| **— Credit Card —** | | | |
| `bill_id` | UUID FK → banking.credit_card_bills | No | Which statement (fatura) this belongs to |
| `installment_number` | INT | No | Current installment; maps to OFB `chargeIdentificator` |
| `total_installments` | INT | No | Total installments; maps to OFB `chargeNumber` |
| `payee_mcc` | INT | No | Merchant Category Code; maps to OFB `payeeMCC` |
| `card_identifier` | VARCHAR(100) | No | Last 4 digits of card; maps to OFB `identificationNumber` |
| `bill_post_date` | DATE | No | Date posted to statement; maps to OFB `billPostDate` |
| **— OFB Sync (Phase 5) —** | | | |
| `provider_transaction_id` | VARCHAR(100) | No | OFB `transactionId` / Pluggy `id` — used for dedup |
| `provider_account_id` | VARCHAR(100) | No | OFB account ID |
| **— Soft delete —** | | | |
| `deleted_at` | TIMESTAMPTZ | No | |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |

**Indexes:**
```sql
CREATE INDEX ON banking.transactions (financial_account_id, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ON banking.transactions (account_id, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ON banking.transactions (bill_id) WHERE bill_id IS NOT NULL;
CREATE INDEX ON banking.transactions (transfer_peer_transaction_id) WHERE transfer_peer_transaction_id IS NOT NULL;
CREATE INDEX ON banking.transactions (provider_transaction_id) WHERE provider_transaction_id IS NOT NULL;
```

---

## Enums

### `TransactionType`

Aligned with OFB `EnumTransactionTypes` + Pluggy `operationType`, extended for manual entry UX.

```
-- OFB-aligned payment types --
PIX                     — PIX transfer (in or out)
TED                     — TED bank transfer
DOC                     — DOC bank transfer
BOLETO                  — Boleto payment
TRANSFER                — Internal or external generic transfer

-- Income types --
SALARY                  — Payroll / salary (OFB: FOLHA_PAGAMENTO)
DEPOSIT                 — Generic deposit
CASHBACK                — Cashback / rewards credit
INVESTMENT_RETURN       — Return from investment redemption (OFB: RESGATE_APLIC_FINANCEIRA)
OTHER_INCOME            — Other income (OFB: OUTROS)

-- Expense types --
CARD                    — Card purchase (debit or credit) (OFB: CARTAO)
WITHDRAWAL              — Cash withdrawal / ATM (OFB: SAQUE)
FEE                     — Bank or card fee (OFB: TARIFA_SERVICOS_AVULSOS)
INTEREST                — Interest charge (OFB: ENCARGOS_JUROS_CHEQUE_ESPECIAL)
LOAN_PAYMENT            — Loan or financing payment (OFB: OPERACAO_CREDITO)
TAX                     — Taxes and levies (IOF, DARF, etc.)
OTHER_EXPENSE           — Other expense (OFB: OUTROS)

-- Neutral / Lifecycle --
BALANCE_ADJUSTMENT      — Sets a balance checkpoint; neither income nor expense nor transfer.
                          Used for: (1) setting opening balance when account is created,
                          (2) reconciling account balance at any point in time.
                          Amount is absolute target balance; system computes required CREDIT or DEBIT delta.
```

### `TransactionStatus`

```
POSTED      — Confirmed/cleared transaction (OFB: TRANSACAO_EFETIVADA)
PENDING     — In processing (OFB: TRANSACAO_PROCESSANDO)
FUTURE      — Scheduled future posting (OFB: LANCAMENTO_FUTURO)
```

### `TransactionSource`

```
MANUAL          — Entered by user
OPEN_FINANCE    — Synced via OFB API (Phase 5)
PLUGGY          — Synced via Pluggy aggregator (Phase 5)
```

---

## Category Seed Data

System categories (`is_system = true, account_id = NULL`) — seeded in migration. Aligned with Pluggy taxonomy, translated and adapted for the Brazilian market.

| Category | `applicable_to` | Subcategories |
|---|---|---|
| **Salário** | INCOME | — |
| **Renda Extra** | INCOME | Freela, Dividendos, Cashback, Outros |
| **Aposentadoria / Pensão** | INCOME | — |
| **Investimentos** | INCOME | Rendimento, Resgate |
| **Transferência recebida** | INCOME | PIX, TED, Outros |
| **Alimentação** | EXPENSE | Supermercado, Restaurante, Delivery, Lanchonete |
| **Moradia** | EXPENSE | Aluguel, Condomínio, IPTU, Agua, Luz, Gás |
| **Transporte** | EXPENSE | Combustível, Pedágio, Estacionamento, Uber/Taxi, Transporte Público, IPVA, Manutenção |
| **Saúde** | EXPENSE | Farmácia, Médico/Consulta, Dentista, Plano de Saúde, Academia |
| **Educação** | EXPENSE | Mensalidade, Curso Online, Material Escolar |
| **Lazer e Entretenimento** | EXPENSE | Cinema/Teatro, Streaming, Games, Viagem |
| **Compras** | EXPENSE | Roupas, Eletrônicos, Casa, Pet |
| **Serviços Digitais** | EXPENSE | Streaming Vídeo, Streaming Música, Software/Apps |
| **Telecomunicações** | EXPENSE | Internet, Celular, TV a Cabo |
| **Seguros** | EXPENSE | Seguro Auto, Seguro Vida, Seguro Residência, Plano de Saúde |
| **Impostos** | EXPENSE | IR, IOF, IPVA, IPTU, DAS (MEI) |
| **Tarifas Bancárias** | EXPENSE | Tarifa Conta, Anuidade Cartão, Tarifa Transferência |
| **Empréstimos e Financiamentos** | EXPENSE | Empréstimo Pessoal, Financiamento Imóvel, Financiamento Veículo |
| **Doações** | EXPENSE | — |
| **Transferência enviada** | TRANSFER | PIX, TED, Entre contas próprias |
| **Investimento aplicado** | TRANSFER | Renda Fixa, Fundos, Ações, Previdência |
| **Outros** | ANY | — |

> **Rationale for BR adaptation**: Pluggy uses English taxonomy internally; we seed in Portuguese for user-facing display. `pluggy_category_id` column maps each seed to the Pluggy ID for future auto-categorization (Phase 3/5).

---

## Business Rules

### 1. Balance is always derived (never stored)

Account balance = `SUM(amount) WHERE credit_debit_type = 'CREDIT'` minus `SUM(amount) WHERE credit_debit_type = 'DEBIT'`, for non-deleted transactions in `POSTED` or `PENDING` status.

**No `current_balance` mutable field exists on `banking.financial_accounts`.** This is the YNAB and Pluggy standard. Benefits: ledger integrity, no sync issues, correct after any edit or delete.

> **Future optimization**: if query performance degrades at scale (> 50k transactions per account), a materialized running balance can be added as a separate table with a DB trigger — without changing the API contract.

### 2. Balance adjustment transaction

`BALANCE_ADJUSTMENT` is a neutral transaction type used in two scenarios:

**a) Opening balance (account creation)**

When a user creates an account and provides a starting balance, the frontend creates a `BALANCE_ADJUSTMENT` transaction immediately after account creation:

```
financial_account_id = <new account id>
type                 = BALANCE_ADJUSTMENT
credit_debit_type    = CREDIT  (if balance > 0)  |  DEBIT (if balance < 0)
amount               = ABS(target balance)
description          = "Saldo inicial"
date                 = account creation date
status               = POSTED
source               = MANUAL
```

No `initialBalance` field is needed on `CreateFinancialAccountCommand` — the frontend calls `createTransaction` separately. This keeps the account creation contract clean.

**b) Account reconciliation**

At any time, the user can "reconcile" — set the account balance to match the real-world balance. The service computes:

```
delta = target_balance - current_derived_balance
if delta > 0: create CREDIT BALANCE_ADJUSTMENT with amount = delta
if delta < 0: create DEBIT  BALANCE_ADJUSTMENT with amount = ABS(delta)
```

Both uses are auditable in the transaction history with a distinct type, clearly separate from income and expenses.

### 3. Internal transfer — double-entry

A transfer between two of the user's own accounts creates **two transaction rows**:

| Field | Debit leg (source) | Credit leg (destination) |
|---|---|---|
| `financial_account_id` | source account | destination account |
| `credit_debit_type` | `DEBIT` | `CREDIT` |
| `type` | `TRANSFER` | `TRANSFER` |
| `amount` | transfer amount | transfer amount |
| `description` | "Transferência para [dest]" | "Transferência de [source]" |
| `transfer_peer_transaction_id` | → destination row | → source row |

Both rows are created in the same transaction (DB transaction). Deleting one automatically deletes the other.

### 4. Parcelamento (installment purchases)

When a credit card purchase is made in installments (compra parcelada):
- Each installment appears as a separate monthly transaction (one per month)
- All share the same purchase description + `total_installments`
- `installment_number` identifies which installment (1 of N, 2 of N, …)
- Each installment links to the corresponding monthly `bill_id`

Manual entry flow: user enters **per-installment amount** (valor da parcela) + number of installments → system creates N transaction rows across N future bill cycles. Each transaction has `amount = per-installment amount`, `status = POSTED` for the current month and `FUTURE` for subsequent months.

> **BR context**: Parcelamento is endemic to Brazil; the vast majority of credit card purchases are split. No global PFM app handles this natively. This is a key differentiator.

### 5. Credit card bill (fatura) lifecycle

Bills are created automatically when the first transaction for a period posts to a credit card:
1. System determines `closing_date` and `due_date` from `FinancialAccount.cardData.closingDay` + `dueDay`
2. Creates `credit_card_bills` row with `status = OPEN`
3. Transaction is linked via `bill_id`
4. On `closing_date`: bill transitions to `CLOSED`; `total_amount` is computed
5. On payment: bill transitions to `PAID`
6. If unpaid after `due_date`: `OVERDUE`

### 6. `source` for deduplication (Phase 5 readiness)

On OFB sync, `provider_transaction_id` + `source` form a natural deduplication key:
```sql
ON CONFLICT (financial_account_id, provider_transaction_id) WHERE source != 'MANUAL' DO UPDATE ...
```
Manual transactions (`source = MANUAL`) are never overwritten by sync.

### 7. Monthly summary — entradas, saídas, saldo total

The monthly summary header shows three figures for the selected month:

| Label | Definition |
|---|---|
| **Entradas** (`totalIncome`) | SUM of CREDIT transactions WHERE type NOT IN (`TRANSFER`, `BALANCE_ADJUSTMENT`) AND status IN (`POSTED`, `PENDING`) |
| **Saídas** (`totalExpenses`) | SUM of DEBIT transactions WHERE type NOT IN (`TRANSFER`, `BALANCE_ADJUSTMENT`) AND status IN (`POSTED`, `PENDING`) |
| **Saldo total** (`closingBalance`) | Full derived balance as of the last day of the selected month, including TRANSFER and BALANCE_ADJUSTMENT, excluding FUTURE |

**Why TRANSFER and BALANCE_ADJUSTMENT are excluded from entradas/saídas:**
- Internal transfers are a reallocation between own accounts, not real income or expense. Including them would double-count in "all accounts" view and distort cash-flow analysis in single-account view.
- BALANCE_ADJUSTMENT is a reconciliation entry; it represents no actual cash flow.
- Both types still affect `closingBalance` because they change the real account balance.

**`FUTURE` transactions:**
- Excluded from `closingBalance` (same rule as current balance — no commitment until posted).
- A separate `projectedClosingBalance` may optionally include FUTURE transactions for a "projeção" indicator.

**"All accounts" aggregated view:**
- `totalIncome` and `totalExpenses` sum across all accounts, TRANSFER excluded, so no double-counting.
- `closingBalance` = sum of `closingBalance` across all debit/payment accounts, minus sum of `outstanding` across all credit card accounts (net worth view).

---

## GraphQL Schema (new types)

```graphql
enum TransactionType {
  PIX TED DOC BOLETO TRANSFER
  SALARY DEPOSIT CASHBACK INVESTMENT_RETURN OTHER_INCOME
  CARD WITHDRAWAL FEE INTEREST LOAN_PAYMENT TAX OTHER_EXPENSE
  BALANCE_ADJUSTMENT
}

enum CreditDebitType { CREDIT DEBIT }
enum TransactionStatus { POSTED PENDING FUTURE }
enum BillStatus { OPEN CLOSED PAID OVERDUE }

type TransactionCategory {
  id: ID!
  parentId: ID
  name: String!
  applicableTo: String!      # INCOME | EXPENSE | TRANSFER | ANY
  icon: String
  color: String
  isSystem: Boolean!
  subcategories: [TransactionCategory!]!
}

type CreditCardBill {
  id: ID!
  financialAccountId: ID!
  closingDate: String!
  dueDate: String!
  totalAmount: Float
  minimumPayment: Float
  status: BillStatus!
}

type Transaction {
  id: ID!
  financialAccount: FinancialAccount!
  creditDebitType: CreditDebitType!
  type: TransactionType!
  status: TransactionStatus!
  amount: Float!
  currencyCode: String!
  date: String!
  description: String!
  notes: String
  category: TransactionCategory
  # Credit card
  bill: CreditCardBill
  installmentNumber: Int
  totalInstallments: Int
  payeeMCC: Int
  # Transfer
  transferPeer: Transaction
  # Counterparty
  counterpartyName: String
  paymentReason: String
  createdAt: String!
  updatedAt: String!
}

type TransactionPage {
  items: [Transaction!]!
  total: Int!
  hasNextPage: Boolean!
}

type MonthSummary {
  year: Int!
  month: Int!
  # Core display fields (shown in summary header)
  totalIncome: Float!           # Entradas — CREDIT excl. TRANSFER & BALANCE_ADJUSTMENT
  totalExpenses: Float!         # Saídas — DEBIT excl. TRANSFER & BALANCE_ADJUSTMENT
  closingBalance: Float!        # Saldo total — full derived balance at end of month (excl. FUTURE)
  openingBalance: Float!        # Balance at start of month (= prior month's closingBalance)
  # Optional breakdown
  transfersIn: Float!           # CREDIT of type TRANSFER (for single-account view)
  transfersOut: Float!          # DEBIT of type TRANSFER (for single-account view)
  projectedClosingBalance: Float # closingBalance + FUTURE transactions (projeção)
  isCurrentMonth: Boolean!      # True if year+month = current calendar month
}

# Queries
type Query {
  transactions(
    financialAccountId: ID
    dateFrom: String
    dateTo: String
    type: TransactionType
    creditDebitType: CreditDebitType
    categoryId: ID
    status: TransactionStatus
    search: String
    first: Int
    after: String
  ): TransactionPage!

  transaction(id: ID!): Transaction

  transactionCategories(
    applicableTo: String     # filter by INCOME/EXPENSE/TRANSFER/ANY
    includeSystem: Boolean   # default true
  ): [TransactionCategory!]!

  creditCardBills(financialAccountId: ID!): [CreditCardBill!]!

  monthSummary(
    financialAccountId: ID   # null = aggregate across all accounts owned by the tenant
    year: Int!
    month: Int!
  ): MonthSummary!
}

# Mutations
type Mutation {
  createTransaction(input: CreateTransactionInput!): Transaction!
  updateTransaction(id: ID!, input: UpdateTransactionInput!): Transaction!
  deleteTransaction(id: ID!): Boolean!

  createTransfer(input: CreateTransferInput!): CreateTransferResult!

  createTransactionCategory(input: CreateCategoryInput!): TransactionCategory!
  updateTransactionCategory(id: ID!, input: UpdateCategoryInput!): TransactionCategory!
  deleteTransactionCategory(id: ID!): Boolean!
}

input CreateTransactionInput {
  financialAccountId: ID!
  creditDebitType: CreditDebitType!
  type: TransactionType!
  amount: Float!
  currencyCode: String
  date: String!
  description: String!
  notes: String
  categoryId: ID
  # Credit card
  billId: ID
  installmentNumber: Int
  totalInstallments: Int
  # Counterparty
  counterpartyName: String
  paymentReason: String
}

input CreateTransferInput {
  fromAccountId: ID!
  toAccountId: ID!
  amount: Float!
  date: String!
  description: String
  notes: String
}

type CreateTransferResult {
  debitTransaction: Transaction!
  creditTransaction: Transaction!
}

input UpdateTransactionInput {
  description: String
  notes: String
  categoryId: ID
  date: String
  amount: Float
}
```

---

## Permissions

New permissions to register in security service:

| Permission | Description |
|---|---|
| `banking:transaction:view` | List transactions for the account's financial accounts |
| `banking:transaction:create` | Create transactions (income, expense, transfer) |
| `banking:transaction:update` | Edit own transactions |
| `banking:transaction:delete` | Soft-delete transactions |
| `banking:category:view` | View categories (system + custom) |
| `banking:category:manage` | Create, update, delete custom categories |

All permissions are assigned to the "Banking User" role (via migration).

---

## UI Requirements

### Transaction List

- Full-width scrollable list, grouped by **date** (today, yesterday, day-of-week, then date); ordered `date DESC, created_at DESC` within each day
- Per-group: date header + running balance at end of day
- Per-transaction row: category icon | description | counterparty (if any) | amount (green for CREDIT, red for DEBIT) | installment badge (e.g. "2/6") if parcelado
- Filter bar (sticky): All / Income / Expense / Transfer + date range picker + account selector
- Search: full-text over `description` + `counterparty_name`
- Empty state: illustration + "Adicione sua primeira transação"

### Add / Edit Transaction

Two-tab form: **Despesa / Receita** (Expense / Income) — tab switches `credit_debit_type`. Transfer is a separate flow.

Fields:
1. **Amount** — large numeric input, front-and-center
2. **Description** — text input with suggestions from recent transactions
3. **Date** — date picker (default: today)
4. **Category** — categorypicker with icons (section: Despesas or Receitas filtered by tab)
5. **Account** — account selector (defaults to active account)
6. **Notes** — optional collapsible field
7. For credit card accounts:
   - **Parcelas** — installment count picker (1x, 2x … 12x)
   - **Fatura** — auto-assigned based on date; user can override

### Category Picker

- Full-screen modal / bottom sheet
- Two columns of category tiles with icon + name
- Section headers: Mais usadas / Alimentação / Moradia / etc.
- "Nova categoria" button at bottom
- Recent categories shown at top

### Credit Card Bill View (Fatura)

- Accessed via card account page
- Shows: current open bill total, closing date, due date, transactions grouped by category
- Previous bills in collapsible list with status badge (PAID ✓ / OVERDUE !)

### Account Balance Display

- Shown on each account card in the carousel (Phase 1 `CardBadge`)
- **Derived in real-time**: `GET /financialAccounts` returns computed `balance` field
- For credit cards: shows **outstanding balance** (sum of open bill + unbilled transactions), not credit limit

---

## Balance Computation

### Current Balance (per account)

Balance is a **derived field computed at query time** — no mutable `current_balance` column exists on `banking.financial_accounts`:

```
bank / payment / cash account:
  balance = SUM(amount WHERE credit_debit_type = 'CREDIT' AND deleted_at IS NULL AND status IN ('POSTED','PENDING'))
          - SUM(amount WHERE credit_debit_type = 'DEBIT'  AND deleted_at IS NULL AND status IN ('POSTED','PENDING'))

credit card:
  outstanding = SUM(amount WHERE credit_debit_type = 'DEBIT' AND deleted_at IS NULL AND status IN ('POSTED','PENDING'))
              - SUM(amount WHERE credit_debit_type = 'CREDIT' AND type != 'BALANCE_ADJUSTMENT' AND deleted_at IS NULL)
  (outstanding = what the user owes on the card)
```

`FUTURE` status transactions are shown in the list but **excluded from the current balance**. A separate `projectedBalance` field (sum including FUTURE) may be returned alongside `balance` for display in a "projeção" indicator.

Manual FUTURE transactions are **allowed in Phase 2** — the user can register a known future expense or income (e.g., a scheduled rent payment or salary). Recurring/repeating transactions (same transaction auto-generated on a schedule) are a distinct feature deferred to **Phase 3**.

---

### Balance Computation Strategy — Snapshots

> Full design, implementation plan, and rationale in [phase-2-monthly-summary-snapshots.md](phase-2-monthly-summary-snapshots.md).

The `monthly_balance_snapshots` table (defined above in Data Model) acts as a read cache for `monthSummary`. The key design decisions:

**Eager synchronous recompute on write** — when any transaction is created, updated, or deleted, the service immediately recomputes the snapshot chain from the affected month through the current month, within the same request. This eliminates any stale-data window.

**`is_dirty` as safety net only** — if the chain recompute fails partway (e.g., DB error mid-chain), remaining months are marked `is_dirty = true`. The read path heals dirty snapshots before returning data. A background sweeper (runs every 15 minutes) heals dirty snapshots proactively.

**Category/tag breakdowns** — not stored in the snapshot. Always computed on-demand via a `GROUP BY` query scoped to one month. Fast enough at MVP scale.

**Computation per month:**
```
opening_balance = prior month's closing_balance, or 0 at account genesis

closing_balance = opening_balance
                + SUM(CREDIT amounts, status IN (POSTED, PENDING), deleted_at IS NULL)
                - SUM(DEBIT  amounts, status IN (POSTED, PENDING), deleted_at IS NULL)

total_income    = SUM(CREDIT WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT), ...)
total_expenses  = SUM(DEBIT  WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT), ...)
```

---

## Brazilian-Specific Requirements

| Requirement | Implementation |
|---|---|
| **Parcelamento** | `installment_number` + `total_installments` on transaction; N rows created for N installments; badge shown in list |
| **Fatura / bill cycle** | `credit_card_bills` table; auto-created per billing cycle; `bill_id` links transactions |
| **PIX metadata** | `counterparty_name`, `counterparty_document`, `payment_reason` fields capture Pix rich data |
| **Boleto** | `type = BOLETO`; barcode stored in `description_raw` on OFB sync |
| **VOUCHER_CARD** | Subtype on `financial_accounts`; maps to specific categories (Alimentação / Refeição / Cultura) |
| **IOF on FX** | `currency_code` != BRL → `amount_brl` field captures BRL equivalent; IOF categorized under `TAX` |
| **Saldo negativo** | Allowed for overdraft / cheque especial accounts; no validation block |

---

## Error Handling

| Scenario | Error |
|---|---|
| `amount <= 0` | `INVALID_AMOUNT` |
| `financialAccountId` not found or not owned by account | `ACCOUNT_NOT_FOUND` |
| `categoryId` not found or not owned by account | `CATEGORY_NOT_FOUND` |
| Transfer: `fromAccountId == toAccountId` | `SAME_ACCOUNT_TRANSFER` |
| Transfer: amount exceeds available balance (warn, not block) | Warning in response |
| Delete `BALANCE_ADJUSTMENT` that would make balance inconsistent | Warn user; allow deletion (ledger must remain editable) |
| Delete one leg of a transfer | Cascades: deletes peer automatically |

---

## Testing

- Unit: balance computation (positive, negative, credit card outstanding, FUTURE exclusion)
- Unit: parcelamento creation (N transactions generated correctly)
- Unit: transfer double-entry + peer linkage
- Unit: opening balance auto-creation on account create
- Unit: `monthSummary` — totalIncome and totalExpenses exclude TRANSFER and BALANCE_ADJUSTMENT
- Unit: `monthSummary` — closingBalance includes TRANSFER and BALANCE_ADJUSTMENT, excludes FUTURE
- Unit: dirty-flag cascade — editing a transaction in month M marks M and all subsequent months dirty
- Integration: CRUD endpoints with permission check
- Integration: bill auto-creation on first credit card transaction
- Integration: `monthSummary` with and without snapshot (both paths return identical results)
- Frontend: transaction list grouping, filter, empty state
- Frontend: monthly summary header shows correct entradas/saídas/saldo for selected month
- Frontend: add form — both expense and income tabs, credit card fields, category picker

---

## Done Criteria

- `banking.transaction_categories`, `banking.credit_card_bills`, `banking.transactions`, `banking.monthly_balance_snapshots` tables created via Flyway migration
- Category seed data applied (all BR categories from this spec)
- Full CRUD GraphQL mutations for transactions and custom categories
- Transfer mutation creates two linked rows atomically
- Balance derived and returned on `financialAccounts` query
- Opening balance transaction auto-created when `CreateFinancialAccountCommand.initialBalance != null`
- Credit card bill auto-created on first transaction if none exists for the period
- `monthSummary` query returns correct totalIncome, totalExpenses, closingBalance, openingBalance
- `monthSummary` excludes TRANSFER and BALANCE_ADJUSTMENT from totalIncome/totalExpenses
- `monthSummary` works for both single-account and "all accounts" (null financialAccountId) modes
- Dirty-flag cascade: any transaction write marks affected and subsequent month snapshots dirty
- All new permissions registered in security service and assigned to "Banking User" role
- Transaction list UI with grouping, filters, search
- Monthly summary header shown above transaction list with entradas / saídas / saldo total
- Add/edit form with category picker, date, parcelas for credit cards
- Tests passing

---

## Resolved Design Decisions

| Decision | Resolution | Rationale |
|---|---|---|
| Stored balance or derived? | **Derived always** | Ledger integrity; YNAB/Pluggy/OFB standard; no sync issues |
| Signed amounts or direction + positive? | **Direction (`CREDIT`/`DEBIT`) + positive amount** | Aligns with OFB `creditDebitType`; avoids UX confusion with negatives |
| Transfer as one row or two? | **Two rows with peer FK** | Correct per-account balance; clean OFB alignment; each leg visible in its own account |
| Parcelamento: one row or N rows? | **N rows** (one per installment/month) | Each installment hits a different fatura; correct monthly cash flow; Pluggy standard |
| Parcelamento input: total or per-installment? | **Per-installment amount (valor da parcela)** | Matches how Brazilians think about installments ("12x de R$100"); total is derived |
| Categories: flat or hierarchical? | **Two levels max** (parent + subcategories) | Pluggy has 3 levels; 2 is sufficient for UX without over-engineering |
| Category in Portuguese or English? | **Portuguese (user-facing)** + `pluggy_category_id` for mapping | User-facing must be PT-BR; mapping field enables AI auto-categorization (Phase 3) |
| Category with budget field? | **Deferred to Phase 4** | Budget/envelope tracking is a distinct feature with its own complexity |
| Bill auto-creation or manual? | **Auto-created** on first transaction in period | Less friction; user doesn't need to manage bill cycle manually |
| `FUTURE` transactions in balance? | **Excluded from current balance**, returned as separate `projectedBalance` | Avoids confusion; shown as "projeção" indicator in UI |
| Manual FUTURE transactions in Phase 2? | **Yes** — user can register known future entries | Useful for scheduled rent, salary, etc.; recurring automation is Phase 3 |
| Opening balance: field on account or separate transaction? | **Separate `BALANCE_ADJUSTMENT` transaction** created by frontend after account creation | Keeps account creation contract clean; reuses same type for future reconciliation; auditable in history |
| Transaction list sort order? | **`ORDER BY date DESC, created_at DESC`** | `date` for user-visible grouping; `created_at` as tiebreaker for same-day entries |
| Monthly balance: compute on-demand or store snapshot? | **Hybrid — on-demand MVP, lazy snapshots for optimization** | On-demand is correct and fast enough at MVP scale. Snapshots (`monthly_balance_snapshots`) are added as a read-cache with dirty-flag invalidation, not a new source of truth. Industry standard: Blnk Finance, YNAB, accounting ERPs all use checkpoint + delta replay. |
| TRANSFER/BALANCE_ADJUSTMENT in monthly summary income/expenses? | **Excluded from totalIncome and totalExpenses; included in closingBalance** | Transfers are reallocations, not income or expense. Including them would double-count in all-accounts view and distort cash-flow analysis. They still move the balance. BALANCE_ADJUSTMENT is a reconciliation entry; no real cash flow. |
