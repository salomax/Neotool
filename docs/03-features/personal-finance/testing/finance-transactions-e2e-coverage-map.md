# E2E Coverage Map — Financial Movements

**Date:** 2026-04-07  
**Scope:** `web/src/app/personal-finance/finance-transactions` module and related backend/GraphQL flows exercised through Playwright.

## Objective

Map the documented requirements and business rules against the existing E2E suite, to answer clearly:

- what is already validated;
- what is only partially validated;
- what still has no E2E coverage;
- where the requirement set and the current suite diverge.

## Current Suite Inventory

| Area | File | Declared coverage |
|---|---|---|
| Financial accounts | `web/tests/e2e/personal-finance/financial-accounts.e2e.spec.ts` | `FA-H-01..08`, `FA-A-01..08`, `FA-N-01..10` |
| Transactions | `web/tests/e2e/personal-finance/transactions.e2e.spec.ts` | `TX-H-01..15`, `TX-A-01..11`, `TX-N-01..07` |
| Advanced transactions | `web/tests/e2e/personal-finance/transactions-advanced.e2e.spec.ts` | `TX-A-09`, `TX-A-12..14`, `TX-A-16`, `TX-N-08..09` |
| Bills | `web/tests/e2e/personal-finance/credit-card-bills.e2e.spec.ts` | `CB-H-01..08`, `CB-A-01..09`, `CB-N-01..04` |
| Monthly summary | `web/tests/e2e/personal-finance/monthly-summary.e2e.spec.ts` | `MS-H-01..08`, `MS-A-01..11`, `MS-N-01..04` |
| Import | `web/tests/e2e/personal-finance/transaction-import.e2e.spec.ts` | `IM-H-01..06`, `IM-A-01..08`, `IM-N-01..06` |
| Security | `web/tests/e2e/personal-finance/security.e2e.spec.ts` | `SEC-FA-01..04`, `SEC-TX-01..05`, `SEC-MS-01..02`, `SEC-CB-01..03`, `SEC-IM-01..02` |

## Coverage Matrix by Requirement Group

