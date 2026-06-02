# Test Scenarios: Renda Fixa

**Requirements ref:** `05-fixed-income.md`
**Document date:** 2026-04-20

---

## Aporte / PURCHASE — Happy Paths

### SC-01: Record a CDI-indexed CDB

**Setup:** INFLOW tab, account selected

| Field              | Value                    |
|---|---|
| Asset type         | CDB                      |
| Asset name         | CDB Banco Inter 115% CDI |
| Issuer             | Banco Inter              |
| Trade date         | 2026-03-01               |
| Issue date         | 2026-03-01               |
| Maturity date      | 2028-03-01               |
| Gross value        | 10 000,00                |
| Indexer            | CDI                      |
| Indexer %          | 115                      |
| Coupon frequency   | AT_MATURITY              |
| Income tax exempt  | false                    |

**Calls:**
1. `createInvestmentAsset({ name, assetType: CDB, issuerName: Banco Inter })`
2. `recordInvestmentTransaction({ direction: INFLOW, type: PURCHASE, quantity: 1, unitPrice: 10000, grossValue: 10000, tradeDate, settlementDate: tradeDate, indexer: CDI, indexerPercentage: 1.15, issueDate, maturityDate, couponFrequency: AT_MATURITY, isTaxExempt: false, source: MANUAL })`

**Asserts:**
- New holding: `quantity=1, averageCost=10000`
- DEBIT R$ 10 000 on custodian account
- `INVESTMENT_MATURED` pending action scheduled `visibleFrom = 2028-03-02`

---

### SC-02: Record an LCI (pre-fixed, tax-exempt auto-checked)

**Setup:** INFLOW tab, select asset type LCI first

**Asserts (form):**
- "Isento de IR" checkbox auto-checked when LCI selected
- Same for LCA, CRI, CRA

| Field             | Value      |
|---|---|
| Indexer           | PRE_FIXADO |
| Fixed annual rate | 14         |
| Coupon frequency  | AT_MATURITY|

**Transaction input:**
- `indexer: PRE_FIXADO, preFixedRate: 0.14, isTaxExempt: true`
- `indexerPercentage` NOT sent

---

### SC-03: Record Tesouro IPCA+ com juros semestrais (NTN-B)

**Setup:** INFLOW tab, select asset type TREASURY

**Asserts (form):**
- Issuer field auto-fills "Tesouro Nacional" and becomes read-only
- "Coupon day of month" field appears (required when frequency ≠ AT_MATURITY)

| Field             | Value         |
|---|---|
| Indexer           | IPCA          |
| Fixed annual rate | 6,0           |
| Coupon frequency  | SEMI_ANNUAL   |
| Coupon day        | 15            |
| Maturity date     | 2035-08-15    |

**Transaction input:** `{ indexer: IPCA, preFixedRate: 0.06, couponFrequency: SEMI_ANNUAL, couponDayOfMonth: 15 }`

**Asserts:**
- `COUPON_PAYMENT_DUE` pending action scheduled `visibleFrom = nextCouponDate − 7 days`

---

### SC-04: Record a Debênture with IPCA+ and grace period

| Field             | Value        |
|---|---|
| Asset type        | DEBENTURES   |
| Indexer           | IPCA         |
| Fixed annual rate | 5,5          |
| Coupon frequency  | SEMI_ANNUAL  |
| Coupon day        | 1            |
| Grace period date | 2027-01-01   |
| Maturity date     | 2030-01-01   |

**Transaction input:** `{ assetType: DEBENTURES, couponFrequency: SEMI_ANNUAL, couponDayOfMonth: 1, gracePeriodDate: 2027-01-01 }`

---

### SC-05: Decimal conversions for indexer rate fields

| Form input       | Sent to API              |
|---|---|
| Indexer % = 115  | `indexerPercentage = 1.15` |
| Indexer % = 120  | `indexerPercentage = 1.20` |
| Fixed rate = 14  | `preFixedRate = 0.14`      |
| Fixed rate = 13,5| `preFixedRate = 0.135`     |

---

### SC-06: Conditional field visibility

