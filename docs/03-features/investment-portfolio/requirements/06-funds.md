# Fundos de Investimento — Requirements Document

**Service:** Invistus Banking Service
**Module:** Investment Portfolio — Investment Funds
**Document date:** 2026-04-20

---

## 1. Overview

Investment funds (Fundos de Investimento) are collective vehicles regulated by CVM. The user holds quotas (cotas) in the fund, and the fund's value varies with the daily net asset value (VNA) published by CVM.

### Asset Types

| `AssetType` | Name | Notes |
|---|---|---|
| `MUTUAL_FUND` | Fundo de Investimento | All CVM-regulated funds; ANBIMA category drives tax treatment |
| `PGBL` | Plano Gerador de Benefício Livre | Previdência complementar; tax-deductible contributions |
| `VGBL` | Vida Gerador de Benefício Livre | Previdência complementar; no tax deduction on contributions |

InvestmentHoldingAssetClass: `INVESTMENT_FUND`

### ANBIMA Categories (MUTUAL_FUND only)

| `AnbimaCategory` | Come-cotas rate | Notes |
|---|---|---|
| `RENDA_FIXA` (longo prazo) | 15% | Semi-annual, May and November |
| `RENDA_FIXA` (curto prazo) | 20% | Semi-annual |
| `MULTIMERCADO` | 15% or 20% | Depends on profile (longo/curto prazo) |
| `ACOES` | Not subject to come-cotas | Annual IR only on redemption |
| `CAMBIAL` | 15% or 20% | Like RENDA_FIXA/MULTIMERCADO |

### Operations Implemented

| Operation | Transaction Type | Backend Service | Frontend Component |
|---|---|---|---|
| Aplicar (new position or additional) | `PURCHASE` or `SUBSCRIPTION` | `InvestmentTransactionService` | `RecordFundHoldingDrawer` |
| Resgatar | `SALE` | `InvestmentTransactionService` | `RecordFundHoldingDrawer` |
| Come-cotas (WITHHOLDING_TAX) | `WITHHOLDING_TAX` | `ConfirmWithholdingTaxService` | `WithholdingTaxDrawer` |
| Transferência de cotas | `QUOTA_TRANSFER` | `TransferCustodyService` | `TransferCustodyDrawer` |

---

## 2. Aplicação (PURCHASE / SUBSCRIPTION)

### 2.1 Form — RecordFundHoldingDrawer

Fields collected on INFLOW direction:

| Field | Required | Notes |
|---|---|---|
| Fund | ✅ | CVM autocomplete by CNPJ or name (`FundAutocomplete`), or manual entry |
| Pricing mode | ✅ | `MARKET_DATA` or `MANUAL` |
| Trade date | ✅ | Date of application |
| Quantity (cotas) | conditional | Required when pricing mode = MARKET_DATA |
| Unit price (cota price) | conditional | Required when pricing mode = MARKET_DATA |
| Gross value | ✅ | Total applied (= `quantity × unitPrice` or entered directly) |
| Valued at | conditional | Date of manual valuation; required when pricing mode = MANUAL |
| Manual gross value | conditional | Total value at `valuedAt`; required when pricing mode = MANUAL |

Transaction type:
- First application → `PURCHASE`
- Subsequent applications to the same fund → `SUBSCRIPTION`

### 2.2 Pricing Modes

**`MARKET_DATA`**: The holding's current value is computed using the fund's latest CVM-published VNA (from the financial-data service). The user enters `quantity` and `unitPrice` at the time of the trade. Display shows live VNA after creation.

**`MANUAL`**: Used when CVM data is unavailable or unreliable (e.g., closed-end funds, PGBL/VGBL). The user supplies `manualGrossValue` and `manualValuedAt`. The holding is valued at the manually provided amount on that date. The user must update the valuation manually over time.

### 2.3 Holding Effect (PURCHASE / SUBSCRIPTION)

- `averageCost = (existingQty × existingCost + newQty × unitPrice) / totalQty`
- Fund assets are looked up by `fundCnpj`. If the fund asset does not exist, it is created from CVM autocomplete data.

---

## 3. Resgate (SALE)

