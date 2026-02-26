---
title: Phase 1 Accounts/Cards
type: milestone
category: personal-finance
status: planned
version: 2.0.0
tags: [feature, personal-finance, accounts, cards, phase-1, open-finance]
ai_optimized: true
search_keywords: [personal finance, accounts, cards, card badge, account selector, carousel, bin, issuer, open finance, pluggy, voucher]
related:
  - 03-features/personal-finance/personal-finance-overview.md
last_updated: 2026-02-26
---

# Phase 1 — Accounts/Cards

> **Purpose**: Deliver full accounts/cards CRUD with the account selector and card carousel UI, plus shared components for reuse. The data model is aligned with Open Finance Brasil (OFB Phase 2) and the Pluggy aggregator API to minimize future integration effort.

## Overview

This phase establishes the accounts and cards foundation for the personal finance module. It includes backend CRUD endpoints, UI for listing and selecting accounts/cards, and reusable shared components (`AccountSelector`, `AccountBadge`, `CardBadge`).

The model is intentionally aligned with Open Finance Brasil contracts and the Pluggy aggregator API. Fields that support future Open Finance sync are included from day one as nullable — zero cost now, zero migration later.

## Design References

- **Open Finance Brasil Phase 2** — Dados Cadastrais e Transacionais (accounts, credit cards endpoints).
- **Pluggy API** — leading Brazilian Open Finance aggregator; mirrors OFB spec with enrichment.
- **Competitors analyzed**: Organizze, Mobills, Minhas Economias, YNAB, Monarch Money, Nubank, Neon.

## Scope

**In scope**
- Backend: accounts/cards model and CRUD endpoints (create, view, edit, delete).
- Frontend: two-path account creation flow (manual entry), account list/cards UI (carousel), and account selector.
- Shared components: `AccountSelector`, `AccountBadge`, `CardBadge`.
- Tests: API and UI.

**Out of scope**
- Open Finance sync / Pluggy integration (planned for Phase 5).
- Transaction list and aggregation (Phase 2).
- Categories and insights (Phase 3).
- Permissions enforcement (Phase 5 — after endpoint shapes stabilize).

---

## Data Model

### Account (root entity)

All account types share this root model. Type-specific fields are nested in sub-objects.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID v7 | Primary key |
| `userId` | UUID | Owner |
| `type` | `AccountType` | Broad classification — see enum below |
| `subtype` | `AccountSubtype` | Granular classification — see enum below |
| `name` | String | User-defined display name (e.g., "Itaú Corrente") |
| `currencyCode` | String | ISO 4217, defaults to `BRL` |
| `includeInNetWorth` | Boolean | Whether to include in total balance calculation |
| `includeInDashboard` | Boolean | Whether to show in the account carousel |
| `themeColor` | String | Hex color for card visual, user-overridable |
| `logoKey` | String? | Asset key for issuer/institution logo |
| `issuerName` | String? | Financial institution name |
| `issuerCountry` | String? | ISO 3166-1 alpha-2 country code |
| `issuerCompeCode` | String? | COMPE bank code (3 digits, e.g. "341" for Itaú) |
| `issuerIspb` | String? | ISPB code (8 digits, used in PIX) |
| `bankData` | `BankData`? | Populated for `BANK` accounts only |
| `creditData` | `CreditData`? | Populated for `CREDIT` accounts only |
| `connectionStatus` | `ConnectionStatus`? | Null for manual accounts; set when linked via Open Finance |
| `consentExpiresAt` | Instant? | OFB consent expiry (OFB mandates re-auth every 12 months) |
| `lastSyncAt` | Instant? | Timestamp of last Open Finance sync |
| `providerItemId` | String? | Pluggy Item ID (for future OFB integration) |
| `createdAt` | Instant | |
| `updatedAt` | Instant | |

> **Design rationale**: `connectionStatus`, `consentExpiresAt`, `lastSyncAt`, and `providerItemId` are nullable and unused in the MVP. They are present now to avoid a migration when Open Finance is integrated in Phase 5.

---

### BankData (nested, populated for `type = BANK`)

| Field | Type | Notes |
|---|---|---|
| `branchCode` | String? | Agency number (agência) |
| `number` | String? | Account number (optional — user may not want to enter) |
| `checkDigit` | String? | Verification digit |
| `maskedNumber` | String? | Display-safe partial number |

---

### CreditData (nested, populated for `type = CREDIT` or `type = PAYMENT`)

| Field | Type | Notes |
|---|---|---|
| `maskedNumber` | String? | Last 4 digits for display |
| `bin` | String? | First 6 or 8 digits (for BIN enrichment) |
| `creditCardNetwork` | `CreditCardNetwork`? | Card network — see enum below |
| `creditCardLevel` | `CreditCardLevel`? | Card tier — see enum below |
| `creditLimit` | BigDecimal? | Total credit limit |
| `availableCreditLimit` | BigDecimal? | Available credit (computed or entered) |
| `minimumPaymentPercentage` | BigDecimal? | Minimum payment % (e.g., 15%) |
| `closingDay` | Int? | Bill closing day of month (1–28) |
| `dueDay` | Int? | Bill due day of month (1–28) |

