# Investment Portfolio — UX Benchmark & Wireframes

> Generated March 2026 · MVP Phase — manual entry and asset management

---

## Benchmark

### Apps analyzed

| App | Market | Key differentiator |
|-----|--------|--------------------|
| **Gorila** | BR | Widest Brazilian asset coverage (40+ product types, 14k+ assets), class grouping, advisor mode |
| **Kinvo** | BR | Contributions vs. Returns split (unique), Sharpe ratio, 2M users, investment goals |
| **Investidor10** | BR | Integrated fundamental analysis, lighter UI, generous free tier |
| **NuInvest** | BR | Embedded in Nubank (100M users), zero brokerage, simplicity-first |
| **Sharesight** | INT | Trade import via confirmation email, country-level tax reports, ETF overlap analysis |
| **Stock Events** | INT | Dividend calendar, monthly recap, polished mobile-first UX (4.58★) |
| **Portfolio Performance** | INT | TWR/IRR calculations, open-source, most rigorous return methodology |
| **Delta (eToro)** | INT | ISIN/SEDOL/CUSIP search, "Why is it moving?" (AI), crypto+stocks multi-asset |
| **Empower** | INT | Investment Checkup, lifetime fee projection, retirement integration |

### Table stakes (everyone has these)

- Total portfolio value prominently displayed with period selector (1D / 1M / 3M / 6M / YTD / 1Y / Max)
- Asset allocation pie/donut chart by class
- Holdings list with: name, current value, quantity, average cost, P&L ($ and %)
- Grouping by asset class, sortable by value or return
- Transaction form: type, asset, date, quantity, unit price, calculated total
- Benchmark comparison (CDI, IBOV for BR market)
- Position detail with transaction history

### Differentiators that matter (by tier)

**Tier 1 — high impact:**
| Feature | Reference | Reason |
|---------|-----------|--------|
| Contributions vs. Returns split | Kinvo | Most emotionally resonant metric — how much came from you vs. the market |
| Estimated IR (income tax) per position | Market gap | Biggest pain point for Brazilian investors, no one solves it natively |
| Average cost (preço médio) always visible | Entire market | Question #1 from equity investors |
| Price suggestion on trade date | Sharesight | Eliminates the main lookup friction in manual entry |
| Broker note PDF import (future) | Total gap in BR | Equivalent of Sharesight's email-import |

**Tier 2 — real differentiation:**
| Feature | Reference |
|---------|-----------|
| Income/dividend calendar (dividends, JCP, yields) | Stock Events |
| Sharpe ratio and risk-adjusted return | Kinvo |
| Full transaction type coverage (split, bonus shares, subscription, amortization) | Portfolio Performance |
| Settlement date suggestion (D+2 equities, D+1 fixed income) | Portfolio Performance |
| ISIN search (for BDRs and international assets) | Delta |

**Tier 3 — nice to have (V3+):**
- Monthly automated recap via push notification (Stock Events)
- Rebalancing calculator with target allocation (Portfolio Performance)
- Lifetime fee projection (Empower)
- ETF overlap analysis (Sharesight)
- Dark mode with color-coded P&L (Delta)

### Unmet opportunities in the Brazilian market

1. **Unified real-time IR dashboard** — no Brazilian app shows "how much IR you owe this month" consolidating equities + fixed income + funds
2. **Broker note (nota de corretagem) PDF parsing** — Sharesight's email-import equivalent; would be a major acquisition differentiator
3. **Rebalancing calculator** — given an IPS (70% equities / 30% fixed income), calculate which contributions to make without selling (to avoid triggering IR)
4. **International assets under Lei 4.173/2023** — no app handles monthly IR on offshore investments in an integrated way
5. **True mobile-first UX** — Delta and Stock Events lead internationally; Brazilian apps are desktop-first ports

---

## Design decisions adopted

