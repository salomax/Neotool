# Ações, ETFs e BDRs — Requirements Document

**Service:** Invistus Banking Service
**Module:** Investment Portfolio — Variable Income Equities
**Document date:** 2026-04-20

---

## 1. Overview

The equities class covers variable income instruments traded on B3 (or international exchanges for BDRs). The user can record purchases, sales, corporate events, stock lending income, and custody transfers.

### Asset Types

| `AssetType` | Name | Notes |
|---|---|---|
| `STOCK` | Ações (ON, PN, UNIT) | B3-listed equities |
| `STOCK_RIGHTS` | Bônus de subscrição / direitos | Rights and receipts |
| `BDR` | Brazilian Depositary Receipt | BDRs linked to foreign stocks |
| `ETF` | ETF de Renda Variável | Variable income ETFs (e.g., BOVA11) |
| `ETF_INTERNATIONAL` | ETF Internacional | Internationally-focused ETFs (e.g., IVVB11) |

InvestmentHoldingAssetClass: `VARIABLE_INCOME`

### Operations Implemented

| Operation | Transaction Type | Backend Service | Frontend Component |
|---|---|---|---|
| Comprar | `PURCHASE` | `InvestmentTransactionService` | `RecordEquityHoldingDrawer` |
| Vender | `SALE` | `InvestmentTransactionService` | `RecordEquityHoldingDrawer` |
| Dividendos | `DIVIDEND` | `InvestmentTransactionService` | `RecordCorporateEventDrawer` |
| JCP | `INTEREST_ON_EQUITY` | `InvestmentTransactionService` | `RecordCorporateEventDrawer` |
| Desdobramento (Split) | `STOCK_SPLIT` | `InvestmentTransactionService` | `RecordCorporateEventDrawer` |
| Grupamento (Reverse split) | `REVERSE_SPLIT` | `InvestmentTransactionService` | `RecordCorporateEventDrawer` |
| Bonificação em ações | `BONUS_SHARES` | `InvestmentTransactionService` | `RecordCorporateEventDrawer` |
| Subscrição | `SUBSCRIPTION` | `InvestmentTransactionService` | `RecordCorporateEventDrawer` |
| Aluguel de ações (BTC B3) | `STOCK_LENDING` | `RecordStockLendingService` | `RecordStockLendingDrawer` |
| Transferência de custódia | `CUSTODY_TRANSFER` | `TransferCustodyService` | `TransferCustodyDrawer` |

---

## 2. Compra (PURCHASE)

### 2.1 Form — RecordEquityHoldingDrawer (INFLOW tab)

| Field | Required | Notes |
|---|---|---|
| Asset | ✅ | B3 autocomplete by ticker or name (`EquityAutocomplete`) |
| Trade date | ✅ | Data do pregão |
| Settlement date | ✅ | Default D+2 (editável) |
| Quantity | ✅ | Number of shares |
| Unit price | ✅ | Price per share (BRL) |
| Price factor | ❌ | B3 `priceFactor` — default 1. Used for assets quoted per 1000 units |
| Broker note fields | ❌ | See section 2.2 |
| Net value | ✅ | Total after all fees (`grossValue − fees`) |
| Income tax (IRRF) | ❌ | Withholding tax at source |

### 2.2 Broker Note (Nota de Corretagem)

The broker note is a 1:1 record with the investment transaction (equities only). All fee fields default to zero.

| Field | `BrokerNote` property |
|---|---|
| Broker note number | `brokerNoteNumber` |
| Gross value | `grossValue` |
| Brokerage fee | `brokerageFee` |
| Clearing settlement fee | `clearingSettlementFee` |
| Clearing registration fee | `clearingRegistrationFee` |
| Stock exchange asset trade notice fee | `stockExchangeAssetTradeNoticeFee` |
| Stock exchange fee | `stockExchangeFee` |
| Clearing custody fee | `clearingCustodyFee` |
| Income tax | `incomeTax` |
| Other taxes (ISS, emolumentos) | `taxes` |
| Net value | `netValue` |

### 2.3 Cost Basis Rule (DEC-4)

The cost basis for equities **should** use `netValue / quantity` (post-fees cost per share), not `grossValue / quantity`. This follows RFB IN 1.585/2015 — fees are part of the acquisition cost for capital gain calculation.