| Area | Requirement / rule | Source | Tests validating it | Status | Notes |
|---|---|---|---|---|---|
| Accounts | Create bank, card, cash, and benefit accounts | `01-financial-accounts` | `FA-H-01`, `FA-H-02`, `FA-H-03`, `FA-H-04` | Covered | Full UI flow |
| Accounts | Account listing and editing | `01-financial-accounts` | `FA-H-05`, `FA-H-06`, `FA-H-07`, `FA-A-07`, `FA-A-08` | Covered | Includes persistence of `closingDay`, `dueDay`, and `bankData` |
| Accounts | Form validations and closing/due day limits | `01-financial-accounts` | `FA-N-01..10`, `FA-A-04`, `FA-A-05`, `FA-A-06` | Covered | Good happy/negative-path coverage |
| Accounts | Account soft delete | `01-financial-accounts` | `FA-H-08` | Partial | Test exists, but the requirement doc says `deleteFinancialAccount` is absent from GraphQL |
| Accounts | Issuer resolution (`issuerId` vs `issuerName`) | `01-financial-accounts` | no specific E2E | No E2E coverage | No scenario covers custom institution or name priority |
| Accounts | Automatic `BALANCE_ADJUSTMENT` creation for initial balance | `01-financial-accounts`, `02-transactions` | `FA-H-01`, `TX-A-02`, `MS-A-05`, `MS-A-06` | Partial | The effects are visible, but the automatic ledger entry is not explicitly validated |
| Accounts | `linkedBankAccountId` for bill-payment debit leg | `01-financial-accounts`, `03-credit-card-bills` | no specific E2E | No E2E coverage | Missing positive payment scenario with linked source account |
| Transactions | Create `POSTED` income and expense transactions | `02-transactions` | `TX-H-01`, `TX-H-02`, `MS-H-01`, `MS-H-02` | Covered | Validates table presence and summary impact |
| Transactions | `PENDING` and `FUTURE` behavior | `02-transactions`, `04-monthly-summaries` | `TX-H-03`, `TX-H-04`, `TX-H-07`, `TX-H-08`, `MS-A-02`, `MS-A-03` | Covered | Inclusion/exclusion rules in balance are covered |
| Transactions | Edit description and amount | `02-transactions` | `TX-H-05`, `TX-H-06`, `TX-A-08`, `TX-A-09`, `MS-H-04` | Covered | Covers both increasing and decreasing amounts |
| Transactions | Transaction soft delete (manual or imported) | `02-transactions` | `TX-H-09`, `IM-H-04a`, `MS-H-03`, `TX-N-09` | Covered | Covers manual UI delete, imported OFX delete, and double-delete error |
| Transactions | Block `amount <= 0` | `02-transactions` | `TX-N-02`, `TX-N-03`, `TX-N-05` | Covered | UI and indirect GraphQL coverage |
| Transactions | 5-year date window | `02-transactions` | `TX-N-04` | Covered | Client-side rule reflected in the suite |
| Transactions | `date >= initialBalanceDate` outside `BALANCE_ADJUSTMENT` | `02-transactions` | no specific E2E | No E2E coverage | No test for `TRANSACTION_BEFORE_INITIAL_BALANCE` |
| Transactions | Internal transfer excluded from income/expense totals | `02-transactions`, `04-monthly-summaries` | `TX-A-01`, `MS-A-04` | Covered | Neutrality rule is validated |
| Transactions | `BALANCE_ADJUSTMENT` excluded from income/expense totals and bill assignment | `02-transactions`, `03-credit-card-bills`, `04-monthly-summaries` | `TX-A-02`, `CB-A-03`, `MS-A-05` | Covered | Strong cross-area coverage |
| Transactions | Installments | `02-transactions` | `TX-A-12`, `TX-A-13`, `TX-N-08` | Partial | `TX-A-12` is conditional and conflicts with the current requirement, which expects manual creation per installment |
| Transactions | Automatic `bill_id` assignment on card accounts | `02-transactions`, `03-credit-card-bills` | `TX-A-16`, `CB-H-01..07`, `CB-A-01`, `CB-A-09` | Covered | Strong cycle and recomputation coverage |
| Transactions | Single-category assignment and category filter | `02-transactions` | `TX-H-10`, `TX-H-12`, `MS-H-08`, `MS-A-10` | Covered | Covers display and breakdown |
| Transactions | Tags, tag filter, and tag summary | `02-transactions`, `04-monthly-summaries` | `TX-H-11`, `TX-H-14`, `MS-A-11` | Covered | Covers functional usage |
| Transactions | Tag normalization and blank rejection | `02-transactions` | no specific E2E | No E2E coverage | Missing trim/dedup/blank validation |
| Transactions | Similar-transaction propagation | `02-transactions` | `TX-A-14` | Partial | Covers dialog appearance, but not all existing/future apply paths |
| Transactions | Implicit rule learning on category/merchant update | `02-transactions` | no specific E2E | No E2E coverage | No validation that a rule is created after update |
| Transactions | Category hierarchy and root + subcategory filtering | `02-transactions` | no specific E2E | No E2E coverage | Missing scenario for `parent_id` and hierarchical filtering |
| Transactions | Pagination / incremental loading | `02-transactions` | `TX-H-15` | Covered | Covers navigation/loading |
| Transactions | Free-form notes | `02-transactions` | `TX-A-07` | Covered | Validates persistence and display |
| Transactions | `belongs_to_user_id` / `belongs_to_name` | `02-transactions` | no specific E2E | No E2E coverage | No attribution scenarios, authenticated-user default, or exclusivity validation |
| Transactions | Bulk categorize | `02-transactions` | no specific E2E | No E2E coverage | No E2E for multi-select + `bulkCategorize` |
| Monthly summary | `closingBalance`, `totalIncome`, `totalExpenses` formula | `04-monthly-summaries` | `MS-H-01..04`, `MS-A-02..05` | Covered | Good confidence level |
| Monthly summary | `openingBalance` chaining from previous month | `04-monthly-summaries` | `MS-H-07`, `TX-A-10`, `TX-A-11`, `MS-A-07`, `MS-A-08` | Covered | Chain recomputation is well exercised |
| Monthly summary | Month navigation | `04-monthly-summaries` | `MS-H-05`, `MS-H-06` | Covered | UI and data |
| Monthly summary | Category and tag breakdown | `04-monthly-summaries` | `MS-H-08`, `MS-A-10`, `MS-A-11` | Covered | Includes total consistency |
| Monthly summary | `projectedClosingBalance` and future-month projection | `04-monthly-summaries` | `MS-A-02`, `MS-A-09` | Partial | There are signs of coverage, but no direct assertion on the projected field |
| Monthly summary | `transfersIn` / `transfersOut` | `04-monthly-summaries` | `MS-N-04` | Partial | Only residual zero assertions exist; missing positive scenario |
| Bills | Automatic bill creation per cycle, `closingDate` and `dueDate` calculation | `03-credit-card-bills` | `CB-H-01`, `CB-H-02`, `CB-A-01`, `CB-A-07` | Covered | Cycle rules are well covered |
| Bills | Net total `DEBIT - CREDIT` | `03-credit-card-bills` | `CB-H-03`, `CB-H-04`, `CB-H-06`, `CB-H-07`, `CB-A-04`, `CB-A-06` | Covered | Includes edit and delete |
| Bills | `OPEN`, `CLOSED`, `OVERDUE`, `PAID` statuses | `03-credit-card-bills` | `CB-H-08`, part of `CB-N-04` | Partial | Little explicit coverage of `CLOSED` and `OVERDUE`; `PAID` appears mainly in a bug scenario |
| Bills | `payBill` and transfer-leg creation | `02-transactions`, `03-credit-card-bills` | `CB-N-04`, `SEC-CB-03` | Partial | Missing reliable happy path for full/partial payment and source account |
| Bills | Descending bill ordering | `03-credit-card-bills` | `CB-A-08` | Covered | |
| Bills | Non-eligible account types for bills | `03-credit-card-bills` | `CB-N-01`, `CB-A-09` | Covered | BANK not eligible and `CHARGE_CARD` eligible |
| Import | CSV/OFX preview and confirmation | `02-transactions` section 5 | `IM-H-01..04` | Covered | Main flow validated |
| Import | Duplicate detection and duplicate review | `02-transactions` section 5 | `IM-H-05`, `IM-H-06`, `IM-A-03`, `IM-A-06`, `IM-A-07` | Covered | Good dedup coverage |
| Import | Cancel flow, UTF-8 accents, account selection in drawer | `02-transactions` section 5 | `IM-A-04`, `IM-A-05`, `IM-A-08` | Covered | |
| Import | Monthly summary update after import | `02-transactions`, `04-monthly-summaries` | `IM-A-01`, `IM-A-02` | Covered | |
| Import | NFC-e, OCR, and bank statement PDF channels | `02-transactions` section 5, `03-credit-card-bills` | no specific E2E | No E2E coverage | Current suite is essentially focused on CSV/OFX |
| Import | Level 2 resolution (`KEEP_BOTH`, `USE_INCOMING`, `KEEP_EXISTING`) | `02-transactions` section 5 | no specific E2E | No E2E coverage | Only skip/ignore duplicate behavior exists |
| Import | SSE and polling fallback | `02-transactions` section 5 | no specific E2E | No E2E coverage | No stream-failure scenario |
| Security | Multi-tenant isolation for accounts, transactions, summary, bills, and import | cross-cutting requirements | `SEC-FA-01..04`, `SEC-TX-01..05`, `SEC-MS-01..02`, `SEC-CB-01..03`, `SEC-IM-01..02` | Covered | Excellent tenant-isolation baseline |

