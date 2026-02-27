---
title: Personal Finance Management — Market Research 2026
type: research
category: personal-finance
status: reference
version: 1.0.0
tags: [market-research, competitive-analysis, personal-finance, transactions, open-finance, brazil, pfm]
last_updated: 2026-02-27
---

# Personal Finance Management — Market Research 2026

> Reference material supporting the Personal Finance Transactions Blueprint. Contains competitive landscape data, feature benchmarks, Open Finance Brasil transaction API specification alignment, and data model recommendations. Updated February 2026.

---

## 1. Market Sizing

| Segment | 2025 Value | 2030–2034 Projection | CAGR |
|---|---|---|---|
| Global PFM Tool Market | USD 3.4B | USD ~6.5B (2035) | ~7–12.5% |
| Global Personal Finance Software | USD 1.35B | USD 2.57B (2034) | 7.60% |
| Brazil Fintech Market | USD 5.5B | USD 19.1B (2034) | 14.92% |

**Key driver — Brazil:** PIX infrastructure, Open Finance (100M+ clients), and rising financial literacy are compressing the gap between Brazil and US/EU markets faster than any other emerging market.

---

## 2. Global PFM Competitors

### Feature & Pricing Benchmark

| App | Price | Category Model | Aggregation | Investments | AI Insights | Collab | Mobile-First |
|---|---|---|---|---|---|---|---|
| **YNAB** | $14.99/mo or $109/yr | Manual, zero-based | Yes | No | No | No | Yes |
| **Monarch Money** | $14.99/mo or $99.99/yr | AI-smart rules | Yes (multi-account) | Yes | Yes | Yes (couples) | Yes |
| **Copilot Money** | $13/mo | AI-learning, auto-tag | Yes | No | Yes | No | iOS only |
| **Lunch Money** | Freemium | Rules-based | Yes | Crypto | No | Yes (teams) | Web-first, no app |
| **Simplifi (Quicken)** | $5.99/mo | Basic auto | Yes | No | No | No | Yes |
| **Tiller Money** | ~$79/yr | Spreadsheet auto | Yes | No | No | No | Desktop only |
| **Actual Budget** | Free/OSS | Manual envelope | No (import) | No | No | No | Self-hosted |
| **Emma (UK)** | £4.99–£14.99/mo | Auto + credit score | OB UK | No | Basic | No | Yes |

### Monarch Money — Key Metrics (2026)

- Raised **$75M Series B at $850M valuation** (May 2025); total $95.5M raised
- **$12.6M revenue**, 187 employees; ~20x growth spike after Mint shutdown (early 2024)
- 9% weekly paid subscriber growth post-Mint
- Positioning: the fastest-growing PFM in the US; filling the Mint/Intuit vacuum

### Key Differentiators by App

| App | Core Differentiator |
|---|---|
| **YNAB** | Methodology cult (zero-based budgeting); most loyal user base; $49M ARR |
| **Monarch Money** | Best aggregation breadth + collaborative finances for couples; AI improving fast |
| **Copilot** | Best AI tagging UX (learns your habits); native Apple experience |
| **Lunch Money** | Multi-currency + crypto; teams/freelancers; privacy-first |
| **Actual Budget** | Open-source, self-hosted, envelope budgeting; local-first |

### Winning UX Patterns (Globally)

1. **Transaction review workflow** — "Mark as reviewed" makes spending intentional (Copilot, Monarch)
2. **AI learning loop** — auto-tag improves with each correction (Copilot)
3. **Rule engine** — user-defined rules for repeating merchants (Monarch, Lunch Money)
4. **Subscription tracker** — surfaces hidden recurring charges (Monarch, Emma, Simplifi)
5. **Unified timeline** — all accounts in one scrollable feed, filterable
6. **Inline editing** — edit category, notes, split transaction directly in list
7. **Search + filter** — full-text, date range, amount range, category, account
8. **Split transactions** — one transaction split across multiple categories

---

## 3. Brazilian PFM Competitors