> **Design rationale**: `closingDay` and `dueDay` are first-class fields in all Brazilian personal finance apps. Credit card bill cycles are central to Brazilian budgeting. Keeping them flat on `CreditData` (not derived) is correct — they are user-configured, not computed.

---

## Enums

### `AccountType`

Aligned with OFB and Pluggy top-level classification.

```
BANK        — Conta bancária (corrente, poupança, salário, digital)
CREDIT      — Cartão de crédito
PAYMENT     — Conta de pagamento / cartão pré-pago / vale
CASH        — Dinheiro em carteira
INVESTMENT  — Investimentos (corretora, renda fixa, previdência, cripto)
LOAN        — Empréstimos e financiamentos (tracking only)
ASSET       — Ativos físicos para net worth (imóvel, veículo) — tracking only
```

### `AccountSubtype`

```
-- BANK subtypes --
CONTA_CORRENTE          — Checking account
CONTA_POUPANCA          — Savings account
CONTA_SALARIO           — Salary account (restricted use)
CONTA_DIGITAL           — Digital bank account (Nubank, C6, Inter, PicPay)
CONTA_CONJUNTA          — Joint account

-- CREDIT subtypes --
CREDIT_CARD             — Standard credit card (cartão de crédito)
CHARGE_CARD             — Charge card (full payment required monthly)

-- PAYMENT subtypes --
PREPAID_CARD            — Prepaid card (cartão pré-pago)
VOUCHER_CARD            — Benefit voucher card (VR Alimentação, Alelo, Sodexo, Ticket)

-- CASH subtypes --
WALLET                  — Physical cash wallet (carteira)

-- INVESTMENT subtypes --
BROKERAGE               — Brokerage account (corretora)
FIXED_INCOME            — Fixed income (CDB, LCI, LCA, Tesouro Direto)
PENSION                 — Pension (previdência privada)
CRYPTO                  — Cryptocurrency

-- LOAN subtypes --
PERSONAL_LOAN           — Personal loan (empréstimo pessoal)
MORTGAGE                — Real estate financing (financiamento imobiliário)
AUTO_LOAN               — Vehicle financing (financiamento de veículo)

-- ASSET subtypes --
PROPERTY                — Real estate (imóvel)
VEHICLE                 — Vehicle (veículo)
OTHER_ASSET             — Other physical asset
```

> **Note on `VOUCHER_CARD`**: VR Alimentação, Alelo, Sodexo, and Ticket cards are extremely common in Brazil and absent from international apps. This subtype is mandatory for the Brazilian market.

### `CreditCardNetwork`

Aligned with OFB `creditCardNetwork` enum.

```
VISA
MASTERCARD
AMERICAN_EXPRESS
DINERS_CLUB
HIPERCARD
ELO
BANDEIRA_PROPRIA        — Proprietary/store network
OUTRAS                  — Other networks
```

### `CreditCardLevel`

```
STANDARD
GOLD
PLATINUM
INFINITE
BLACK
```

### `ConnectionStatus`

```
CONNECTED               — Active Open Finance connection, data in sync
SYNCING                 — Sync in progress
DISCONNECTED            — Connection was removed by user
ERROR                   — Sync failed (see error details)
CONSENT_EXPIRING        — Consent expiry within 30 days (prompt re-auth)
CONSENT_EXPIRED         — Consent expired, re-authorization required
```

---

## BIN Enrichment Rules

- On card creation, accept the BIN (first 6 or 8 digits) if the user provides it.
- Lookup BIN in local CSV or BIN service to enrich `creditCardNetwork`, `issuerName`, `issuerCountry`, and `creditCardLevel`.
- If lookup fails, keep user-provided values; `bin` is stored but enrichment fields remain null.
- `themeColor` defaults by network (purple for Nubank, blue for Itaú, etc.) and can be overridden by the user.

---

## Initial Balance Rule

When a user creates an account with a non-zero opening balance, the system must automatically create an "opening balance" transaction:

```
transactionType: OPENING_BALANCE
description: "Saldo inicial"
amount: <entered balance>
type: CREDIT (if positive) | DEBIT (if negative)
date: account creation date
status: POSTED
```

The account balance is always **derived from transaction history** — it is never stored as a mutable field. This is the YNAB and Pluggy standard and is essential for maintaining ledger integrity.

---

## UI Requirements

### Account Creation — Two-Path Flow

Account creation always starts with an explicit fork:

```
"Como você quer adicionar sua conta?"

[ Conectar via Open Finance ]      [ Adicionar manualmente ]
  Sync automático de              Insira os dados
  extrato e saldo.                da sua conta.
```

For Phase 1, only "Adicionar manualmente" is active. The "Conectar via Open Finance" button is visible but shows a "Em breve" state — this primes users for the feature and signals the product direction.

### Manual Account Creation Flow