| Indexer selected | "% do indexador" | "Taxa (% a.a.)" | "Dia do cupom" |
|---|---|---|---|
| CDI              | visible + required | hidden          | visible when freq ≠ AT_MATURITY |
| PRE_FIXADO       | hidden             | visible + required | visible when freq ≠ AT_MATURITY |
| AT_MATURITY freq | —                  | —               | hidden |
| MONTHLY freq     | —                  | —               | visible + required |

---

## Resgate Antecipado / SALE

### SC-07: Record early redemption — full position

**Setup:** OUTFLOW tab for CDB holding (quantity=1, averageCost=10 000)

| Field        | Value    |
|---|---|
| Trade date   | 2026-06-01 |
| Gross value  | 10 500,00  |
| Net value    | 8 925,00   |
| Income tax   | 1 575,00   |

**Transaction input:** `{ direction: OUTFLOW, type: SALE, grossValue: 10500, netValue: 8925, incomeTax: 1575 }`

**Asserts:**
- CREDIT R$ 8 925 on custodian account
- Holding: `quantity → 0`, `closeReason = REDEEMED_EARLY`
- `INVESTMENT_MATURED` pending action cancelled
- `averageCost` unchanged

---

### SC-08: Sale with IOF (redemption within 30 days)

**Setup:** CDB purchased on 2026-04-01, SALE on 2026-04-25 (24 days)

| Field       | Value    |
|---|---|
| Gross value | 10 020,00|
| Net value   | 9 900,00 |
| IOF         | 120,00   |

**Transaction input:** `{ iof: 120, netValue: 9900 }`

---

## Vencimento / MATURITY

### SC-09: Confirm maturity via pending action — full flow

**Setup:** holding with `maturityDate = yesterday`, `INVESTMENT_MATURED` pending action exists

| Field       | Value     |
|---|---|
| Maturity date | (pre-filled from holding) |
| Gross value | 12 000,00 |
| Net value   | 10 200,00 |
| Income tax  | 1 800,00  |

**Transaction input:** `{ type: MATURITY, direction: OUTFLOW, grossValue: 12000, netValue: 10200 }`

**Asserts:**
- CREDIT R$ 10 200 on custodian account
- Holding: `closedAt = maturityDate, closeReason = MATURED`
- Pending action status → COMPLETED

---

### SC-10: Maturity validation failures

| Scenario                                 | Expected error                              |
|---|---|
| `maturityDate` is tomorrow              | "Data de vencimento ainda não atingida"     |
| No `INVESTMENT_MATURED` pending action   | "Ação pendente de vencimento não encontrada" |
| Holding already closed                   | "Posição já encerrada"                       |
| `quantity = 0` (position closed)        | Error — cannot confirm already closed       |

---

## Pagamento de Cupom / INTEREST_PAYMENT

### SC-11: Confirm coupon payment — NTN-B semi-annual

**Setup:** holding with `couponFrequency = SEMI_ANNUAL`, `COUPON_PAYMENT_DUE` pending action visible

| Field       | Value      |
|---|---|
| Trade date  | 2026-08-15 |
| Gross value | 500,00     |
| Net value   | 425,00     |
| Income tax  | 75,00      |

**Transaction input:** `{ type: INTEREST_PAYMENT, grossValue: 500, netValue: 425, incomeTax: 75 }`

**Asserts:**
- CREDIT R$ 425 on account
- Current `COUPON_PAYMENT_DUE` pending action → COMPLETED
- New `COUPON_PAYMENT_DUE` scheduled for next coupon date
- Holding `quantity` and `averageCost` UNCHANGED
- Valuation accrual clock resets from `2026-08-15`

---

### SC-12: Coupon payment validation failures

| Scenario                           | Expected error                           |
|---|---|
| `couponFrequency = AT_MATURITY`   | "Ativo não possui frequência de cupom"   |
| `grossValue = 0`                  | Validation error: must be > 0            |
| `netValue > grossValue`           | "Valor líquido não pode exceder o bruto" |

---

### SC-13: Coupon pending action visibility schedule

**Asserts:**
- `visibleFrom = nextCouponDate − 7 days`
- Action NOT visible when `today < visibleFrom`
- Action visible when `today >= visibleFrom`

---

## Amortização / AMORTIZATION

### SC-14: Record amortization on a CRI

**Setup:** CRI holding

