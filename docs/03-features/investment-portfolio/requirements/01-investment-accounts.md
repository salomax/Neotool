# Investment Accounts — Requirements Document

**Service:** Invistus Banking Service
**Module:** Investment Portfolio — Investment Accounts
**Document date:** 2026-04-20

---

## 1. Overview

Investment accounts are `FinancialAccount` entities with `type = INVESTMENT` or `type = BENEFIT`. They represent custody accounts (corretoras, bancos de investimento, previdência, exchanges de cripto) that hold the user's investment positions.

The user can:

- **Create** an investment custody account from the investments page via a dedicated drawer (`CreateInvestmentAccountDrawer`).
- **List** all investment accounts in the sidebar of the investments page (type `INVESTMENT` + `BENEFIT`).
- **Select** a single account to scope the portfolio view, or view a consolidated view across all accounts.
- **Create the first holding** in an account using the "Registrar ativo" flow (see `05-fixed-income.md`, `07-equities.md`, etc.).

The investment account drawer is **completely separate** from the banking account drawer (`CreateFinancialAccountDrawer`). There is no type-selection step — the user is always creating an investment account in this context.

---

## 2. Account Subtypes

Investment accounts use the `FinancialAccountSubtype` enum. Subtypes exposed in the `CreateInvestmentAccountDrawer`:

| Subtype | Label | Notes |
|---|---|---|
| `BROKERAGE` | Corretora de Valores | General brokerage (XP, BTG, Rico, etc.) |
| `FIXED_INCOME` | Banco (Renda Fixa) | Bank issuing fixed income instruments directly |
| `PENSION` | Previdência Privada | PGBL / VGBL accounts |
| `CRYPTO` | Exchange de Cripto | Cryptocurrency exchange accounts |

`BENEFIT` accounts (FGTS, INSS) are not created via this drawer. They are pre-existing accounts loaded alongside `INVESTMENT` accounts in the sidebar filter.

---

## 3. Business Rules

### 3.1 Issuer Resolution

- Either `issuerId` (known institution from catalogue) or a free-text `issuerName` must be provided. Providing neither is rejected.
- When `issuerId` is provided, `issuerName` is populated from the issuer record — user input is overridden.
- When `issuerId` is null and `issuerName` is provided, it is stored as a custom institution.
- Account name defaults to the resolved issuer name when left blank. If neither resolves, creation is rejected.

### 3.2 Account Name

- Blank name → defaults to issuer name at creation.
- If the user provides a name, it is stored verbatim.
- On update, blank name preserves the existing name.

### 3.3 No Initial Balance

The `CreateInvestmentAccountDrawer` does **not** expose an `initialBalance` field. Investment account cash balances are built up via `InvestmentCashService` (manual cash deposits) and as side effects of investment transactions.

### 3.4 One Holding Constraint

There is a DB-level unique constraint on `(financialAccountId, assetId)`. Each investment account can hold at most one `Holding` per asset. Multiple purchases of the same asset increment the same holding via weighted average cost recalculation.

### 3.5 Account Type Lock

Once created as `type = INVESTMENT`, the account type cannot be changed. The general `FinancialAccountService.update()` enforces this via the BANK / non-BANK boundary rule. Since `INVESTMENT ≠ BANK`, any attempt to change to `BANK` is rejected.

### 3.6 Soft Delete

Investment accounts are never hard-deleted. `deleted_at IS NULL` is enforced on all active lookups.

---

## 4. User Scenarios

**Scenario 1 — Create a brokerage account with a known issuer**
User opens the investments page, clicks "Criar conta de investimento" in the empty state or sidebar. The drawer opens without a type-selection step. User selects subtype "Corretora de Valores", chooses "XP Investimentos" from the issuer autocomplete, leaves the name blank, and clicks "Criar". The account is created with name = "XP Investimentos".

**Scenario 2 — Create an account with a custom institution name**
User selects "Corretora de Valores", chooses the "Custom" sentinel from the issuer field, types "Modal Mais" as institution name. The account is created with `issuerName = "Modal Mais"` and `issuerId = null`.

**Scenario 3 — Create a crypto exchange account**
User selects subtype "Exchange de Cripto", chooses "Binance" from the issuer autocomplete (or types a custom name). Account is created with `subtype = CRYPTO`.

**Scenario 4 — Create a pension account**
User selects "Previdência Privada", selects issuer (e.g., "Bradesco Vida e Previdência"). Account is created with `subtype = PENSION`.

**Scenario 5 — View accounts in sidebar**
After creating accounts, the sidebar shows all `INVESTMENT` and `BENEFIT` accounts. The user can click any account to scope the portfolio view to that account, or choose the consolidated "Todas as contas" option.

**Scenario 6 — No accounts (empty state)**
When the user has no investment accounts, the page shows a full-page empty state with a prominent "Criar conta de investimento" CTA. The sidebar is not shown in this state.

**Scenario 7 — Account has holdings but no cash balance**
After the account is created and holdings are recorded, the sidebar shows the account. The main content shows the holding grid; cash balance defaults to zero until a manual deposit or investment transaction side effect credits the account.

---

## 5. Validations

| Field | Rule | Enforced in |
|---|---|---|
| `subtype` | Must be one of the 4 investment subtypes | GraphQL enum + DB CHECK |
| `issuerId` or `issuerName` | At least one must be provided | Service layer |
| Account name | Must resolve to non-blank | Service layer |
| `type` | Must be `INVESTMENT` | Fixed in `CreateInvestmentAccountDrawer` |

---

## 6. Open Questions / Gaps

**6.1 Edit investment account not exposed in the investments UI.**
The `updateFinancialAccount` mutation exists and supports investment accounts, but the investments page has no "edit account" flow. Editing an investment account requires using the banking module's account drawer (out of context) or direct API access.

**6.2 Delete / deactivate investment account is not implemented.**
No `deleteFinancialAccount` GraphQL mutation exists. Soft-delete capability (`deleted_at` column) is in the schema but not exposed in any UI or API for investment accounts.

**6.3 `BENEFIT` account creation is not in scope.**
FGTS and INSS accounts are pre-seeded or created via a separate flow not documented here. The `CreateInvestmentAccountDrawer` does not create `BENEFIT` accounts.

**6.4 No quota enforcement on account creation.**
The service has a planned quota (FREE: 5, BASIC: 20, PRO: 100) that is not yet enforced.