**Current implementation**: `InvestmentTransactionService` uses `unitPrice` (= `grossValue / quantity`) for `averageCost` update. When broker fees are entered, this slightly underestimates the true cost basis. Tracked as `COST_BASIS_NET_VALUE_FIX` (open backlog item).

### 2.4 Holding Effect (PURCHASE)

- If no holding exists for `(financialAccountId, assetId)`: creates a new holding.
- `averageCost = (existingQty × existingCost + newQty × unitPrice) / totalQty`
- Side effects: DEBIT ledger entry on custodian account for `netValue` (via `CustodianCashLedgerHandler`).

---

## 3. Venda (SALE)

### 3.1 Form — RecordEquityHoldingDrawer (OUTFLOW tab)

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Data do pregão |
| Settlement date | ✅ | Default D+2 |
| Quantity | ✅ | Shares being sold |
| Unit price | ✅ | Sale price per share |
| Broker note fields | ❌ | Same structure as purchase |
| Net value | ✅ | Sale proceeds after fees and IRRF |
| Income tax (IRRF) | ❌ | IRRF 0.005% (day trade: 1%) |

### 3.2 Holding Effect (SALE)

- `quantity` decreases.
- `averageCost` unchanged.
- Side effects: CREDIT ledger entry on custodian account for `netValue`.
- If `quantity → 0`: position is closed (holding record preserved).

---

## 4. Eventos Corporativos (RecordCorporateEventDrawer)

All corporate events are recorded via `RecordCorporateEventDrawer`. The drawer is opened from the holding's context menu.

### 4.1 Dividendos (DIVIDEND)

Income event. The company distributes cash to shareholders.

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Ex-date or payment date |
| Gross value | ✅ | Total dividend amount received |
| Net value | ✅ | Post-IR (dividends from Brazilian equities are currently IR-exempt for PF) |
| Income tax | ❌ | 0 for Brazilian listed equities under current rules |
| Destination account | ❌ | Defaults to same account |

Holding effect: quantity and `averageCost` unchanged. CREDIT ledger entry created.

### 4.2 JCP — Juros sobre Capital Próprio (INTEREST_ON_EQUITY)

Income event. A Brazilian-exclusive instrument where the company pays interest on equity (deductible for the company; taxed at 15% IRRF at source for the investor).

Fields: same as DIVIDEND. `incomeTax` is typically 15% of gross value.

Holding effect: quantity and `averageCost` unchanged. CREDIT ledger entry created.

### 4.3 Desdobramento / Split (STOCK_SPLIT)

The company increases the number of shares, reducing the unit price proportionally.

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Ex-date |
| Split ratio | ✅ | E.g., 3:1 means 3 new shares per 1 old share |

Holding effect:
- `quantity = existingQty × splitFactor`
- `averageCost = (existingQty × existingAverageCost) / newQty` (total cost preserved)
- No cash movement; `grossValue = 0` on the transaction.

### 4.4 Grupamento / Reverse Split (REVERSE_SPLIT)

The company reduces the number of shares, increasing the unit price proportionally.

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Ex-date |
| Reverse split ratio | ✅ | E.g., 1:3 means 1 new share per 3 old shares |

Holding effect:
- `quantity = existingQty / reverseSplitFactor`
- `averageCost = (existingQty × existingAverageCost) / newQty` (total cost preserved)
- No cash movement.

### 4.5 Bonificação em Ações (BONUS_SHARES)

The company distributes additional free shares to existing shareholders.

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Ex-date |
| Quantity of bonus shares | ✅ | New shares received |
| Unit price of bonus shares | ❌ | Price used for IR purposes; may be 0 for fully free shares |

Holding effect:
- `quantity += bonusQuantity`
- `averageCost = (existingQty × existingAverageCost) / newQty` (existing cost spread over more shares)

### 4.6 Subscrição (SUBSCRIPTION)

The company offers existing shareholders the right to purchase new shares at a preferential price.

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Exercise date |
| Quantity subscribed | ✅ | Shares being subscribed |
| Unit price | ✅ | Subscription price per share |
| Net value | ✅ | Total paid |

Holding effect: same as PURCHASE (`averageCost` updated via weighted average). DEBIT ledger entry created.

---

## 5. Aluguel de Ações / BTC B3 (STOCK_LENDING)