| Decision | Rationale |
|----------|-----------|
| Bottom sheet for transaction form | Context preserved, less navigation disruption |
| Progressive disclosure in the form | Simple for most users; power fields available on demand |
| Price suggestion = closing price on trade date | Removes the main friction point in manual entry |
| Segmented control for transaction type | Buy/Sell/Income cover 90% of cases; advanced types behind "more options" |
| Collapsible grouping by asset class | Gorila pattern — avoids visual overload; class subtotals are the priority |
| Contributions vs. Returns in overview | Kinvo differentiator — adopting as standard |
| Estimated IR in position detail | Market gap — delivered in MVP |
| Custody as INVESTMENT account selector | Consistent with the data model (financial_account of type INVESTMENT) |

---

## Wireframes

### Screen 1 — Investments Tab (Portfolio Overview)

```
┌─────────────────────────────────────┐
│ ☰  Investments            🔔  [👤]  │
├─────────────────────────────────────┤
│                                     │
│  Total Portfolio                    │
│  R$ 142,830.00                      │
│                                     │
│  ┌──────────────┬──────────────┐    │
│  │ Contributions│   Returns    │    │
│  │ R$ 120,000   │ +R$ 22,830   │    │
│  │              │    +19.0%    │    │
│  └──────────────┴──────────────┘    │
│                                     │
│  [1D] [1M] [3M] [6M] [YTD] [1Y] [Max]
│  ──────────────────────────────     │
│  ╭─────────────────────────────╮    │
│  │  ╱╲    ╱╲         ╱╲       │    │
│  │╱    ╲╱    ╲    ╱╲╱   ╲    │    │
│  │·  ·  ·  ·  ╲╱        ╲   │    │
│  ╰─────────────────────────────╯    │
│  vs CDI ── +2.3pp   vs IBOV +8.1pp │
│                                     │
├─────────────────────────────────────┤
│  Allocation      [List ▾]  [+ Asset] │
│  ╭───────╮                          │
│  │ ██░░░ │  Equities        52%     │
│  │ ░█░░░ │  Fixed Income    31%     │
│  │ ░░█░░ │  REITs (FIIs)    12%     │
│  │ ░░░█░ │  Other            5%     │
│  ╰───────╯                          │
├─────────────────────────────────────┤
│  My Holdings         Sort ↕    ⚙️   │
│                                     │
│  ▼ Equities                R$ 74,270│
│  ┌─────────────────────────────────┐│
│  │ PETR4   Petrobras ON            ││
│  │ 200 sh · Avg R$ 28.50           ││
│  │ R$ 37,200           +30.5% ▲   ││
│  └─────────────────────────────────┘│
│  ┌─────────────────────────────────┐│
│  │ BOVA11  iShares IBOV ETF        ││
│  │  50 sh · Avg R$ 112.40          ││
│  │ R$ 6,250            +11.2% ▲   ││
│  └─────────────────────────────────┘│
│  [show 3 more]                      │
│                                     │
│  ▶ Fixed Income            R$ 44,290│
│  ▶ REITs (FIIs)            R$ 17,140│
│  ▶ Other                   R$  7,130│
│                                     │
├─────────────────────────────────────┤
│  📅 Upcoming Income                 │
│  MXRF11  R$ 0.09/sh   Apr 15       │
│  KNRI11  R$ 0.67/sh   Apr 20       │
│  [view full calendar]               │
│                                     │
├─────────────────────────────────────┤
│  🏠 Home  │  💳 Banking  │  📈 Invest  │  👤 │
└─────────────────────────────────────┘
```

**Design notes:**
- `Contributions vs. Returns` card placed directly below total portfolio value (Kinvo differentiator)
- Chart shows portfolio line + configurable benchmark overlay (CDI default)
- Holdings grouped by asset class with collapse; class subtotal always visible
- "Upcoming Income" section previews the full dividend/income calendar (V2 complete)
- `+ Asset` FAB for quick position entry

---

### Screen 2 — Position Detail

