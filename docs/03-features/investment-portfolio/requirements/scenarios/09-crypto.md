# Test Scenarios: Criptomoedas

**Requirements ref:** `09-crypto.md`
**Document date:** 2026-04-20

---

```gherkin
Feature: Compra de Criptomoeda (PURCHASE)
  As a user
  I want to manually record a cryptocurrency purchase
  So that my portfolio tracks my crypto holdings and cost basis

  Background:
    Given I am authenticated
    And I am on the /personal-finance/investments page
    And I have at least one CRYPTO (exchange) investment account selected

  # ─── HAPPY PATHS ──────────────────────────────────────────────────────────

  Scenario: Buy Bitcoin — first purchase creates a new holding
    Given the RecordEquityHoldingDrawer is open in INFLOW mode with variant="crypto"
    When I fill in:
      | Asset name   | Bitcoin      |
      | Ticker       | BTC          |
      | Trade date   | 2026-04-01   |
      | Quantity     | 0.05         |
      | Unit price   | 350000.00    |
      | Net value    | 17500.00     |
    And I click "Registrar"
    Then createInvestmentAsset is called with:
      | name       | Bitcoin  |
      | ticker     | BTC      |
      | assetType  | CRYPTO   |
    Then recordInvestmentTransaction is called with:
      | direction       | INFLOW       |
      | type            | PURCHASE     |
      | quantity        | 0.05         |
      | unitPrice       | 350000.00    |
      | grossValue      | 17500.00     |
      | netValue        | 17500.00     |
      | tradeDate       | 2026-04-01   |
      | settlementDate  | 2026-04-01   |
      | source          | MANUAL       |
    And a DEBIT of R$ 17500.00 is created on the CRYPTO custodian account
    And a new BTC holding is created with:
      | quantity     | 0.05      |
      | averageCost  | 350000.00 |

  Scenario: Buy more BTC on the same exchange — weighted average cost update
    Given I already hold 0.05 BTC at averageCost = R$ 350000.00 on Binance
    When I record a second purchase of 0.02 BTC at R$ 380000.00
    Then the BTC holding on Binance is updated:
      | quantity     | 0.07                                                      |
      | averageCost  | (0.05 × 350000 + 0.02 × 380000) / 0.07 = R$ 358571.43   |

  Scenario: Buy Ethereum with optional network field
    Given the RecordEquityHoldingDrawer is open in INFLOW mode with variant="crypto"
    When I fill in:
      | Asset name  | Ethereum         |
      | Ticker      | ETH              |
      | Network     | Ethereum         |
      | Quantity    | 1.5              |
      | Unit price  | 20000.00         |
      | Net value   | 30000.00         |
    And I click "Registrar"
    Then the asset is created with the network field stored
    And recordInvestmentTransaction succeeds

  Scenario: Buy SOL — fractional quantity with high precision
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    When I enter quantity = 0.00432
    Then the form accepts the fractional value
    And recordInvestmentTransaction is called with quantity = 0.00432

  Scenario: Exchange identity is determined by the custody account (not the asset)
    Given I have two CRYPTO accounts: "Binance" and "Coinbase"
    When I record a BTC purchase on the "Binance" account
    And I record another BTC purchase on the "Coinbase" account
    Then two separate BTC holdings are created:
      | Account  | Asset |
      | Binance  | BTC   |
      | Coinbase | BTC   |
    And each holding has its own averageCost and quantity

  Scenario: Reuse existing holding when buying more of the same ticker on the same account
    Given I already hold 0.05 BTC on Binance
    When I record another BTC purchase on Binance
    Then the SAME BTC holding on Binance is updated (no new holding created)

  # ─── FORM VARIANT RULES ────────────────────────────────────────────────────

  Scenario: Crypto variant hides broker note / nota de corretagem fields
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    Then I do NOT see the following fields:
      | Brokerage fee              |
      | Clearing settlement fee    |
      | Clearing registration fee  |
      | Stock exchange fee         |
      | Broker note number         |

  Scenario: Crypto variant hides B3 autocomplete — shows manual asset name/ticker input
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    Then I do NOT see the B3 EquityAutocomplete dropdown
    And I see a free-text "Asset name" input field
    And I see a free-text "Ticker" input field

  Scenario: settlementDate defaults to tradeDate (D+0) for crypto — no settlement cycle
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    When I select trade date = 2026-04-01
    Then the settlement date is automatically set to 2026-04-01 (same day)
    And the settlement date field is not shown to the user


Feature: Venda de Criptomoeda (SALE)
  As a user
  I want to record the sale of cryptocurrency
  So that the proceeds are credited and the holding quantity decreases

  Background:
    Given I am authenticated
    And I hold 0.07 BTC at averageCost = R$ 358571.43 on Binance

  Scenario: Sell 0.03 BTC — partial position
    Given the RecordEquityHoldingDrawer is open in OUTFLOW mode with variant="crypto"
    When I fill in:
      | Trade date   | 2026-05-01   |
      | Quantity     | 0.03         |
      | Unit price   | 400000.00    |
      | Net value    | 12000.00     |
    And I click "Vender"
    Then recordInvestmentTransaction is called with:
      | direction    | OUTFLOW       |
      | type         | SALE          |
      | quantity     | 0.03          |
      | netValue     | 12000.00      |
    And a CREDIT of R$ 12000.00 is created on the Binance CRYPTO account
    And the BTC holding quantity decreases to 0.04
    And the averageCost remains R$ 358571.43 (SALE does not change averageCost)

  Scenario: Sell with income tax (IR on capital gain)
    Given the RecordEquityHoldingDrawer is open in OUTFLOW mode with variant="crypto"
    When I fill in gross value = 12000.00, net value = 10800.00, income tax = 1200.00
    Then recordInvestmentTransaction is called with:
      | grossValue  | 12000.00  |
      | netValue    | 10800.00  |
      | incomeTax   | 1200.00   |

  Scenario: averageCost is not changed by a SALE
    Given I hold 0.07 BTC at averageCost = R$ 358571.43
    When I sell 0.03 BTC
    Then the remaining 0.04 BTC still have averageCost = R$ 358571.43

  Scenario: SALE for full quantity closes the position
    Given I hold 0.07 BTC
    When I sell 0.07 BTC
    Then the BTC holding quantity becomes 0
    And the holding record is preserved (not deleted)


Feature: Transferência de Custódia — Cripto (CUSTODY_TRANSFER)
  As a user
  I want to transfer crypto holdings between exchange accounts
  So that I can move assets between exchanges while preserving cost basis

  Background:
    Given I am authenticated
    And I hold 0.07 BTC at averageCost = R$ 358571.43 on "Binance"
    And I have a second CRYPTO account "Coinbase"

  Scenario: Transfer full BTC position from Binance to Coinbase
    Given the TransferCustodyDrawer is open for the BTC holding on Binance
    When I select "Coinbase" as the destination account
    And I leave quantity blank (full transfer)
    And I click "Transferir"
    Then TransferCustodyService records:
      | CUSTODY_TRANSFER OUT on Binance | quantity = 0.07, transferValue = 0.07 × 358571.43 |
      | CUSTODY_TRANSFER IN on Coinbase | quantity = 0.07, averageCost = 358571.43           |
    And the Binance BTC holding quantity becomes 0
    And a new BTC holding is created at Coinbase with:
      | quantity     | 0.07       |
      | averageCost  | 358571.43  |

  Scenario: Partial transfer of crypto holdings
    Given I hold 0.07 BTC on Binance
    When I transfer 0.04 BTC to Coinbase
    Then the Binance holding quantity decreases to 0.03
    And the Coinbase holding is created with quantity = 0.04

  Scenario: averageCost is preserved during crypto custody transfer
    Given the source Binance BTC holding has averageCost = R$ 358571.43
    When I transfer to Coinbase
    Then the Coinbase BTC holding receives averageCost = R$ 358571.43 (preserved)

  Scenario: CUSTODY_TRANSFER type is used for crypto (not QUOTA_TRANSFER)
    Given I am transferring a CRYPTO holding
    When TransferCustodyService processes the request
    Then the transaction type is CUSTODY_TRANSFER


Feature: Crypto Valuation
  As a user
  I want to see my crypto holdings valued at current market prices
  So that I can monitor portfolio performance in real time

  Background:
    Given I am authenticated
    And I hold BTC, ETH, and SOL

  Scenario: Holdings are valued using CoinGecko prices (MARKET_DATA)
    When the portfolio page loads
    Then each crypto holding shows:
      | grossValue      = quantity × latestPrice |
      | valuationSource = MARKET_DATA             |

  Scenario: When CoinGecko price is unavailable, cost basis is used as fallback
    Given the financial-data service cannot return a price for a small-cap token
    When the portfolio page loads
    Then that holding shows:
      | grossValue      = quantity × averageCost |
      | valuationSource = UNAVAILABLE             |

  Scenario: Multiple holdings of the same coin on different exchanges are shown separately
    Given I hold BTC on both Binance and Coinbase
    When the portfolio page loads
    Then both BTC holdings appear as separate rows
    And each shows its own quantity, averageCost, and current grossValue


Feature: Crypto — Validation Rules
  As a user
  I should receive clear errors when I provide invalid crypto trade data

  Background:
    Given I am authenticated
    And I have a CRYPTO account selected

  Scenario: Asset name is required
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    When I leave the asset name blank and click "Registrar"
    Then I see a validation error: "Nome do ativo é obrigatório"
    And the mutation is NOT called

  Scenario: Ticker is required
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    When I leave the ticker blank and click "Registrar"
    Then I see a validation error: "Ticker é obrigatório"
    And the mutation is NOT called

  Scenario: Quantity must be > 0
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    When I enter quantity = 0 and click "Registrar"
    Then I see a validation error: "Quantidade deve ser maior que zero"
    And the mutation is NOT called

  Scenario: Unit price must be > 0
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    When I enter unit price = 0 and click "Registrar"
    Then I see a validation error
    And the mutation is NOT called

  Scenario: Net value must be > 0
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    When I enter net value = 0 and click "Registrar"
    Then I see a validation error
    And the mutation is NOT called

  Scenario: Quantity on SALE cannot exceed current holding quantity
    Given I hold 0.07 BTC on Binance
    When I try to record a SALE of 0.10 BTC
    Then the service returns an error: "Quantidade insuficiente"

  Scenario: Stock lending is not available for CRYPTO assets
    Given I have a Bitcoin holding
    When I try to access the stock lending option
    Then the option is NOT available (CRYPTO is not in LENDABLE_ASSET_TYPES)
    And the backend returns an error if attempted via API

  Scenario: Fractional quantities with many decimal places are accepted
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    When I enter quantity = 0.00001234
    Then the form accepts the value
    And recordInvestmentTransaction is called with the exact fractional value

  Scenario: Network field is optional — form is valid without it
    Given the RecordEquityHoldingDrawer is open with variant="crypto"
    When I fill in all required fields but leave network blank
    And I click "Registrar"
    Then the form submits successfully
    And the asset is created without a network value
```
