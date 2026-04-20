# Test Scenarios: Investment Accounts

**Requirements ref:** `01-investment-accounts.md`
**Document date:** 2026-04-20

---

```gherkin
Feature: Create Investment Account
  As a user
  I want to create an investment custody account
  So that I can track my investment portfolio in a specific brokerage, bank, or exchange

  Background:
    Given I am authenticated
    And I am on the /personal-finance/investments page

  # ─── EMPTY STATE ──────────────────────────────────────────────────────────

  Scenario: Empty state CTA is visible when user has no investment accounts
    Given I have no investment accounts
    Then I see a full-page empty state
    And I see a prominent "Criar conta de investimento" button
    And the account sidebar is NOT visible

  Scenario: Open the account creation drawer from the empty state CTA
    Given I have no investment accounts
    When I click "Criar conta de investimento"
    Then a drawer opens on the right side
    And the form displays immediately — no type selection step
    And I do NOT see a bank type selector (Conta Corrente, Cartão de Crédito, etc.)

  # ─── HAPPY PATHS ──────────────────────────────────────────────────────────

  Scenario: Create a brokerage account with a known issuer — name defaults to issuer
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I select issuer "XP Investimentos" from the autocomplete
    And I leave the account name blank
    And I click "Criar"
    Then the mutation createFinancialAccount is called with:
      | type     | INVESTMENT |
      | subtype  | BROKERAGE  |
      | issuerId | <XP UUID>  |
      | name     | (omitted)  |
    And the drawer closes
    And the new account "XP Investimentos" appears in the account sidebar
    And the page refreshes showing the account

  Scenario: Create a brokerage account with an explicit custom name
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I select issuer "XP Investimentos" from the autocomplete
    And I type "Minha Carteira Principal" in the account name field
    And I click "Criar"
    Then the mutation createFinancialAccount is called with:
      | type     | INVESTMENT              |
      | subtype  | BROKERAGE               |
      | issuerId | <XP UUID>               |
      | name     | Minha Carteira Principal|
    And the new account appears in the sidebar with name "Minha Carteira Principal"

  Scenario: Create a brokerage account with a custom (unknown) institution
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I select "Outro / Personalizado" in the issuer field
    And I type "Modal Mais" in the institution name field
    And I click "Criar"
    Then the mutation createFinancialAccount is called with:
      | type       | INVESTMENT |
      | subtype    | BROKERAGE  |
      | issuerId   | (omitted)  |
      | issuerName | Modal Mais |
    And the new account appears in the sidebar with name "Modal Mais"

  Scenario: Create a fixed income bank account
    Given the investment account drawer is open
    When I select subtype "Renda Fixa"
    And I select issuer "Banco Inter" from the autocomplete
    And I click "Criar"
    Then the mutation createFinancialAccount is called with:
      | type    | INVESTMENT   |
      | subtype | FIXED_INCOME |
    And the drawer closes successfully

  Scenario: Create a pension (previdência) account
    Given the investment account drawer is open
    When I select subtype "Previdência Privada"
    And I select issuer "Bradesco Vida e Previdência" from the autocomplete
    And I click "Criar"
    Then the mutation createFinancialAccount is called with:
      | type    | INVESTMENT |
      | subtype | PENSION    |
    And the drawer closes successfully

  Scenario: Create a crypto exchange account
    Given the investment account drawer is open
    When I select subtype "Criptomoedas"
    And I select issuer "Binance" from the autocomplete
    And I click "Criar"
    Then the mutation createFinancialAccount is called with:
      | type    | INVESTMENT |
      | subtype | CRYPTO     |
    And the drawer closes successfully

  Scenario: Create a second brokerage account when one already exists
    Given I already have one investment account "XP Investimentos"
    And I click the "+" or "Adicionar conta" button in the sidebar
    When the drawer opens
    And I select subtype "Corretora de Valores"
    And I select issuer "BTG Pactual" from the autocomplete
    And I click "Criar"
    Then both "XP Investimentos" and "BTG Pactual" appear in the sidebar

  # ─── ISSUER RESOLUTION RULES ──────────────────────────────────────────────

  Scenario: issuerId overrides any issuerName entered by the user
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I select issuer "XP Investimentos" (which sets issuerId)
    Then the mutation sends issuerId only — issuerName is NOT sent in the payload
    And the account name resolves to "XP Investimentos" from the issuer record

  Scenario: Custom name field appears only when "Outro / Personalizado" is selected
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I have not selected "Outro / Personalizado"
    Then the free-text institution name field is NOT visible
    When I select "Outro / Personalizado"
    Then the free-text institution name field becomes visible

  # ─── FORM FIELD PRESENCE RULES ────────────────────────────────────────────

  Scenario: Banking and card fields are never shown in the investment account drawer
    Given the investment account drawer is open
    Then I do NOT see any of the following fields:
      | Agência (branch code)       |
      | Número da conta             |
      | Bandeira do cartão          |
      | Limite de crédito           |
      | Dia de fechamento           |
      | Dia de vencimento           |
      | Saldo inicial               |

  Scenario: The mutation payload never includes bankData, cardData, or initialBalance
    Given the investment account drawer is open
    When I complete a valid form and submit
    Then the createFinancialAccount mutation payload does NOT include:
      | bankData        |
      | cardData        |
      | initialBalance  |

  Scenario: The mutation always sends type = INVESTMENT regardless of subtype
    Given the investment account drawer is open
    When I select any subtype (BROKERAGE, FIXED_INCOME, PENSION, or CRYPTO)
    And I complete the form and submit
    Then the mutation is called with type = INVESTMENT in all cases

  # ─── ACCOUNT SIDEBAR AND SELECTION ───────────────────────────────────────

  Scenario: Sidebar shows all INVESTMENT and BENEFIT accounts
    Given I have the following investment accounts:
      | Name          | Type       | Subtype      |
      | XP Corretora  | INVESTMENT | BROKERAGE    |
      | Banco Inter   | INVESTMENT | FIXED_INCOME |
      | Binance       | INVESTMENT | CRYPTO       |
    And I have a BENEFIT account "FGTS"
    When the investments page loads
    Then the sidebar lists all 4 accounts: XP Corretora, Banco Inter, Binance, FGTS
    And I also see a "Todas as contas" consolidated option

  Scenario: Selecting a single account scopes the portfolio view
    Given I have two accounts: "XP Corretora" and "BTG Pactual"
    When I click "XP Corretora" in the sidebar
    Then the holdings grid shows only holdings from XP Corretora
    And the portfolio summary shows totals for XP Corretora only

  Scenario: Consolidated view aggregates all accounts
    Given I have two accounts each with holdings
    When I select "Todas as contas" in the sidebar (selectedAccountId = null)
    Then the holdings grid shows holdings from all accounts
    And the portfolio summary shows combined totals

  Scenario: Newly created account starts with zero cash balance
    Given I have just created a new account "Rico Investimentos"
    When I select "Rico Investimentos" in the sidebar
    Then the cash balance displayed is R$ 0,00
    And no holdings are shown (empty holdings state)

  # ─── VALIDATION — NEGATIVE CASES ─────────────────────────────────────────

  Scenario: Subtype is required — validation error on submit
    Given the investment account drawer is open
    When I select issuer "XP Investimentos"
    And I click "Criar" without selecting a subtype
    Then a validation error appears: "Tipo de conta é obrigatório"
    And the mutation createFinancialAccount is NOT called

  Scenario: Issuer is required — validation error when both fields are blank
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I leave the issuer autocomplete blank
    And I do NOT select "Outro / Personalizado"
    And I click "Criar"
    Then a validation error appears: "Informe a corretora ou nome da instituição"
    And the mutation createFinancialAccount is NOT called

  Scenario: Custom institution name is required when "Outro / Personalizado" is selected
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I select "Outro / Personalizado" in the issuer field
    And I leave the institution name field blank
    And I click "Criar"
    Then a validation error appears on the institution name field
    And the mutation createFinancialAccount is NOT called

  Scenario: Empty form submission shows all required field errors
    Given the investment account drawer is open
    When I click "Criar" without filling in any field
    Then I see a validation error for subtype
    And I see a validation error for issuer/institution
    And the mutation createFinancialAccount is NOT called

  # ─── EDGE CASES ───────────────────────────────────────────────────────────

  Scenario: Account name with only whitespace is treated as blank (defaults to issuer name)
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I select issuer "XP Investimentos"
    And I type "   " (only spaces) in the account name field
    And I click "Criar"
    Then the mutation is called without a name field (or with name = undefined)
    And the account resolves to name "XP Investimentos" from the issuer record

  Scenario: Long institution name is stored verbatim
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I select "Outro / Personalizado"
    And I type a 100-character institution name
    And I click "Criar"
    Then the mutation is called with the full institution name
    And the account is created successfully

  Scenario: Mutation error shows an error banner — drawer stays open
    Given the investment account drawer is open
    And the createFinancialAccount mutation will return a server error
    When I fill in a valid form and click "Criar"
    Then an error alert is shown inside the drawer
    And the drawer remains open
    And the form fields retain their values

  Scenario: currencyCode defaults to BRL
    Given the investment account drawer is open
    When I complete a valid form and submit
    Then the createFinancialAccount mutation is called with:
      | currencyCode | BRL |

  Scenario: includeInNetWorth and includeInDashboard default to true
    Given the investment account drawer is open
    When I complete a valid form and submit
    Then the createFinancialAccount mutation is called with:
      | includeInNetWorth  | true |
      | includeInDashboard | true |

  Scenario: BENEFIT accounts are visible in the sidebar but not creatable via this drawer
    Given I have a BENEFIT account "FGTS" pre-existing in my profile
    When the investments page loads
    Then "FGTS" appears in the sidebar account list
    And I cannot create a BENEFIT account via "Criar conta de investimento"

  Scenario: Closing the drawer without submitting does not create an account
    Given the investment account drawer is open
    When I fill in a subtype and issuer
    And I click the "×" close button or press Escape
    Then the drawer closes
    And no new account is created
    And the createFinancialAccount mutation is NOT called
```
