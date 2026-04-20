# Cripto — Requirements Document

**Service:** Invistus Banking Service
**Module:** Investment Portfolio — Cryptocurrency
**Document date:** 2026-04-20

---

## 1. Overview

Cryptocurrency holdings allow users to track Bitcoin, Ethereum, and other digital assets manually. Unlike equities or fixed income, crypto assets are not traded on B3 and have no CVM registration. They are created manually by the user and associated with a custody account of subtype `CRYPTO` (representing the exchange or wallet).

### Asset Type

| `AssetType` | Name | Notes |
|---|---|---|
| `CRYPTO` | Criptomoedas | All digital assets; manual asset creation only |

InvestmentHoldingAssetClass: `CRYPTO`

### Operations Implemented

| Operation | Transaction Type | Backend Service | Frontend Component |
|---|---|---|---|
| Comprar | `PURCHASE` | `InvestmentTransactionService` | `RecordEquityHoldingDrawer` (variant `crypto`) |
| Vender | `SALE` | `InvestmentTransactionService` | `RecordEquityHoldingDrawer` (variant `crypto`) |
| Transferência de custódia | `CUSTODY_TRANSFER` | `TransferCustodyService` | `TransferCustodyDrawer` |

**Note**: `RecordEquityHoldingDrawer` is a shared component used for equities, FIIs, and crypto. For crypto, it is rendered with `variant="crypto"` which hides broker note fields and the B3 autocomplete, replacing them with a manual asset name/ticker input.

---

## 2. Compra (PURCHASE)

### 2.1 Form — RecordEquityHoldingDrawer (variant "crypto")

| Field | Required | Notes |
|---|---|---|
| Asset name | ✅ | Free text (e.g., "Bitcoin", "Ethereum", "Solana") |
| Ticker | ✅ | Short identifier (e.g., BTC, ETH, SOL) |
| Network (rede) | ❌ | Optional blockchain network identifier (e.g., Ethereum, Solana) |
| Trade date | ✅ | Date of purchase |
| Quantity | ✅ | Amount of crypto purchased (may be fractional, e.g., 0,00432 BTC) |
| Unit price | ✅ | Price per unit in BRL at trade date |
| Net value | ✅ | Total paid in BRL (= quantity × unitPrice; no brokerage fields) |

### 2.2 No Broker Note

Crypto trades do not have a broker note (`BrokerNote` is not created). The `variant="crypto"` hides all broker fee fields from the form.

### 2.3 No Mandatory Settlement Date

For crypto, there is no standard settlement cycle. `settlementDate` defaults to `tradeDate` (D+0) and is not required or exposed in the form.

### 2.4 Exchange as Custody Account

The exchange or wallet is represented by the `FinancialAccount` with `subtype = CRYPTO`. Each distinct exchange used by the user is a separate custody account. There is no separate `exchange` field on the asset or transaction.

_DEC-2 (from holding-transaction-operations.md)_: The exchange identity is carried by the `financialAccountId`. If the user holds BTC on both Binance and Coinbase, they have two separate CRYPTO accounts, each with its own BTC holding. The `issuerName` on the asset may be used as a supplementary exchange identifier if needed.

### 2.5 Asset Creation

Crypto assets are created **manually** — there is no B3 or CVM autocomplete. The user provides name and ticker. The asset is created via `createInvestmentAsset` mutation. Subsequent purchases of the same ticker on the same account reuse the existing holding.

### 2.6 Holding Effect (PURCHASE)

- `averageCost = (existingQty × existingCost + newQty × unitPrice) / totalQty`
- Side effects: DEBIT ledger entry on the CRYPTO custody account for `netValue`.

---

## 3. Venda (SALE)

### 3.1 Form — RecordEquityHoldingDrawer (OUTFLOW tab, variant "crypto")

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Date of sale |
| Quantity | ✅ | Amount of crypto sold |
| Unit price | ✅ | Sale price per unit in BRL |
| Net value | ✅ | Proceeds received in BRL (no brokerage deduction) |
| Income tax | ❌ | IR on capital gain (if applicable) |

Holding effect:
- `quantity` decreases.
- `averageCost` unchanged.
- CREDIT ledger entry on the CRYPTO account for `netValue`.
- If `quantity → 0`: position is closed.

---

## 4. Transferência de Custódia (CUSTODY_TRANSFER)

Moves crypto holdings between exchange accounts (same user, different exchanges or wallets).

Mechanics identical to equities custody transfer — see `07-equities.md` section 6. `TransferCustodyService` uses `CUSTODY_TRANSFER` type for `CRYPTO` asset type.

---

## 5. Valuation

Crypto prices are fetched via CoinGecko or equivalent from the financial-data service.

- `grossValue = quantity × latestPrice`
- `valuationSource = MARKET_DATA`

When price data is unavailable:
- `grossValue = quantity × averageCost`
- `valuationSource = UNAVAILABLE`

---

## 6. Business Rules

**BR-1: Weighted average cost on PURCHASE.**
`averageCost = (existingQty × existingCost + newQty × unitPrice) / totalQty`

