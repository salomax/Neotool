---
title: Investment Portfolio — Retail Roadmap Session Notes (March 2026)
type: session-notes
category: investment-portfolio
status: reference
version: 1.0.0
tags: [roadmap, retail, b2c, investments, data-sources, mvp, ir, tax, open-finance]
last_updated: 2026-03-05
---

# Investment Portfolio — Retail Roadmap Session Notes

> Planning session held on 2026-03-05. Covers competitive benchmark, roadmap reprioritization, data source strategy, and open decisions for the Retail (B2C) investment feature.

---

## 1. Context

The team is currently building the **financial control feature set** (bank accounts, credit cards, income/expenses). The **investment portfolio** module is the next major feature to be planned and designed.

This session was focused on:
- Understanding what the market is doing (competitive benchmark)
- Defining which features to prioritize for MVP
- Deciding how investment accounts relate to regular banking accounts in the architecture
- Evaluating data sources for market prices and asset data to minimize costs

---

## 2. Competitive Benchmark Summary

### Brazilian Players

| Player | Core Focus | Key Differentiator |
|---|---|---|
| **Kinvo** | Multi-custodian consolidation | Sharpe ratio per asset; FGC coverage visualized on portfolio |
| **Gorila** | B2B/B2C consolidation | Personal API key for PRO users; white-label for brokerages; independent of custodians |
| **Grana Capital** | IR (Tax) automation | Only app to automate full IR cycle: calculation + DARF payment + annual IRPF filing |
| **Status Invest** | Fundamental analysis | 200+ indicators per asset; advanced FII and stock screener — largely free |
| **Meus Dividendos** | Dividends / passive income | Prospective dividend agenda; consolidated portfolio DY |
| **TradeMap** | Analysis + Order Execution | Only BR platform with multi-broker integrated order execution; full options module |
| **App B3** (Dec 2025) | Consolidation via CPF | New entrant by B3 itself; free; IR automation planned for 2027 |

### International Players (Reference)

| Player | Core Focus | Key Differentiator |
|---|---|---|
| **Empower (US)** | Net worth + retirement | Fee Analyzer (hidden fund costs); Monte Carlo retirement simulator (5k scenarios) |
| **Monarch Money (US)** | Budgeting + portfolio | Best app for couples/families; most balanced budgeting + investment tracking |
| **Copilot (US)** | Apple-ecosystem finance | Best UX/design; AI transaction categorization; hardware wallet support |
| **Wealthfront (US)** | Robo-advisor | Daily automated tax-loss harvesting; Direct Indexing for HNW |
| **YNAB (US)** | Zero-based budgeting | Proof that "do less, do it better" is a viable model; intentionally ignores investments |

### Key Market Gaps in Brazil (Opportunity)

