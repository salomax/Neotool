# Renda Fixa — Requirements Document

**Service:** Invistus Banking Service
**Module:** Investment Portfolio — Fixed Income
**Document date:** 2026-04-20

---

## 1. Overview

The fixed income class covers instruments where the investor agrees on a contracted return at the time of purchase. The user can manually record positions, receive periodic coupon payments, record amortizations, and confirm vencimentos or resgates antecipados.

### Asset Types

| `AssetType` | Name | Notes |
|---|---|---|
| `CDB` | Certificado de Depósito Bancário | Bancária |
| `RDB` | Recibo de Depósito Bancário | Bancária |
| `LCI` | Letra de Crédito Imobiliário | Bancária; IR-exempt for PF |
| `LCA` | Letra de Crédito do Agronegócio | Bancária; IR-exempt for PF |
| `LC` | Letra de Câmbio | Bancária |
| `LF` | Letra Financeira | Bancária |
| `LIG` | Letra Imobiliária Garantida | Bancária |
| `DEBENTURES` | Debêntures | Crédito; may have coupon + amortization |
| `CRI` | Certificado de Recebíveis Imobiliários | Crédito; IR-exempt for PF; may have coupon |
| `CRA` | Certificado de Recebíveis do Agronegócio | Crédito; IR-exempt for PF; may have coupon |
| `TREASURY` | Tesouro Direto | Government; may have semi-annual coupon |
| `ETF_FIXED_INCOME` | ETF de Renda Fixa | Listed; amortizable |

InvestmentHoldingAssetClass: `FIXED_INCOME`

### Operations Implemented

| Operation | Transaction Type | Backend Service | Frontend Component |
|---|---|---|---|
| Aportar (create or add to position) | `PURCHASE` | `InvestmentTransactionService` | `RecordFixedIncomeDrawer` |
| Resgatar antecipado | `SALE` | `InvestmentTransactionService` | `RecordFixedIncomeDrawer` (OUTFLOW tab) |
| Confirmar vencimento | `MATURITY` | `HoldingMaturityConfirmService` | `MaturityConfirmDrawer` |
| Pagamento de cupom | `INTEREST_PAYMENT` | `ConfirmCouponPaymentService` | `CouponPaymentConfirmDrawer` |
| Amortização | `AMORTIZATION` | `RecordAmortizationService` | `RecordAmortizationDrawer` |
| Transferência de custódia | `CUSTODY_TRANSFER` | `TransferCustodyService` | `TransferCustodyDrawer` |

---

## 2. Aporte (PURCHASE)

### 2.1 Form — RecordFixedIncomeDrawer

Fields collected on INFLOW direction:

| Field | Required | Notes |
|---|---|---|
| Asset type | ✅ | CDB, RDB, LCI, LCA, LC, LF, LIG, DEBENTURES, CRI, CRA, TREASURY |
| Asset name | ✅ | Free text; becomes the `Asset.name` |
| Issuer (emissor) | ✅ | Known issuer from catalogue or custom `issuerName` |
| Trade date | ✅ | Date of purchase |
| Issue date | ✅ | Date the instrument was issued (used as `Holding.issueDate`) |
| Maturity date | ✅ | `Holding.maturityDate`; drives pending action and valuation |
| Grace period date | ❌ | `Holding.gracePeriodDate` — earliest redemption date |
| Gross value | ✅ | Total amount invested |
| Indexer | ✅ | CDI, DI, IPCA, IGP-M, SELIC, PRE_FIXADO, OUTROS |
| Indexer percentage | conditional | % of the indexer (e.g., 115% CDI); required if indexer ≠ PRE_FIXADO and ≠ OUTROS |
| Fixed annual rate | conditional | Pre-fixed rate or spread component; required if indexer = PRE_FIXADO |
| Coupon frequency | ✅ | AT_MATURITY, MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL |
| Coupon day of month | conditional | Day (1–31) on which coupon settles; required if frequency ≠ AT_MATURITY |
| Income tax exempt | ✅ | Auto-detected for LCI, LCA, CRI, CRA; editable |
| Net value | ❌ | Only relevant on OUTFLOW; ignored on INFLOW |
| Income tax | ❌ | Only relevant on OUTFLOW |
| IOF | ❌ | Only relevant on OUTFLOW |

### 2.2 Asset Creation

Fixed income holdings use **one asset per contract** (DEC-1): each CDB from a specific bank at a specific rate/maturity is its own asset. This keeps the holding model clean without lot-level tracking.

On submit, the form calls `createInvestmentAsset` first, then `recordInvestmentTransaction` with the returned `assetId`.

### 2.3 Holding Effect (PURCHASE)

