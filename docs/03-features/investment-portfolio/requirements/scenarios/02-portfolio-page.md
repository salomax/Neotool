# Test Scenarios: Portfolio Page

**Requirements ref:** `02-portfolio-page.md`
**Document date:** 2026-04-20

---

## Empty States

### SC-01: No accounts → full-page empty state

**Setup:** user has no investment accounts

**Asserts:**
- Sidebar NOT rendered
- Full-page empty state shown with "Criar conta de investimento" CTA
- `InvestmentSummary` NOT rendered

---

### SC-02: Account exists but no holdings → account-scoped empty state

**Setup:** one account "XP Corretora", no holdings

**Asserts:**
- Grid shows "Nenhum ativo registrado" with "Registrar ativo" CTA
- `InvestmentSummary` shows all-zero values

---

### SC-03: Search returns no results

**Setup:** holdings exist; user types query matching nothing

**Asserts:** Grid shows "Nenhum ativo encontrado"; search query visible in message

---

## Account Selector (Sidebar)

### SC-04: Sidebar lists INVESTMENT + BENEFIT accounts; excludes BANK

**Setup:** 2 × INVESTMENT + 1 × BENEFIT "FGTS" + 1 × BANK "Nubank"

**Asserts:**
- Sidebar shows: XP, BTG, FGTS, "Todas as contas"
- "Nubank" NOT listed

---

### SC-05: First account selected by default on page load

**Setup:** accounts created in order: "XP" then "BTG"

**Asserts:**
- "XP" selected on load
- `investmentPortfolioSummary(financialAccountId: <XP id>)` called

---

### SC-06: Selecting account scopes summary and grid

**Action:** click "BTG Pactual" in sidebar

**Asserts:**
- `investmentPortfolioSummary(financialAccountId: <BTG id>)` called
- Grid queries scoped to BTG holdings only

---

### SC-07: "Todas as contas" → consolidated view

**Action:** click "Todas as contas"

**Asserts:**
- `investmentPortfolioSummary(financialAccountId: null)` called
- Grid shows holdings from all accounts

---

### SC-08: "+ Criar conta" opens CreateInvestmentAccountDrawer

**Asserts:** drawer opens (not the banking account drawer)

---

## InvestmentSummary Panel

### SC-09: Summary metrics computed correctly

**Setup:** 100 PETR4 @ averageCost=38.50, grossValue=4200 (market); cashBalance=5000

**Expected values:**

| Metric              | Value                             |
|---|---|
| totalPortfolioValue | R$ 9 200,00 (4200 + 5000)        |
| totalCostBasis      | R$ 3 850,00 (100 × 38.50)        |
| grossGain           | R$ 350,00 (4200 − 3850)          |
| grossGainPercent    | 9,09% (350 / 3850)               |
| cashBalance         | R$ 5 000,00                       |

---

### SC-10: Loading state shows skeleton placeholders

**Asserts:** while `investmentPortfolioSummary` query in flight, skeleton placeholders shown; no real values

---

### SC-11: Error state shows retry option

**Asserts:** query error → error state with retry; no stale values shown

---

### SC-12: "estimado" warning chip when any holding is ESTIMATED or UNAVAILABLE

**Setup:** one CDI holding with `valuationSource = ESTIMATED`

**Asserts:**
- Warning chip "estimado" appears next to totalPortfolioValue
- Tooltip shows reference date of latest indicator

---

### SC-13: Allocation breakdown — only groups with holdings shown

**Setup:** holdings in Renda Fixa and Cripto only

**Asserts:**
- Breakdown shows: Renda Fixa (%) + Cripto (%)
- Groups with no holdings NOT shown
- Cash group shown if `cashBalance > 0`

---

### SC-14: Consolidated summary aggregates all accounts

**Setup:** Account A grossValue=50 000; Account B grossValue=30 000

**Asserts:** consolidated `totalGrossValue = 80 000`

---

## InvestmentGrid — Asset Group Cards

### SC-15: One AssetGroupCard per asset class with holdings; canonical order

**Setup:** holdings in Cripto and Renda Fixa only

**Asserts:**
- Two cards rendered; Renda Fixa appears before Cripto (canonical order)
- No cards for empty classes

---

### SC-16: Group card header shows total value for that class

**Setup:** two Renda Fixa holdings worth R$ 10 000 + R$ 5 000

**Asserts:** Renda Fixa header shows R$ 15 000

---

### SC-17: Holding row click opens InvestmentHoldingDetailDrawer

**Asserts:** drawer opens for the clicked holding; correct data loaded

---

### SC-18: Context menu shows operations relevant to asset class

| Asset class        | Expected options                                                     |
|---|---|
| STOCK              | Comprar mais, Vender, Dividendos, JCP, Split, Bonificação, Aluguel, Transferir |
| CRYPTO             | Comprar mais, Vender, Transferir (no Aluguel, no Dividendos)         |
| MUTUAL_FUND        | Aplicar, Resgatar, Come-cotas, Transferir cotas                      |
| FIXED_INCOME       | Aportar, Resgatar, Confirmar vencimento, Cupom, Amortização, Transferir |

---

### SC-19: "Registrar ▼" disabled in consolidated view (no account selected)

**Asserts:**
- "Registrar ▼" button disabled when `selectedAccountId = null`
- In single account view: button enabled, opens `InvestmentEventSelectorDrawer`

---

## Holding Detail Drawer

### SC-20: Fixed income detail shows contractual terms and coupon schedule

**Setup:** Tesouro IPCA+ holding with `couponFrequency = SEMI_ANNUAL`

**Asserts:**
- Drawer shows: indexer, rate, maturity date
- Coupon schedule timeline visible
- Transaction history timeline visible

---

### SC-21: Fund detail shows pricing mode and latest VNA

**Setup:** MUTUAL_FUND with `pricingMode = MARKET_DATA`

**Asserts:** fund metadata + VNA + reference date shown

---

## Pending Actions

### SC-22: Badge shows count of visible pending actions

**Setup:** 2 × COUPON_PAYMENT_DUE + 1 × INVESTMENT_MATURED (all `visibleFrom ≤ today`)

**Asserts:** badge shows count = 3

---

### SC-23: `?pendingActions=1` URL param auto-opens PendingActionsDrawer

**Asserts:** drawer open on page load; no user interaction required

---

### SC-24: Click INVESTMENT_MATURED action → MaturityConfirmDrawer

**Asserts:** `MaturityConfirmDrawer` opens pre-filled with holding's `maturityDate`

---

### SC-25: Click COUPON_PAYMENT_DUE action → CouponPaymentConfirmDrawer

**Asserts:** drawer opens pre-filled with next coupon date

---

### SC-26: Actions with `visibleFrom` in the future are hidden

**Setup:** INVESTMENT_MATURED with `visibleFrom = tomorrow`

**Asserts:** action NOT listed in `PendingActionsDrawer` today

---

## Search

### SC-27: Search filters holdings across all group cards simultaneously

| Query entered  | Expected result                                   |
|---|---|
| "banco"        | Only "CDB Banco Inter" shown; others hidden       |
| "PETR"         | Only PETR4 shown                                  |
| "bitcoin" (lc) | Bitcoin shown (case-insensitive)                  |
| "" (cleared)   | All holdings restored                             |