| App | Positioning | Auto-Sync | AI | Pricing |
|---|---|---|---|---|
| **Organizze** | Simple daily control | No (manual) | No | Freemium |
| **Mobills** | Multi-card tracking + goals | Limited | No | Freemium |
| **Minhas Economias** | 100% free, established | Import (OFX/CSV) | No | Free |
| **Guiabolso** | Auto bank import (legacy) | Proprietary scraping | No | Acquired by PicPay (2021) |
| **Neon** | Digital bank + basic PFM | Native (own bank) | No | Free (banking) |
| **Nubank** | SuperApp PFM embedded | Native (own bank) | Basic | Free (banking) |
| **C6 Bank** | Premium multi-currency | Native (own bank) | No | Free (banking) |

### Nubank Scale (Jan 2026)
- **112M+ customers** in Brazil (~61% of adult population)
- Added 17M net new customers in 2025 (15% YoY growth)
- Q4 2025 record profit: R$895M
- Transitioning to full banking license 2026; embedded PFM within SuperApp

### Brazilian Market Reality
Most Brazilian PFMs (Organizze, Mobills, Minhas Economias) were built **before Open Finance Phase 2** (2023). They rely on manual entry or OFX/CSV import. **None offer OFB-native transaction sync.** This is the core gap.

---

## 4. Open Finance Brasil — Transaction API Specification

### Phase 2 Scope (Current — Read-Only Data)

Open Finance Brasil Phase 2 covers read-only customer data APIs, including:
- Payment accounts (contas de pagamento / corrente / poupança)
- Credit card accounts (cartão de crédito)

### 4.1 Payment Account Transactions — `AccountTransactionsData`

**Endpoint:** `GET /accounts/{accountId}/transactions`

| Field | Type | Required | Notes |
|---|---|---|---|
| `transactionId` | string (1–100) | Yes | Immutable once `TRANSACAO_EFETIVADA`; pattern `^[a-zA-Z0-9][a-zA-Z0-9-]{0,99}$` |
| `completedAuthorisedPaymentType` | enum | Yes | `TRANSACAO_EFETIVADA` / `LANCAMENTO_FUTURO` / `TRANSACAO_PROCESSANDO` |
| `creditDebitType` | enum | Yes | `CREDITO` / `DEBITO` |
| `transactionName` | string (max 200) | Yes | Literal from institution's electronic channels |
| `type` | enum `EnumTransactionTypes` | Yes | TED, DOC, PIX, TRANSFERENCIA_MESMA_INSTITUICAO, BOLETO, CONVENIO_ARRECADACAO, PACOTE_TARIFA_SERVICOS, TARIFA_SERVICOS_AVULSOS, FOLHA_PAGAMENTO, DEPOSITO, SAQUE, CARTAO, ENCARGOS_JUROS_CHEQUE_ESPECIAL, RENDIMENTO_APLIC_FINANCEIRA, PORTABILIDADE_SALARIO, RESGATE_APLIC_FINANCEIRA, OPERACAO_CREDITO, OUTROS |
| `transactionAmount.amount` | string decimal | Yes | Always positive; direction from `creditDebitType` |
| `transactionAmount.currency` | string (ISO-4217) | Yes | Always `BRL` |
| `transactionDateTime` | string (ISO-8601 UTC) | Yes | Original date/time of transaction |
| `partieCnpjCpf` | string (11 or 14) | No | Counterparty CPF or CNPJ |
| `partiePersonType` | enum | No | `PESSOA_NATURAL` / `PESSOA_JURIDICA` |
| `partieCompeCode` | string (3 digits) | No | Counterparty bank COMPE code |
| `partieBranchCode` | string (4 digits) | No | Counterparty branch |
| `partieNumber` | string (8–20) | No | Counterparty account number |
| `partieCheckDigit` | string (1) | No | Counterparty account check digit |

### 4.2 Credit Card Transactions — `CreditCardAccountsTransaction`

**Endpoint:** `GET /credit-cards-accounts/{creditCardAccountId}/transactions`

