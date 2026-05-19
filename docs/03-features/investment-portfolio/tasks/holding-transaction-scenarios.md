---
title: Holding Transaction — Scenario Matrix
type: test-reference
category: investment-portfolio
status: current
spec: 03-features/investment-portfolio/holding-transaction-editor-refactor.md
last_updated: 2026-05-12
---

# Holding Transaction — Scenario Matrix

Use this table to map all create / edit / delete scenarios when writing tests or doing manual QA. Every scenario maps to an intent and a UX flow.

**Editor routing (web):** For `new-buy` and `new-sell` with asset class **funds**, **stocks**, **reits**, or **crypto**, the UI uses `NewHoldingFlowShell` in `transaction-editor/` (custodian account, then step 1 asset gate, then step 2 values with a read-only asset summary and **Alterar** to return to step 1). **Fixed income** still opens `RecordFixedIncomeDrawer` directly. **row-** and **edit** intents skip the shell because the asset is already known.

---

## Legend

| Column | Meaning |
|---|---|
| **Entry point** | Where the user starts |
| **Flow** | Steps the user takes |
| **Intent** | `TransactionEditorIntent` kind passed to the shell |
| **Editor step** | Which step `TransactionEditor` opens at |

---

## Fixed Income (`fixed-income`)

| Operation | Entry point | Flow | Intent | Editor step |
|---|---|---|---|---|
| **Create new holding** | Grid card "Aplicar" | Click card button → `TransactionEditor` opens | `new-buy` | Step 2 directly (no Step 1 for Fixed Income) |
| **Create new holding** | Table header "+ Investimento" | Click button → asset class popover → select "Renda Fixa" → `TransactionEditor` opens | `new-buy` | Step 2 directly |
| **Add transaction to existing holding** | Holding row hover "+" | Hover over row → click "+" → `TransactionEditor` opens | `row-buy` | Step 2 directly |
| **Add transaction to existing holding** | Detail drawer → Movimentações tab → "+" | Open detail drawer → click "Movimentações" tab → click "+" button | `row-buy` | Step 2 directly |
| **Edit transaction** | Detail drawer → Movimentações tab → edit icon | Open detail drawer → "Movimentações" tab → click edit icon on row | `edit` | Step 2 directly |
| **Delete transaction** | Detail drawer → Movimentações tab → delete icon | Open detail drawer → "Movimentações" tab → click delete icon → confirm dialog | — | — (mutation only) |

**Notes**:
- No row hover "−" for fixed income sell — redemptions are always partial or full on an existing position; use the `+` button in the Movimentações tab and set direction to "Resgatar"
- No Step 1: issuer and asset type are form fields in Step 2
- "Mais detalhes" toggle exposes IR withheld + IOF (Section C)

---

## Funds (`funds`)

| Operation | Entry point | Flow | Intent | Editor step |
|---|---|---|---|---|
| **Create new holding** | Grid card "Aplicar" | Click card button → `TransactionEditor` opens at Step 1 (fund search) | `new-buy` | Step 1 → Step 2 |
| **Create new holding** | Table header "+ Investimento" | Click button → popover → "Fundos" → `TransactionEditor` Step 1 | `new-buy` | Step 1 → Step 2 |
| **Create new holding (unlisted fund)** | Grid card "Aplicar" | Step 1 → search returns no results → "Registrar fundo manualmente" → inline sub-form → confirm → Step 2 with MANUAL pricing | `new-buy` | Step 1 (manual path) → Step 2 |
| **Add transaction to existing holding** | Holding row hover "+" | Hover → click "+" | `row-buy` | Step 2 directly |
| **Add transaction to existing holding** | Detail drawer → Movimentações tab → "+" | Open detail → "Movimentações" → "+" | `row-buy` | Step 2 directly |
| **Edit transaction** | Detail drawer → Movimentações tab → edit icon | Open detail → "Movimentações" → edit icon | `edit` | Step 2 directly |
| **Delete transaction** | Detail drawer → Movimentações tab → delete icon | Open detail → "Movimentações" → delete → confirm | — | — |

**Notes**:
- Step 1 shows CVM-indexed funds and previously created manual funds in separate sections
- For unlisted funds: `selectedAsset.source === 'manual'` → pricing mode locked to MANUAL in Step 2
- Direction toggle shown in Step 2 (Aportar / Resgatar)
- No cost fields (Section C absent for funds)

---

## Stocks (`stocks`)

| Operation | Entry point | Flow | Intent | Editor step |
|---|---|---|---|---|
| **Create new holding** | Grid card "Comprar" | Click → `TransactionEditor` Step 1 (B3 stock search) → select → Step 2 | `new-buy` | Step 1 → Step 2 |
| **Create new holding** | Table header "+ Investimento" | Popover → "Ações" → same Step 1 | `new-buy` | Step 1 → Step 2 |
| **Add buy to existing holding** | Holding row hover "+" | Hover → "+" | `row-buy` | Step 2 directly |
| **Add sell to existing holding** | Holding row hover "−" | Hover → "−" | `row-sell` (`maxQty` = current qty) | Step 2 directly |
| **Add transaction** | Detail drawer → Movimentações tab → "+" | Open detail → "Movimentações" → "+" | `row-buy` | Step 2 directly |
| **Edit transaction** | Detail drawer → Movimentações tab → edit icon | Open detail → "Movimentações" → edit | `edit` | Step 2 directly |
| **Delete transaction** | Detail drawer → Movimentações tab → delete icon | Open detail → "Movimentações" → delete → confirm | — | — |

