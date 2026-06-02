# Test Scenarios: Investment Accounts

**Requirements ref:** `01-investment-accounts.md`
**Document date:** 2026-04-20

---

## Create Account — Happy Paths

### SC-01: Create brokerage account with known issuer (name defaults to issuer)

**Setup:** investments page, no accounts exist → click "Criar conta de investimento"

| Field    | Value            |
|---|---|
| Subtype  | BROKERAGE        |
| Issuer   | XP Investimentos |
| Name     | (blank)          |

**Asserts:**
- `createFinancialAccount` called with `{ type: INVESTMENT, subtype: BROKERAGE, issuerId: <XP UUID> }` — name NOT sent
- Account name resolves to "XP Investimentos" (from issuer record)
- Drawer closes; account appears in sidebar

---

### SC-02: Create brokerage account with explicit custom name

**Setup:** drawer open

| Field   | Value                    |
|---|---|
| Subtype | BROKERAGE                |
| Issuer  | XP Investimentos         |
| Name    | Minha Carteira Principal |

**Asserts:**
- `createFinancialAccount` called with `{ name: "Minha Carteira Principal", issuerId: <XP UUID> }`
- Sidebar shows "Minha Carteira Principal"

---

### SC-03: Create account with custom (unknown) institution

**Setup:** drawer open, select "Outro / Personalizado" in issuer field

| Field          | Value      |
|---|---|
| Subtype        | BROKERAGE  |
| Issuer field   | Personalizado |
| Institution    | Modal Mais |

**Asserts:**
- `createFinancialAccount` called with `{ issuerName: "Modal Mais" }` — `issuerId` NOT sent
- Account name = "Modal Mais"

---

### SC-04: Create all four subtypes

| Subtype      | Label PT              | Expected `subtype` |
|---|---|---|
| BROKERAGE    | Corretora de Valores  | BROKERAGE          |
| FIXED_INCOME | Renda Fixa            | FIXED_INCOME       |
| PENSION      | Previdência Privada   | PENSION            |
| CRYPTO       | Criptomoedas          | CRYPTO             |

**Asserts for all:** `type = INVESTMENT` in every call; drawer closes; account visible in sidebar

---

### SC-05: Create second account (sidebar already has accounts)

**Setup:** one account "XP Corretora" exists; click "+" or "Adicionar conta" in sidebar

**Asserts:**
- Drawer opens (same flow as SC-01)
- After creation, sidebar lists both accounts

---

## Form Constraint Rules

### SC-06: Banking and card fields never appear

**Setup:** drawer open, any subtype selected

**Asserts — none of these fields are rendered:**
- Agência / número da conta
- Bandeira / limite / dia de fechamento / dia de vencimento
- Saldo inicial

---

### SC-07: Mutation payload never includes bankData, cardData, or initialBalance

**Setup:** valid form submitted

**Asserts:**
- `createFinancialAccount` input has no `bankData`, `cardData`, `initialBalance`
- `type` is always `INVESTMENT`
- `currencyCode = BRL`, `includeInNetWorth = true`, `includeInDashboard = true`

---

### SC-08: Custom name field only appears when "Outro / Personalizado" selected

**Asserts:**
- Before selecting "Personalizado": free-text institution name field NOT visible
- After selecting "Personalizado": field becomes visible and required

---

### SC-09: `issuerId` takes precedence — `issuerName` not sent when issuer is known

**Setup:** user selects a catalogued issuer (e.g., "Binance")

**Asserts:**
- Payload contains only `issuerId`; `issuerName` is omitted

---

### SC-10: Whitespace-only name treated as blank → defaults to issuer name

**Setup:** Name field = `"   "` (three spaces)

**Asserts:**
- `name` is NOT sent (or sent as undefined)
- Account resolves to issuer name

---

## Sidebar & Account Selection

### SC-11: Sidebar lists INVESTMENT and BENEFIT accounts; excludes BANK

**Setup:** 2 INVESTMENT accounts + 1 BENEFIT "FGTS" + 1 BANK "Nubank"

**Asserts:**
- Sidebar shows: XP, BTG, FGTS, "Todas as contas"
- "Nubank" does NOT appear

---

### SC-12: Selecting account scopes summary and grid

**Asserts:**
- `investmentPortfolioSummary(financialAccountId: <selected>)` called
- Grid shows only that account's holdings

---

### SC-13: "Todas as contas" triggers consolidated view

**Asserts:**
- `investmentPortfolioSummary(financialAccountId: null)` called
- Grid shows holdings from all accounts

---

### SC-14: Newly created account starts with zero cash balance

**Asserts:**
- Selecting the new account: cash balance = R$ 0,00; holdings grid empty

---

## Validation — Negative Cases

| SC   | Trigger                                          | Expected error                                   | Mutation called? |
|---|---|---|---|
| SC-15 | Submit without selecting subtype                | "Tipo de conta é obrigatório"                    | No               |
| SC-16 | Submit with subtype but no issuer/name          | "Informe a corretora ou nome da instituição"     | No               |
| SC-17 | "Personalizado" selected, institution name blank | Validation error on institution name field      | No               |
| SC-18 | Completely empty form                            | Errors on subtype + issuer fields                | No               |
| SC-19 | Server returns error                             | Error alert inside drawer; drawer stays open; form retains values | No |
| SC-20 | Close drawer without submitting (click × or Escape) | No account created; mutation NOT called       | No               |

---

## Edge Cases

### SC-21: BENEFIT accounts visible in sidebar but not creatable via this drawer

**Asserts:**
- Pre-existing "FGTS" appears in sidebar
- "Criar conta de investimento" drawer subtypes list does NOT include BENEFIT options

---

### SC-22: Long institution name (100 chars) stored verbatim

**Asserts:**
- `createFinancialAccount` called with the full institution name
- Account created successfully
