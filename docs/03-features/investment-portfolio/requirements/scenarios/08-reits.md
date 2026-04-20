# Test Scenarios: FIIs e Fundos de Agronegócio

**Requirements ref:** `08-reits.md`
**Document date:** 2026-04-20

---

```gherkin
Feature: Compra de FII/FIAGRO (PURCHASE)
  As a user
  I want to record the purchase of FII or FIAGRO quotas
  So that my portfolio tracks my real estate and agribusiness fund positions

  Background:
    Given I am authenticated
    And I am on the /personal-finance/investments page
    And I have at least one BROKERAGE investment account selected

  # ─── HAPPY PATHS ──────────────────────────────────────────────────────────

  Scenario: Buy MXRF11 (FII) — first purchase creates new holding
    Given the RecordEquityHoldingDrawer is open in INFLOW mode for FIIs
    When I search for "MXRF11" in the asset autocomplete
    And the autocomplete is filtered to show only REAL_ESTATE_FUND and AGRIBUSINESS_FUND
    And I select "MXRF11 — Maxi Renda FII"
    And I fill in:
      | Trade date       | 2026-04-10   |
      | Settlement date  | 2026-04-14   |
      | Quantity (cotas) | 500          |
      | Unit price       | 10.20        |
      | Brokerage fee    | 7.00         |
      | Net value        | 5093.00      |
    And I click "Registrar"
    Then recordInvestmentTransaction is called with:
      | direction        | INFLOW       |
      | type             | PURCHASE     |
      | quantity         | 500          |
      | unitPrice        | 10.20        |
      | grossValue       | 5100.00      |
      | netValue         | 5093.00      |
      | tradeDate        | 2026-04-10   |
    And a DEBIT of R$ 5093.00 is created on the custodian account
    And a new MXRF11 holding is created with:
      | quantity     | 500   |
      | averageCost  | 10.20 |

  Scenario: Buy HGLG11 (FII) — second purchase updates weighted average cost
    Given I already hold 200 HGLG11 at averageCost = R$ 155.00
    When I record a second purchase of 100 HGLG11 at R$ 165.00
    Then the holding is updated:
      | quantity     | 300                                              |
      | averageCost  | (200 × 155 + 100 × 165) / 300 = R$ 158.33       |

  Scenario: Buy a FIAGRO asset (AGRIBUSINESS_FUND)
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I search for a FIAGRO ticker (e.g., RURA11)
    And I select it
    Then the asset type is AGRIBUSINESS_FUND
    And recordInvestmentTransaction is called successfully

  Scenario: The asset autocomplete filters to FII/FIAGRO only
    Given the RecordEquityHoldingDrawer is opened from the FIIs/FIAGROs group
    When I type a stock ticker (e.g., "PETR4") in the autocomplete
    Then PETR4 does NOT appear in the suggestions
    And only REAL_ESTATE_FUND and AGRIBUSINESS_FUND assets are shown

  Scenario: Broker note fields are available and stored
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I fill in brokerage fee = R$ 7.00 and clearing fee = R$ 0.50
    Then the broker note is stored with the transaction

  Scenario: Settlement date defaults to T+2 for FII purchases
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I select trade date = 2026-04-10
    Then the settlement date field is pre-filled with 2026-04-14


Feature: Venda de FII/FIAGRO (SALE)
  As a user
  I want to record the sale of FII or FIAGRO quotas
  So that proceeds are credited and the holding quantity decreases

  Background:
    Given I am authenticated
    And I hold 500 MXRF11 at averageCost = R$ 10.20

  Scenario: Sell 200 MXRF11 — partial position
    Given the RecordEquityHoldingDrawer is open in OUTFLOW mode
    When I fill in:
      | Trade date       | 2026-05-01  |
      | Settlement date  | 2026-05-05  |
      | Quantity         | 200         |
      | Unit price       | 11.00       |
      | Net value        | 2195.00     |
    And I click "Vender"
    Then recordInvestmentTransaction is called with type = SALE
    And a CREDIT of R$ 2195.00 is created on the custodian account
    And the MXRF11 quantity decreases to 300
    And the averageCost remains R$ 10.20 (SALE does not change averageCost)


Feature: Rendimentos / Proventos (DIVIDEND)
  As a user
  I want to record monthly rendimento income from FIIs
  So that cash proceeds are credited and the holding position is unchanged

  Background:
    Given I am authenticated
    And I hold 500 MXRF11 in "XP Corretora"

  Scenario: Record monthly rendimento — qualifying FII (IR-exempt)
    Given the RecordCorporateEventDrawer is open with event type DIVIDEND
    When I fill in:
      | Trade date   | 2026-04-15   |
      | Gross value  | 50.00        |
      | Net value    | 50.00        |
      | Income tax   | 0.00         |
    And I click "Registrar"
    Then recordInvestmentTransaction is called with:
      | type         | DIVIDEND     |
      | direction    | INFLOW       |
      | grossValue   | 50.00        |
      | netValue     | 50.00        |
      | incomeTax    | 0.00         |
    And a CREDIT of R$ 50.00 is created on the custodian account
    And the MXRF11 holding quantity and averageCost are UNCHANGED

  Scenario: Record rendimento for a non-qualifying FII — IR applies
    Given the RecordCorporateEventDrawer is open for DIVIDEND
    When I enter gross value = 100.00 and net value = 85.00 and income tax = 15.00
    Then recordInvestmentTransaction is called with:
      | netValue   | 85.00  |
      | incomeTax  | 15.00  |
    And a CREDIT of R$ 85.00 is created on the custodian account

  Scenario: System does not enforce IR exemption criteria automatically
    Given the RecordCorporateEventDrawer is open for DIVIDEND
    When I enter income tax = 0.00 for a non-qualifying FII
    Then the form accepts the value without validation warning
    And the user is responsible for the correct tax amount


Feature: Amortização de Cota (AMORTIZATION)
  As a user
  I want to record an amortization payment from a FII
  So that the principal return is credited to my account

  Background:
    Given I am authenticated
    And I hold 200 HGLG11 (REAL_ESTATE_FUND) in "XP Corretora"

  Scenario: Record a FII amortization — happy path
    Given the RecordAmortizationDrawer is open for HGLG11
    When I fill in:
      | Trade date   | 2026-04-01   |
      | Gross value  | 200.00       |
      | Net value    | 200.00       |
      | Income tax   | 0.00         |
    And I click "Registrar"
    Then RecordAmortizationService records type = AMORTIZATION with:
      | grossValue  | 200.00   |
      | netValue    | 200.00   |
    And a CREDIT of R$ 200.00 is created on the custodian account
    And the HGLG11 holding quantity is UNCHANGED (V1 simplification)
    And the averageCost is UNCHANGED (V1 simplification — cost basis overstated)

  Scenario: FIAGRO is amortizable
    Given I have an AGRIBUSINESS_FUND holding
    When I try to open RecordAmortizationDrawer
    Then the amortization option is available

  Scenario: Amortization gross value must be > 0
    Given the RecordAmortizationDrawer is open
    When I enter gross value = 0 and click "Registrar"
    Then I see a validation error: gross value must be > 0
    And the mutation is NOT called

  Scenario: Amortization net value must be ≤ gross value
    Given the RecordAmortizationDrawer is open
    When I enter gross value = 200.00 and net value = 250.00
    Then the service returns an error: net value cannot exceed gross value


Feature: Subscrição de Cotas (SUBSCRIPTION)
  As a user
  I want to record exercising FII subscription rights
  So that my holding quantity and cost basis are updated

  Background:
    Given I am authenticated
    And I hold 200 HGLG11 at averageCost = R$ 155.00

  Scenario: Record a FII quota subscription
    Given the RecordCorporateEventDrawer is open with event type SUBSCRIPTION
    When I fill in:
      | Trade date       | 2026-05-01  |
      | Quantity         | 10          |
      | Unit price       | 160.00      |
      | Net value        | 1600.00     |
    And I click "Registrar"
    Then recordInvestmentTransaction is called with type = SUBSCRIPTION
    And the holding averageCost is updated via weighted average:
      | newAverageCost = (200 × 155 + 10 × 160) / 210 = R$ 155.24 |
    And a DEBIT of R$ 1600.00 is created on the custodian account


Feature: Desdobramento / Grupamento FII (STOCK_SPLIT / REVERSE_SPLIT)
  As a user
  I want to record a FII split or reverse split
  So that the holding reflects the correct post-event quota count and cost per quota

  Background:
    Given I am authenticated
    And I hold 100 XPML11 at averageCost = R$ 120.00

  Scenario: Record a 2:1 FII split
    Given the RecordCorporateEventDrawer is open with event type STOCK_SPLIT
    When I fill in trade date = 2026-06-01 and split ratio = 2:1
    Then the holding is updated:
      | quantity     | 200                             |
      | averageCost  | (100 × 120) / 200 = R$ 60.00   |
    And no cash movement is created

  Scenario: Record a 1:2 FII reverse split
    Given I hold 200 XPML11 at averageCost = R$ 60.00
    When I record a REVERSE_SPLIT with ratio 1:2
    Then the holding is updated:
      | quantity     | 100                               |
      | averageCost  | (200 × 60) / 100 = R$ 120.00     |
    And no cash movement is created


Feature: Aluguel de Cotas FII/FIAGRO (STOCK_LENDING)
  As a user
  I want to record BTC B3 lending income for my FII holdings
  So that the net proceeds are credited

  Background:
    Given I am authenticated
    And I hold 1000 MXRF11

  Scenario: Record stock lending income for a FII
    Given the RecordStockLendingDrawer is open for MXRF11
    When I fill in gross value = 80.00, net value = 68.00, income tax = 12.00
    And I click "Registrar"
    Then RecordStockLendingService accepts the request (FII is in LENDABLE_ASSET_TYPES)
    And a CREDIT of R$ 68.00 is created on the custodian account
    And the MXRF11 holding quantity and averageCost are UNCHANGED

  Scenario: FIAGRO is also eligible for stock lending
    Given I hold a AGRIBUSINESS_FUND holding
    When I try to record stock lending for it
    Then the service accepts the request (AGRIBUSINESS_FUND is in LENDABLE_ASSET_TYPES)


Feature: Transferência de Custódia — FII/FIAGRO (CUSTODY_TRANSFER)
  As a user
  I want to transfer FII quotas between my investment accounts
  So that I can change brokers while preserving my cost basis

  Background:
    Given I am authenticated
    And I hold 200 MXRF11 at averageCost = R$ 10.20 in "XP Corretora"
    And I have a second account "Rico Investimentos"

  Scenario: Transfer full MXRF11 position from XP to Rico
    Given the TransferCustodyDrawer is open for the MXRF11 holding
    When I select "Rico Investimentos" as the destination
    And I leave quantity blank (full transfer)
    And I click "Transferir"
    Then TransferCustodyService records CUSTODY_TRANSFER (not QUOTA_TRANSFER) because assetType = REAL_ESTATE_FUND
    And the XP holding quantity becomes 0
    And a new MXRF11 holding is created at Rico with:
      | quantity     | 200   |
      | averageCost  | 10.20 |

  Scenario: CUSTODY_TRANSFER type is used for FIIs (not QUOTA_TRANSFER)
    Given I am transferring a REAL_ESTATE_FUND holding
    When TransferCustodyService processes the request
    Then the transaction type is CUSTODY_TRANSFER
    And NOT QUOTA_TRANSFER (which is reserved for MUTUAL_FUND only)

  Scenario: averageCost is preserved during FII custody transfer
    Given the source holding has averageCost = R$ 10.20
    When I transfer to another account
    Then the destination holding receives averageCost = R$ 10.20


Feature: FII/FIAGRO Valuation
  As a user
  I want to see my FII/FIAGRO holdings valued at current B3 prices
  So that I can monitor portfolio performance

  Background:
    Given I am authenticated
    And I hold MXRF11 and HGLG11

  Scenario: Holdings are valued with live B3 prices
    When the portfolio page loads
    Then each FII holding shows:
      | grossValue      = quantity × latestPrice |
      | valuationSource = MARKET_DATA             |

  Scenario: When market data is unavailable, cost basis is used as fallback
    Given the financial-data service cannot return a price for MXRF11
    When the portfolio page loads
    Then MXRF11 shows:
      | grossValue      = quantity × averageCost |
      | valuationSource = UNAVAILABLE             |

  Scenario: FII rendimentos provisioned IR is not computed in V1
    Given I hold a qualifying FII
    When the valuation runs
    Then incomeTax = 0 in the valuation output (IR on rendimentos is exempt; no IR provisioning)
```