| Field | Type | Required | Notes |
|---|---|---|---|
| `transactionId` | string (1–100) | Yes | Unique identifier |
| `identificationNumber` | string (1–100) | Yes | Last 4 digits of card or PJ identifier |
| `transactionName` | string (max 200) | Yes | Institution's literal description |
| `creditDebitType` | enum | Yes | `CREDITO` / `DEBITO` |
| `transactionType` | enum | Yes | PAGAMENTO, TARIFA, OPERACOES_CREDITO_CONTRATADAS_CARTAO, ESTORNO, CASHBACK, OUTROS |
| `transactionDate` | date | Yes | Effective date |
| `transactionDateTime` | date-time (RFC-3339) | Yes | Precise timestamp |
| `billPostDate` | date | Yes | Date posted to bill/statement |
| `billId` | string (1–100) | No | Statement/invoice identifier |
| `brazilianAmount.amount` | double | Yes | Amount in BRL |
| `brazilianAmount.currency` | string | Yes | ISO-4217 |
| `amount.amount` | double | Yes | Original currency amount |
| `amount.currency` | string | Yes | ISO-4217 (may differ from BRL for foreign) |
| `paymentType` | enum | Conditional | `A_VISTA` / `A_PRAZO` — required if `transactionType = PAGAMENTO` |
| `chargeIdentificator` | integer (1–999) | Conditional | Current installment number — required if `paymentType = A_PRAZO` |
| `chargeNumber` | integer (max 999) | Conditional | Total installments — required if `A_PRAZO` |
| `feeType` | enum | Conditional | ANUIDADE, SAQUE_CARTAO_BRASIL, SAQUE_CARTAO_EXTERIOR, etc. — required if `transactionType = TARIFA` |
| `otherCreditsType` | enum | Conditional | CREDITO_ROTATIVO, PARCELAMENTO_FATURA, EMPRESTIMO, OUTROS |
| `payeeMCC` | integer | No | Merchant Category Code |

---

## 5. Pluggy API — Transaction Model

Pluggy is the leading Open Finance aggregator in Brazil, normalizing raw OFB data.

### Core Fields

| Field | Type | Notes |
|---|---|---|
| `id` | string | Pluggy-assigned; may change if source data changes significantly |
| `date` | date (ISO-8601) | Posted date |
| `description` | string | Normalized description |
| `descriptionRaw` | string | Raw institution description |
| `amount` | number | Always positive |
| `type` | enum | `DEBIT` / `CREDIT` |
| `status` | enum | `PENDING` / `POSTED` |
| `balance` | number | Post-transaction balance |
| `currencyCode` | string | ISO-4217 |
| `amountInAccountCurrency` | number | For FX transactions |
| `category` | string | Pluggy taxonomy (Pro) |
| `categoryId` | string | Pluggy category identifier |
| `operationType` | string | OFB type: PIX, TED, DOC, BOLETO, SAQUE, CARTAO, etc. |
| `providerId` | string | OFB `transactionId` |
| `providerCode` | string | Provider's internal code |

### Payment Data Fields (`paymentData`)

| Field | Notes |
|---|---|
| `paymentMethod` | PIX, TED, DOC, BOLETO |
| `reason` | Payer's transaction description/motive |
| `referenceNumber` | Institution reference |
| `payer / receiver` | Name, account, CPF/CNPJ, bank COMPE/ISPB |

### Credit Card Specific (`creditCardMetadata`)

| Field | Notes |
|---|---|
| `installmentNumber` | Current installment |
| `totalInstallments` | Total planned installments |
| `totalAmount` | Sum of all installment amounts |
| `payeeMCC` | MCC code |
| `cardNumber` | Last 4 digits |
| `billId` | OFB bill reference |
| `purchaseDate` | Original purchase date |

### Boleto Specific (`boletoMetadata`)

| Field | Notes |
|---|---|
| `digitableLine` | Boleto identifier |
| `barcode` | Barcode |
| `baseAmount / interestAmount / penaltyAmount / discountAmount` | Breakdown amounts |