- If no holding exists for `(financialAccountId, assetId)`: creates a new holding.
- Updates `averageCost = (existingQty × existingCost + newQty × unitPrice) / totalQty`.
- For fixed income, `quantity = 1` typically (the invested principal IS the holding), unless the instrument has explicit unit pricing (e.g., Tesouro Direto with fractional units).
- Pending actions for maturity and coupon are upserted by `MaturityPendingActionHandler` and `CouponPendingActionHandler`.

---

## 3. Resgate Antecipado (SALE)

User records an early redemption on the OUTFLOW tab of `RecordFixedIncomeDrawer`.

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Date of redemption |
| Gross value | ✅ | Gross redemption value |
| Net value | ✅ | Post-IR/IOF value actually received |
| Income tax | ❌ | IR withheld at source |
| IOF | ❌ | IOF for redemptions < 30 days |
| Cash destination | ❌ | Account to credit; defaults to same custodian account |

On submit, `InvestmentTransactionService.record()` is called with `type = SALE`, `direction = OUTFLOW`.

Holding effect:
- `quantity` decreases by the sold quantity.
- `averageCost` unchanged on SALE.
- If resulting `quantity = 0`: holding is NOT deleted; position is closed (status preserved for history). `closeReason = REDEEMED_EARLY` is set if the resgate precedes `maturityDate`.

---

## 4. Vencimento (MATURITY)

The vencimento flow is triggered by the user confirming a pending action of type `INVESTMENT_MATURED`.

### 4.1 Pending Action Lifecycle

- Created/updated by `MaturityPendingActionHandler` when a holding has `maturityDate != null` and `quantity > 0`.
- `visibleFrom = maturityDate + 1 day` — only shown after maturity date.
- On confirmation: status → `COMPLETED`, `relatedTransactionId` set.
- On `quantity → 0` before maturity (early redemption or transfer): pending action is cancelled.

### 4.2 MaturityConfirmDrawer

Opens from `PendingActionsDrawer` or from the holding's context menu (when `maturityDate ≤ today`).

| Field | Required | Notes |
|---|---|---|
| Maturity date | ✅ | Pre-filled from holding; editable |
| Gross value | ✅ | Total gross value at maturity |
| Net value | ✅ | Post-IR value received |
| Income tax | ❌ | IR withheld |
| IOF | ❌ | Typically 0 at maturity |
| Close reason | ✅ | `MATURED` (pre-filled); can be `REDEEMED_EARLY` for manual override |

### 4.3 Service Validation (HoldingMaturityConfirmService)

- Holding must exist and belong to the user.
- `maturityDate` must not be null and must not be in the future (`maturityDate > today` → rejected).
- `quantity` must be > 0 (position must be open).
- `closeReason` must not already be set (position not already closed).
- A `INVESTMENT_MATURED` pending action must exist for this holding.

On success:
- Records a `MATURITY` transaction (`type = MATURITY`, `direction = OUTFLOW`).
- Sets `Holding.closedAt = maturityDate`, `Holding.closeReason = MATURED` (or `REDEEMED_EARLY`).
- Marks the pending action as `COMPLETED`.
- Side effects (via lifecycle orchestrator): CREDIT cash ledger entry on custodian account.

---

## 5. Pagamento de Cupom (INTEREST_PAYMENT)

Coupon-bearing instruments (NTN-B / Tesouro IPCA+ with semi-annual coupons, Debêntures, CRI, CRA) generate periodic `COUPON_PAYMENT_DUE` pending actions.

### 5.1 Coupon Schedule

`CouponScheduleService` computes the projected coupon dates from `couponFrequency` + `couponDayOfMonth` + `maturityDate`. The `CouponScheduleTimeline` component renders the upcoming schedule in the holding detail drawer (`FixedIncomeHoldingDetailContent`).

### 5.2 Pending Action Lifecycle

- `CouponPendingActionHandler.onRecord()` upserts a `COUPON_PAYMENT_DUE` pending action when `couponFrequency ≠ AT_MATURITY AND maturityDate != null AND quantity > 0`.
- `visibleFrom = nextCouponDate − 7 days`.
- On `INTEREST_PAYMENT` recorded: the current pending action is marked `COMPLETED` and a new one is scheduled for the next coupon date.
- On holding quantity → 0: pending action is cancelled.

### 5.3 CouponPaymentConfirmDrawer

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Payment date |
| Gross value | ✅ | Coupon gross amount |
| Net value | ✅ | Coupon net of IR |
| Income tax | ❌ | IR withheld (regressive table: 22.5% → 15%) |
| Destination account | ❌ | Account to credit the net payment; defaults to same account |
| Pending action ID | ❌ | Links to the pending action being resolved |

### 5.4 Service Validation (ConfirmCouponPaymentService)