1. **Type selector**: visual grid of account type tiles with icons. Order: Conta Corrente, Conta Digital, Poupança, Cartão de Crédito, Vale/Benefício, Dinheiro, Investimento, Outros.

2. **Institution picker** (for BANK and CREDIT): searchable dropdown from the existing BACEN institution list. Pre-fills `issuerName`, `logoKey`, `issuerCompeCode`. Even though there is no Open Finance sync, the institution association enables proper `AccountBadge` rendering and future connection.

3. **Type-specific fields** — shown conditionally:

   **BANK (all subtypes)**:
   - Name (pre-filled: "Itaú Corrente")
   - Opening balance ("Saldo atual")
   - Currency (defaults BRL; show advanced toggle for foreign currency)
   - Include in net worth? (toggle, default: on)

   **CREDIT (credit card)**:
   - Name
   - BIN / last 4 digits (triggers BIN enrichment)
   - Credit limit
   - Closing day (dia de fechamento, 1–28) — **required**
   - Due day (dia de vencimento, 1–28) — **required**
   - Current outstanding balance (creates opening balance transaction)
   - Include in net worth? (toggle, default: off — outstanding balance is a liability)

   **PAYMENT (voucher/prepaid)**:
   - Name
   - Balance
   - Card network (optional)
   - Include in net worth? (toggle, default: on)

   **CASH**:
   - Name (default: "Carteira")
   - Current balance

4. **Confirmation**: summary of account details before saving.

### Account List / Carousel

- Horizontal card carousel with next/previous navigation.
- Each card (`CardBadge`) shows: name, masked number (last 4), institution logo, network logo, theme color.
- "Todas as contas" (All Accounts) is a virtual first card showing aggregate balance.
- "Adicionar conta" action as the last item in the carousel.
- Below carousel: compact account list (`AccountBadge`) with balance per account.

### `AccountSelector` (shared component)

- Compact dropdown for use in transaction filters and sidebar.
- First option: "Todas as contas" with a sum icon.
- Each option shows: institution logo (small), account name, current balance.
- Supports keyboard navigation.

---

## API Endpoints

```
POST   /personal-finance/accounts          — create account
GET    /personal-finance/accounts          — list accounts (with balances)
GET    /personal-finance/accounts/{id}     — get account by id
PATCH  /personal-finance/accounts/{id}     — update account
DELETE /personal-finance/accounts/{id}     — delete account (soft-delete)
```

---

## Shared Components

| Component | Description |
|---|---|
| `AccountSelector` | Compact selector with "Todas as contas" + per-account options |
| `AccountBadge` | List item: institution logo, name, type label, balance |
| `CardBadge` | Visual card: theme color, network logo, institution logo, name, masked number |
| `AccountTypeIcon` | Icon + label for account type tile in the creation flow |

---

## User Flows

1. **Create account**: tap "Adicionar conta" → choose type → pick institution → fill fields → confirm.
2. **Edit account**: tap account in list → edit fields → save.
3. **Delete account**: tap account → delete with confirmation dialog → soft-delete (data retained for transaction history).
4. **Select account**: use `AccountSelector` in transaction filter or sidebar.

---

## Error Handling

- Validate BIN format: 6 or 8 digits only.
- Validate `closingDay` and `dueDay`: integer between 1 and 28.
- Validate account name uniqueness per user.
- Return structured errors: `{ field, code, message }`.

---

## Testing

- API: CRUD endpoints, validation errors, BIN enrichment success and fallback.
- UI: carousel navigation, type-conditional form fields, institution picker.
- Snapshot: `CardBadge` with all network types and theme colors.

---

## Done Criteria

- All CRUD endpoints available and documented.
- Two-path creation flow UI built (manual path active; Open Finance path shows "Em breve").
- Carousel and selector UI wired to API.
- Type-specific form fields shown correctly per account type.
- BIN enrichment working with fallback.
- Initial balance transaction created automatically on account creation.
- Shared components exported and reusable.
- Tests passing for API and UI.

---

## Resolved Design Decisions

| Decision | Resolution | Rationale |
|---|---|---|
| Soft-delete or hard-delete? | **Soft-delete** | Accounts are referenced by transactions; deleting breaks history |
| Multiple cards per account, or cards as separate accounts? | **Separate account entities** | Aligns with OFB/Pluggy model; each card has its own balance and bill cycle |
| Signed amounts or direction + positive amount? | **Direction + positive** (`DEBIT`/`CREDIT`) | Avoids negative number confusion; aligns with OFB and Pluggy |
| `closingDay`/`dueDay` on root or nested? | **Nested in `creditData`** | Cleanly separates bank-account fields from card-specific fields |
| `VOUCHER_CARD` subtype? | **Yes, included** | VR/Alelo/Sodexo ecosystem is too prevalent in Brazil to omit |

## Open Questions

- Should `INVESTMENT` and `ASSET` account types be delivered in Phase 1 (form only, no transaction tracking) or deferred?
- Should `includeInNetWorth` default differ by subtype (e.g., off for `LOAN`, on for `BANK`)?