### Pluggy's Enrichment vs. Raw OFB

| Aspect | Raw OFB | Pluggy |
|---|---|---|
| Field naming | Per-institution variation | Normalized across all banks |
| Categorization | None | ML taxonomy (see §6) |
| Merchant data | None | CNPJ, CNAE, legal name (Pro) |
| Multi-institution | Per-institution APIs | Unified REST |
| FX handling | `brazilianAmount` + `amount` | `amountInAccountCurrency` |

---

## 6. Pluggy Category Taxonomy (Standard — maps to our category seed)

| Level 1 | Level 2 Examples |
|---|---|
| Income | Salary, Retirement, Government aid, Entrepreneurial, Non-recurring |
| Transfers | PIX, TED, DOC, Boleto, Internal, Credit card payment, Same-person |
| Loans & Financing | Overdraft charges, Interest, Real estate, Vehicle, Student loan |
| Investments | Fixed income, Funds, Variable income, Dividends, Pension |
| Groceries | — |
| Food & Drinks | Eating out, Food delivery |
| Shopping | Online, Electronics, Clothing, Pet, Bookstore |
| Digital Services | Gaming, Video streaming, Music streaming |
| Housing | Rent, Utilities (water/electricity/gas), Houseware, IPTU |
| Healthcare | Dentist, Pharmacy, Hospital/labs, Optometry |
| Transportation | Taxi/ride-hailing, Public transport, Gas stations, Parking, Tolls, Vehicle tax |
| Insurance | Life, Health, Home, Vehicle |
| Telecom | Internet, Mobile, TV |
| Education | University, School, Online courses |
| Travel | Airlines, Accommodation, Mileage programs |
| Taxes | Income tax, IOF, Investment taxes |
| Bank Fees | Account fees, Transfer fees, Credit card fees |
| Services | Wellness/gyms, Tickets |
| Legal Obligations | Alimony, Blocked balances |
| Gambling | Lottery, Online betting |
| Donations | — |

---

## 7. AI Trends in PFM (2026)

### Automatic Categorization
- **Technology:** Fine-tuned NLP/BERT models classifying transaction descriptions into standard taxonomy
- **Accuracy:** Improves via feedback loop (user corrections train model)
- **Challenge for Brazil:** Most models trained on English; Portuguese-language NLP for PFM still nascent — significant opportunity
- **OFB advantage:** Pluggy's `operationType` (PIX, BOLETO, etc.) provides strong signals before NLP

### Spending Insights & Anomaly Detection
- **Behavioral patterns:** Detecting lifestyle inflation creep, emotional spending triggers
- **Predictive:** "You're on pace to overspend on food delivery this month"
- **Anomaly agents:** 85% of financial firms use AI for fraud detection; consumer PFM catching up
- **Subscription detection:** Finding hidden recurring charges (Monarch, Copilot, Emma all do this)

### Agentic Finance (Emerging)
- Global leaders (Copilot, Monarch) moving from *reporting* to *acting*: auto-pay optimization, bill negotiation, savings automation
- Brazil: No Brazilian PFM offers this in 2026 — open opportunity

---

## 8. Brazilian Market Gaps (2026)

| Gap | Severity | Opportunity |
|---|---|---|
| No OFB-native transaction sync in standalone PFMs | **Critical** | First-mover advantage with Phase 2+3 |
| No AI auto-categorization in Portuguese for BR context | **Critical** | Pluggy taxonomy + fine-tuning |
| No unified multi-bank view with real-time data | **Critical** | Pluggy integration (Phase 5) |
| No subscription/recurring charge tracker | **High** | Low-hanging fruit for v1 |
| No installment (parcelamento) tracking across cards | **High** | Brazil-specific: most purchases are parcelado |
| No cash flow forecast / projection | **High** | Phase 2 |
| No credit-to-investment journey (debt → savings) | **High** | Phase 3 |
| No self-employed / MEI expense categorization | **Medium** | Vertical for freelancers |
| No family shared finances / goals | **Medium** | Collab features |

