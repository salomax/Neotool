# Test Scenarios: Fundos de Investimento

**Requirements ref:** `06-funds.md`
**Document date:** 2026-04-20

---

## Aplicação / PURCHASE + SUBSCRIPTION — Happy Paths

### SC-01: First application in a CVM fund (MARKET_DATA mode) — creates PURCHASE

**Setup:** RecordFundHoldingDrawer, INFLOW mode, account selected

| Field         | Value              |
|---|---|
| Fund          | Núcleo FIC FIM (by CNPJ/name) |
| Pricing mode  | MARKET_DATA        |
| Trade date    | 2026-03-01         |
| Quantity      | 1 234,56           |
| Unit price    | 10,50              |
| Gross value   | 12 962,88          |

**Transaction input:** `{ type: PURCHASE, direction: INFLOW, quantity: 1234.56, unitPrice: 10.50, grossValue: 12962.88, source: MANUAL }`

**Asserts:**
- Fund asset found-or-created by `fundCnpj`
- New holding: `pricingMode=MARKET_DATA, quantity=1234.56, averageCost=10.50`

---

### SC-02: Additional application to same fund — type changes to SUBSCRIPTION

**Setup:** holding 1 234,56 cotas @ averageCost=10,50

**Buy:** 500 cotas @ 10,80

**Transaction input:** `{ type: SUBSCRIPTION }` (not PURCHASE)

**Expected averageCost:** `(1234.56 × 10.50 + 500 × 10.80) / 1734.56 = R$ 10,59`

---

### SC-03: Record a PGBL with manual valuation (MANUAL mode)

**Setup:** RecordFundHoldingDrawer, pricing mode = MANUAL

| Field            | Value      |
|---|---|
| Trade date       | 2026-04-01 |
| Gross value      | 50 000,00  |
| Valued at (date) | 2026-03-31 |
| Manual value     | 50 000,00  |

**Asserts:**
- Holding: `pricingMode=MANUAL, manualGrossValue=50000, manualValuedAt=2026-03-31`
- UI shows "estimado" indicator with reference date

---

### SC-04: MARKET_DATA vs MANUAL — conditional required fields

| Mode        | Quantity | Unit price | Valued at | Manual value |
|---|---|---|---|---|
| MARKET_DATA | Required | Required   | Not shown | Not shown    |
| MANUAL      | Not required | Not required | Required | Required |

---

## Resgate / SALE

### SC-05: Record partial fund redemption

**Setup:** holding 1 234,56 cotas @ averageCost=10,50

| Field             | Value      |
|---|---|
| Trade date        | 2026-04-10 |
| Quantity redeemed | 500        |
| Net value         | 5 200,00   |

**Transaction input:** `{ type: SALE, direction: OUTFLOW, quantity: 500, netValue: 5200 }`

**Asserts:**
- CREDIT R$ 5 200 on custodian account
- Holding: `quantity → 734.56`, `averageCost = 10.50` (unchanged)

---

### SC-06: Net value is always user-confirmed — no auto-fill override

**Setup:** fund latest VNA = R$ 10,80; user enters 500 cotas

**Asserts:**
- Form may suggest `500 × 10.80 = R$ 5 400` but field is editable
- Saved `netValue` = whatever user confirms (e.g., R$ 5 200)

---

### SC-07: Gross value is optional on resgate

**Asserts:**
- Form valid when gross value left blank, only net value provided
- `recordInvestmentTransaction` called with `grossValue = null` or omitted

---

## Come-cotas / WITHHOLDING_TAX

### SC-08: Record come-cotas — quota deduction on May 15

**Setup:** MUTUAL_FUND holding, `anbimaCategory = RENDA_FIXA`

| Field            | Value     |
|---|---|
| Trade date       | 2026-05-15 |
| IR value         | 425,00    |
| Quotas deducted  | 40,238    |

**Transaction input:** `{ type: WITHHOLDING_TAX, direction: OUTFLOW, quantity: 40.238, unitPrice: 425/40.238 }`

**Asserts:**
- Holding `quantity` decreases by 40,238
- **NO cash ledger entry created** (`CustodianCashLedgerHandler` skips `WITHHOLDING_TAX`)
- Account cash balance UNCHANGED
- Quotas auto-calculated from `irValue / currentVNA`; user can confirm or override

---

### SC-09: Come-cotas applicability by fund category

| anbimaCategory | Come-cotas applies? |
|---|---|
| RENDA_FIXA     | ✅                  |
| MULTIMERCADO   | ✅                  |
| CAMBIAL        | ✅                  |
| ACOES          | ❌                  |

**Validation failure:** asset type ≠ MUTUAL_FUND → "Come-cotas aplicável apenas para fundos de investimento"

---

### SC-10: Come-cotas validation failures

| Scenario                            | Expected error                              |
|---|---|
| `irValue = 0`                      | "Valor do IR deve ser maior que zero"        |
| `quotasDeducted = 0`               | Validation error: must be > 0               |
| `quotasDeducted > holding.quantity`| "Quantidade de cotas insuficiente"           |

---

## Transferência de Cotas / QUOTA_TRANSFER

### SC-11: Transfer full mutual fund position — uses QUOTA_TRANSFER

**Setup:** 1 234,56 cotas @ averageCost=10,50 on "XP"; destination "BTG"

**Asserts:**
- `TransferCustodyService` detects `assetType = MUTUAL_FUND`
- Transaction type = `QUOTA_TRANSFER` (NOT `CUSTODY_TRANSFER`)
- `QUOTA_TRANSFER OUT` XP: `quantity=1234.56`
- `QUOTA_TRANSFER IN` BTG: `quantity=1234.56, averageCost=10.50` (preserved)

---

### SC-12: QUOTA_TRANSFER vs CUSTODY_TRANSFER — type selection by asset

| Asset type     | Transaction type used  |
|---|---|
| MUTUAL_FUND    | QUOTA_TRANSFER         |
| PGBL / VGBL    | QUOTA_TRANSFER         |
| STOCK / ETF / BDR | CUSTODY_TRANSFER   |
| REAL_ESTATE_FUND  | CUSTODY_TRANSFER   |
| CRYPTO         | CUSTODY_TRANSFER       |
| CDB (fixed income) | CUSTODY_TRANSFER  |

---

## Valuation

### SC-13: Fund valuation by pricing mode

| Pricing mode | `valuationSource` | `grossValue`                      |
|---|---|---|
| MARKET_DATA  | MARKET_DATA       | `quantity × latestVNA`            |
| MANUAL       | MANUAL            | `manualGrossValue`                |
| No VNA data  | UNAVAILABLE       | Falls back to `averageCost × qty` |

**Asserts for MANUAL:**
- `valuationReferenceDate = manualValuedAt`
- UI shows "estimado" indicator

---

## Validation — Negative Cases

| SC    | Trigger                                        | Expected error                         | Mutation called? |
|---|---|---|---|
| SC-14 | No pricing mode selected                       | Validation error on pricing mode field | No               |
| SC-15 | MARKET_DATA + gross value = 0                 | "Valor deve ser maior que zero"         | No               |
| SC-16 | SALE: net value > gross value                 | Service error                           | No               |
| SC-17 | SALE: quantity > holding.quantity             | "Quantidade insuficiente"               | No               |
| SC-18 | MANUAL mode + valuedAt blank                  | "Data de valorização é obrigatória"     | No               |
| SC-19 | MANUAL mode + manual gross value blank        | "Valor estimado é obrigatório"          | No               |
