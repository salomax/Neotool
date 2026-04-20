# FIIs e Fundos de Agronegócio — Requirements Document

**Service:** Invistus Banking Service
**Module:** Investment Portfolio — Real Estate and Agribusiness Funds
**Document date:** 2026-04-20

---

## 1. Overview

FIIs (Fundos de Investimento Imobiliário) and FIAGROs (Fundos de Investimento nas Cadeias Produtivas do Agronegócio) are listed on B3. They are treated similarly to equities for trading mechanics (buy/sell with broker notes) but have unique income characteristics: monthly dividend distributions (rendimentos/proventos) and, in some cases, amortizations.

### Asset Types

| `AssetType` | Name | Notes |
|---|---|---|
| `REAL_ESTATE_FUND` | FII — Fundo de Investimento Imobiliário | Listed on B3; IFIX component |
| `AGRIBUSINESS_FUND` | FIAGRO — Fundo do Agronegócio | Listed on B3 |

InvestmentHoldingAssetClass: `VARIABLE_INCOME` (same class as equities — they share the `RecordEquityHoldingDrawer` for buy/sell)

### Operations Implemented

| Operation | Transaction Type | Backend Service | Frontend Component |
|---|---|---|---|
| Comprar | `PURCHASE` | `InvestmentTransactionService` | `RecordEquityHoldingDrawer` |
| Vender | `SALE` | `InvestmentTransactionService` | `RecordEquityHoldingDrawer` |
| Rendimentos / Proventos | `DIVIDEND` | `InvestmentTransactionService` | `RecordCorporateEventDrawer` |
| Amortização de cota | `AMORTIZATION` | `RecordAmortizationService` | `RecordAmortizationDrawer` |
| Subscrição (novas cotas) | `SUBSCRIPTION` | `InvestmentTransactionService` | `RecordCorporateEventDrawer` |
| Desdobramento / Grupamento | `STOCK_SPLIT` / `REVERSE_SPLIT` | `InvestmentTransactionService` | `RecordCorporateEventDrawer` |
| Aluguel (BTC B3) | `STOCK_LENDING` | `RecordStockLendingService` | `RecordStockLendingDrawer` |
| Transferência de custódia | `CUSTODY_TRANSFER` | `TransferCustodyService` | `TransferCustodyDrawer` |

---

## 2. Compra (PURCHASE)

### 2.1 Form — RecordEquityHoldingDrawer (INFLOW tab, filtered to FII/FIAGRO)

Identical to the equities buy form. The `EquityAutocomplete` is filtered to return only `REAL_ESTATE_FUND` and `AGRIBUSINESS_FUND` asset types when opened from the FIIs group.

| Field | Required | Notes |
|---|---|---|
| Asset (FII/FIAGRO) | ✅ | B3 autocomplete filtered to `REAL_ESTATE_FUND`, `AGRIBUSINESS_FUND` |
| Trade date | ✅ | Data do pregão |
| Settlement date | ✅ | Default D+2 |
| Quantity (cotas) | ✅ | Number of fund quotas |
| Unit price | ✅ | Price per quota on the trade |
| Broker note fields | ❌ | Same structure as equities (see `07-equities.md` section 2.2) |
| Net value | ✅ | Post-fee total paid |
| Income tax (IRRF) | ❌ | Typically 0 for FII primary market purchases |

Holding effect: same as equities — `averageCost` updated via weighted average. DEBIT ledger entry for `netValue`.

---

## 3. Venda (SALE)

Identical to equities venda. `RecordEquityHoldingDrawer` OUTFLOW tab, same fields and holding effects.

---

## 4. Rendimentos / Proventos (DIVIDEND)

FIIs and FIAGROs distribute monthly income to quota holders (rendimentos). For qualifying FIIs, this income is **IR-exempt for PF** as long as the fund has ≥ 50 quotaholders and is publicly listed.

