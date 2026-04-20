# Test Scenarios: Ações, ETFs e BDRs (Renda Variável)

**Requirements ref:** `07-equities.md`
**Document date:** 2026-04-20

---

```gherkin
Feature: Compra de Ação/ETF/BDR (PURCHASE)
  As a user
  I want to record the purchase of equities, ETFs, or BDRs
  So that my portfolio reflects my current positions and cost basis

  Background:
    Given I am authenticated
    And I am on the /personal-finance/investments page
    And I have at least one BROKERAGE investment account selected

  # ─── HAPPY PATHS ──────────────────────────────────────────────────────────

  Scenario: Buy PETR4 — first purchase creates new holding
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I search for "PETR4" in the asset autocomplete
    And I select "PETR4 — Petróleo Brasileiro" from the suggestions
    And I fill in:
      | Trade date       | 2026-04-01   |
      | Settlement date  | 2026-04-03   |
      | Quantity         | 100          |
      | Unit price       | 38.50        |
      | Net value        | 3845.00      |
    And I click "Registrar"
    Then recordInvestmentTransaction is called with:
      | direction        | INFLOW       |
      | type             | PURCHASE     |
      | quantity         | 100          |
      | unitPrice        | 38.50        |
      | grossValue       | 3850.00      |
      | netValue         | 3845.00      |
      | tradeDate        | 2026-04-01   |
      | settlementDate   | 2026-04-03   |
      | source           | MANUAL       |
    And a DEBIT of R$ 3845.00 is created on the custodian account
    And a new holding is created for PETR4 with:
      | quantity     | 100   |
      | averageCost  | 38.50 |

  Scenario: Buy more PETR4 — holding is updated with weighted average cost
    Given I already hold 100 PETR4 at averageCost = R$ 38.50
    When I record a second purchase of 50 PETR4 at R$ 42.00
    Then the holding is updated:
      | quantity     | 150                                            |
      | averageCost  | (100 × 38.50 + 50 × 42.00) / 150 = R$ 39.67  |

  Scenario: Buy BOVA11 (ETF de Renda Variável)
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I search for "BOVA11" in the asset autocomplete
    And I select it and fill in a valid purchase form
    Then recordInvestmentTransaction is called with assetType = ETF

  Scenario: Buy IVVB11 (ETF Internacional)
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I select asset "IVVB11"
    Then recordInvestmentTransaction is called with assetType = ETF_INTERNATIONAL

  Scenario: Buy a BDR (e.g., AAPL34)
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I select asset "AAPL34" (BDR)
    Then recordInvestmentTransaction is called with assetType = BDR

  Scenario: Broker note fields are filled with fee details
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I fill in:
      | Quantity                   | 100      |
      | Unit price                 | 38.50    |
      | Brokerage fee              | 3.00     |
      | Clearing settlement fee    | 0.50     |
      | Other taxes                | 0.25     |
      | Net value                  | 3845.75  |
    And I click "Registrar"
    Then the broker note fields are stored with the transaction:
      | brokerageFee             | 3.00  |
      | clearingSettlementFee    | 0.50  |
      | taxes                    | 0.25  |
      | netValue                 | 3845.75 |

  Scenario: Settlement date defaults to T+2 from trade date
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I select trade date = 2026-04-01
    Then the settlement date field is pre-filled with 2026-04-03

  Scenario: Settlement date can be manually overridden
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I select trade date = 2026-04-01
    And I change settlement date to 2026-04-04
    Then recordInvestmentTransaction is called with settlementDate = 2026-04-04

  Scenario: DEBIT ledger entry is created for net value on purchase
    Given I record a valid purchase with net value = R$ 3845.00
    Then a DEBIT of R$ 3845.00 is posted to the custodian account's ledger


Feature: Venda de Ação/ETF/BDR (SALE)
  As a user
  I want to record the sale of equities
  So that the proceeds are credited to my account and the holding quantity decreases

  Background:
    Given I am authenticated
    And I hold 100 PETR4 at averageCost = R$ 38.50 in "XP Corretora" account

  Scenario: Sell 50 PETR4 — partial position
    Given the RecordEquityHoldingDrawer is open in OUTFLOW mode for PETR4
    When I fill in:
      | Trade date       | 2026-04-15   |
      | Settlement date  | 2026-04-17   |
      | Quantity         | 50           |
      | Unit price       | 42.00        |
      | Net value        | 2095.00      |
    And I click "Vender"
    Then recordInvestmentTransaction is called with:
      | direction       | OUTFLOW      |
      | type            | SALE         |
      | quantity        | 50           |
      | unitPrice       | 42.00        |
      | netValue        | 2095.00      |
    And a CREDIT of R$ 2095.00 is created on the custodian account
    And the PETR4 holding quantity decreases to 50
    And the averageCost remains R$ 38.50 (unchanged by SALE)

  Scenario: Sell full position — holding is closed
    Given I hold 100 PETR4
    When I record a SALE of 100 PETR4
    Then the holding quantity becomes 0
    And the holding record is preserved (not deleted)

  Scenario: averageCost is not changed by a SALE
    Given I hold 100 PETR4 at averageCost = R$ 38.50
    When I sell 80 PETR4
    Then the remaining 20 PETR4 still have averageCost = R$ 38.50

  Scenario: IRRF can be entered on a SALE
    Given the RecordEquityHoldingDrawer is open in OUTFLOW mode
    When I enter IRRF (income tax) = R$ 0.20
    Then recordInvestmentTransaction is called with incomeTax = 0.20


Feature: Dividendos (DIVIDEND)
  As a user
  I want to record dividend income received from a Brazilian stock
  So that cash proceeds are credited and my holdings are unchanged

  Background:
    Given I am authenticated
    And I hold 100 PETR4 in "XP Corretora"

  Scenario: Record a dividend — Brazilian equity (IR-exempt)
    Given the RecordCorporateEventDrawer is open with event type DIVIDEND
    When I fill in:
      | Trade date   | 2026-04-20   |
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
    And the PETR4 holding quantity and averageCost are UNCHANGED

  Scenario: Record a dividend with a different destination account
    Given the RecordCorporateEventDrawer is open for DIVIDEND
    When I select "Conta Corrente BTG" as the destination account
    And I submit
    Then recordInvestmentTransaction is called with the selected destinationAccountId


Feature: JCP — Juros sobre Capital Próprio (INTEREST_ON_EQUITY)
  As a user
  I want to record JCP income received from a Brazilian stock
  So that the net amount after 15% IRRF is credited to my account

  Background:
    Given I am authenticated
    And I hold 200 ITUB4 in "XP Corretora"

  Scenario: Record a JCP payment — 15% IRRF at source
    Given the RecordCorporateEventDrawer is open with event type JCP
    When I fill in:
      | Trade date   | 2026-04-15  |
      | Gross value  | 100.00      |
      | Net value    | 85.00       |
      | Income tax   | 15.00       |
    And I click "Registrar"
    Then recordInvestmentTransaction is called with:
      | type        | INTEREST_ON_EQUITY |
      | grossValue  | 100.00             |
      | netValue    | 85.00              |
      | incomeTax   | 15.00              |
    And a CREDIT of R$ 85.00 is created on the custodian account
    And the ITUB4 holding quantity and averageCost are UNCHANGED


Feature: Desdobramento / Grupamento (STOCK_SPLIT / REVERSE_SPLIT)
  As a user
  I want to record a stock split or reverse split
  So that my quantity and cost basis reflect the correct post-event values

  Background:
    Given I am authenticated
    And I hold 100 VALE3 at averageCost = R$ 60.00

  Scenario: Record a 3:1 stock split (desdobramento)
    Given the RecordCorporateEventDrawer is open with event type STOCK_SPLIT
    When I fill in:
      | Trade date   | 2026-05-01   |
      | Split ratio  | 3:1          |
    And I click "Registrar"
    Then the VALE3 holding is updated:
      | quantity     | 300                              |
      | averageCost  | (100 × 60) / 300 = R$ 20.00     |
    And no cash ledger entry is created (grossValue = 0)

  Scenario: Record a 1:4 reverse split (grupamento)
    Given I hold 400 XPML11 at averageCost = R$ 5.00
    When I record a REVERSE_SPLIT with ratio 1:4
    Then the holding is updated:
      | quantity     | 100                              |
      | averageCost  | (400 × 5) / 100 = R$ 20.00      |
    And no cash ledger entry is created

  Scenario: Total cost basis is preserved after a split
    Given I hold 100 VALE3 at averageCost = R$ 60.00 (total cost = R$ 6000)
    When I record a 3:1 split
    Then the total cost basis = 300 × R$ 20.00 = R$ 6000.00 (unchanged)

  Scenario: Total cost basis is preserved after a reverse split
    Given I hold 400 XPML11 at averageCost = R$ 5.00 (total cost = R$ 2000)
    When I record a 1:4 reverse split
    Then the total cost basis = 100 × R$ 20.00 = R$ 2000.00 (unchanged)


Feature: Bonificação em Ações (BONUS_SHARES)
  As a user
  I want to record bonus shares received from a stock
  So that my quantity is updated and the cost basis is diluted correctly

  Background:
    Given I am authenticated
    And I hold 100 BBAS3 at averageCost = R$ 50.00

  Scenario: Record bonus shares — company distributes 10% in free shares
    Given the RecordCorporateEventDrawer is open with event type BONUS_SHARES
    When I fill in:
      | Trade date          | 2026-06-01  |
      | Bonus quantity      | 10          |
      | Unit price          | 0.00        |
    And I click "Registrar"
    Then the BBAS3 holding is updated:
      | quantity     | 110                             |
      | averageCost  | (100 × 50) / 110 = R$ 45.45    |
    And no cash movement is created (no CREDIT or DEBIT)

  Scenario: Bonus shares with a non-zero unit price for IR purposes
    Given the RecordCorporateEventDrawer is open with event type BONUS_SHARES
    When I enter bonus quantity = 10 and unit price = R$ 2.00
    Then the bonus unit price is stored with the transaction for IR record-keeping


Feature: Subscrição (SUBSCRIPTION)
  As a user
  I want to record exercising a stock subscription right
  So that my holding quantity and cost basis are updated

  Background:
    Given I am authenticated
    And I hold 1000 VALE3 in "XP Corretora"

  Scenario: Record a stock subscription
    Given the RecordCorporateEventDrawer is open with event type SUBSCRIPTION
    When I fill in:
      | Trade date       | 2026-07-01  |
      | Quantity         | 100         |
      | Unit price       | 45.00       |
      | Net value        | 4500.00     |
    And I click "Registrar"
    Then recordInvestmentTransaction is called with type = SUBSCRIPTION
    And the holding averageCost is updated via weighted average:
      | newAverageCost = (1000 × existingCost + 100 × 45) / 1100 |
    And a DEBIT of R$ 4500.00 is created on the custodian account


Feature: Aluguel de Ações / BTC B3 (STOCK_LENDING)
  As a user
  I want to record stock lending income received from B3's BTC program
  So that the net proceeds are credited to my account

  Background:
    Given I am authenticated
    And I hold 500 ITUB4 in "XP Corretora"

  Scenario: Record stock lending income — ITUB4
    Given the RecordStockLendingDrawer is open for ITUB4
    When I fill in:
      | Trade date   | 2026-04-10   |
      | Gross value  | 250.00       |
      | Net value    | 212.50       |
      | Income tax   | 37.50        |
    And I click "Registrar"
    Then RecordStockLendingService records type = STOCK_LENDING with:
      | direction    | INFLOW       |
      | grossValue   | 250.00       |
      | netValue     | 212.50       |
      | incomeTax    | 37.50        |
    And a CREDIT of R$ 212.50 is created on the custodian account
    And the ITUB4 holding quantity and averageCost are UNCHANGED

  Scenario: ETF is eligible for stock lending
    Given I hold BOVA11 (assetType = ETF)
    When I try to open RecordStockLendingDrawer for BOVA11
    Then the option is available and the drawer opens

  Scenario: BDR is eligible for stock lending
    Given I hold AAPL34 (assetType = BDR)
    When I try to open RecordStockLendingDrawer for AAPL34
    Then the option is available and the drawer opens

  Scenario: Stock lending is rejected for CRYPTO assets
    Given I have a Bitcoin holding (assetType = CRYPTO)
    When the backend receives a recordStockLending request for Bitcoin
    Then the service returns an error: "Tipo de ativo não suporta aluguel"

  Scenario: Stock lending gross value must be > 0
    Given the RecordStockLendingDrawer is open
    When I enter gross value = 0 and click "Registrar"
    Then I see a validation error: gross value must be > 0
    And the mutation is NOT called

  Scenario: Stock lending net value must be ≤ gross value
    Given the RecordStockLendingDrawer is open
    When I enter gross value = 250.00 and net value = 300.00
    And I click "Registrar"
    Then the service returns an error: net value cannot exceed gross value


Feature: Transferência de Custódia — Equities (CUSTODY_TRANSFER)
  As a user
  I want to transfer equity holdings between my investment accounts
  So that I can change brokers without affecting my cost basis

  Background:
    Given I am authenticated
    And I hold 200 BBAS3 at averageCost = R$ 55.00 in "XP Corretora"
    And I have a second account "BTG Pactual"

  Scenario: Transfer full BBAS3 position from XP to BTG
    Given the TransferCustodyDrawer is open for the BBAS3 holding
    When I select "BTG Pactual" as the destination account
    And I leave the quantity field blank (full transfer)
    And I click "Transferir"
    Then TransferCustodyService records:
      | CUSTODY_TRANSFER OUT on XP | quantity = 200, transferValue = 200 × 55 = R$ 11000 |
      | CUSTODY_TRANSFER IN on BTG | quantity = 200, averageCost = R$ 55.00               |
    And the XP holding quantity becomes 0
    And the BTG holding is created with averageCost = R$ 55.00 (preserved)
    And no cash ledger entries are created (custody transfer is non-cash)

  Scenario: Transfer partial position — 100 of 200 shares
    Given the TransferCustodyDrawer is open for the BBAS3 holding
    When I select "BTG Pactual" as the destination account
    And I enter quantity = 100
    And I click "Transferir"
    Then the XP BBAS3 holding quantity decreases to 100
    And a new BBAS3 holding is created at BTG with quantity = 100 and averageCost = R$ 55.00

  Scenario: averageCost is preserved at the destination
    Given the source holding has averageCost = R$ 55.00
    When I transfer 50 shares to another account
    Then the destination holding receives averageCost = R$ 55.00

  Scenario: CUSTODY_TRANSFER type is used for equities (not QUOTA_TRANSFER)
    Given I am transferring a STOCK holding
    When TransferCustodyService processes the request
    Then the transaction type is CUSTODY_TRANSFER (not QUOTA_TRANSFER)

  Scenario: Cannot transfer to the same account
    Given the TransferCustodyDrawer is open
    When I select the same "XP Corretora" as the destination
    And I click "Transferir"
    Then the service returns an error: "Conta de destino deve ser diferente da conta de origem"

  Scenario: Cannot transfer to an account belonging to another user
    When TransferCustodyService receives a request with a destinationAccountId belonging to another user
    Then the service returns a permission error

  Scenario: Transfer quantity cannot exceed source holding quantity
    Given the source holding has quantity = 200
    When I enter transfer quantity = 250
    Then the service returns an error: "Quantidade insuficiente para transferência"

  Scenario: Transfer is atomic — both OUT and IN succeed or both fail
    Given a valid transfer request
    When the server processes the request
    Then both the CUSTODY_TRANSFER OUT and CUSTODY_TRANSFER IN are committed in one transaction
    And if either fails, neither is persisted


Feature: Equity Valuation
  As a user
  I want to see my equity holdings valued at current market prices
  So that I can track portfolio performance in real time

  Background:
    Given I am authenticated
    And I hold PETR4, BOVA11, and AAPL34

  Scenario: Holdings are valued with live B3 prices (MARKET_DATA)
    When the portfolio page loads
    Then each equity holding shows:
      | grossValue      = quantity × latestPrice × priceFactor |
      | valuationSource = MARKET_DATA                           |

  Scenario: When market data is unavailable, cost basis is used as fallback
    Given the financial-data service cannot return a price for a holding
    When the portfolio page loads
    Then that holding shows:
      | grossValue      = quantity × averageCost |
      | valuationSource = UNAVAILABLE             |

  Scenario: priceFactor = 1 for standard equities
    Given I hold PETR4 with priceFactor = 1
    When the valuation runs
    Then grossValue = quantity × latestPrice (factor has no effect)


Feature: Equity — Validation Rules
  As a user
  I should receive clear errors when I provide invalid purchase or sale data

  Background:
    Given I am authenticated
    And I have a BROKERAGE account selected

  Scenario: Quantity must be > 0
    Given the RecordEquityHoldingDrawer is open
    When I enter quantity = 0 and submit
    Then I see a validation error: "Quantidade deve ser maior que zero"
    And the mutation is NOT called

  Scenario: Unit price must be > 0
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I enter unit price = 0 and submit
    Then I see a validation error: "Preço unitário deve ser maior que zero"
    And the mutation is NOT called

  Scenario: Quantity on SALE cannot exceed current holding
    Given I hold 100 PETR4
    When I record a SALE of 101 shares
    Then the service returns an error: "Quantidade insuficiente"

  Scenario: Net value must be ≤ gross value
    Given the RecordEquityHoldingDrawer is open
    When I enter net value = 4000.00 and gross value = 3800.00
    Then the service returns an error: "Valor líquido não pode exceder o valor bruto"

  Scenario: Asset is required — cannot submit without selecting an asset
    Given the RecordEquityHoldingDrawer is open in INFLOW mode
    When I click "Registrar" without selecting an asset
    Then I see a validation error on the asset field
    And the mutation is NOT called

  Scenario: Only MANUAL transactions are editable
    Given I have an OPEN_FINANCE-sourced PURCHASE transaction
    When I try to edit the transaction
    Then the edit option is not available (read-only transaction)
```
