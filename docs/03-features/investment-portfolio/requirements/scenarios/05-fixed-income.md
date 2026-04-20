# Test Scenarios: Fixed Income (Renda Fixa)

**Requirements ref:** `05-fixed-income.md`
**Document date:** 2026-04-20

---

```gherkin
Feature: Record Fixed Income Holding (PURCHASE)
  As a user
  I want to manually record a fixed income investment
  So that I can track its value, maturity, and coupon schedule in my portfolio

  Background:
    Given I am authenticated
    And I am on the /personal-finance/investments page
    And I have at least one investment account selected

  # ─── HAPPY PATHS — APORTE (PURCHASE) ─────────────────────────────────────

  Scenario: Record a CDI-indexed CDB
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I fill in:
      | Asset type          | CDB                       |
      | Asset name          | CDB Banco Inter 115% CDI  |
      | Issuer              | Banco Inter               |
      | Trade date          | 2026-03-01                |
      | Issue date          | 2026-03-01                |
      | Maturity date       | 2028-03-01                |
      | Gross value         | 10000.00                  |
      | Indexer             | CDI                       |
      | Indexer percentage  | 115                       |
      | Coupon frequency    | AT_MATURITY               |
      | Income tax exempt   | false                     |
    And I click "Registrar"
    Then createInvestmentAsset is called with:
      | name       | CDB Banco Inter 115% CDI |
      | assetType  | CDB                      |
      | issuerName | Banco Inter              |
    Then recordInvestmentTransaction is called with:
      | direction           | INFLOW       |
      | type                | PURCHASE     |
      | quantity            | 1            |
      | unitPrice           | 10000.00     |
      | grossValue          | 10000.00     |
      | tradeDate           | 2026-03-01   |
      | settlementDate      | 2026-03-01   |
      | source              | MANUAL       |
      | indexer             | CDI          |
      | indexerPercentage   | 1.15         |
      | issueDate           | 2026-03-01   |
      | maturityDate        | 2028-03-01   |
      | couponFrequency     | AT_MATURITY  |
      | isTaxExempt         | false        |
    And the drawer closes
    And the holding appears in the portfolio table
    And a INVESTMENT_MATURED pending action is scheduled for 2028-03-02

  Scenario: Record a pre-fixed LCI (isTaxExempt auto-checked)
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I select asset type "LCI"
    Then the "Isento de IR" checkbox is automatically checked
    When I fill in:
      | Asset name         | LCI Santander Prefixado 14% |
      | Issuer             | Santander                   |
      | Trade date         | 2026-04-01                  |
      | Issue date         | 2026-04-01                  |
      | Maturity date      | 2027-04-01                  |
      | Gross value        | 5000.00                     |
      | Indexer            | PRE_FIXADO                  |
      | Fixed annual rate  | 14.0                        |
      | Coupon frequency   | AT_MATURITY                 |
    And I click "Registrar"
    Then recordInvestmentTransaction is called with:
      | indexer      | PRE_FIXADO |
      | preFixedRate | 0.14       |
      | isTaxExempt  | true       |
    And the indexer percentage field was NOT sent in the payload

  Scenario: Record an LCA (isTaxExempt auto-checked, user can override)
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I select asset type "LCA"
    Then the "Isento de IR" checkbox is automatically checked
    When I uncheck "Isento de IR"
    Then "Isento de IR" remains unchecked
    When I submit the form
    Then recordInvestmentTransaction is called with isTaxExempt = false

  Scenario: Record a CRI with CRA (isTaxExempt auto-checked)
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I select asset type "CRI"
    Then the "Isento de IR" checkbox is automatically checked
    When I select asset type "CRA"
    Then the "Isento de IR" checkbox remains checked

  Scenario: Record a Tesouro IPCA+ com juros semestrais (NTN-B)
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I select asset type "TREASURY"
    Then the issuer field auto-fills with "Tesouro Nacional" and becomes read-only
    When I fill in:
      | Asset name         | Tesouro IPCA+ 2035 com Juros Semestrais |
      | Trade date         | 2026-01-15                              |
      | Issue date         | 2026-01-15                              |
      | Maturity date      | 2035-08-15                              |
      | Gross value        | 30000.00                                |
      | Indexer            | IPCA                                    |
      | Fixed annual rate  | 6.0                                     |
      | Coupon frequency   | SEMI_ANNUAL                             |
      | Coupon day         | 15                                      |
    And I click "Registrar"
    Then recordInvestmentTransaction is called with:
      | indexer          | IPCA         |
      | preFixedRate     | 0.06         |
      | couponFrequency  | SEMI_ANNUAL  |
      | couponDayOfMonth | 15           |
    And a COUPON_PAYMENT_DUE pending action is scheduled 7 days before the next coupon date

  Scenario: Record a Debênture with IPCA+ indexer and semi-annual amortization
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I fill in:
      | Asset type         | DEBENTURES             |
      | Asset name         | Debenture VALE IPCA+6% |
      | Issuer             | VALE S.A.              |
      | Indexer            | IPCA                   |
      | Fixed annual rate  | 6.0                    |
      | Coupon frequency   | SEMI_ANNUAL            |
      | Coupon day         | 1                      |
      | Grace period date  | 2027-01-01             |
      | Maturity date      | 2030-01-01             |
      | Gross value        | 100000.00              |
    And I click "Registrar"
    Then recordInvestmentTransaction is called with:
      | assetType         | DEBENTURES   |
      | couponFrequency   | SEMI_ANNUAL  |
      | couponDayOfMonth  | 1            |
      | gracePeriodDate   | 2027-01-01   |

  Scenario: Indexer % field is hidden when indexer is PRE_FIXADO
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I select indexer "PRE_FIXADO"
    Then the "% do indexador" field is NOT visible
    And the "Taxa (% a.a.)" field IS visible

  Scenario: Indexer % field is visible and required when indexer is CDI
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I select indexer "CDI"
    Then the "% do indexador" field IS visible
    And the "Taxa (% a.a.)" field is NOT visible

  Scenario: Indexer % is sent as decimal fraction (115% → 1.15)
    Given the RecordFixedIncomeDrawer is open
    When I enter indexer percentage = 115
    Then recordInvestmentTransaction is called with indexerPercentage = 1.15

  Scenario: Fixed annual rate is sent as decimal fraction (14.5% → 0.145)
    Given the RecordFixedIncomeDrawer is open
    When I enter fixed annual rate = 14.5
    Then recordInvestmentTransaction is called with preFixedRate = 0.145

  Scenario: Coupon day of month field appears only when coupon frequency ≠ AT_MATURITY
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I select coupon frequency "AT_MATURITY"
    Then the "Dia do cupom" field is NOT visible
    When I change coupon frequency to "MONTHLY"
    Then the "Dia do cupom" field IS visible and required

  Scenario: Grace period date is optional and not required
    Given the RecordFixedIncomeDrawer is open in INFLOW mode
    When I submit a valid CDB form without filling in grace period date
    Then recordInvestmentTransaction is called without gracePeriodDate
    And the form submits successfully


Feature: Resgate Antecipado (SALE)
  As a user
  I want to record an early redemption of a fixed income position
  So that I can track the realized proceeds and close the position

  Background:
    Given I am authenticated
    And I have an open fixed income holding "CDB Banco Inter 115% CDI" with quantity = 1

  Scenario: Record an early redemption — full position
    Given the RecordFixedIncomeDrawer is open in OUTFLOW mode for the CDB holding
    When I fill in:
      | Trade date   | 2026-06-01   |
      | Gross value  | 10500.00     |
      | Net value    | 8925.00      |
      | Income tax   | 1575.00      |
    And I click "Resgatar"
    Then recordInvestmentTransaction is called with:
      | direction   | OUTFLOW          |
      | type        | SALE             |
      | grossValue  | 10500.00         |
      | netValue    | 8925.00          |
      | incomeTax   | 1575.00          |
      | tradeDate   | 2026-06-01       |
      | source      | MANUAL           |
    And a CREDIT of R$ 8925.00 is created on the custodian account
    And the holding quantity decreases to 0
    And the holding closeReason is set to REDEEMED_EARLY
    And the INVESTMENT_MATURED pending action is cancelled

  Scenario: Early redemption with IOF (within 30 days of purchase)
    Given I purchased a CDB on 2026-04-01 (28 days ago)
    When I record a SALE on 2026-04-29
    And I fill in gross value R$ 10.020, net value R$ 9.900, IOF R$ 120
    Then recordInvestmentTransaction is called with:
      | iof        | 120.00  |
      | netValue   | 9900.00 |

  Scenario: averageCost is not changed by a SALE
    Given a holding with averageCost = 10000.00 and quantity = 1
    When I record a SALE for the full quantity
    Then the holding averageCost remains 10000.00 until the position is closed

  Scenario: Closed position is preserved for history (not deleted)
    Given I record an early redemption that reduces quantity to 0
    Then the holding record is preserved with closedAt and closeReason = REDEEMED_EARLY
    And the holding is no longer shown in the active holdings grid


Feature: Confirmar Vencimento (MATURITY)
  As a user
  I want to confirm that a matured position was paid
  So that I can record the net proceeds and close the position

  Background:
    Given I am authenticated
    And I have a CDB holding with maturityDate = yesterday
    And there is an INVESTMENT_MATURED pending action for this holding

  Scenario: Confirm maturity via pending action — full flow
    Given the pending actions drawer shows "Vencimento — CDB Banco Inter"
    When I click the pending action
    Then the MaturityConfirmDrawer opens with maturityDate pre-filled
    When I fill in:
      | Gross value  | 12000.00   |
      | Net value    | 10200.00   |
      | Income tax   | 1800.00    |
    And I click "Confirmar"
    Then recordInvestmentTransaction (type=MATURITY, direction=OUTFLOW) is called with:
      | grossValue  | 12000.00    |
      | netValue    | 10200.00    |
      | incomeTax   | 1800.00     |
    And a CREDIT of R$ 10200.00 is created on the custodian account
    And the holding is closed: closedAt = maturityDate, closeReason = MATURED
    And the pending action status is set to COMPLETED

  Scenario: Maturity drawer appears in holding context menu when maturityDate ≤ today
    Given I have a holding with maturityDate = today
    When I open the holding's context menu
    Then I see a "Confirmar vencimento" option
    When I click it
    Then the MaturityConfirmDrawer opens

  Scenario: Maturity is rejected when maturityDate is in the future
    Given I have a holding with maturityDate = tomorrow
    When the backend receives a confirmMaturity request for this holding
    Then the service returns an error: "Data de vencimento ainda não atingida"
    And the holding remains open

  Scenario: Maturity is rejected when no INVESTMENT_MATURED pending action exists
    Given I have a holding with maturityDate = yesterday
    But no INVESTMENT_MATURED pending action exists for this holding
    When the backend receives a confirmMaturity request
    Then the service returns an error: "Ação pendente de vencimento não encontrada"

  Scenario: Maturity is rejected when the holding is already closed
    Given I have a holding with closeReason = REDEEMED_EARLY
    When the backend receives a confirmMaturity request
    Then the service returns an error: "Posição já encerrada"

  Scenario: Close reason can be overridden to REDEEMED_EARLY during maturity confirm
    Given the MaturityConfirmDrawer is open
    When I change the close reason to "REDEEMED_EARLY"
    And I submit
    Then the holding closeReason is set to REDEEMED_EARLY


Feature: Pagamento de Cupom (INTEREST_PAYMENT)
  As a user
  I want to confirm a coupon payment received
  So that the cash proceeds are recorded and the next coupon action is scheduled

  Background:
    Given I am authenticated
    And I have a holding with couponFrequency = SEMI_ANNUAL
    And there is a COUPON_PAYMENT_DUE pending action for this holding

  Scenario: Confirm a coupon payment — happy path
    Given the pending actions drawer shows "Pagamento de cupom — Tesouro IPCA+ 2035"
    When I click the pending action
    Then CouponPaymentConfirmDrawer opens with the next coupon date pre-filled
    When I fill in:
      | Trade date   | 2026-08-15   |
      | Gross value  | 500.00       |
      | Net value    | 425.00       |
      | Income tax   | 75.00        |
    And I click "Confirmar"
    Then recordInvestmentTransaction (type=INTEREST_PAYMENT) is called with:
      | grossValue  | 500.00       |
      | netValue    | 425.00       |
      | incomeTax   | 75.00        |
      | tradeDate   | 2026-08-15   |
    And a CREDIT of R$ 425.00 is created on the custodian account
    And the current COUPON_PAYMENT_DUE pending action is marked COMPLETED
    And a new COUPON_PAYMENT_DUE pending action is created for the next coupon date
    And the holding quantity and averageCost are UNCHANGED

  Scenario: Coupon payment is rejected for AT_MATURITY holdings
    Given I have a holding with couponFrequency = AT_MATURITY
    When the backend receives a confirmCouponPayment request
    Then the service returns an error: "Ativo não possui frequência de cupom"

  Scenario: Coupon payment with destination account
    Given the CouponPaymentConfirmDrawer is open
    When I select a different destination account "Conta Corrente BTG"
    And I submit
    Then recordInvestmentTransaction is called with the selected destinationAccountId

  Scenario: The coupon schedule timeline is visible in the holding detail
    Given I have a holding with couponFrequency = SEMI_ANNUAL and couponDayOfMonth = 15
    When I open the holding detail drawer
    Then I see a coupon schedule timeline with upcoming coupon dates listed

  Scenario: Coupon pending action becomes visible 7 days before the coupon date
    Given I recorded a SEMI_ANNUAL coupon holding today
    When the next coupon date is 30 days from now
    Then no COUPON_PAYMENT_DUE pending action is visible yet (visibleFrom = nextDate − 7 days)
    When the date reaches nextCouponDate − 7 days
    Then the COUPON_PAYMENT_DUE pending action becomes visible


Feature: Amortização (AMORTIZATION)
  As a user
  I want to record an amortization payment received from a fixed income instrument
  So that the cash proceeds are recorded in my account

  Background:
    Given I am authenticated
    And I have a "CRI CSHG IPCA+7%" holding with assetType = CRI

  Scenario: Record an amortization on a CRI — happy path
    Given the RecordAmortizationDrawer is open for the CRI holding
    When I fill in:
      | Trade date   | 2026-05-01   |
      | Gross value  | 1000.00      |
      | Net value    | 1000.00      |
      | Income tax   | 0.00         |
    And I click "Registrar"
    Then RecordAmortizationService records type=AMORTIZATION with:
      | grossValue  | 1000.00   |
      | netValue    | 1000.00   |
    And a CREDIT of R$ 1000.00 is created on the custodian account
    And the holding quantity is UNCHANGED
    And the holding averageCost is UNCHANGED (V1 simplification)

  Scenario: Record an amortization on a Debênture
    Given I have a DEBENTURES holding
    When I open the RecordAmortizationDrawer and submit valid data
    Then the amortization is recorded successfully

  Scenario: Amortization is rejected for non-amortizable asset types
    Given I have a CDB holding (not in AMORTIZABLE_ASSET_TYPES)
    When the backend receives a recordAmortization request for the CDB
    Then the service returns an error: "Tipo de ativo não suporta amortização"

  Scenario: Amortization gross value must be > 0
    Given the RecordAmortizationDrawer is open
    When I enter gross value = 0
    And I click "Registrar"
    Then a validation error appears: gross value must be greater than zero
    And the mutation is NOT called

  Scenario: Amortization net value must be ≤ gross value
    Given the RecordAmortizationDrawer is open
    When I enter gross value = 1000.00 and net value = 1200.00
    And I click "Registrar"
    Then the service returns an error: "Valor líquido não pode exceder o valor bruto"

  Scenario: TREASURY is amortizable
    Given I have a TREASURY holding (Tesouro Prefixado)
    When I try to open RecordAmortizationDrawer for this holding
    Then the amortization option is available
    And the drawer opens successfully


Feature: Transferência de Custódia — Fixed Income (CUSTODY_TRANSFER)
  As a user
  I want to transfer a fixed income position to a different custody account
  So that I can migrate brokers without losing my cost basis history

  Background:
    Given I am authenticated
    And I have an "XP Investimentos" account with a CDB holding (quantity=1, averageCost=10000)
    And I have a second "BTG Pactual" investment account

  Scenario: Transfer full CDB position from XP to BTG
    Given the TransferCustodyDrawer is open for the XP CDB holding
    When I select "BTG Pactual" as the destination account
    And I leave quantity blank (full transfer)
    And I click "Transferir"
    Then TransferCustodyService records a CUSTODY_TRANSFER OUT on XP:
      | quantity       | 1         |
      | transferValue  | 10000.00  |
    And a CUSTODY_TRANSFER IN on BTG:
      | quantity       | 1         |
      | averageCost    | 10000.00  |
    And the XP holding quantity becomes 0
    And the BTG holding is created with averageCost = 10000.00 (preserved)

  Scenario: QUOTA_TRANSFER is NOT used for fixed income — CUSTODY_TRANSFER is used
    Given I have a fixed income holding (any non-MUTUAL_FUND type)
    When TransferCustodyService processes the transfer
    Then the transaction type is CUSTODY_TRANSFER (not QUOTA_TRANSFER)

  Scenario: Transfer preserves averageCost at the destination
    Given the source holding has averageCost = 12345.67
    When I transfer the full position
    Then the destination holding is created with averageCost = 12345.67


Feature: Fixed Income Valuation
  As a user
  I want to see the estimated current value of my fixed income holdings
  So that I can track portfolio performance over time

  Background:
    Given I am authenticated
    And I have fixed income holdings with various indexer types

  Scenario: PRE_FIXADO holding has CALCULATED valuation
    Given I have a pre-fixed CDB
    When the portfolio page loads
    Then the holding's valuationSource is CALCULATED
    And grossValue is computed from the exact formula (no estimation uncertainty)

  Scenario: CDI-indexed holding has ESTIMATED valuation
    Given I have a CDI-indexed CDB
    When the portfolio page loads
    Then the holding's valuationSource is ESTIMATED
    And the grossValue reflects CDI rate from the latest available data

  Scenario: OUTROS indexer results in UNAVAILABLE valuation
    Given I have a holding with indexer = OUTROS
    When the portfolio page loads
    Then the holding's valuationSource is UNAVAILABLE
    And grossValue falls back to averageCost × quantity

  Scenario: LCI, LCA, CRI, CRA have incomeTax = 0 in valuation
    Given I have an LCI holding with isTaxExempt = true
    When the valuation is computed
    Then incomeTax = 0 and iof = 0 in the valuation output

  Scenario: IR is provisioned using the regressive table
    Given I have a CDB held for 200 days
    When the valuation is computed
    Then the provisioned IR rate is 20% (181–360 day bracket)

  Scenario: IOF is provisioned for holdings held less than 30 days
    Given I have a CDB purchased 15 days ago
    When the valuation is computed
    Then the valuation includes an IOF provision

  Scenario: Post-coupon accrual resets after an INTEREST_PAYMENT
    Given I have an NTN-B holding
    And I recorded an INTEREST_PAYMENT on 2026-02-15 (grossValue = 500)
    When the valuation runs today (2026-04-20)
    Then the accrual period starts from 2026-02-15
    And the principal for accrual = averageCost × quantity (not the coupon gross)


Feature: Fixed Income — Validation Rules
  As a user
  I should receive clear errors when I provide invalid data
  So that data integrity is maintained

  Background:
    Given I am authenticated
    And I have at least one investment account selected

  Scenario: Gross value must be > 0
    Given the RecordFixedIncomeDrawer is open
    When I enter gross value = 0
    And I submit
    Then I see error: "Valor deve ser maior que zero"
    And the mutation is NOT called

  Scenario: Maturity date must be ≥ issue date
    Given the RecordFixedIncomeDrawer is open
    When I enter issue date = 2026-04-01 and maturity date = 2025-12-31
    And I submit
    Then I see error: "Data de vencimento deve ser após a data de emissão"
    And the mutation is NOT called

  Scenario: Coupon day of month is required when frequency ≠ AT_MATURITY
    Given the RecordFixedIncomeDrawer is open
    When I select coupon frequency = MONTHLY
    And I leave coupon day blank
    And I submit
    Then I see a validation error on the coupon day field

  Scenario: Coupon day must be between 1 and 31
    Given the RecordFixedIncomeDrawer is open
    When I select coupon frequency = MONTHLY
    And I enter coupon day = 32
    Then I see a validation error: "Dia deve estar entre 1 e 31"

  Scenario: Indexer percentage is required when indexer is CDI and not provided
    Given the RecordFixedIncomeDrawer is open
    When I select indexer = CDI
    And I leave indexer percentage blank
    And I submit
    Then I see a validation error on the indexer percentage field

  Scenario: Fixed rate is required when indexer is PRE_FIXADO and not provided
    Given the RecordFixedIncomeDrawer is open
    When I select indexer = PRE_FIXADO
    And I leave the fixed rate blank
    And I submit
    Then I see a validation error on the fixed rate field

  Scenario: Net value on SALE must be ≤ gross value
    Given the RecordFixedIncomeDrawer is open in OUTFLOW mode
    When I enter gross value = 10000.00 and net value = 11000.00
    And I submit
    Then the service returns an error: net value cannot exceed gross value

  Scenario: Quantity on SALE cannot exceed current holding quantity
    Given I have a holding with quantity = 1
    When I try to record a SALE with quantity = 2
    Then the service returns an error: "Quantidade insuficiente"

  Scenario: All required fields show errors on empty form submission
    Given the RecordFixedIncomeDrawer is open
    When I click "Registrar" without filling any field
    Then I see validation errors for all required fields:
      | Asset type   |
      | Asset name   |
      | Issuer       |
      | Trade date   |
      | Issue date   |
      | Maturity date|
      | Gross value  |
      | Indexer      |
```