1. **No app integrates expense control + investments** in a unified dashboard — Invistus already has the financial control module, which is a significant competitive advantage.
2. **No goal-based planning engine** — "How much should I invest monthly to retire with R$ 5k/month in 20 years?" — no Brazilian app does this well.
3. **Fee Analyzer** — no Brazilian app automatically shows how much the user pays in fund administration fees (Empower does this; it's transformational).
4. **Liquidity-aware net worth** — "I have R$ 103k in net worth, but only R$ 12.5k available tomorrow" — essential for real financial planning.
5. **Full tax automation** — Grana Capital leads, but only covers equity. A fully integrated fiscal engine covering all asset classes does not exist.

---

## 3. How Investment Accounts Relate to Regular Accounts

### Three-Layer Architecture

The market consensus (adopted by Kinvo, Gorila, Empower) separates financial data into three distinct layers:

```
Layer 1 — TRANSACTIONAL ACCOUNTS
  Bank accounts (checking, savings), credit cards
  → Tracks: available balance, cash flow, income/expenses

Layer 2 — BROKERAGE CUSTODY ACCOUNTS
  The "wallet" at a brokerage (XP, BTG, NuInvest, etc.)
  → Tracks: uninvested cash available at the broker to deploy

Layer 3 — ASSETS / POSITIONS
  The actual investments held in custody
  → CDB at BTG: R$ 10,000 @ 115% CDI, matures 2026-12
  → 100 shares of PETR4 @ R$ 38.50 average cost
  → Tesouro IPCA+ 2035: R$ 5,000 invested
  → FII KNRI11: 50 units @ R$ 148.00
```

### Net Worth Dashboard

The user dashboard should aggregate all three layers into a single **Net Worth** view, inspired by Empower's model:

```
NET WORTH (Total Net Patrimony)
├── ASSETS
│   ├── Bank Accounts                R$ 12,500
│   ├── Fixed Income (RF)            R$ 45,000
│   ├── Variable Income — Stocks     R$ 28,300
│   ├── FIIs                         R$ 15,000
│   └── Other (Crypto, etc.)          R$ 2,200
│                          Total:   R$ 103,000
└── LIABILITIES
    ├── Credit Card Balance           -R$ 3,200
    └── Auto Loan                    -R$ 45,000
                           Total:   -R$ 48,200

NET WORTH:                            R$ 54,800
```

### Technical Pricing Challenges per Asset Type

| Asset Class | Pricing Method | Update Frequency |
|---|---|---|
| Stocks (B3) | EOD closing price or intraday quote | Daily (or real-time) |
| FIIs | B3 closing unit price (cota) | Daily |
| ETFs | B3 closing unit price | Daily |
| BDRs | B3 closing unit price | Daily |
| Tesouro Direto | Published by Tesouro Nacional | Twice daily |
| Fixed Income (CDB, LCI, etc.) | Accrued interest engine (CDI/SELIC from BACEN) | Daily |
| Investment Funds | NAV (COTAS) from CVM/ANBIMA | D+1 to D+2 |
| Crypto | Exchange price feed | Real-time / hourly |

---

## 4. Supported Asset Types

### Target for MVP

```
Fixed Income:      CDB, LCI, LCA, LC, Debentures, CRA, CRI, RDB, Tesouro Direto
Variable Income:   Stocks (B3), FIIs, ETFs, BDRs
Investment Funds:  All regulated funds — data sourced from CVM (free, public)
```

### Deferred to Phase 2+

```
International:     US Stocks (via BDR or direct), ADRs
Private Pension:   PGBL, VGBL
Crypto Assets:     CEX-only initially (manual or API)
Offshore Assets:   Manual entry — Lei 14.754/2023 compliance
```

---

## 5. Data Sources Strategy

### Design Principle

> Minimize licensing costs in MVP by relying on free/public data sources. Defer expensive integrations (Open Finance, real-time B3 tickers) to a later phase when revenue justifies the cost.

### Free / Low-Cost Sources

| Source | Data Provided | Cost | MVP Fit |
|---|---|---|---|
| **B3 file export (PDF/Excel)** | Equity positions and transactions exported by the user from the Área do Investidor portal | Free (user action) | ★★★★☆ — same import workflow as credit card statements |
| **Tesouro Nacional API** | Tesouro Direto prices (updated 2x/day) | Free | ★★★★★ |
| **BACEN SGS API** | CDI, SELIC, IPCA historical rates | Free | ★★★★★ — essential for RF pricing and benchmarks |
| **CVM (open data)** | All regulated investment funds — NAV, composition, history | Free | ★★★★★ — source for funds module |
| **B3 COTAHIST** | Daily market bulletin (stocks, FIIs, ETFs, BDRs, options, forwards) — EOD closing prices for all B3 instruments | Free (public file, D+1) | ★★★★★ — **legally clear**: B3 FAQ explicitly states EOD/D-1 data can be distributed at no cost without prior B3 authorization; CAPTCHA limits automation (operational download process required); primary source for historical backfill and daily pricing |
| **Yahoo Finance** | Broad coverage including BR stocks (.SA suffix) | Free | ★★★☆☆ — legally clear for EOD (same B3 exemption); useful as automated fallback; no SLA, scraping-dependent, can break without notice |
| **brapi.dev** | B3 stock/FII/ETF EOD quotes, dividends, corporate events | R$49,90/month (150k req/month) | ★★☆☆☆ — legally clear for EOD data, but cost provides no additional legal protection over free sources |
| **Alpha Vantage** | BR and US stocks, EOD | Free tier + paid plans | ★★★☆☆ |

### Note: B3 CEI Discontinued

> The Canal Eletrônico do Investidor (CEI) was **discontinued by B3 in November 2021**. It was replaced by the **Área do Investidor**, which operates in two modes:
>
> - **Web portal (free to the user):** allows exporting positions and transactions as PDF or Excel — enables the file-import approach.
> - **Área do Investidor API (B2B, paid):** exclusive to B2B clients (fintechs, brokerages); requires a contract with B3 and carries licensing fees. Falls in the same category as Open Finance — **deferred to Phase 3**.

### B3 COTAHIST — EOD Market Data

The **COTAHIST** (Boletim Diário de Informações) is B3's official daily market bulletin covering all B3 instruments with EOD closing prices. Fixed-width format, 245-byte records, BDI type codes: 02=stocks, 12=FIIs, 14=ETFs, 70=calls, 80=puts, 10=forwards.

**Historical series:** https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/historico/mercado-a-vista/series-historicas/

#### Legal Status — CONFIRMED CLEAR (reviewed 2026-03-05)

Two independent sources confirm EOD data requires no B3 contract or fee:

1. **B3 Distributors FAQ:** *"EOD and historical D-1 data can be distributed at no cost by distributors or redistributors without prior B3 authorization."*
2. **B3 Commercial Policy 2026 (v1.0, OC 104/2025-PRE):** Pricing table covers only Tempo Real, Atraso Contínuo, and Atraso Snapshot. **EOD/historical has no pricing row.**
3. **B3 Commercial Policy 2027 (v1.0, OC 104/2025-PRE):** Same structure, same absence of EOD pricing. The exemption is structural, not a one-year anomaly. Both policies issued under the same Ofício Circular.

#### Operational Constraint

B3 added human validation (CAPTCHA) on the download page (`FormConsultaValida.asp`). Direct URLs no longer work for automated ingestion. **Solution: operational download process** — a team member downloads the daily/annual file; this is a private internal workflow and has no bearing on the legal right to use and display the data.

#### Future: Delayed Quotes (Phase 2+)

When adding real-time or delayed quotes (up to 15 min), a B3 distribution contract is required. For a B2C app, the applicable tier is **Terminal Não Profissional** (per dataset): R$1,86/user/month in 2026, R$2,10/user/month in 2027 (~13% annual increase). Budget accordingly when planning Phase 2.

### Open Finance and B3 API (B2B/Paid)

- **Decision: Deferred.** Open Finance certification, B3 Área do Investidor API, and BACEN compliance carry significant infrastructure and regulatory costs not justified at MVP stage.
- **When to revisit:** When revenue from paid plans supports the investment. Open Finance Phase 4 (investments data) reached 1.81 billion API calls/month in December 2024 — the infrastructure is maturing rapidly.

---

## 6. Fixed Income Data — RESOLVED

### Discovery: The B3 PDF Includes Fixed Income

The **B3 Área do Investidor portal** allows the user to download a consolidated PDF with **all custodied products**, including fixed income from any brokerage. Real example of the format:

```
CDB - BANCO BMG S/A    XP INVESTIMENTOS CCTVM S/A    30/03/2026    877    R$ 1.65    R$ 1,448.26
```

This means CDBs, LCIs, LCAs, and other RF products held in B3 custody appear in the same file as stocks, FIIs, and Tesouro Direto — **a single import covers the user's entire portfolio**, regardless of how many brokerages they use.

### Decision RF-01: RESOLVED — Option B (file import)

The workflow is identical to the one already used for credit card statements:

```
B3 PDF Upload → Extractor Service → AI Parser → Normalized Positions (RF + Equity)
```

- **Covers:** CDB, LCI, LCA, Debentures, CRA, CRI, Stocks, FIIs, ETFs, BDRs, Tesouro Direto
- **Multi-brokerage by nature:** the PDF aggregates all brokerages for the user's CPF
- **Cost:** zero — it is a free user action on the B3 portal
- **Updates:** user re-imports whenever they want to refresh positions (async flow, no real-time pressure)

### Caveat

The PDF reflects positions at **D-1** (previous business day). Not real-time, but sufficient for the portfolio tracking use case.

### Open Finance Phase 4 (deferred — still valid for future automation)

Once Open Finance becomes cost-viable, we can automate the cycle without any manual user action. The B3 file is the MVP solution; Open Finance is the natural evolution.

---

## 7. Investment Funds — CVM as Free Source

### Decision: Use CVM Open Data

The CVM (Comissão de Valores Mobiliários) publishes comprehensive, free, machine-readable data on all Brazilian regulated investment funds:

- Fund registry (CNPJ, name, type, manager, administrator)
- Daily NAV (quota value) history
- Monthly composition / holdings disclosure
- Performance and risk metrics

This eliminates the need for any paid data vendor for the investment funds module in MVP.

**Integration approach:**
1. Periodic ingestion job to sync CVM data into internal database
2. Fund search backed by internal CVM-sourced catalog
3. User links their fund positions (manual entry of units held) → app computes value using CVM NAV

---

## 8. Performance Calculation Methodology

### Two Methods — Both Relevant

| Method | What it measures | Best for |
|---|---|---|
| **TWRR** (Time-Weighted Rate of Return) | Asset/portfolio performance, isolating the effect of cash flows | Comparing against benchmarks (CDI, IBOVESPA) |
| **MWRR / IRR** (Money-Weighted Rate of Return) | Actual investor return, accounting for timing of contributions | Showing the user their real personal return |

**Recommendation:** Display TWRR for benchmark comparison; offer MWRR as an additional view (or tooltip explanation).

### Standard Benchmarks (Brazilian Market)

| Benchmark | Asset Class | Source |
|---|---|---|
| CDI | Fixed income reference | BACEN SGS (free) |
| SELIC | Government bond reference | BACEN SGS (free) |
| IPCA | Inflation | BACEN SGS (free) |
| IBOVESPA | Equity (broad market) | brapi.dev or B3 |
| IFIX | FII index | brapi.dev or B3 |
| Poupança | Low-risk savings reference | BACEN SGS (free) |

---

## 9. IR / Tax Features

### Why This Matters

IR (Imposto de Renda) automation is the **most underserved and highest-value** feature in the Brazilian investment app market. Grana Capital leads but covers only equity. A full-stack fiscal engine is a major differentiator.

### Feature Set by Priority

**High priority (MVP or Phase 2):**
- Auxiliary IR report for variable income (stocks, FIIs)
- Swing trade vs. day trade segregation (different tax rates: 15% vs. 20%)
- Monthly exemption tracking (R$ 20k/month exemption for stock sales)
- Loss compensation tracker (carry forward losses to offset future gains)

**Medium priority (Phase 2):**
- DARF generation (PDF with correct tax amount and payment code)
- Annual IRPF declaration report (structured data for Receita Federal's program)
- Fixed income yield reporting (from informes de rendimentos issued by custodians)
- FII IR rules (exempt when holding < 10% and fund has 50+ shareholders)

**Lower priority (Phase 3+):**
- Automated DARF payment (bank integration required)
- Crypto tax reporting (DeCripto / Receita Federal regulation, effective July 2026)
- Offshore asset tax reporting (Lei 14.754/2023)

---

## 10. Revised Roadmap

### Design Principle

> **Maximize value in MVP. Delay Open Finance due to cost. Bring high-value differentiation features (IR, planning) forward rather than waiting for data integration maturity.**

---

### MVP — Maximum Value, Minimum Cost

**Goal:** Deliver a compelling, usable investment module that outperforms competitors on integration with the existing financial control features.

**Scope:**

| Area | Features |
|---|---|
| **Asset Entry** | Manual entry of all asset types; B3 file import (PDF/Excel exported by the user from the Área do Investidor portal) |
| **Asset Types** | Fixed income (manual), stocks, FIIs, ETFs, BDRs, Tesouro Direto, investment funds (CVM) |
| **Pricing** | EOD quotes via brapi.dev; RF calculated via BACEN CDI/SELIC; Tesouro via Tesouro Nacional API; funds via CVM NAV |
| **Dashboard** | Net worth view aggregating bank accounts + cards + investments; allocation breakdown by asset class |
| **Performance** | TWRR rentability vs. benchmarks (CDI, IBOVESPA, IPCA, IFIX) |
| **Dividends / Proventos** | Dividend income calendar; YTD and historical dividend receipts |
| **FGC Coverage** | FGC coverage per issuer and portfolio percentage (visible on RF positions) |
| **IR Reports** | Auxiliary IR report for variable income (swing trade + FII rules) |
| **Fee Analyzer** | Automatic calculation of total fund administration fees across the portfolio |

**Unified import via B3 PDF:** The Área do Investidor portal PDF consolidates RF + equity from all the user's brokerages in a single file. The same `Upload → Extractor → AI` pipeline already in use for card statements covers the entire investment portfolio.

---

### Phase 2 — Planning and Differentiation

| Area | Features |
|---|---|
| **Tax Automation** | DARF generation; annual IRPF filing report; FII IR edge cases |
| **Goal Planner** | Retirement simulator; custom financial goals with contribution projections |
| **Intraday Quotes** | More frequent price updates — evaluate cost vs. benefit |
| **Private Pension** | PGBL and VGBL positions |
| **Crypto** | CEX-only: Binance, Mercado Bitcoin (manual or exchange API) |
| **Offline Import** | File-based RF import fully productized (if not in MVP) |

---

### Phase 3 — Premium Integrations

| Area | Features |
|---|---|
| **Open Finance Phase 4** | Automated RF data via standardized API (when cost is justified by revenue) |
| **B3 Área do Investidor API** | Automated equity position import via B3's paid B2B API (replacement for discontinued CEI) |
| **Broker APIs** | Direct integrations with XP, BTG, NuInvest, etc. |
| **Offshore Assets** | Manual + semi-automated import; Lei 14.754 compliance |
| **Advanced Tax** | Automated DARF payment; crypto tax (DeCripto); HNW structures |

---

## 11. Open Decisions from This Session

| # | Decision | Status | Notes |
|---|---|---|---|
| RF-01 | Fixed income import method for MVP | **RESOLVED** | Option B: B3 PDF (Área do Investidor) includes RF + equity from all brokerages — same pipeline as card statements |
| RF-02 | Automated EOD data source | **RESOLVED** | Yahoo Finance for MVP (free, same legal risk as paid alternatives that lack formal B3 license); COTAHIST for historical backfill via operational download process; migrate to licensed source when revenue justifies |
| RF-03 | EOD vs. delayed intraday quote tradeoff | **TBD** | EOD is the MVP approach; revisit at Phase 2 |
| RF-04 | CVM data ingestion frequency | **TBD** | Daily batch job likely sufficient; define SLA |
| RF-05 | COTAHIST display license | **RESOLVED — NO LICENSE REQUIRED** | B3 FAQ explicitly states: "EOD and historical D-1 data can be distributed at no cost by distributors or redistributors without prior B3 authorization." Distribution contract is only required for real-time or up-to-15-minute delayed data. COTAHIST (EOD/D+1) is fully exempt. Source: B3 Market Data Distributors FAQ. |

---

## 12. Key Insights

- **Invistus has a structural advantage** no Brazilian competitor has: expense control + investment tracking in one product. This is the north star for the integration strategy.
- **B3 PDF + COTAHIST + BACEN + CVM** covers the user's entire portfolio (RF + equity, multi-brokerage) at zero cost and with full legal clarity for MVP. EOD/D-1 data requires no B3 distribution contract (confirmed by B3 FAQ). COTAHIST provides all EOD prices legally and for free — the only operational constraint is the CAPTCHA on the download page, which is solved by an internal download process. Real-time or sub-15-minute delayed data is what triggers the contract requirement — irrelevant for this use case.
- **Open Finance is a future accelerator, not an MVP requirement.** The ecosystem is growing fast (1.81B API calls/month for investments in Dec 2024); timing will improve.
- **IR features should not be deferred.** Tax automation is the highest-perceived-value feature among Brazilian investors and the least covered by competitors outside Grana Capital's narrow scope.
- **App B3 (launched Dec 2025)** is a potential commoditizer of basic consolidation — Invistus must differentiate on planning, IR depth, and the financial control integration, not just on portfolio tracking.