User records a redemption on the OUTFLOW tab of `RecordFundHoldingDrawer`.

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Date of redemption request |
| Quantity redeemed | ✅ | Cotas being redeemed |
| Net value | ✅ | Value actually received by user (after IR) — always user-confirmed per DEC-3 |
| Gross value | ❌ | Optionally entered; can be derived from `quantity × current VNA` for MARKET_DATA mode |
| Income tax | ❌ | IR withheld at source |
| Cash destination | ❌ | Account to credit; defaults to same account |

**DEC-3 (from holding-transaction-operations.md)**: Regardless of `pricingMode`, the user always enters or confirms `netValue`. For `MARKET_DATA` mode, the form may suggest `quantity × latestVNA` but the field remains editable. The value saved is always user-confirmed.

Holding effect:
- `quantity` decreases.
- `averageCost` unchanged.
- If `quantity → 0`: position is closed (holding record preserved).

---

## 4. Come-cotas (WITHHOLDING_TAX)

Come-cotas is a semi-annual IR levy (May 15 and November 15) applied to `RENDA_FIXA`, `MULTIMERCADO`, and `CAMBIAL` funds. The fund manager deducts the tax by reducing the investor's quota count (not the unit price).

### 4.1 Mechanics

- The fund's VNA (unit price) is **not changed**.
- The holding's `quantity` decreases by `irValue / unitPrice` quotas.
- Effect: the investor holds fewer quotas at the same price, reducing total value.

### 4.2 WithholdingTaxDrawer

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Date of come-cotas (typically May 15 or Nov 15) |
| IR value (irValue) | ✅ | Amount of IR deducted (R$), must be > 0 |
| Quotas deducted | ✅ | `= irValue / unitPrice` (auto-calculated; user can confirm or override) |

### 4.3 Service Validation (ConfirmWithholdingTaxService)

- Asset type must be `MUTUAL_FUND`.
- `irValue > 0`.
- `quotasDeducted > 0`.
- `holding.quantity ≥ quotasDeducted` (cannot deduct more quotas than held).

Transaction recorded: `type = WITHHOLDING_TAX`, `direction = OUTFLOW`, `quantity = quotasDeducted`, `unitPrice = irValue / quotasDeducted`.

**Important**: Come-cotas does **not** generate a cash ledger entry (it is a non-cash tax event — the fund itself pays the Receita Federal). `CustodianCashLedgerHandler` must not create a DEBIT/CREDIT for `WITHHOLDING_TAX`. _(Verify in code: if CustodianCashLedgerHandler skips WITHHOLDING_TAX — this is the expected behavior.)_

---

## 5. Transferência de Cotas (QUOTA_TRANSFER)

Moves fund quotas between custody accounts (same user, different custodians/accounts).

`TransferCustodyService` detects `assetType = MUTUAL_FUND` and uses `InvestmentTransactionType.QUOTA_TRANSFER` (instead of `CUSTODY_TRANSFER` used for other assets).

See custody transfer rules in `07-equities.md` section 7 — same service, same mechanics. Only the `type` differs.

---

## 6. Fund Valuation (FundValuationService)

For holdings with `pricingMode = MARKET_DATA`:
- Current `grossValue = quantity × latestVNA` (from financial-data service)
- `valuationSource = MARKET_DATA`
- `valuationReferenceDate` = date of latest VNA

For holdings with `pricingMode = MANUAL`:
- `grossValue = manualGrossValue`
- `valuationSource = MANUAL`
- `valuationReferenceDate = manualValuedAt`

IR provisioning for come-cotas is **not** pre-computed in valuation (come-cotas is a past event; future come-cotas scheduling is not yet implemented as a pending action).

---

## 7. Business Rules

**BR-1: MUTUAL_FUND only for come-cotas.**
`ConfirmWithholdingTaxService` validates `asset.assetType == MUTUAL_FUND`. PGBL/VGBL have different tax rules and are not subject to come-cotas in the same way.

**BR-2: QUOTA_TRANSFER vs. CUSTODY_TRANSFER.**
`TransferCustodyService` uses `QUOTA_TRANSFER` for `MUTUAL_FUND`, `CUSTODY_TRANSFER` for all other asset types.

**BR-3: Quantity cannot go negative.**
On SALE or WITHHOLDING_TAX, service validates `quantityRedeemed ≤ currentQuantity`.