### 4.1 RecordCorporateEventDrawer

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Payment date (data-com is usually 2 business days before) |
| Gross value | ✅ | Total rendimento received for the holding |
| Net value | ✅ | Same as gross for qualifying FIIs (IR-exempt); may differ for non-qualifying |
| Income tax | ❌ | 0 for qualifying FIIs; non-zero for FIIs that do not meet the exemption criteria |
| Destination account | ❌ | Defaults to same account |

Holding effect: quantity and `averageCost` **unchanged**. CREDIT ledger entry for `netValue`.

**IR exemption**: The system does not automatically enforce the exemption criteria (50 quotaholders, listed on regulated exchange). The user enters `netValue` and `incomeTax` as received. For typical listed FIIs, `incomeTax = 0`.

---

## 5. Amortização de Cota (AMORTIZATION)

FIIs de papel (receivables-backed) commonly return capital to quota holders through amortizations. Unlike fixed income amortization, this is a **return of capital** (not interest payment), which **reduces the average cost** of the holding.

### 5.1 Eligible Asset Types (RecordAmortizationService)

`AMORTIZABLE_ASSET_TYPES` includes `REAL_ESTATE_FUND` and `AGRIBUSINESS_FUND`.

### 5.2 RecordAmortizationDrawer

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Payment date |
| Gross value | ✅ | Total amortization amount, must be > 0 |
| Net value | ✅ | Post-IR, must be ≤ grossValue |
| Income tax | ❌ | IR on the gain component (if any) |
| IOF | ❌ | Typically 0 |
| Destination account | ❌ | Account to credit; defaults to same account |

Holding effect (V1): The transaction is recorded and CREDIT cash entry is created. **Quantity and `averageCost` are NOT adjusted**. The correct behavior is to reduce `averageCost` by `amortizationValue / quantity`, but this is a known V1 simplification (same limitation as fixed income amortization). Tracked as future work.

---

## 6. Subscrição (SUBSCRIPTION)

When a FII or FIAGRO issues new quotas through a rights offering, existing quota holders may subscribe to additional quotas at a preferential price.

### 6.1 RecordCorporateEventDrawer

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Exercise date |
| Quantity subscribed | ✅ | New quotas acquired |
| Unit price | ✅ | Subscription price per quota |
| Net value | ✅ | Total paid |

Holding effect: `averageCost` updated via weighted average (same as PURCHASE). DEBIT ledger entry created.

---

## 7. Desdobramento / Grupamento (STOCK_SPLIT / REVERSE_SPLIT)

FIIs and FIAGROs occasionally undergo splits or reverse splits (uncommon but valid).

Fields, holding effects, and business rules are identical to equities splits. See `07-equities.md` sections 4.3 and 4.4.

---

## 8. Aluguel (STOCK_LENDING)

FIIs and FIAGROs are included in `LENDABLE_ASSET_TYPES` in `RecordStockLendingService`. The mechanics are identical to equity lending — see `07-equities.md` section 5.

---

## 9. Transferência de Custódia (CUSTODY_TRANSFER)

Identical to equities custody transfer — see `07-equities.md` section 6. Uses `CUSTODY_TRANSFER` transaction type (not `QUOTA_TRANSFER`, which is reserved for MUTUAL_FUND).

---

## 10. Valuation

Live prices for FIIs and FIAGROs are fetched from B3 via the financial-data service.

- `grossValue = quantity × latestPrice`
- `valuationSource = MARKET_DATA`
- `incomeTax = 0` (provisioned IR not computed in V1; FII rendimentos are IR-exempt for qualifying funds)

When market data is unavailable:
- `grossValue = quantity × averageCost`
- `valuationSource = UNAVAILABLE`

---

## 11. Business Rules

**BR-1: Weighted average cost on PURCHASE and SUBSCRIPTION.**
Same formula as equities.

**BR-2: SALE does not change averageCost.**
Only `quantity` decreases.

**BR-3: Splits / reverse splits preserve total cost basis.**
`averageCost = (existingQty × existingCost) / newQty`.