The investor lends shares to borrowers (typically short sellers) via B3's BTC (Banco de Títulos). The investor retains ownership and receives periodic lending income.

### 5.1 RecordStockLendingDrawer

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Date of lending income receipt |
| Gross value | ✅ | Gross lending income, must be > 0 |
| Net value | ✅ | Post-IR income, must be ≤ grossValue |
| Income tax | ✅ | IRRF withheld (typically 15%), must be ≥ 0 |
| Destination account | ❌ | Account to credit; defaults to same account |

### 5.2 Eligible Asset Types

`RecordStockLendingService` accepts: `STOCK`, `ETF`, `BDR`, `REAL_ESTATE_FUND`, `AGRIBUSINESS_FUND`.

_Note_: REAL_ESTATE_FUND and AGRIBUSINESS_FUND (FIIs and FIAGROs) are also eligible for lending. See `08-reits.md`.

### 5.3 Holding Effect

Quantity and `averageCost` are **unchanged** by lending income. The lent shares remain in the holding. A CREDIT ledger entry is created for `netValue` (or `grossValue` if no destination account).

---

## 6. Transferência de Custódia (CUSTODY_TRANSFER)

Moves equity holdings between custody accounts (same user, e.g., migrating from XP to BTG).

### 6.1 TransferCustodyDrawer

| Field | Required | Notes |
|---|---|---|
| Source holding | ✅ | Pre-filled from context menu |
| Destination account | ✅ | Another investment account of the same user |
| Quantity | ❌ | Defaults to full position if omitted |
| Trade date | ✅ | Date of transfer |

### 6.2 Service Mechanics (TransferCustodyService)

1. Validates `destinationFinancialAccountId ≠ sourceHolding.financialAccountId`.
2. Validates destination account belongs to same user.
3. `quantity = command.quantity ?? sourceHolding.quantity`.
4. Validates `quantity > 0` and `quantity ≤ sourceHolding.quantity`.
5. `transferGrossValue = quantity × sourceHolding.averageCost`.
6. Records OUTFLOW `CUSTODY_TRANSFER` on source account (decreases source `quantity`).
7. Records INFLOW `CUSTODY_TRANSFER` on destination account (creates or updates holding with same `averageCost`).
8. Both operations are atomic within one `@Transactional` boundary.

**Average cost preservation**: The destination holding receives the source's `averageCost` as `unitPrice`. This ensures cost basis is preserved across the transfer, which is the correct tax treatment for intra-user custody transfers.

---

## 7. Valuation (Equities)

Live prices for equities are fetched from the financial-data service via `FinancialDataListedAssetMarketDataProvider`.

- `grossValue = quantity × latestPrice × priceFactor`
- `valuationSource = MARKET_DATA`
- `incomeTax = 0` for equities (provisioned IR is not computed in V1)

When market data is unavailable:
- `grossValue = quantity × averageCost` (cost basis used as fallback)
- `valuationSource = UNAVAILABLE`

---

## 8. Business Rules

**BR-1: Weighted average cost on PURCHASE and SUBSCRIPTION.**
`averageCost = (existingQty × existingCost + newQty × unitPrice) / totalQty`

**BR-2: SALE does not change averageCost.**
Only `quantity` decreases.

**BR-3: Corporate events preserve total cost basis.**
STOCK_SPLIT, REVERSE_SPLIT, BONUS_SHARES: `averageCost = (existingQty × existingCost) / newQty`.

**BR-4: Quantity cannot go negative.**
SALE/OUTFLOW: service validates `quantity ≤ currentQuantity`.

**BR-5: Only MANUAL source transactions are editable.**
Transactions with `source = OPEN_FINANCE` or `FILE_IMPORT` are read-only.

**BR-6: IRRF at source.**
Equities in Brazil are subject to IRRF on day trades (1%) and optional 0.005% on normal trades (for monthly apuration purposes). The system stores IRRF amounts but does not compute monthly IR obligations.

---

## 9. User Scenarios

**Scenario 1 — Buy PETR4**
User clicks "Comprar" in the Renda Variável group. `RecordEquityHoldingDrawer` opens. User searches for "PETR4" in `EquityAutocomplete`. Selects 100 shares at R$ 38,50. Settlement date D+2. Net value R$ 3.845 (R$ 5 brokerage fee). On submit: asset found (or created if new), holding upserted, PURCHASE recorded, DEBIT of R$ 3.845 on custodian account.