```
┌─────────────────────────────────────┐
│ ←   PETR4 · Petrobras ON       ⋮   │
├─────────────────────────────────────┤
│                                     │
│  R$ 37,200.00                       │
│  +R$ 8,700  (+30.5%)  ▲ above avg  │
│                                     │
│  ╭─────────────────────────────╮    │
│  │         ╱╲                  │    │
│  │        ╱  ╲      ╱╲        │    │
│  │    ╱╲ ╱    ╲    ╱  ╲       │    │
│  │───╱──╲──────╲──╱────╲──    │    │
│  ╰─────────────────────────────╯    │
│  [1M] [3M] [6M] [YTD] [1Y] [Max]   │
│                                     │
├─────────────────────────────────────┤
│  ┌──────────┬──────────┬──────────┐ │
│  │ Shares   │ Avg Cost │ Price    │ │
│  │ 200      │ R$ 28.50 │ R$ 37.60 │ │
│  └──────────┴──────────┴──────────┘ │
│                                     │
│  ┌──────────────────────────────┐   │
│  │ Amount invested  R$  5,700   │   │
│  │ Current value    R$  7,520   │   │
│  │ Unrealized gain +R$  1,820   │   │
│  │ Income received +R$    820   │   │
│  │ Estimated IR     -R$    273  │   │  ← differentiator: IR visible
│  └──────────────────────────────┘   │
│                                     │
├─────────────────────────────────────┤
│  Transaction History                │
│                                     │
│  ↑ Buy      Jan 15, 2024   100 sh   │
│    R$ 31.20/sh · Total R$ 3,120     │
│    XP Investimentos                 │
│                                     │
│  ↑ Buy      Oct 10, 2023   100 sh   │
│    R$ 25.80/sh · Total R$ 2,580     │
│    XP Investimentos                 │
│                                     │
│  💰 Dividend  Mar 15, 2024          │
│    R$ 0.42/sh · Total R$   420      │
│                                     │
│  [load more]                        │
│                                     │
├─────────────────────────────────────┤
│        ┌─────────────────┐          │
│        │ + New Transaction│          │
│        └─────────────────┘          │
└─────────────────────────────────────┘
```

**Design notes:**
- Average cost, current price, and quantity in a dedicated row — investor metric #1
- Estimated IR visible at position level — no Brazilian app does this natively
- Transaction type with icon + color (↑ green = buy, ↓ red = sell, 💰 blue = income)
- Contextual FAB `+ New Transaction` pre-fills asset and custody account

---

### Screen 3 — Record Transaction (bottom sheet, main fields)

```
┌─────────────────────────────────────┐
│  [dimmed background — previous screen]
│                                     │
╰─────────────────────────────────────╯
┌─────────────────────────────────────┐  ← bottom sheet
│  ▬▬▬                               │    drag handle
│  New Transaction                 ✕  │
│                                     │
│  Type                               │
│  ╔══════════╦══════════╦══════════╗  │
│  ║  ▲ BUY   ║  ▼ SELL  ║ 💰 INC. ║  │  ← segmented control
│  ╚══════════╩══════════╩══════════╝  │
│  [+ advanced ▾]  Split · Rights…    │  ← progressive disclosure
│                                     │
│  Asset                              │
│  ┌─────────────────────────────────┐│
│  │ 🔍  Search by name or ticker…  ││
│  └─────────────────────────────────┘│
│  ● PETR4  Petrobras ON         ✓   │  ← pre-filled if navigated
│            B3 · Equity              │    from position detail
│                                     │
│  Trade Date                         │
│  ┌─────────────────────────────────┐│
│  │  04  /  15  /  2025       📅   ││
│  └─────────────────────────────────┘│
│                                     │
│  Quantity             Unit Price    │
│  ┌──────────────┐  ┌───────────────┐│
│  │  100         │  │  R$ 37.60     ││
│  └──────────────┘  └───────────────┘│
│                        💡 today's price
│                                     │
│  ┌─────────────────────────────────┐│
│  │  Estimated total:  R$ 3,760.00  ││  ← computed in real time
│  └─────────────────────────────────┘│
│                                     │
│  Custody Account                    │
│  ┌─────────────────────────────────┐│
│  │  XP Investimentos           ▾  ││  ← INVESTMENT account list
│  └─────────────────────────────────┘│
│                                     │
│  [+ Advanced fields ▾]              │
│                                     │
│  ┌─────────────────────────────────┐│
│  │           Record Buy            ││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘
```

---

### Screen 4 — Advanced Fields (expanded)