### Brazil-Specific Must-Haves (not in global apps)
1. **Parcelamento tracking** — installment purchases split across N months; tracking remaining installments per card
2. **Fatura / bill cycle** — linking credit card transactions to their monthly statement (`billId`)
3. **PIX metadata** — payer/receiver name, reason field (much richer than US ACH)
4. **Boleto payments** — separate entity in the PF landscape (not a card, not a transfer)
5. **VOUCHER_CARD** — VR, Alelo, Sodexo; used for food/meal/culture purchases; distinct category behavior
6. **Taxes on transactions** — IOF on credit card foreign transactions; specific tax category needed

---

## 9. Recommended Data Model (OFB-Aligned)

Design principle: **manual-first, OFB-ready**. All OFB fields included as nullable columns from day one. Zero migration cost when Pluggy integration (Phase 5) arrives.

### 9.1 `banking.transaction_categories` (seed table)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID v7 PK | |
| `account_id` | UUID FK → security.accounts | NULL = system/global category |
| `parent_id` | UUID FK → self | For subcategories |
| `name` | VARCHAR(100) | Display name |
| `applicable_to` | VARCHAR CHECK IN ('INCOME', 'EXPENSE', 'TRANSFER', 'ANY') | |
| `icon` | VARCHAR(50) | Emoji or icon identifier |
| `color` | VARCHAR(7) | Hex color |
| `pluggy_category_id` | VARCHAR(100) | For OFB/Pluggy sync mapping |
| `is_system` | BOOLEAN DEFAULT false | True = seed data, not deletable |
| `sort_order` | INT | Display order |
| `deleted_at` | TIMESTAMPTZ | Soft delete |
| `created_at / updated_at` | TIMESTAMPTZ | |

### 9.2 `banking.credit_card_bills` (credit card statements)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID v7 PK | |
| `financial_account_id` | UUID FK → banking.financial_accounts | Must be a credit card account |
| `account_id` | UUID FK → security.accounts | Tenant |
| `closing_date` | DATE | Billing cycle close date |
| `due_date` | DATE | Payment due date |
| `total_amount` | NUMERIC(18,4) | Total balance of statement |
| `minimum_payment` | NUMERIC(18,4) | |
| `status` | VARCHAR CHECK IN ('OPEN', 'CLOSED', 'PAID', 'OVERDUE') | |
| `provider_bill_id` | VARCHAR(100) | OFB `billId` |
| `created_at / updated_at` | TIMESTAMPTZ | |