**BR-4: First application is PURCHASE, subsequent are SUBSCRIPTION.**
This distinction maps to OFB APLICACAO vs. subsequent subscriptions. The frontend uses `PURCHASE` for the initial investment and the backend may use `SUBSCRIPTION` for additional applications into an existing holding. In practice the financial effect is identical (weighted average cost recalculation).

**BR-5: netValue on resgate is user-confirmed.**
Even when the system can suggest a value from VNA data, the user must confirm `netValue`. This ensures accuracy when the fund's settlement VNA differs from the request VNA.

---

## 8. User Scenarios

**Scenario 1 — Record an application in a known CVM fund**
User clicks "Aplicar" on the Fundos group. `RecordFundHoldingDrawer` opens. User searches for "Núcleo FIC FIM" by CNPJ. Selects `pricingMode = MARKET_DATA`. Enters 1.234,56 cotas at R$ 10,50/cota. Total = R$ 12.962,88. On submit: asset created (or found by `fundCnpj`), holding created, PURCHASE transaction recorded.

**Scenario 2 — Additional application**
User applies R$ 5.000 more to the same fund already in the portfolio. `RecordFundHoldingDrawer` shows the existing holding. Transaction type = `SUBSCRIPTION`. `averageCost` is updated via weighted average.

**Scenario 3 — Record a PGBL with manual valuation**
User creates a PGBL position with `pricingMode = MANUAL`. Enters total value R$ 50.000 and `valuedAt = 2026-03-31`. Holding is created with `manualGrossValue = 50000` and `manualValuedAt = 2026-03-31`. In the grid, the value shows "estimado" with the reference date.

**Scenario 4 — Record come-cotas**
On May 15, the user receives a statement from XP showing come-cotas deducted: IR = R$ 425, quotas redeemed = 40,238. User opens `WithholdingTaxDrawer`, enters irValue = 425 and quotasDeducted = 40.238. On submit: WITHHOLDING_TAX transaction recorded, holding `quantity` reduced by 40.238. No cash movement is created.

**Scenario 5 — Redeem a fund**
User selects fund holding > "Resgatar". OUTFLOW tab of `RecordFundHoldingDrawer` opens. User enters quantity = 500 cotas, net value R$ 5.200. On submit: SALE transaction recorded, `quantity` decreases, CREDIT of R$ 5.200 on custodian account.

---

## 9. Validations

| Field | Rule | Enforced in |
|---|---|---|
| Asset type | Must be MUTUAL_FUND / PGBL / VGBL | Form + service |
| Pricing mode | Required; must be MARKET_DATA or MANUAL | Form |
| Gross value | Must be > 0 | Service layer |
| Net value (on SALE) | Must be ≤ gross value and ≥ 0 | Service layer |
| quotasDeducted (come-cotas) | Must be > 0 and ≤ holding.quantity | `ConfirmWithholdingTaxService` |
| irValue (come-cotas) | Must be > 0 | `ConfirmWithholdingTaxService` |
| Quantity on SALE | Must be ≤ holding.quantity | `InvestmentTransactionService` |
| Asset type (come-cotas) | Must be MUTUAL_FUND | `ConfirmWithholdingTaxService` |

---

## 10. Open Questions / Gaps

**10.1 PGBL/VGBL tax rules not enforced.**
PGBL and VGBL have specific IR rules (PGBL: progressive table on full amount; VGBL: progressive table only on income). The current system does not enforce or compute these. Tax calculations for previdência are V2 scope.

**10.2 Come-cotas pending action is not yet implemented.**
The system does not automatically schedule a `WITHHOLDING_TAX_DUE` pending action for May/November. The user must record come-cotas manually. Automatic scheduling is V2 scope.

**10.3 CustodianCashLedgerHandler behavior for WITHHOLDING_TAX needs verification.**
The expected behavior is that `WITHHOLDING_TAX` does NOT generate a cash ledger entry (the tax is paid by the fund, not by the user from their account balance). This should be verified in `CustodianCashLedgerHandler.appliesTo()` or the event type mapping.

**10.4 Fund VNA data coverage.**
Not all CVM-registered funds have daily VNA data available in the financial-data service. For funds without data, `valuationSource = UNAVAILABLE` and the user must use `pricingMode = MANUAL`.
