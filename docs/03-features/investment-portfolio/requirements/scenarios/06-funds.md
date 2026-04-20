# Test Scenarios: Fundos de Investimento

**Requirements ref:** `06-funds.md`
**Document date:** 2026-04-20

---

```gherkin
Feature: Aplicação em Fundo (PURCHASE / SUBSCRIPTION)
  As a user
  I want to record an application in a mutual fund
  So that my fund holding is tracked with the correct quantity and cost basis

  Background:
    Given I am authenticated
    And I am on the /personal-finance/investments page
    And I have at least one investment account selected

  # ─── HAPPY PATHS — MARKET_DATA mode ──────────────────────────────────────

  Scenario: Record first application in a known CVM fund (MARKET_DATA mode)
    Given the RecordFundHoldingDrawer is open in INFLOW mode
    When I search for "Núcleo FIC FIM" in the fund autocomplete (by CNPJ or name)
    And I select pricing mode "MARKET_DATA"
    And I fill in:
      | Trade date    | 2026-03-01    |
      | Quantity      | 1234.56       |
      | Unit price    | 10.50         |
      | Gross value   | 12962.88      |
    And I click "Aplicar"
    Then the fund asset is created or found by fundCnpj
    Then recordInvestmentTransaction is called with:
      | direction    | INFLOW       |
      | type         | PURCHASE     |
      | quantity     | 1234.56      |
      | unitPrice    | 10.50        |
      | grossValue   | 12962.88     |
      | tradeDate    | 2026-03-01   |
      | source       | MANUAL       |
    And a new holding is created with:
      | pricingMode  | MARKET_DATA  |
      | averageCost  | 10.50        |
      | quantity     | 1234.56      |

  Scenario: Additional application to same fund uses SUBSCRIPTION type
    Given I already hold 1234.56 cotas in "Núcleo FIC FIM" at averageCost = R$ 10.50
    When I record another application of 500 cotas at R$ 10.80
    Then recordInvestmentTransaction is called with type = SUBSCRIPTION (not PURCHASE)
    And the holding averageCost is updated via weighted average:
      | newAverageCost = (1234.56 × 10.50 + 500 × 10.80) / 1734.56 |

  Scenario: First application uses PURCHASE type
    Given I have no position in the selected fund
    When I apply to the fund for the first time
    Then recordInvestmentTransaction is called with type = PURCHASE

  # ─── HAPPY PATHS — MANUAL mode ────────────────────────────────────────────

  Scenario: Record a PGBL with manual valuation (MANUAL mode)
    Given the RecordFundHoldingDrawer is open in INFLOW mode
    When I select a PGBL fund
    And I select pricing mode "MANUAL"
    And I fill in:
      | Trade date       | 2026-04-01   |
      | Gross value      | 50000.00     |
      | Valued at        | 2026-03-31   |
      | Manual gross val | 50000.00     |
    And I click "Aplicar"
    Then the holding is created with:
      | pricingMode      | MANUAL       |
      | manualGrossValue | 50000.00     |
      | manualValuedAt   | 2026-03-31   |
    And the UI shows the value as "estimado" with the reference date

  Scenario: MANUAL mode — quantity and unit price fields are not required
    Given the RecordFundHoldingDrawer is open in INFLOW mode with pricing mode = MANUAL
    Then the quantity and unit price fields are not shown or not required
    And the "Valued at" date and "Manual gross value" fields are required instead

  Scenario: MARKET_DATA mode — quantity and unit price fields are required
    Given the RecordFundHoldingDrawer is open in INFLOW mode with pricing mode = MARKET_DATA
    Then the quantity and unit price fields are required
    And the "Valued at" and "Manual gross value" fields are NOT required


Feature: Resgate de Fundo (SALE)
  As a user
  I want to record the redemption of fund quotas
  So that the net proceeds are credited and the holding quantity decreases

  Background:
    Given I am authenticated
    And I hold 1234.56 cotas in "Núcleo FIC FIM" at averageCost = R$ 10.50

  Scenario: Record a fund redemption — partial
    Given the RecordFundHoldingDrawer is open in OUTFLOW mode
    When I fill in:
      | Trade date        | 2026-04-10   |
      | Quantity redeemed | 500          |
      | Net value         | 5200.00      |
    And I click "Resgatar"
    Then recordInvestmentTransaction is called with:
      | direction    | OUTFLOW      |
      | type         | SALE         |
      | quantity     | 500          |
      | netValue     | 5200.00      |
      | tradeDate    | 2026-04-10   |
    And a CREDIT of R$ 5200.00 is created on the custodian account
    And the holding quantity decreases to 734.56
    And the averageCost remains R$ 10.50 (SALE does not change averageCost)

  Scenario: Net value on resgate is always user-confirmed — no auto-calculation
    Given the RecordFundHoldingDrawer is open in OUTFLOW mode
    And the fund has a latest VNA of R$ 10.80
    When the form pre-suggests net value = 500 × R$ 10.80 = R$ 5400.00
    Then the net value field is editable and user can override to any value
    And the saved netValue is whatever the user confirms

  Scenario: Gross value is optional on resgate — can be derived from VNA
    Given the RecordFundHoldingDrawer is open in OUTFLOW mode
    When I leave gross value blank and fill only net value
    Then the form is valid
    And recordInvestmentTransaction is called with grossValue = null or omitted

  Scenario: Holding quantity cannot go below zero
    Given I hold 734.56 cotas
    When I try to redeem 800 cotas
    Then the service returns an error: "Quantidade insuficiente"

  Scenario: Redemption to a different destination account
    Given the RecordFundHoldingDrawer is open in OUTFLOW mode
    When I select "Conta Corrente BTG" as the cash destination
    And I submit
    Then recordInvestmentTransaction is called with the selected destinationAccountId


Feature: Come-cotas (WITHHOLDING_TAX)
  As a user
  I want to record the semi-annual come-cotas quota deduction
  So that my holding quantity is reduced by the correct amount and no cash movement is created

  Background:
    Given I am authenticated
    And I hold a MUTUAL_FUND with pricingMode = MARKET_DATA
    And the fund has anbimaCategory = RENDA_FIXA (subject to come-cotas)

  Scenario: Record come-cotas — quota deduction on May 15
    Given the WithholdingTaxDrawer is open for the fund holding
    When I fill in:
      | Trade date        | 2026-05-15   |
      | IR value          | 425.00       |
      | Quotas deducted   | 40.238       |
    And I click "Confirmar"
    Then ConfirmWithholdingTaxService records type = WITHHOLDING_TAX with:
      | direction        | OUTFLOW      |
      | quantity         | 40.238       |
      | unitPrice        | 425 / 40.238 |
      | tradeDate        | 2026-05-15   |
    And the holding quantity decreases by 40.238
    And NO cash ledger entry is created (come-cotas does not move cash — the fund pays the tax)

  Scenario: Come-cotas does NOT create a cash debit on the account
    Given come-cotas is recorded with IR value = R$ 425.00
    When the WITHHOLDING_TAX transaction is processed
    Then CustodianCashLedgerHandler does NOT create a DEBIT for this transaction
    And the account cash balance is UNCHANGED

  Scenario: Come-cotas is rejected for non-MUTUAL_FUND assets
    Given I have a REAL_ESTATE_FUND holding
    When the backend receives a confirmWithholdingTax request for it
    Then the service returns an error: "Come-cotas aplicável apenas para fundos de investimento"

  Scenario: IR value must be > 0
    Given the WithholdingTaxDrawer is open
    When I enter IR value = 0 and click "Confirmar"
    Then I see a validation error: IR value must be > 0
    And the mutation is NOT called

  Scenario: Quotas deducted must be > 0
    Given the WithholdingTaxDrawer is open
    When I enter quotas deducted = 0
    Then I see a validation error: quotas deducted must be > 0

  Scenario: Quotas deducted cannot exceed current holding quantity
    Given I hold 40 cotas in the fund
    When I enter quotas deducted = 50
    Then the service returns an error: "Quantidade de cotas insuficiente"

  Scenario: Quotas deducted is auto-calculated from IR value and unit price
    Given the WithholdingTaxDrawer is open
    And the current VNA is R$ 10.57
    When I enter IR value = 425.00
    Then the quotas deducted field is auto-populated with 425.00 / 10.57 ≈ 40.208
    And the user can confirm or override the value

  Scenario: Come-cotas applies to RENDA_FIXA, MULTIMERCADO, and CAMBIAL fund categories
    Given I hold three funds:
      | Fund type    | anbimaCategory  |
      | FIC RF LP    | RENDA_FIXA      |
      | FIC MM       | MULTIMERCADO    |
      | FIC Cambial  | CAMBIAL         |
    When come-cotas occurs in May
    Then each fund's holding shows come-cotas options

  Scenario: Come-cotas does NOT apply to ACOES category funds
    Given I hold a fund with anbimaCategory = ACOES
    When come-cotas time arrives
    Then no come-cotas option is available for this fund (ACOES funds are not subject to come-cotas)


Feature: Transferência de Cotas (QUOTA_TRANSFER)
  As a user
  I want to transfer mutual fund quotas between my investment accounts
  So that I can change custodians while preserving my cost basis

  Background:
    Given I am authenticated
    And I hold 1234.56 cotas of "Núcleo FIC FIM" at averageCost = R$ 10.50 in "XP Corretora"
    And I have a second account "BTG Pactual"

  Scenario: Transfer full fund position from XP to BTG — uses QUOTA_TRANSFER
    Given the TransferCustodyDrawer is open for the fund holding
    When I select "BTG Pactual" as the destination
    And I leave quantity blank (full transfer)
    And I click "Transferir"
    Then TransferCustodyService detects assetType = MUTUAL_FUND
    And records QUOTA_TRANSFER OUT on XP and QUOTA_TRANSFER IN on BTG
    And the transaction type is QUOTA_TRANSFER (NOT CUSTODY_TRANSFER)
    And the XP holding quantity becomes 0
    And a new holding at BTG is created with:
      | quantity     | 1234.56  |
      | averageCost  | 10.50    |

  Scenario: QUOTA_TRANSFER is used for MUTUAL_FUND — not CUSTODY_TRANSFER
    Given I am transferring a MUTUAL_FUND holding
    When TransferCustodyService processes the request
    Then the transaction type is QUOTA_TRANSFER
    And NOT CUSTODY_TRANSFER (which is used for equities, FIIs, fixed income, crypto)

  Scenario: averageCost is preserved during quota transfer
    Given the source holding has averageCost = R$ 10.50
    When I transfer to another account
    Then the destination holding receives averageCost = R$ 10.50

  Scenario: PGBL quota transfer uses QUOTA_TRANSFER type
    Given I hold a PGBL fund
    When I transfer it to another account
    Then the transaction type is QUOTA_TRANSFER


Feature: Fund Valuation
  As a user
  I want to see my fund holdings valued at the latest CVM VNA
  So that I can track portfolio performance

  Background:
    Given I am authenticated
    And I hold two funds: one MARKET_DATA and one MANUAL pricing mode

  Scenario: MARKET_DATA fund is valued at latest CVM VNA
    Given I hold 1234.56 cotas of a MARKET_DATA fund with latest VNA = R$ 10.80
    When the portfolio page loads
    Then the holding shows:
      | grossValue         = 1234.56 × 10.80 = R$ 13332.85 |
      | valuationSource    = MARKET_DATA                     |
      | valuationReference = date of latest VNA              |

  Scenario: MANUAL fund shows user-entered value
    Given I hold a PGBL with manualGrossValue = R$ 50000.00 and manualValuedAt = 2026-03-31
    When the portfolio page loads
    Then the holding shows:
      | grossValue         = R$ 50000.00       |
      | valuationSource    = MANUAL            |
      | valuationReference = 2026-03-31        |
    And a "estimado" indicator is shown

  Scenario: When VNA data is unavailable, valuationSource = UNAVAILABLE
    Given the financial-data service has no VNA data for a fund
    When the portfolio page loads
    Then that fund shows valuationSource = UNAVAILABLE


Feature: Fund — Validation Rules
  As a user
  I should receive clear errors when I provide invalid fund application data

  Background:
    Given I am authenticated

  Scenario: Pricing mode is required — validation error if not selected
    Given the RecordFundHoldingDrawer is open in INFLOW mode
    When I submit without selecting a pricing mode
    Then I see a validation error on the pricing mode field

  Scenario: Gross value must be > 0
    Given the RecordFundHoldingDrawer is open in INFLOW mode
    When I enter gross value = 0
    Then I see a validation error: gross value must be > 0

  Scenario: Net value on resgate must be ≤ gross value and ≥ 0
    Given the RecordFundHoldingDrawer is open in OUTFLOW mode
    When I enter gross value = 5000.00 and net value = 6000.00
    Then the service returns an error: net value cannot exceed gross value

  Scenario: MANUAL mode requires valuedAt date
    Given the RecordFundHoldingDrawer is open with pricing mode = MANUAL
    When I leave the "Valued at" date blank
    And I submit
    Then I see a validation error: "Data de valorização é obrigatória"

  Scenario: MANUAL mode requires manual gross value
    Given the RecordFundHoldingDrawer is open with pricing mode = MANUAL
    When I leave manual gross value blank
    And I submit
    Then I see a validation error: "Valor estimado é obrigatório"
```