- Holding must exist and belong to the user.
- `grossValue > 0`.
- `netValue >= 0` and `netValue ≤ grossValue`.
- Holding must have `couponFrequency != null AND couponFrequency != AT_MATURITY`.

Holding effect: quantity and `averageCost` are **not changed** by a coupon payment. Only the accrual clock resets for valuation purposes (`FixedIncomeValuationService` uses last `INTEREST_PAYMENT.tradeDate` as the new accrual start).

---

## 6. Amortização (AMORTIZATION)

Supported for: `DEBENTURES`, `CRI`, `CRA`, `TREASURY`, `REAL_ESTATE_FUND`, `AGRIBUSINESS_FUND`, `ETF_FIXED_INCOME`.

Amortization is a partial repayment of principal before maturity. The holding's `netValue` balance decreases. Quantity and `averageCost` are **not changed** by amortization.

### 6.1 RecordAmortizationDrawer

| Field | Required | Notes |
|---|---|---|
| Trade date | ✅ | Date of amortization payment |
| Gross value | ✅ | Principal amount returned, must be > 0 |
| Net value | ✅ | Net of IR, must be ≤ `grossValue` |
| Income tax | ❌ | IR withheld |
| IOF | ❌ | Typically 0 |
| Destination account | ❌ | Account to credit; defaults to same custodian account |

### 6.2 Service Validation (RecordAmortizationService)

- Holding asset type must be in `AMORTIZABLE_ASSET_TYPES`.
- `grossValue > 0`, `netValue >= 0`, `netValue ≤ grossValue`.

---

## 7. Transferência de Custódia (CUSTODY_TRANSFER)

Moves a fixed income position from one custody account to another (same user, different brokers). Uses `TransferCustodyService`.

See the shared custody transfer rules in `07-equities.md` section 7 (same service, same rules). Fixed income uses `CUSTODY_TRANSFER` type (not `QUOTA_TRANSFER` which is reserved for funds).

---

## 8. Fixed Income Valuation

Backend service: `FixedIncomeValuationService.kt`

For manually recorded holdings (no Open Finance sync), the service estimates current value from the contractual terms + latest economic indicators.

### 8.1 Valuation Sources

| `ValuationSource` | When |
|---|---|
| `PROVIDER` | `grossValue` set by Open Finance or file import — takes precedence, no estimation |
| `CALCULATED` | `indexer = PRE_FIXADO` — exact formula, no indicator needed |
| `ESTIMATED` | `indexer` in {CDI, DI, IPCA, SELIC, IGP-M, etc.} — uses latest available indicator rate |
| `UNAVAILABLE` | `indexer = OUTROS`, or `issueDate = null`, or indicator data missing |

### 8.2 IR and IOF Provisioning

The valuation service computes provisioned `incomeTax` and `iof` based on:
- Brazilian regressive IR table: 22.5% (0–180 days), 20% (181–360 days), 17.5% (361–720 days), 15% (>720 days).
- IOF: applied for redemptions within 30 days of purchase.
- IR-exempt instruments (LCI, LCA, CRI, CRA): `incomeTax = 0`, `iof = 0`.

### 8.3 Post-Coupon Accrual Context