**BR-2: SALE does not change averageCost.**
Only `quantity` decreases.

**BR-3: Quantity supports high precision (fractional units).**
`quantity` is stored as `BigDecimal`. Crypto can be fractional (e.g., 0.00432 BTC). The form must support fractional quantity input.

**BR-4: No brokerage fees.**
`BrokerNote` is not created for crypto transactions. `brokerageFee` and related fields are not collected.

**BR-5: No settlement date cycle.**
`settlementDate` = `tradeDate` (D+0) by default. Not required or editable in the crypto form variant.

**BR-6: Stock lending not supported for CRYPTO.**
`RecordStockLendingService` validates asset type against `LENDABLE_ASSET_TYPES` which does not include `CRYPTO`. Attempting to record stock lending for a crypto holding will be rejected.

**BR-7: Only MANUAL transactions are editable.**
Same rule as all other classes.

**BR-8: Quantity cannot go negative.**
SALE: validated by service.

---

## 7. User Scenarios

**Scenario 1 — Buy Bitcoin**
User opens the Cripto group > "Comprar". `RecordEquityHoldingDrawer` opens in crypto variant. User types asset name "Bitcoin", ticker "BTC". Enters 0,05 BTC at R$ 350.000/BTC. Net value R$ 17.500. On submit: asset "Bitcoin (BTC)" created, holding created, PURCHASE recorded. DEBIT of R$ 17.500 on the Binance custody account.

**Scenario 2 — Buy more BTC on the same exchange**
User records another purchase of 0,02 BTC at R$ 380.000/BTC. Net value R$ 7.600. On submit: existing BTC holding on Binance is updated. New `averageCost = (0,05 × 350.000 + 0,02 × 380.000) / 0,07 = R$ 358.571/BTC`.

**Scenario 3 — Sell 0,03 BTC**
User selects BTC holding > "Vender". Enters 0,03 BTC at R$ 400.000/BTC. Net value R$ 12.000. On submit: SALE recorded, `quantity → 0,04 BTC`, `averageCost` unchanged. CREDIT of R$ 12.000 on account.

**Scenario 4 — Transfer BTC from Binance to Coinbase**
User creates a second CRYPTO account for Coinbase. Opens `TransferCustodyDrawer` on the Binance BTC holding. Selects Coinbase as destination. Quantity: 0,04 (full position). On submit: CUSTODY_TRANSFER OUT on Binance (quantity → 0), CUSTODY_TRANSFER IN on Coinbase (new holding at same averageCost).

**Scenario 5 — View crypto portfolio**
User opens the investments page with the Cripto asset group card visible. It shows BTC, ETH, SOL holdings with their current values (from CoinGecko prices) and allocation percentage.

---

## 8. Validations

| Field | Rule | Enforced in |
|---|---|---|
| Asset name | Required, non-blank | Form + `createInvestmentAsset` |
| Ticker | Required, non-blank | Form + `createInvestmentAsset` |
| Quantity | Must be > 0; supports fractional values | Service layer |
| Unit price | Must be > 0 | Service layer |
| Net value | Must be > 0 | Service layer |
| Quantity on SALE | Must be ≤ holding.quantity | `InvestmentTransactionService` |
| Custody account subtype | Should be `CRYPTO` | Enforced by account selector context; no hard service check |

---

## 9. Open Questions / Gaps

**9.1 Staking / yield rewards have no transaction type.**
Passive income from staking (on-chain or exchange staking programs) has no equivalent in the current `InvestmentTransactionType` enum. Options: reuse `DIVIDEND` (semantically approximate) or introduce a new `STAKING_REWARD` type. No drawer exists. V2 scope.

**9.2 DeFi and on-chain wallet tracking not supported.**
The current model requires a CRYPTO custody account (exchange). Tracking on-chain wallets (Ledger, MetaMask) by address would require integration with blockchain APIs (Etherscan, Solana RPC, etc.). Not in scope for V1 manual tracking.

**9.3 Monthly IR obligation for crypto not computed.**
Brazilian crypto is subject to IR on monthly gains above R$ 35.000/month. The system stores transaction data but does not accumulate monthly taxable gains, apply the exemption threshold, or generate DARF input. V2 tax engine scope.

**9.4 DeCripto / OECD CARF reporting (effective July 2026).**
New Brazilian regulation (DeCripto) requires monthly reporting of crypto transactions to Receita Federal via exchanges. This is an exchange-level obligation, not a user-level one. The system may need to support data export in the required format for users who operate their own wallets. Out of scope for V1.

**9.5 RecordEquityHoldingDrawer shared for equities, FIIs, and crypto.**
The current architecture uses a single drawer component with a `variant` prop to differentiate crypto from equities. A dedicated `RecordCryptoDrawer` component (as planned in `holding-events-roadmap.md` Phase 3) would improve maintainability and allow crypto-specific UX enhancements without coupling to the equities drawer.

**9.6 Price feed coverage.**
Not all crypto assets (especially small-cap or new tokens) have price data from CoinGecko. For assets without coverage, `valuationSource = UNAVAILABLE` and the user sees the cost basis as the fallback value.
