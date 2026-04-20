# Test Scenarios: Ações, ETFs e BDRs

**Requirements ref:** `07-equities.md`
**Document date:** 2026-04-20

---

## Compra / PURCHASE — Happy Paths

### SC-01: Buy PETR4 — first purchase, new holding

**Setup:** INFLOW tab, BROKERAGE account selected

| Field            | Value      |
|---|---|
| Asset (B3)       | PETR4      |
| Trade date       | 2026-04-01 |
| Settlement date  | 2026-04-03 |
| Quantity         | 100        |
| Unit price       | 38,50      |
| Net value        | 3 845,00   |

**Transaction input:** `{ direction: INFLOW, type: PURCHASE, quantity: 100, unitPrice: 38.50, grossValue: 3850, netValue: 3845, settlementDate: 2026-04-03, source: MANUAL }`

**Asserts:**
- New holding: `quantity=100, averageCost=38.50`
- DEBIT R$ 3 845 on custodian account

---

### SC-02: Second purchase — weighted average cost update

**Setup:** existing holding 100 PETR4 @ averageCost=38.50

**Buy:** 50 more PETR4 @ unit price 42,00

**Expected averageCost:** `(100 × 38,50 + 50 × 42,00) / 150 = R$ 39,67`
**Expected quantity:** 150

---

### SC-03: Buy with broker note fees

| Field                       | Value     |
|---|---|
| Quantity                    | 100       |
| Unit price                  | 38,50     |
| Brokerage fee               | 3,00      |
| Clearing settlement fee     | 0,50      |
| Other taxes (emolumentos)   | 0,25      |
| Net value                   | 3 845,75  |

**Asserts:**
- BrokerNote stored: `{ brokerageFee: 3.00, clearingSettlementFee: 0.50, taxes: 0.25, netValue: 3845.75 }`
- DEBIT = R$ 3 845,75

---

### SC-04: Settlement date defaults to T+2 and is editable

| Action                                 | Expected settlement |
|---|---|
| Trade date = 2026-04-01 (no override)  | 2026-04-03          |
| User changes to 2026-04-04             | 2026-04-04          |

---

### SC-05: Asset types accepted in RecordEquityHoldingDrawer

| Asset type        | B3 ticker example | Accepted? |
|---|---|---|
| STOCK             | PETR4             | ✅        |
| ETF               | BOVA11            | ✅        |
| ETF_INTERNATIONAL | IVVB11            | ✅        |
| BDR               | AAPL34            | ✅        |

---

## Venda / SALE

### SC-06: Sell 50 PETR4 — partial position

**Setup:** holding 100 PETR4 @ averageCost=38.50

| Field           | Value      |
|---|---|
| Trade date      | 2026-04-15 |
| Settlement date | 2026-04-17 |
| Quantity        | 50         |
| Unit price      | 42,00      |
| Net value       | 2 095,00   |

**Transaction input:** `{ direction: OUTFLOW, type: SALE, quantity: 50, unitPrice: 42.00, netValue: 2095 }`

**Asserts:**
- CREDIT R$ 2 095 on custodian account
- Holding: `quantity → 50`, `averageCost = 38.50` (unchanged)

---

### SC-07: Sell full position — holding closed, record preserved

**Setup:** 100 PETR4 → SALE of 100

**Asserts:**
- `quantity → 0`; holding record NOT deleted (preserved for history)
- `averageCost` unchanged

---

## Dividendos / DIVIDEND

### SC-08: Record dividend — Brazilian equity (IR-exempt)

**Setup:** holding 100 PETR4

| Field       | Value  |
|---|---|
| Trade date  | 2026-04-20 |
| Gross value | 50,00  |
| Net value   | 50,00  |
| Income tax  | 0,00   |

**Transaction input:** `{ type: DIVIDEND, direction: INFLOW, grossValue: 50, netValue: 50, incomeTax: 0 }`

**Asserts:**
- CREDIT R$ 50 on account
- Holding `quantity` and `averageCost` UNCHANGED

---

## JCP / INTEREST_ON_EQUITY

### SC-09: Record JCP — 15% IRRF at source

**Setup:** holding 200 ITUB4

| Field       | Value   |
|---|---|
| Gross value | 100,00  |
| Net value   | 85,00   |
| Income tax  | 15,00   |

**Transaction input:** `{ type: INTEREST_ON_EQUITY, direction: INFLOW, grossValue: 100, netValue: 85, incomeTax: 15 }`

**Asserts:**
- CREDIT R$ 85 on account; holding unchanged

---

## Desdobramento / STOCK_SPLIT

### SC-10: 3:1 split — quantity triples, cost per share divides

**Setup:** 100 VALE3 @ averageCost=60,00 (total cost=R$ 6 000)

**Event:** STOCK_SPLIT, ratio 3:1

