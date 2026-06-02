# Test Scenarios: Criptomoedas

**Requirements ref:** `09-crypto.md`
**Document date:** 2026-04-20

---

## Compra / PURCHASE — Happy Paths

### SC-01: Buy Bitcoin — first purchase, new holding

**Setup:** RecordEquityHoldingDrawer `variant="crypto"`, CRYPTO account (Binance) selected

| Field      | Value      |
|---|---|
| Asset name | Bitcoin    |
| Ticker     | BTC        |
| Trade date | 2026-04-01 |
| Quantity   | 0,05       |
| Unit price | 350 000,00 |
| Net value  | 17 500,00  |

**Calls:**
1. `createInvestmentAsset({ name: "Bitcoin", ticker: "BTC", assetType: CRYPTO })`
2. `recordInvestmentTransaction({ direction: INFLOW, type: PURCHASE, quantity: 0.05, unitPrice: 350000, grossValue: 17500, netValue: 17500, tradeDate: 2026-04-01, settlementDate: 2026-04-01, source: MANUAL })`

**Asserts:**
- New holding: `quantity=0.05, averageCost=350000`
- DEBIT R$ 17 500 on Binance CRYPTO account
- `settlementDate = tradeDate` (D+0 — no settlement cycle for crypto)

---

### SC-02: Second BTC purchase on same exchange — weighted average cost

**Setup:** 0,05 BTC @ averageCost=350 000 on Binance

**Buy:** 0,02 BTC @ 380 000

**Expected:** `quantity=0.07, averageCost=(0.05×350000 + 0.02×380000)/0.07 = R$ 358 571,43`

---

### SC-03: Same ticker on different exchanges creates separate holdings

**Setup:** BTC on "Binance" (0,05 BTC) and BTC on "Coinbase" (0,02 BTC)

**Asserts:**
- Two separate holdings exist for BTC (one per `financialAccountId`)
- Each holding has its own `averageCost` and `quantity`
- Exchange identity = `financialAccountId` of the CRYPTO account

---

### SC-04: Repeat purchase on same exchange updates existing holding (no duplicate)

**Setup:** existing BTC holding on Binance

**Asserts:** same holding is updated via weighted average — no new holding created

---

### SC-05: Network field is optional

**Setup:** valid form without network

**Asserts:** form submits; `recordInvestmentTransaction` called without network; success

---

### SC-06: Fractional quantities with high precision accepted

| Quantity entered | Stored in API       |
|---|---|
| 0,05             | 0.05                |
| 0,00432          | 0.00432             |
| 0,00001234       | 0.00001234          |

---

## Crypto Variant — Form Constraint Rules

### SC-07: Broker note fields NOT shown in crypto variant

**Asserts — none of these fields rendered:**
- Brokerage fee, clearing settlement fee, clearing registration fee
- Stock exchange fee, broker note number

---

### SC-08: B3 autocomplete NOT shown — manual asset name/ticker inputs shown instead

**Asserts:**
- `EquityAutocomplete` (B3 search) is NOT rendered
- Free-text "Asset name" and "Ticker" fields ARE rendered

---

### SC-09: Settlement date = trade date (D+0), not exposed to user

**Asserts:**
- No settlement date field shown
- `recordInvestmentTransaction` called with `settlementDate = tradeDate`

---

## Venda / SALE

### SC-10: Sell 0,03 BTC — partial position

**Setup:** 0,07 BTC @ averageCost=358 571,43 on Binance

| Field       | Value       |
|---|---|
| Trade date  | 2026-05-01  |
| Quantity    | 0,03        |
| Unit price  | 400 000,00  |
| Net value   | 12 000,00   |

**Transaction input:** `{ direction: OUTFLOW, type: SALE, quantity: 0.03, netValue: 12000 }`

**Asserts:**
- CREDIT R$ 12 000 on Binance account
- Holding: `quantity → 0.04`, `averageCost = 358571.43` (unchanged)

---

### SC-11: SALE with IR on capital gain

| Gross    | Net       | IR       |
|---|---|---|
| 12 000,00| 10 800,00 | 1 200,00 |

**Transaction input:** `{ grossValue: 12000, netValue: 10800, incomeTax: 1200 }`

---

## Transferência de Custódia / CUSTODY_TRANSFER

### SC-12: Transfer BTC from Binance to Coinbase

**Setup:** 0,07 BTC @ averageCost=358 571,43 on Binance; destination Coinbase

**Asserts:**
- `CUSTODY_TRANSFER OUT` Binance: `quantity=0.07`
- `CUSTODY_TRANSFER IN` Coinbase: `quantity=0.07, averageCost=358571.43`
- Transaction type = `CUSTODY_TRANSFER`
- `averageCost` preserved; atomic operation

---

### SC-13: Partial crypto transfer

**Setup:** 0,07 BTC → transfer 0,04 to Coinbase

**Asserts:**
- Binance: `quantity → 0.03`
- Coinbase: new holding `quantity=0.04, averageCost=358571.43`

---

## Stock Lending — NOT Available for Crypto

### SC-14: Stock lending rejected for CRYPTO

**Asserts:**
- "Aluguel de ações" option NOT shown in crypto holding context menu
- Backend: `RecordStockLendingService` returns error if attempted via API (CRYPTO not in `LENDABLE_ASSET_TYPES`)

---

## Valuation

### SC-15: Crypto valuation sources

| Scenario                     | `valuationSource` | `grossValue`                |
|---|---|---|
| CoinGecko price available    | MARKET_DATA       | `quantity × latestPrice`    |
| CoinGecko price unavailable  | UNAVAILABLE       | `quantity × averageCost`    |

---

### SC-16: Multiple holdings same coin shown separately in portfolio

**Setup:** BTC on Binance (0,05) + BTC on Coinbase (0,02)

**Asserts:**
- Two separate rows in the Cripto asset group card
- Each shows its own `grossValue`, `averageCost`, and `quantity`

---

## Validation — Negative Cases

| SC    | Trigger                                  | Expected error                            | Mutation called? |
|---|---|---|---|
| SC-17 | Asset name blank                        | "Nome do ativo é obrigatório"              | No               |
| SC-18 | Ticker blank                            | "Ticker é obrigatório"                     | No               |
| SC-19 | Quantity = 0                            | "Quantidade deve ser maior que zero"       | No               |
| SC-20 | Unit price = 0                          | Validation error on unit price field       | No               |
| SC-21 | Net value = 0                           | Validation error                           | No               |
| SC-22 | SALE quantity > holding.quantity        | "Quantidade insuficiente"                  | No (service)     |
| SC-23 | Stock lending attempted for CRYPTO      | "Tipo de ativo não suporta aluguel"        | No (service)     |