After an `INTEREST_PAYMENT` is recorded, the valuation engine resets the accrual clock. The principal for accrual = `averageCost × quantity` (not the coupon's `grossValue`). The accrual starts from `lastInterestPaymentDate` (the most recent `INTEREST_PAYMENT.tradeDate`).

---

## 9. Business Rules

**BR-1: Weighted average cost on PURCHASE.**
`averageCost = (existingQty × existingCost + newQty × unitPrice) / totalQty`

**BR-2: SALE does not change averageCost.**
Only `quantity` decreases. `averageCost` is preserved.

**BR-3: Coupon payments do not change quantity or averageCost.**
Coupon payments are income events only. Position size is unchanged.

**BR-4: Amortization does not change quantity or averageCost (V1).**
The backend records the transaction but does not adjust quantity or averageCost for amortization. The user must manually record a SALE or MATURITY to close/reduce the position if needed. This is a known simplification.

**BR-5: Tax exemption auto-detection.**
LCI, LCA, CRI, CRA: `isTaxExempt` defaults to `true` in the form. User can override manually if needed.

**BR-6: Only MANUAL transactions are editable.**
Transactions with `source ≠ MANUAL` (OPEN_FINANCE, FILE_IMPORT) are read-only.

**BR-7: Holding quantity cannot go negative.**
On SALE/MATURITY, the service validates `quantitySold ≤ currentQuantity`.

---

## 10. User Scenarios

**Scenario 1 — Record a CDB position**
User clicks "Registrar ativo", selects "Renda Fixa", fills in asset name ("CDB Banco Inter"), selects issuer "Banco Inter", enters R$ 10.000, trade date, issue date, maturity date (e.g., 2027-06-30), indexer CDI at 115%, coupon frequency AT_MATURITY. On submit: asset created, holding created, PURCHASE transaction recorded, maturity pending action scheduled.

**Scenario 2 — Record an NTN-B (Tesouro IPCA+ com juros semestrais)**
User selects "TREASURY", enters asset name, issue date, maturity date, indexer IPCA, fixed annual rate 6.0% (the spread), coupon frequency SEMI_ANNUAL, coupon day of month 15. On submit: holding created. `CouponScheduleService` computes the schedule; pending action created 7 days before the next coupon.

**Scenario 3 — Confirm a coupon payment**
User sees the "Pagamento de cupom" pending action in the drawer. Clicks it. `CouponPaymentConfirmDrawer` opens pre-filled with holding data and the next coupon date. User enters gross value R$ 500 and net value R$ 425 (IR 15%). On confirm: INTEREST_PAYMENT recorded, CREDIT of R$ 425 created in the custodian account, pending action completed, next coupon pending action scheduled.

**Scenario 4 — Confirm a maturity**
User sees "Vencimento" pending action for a CDB matured yesterday. Opens `MaturityConfirmDrawer`. Enters gross value R$ 12.000, net value R$ 10.800 (IR 15%). On confirm: MATURITY transaction recorded, holding closed (`closeReason = MATURED`), CREDIT of R$ 10.800 in account, pending action completed.

**Scenario 5 — Early redemption**
User selects holding > context menu > "Resgatar". OUTFLOW tab of `RecordFixedIncomeDrawer` opens. Enters redemption gross/net values. On submit: SALE transaction recorded, `closeReason = REDEEMED_EARLY`, pending actions cancelled.

**Scenario 6 — Record amortization on a CRI**
User selects CRI holding > context menu > "Amortização". `RecordAmortizationDrawer` opens. User enters R$ 1.000 gross, R$ 900 net (IR). On submit: AMORTIZATION transaction recorded, CREDIT of R$ 900 on custodian account. Position quantity unchanged.

**Scenario 7 — View valuation estimate**
User views a CDB holding in the grid. `grossValue` shows "R$ 11.450" with a tooltip indicating `valuationSource = ESTIMATED` based on CDI data from 2026-04-19. A warning chip appears next to the total in `InvestmentSummary`.

---

## 11. Validations

| Field | Rule | Enforced in |
|---|---|---|
| Asset type (fixed income) | Must be in fixed income types | `RecordFixedIncomeDrawer` (form) |
| Issuer | Required | `IssuerSelection` component + service |
| Trade date | Within 5-year window | `getFinanceMinAllowedDate()` + service |
| Maturity date | Must be ≥ issue date | Frontend form |
| Coupon day of month | Must be 1–31 when provided | Frontend + domain |
| Gross value | Must be > 0 | Service layer |
| Net value (on SALE) | Must be ≤ grossValue | Service layer |
| Amortization asset type | Must be in `AMORTIZABLE_ASSET_TYPES` | `RecordAmortizationService` |
| Maturity confirm | `maturityDate` must be ≤ today | `HoldingMaturityConfirmService` |
| Maturity confirm | Pending action must exist | `HoldingMaturityConfirmService` |
| Quantity on SALE | `quantitySold ≤ currentQuantity` | `InvestmentTransactionService` |

---

## 12. Open Questions / Gaps

**12.1 Amortization does not reduce the holding's book value (averageCost × quantity).**
The `RecordAmortizationService` records the transaction but does not adjust `quantity` or `averageCost`. The correct behavior for amortizing instruments is to reduce the notional (`averageCost` or a separate `principalBalance` field). This is a V1 simplification — tracked as future work.

**12.2 PRIZE, FINE, LATE_PAYMENT_INTEREST have no frontend UI.**
`InvestmentTransactionType.PRIZE`, `FINE`, and `LATE_PAYMENT_INTEREST` exist in the backend enum and are valid for Debêntures/CRI/CRA but have no drawer in the frontend. They can be recorded directly via the GraphQL API but not through the UI.

**12.3 ETF_FIXED_INCOME uses fixed income form but behaves like an equity.**
`ETF_FIXED_INCOME` (e.g., IRFM11) is listed on B3 with live pricing but is grouped in the fixed income class. The frontend currently treats it as a fixed income instrument in the record form. A B3 autocomplete for these tickers is not wired up.

**12.4 Cost basis should use netValue / quantity for some instruments.**
Per DEC-4 (holding-transaction-operations.md), the cost basis for instruments with brokerage fees should use `netValue / quantity`. For most fixed income instruments (no brokerage), this is not relevant. But for ETF_FIXED_INCOME traded on B3, the distinction may matter.