**Expected:** `quantity=300, averageCost=20.00` (total cost preserved at R$ 6 000)
**Asserts:** no cash ledger entry; `grossValue = 0` on the transaction

---

## Grupamento / REVERSE_SPLIT

### SC-11: 1:4 reverse split — quantity quarters, cost per share quadruples

**Setup:** 400 XPML11 @ averageCost=5,00 (total cost=R$ 2 000)

**Event:** REVERSE_SPLIT, ratio 1:4

**Expected:** `quantity=100, averageCost=20.00` (total cost preserved at R$ 2 000)
**Asserts:** no cash ledger entry

---

## Bonificação em Ações / BONUS_SHARES

### SC-12: 10% bonus shares — quantity increases, cost diluted

**Setup:** 100 BBAS3 @ averageCost=50,00 (total cost=R$ 5 000)

**Event:** BONUS_SHARES, `bonusQuantity=10, unitPrice=0`

**Expected:** `quantity=110, averageCost=(100×50)/110 = R$ 45,45`
**Asserts:** no cash ledger entry; unit price stored for IR record-keeping

---

## Subscrição / SUBSCRIPTION

### SC-13: Exercise stock subscription rights

**Setup:** 1 000 VALE3 @ averageCost=55,00

| Field         | Value   |
|---|---|
| Quantity      | 100     |
| Unit price    | 45,00   |
| Net value     | 4 500,00|

**Transaction input:** `{ type: SUBSCRIPTION, direction: INFLOW }`

**Expected averageCost:** `(1000 × 55 + 100 × 45) / 1100 = R$ 54,09`
**Asserts:** DEBIT R$ 4 500 on account

---

## Aluguel de Ações / STOCK_LENDING

### SC-14: Record BTC lending income — ITUB4

**Setup:** holding 500 ITUB4

| Field       | Value  |
|---|---|
| Trade date  | 2026-04-10 |
| Gross value | 250,00 |
| Net value   | 212,50 |
| Income tax  | 37,50  |

**Transaction input:** `{ type: STOCK_LENDING, direction: INFLOW, grossValue: 250, netValue: 212.50, incomeTax: 37.50 }`

**Asserts:**
- CREDIT R$ 212,50 on account
- Holding `quantity` and `averageCost` UNCHANGED

---

### SC-15: Eligible asset types for STOCK_LENDING

| Asset type         | Eligible? |
|---|---|
| STOCK              | ✅        |
| ETF                | ✅        |
| BDR                | ✅        |
| REAL_ESTATE_FUND   | ✅        |
| AGRIBUSINESS_FUND  | ✅        |
| CRYPTO             | ❌ — "Tipo de ativo não suporta aluguel" |
| CDB (fixed income) | ❌        |

---

## Transferência de Custódia / CUSTODY_TRANSFER

### SC-16: Transfer full BBAS3 position — XP → BTG

**Setup:** 200 BBAS3 @ averageCost=55,00 on "XP Corretora"; destination "BTG Pactual"

**Asserts:**
- `CUSTODY_TRANSFER OUT` XP: `quantity=200, transferValue=200×55=11000`
- `CUSTODY_TRANSFER IN` BTG: `quantity=200, averageCost=55.00`
- Transaction type = `CUSTODY_TRANSFER` (NOT `QUOTA_TRANSFER`)
- Both operations atomic (single `@Transactional`)
- No cash ledger entries (non-cash event)

---

### SC-17: Partial transfer (100 of 200 shares)

**Asserts:**
- XP holding: `quantity → 100`
- BTG holding created: `quantity=100, averageCost=55.00`

---

### SC-18: Custody transfer validation failures

| Scenario                                    | Expected error                       |
|---|---|
| Destination = same as source               | "Conta destino deve ser diferente"    |
| Transfer `quantity > source quantity`      | "Quantidade insuficiente"             |
| Destination belongs to different user      | Permission error                      |

---

## Valuation

### SC-19: Equity valuation sources

| Scenario                             | `valuationSource` | `grossValue`            |
|---|---|---|
| Market price available               | MARKET_DATA       | `quantity × latestPrice × priceFactor` |
| Market price unavailable             | UNAVAILABLE       | `quantity × averageCost`              |

---

## Validation — Negative Cases

| SC    | Trigger                                  | Expected error                              | Mutation called? |
|---|---|---|---|
| SC-20 | quantity = 0 on PURCHASE                | "Quantidade deve ser maior que zero"         | No               |
| SC-21 | unit price = 0 on PURCHASE             | "Preço unitário deve ser maior que zero"     | No               |
| SC-22 | SALE quantity > holding.quantity       | "Quantidade insuficiente"                    | No (service)     |
| SC-23 | net value > gross value                | "Valor líquido não pode exceder o bruto"     | No (service)     |
| SC-24 | No asset selected on submit            | Validation error on asset field              | No               |
| SC-25 | OPEN_FINANCE transaction edit attempt  | Edit option not available (read-only)        | N/A              |