**BR-4: DIVIDEND does not change quantity or averageCost.**
FII rendimentos are income events only.

**BR-5: Amortization does not reduce averageCost in V1.**
Known simplification. The user's cost basis will be overstated after an amortization event until this is corrected.

**BR-6: Quantity cannot go negative.**
SALE/OUTFLOW: validated by service.

**BR-7: Only MANUAL transactions are editable.**

---

## 12. User Scenarios

**Scenario 1 — Buy MXRF11**
User opens FII group > "Comprar". `RecordEquityHoldingDrawer` opens with B3 autocomplete filtered to FIIs. User searches "MXRF11", enters 500 cotas at R$ 10,20, settlement D+2, net value R$ 5.093 (with R$ 7 brokerage). On submit: holding created, PURCHASE recorded, DEBIT of R$ 5.093 on account.

**Scenario 2 — Record monthly rendimento**
MXRF11 pays R$ 0,10/cota this month. User holds 500 cotas → R$ 50,00. User opens `RecordCorporateEventDrawer` > "Rendimentos". Enters trade date (payment date), gross value R$ 50, net value R$ 50 (IR-exempt). On submit: DIVIDEND recorded, CREDIT of R$ 50 on account. Holding unchanged.

**Scenario 3 — Record a FII amortization**
A FII de papel (e.g., HGLG11) amortizes R$ 1,00/cota. User holds 200 cotas → R$ 200 gross. User opens `RecordAmortizationDrawer`. Enters gross value R$ 200, net value R$ 200 (no IR if IR-exempt). On submit: AMORTIZATION recorded, CREDIT of R$ 200 on account. Quantity and averageCost unchanged (V1 simplification).

**Scenario 4 — Subscribe to new quotas**
HGLG11 issues a rights offering at R$ 160/cota. User subscribes to 10 new cotas. Opens `RecordCorporateEventDrawer` > "Subscrição". Enters quantity 10, unit price R$ 160. On submit: SUBSCRIPTION recorded, `averageCost` updated via weighted average. DEBIT of R$ 1.600 on account.

**Scenario 5 — Transfer MXRF11 custody from XP to Rico**
User migrates 200 MXRF11 from XP to Rico. Opens `TransferCustodyDrawer`. Selects Rico account as destination, leaves quantity blank (full 200). On submit: CUSTODY_TRANSFER OUT on XP, CUSTODY_TRANSFER IN on Rico. averageCost preserved.

---

## 13. Validations

| Field | Rule | Enforced in |
|---|---|---|
| Asset type | Must be REAL_ESTATE_FUND or AGRIBUSINESS_FUND | `EquityAutocomplete` filter (B3AssetType) |
| Quantity | Must be > 0 | Service layer |
| Net value | Must be ≤ grossValue | Service layer |
| Quantity on SALE | Must be ≤ holding.quantity | `InvestmentTransactionService` |
| Amortization asset type | Must be in AMORTIZABLE_ASSET_TYPES | `RecordAmortizationService` |
| Amortization grossValue | Must be > 0 | `RecordAmortizationService` |
| Stock lending asset type | Must be in LENDABLE_ASSET_TYPES | `RecordStockLendingService` |

---

## 14. Open Questions / Gaps

**14.1 Amortization should reduce averageCost.**
FII amortization is a return of capital. The correct accounting is to reduce `averageCost` by `grossValue / quantity` after each amortization event. The current V1 implementation does not do this, causing overstated cost basis and understated gain upon eventual sale. Tracked as future work alongside fixed income amortization.

**14.2 IR-exempt status is user-managed.**
The system does not verify whether a specific FII meets the CVM criteria for IR-exemption (≥ 50 quotaholders, listed). The user is responsible for entering the correct `netValue` and `incomeTax`. A future integration with CVM fund data could auto-detect exemption status.

**14.3 IFIX benchmark not yet wired.**
FIIs are the natural comparison class for the IFIX benchmark. The financial-data service has IFIX series, but benchmark comparison is not yet displayed in the UI (V2 scope).
