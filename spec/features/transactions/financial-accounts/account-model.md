---
id: feature.transactions.financial-accounts.account-model
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/account/FinancialAccount.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/account/FinancialAccountEnums.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountService.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_3__create_banking_accounts.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/account/FinancialAccountServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/FinancialAccountMultiCardIT.kt
  frontend:
    - web/src/app/personal-finance/finance-transactions/account/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.financial-accounts.account-crud
  - feature.transactions.financial-accounts.tenant-ownership-guard
  - feature.transactions.financial-accounts.issuers-reference
  - feature.transactions.ledger-transactions.transaction-model
schema: spec-metadata-v1
---

# Financial Accounts — Account Model

## Glossary

- `accountId` — tenant/workspace from security JWT (`current_account`). Ownership of every financial account.
- `financialAccountId` — banking personal-finance account/card row (`banking.financial_accounts.id`).

## Behavior

- Domain `FinancialAccount` models a personal-finance account or card: type/subtype, name, currency (default BRL), dashboard/net-worth/cash-allocation flags, optional issuer link or custom issuer name snapshot fields, optional `BankData` / `CardData`, Open Finance connection fields (`connectionStatus`, consent/sync timestamps, `providerItemId`), soft-delete `deletedAt`.
- Types include BANK, CREDIT, PAYMENT, CASH, INVESTMENT, LOAN, ASSET, BENEFIT. Subtypes cover checking/savings, credit/charge/prepaid/voucher cards, brokerage/pension/crypto, loans, assets, FGTS/INSS benefits.
- `NON_CASHFLOW_ACCOUNT_TYPES` = INVESTMENT + BENEFIT (used by cashflow/summary paths — pointer for those specs).
- CardData may include limit/closing/due days, network/level, `linkedBankAccountId`, `isMultiCard` (umbrella card — no single last-4).
- GraphQL `FinancialAccount` does **not** expose tenant `accountId`; resolves issuer object, optional `initialBalance` / `initialBalanceDate` derived from BALANCE_ADJUSTMENT tx, and `dataSince`.

## Invariants

- Every financial account belongs to exactly one tenant `accountId`.
- BANK uses bankData path; non-BANK must not carry bankData (service shape validation).
- INVESTMENT/BENEFIT share the table but are not the investment-portfolio product surface in this domain.

## Permissions

Backend enforcement: reads/writes gated at GraphQL with `banking:account:view` / `banking:account:create` (see CRUD + guard specs).

UX hints: web account selectors/drawers; not enforcement.

## Data And Contracts

- DB: `banking.financial_accounts` + `financial_account_bank_data` / `financial_account_card_data` (V0_3).
- GraphQL type `FinancialAccount` + enums `FinancialAccountType` / `FinancialAccountSubtype` / card enums / `ConnectionStatus`.

## Impact Surface

- Backend: domain + FinancialAccountService
- GraphQL: banking schema FinancialAccount
- Database: V0_3
- Tests: FinancialAccountServiceTest, MultiCard IT
- Frontend: finance-transactions/account

## Decisions

- Tenant association is JWT-only at API boundary (not a client-supplied field).
- Custom institutions allowed via `issuerName` when `issuerId` is null.

## Known Gaps

- Open Finance fields exist; connector/sync product depth not mapped in Batch 1.
- Full INVESTMENT/BENEFIT product behavior lives under investment-portfolio / benefits — only shared model here.

## Legacy Notes

- None used as authority.