```
│  [+ Advanced fields ▲]              │
│                                     │
│  Brokerage fee                      │
│  ┌─────────────────────────────────┐│
│  │  R$ 0.00                        ││
│  └─────────────────────────────────┘│
│                                     │
│  Settlement date                    │
│  ┌─────────────────────────────────┐│
│  │  04  /  17  /  2025  (D+2)  📅 ││  ← suggested D+2 eq / D+1 FI
│  └─────────────────────────────────┘│
│                                     │
│  Price factor                       │
│  ┌─────────────────────────────────┐│
│  │  1                              ││  ← relevant for debentures
│  └─────────────────────────────────┘│
│                                     │
│  Broker note number                 │
│  ┌─────────────────────────────────┐│
│  │  e.g. 123456                    ││
│  └─────────────────────────────────┘│
│                                     │
│  Notes                              │
│  ┌─────────────────────────────────┐│
│  │  e.g. strategic buy Q1…         ││
│  └─────────────────────────────────┘│
│                                     │
│  📎 Attach broker note              │  ← audit trail / future phase
│     (PDF or image)                  │
```

---

### Screen 5 — Fixed Income: first entry (conditional fields)

> Shown when selected asset type is CDB / LCI / LCA / CRI / CRA / Debentures / Treasury

```
│  ─── Bond terms ───────────────────  │
│                                      │
│  Indexer                             │
│  ┌──────────────────────────────────┐│
│  │  CDI                         ▾  ││
│  └──────────────────────────────────┘│
│                                      │
│  % of indexer          Pre-fixed rate│
│  ┌──────────────┐  ┌────────────────┐│
│  │  110%        │  │  —             ││  ← floating or fixed, not both
│  └──────────────┘  └────────────────┘│
│                                      │
│  Maturity date       Grace period    │
│  ┌──────────────┐  ┌────────────────┐│
│  │  04/15/2027  │  │  10/15/2025    ││
│  └──────────────┘  └────────────────┘│
│                                      │
│  Issue date                          │
│  ┌──────────────────────────────────┐│
│  │  04/10/2024                      ││
│  └──────────────────────────────────┘│
│                                      │
│  ☑  Tax exempt  (LCI / LCA / CRI / CRA)
```

---

### Screen 6 — Empty state (first access)

```
┌─────────────────────────────────────┐
│ ☰  Investments            🔔  [👤]  │
├─────────────────────────────────────┤
│                                     │
│                                     │
│           📈                        │
│                                     │
│   Your portfolio is empty           │
│   Add your first asset to start     │
│   tracking your net worth           │
│                                     │
│   ┌─────────────────────────────┐   │
│   │    + Add your first asset   │   │
│   └─────────────────────────────┘   │
│                                     │
│   ┌─────────────────────────────┐   │
│   │    View demo portfolio      │   │  ← reduces abandonment
│   └─────────────────────────────┘   │
│                                     │
│   💡 Connect your brokerage via     │
│      Open Finance to import         │
│      automatically (coming soon)    │
│                                     │
│                                     │
├─────────────────────────────────────┤
│  🏠 Home  │  💳 Banking  │  📈 Invest  │  👤 │
└─────────────────────────────────────┘
```

---

## Feature prioritization

### MVP (current phase)
- [ ] Investments tab with Portfolio Overview
- [ ] Contributions vs. Returns breakdown in overview
- [ ] Collapsible holdings list grouped by asset class
- [ ] Position detail with average cost + estimated IR
- [ ] Manual transaction form (Buy / Sell / Income)
- [ ] Conditional fields for Fixed Income
- [ ] Empty state with guided CTA
- [ ] Custody via existing INVESTMENT account selector

### V2
- [ ] Full income/dividend calendar
- [ ] Sharpe ratio and risk-adjusted return display
- [ ] Customizable benchmark (beyond CDI/IBOV)
- [ ] ISIN search (for BDRs and international assets)
- [ ] Push notifications for income events and corporate actions

### V3+
- [ ] Rebalancing calculator with target allocation
- [ ] Broker note (nota de corretagem) PDF parsing — biggest possible differentiator in BR market
- [ ] Unified monthly IR dashboard (equities + fixed income + funds) with DARF generation
- [ ] ETF overlap analysis
- [ ] IRPF report (copy-paste format compatible with Receita Federal's tax program)