### 9.3 `banking.transactions` (main table)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID v7 PK | |
| `financial_account_id` | UUID FK → banking.financial_accounts | NOT NULL |
| `account_id` | UUID FK → security.accounts | Tenant; NOT NULL |
| `created_by_user_id` | UUID FK → security.users | Audit |
| — **Classification** — | | |
| `credit_debit_type` | VARCHAR CHECK IN ('CREDIT', 'DEBIT') NOT NULL | Maps to OFB `creditDebitType` |
| `type` | VARCHAR NOT NULL | PIX, TED, DOC, BOLETO, CARD, TRANSFER, SALARY, WITHDRAWAL, DEPOSIT, FEE, INTEREST, CASHBACK, OTHER |
| `status` | VARCHAR CHECK IN ('POSTED', 'PENDING', 'FUTURE') DEFAULT 'POSTED' | Maps to OFB `completedAuthorisedPaymentType` |
| `source` | VARCHAR CHECK IN ('MANUAL', 'OPEN_FINANCE', 'PLUGGY') DEFAULT 'MANUAL' | |
| — **Amount** — | | |
| `amount` | NUMERIC(18,4) NOT NULL | Always positive; direction from `credit_debit_type` |
| `currency_code` | VARCHAR(3) NOT NULL DEFAULT 'BRL' | ISO 4217 |
| `amount_brl` | NUMERIC(18,4) | BRL equivalent for FX; maps to OFB `brazilianAmount` |
| — **Dates** — | | |
| `date` | DATE NOT NULL | Effective/post date (what user sees) |
| `transaction_datetime` | TIMESTAMPTZ | Precise timestamp; maps to OFB `transactionDateTime` |
| — **Description** — | | |
| `description` | VARCHAR(500) NOT NULL | User-facing; editable |
| `description_raw` | VARCHAR(500) | Original from institution (immutable after sync) |
| `notes` | TEXT | User freeform notes |
| — **Category** — | | |
| `category_id` | UUID FK → banking.transaction_categories | Nullable |
| — **Counterparty** (bank account transactions) — | | |
| `counterparty_name` | VARCHAR(200) | Payer/receiver name; maps to OFB `partieCnpjCpf` payer |
| `counterparty_document` | VARCHAR(14) | CPF (11) or CNPJ (14) |
| `counterparty_bank_code` | VARCHAR(3) | COMPE code; maps to OFB `partieCompeCode` |
| `counterparty_account_number` | VARCHAR(20) | Maps to OFB `partieNumber` |
| `payment_reason` | VARCHAR(500) | Payer motive/reason; Pluggy `reason` |
| — **Transfer** (between user's own accounts) — | | |
| `transfer_peer_transaction_id` | UUID FK → banking.transactions | The other leg of an internal transfer |
| — **Credit Card** — | | |
| `bill_id` | UUID FK → banking.credit_card_bills | Which statement this belongs to |
| `installment_number` | INT | Current installment; maps to OFB `chargeIdentificator` |
| `total_installments` | INT | Total installments; maps to OFB `chargeNumber` |
| `payee_mcc` | INT | Merchant Category Code; maps to OFB `payeeMCC` |
| `card_identifier` | VARCHAR(100) | Last 4 digits or card ref; maps to OFB `identificationNumber` |
| `bill_post_date` | DATE | Date posted to statement; maps to OFB `billPostDate` |
| — **OFB Sync** (populated by Pluggy integration, Phase 5) — | | |
| `provider_transaction_id` | VARCHAR(100) | OFB `transactionId` / Pluggy `id` |
| `provider_account_id` | VARCHAR(100) | OFB account ID |
| — **Soft delete** — | | |
| `deleted_at` | TIMESTAMPTZ | |
| `created_at / updated_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

### Key Indexes
```sql
CREATE INDEX ON banking.transactions (financial_account_id, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ON banking.transactions (account_id, date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ON banking.transactions (bill_id) WHERE bill_id IS NOT NULL;
CREATE INDEX ON banking.transactions (provider_transaction_id) WHERE provider_transaction_id IS NOT NULL;
CREATE INDEX ON banking.transactions (transfer_peer_transaction_id) WHERE transfer_peer_transaction_id IS NOT NULL;
```

### Design Decisions

| Decision | Rationale |
|---|---|
| `amount` always positive | Simpler queries; direction from `credit_debit_type`. OFB pattern. |
| `date` (DATE) + `transaction_datetime` (TIMESTAMPTZ) | `date` for display/grouping; `datetime` for OFB precision |
| `description` editable + `description_raw` immutable | User can rename; original preserved for re-sync |
| `transfer_peer_transaction_id` self-reference | Avoids duplication; both legs of a transfer exist as separate rows for clean per-account balance calculation |
| `bill_id` FK to `credit_card_bills` | Enables future "fatura" view grouping transactions by statement |
| `source` field | Enables clean separation of manual vs. OFB-synced transactions; prevents re-import duplicates |
| Counterparty as flat columns (not a sub-table) | PF scale doesn't justify normalization; matches OFB flat schema |
| No opening balance field on account | Opening balance = first CREDIT transaction of type DEPOSIT; aligns with the business rule |

---

## 10. Identified Market Gaps vs. Invistus Positioning

| Gap | Severity | Addressable | Phase |
|---|---|---|---|
| First OFB-native PFM with auto-sync | Critical | Yes | Phase 5 (Pluggy) |
| Portuguese-language AI categorization | Critical | Yes | Phase 3 (category ML) |
| Parcelamento tracker across cards | High | Yes — unique BR need | Phase 2 |
| Subscription/recurring detector | High | Yes | Phase 2 |
| Cash flow forecast | High | Yes | Phase 2 |
| Credit card bill (fatura) management | High | Yes | Phase 2 (bills model) |
| Multi-user / family shared finances | Medium | Yes | Phase 4 |
| MEI / self-employed categories | Medium | Yes — niche | Phase 3 |
| Agentic bill payment (OFB Phase 3) | Medium (emerging) | Yes | Phase 6 |

---

## 11. Sources

- [Personal Financial Management Tool Market Size 2035 — Business Research Insights](https://www.businessresearchinsights.com/market-reports/personal-financial-management-tool-market-111515)
- [Personal Finance Software Market Size 2034 — Fortune Business Insights](https://www.fortunebusinessinsights.com/personal-finance-software-market-112683)
- [Brazil Fintech Market 14.92% CAGR 2026–2034 — OpenPR](https://www.openpr.com/news/4393574/brazil-fintech-market-projected-to-record-14-92-cagr-from-2026)
- [Monarch Money vs YNAB 2026 — Monarch](https://www.monarch.com/compare/ynab-alternative)
- [Monarch raises $75M Series B — CNBC](https://www.cnbc.com/2025/05/23/personal-finance-app-monarch-raises-75-million.html)
- [Monarch Money $12.6M revenue — Latka](https://getlatka.com/companies/monarchmoney.com)
- [Best Budgeting Apps 2026 — Engadget](https://www.engadget.com/apps/best-budgeting-apps-120036303.html)
- [Copilot vs Monarch vs YNAB — Beancount Forum](https://beancount.io/forum/t/copilot-vs-monarch-vs-ynab-which-premium-budget-app-is-worth-it/98)
- [Actual Budget — actualbudget.org](https://actualbudget.org/)
- [Emma App — emma-app.com](https://emma-app.com/)
- [Nubank 112M clientes Jan 2026 — Nubank IR](https://international.nubank.com.br/company/nubank-reaches-more-than-112-million-customers-and-becomes-the-largest-private-financial-institution-in-brazil-according-to-the-central-bank/)
- [Nubank Q4 2025 Record Profit — Rio Times Online](https://www.riotimesonline.com/brazils-nubank-posts-record-895m-profit-in-q4-2025/)
- [Melhores Apps Finanças Pessoais BR — My Best](https://br.my-best.com/18262)
- [Pluggy API — Transaction Fields](https://docs.pluggy.ai/docs/transactions)
- [Pluggy — Transaction Enrichment](https://docs.pluggy.ai/docs/enrich-api)
- [Pluggy — Category Taxonomy](https://docs.pluggy.ai/docs/transaction-categories)
- [Open Finance Brasil — OpenAPI Spec (GitHub)](https://github.com/OpenBanking-Brasil/openapi)
- [OFB Accounts API v2.4.0 — AccountTransactionsData schema](https://raw.githubusercontent.com/OpenBanking-Brasil/openapi/main/swagger-apis/accounts/2.4.0.yml)
- [OFB Credit Cards API v2.2.0 — CreditCardAccountsTransaction schema](https://raw.githubusercontent.com/OpenBanking-Brasil/openapi/main/swagger-apis/credit-cards/2.2.0.yml)
- [Open Finance Brasil — Developer Portal](https://openfinancebrasil.atlassian.net)
- [Raidiam — Deep dive OFB Payments APIs](https://www.raidiam.com/insights/thought-leadership/deep-dive-into-open-finance-brasil-payments-apis)
- [AI in Personal Finance 2026 — SR Analytics](https://sranalytics.io/blog/ai-personal-finance/)
- [Bank Transaction Categorization ML — Neontri](https://neontri.com/blog/ai-transaction-categorization/)
- [Brazil Open Finance 100M clients — Finsiders Brasil](https://finsidersbrasil.com.br/economia-open/brasil-lidera-open-finance-no-mundo-com-100-milhoes-de-clientes/)
