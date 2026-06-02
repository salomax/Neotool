# Test Scenarios: FIIs e Fundos de Agronegócio

**Requirements ref:** `08-reits.md`
**Document date:** 2026-04-20

---

## Compra / PURCHASE — Happy Paths

### SC-01: Buy MXRF11 — first purchase, new holding

**Setup:** INFLOW tab, BROKERAGE account selected

| Field            | Value      |
|---|---|
| Asset (B3)       | MXRF11     |
| Trade date       | 2026-04-10 |
| Settlement date  | 2026-04-14 |
| Quantity (cotas) | 500        |
| Unit price       | 10,20      |
| Brokerage fee    | 7,00       |
| Net value        | 5 093,00   |

**Transaction input:** `{ type: PURCHASE, direction: INFLOW, quantity: 500, unitPrice: 10.20, grossValue: 5100, netValue: 5093 }`

**Asserts:**
- New holding: `quantity=500, averageCost=10.20`
- DEBIT R$ 5 093 on custodian account

---

### SC-02: Second purchase — weighted average cost update

**Setup:** 200 HGLG11 @ averageCost=155,00

**Buy:** 100 more @ 165,00

**Expected:** `quantity=300, averageCost=(200×155 + 100×165)/300 = R$ 158,33`

---

### SC-03: Asset autocomplete filters to FII/FIAGRO only

**Setup:** RecordEquityHoldingDrawer opened from FIIs/FIAGROs group

**Asserts:**
- Typing "PETR4" returns no results
- Only `REAL_ESTATE_FUND` and `AGRIBUSINESS_FUND` assets appear

---

### SC-04: Settlement date defaults to T+2

**Trade date:** 2026-04-10 → **Expected settlement:** 2026-04-14

---

## Venda / SALE

### SC-05: Sell 200 MXRF11 — partial position

**Setup:** 500 MXRF11 @ averageCost=10,20

| Field       | Value      |
|---|---|
| Quantity    | 200        |
| Net value   | 2 195,00   |

**Asserts:**
- CREDIT R$ 2 195 on account
- Holding: `quantity → 300`, `averageCost = 10.20` (unchanged)

---

## Rendimentos / DIVIDEND

### SC-06: Record monthly rendimento — qualifying FII (IR-exempt)

**Setup:** 500 MXRF11 on "XP Corretora"

| Field       | Value      |
|---|---|
| Trade date  | 2026-04-15 |
| Gross value | 50,00      |
| Net value   | 50,00      |
| Income tax  | 0,00       |

**Transaction input:** `{ type: DIVIDEND, direction: INFLOW, grossValue: 50, netValue: 50, incomeTax: 0 }`

**Asserts:**
- CREDIT R$ 50 on account
- Holding `quantity` and `averageCost` UNCHANGED
- System does NOT enforce IR exemption criteria — user enters values as received

---

### SC-07: Record rendimento for non-qualifying FII — IR applies

| Gross | Net   | IR    |
|---|---|---|
| 100,00| 85,00 | 15,00 |

**Asserts:** CREDIT R$ 85; `incomeTax=15` stored

---

## Amortização de Cota / AMORTIZATION

### SC-08: Record FII amortization — happy path

**Setup:** 200 HGLG11 (`REAL_ESTATE_FUND`)

| Field       | Value      |
|---|---|
| Trade date  | 2026-04-01 |
| Gross value | 200,00     |
| Net value   | 200,00     |
| Income tax  | 0,00       |

**Transaction input:** `{ type: AMORTIZATION, grossValue: 200, netValue: 200 }`

**Asserts:**
- CREDIT R$ 200 on account
- Holding `quantity` and `averageCost` UNCHANGED (V1 simplification)

---

### SC-09: Amortization eligibility

| Asset type         | Eligible? |
|---|---|
| REAL_ESTATE_FUND   | ✅        |
| AGRIBUSINESS_FUND  | ✅        |
| STOCK              | ❌ — "Tipo não suporta amortização" |

---

### SC-10: Amortization validation failures

| Scenario                   | Expected error                             |
|---|---|
| `grossValue = 0`          | "Valor bruto deve ser maior que zero"       |
| `netValue > grossValue`   | "Valor líquido não pode exceder o bruto"    |

---

## Subscrição / SUBSCRIPTION

### SC-11: FII rights offering subscription

**Setup:** 200 HGLG11 @ averageCost=155,00

| Field       | Value      |
|---|---|
| Quantity    | 10         |
| Unit price  | 160,00     |
| Net value   | 1 600,00   |

**Transaction input:** `{ type: SUBSCRIPTION, direction: INFLOW }`

**Expected averageCost:** `(200×155 + 10×160) / 210 = R$ 155,24`
**Asserts:** DEBIT R$ 1 600 on account

---

## Desdobramento / Grupamento

### SC-12: 2:1 FII split

**Setup:** 100 XPML11 @ averageCost=120,00 (total cost=R$ 12 000)

**Expected:** `quantity=200, averageCost=60.00` (total cost preserved)
**Asserts:** no cash ledger entry

---

### SC-13: 1:2 FII reverse split

**Setup:** 200 XPML11 @ averageCost=60,00 (total cost=R$ 12 000)

**Expected:** `quantity=100, averageCost=120.00` (total cost preserved)
**Asserts:** no cash ledger entry

---

## Aluguel de Cotas / STOCK_LENDING

### SC-14: Record BTC lending for a FII

**Setup:** 1 000 MXRF11

| Gross  | Net   | IR    |
|---|---|---|
| 80,00  | 68,00 | 12,00 |

**Asserts:**
- `RecordStockLendingService` accepts (FII in `LENDABLE_ASSET_TYPES`)
- CREDIT R$ 68; holding unchanged

---

### SC-15: FIAGRO also eligible for stock lending

**Asserts:** `AGRIBUSINESS_FUND` accepted by `RecordStockLendingService`

---

## Transferência de Custódia / CUSTODY_TRANSFER

### SC-16: Transfer full MXRF11 position — XP → Rico

**Setup:** 200 MXRF11 @ averageCost=10,20 on "XP Corretora"; destination "Rico Investimentos"

**Asserts:**
- Transaction type = `CUSTODY_TRANSFER` (NOT `QUOTA_TRANSFER` — that's only for `MUTUAL_FUND`)
- `CUSTODY_TRANSFER OUT` XP: `quantity=200`
- `CUSTODY_TRANSFER IN` Rico: `quantity=200, averageCost=10.20`
- Atomic operation; `averageCost` preserved

---

## Valuation

### SC-17: FII valuation sources

| Scenario                    | `valuationSource` | `grossValue`                          |
|---|---|---|
| B3 price available          | MARKET_DATA       | `quantity × latestPrice`              |
| B3 price unavailable        | UNAVAILABLE       | `quantity × averageCost`              |

**Asserts:** `incomeTax = 0` in valuation (rendimento IR exemption; no IR provisioning in V1)