**Notes**:
- Step 1 search filtered to: `STOCK`, `STOCK_RIGHTS`, `BDR`, `ETF`, `ETF_INTERNATIONAL`
- Step 2 fields: direction, date, quantity, unit price, price factor, settlement date (D+2 default)
- "Mais detalhes" toggle exposes costs: corretagem, emolumentos, IRRF

---

## FIIs / Fundos Imobiliários (`reits`)

| Operation | Entry point | Flow | Intent | Editor step |
|---|---|---|---|---|
| **Create new holding** | Grid card "Comprar" | Click → Step 1 (B3 FII search) → select → Step 2 | `new-buy` | Step 1 → Step 2 |
| **Create new holding** | Table header "+ Investimento" | Popover → "FIIs" → same Step 1 | `new-buy` | Step 1 → Step 2 |
| **Add buy to existing holding** | Holding row hover "+" | Hover → "+" | `row-buy` | Step 2 directly |
| **Add sell to existing holding** | Holding row hover "−" | Hover → "−" | `row-sell` (`maxQty` = current qty) | Step 2 directly |
| **Add transaction** | Detail drawer → Movimentações tab → "+" | Open detail → "Movimentações" → "+" | `row-buy` | Step 2 directly |
| **Edit transaction** | Detail drawer → Movimentações tab → edit icon | Open detail → "Movimentações" → edit | `edit` | Step 2 directly |
| **Delete transaction** | Detail drawer → Movimentações tab → delete icon | Open detail → "Movimentações" → delete → confirm | — | — |

**Notes**:
- Step 1 search filtered to: `REAL_ESTATE_FUND`, `AGRIBUSINESS_FUND`
- Shares `EquityFormFields` and `EquityCostFields` with Stocks
- Same cost structure as Stocks (corretagem, emolumentos, IRRF)

---

## Crypto (`crypto`)

| Operation | Entry point | Flow | Intent | Editor step |
|---|---|---|---|---|
| **Create new holding** | Grid card "Comprar" | Click → `CryptoStep1` (free-text: name + ticker + network) → confirm → Step 2 | `new-buy` | Step 1 (free-text form) → Step 2 |
| **Create new holding** | Table header "+ Investimento" | Popover → "Cripto" → same `CryptoStep1` | `new-buy` | Step 1 → Step 2 |
| **Add buy to existing holding** | Holding row hover "+" | Hover → "+" | `row-buy` | Step 2 directly |
| **Add sell to existing holding** | Holding row hover "−" | Hover → "−" | `row-sell` (`maxQty` = current qty) | Step 2 directly |
| **Add transaction** | Detail drawer → Movimentações tab → "+" | Open detail → "Movimentações" → "+" | `row-buy` | Step 2 directly |
| **Edit transaction** | Detail drawer → Movimentações tab → edit icon | Open detail → "Movimentações" → edit | `edit` | Step 2 directly |
| **Delete transaction** | Detail drawer → Movimentações tab → delete icon | Open detail → "Movimentações" → delete → confirm | — | — |

**Notes**:
- `CryptoStep1` is a free-text form — no search query, no B3 lookup
- `source: 'manual'` on all new Crypto assets
- No cost fields (Section C absent for crypto)
- No settlement date, no price factor

---

## Cross-cutting scenarios

| Scenario | Expected behaviour |
|---|---|
| **"Alterar" asset in new transaction** | Step 2 → click "Alterar" in identity block → back to Step 1 → form data discarded → pick new asset → Step 2 fresh |
| **Cancel mid-transaction** | Any step → close drawer → no mutation fired → holding data unchanged |
| **TransactionEditor + detail drawer open simultaneously** | Both visible; editor in front; closing editor returns focus to Movimentações tab |
| **Submit success** | Toast appears; `InvestmentHoldings` + `InvestmentCashBalance` + `InvestmentPortfolioSummary` queries refetch; Movimentações tab shows updated list |
| **Submit error (ApolloError)** | `ErrorAlert` shown inside `TransactionEditor`; no toast; user can correct and retry |
| **Delete + refetch** | `DELETE_INVESTMENT_TRANSACTION` fires; same three queries refetch; deleted row disappears from Movimentações tab |
| **`row-sell` quantity cap** | Quantity field in Step 2 has `max = intent.maxQty`; entering more shows validation error |
| **Unlisted fund pricing** | `source: 'manual'` → pricing mode locked to MANUAL in Step 2; MARKET option not rendered |
| **`edit` intent pre-fill** | All fields pre-populated from existing transaction; direction toggle shown and editable; CTA = "Salvar alterações" |
| **Fixed Income — no Step 1** | `TransactionEditor` opens directly at Step 2 form for all Fixed Income intents; no asset search screen |
| **Hover buttons — mobile / touch** | Row hover buttons visible on touch (not hover-only); accessible via keyboard focus |

---

## Entry point summary by asset class

| Class | New holding entry points | Add to existing (buy) | Add to existing (sell) | Edit | Delete |
|---|---|---|---|---|---|
| Fixed Income | Grid "Aplicar", Table "+" | Row hover "+", Movimentações "+" | Movimentações "+" (set direction Resgatar) | Movimentações edit icon | Movimentações delete icon |
| Funds | Grid "Aplicar", Table "+" | Row hover "+", Movimentações "+" | Movimentações "+" (set direction Resgatar) | Movimentações edit icon | Movimentações delete icon |
| Stocks | Grid "Comprar", Table "+" | Row hover "+" | Row hover "−" | Movimentações edit icon | Movimentações delete icon |
| FIIs | Grid "Comprar", Table "+" | Row hover "+" | Row hover "−" | Movimentações edit icon | Movimentações delete icon |
| Crypto | Grid "Comprar", Table "+" | Row hover "+" | Row hover "−" | Movimentações edit icon | Movimentações delete icon |