| Field       | Value    |
|---|---|
| Trade date  | 2026-05-01 |
| Gross value | 1 000,00   |
| Net value   | 1 000,00   |
| Income tax  | 0,00       |

**Transaction input:** `{ type: AMORTIZATION, grossValue: 1000, netValue: 1000 }`

**Asserts:**
- CREDIT R$ 1 000 on custodian account
- Holding `quantity` and `averageCost` UNCHANGED (V1 simplification)

---

### SC-15: Amortizable asset types and rejections

| Asset type    | Amortizable? |
|---|---|
| DEBENTURES    | ✅            |
| CRI           | ✅            |
| CRA           | ✅            |
| TREASURY      | ✅            |
| ETF_FIXED_INCOME | ✅         |
| CDB           | ❌ — "Tipo de ativo não suporta amortização" |
| LCI           | ❌            |

---

### SC-16: Amortization validation failures

| Scenario                       | Expected error                            |
|---|---|
| `grossValue = 0`              | "Valor bruto deve ser maior que zero"     |
| `netValue > grossValue`       | "Valor líquido não pode exceder o bruto"  |

---

## Transferência de Custódia / CUSTODY_TRANSFER

### SC-17: Transfer full CDB position between accounts

**Setup:** CDB holding (quantity=1, averageCost=10 000) on "XP"; destination = "BTG"

**Asserts:**
- `CUSTODY_TRANSFER OUT` on XP: `quantity=1, transferValue=10000`
- `CUSTODY_TRANSFER IN` on BTG: `quantity=1, averageCost=10000`
- Both operations in one transaction (atomic)
- `averageCost` preserved at destination
- Transaction type = `CUSTODY_TRANSFER` (NOT `QUOTA_TRANSFER`)

---

### SC-18: Custody transfer validation failures

| Scenario                                      | Expected error                      |
|---|---|
| Destination = same as source account         | "Conta destino deve ser diferente"   |
| Transfer quantity > source holding quantity  | "Quantidade insuficiente"            |
| Destination belongs to another user          | Permission error                     |

---

## Valuation

### SC-19: Valuation source by indexer type

| Indexer      | `valuationSource` | Notes                          |
|---|---|---|
| PRE_FIXADO   | CALCULATED        | Exact formula                  |
| CDI, SELIC, IPCA, IGP-M | ESTIMATED | Uses latest indicator data |
| OUTROS       | UNAVAILABLE       | Falls back to `averageCost × quantity` |
| (Open Finance)| PROVIDER         | Takes precedence over all computed |

---

### SC-20: IR provisioning — regressive table

| Holding days | IR rate |
|---|---|
| 0–180        | 22,5%   |
| 181–360      | 20%     |
| 361–720      | 17,5%   |
| > 720        | 15%     |

**Asserts:**
- LCI, LCA, CRI, CRA: `incomeTax = 0, iof = 0` (tax-exempt)
- IOF provisioned only when holding < 30 days

---

## Validation — Required Fields

### SC-21: Empty PURCHASE form — all required field errors shown

| Required field   | Error message (example)                    |
|---|---|
| Asset type       | "Tipo de ativo é obrigatório"              |
| Asset name       | "Nome do ativo é obrigatório"              |
| Issuer           | "Emissor é obrigatório"                    |
| Trade date       | "Data da operação é obrigatória"           |
| Issue date       | "Data de emissão é obrigatória"            |
| Maturity date    | "Data de vencimento é obrigatória"         |
| Gross value      | "Valor deve ser maior que zero"            |
| Indexer          | "Indexador é obrigatório"                  |

---

### SC-22: Cross-field validation failures

| Scenario                                     | Expected error                              |
|---|---|
| `maturityDate < issueDate`                  | "Vencimento deve ser após a data de emissão" |
| `grossValue = 0` on PURCHASE               | "Valor deve ser maior que zero"              |
| `netValue > grossValue` on SALE            | "Valor líquido não pode exceder o bruto"     |
| `quantitySold > holding.quantity` on SALE  | "Quantidade insuficiente"                    |
| Coupon day = 32                            | "Dia deve estar entre 1 e 31"               |
| CDI indexer + indexer % blank              | Validation error on indexer % field         |
| PRE_FIXADO + fixed rate blank              | Validation error on fixed rate field        |