## Priority Gaps for the Next Round

| Priority | Topic | Reason |
|---|---|---|
| P0 | `TRANSACTION_BEFORE_INITIAL_BALANCE` | Critical ledger-integrity rule with no E2E |
| P0 | Non-manual transaction delete | Documented protection rule with no E2E validation |
| P0 | `payBill` happy path | Core module flow still only partially validated |
| P1 | `belongs_to_user_id` / `belongs_to_name` | Important functional rule with no current coverage |
| P1 | Tag normalization and validation | Data quality guarantees are not tested |
| P1 | Category hierarchy and `bulkCategorize` | Documented rules with no E2E |
| P1 | NFC-e / OCR import and SSE fallback | Current coverage is concentrated on CSV/OFX |
| P2 | `projectedClosingBalance`, `transfersIn`, `transfersOut` | Fields exist in the requirements but are barely validated |

## Divergences Found Between Requirements and the Current Suite/Docs

| Topic | Divergence |
|---|---|
| Zero initial balance | `01-financial-accounts` says zero `initialBalance` should be rejected, but `FA-A-01` expects account creation with zero balance |
| Account deletion | `01-financial-accounts` records the absence of `deleteFinancialAccount` in GraphQL, but `FA-H-08` assumes a working delete flow |
| Installments | `02-transactions` describes manual creation of each installment, but `TX-A-12` tries to validate auto-generation from a single input |
| PDF import | The header in `transaction-import.e2e.spec.ts` lists `.pdf` as accepted, but `IM-N-01` treats PDF as unsupported |

## Suggested Next Step

Use this map to open an incremental testing backlog by priority, starting with the P0 items and first resolving the divergences between requirements and suite behavior, so the team does not automate the wrong behavior or keep outdated documentation.
