# Test Scenarios: Portfolio Page

**Requirements ref:** `02-portfolio-page.md`
**Document date:** 2026-04-20

---

```gherkin
Feature: Portfolio Page Layout and Navigation
  As a user
  I want the investments page to show my portfolio organized by account and asset class
  So that I can understand my overall financial position at a glance

  Background:
    Given I am authenticated
    And I am on the /personal-finance/investments page

  # ─── EMPTY STATES ─────────────────────────────────────────────────────────

  Scenario: No investment accounts — full-page empty state
    Given I have no investment accounts
    Then the sidebar is NOT visible
    And the main content shows a full-page empty state
    And I see a prominent "Criar conta de investimento" CTA button
    And InvestmentSummary is NOT displayed

  Scenario: Account exists but has no holdings — account-scoped empty state
    Given I have one investment account "XP Corretora" with no holdings
    When I select "XP Corretora" in the sidebar
    Then the InvestmentGrid shows a scoped empty state: "Nenhum ativo registrado"
    And I see a "Registrar ativo" CTA button
    And InvestmentSummary shows zero values

  Scenario: Search returns no matching holdings
    Given I have holdings in the selected account
    When I type a search query that matches no asset name or ticker
    Then the InvestmentGrid shows "Nenhum ativo encontrado"
    And the search query is visible in the empty state message

  # ─── PAGE LAYOUT ──────────────────────────────────────────────────────────

  Scenario: Page renders sidebar + summary + grid when accounts and holdings exist
    Given I have investment accounts with holdings
    When the page loads
    Then I see the sidebar on the left
    And I see InvestmentSummary panel above the grid
    And I see AssetGroupCard sections in the grid for each asset class with holdings

  Scenario: First account is selected by default on page load
    Given I have two accounts: "XP Corretora" (created first) and "BTG Pactual"
    When the investments page loads
    Then "XP Corretora" is selected in the sidebar
    And InvestmentSummary shows totals for XP Corretora
    And the grid shows holdings from XP Corretora


Feature: Account Selector (Sidebar)
  As a user
  I want to select which account to view or use the consolidated view
  So that I can scope the portfolio display to a single broker or all accounts

  Background:
    Given I am authenticated
    And I have accounts "XP Corretora", "BTG Pactual", and a BENEFIT account "FGTS"

  Scenario: Sidebar lists all INVESTMENT and BENEFIT accounts
    When the investments page loads
    Then the sidebar shows all three accounts:
      | XP Corretora   |
      | BTG Pactual    |
      | FGTS           |
    And I also see a "Todas as contas" consolidated option

  Scenario: Selecting a single account scopes summary and grid
    When I click "XP Corretora" in the sidebar
    Then InvestmentSummary calls investmentPortfolioSummary(financialAccountId: <XP id>)
    And InvestmentGrid shows only XP Corretora holdings

  Scenario: Selecting "Todas as contas" shows consolidated view
    When I click "Todas as contas" in the sidebar
    Then InvestmentSummary calls investmentPortfolioSummary(financialAccountId: null)
    And InvestmentGrid shows holdings from all accounts combined

  Scenario: "+ Criar conta" button in sidebar opens CreateInvestmentAccountDrawer
    When I click "+ Criar conta" in the sidebar
    Then CreateInvestmentAccountDrawer opens

  Scenario: Bank accounts are NOT shown in the sidebar
    Given I also have a BANK account "Nubank Conta Corrente"
    When the investments page loads
    Then "Nubank Conta Corrente" does NOT appear in the sidebar


Feature: InvestmentSummary Panel
  As a user
  I want to see portfolio-level metrics at the top of the page
  So that I can quickly assess my total portfolio value, gains, and allocation

  Background:
    Given I am authenticated
    And I have holdings with known values

  Scenario: Summary shows correct metrics for a single account
    Given I have one holding: 100 PETR4 at averageCost=38.50, grossValue=4200 (market)
    And cashBalance = 5000.00 for "XP Corretora"
    When I select "XP Corretora" in the sidebar
    Then InvestmentSummary shows:
      | totalPortfolioValue  | R$ 9200.00 (4200 + 5000)              |
      | totalCostBasis       | R$ 3850.00 (100 × 38.50)              |
      | grossGain            | R$ 350.00 (4200 − 3850)               |
      | cashBalance          | R$ 5000.00                            |

  Scenario: Summary shows loading skeleton while query is in flight
    When the investmentPortfolioSummary query is loading
    Then InvestmentSummary shows skeleton placeholders
    And no actual values are displayed

  Scenario: Summary shows error state when query fails
    Given the investmentPortfolioSummary query returns an error
    Then InvestmentSummary shows an error state with a retry option

  Scenario: "Estimado" warning chip appears when any holding uses ESTIMATED or UNAVAILABLE valuation
    Given I have a holding with valuationSource = ESTIMATED
    When the summary loads
    Then a warning chip "estimado" appears next to the total portfolio value
    And a tooltip explains the reference date of the latest estimate

  Scenario: Allocation breakdown shows groups with holdings
    Given I have holdings in Renda Fixa and Cripto groups only
    When InvestmentSummary loads
    Then the allocation breakdown shows:
      | Renda Fixa  | (correct % and value) |
      | Cripto      | (correct % and value) |
    And groups with no holdings are NOT shown (or have 0% allocation)

  Scenario: Cash group appears in allocation when account has a positive cash balance
    Given the account has cashBalance = R$ 5000.00
    When the allocation breakdown is computed
    Then a "Caixa" group appears in the allocation showing the cash percentage

  Scenario: Consolidated summary aggregates all accounts
    Given I have two accounts each with different holdings
    When I select "Todas as contas"
    Then the summary totals are the sum of both accounts' values


Feature: InvestmentGrid — Asset Group Cards
  As a user
  I want to see my holdings organized by asset class
  So that I can quickly navigate to a specific investment category

  Background:
    Given I am authenticated
    And I have holdings across multiple asset classes

  Scenario: Each asset class with holdings shows a corresponding AssetGroupCard
    Given I have holdings in Renda Fixa, Renda Variável, and Cripto
    When the InvestmentGrid loads
    Then I see three AssetGroupCard sections:
      | Renda Fixa       |
      | Renda Variável   |
      | Cripto           |
    And asset classes without holdings are NOT shown

  Scenario: Asset group cards are ordered in canonical order
    Given I have holdings in Cripto and Renda Fixa
    When the InvestmentGrid loads
    Then Renda Fixa appears before Cripto in the canonical group order

  Scenario: Each AssetGroupCard shows the group total value
    Given a Renda Fixa group with two holdings worth R$ 10000 and R$ 5000
    When the AssetGroupCard for Renda Fixa renders
    Then the header shows total value = R$ 15000

  Scenario: Holding row in a group shows core columns
    Given I have a CDB holding in Renda Fixa group
    Then the holding row shows: asset name, type, current value, and maturity date (for fixed income)

  Scenario: Holding row click opens InvestmentHoldingDetailDrawer
    When I click a holding row
    Then InvestmentHoldingDetailDrawer opens for that holding

  Scenario: Context menu on holding row shows available operations
    When I open the context menu on a STOCK holding
    Then I see options: "Comprar mais", "Vender", "Dividendos", "JCP", "Desdobramento", "Aluguel", "Transferir custódia"

  Scenario: Context menu shows only applicable operations for the asset class
    When I open the context menu on a CRYPTO holding
    Then I see "Comprar mais", "Vender", "Transferir custódia"
    And I do NOT see "Aluguel de ações" (not available for CRYPTO)
    And I do NOT see "Dividendos" (not applicable for CRYPTO)

  Scenario: "Registrar ▼" button opens InvestmentEventSelectorDrawer
    When I click the "Registrar ▼" button in the page toolbar
    Then InvestmentEventSelectorDrawer opens
    And it shows operation types available for the selected account's asset classes

  Scenario: "Registrar ▼" button is disabled or hidden in consolidated view (no account selected)
    When I am in the "Todas as contas" consolidated view
    Then the "Registrar ▼" button is disabled
    And clicking it does not open any drawer


Feature: Holding Detail Drawer
  As a user
  I want to view detailed information about a specific holding
  So that I can understand its terms, performance, and transaction history

  Background:
    Given I am authenticated
    And I have holdings in my selected account

  Scenario: Fixed income holding detail shows contractual terms and coupon schedule
    Given I have a Tesouro IPCA+ holding with couponFrequency = SEMI_ANNUAL
    When I open the holding detail drawer
    Then I see FixedIncomeHoldingDetailContent with:
      | Indexer, rate, maturity date  |
      | Coupon schedule timeline      |
      | Transaction history timeline  |
      | Performance chart             |

  Scenario: Fund holding detail shows fund metadata and pricing mode
    Given I have a MUTUAL_FUND holding with pricingMode = MARKET_DATA
    When I open the holding detail drawer
    Then I see FundHoldingDetailContent with:
      | Fund name, CNPJ, anbimaCategory |
      | Latest VNA and reference date   |
      | Transaction history             |

  Scenario: Holding detail shows full transaction history
    Given I have a holding with 3 transactions (PURCHASE, INTEREST_PAYMENT, SALE)
    When I open the holding detail drawer
    Then the transaction timeline shows all 3 events in chronological order


Feature: Pending Actions
  As a user
  I want to be notified of upcoming or overdue investment events
  So that I can confirm maturities, coupon payments, and other time-sensitive actions

  Background:
    Given I am authenticated

  Scenario: Pending actions badge shows count when actions are available
    Given I have 3 visible pending actions (1 INVESTMENT_MATURED + 2 COUPON_PAYMENT_DUE)
    When the investments page loads
    Then the "Ações Pendentes" button shows a badge with count = 3

  Scenario: Clicking "Ações Pendentes" opens PendingActionsDrawer
    When I click "Ações Pendentes"
    Then PendingActionsDrawer opens
    And it lists all visible pending actions for the user

  Scenario: URL param ?pendingActions=1 opens PendingActionsDrawer on load
    When I navigate to /personal-finance/investments?pendingActions=1
    Then PendingActionsDrawer opens automatically on page load

  Scenario: Clicking a INVESTMENT_MATURED action opens MaturityConfirmDrawer
    Given the PendingActionsDrawer is open
    And there is an INVESTMENT_MATURED action for "CDB Banco Inter"
    When I click that action
    Then MaturityConfirmDrawer opens pre-filled with the holding's maturity date

  Scenario: Clicking a COUPON_PAYMENT_DUE action opens CouponPaymentConfirmDrawer
    Given the PendingActionsDrawer is open
    And there is a COUPON_PAYMENT_DUE action for "Tesouro IPCA+ 2035"
    When I click that action
    Then CouponPaymentConfirmDrawer opens pre-filled with the next coupon date

  Scenario: Pending actions show only after their visibleFrom date
    Given an INVESTMENT_MATURED action with visibleFrom = tomorrow
    When the investments page loads today
    Then that action does NOT appear in the pending actions list


Feature: Holdings Search
  As a user
  I want to search my holdings by name or ticker
  So that I can quickly find a specific investment in a large portfolio

  Background:
    Given I am authenticated
    And I have holdings: "CDB Banco Inter 115% CDI", "PETR4", "MXRF11", "Bitcoin"

  Scenario: Search filters holdings by asset name
    When I type "banco" in the search field
    Then only "CDB Banco Inter 115% CDI" remains visible
    And PETR4, MXRF11, and Bitcoin are hidden

  Scenario: Search filters holdings by ticker
    When I type "PETR" in the search field
    Then only "PETR4" remains visible

  Scenario: Search is case-insensitive
    When I type "bitcoin" (lowercase)
    Then the "Bitcoin" holding is shown

  Scenario: Clearing the search field restores all holdings
    Given I have filtered holdings by typing "CDB"
    When I clear the search field
    Then all holdings are visible again

  Scenario: Search applies across all asset group cards simultaneously
    Given I have holdings in Renda Fixa, Renda Variável, and Cripto groups
    When I search for a term that matches one holding in Renda Variável
    Then only the matching holding is shown in Renda Variável
    And Renda Fixa and Cripto groups appear empty or are hidden
```