**Scenario 2 — Sell 50 PETR4**
User selects PETR4 holding > "Vender". OUTFLOW tab. Enters 50 shares at R$ 42,00, net value R$ 2.095. On submit: SALE recorded, `quantity` decreases from 100 to 50, CREDIT of R$ 2.095 on account.

**Scenario 3 — Record a dividend**
Company declares R$ 0,50/share dividend on 100 PETR4 shares. User opens `RecordCorporateEventDrawer`, selects "Dividendos". Enters gross value R$ 50,00 (IR-exempt). On submit: DIVIDEND recorded, CREDIT of R$ 50 on account. Quantity and averageCost unchanged.

**Scenario 4 — Record a 3:1 split**
MXRF11 undergoes a 3:1 split. User opens `RecordCorporateEventDrawer` > "Desdobramento". Current holding: 100 shares at R$ 12,00/share. Ratio 3:1. On submit: STOCK_SPLIT recorded, quantity → 300, averageCost → R$ 4,00/share. Total cost basis preserved at R$ 1.200.

**Scenario 5 — Record stock lending income**
User receives BTC rental income of R$ 250 gross (R$ 212,50 net, IR 15%) for lending ITUB4. Opens `RecordStockLendingDrawer`. Enters values. On submit: STOCK_LENDING recorded, CREDIT of R$ 212,50 on custodian account. Holdings unchanged.

**Scenario 6 — Transfer custody from XP to BTG**
User migrates 200 BBAS3 from XP account to BTG account. Opens `TransferCustodyDrawer`. Selects BTG as destination, leaves quantity blank (full position). On submit: OUTFLOW CUSTODY_TRANSFER on XP (200 shares reduced), INFLOW CUSTODY_TRANSFER on BTG (200 shares created with same averageCost). averageCost preserved.

---

## 10. Validations

| Field | Rule | Enforced in |
|---|---|---|
| Asset type | Must be in VARIABLE_INCOME types | `EquityAutocomplete` filter + service |
| Quantity | Must be > 0 | Service layer |
| Unit price | Must be > 0 on PURCHASE/SALE | Service layer |
| Net value | Must be ≤ grossValue | Service layer |
| Quantity on SALE | Must be ≤ holding.quantity | `InvestmentTransactionService` |
| Stock lending asset type | Must be in `LENDABLE_ASSET_TYPES` | `RecordStockLendingService` |
| Stock lending grossValue | Must be > 0 | `RecordStockLendingService` |
| Custody transfer destination | Must be different from source account | `TransferCustodyService` |
| Custody transfer destination | Must belong to same user | `TransferCustodyService` |
| Custody transfer quantity | Must be ≤ source holding quantity | `TransferCustodyService` |

---

## 11. Open Questions / Gaps

**11.1 Cost basis for equities should use netValue / quantity (COST_BASIS_NET_VALUE_FIX).**
Current implementation uses `unitPrice = grossValue / quantity`. For accurate IR gain calculation, the cost basis should include brokerage fees: `effectiveUnitPrice = netValue / quantity`. This is a known deviation from RFB IN 1.585/2015. Correcting this requires replaying positions for existing users.

**11.2 Monthly IR obligation (Darf) not computed.**
The system stores IRRF amounts but does not accumulate monthly taxable gains, apply the R$20k/month exemption for small sales, or generate DARF input data. This is a planned V2 tax engine feature.

**11.3 Day trade detection not implemented.**
IRRF at 1% for day trades (same-day buy and sell) requires matching same-day PURCHASE/SALE pairs. Not yet implemented.

**11.4 priceFactor not exposed in the frontend for most assets.**
`priceFactor` is stored in the `Asset` and `InvestmentTransaction` domain objects. The form allows it to be set but most users will not need it (factor = 1 for standard assets). For debentures quoted per 1000 units, the correct factor (0.001) must be set at asset creation. No validation or auto-detection exists.

**11.5 OWNERSHIP_TRANSFER has no frontend UI.**
`InvestmentTransactionType.OWNERSHIP_TRANSFER` exists in the enum (for inter-user asset transfers) but has no drawer. Not in scope for V1.
