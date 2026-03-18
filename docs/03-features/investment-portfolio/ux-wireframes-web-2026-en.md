# Investment Portfolio — Web UI/UX Wireframes

> March 2026 · Web-first design · 1280px+ desktop baseline

---

## Design system references

| Pattern | Inspiration |
|---------|-------------|
| Sidebar navigation | Linear, Notion, Vercel Dashboard |
| Data table with inline actions | Airtable, Retool, Sharesight |
| Metric cards row | Stripe Dashboard, Vercel Analytics |
| Slide-over detail panel | GitHub PR detail, Figma comments |
| Centered modal for transactions | Stripe, Intercom |
| Command palette (⌘K) | Linear, Raycast, Vercel |
| Chart with period selector | Robinhood Web, Coinbase Pro |
| Split-panel (list + detail) | Gmail, Superhuman, Apple Mail web |

---

## Screen 1 — Portfolio Dashboard (main view)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  ▪ invistus                                                    [⌘K Search…]  👤  │  ← topbar
├────────────┬─────────────────────────────────────────────────────────────────────┤
│            │                                                                      │
│  📊 Overview│  Portfolio Overview                            Apr 2025  [+ Record] │  ← page header + primary CTA
│            │                                                                      │
│  💼 Holdings│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐       │
│            │  │ Total Portfolio  │ │  Contributions   │ │    Returns      │       │
│  📋 History │  │                 │ │                  │ │                 │       │
│            │  │ R$ 142,830      │ │  R$ 120,000      │ │ +R$ 22,830      │       │
│  📅 Income  │  │                 │ │                  │ │    +19.0%       │       │
│            │  └─────────────────┘ └─────────────────┘ └─────────────────┘       │
│  ──────────│                                                                      │
│            │  ┌────────────────────────────────────────────────────────────────┐  │
│  Settings  │  │  Performance                                                    │  │
│            │  │                                                                 │  │
│            │  │  [1D] [1W] [1M] [3M] [6M] [YTD] [1Y] [All]    ○ CDI  ○ IBOV  │  │
│            │  │                                                                 │  │
│            │  │   R$145k ┤                                          ╭─╮         │  │
│            │  │   R$140k ┤                                    ╭─────╯ ╰──       │  │
│            │  │   R$135k ┤                             ╭──────╯                 │  │
│            │  │   R$130k ┤                      ╭──────╯  ·  ·  ·  ·  ·  CDI  │  │
│            │  │   R$125k ┤               ╭──────╯                               │  │
│            │  │   R$120k ┤───────────────╯                                      │  │
│            │  │          └────────────────────────────────────────────────────  │  │
│            │  │          Oct    Nov    Dec    Jan    Feb    Mar    Apr           │  │
│            │  │                                                                 │  │
│            │  │  vs CDI  +2.3pp ▲      vs IBOV  +8.1pp ▲      IRR  14.2% p.a. │  │
│            │  └────────────────────────────────────────────────────────────────┘  │
│            │                                                                      │
│            │  ┌─────────────────────────────────┐  ┌────────────────────────┐   │
│            │  │  Allocation                      │  │  Upcoming Income       │   │
│            │  │                                  │  │                        │   │
│            │  │   ╭──────╮  ■ Equities    52%    │  │  Apr 15  MXRF11        │   │
│            │  │   │██░░░ │  □ Fixed Inc.  31%    │  │          R$ 0.09/sh    │   │
│            │  │   │░█░░░ │  ▒ REITs       12%    │  │                        │   │
│            │  │   │░░█░░ │  ░ Other        5%    │  │  Apr 20  KNRI11        │   │
│            │  │   ╰──────╯                       │  │          R$ 0.67/sh    │   │
│            │  │                                  │  │                        │   │
│            │  │  Total: 12 assets  3 brokerages  │  │  [view full calendar]  │   │
│            │  └─────────────────────────────────┘  └────────────────────────┘   │
│            │                                                                      │
└────────────┴─────────────────────────────────────────────────────────────────────┘
```

**Design notes:**
- Left sidebar: persistent navigation, 240px, collapses to icon-only on 1024px
- Metric cards: 3-column grid, uniform height, subtle border, no heavy shadows
- Chart: full-width, benchmark lines as dashed overlays, toggled via pill buttons
- Two bottom widgets side-by-side: Allocation donut + Income calendar preview
- `[+ Record]` primary button always visible top-right — lowest friction for data entry

---

## Screen 2 — Holdings Table (main holdings view)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  ▪ invistus                                                    [⌘K Search…]  👤  │
├────────────┬─────────────────────────────────────────────────────────────────────┤
│            │                                                                      │
│  📊 Overview│  Holdings                                              [+ Record]   │
│            │                                                                      │
│  💼 Holdings│  [All classes ▾]  [All brokerages ▾]  [🔍 Filter]     Group by ▾   │
│  ← active  │                                                                      │
│  📋 History │  ┌──────────────────────────────────────────────────────────────┐   │
│            │  │  ▼ Equities                        R$ 74,270      +18.4%      │   │  ← collapsible group header
│  📅 Income  │  ├───────────┬──────────┬──────────┬──────────┬────────┬───────┤   │
│            │  │ Asset ↕   │  Shares  │ Avg Cost │  Price   │ Value  │  P&L  │   │  ← sortable columns
│  ──────────│  ├───────────┼──────────┼──────────┼──────────┼────────┼───────┤   │
│            │  │ PETR4     │  200     │ R$ 28.50 │ R$ 37.60 │ R$ 37,200│+30.5%▲│   │
│  Settings  │  │ Petrobras │          │          │          │        │       │   │
│            │  ├───────────┼──────────┼──────────┼──────────┼────────┼───────┤   │
│            │  │ BOVA11    │   50     │ R$112.40 │ R$124.90 │  R$ 6,245│+11.1%▲│   │
│            │  │ iSh. IBOV │          │          │          │        │       │   │
│            │  ├───────────┼──────────┼──────────┼──────────┼────────┼───────┤   │
│            │  │ MXRF11    │  800     │ R$ 10.20 │  R$ 9.80 │  R$ 7,840│ -3.9%▼│   │
│            │  │ Maxi Renda│          │          │          │        │       │   │
│            │  ├───────────┴──────────┴──────────┴──────────┴────────┴───────┤   │
│            │  │  ▶ Fixed Income                   R$ 44,290      +11.2%      │   │  ← collapsed
│            │  ├─────────────────────────────────────────────────────────────┤   │
│            │  │  ▶ REITs (FIIs)                   R$ 17,140       +8.7%      │   │
│            │  ├─────────────────────────────────────────────────────────────┤   │
│            │  │  ▶ Other                           R$  7,130       +4.2%      │   │
│            │  └─────────────────────────────────────────────────────────────┘   │
│            │                                                                      │
│            │  12 positions  ·  3 asset classes  ·  last updated 2m ago  [↻]     │
│            │                                                                      │
└────────────┴─────────────────────────────────────────────────────────────────────┘

  ← hover state on a row reveals inline actions:
  │ PETR4     │  200     │ R$ 28.50 │ R$ 37.60 │ R$ 37,200│+30.5%▲│ [+ Tx] [↗]  │
```

**Design notes:**
- Full data table with sortable columns — web can afford density that mobile can't
- Collapsible group rows function as section headers with subtotals
- Hover reveals `[+ Tx]` (quick record transaction) and `[↗]` (open detail) per row
- Filters: asset class, brokerage, positive/negative P&L — always visible above table
- `Group by` dropdown: Class (default) · Brokerage · None (flat list)
- Negative P&L in muted red, positive in muted green — accessible, not glaring
- Sticky table header as user scrolls down through many positions

---

## Screen 3 — Position Detail (slide-over panel)

> Clicking a row or `[↗]` opens a slide-over from the right, keeping the table visible

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  ▪ invistus                                                    [⌘K Search…]  👤  │
├────────────┬──────────────────────────────────┬────────────────────────────────  │
│            │                                  │                               ✕  │
│  📊 Overview│  Holdings          [+ Record]    │  PETR4 · Petrobras ON            │
│            │                                  │                                  │
│  💼 Holdings│  [All ▾]  [All ▾]  [🔍]          │  R$ 37,200.00                    │
│  ← active  │                                  │  +R$ 8,700  · +30.5%  ▲         │
│            │  ┌──────────────────────────────┐│                                  │
│  📋 History │  │▼ Equities       R$ 74,270   ││  ┌────────────────────────────┐ │
│            │  ├───────┬──────┬──────┬────────┤│  │ [Overview] [Transactions]  │ │  ← tabs
│  📅 Income  │  │ Asset │ Shs  │ Avg  │  P&L   ││  └────────────────────────────┘ │
│            │  ├───────┼──────┼──────┼────────┤│                                  │
│  ──────────│  │PETR4  │ 200  │28.50 │+30.5%▲ ││  ┌────────────────────────────┐ │
│            │  │       │      │      │        ││  │                            │ │
│  Settings  │  ├───────┼──────┼──────┼────────┤│  │   ╱╲                       │ │
│            │  │BOVA11 │  50  │112.40│+11.1%▲ ││  │  ╱  ╲    ╱╲               │ │
│            │  ├───────┼──────┼──────┼────────┤│  │ ╱    ╲╱╲╱  ╲              │ │
│            │  │MXRF11 │ 800  │ 10.20│ -3.9%▼ ││  │╱          ╲──             │ │
│            │  ├───────┴──────┴──────┴────────┤│  └────────────────────────────┘ │
│            │  │▶ Fixed Income   R$ 44,290    ││  [1M] [3M] [6M] [1Y] [All]      │
│            │  ├──────────────────────────────┤│                                  │
│            │  │▶ REITs (FIIs)   R$ 17,140    ││  ┌───────────┬────────────────┐ │
│            │  └──────────────────────────────┘│  │ Shares    │ 200            │ │
│            │                                  │  │ Avg Cost  │ R$ 28.50       │ │
│            │                                  │  │ Curr. Price│ R$ 37.60      │ │
│            │                                  │  │ Invested  │ R$ 5,700       │ │
│            │                                  │  │ Value     │ R$ 7,520       │ │
│            │                                  │  │ Unreal. G.│ +R$ 1,820      │ │
│            │                                  │  │ Income    │ +R$   820      │ │
│            │                                  │  │ Est. IR   │ -R$   273  ⓘ  │ │  ← IR tooltip
│            │                                  │  └───────────┴────────────────┘ │
│            │                                  │                                  │
│            │                                  │  [+ Record Transaction]          │
└────────────┴──────────────────────────────────┴──────────────────────────────────┘
```

**Design notes:**
- Slide-over is 420px wide, overlaps the table (table stays interactive underneath)
- Two tabs: Overview (summary + chart) and Transactions (full history)
- Estimated IR row has an `ⓘ` tooltip explaining the calculation method
- `[+ Record Transaction]` button inside the panel pre-fills asset + brokerage
- Slide-over can be pinned open; resizable with a drag handle on its left edge
- ESC or clicking backdrop closes it; browser URL updates (`/holdings/PETR4`) for shareability

---

## Screen 4 — Transaction History tab (inside slide-over)

```
│  PETR4 · Petrobras ON                                              ✕  │
│                                                                        │
│  R$ 37,200.00   +30.5% ▲                                             │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │ [Overview]  [Transactions ●]                                     │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  [All types ▾]  [All brokerages ▾]                  [+ Record here]  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │ Type       Date        Qty    Price      Total      Brokerage    │ │
│  ├──────────────────────────────────────────────────────────────────┤ │
│  │ ▲ Buy      Jan 15,'24  100   R$ 31.20  R$ 3,120   XP Invest.  ⋮│ │
│  ├──────────────────────────────────────────────────────────────────┤ │
│  │ ▲ Buy      Oct 10,'23  100   R$ 25.80  R$ 2,580   XP Invest.  ⋮│ │
│  ├──────────────────────────────────────────────────────────────────┤ │
│  │ 💰 Dividend Mar 15,'24   —    R$  0.42  R$   420   —           ⋮│ │
│  ├──────────────────────────────────────────────────────────────────┤ │
│  │ 💰 Dividend Sep 15,'23   —    R$  0.38  R$   380   —           ⋮│ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  4 transactions · avg cost updated on each buy                        │
│                                                                        │
│  [+ Record Transaction]                                               │
```

---

## Screen 5 — Record Transaction (centered modal)

> Opens from `[+ Record]` in the header, or `[+ Tx]` hover action on a row

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  [dimmed backdrop]                                                               │
│                                                                                  │
│              ┌──────────────────────────────────────────────┐                   │
│              │  Record Transaction                        ✕  │                   │
│              ├──────────────────────────────────────────────┤                   │
│              │                                              │                   │
│              │  Type                                        │                   │
│              │  ┌──────────┬──────────┬──────────────────┐ │                   │
│              │  │  ▲ Buy   │  ▼ Sell  │   💰 Income      │ │                   │
│              │  └──────────┴──────────┴──────────────────┘ │                   │
│              │  [Split · Bonus · Subscription · Other ▾]   │                   │
│              │                                              │                   │
│              │  Asset                                       │                   │
│              │  ┌──────────────────────────────────────┐   │                   │
│              │  │ 🔍  PETR4 — Petrobras ON          ✓  │   │                   │
│              │  └──────────────────────────────────────┘   │                   │
│              │                                              │                   │
│              │  Trade Date              Custody Account     │                   │
│              │  ┌───────────────────┐  ┌─────────────────┐ │                   │
│              │  │  04 / 15 / 2025   │  │ XP Investimentos│ │                   │
│              │  └───────────────────┘  └─────────────────┘ │                   │
│              │                                              │                   │
│              │  Quantity                Unit Price          │                   │
│              │  ┌───────────────────┐  ┌─────────────────┐ │                   │
│              │  │  100              │  │  R$ 37.60       │ │                   │
│              │  └───────────────────┘  └─────────────────┘ │                   │
│              │                          💡 closing price    │                   │
│              │                                              │                   │
│              │  ┌──────────────────────────────────────┐   │                   │
│              │  │  Total:  R$ 3,760.00                  │   │                   │
│              │  └──────────────────────────────────────┘   │                   │
│              │                                              │                   │
│              │  ▸ Advanced  (settlement date · fees · note)│                   │
│              │                                              │                   │
│              │  ┌──────────────┐  ┌───────────────────────┐│                   │
│              │  │    Cancel    │  │      Record Buy        ││                   │
│              │  └──────────────┘  └───────────────────────┘│                   │
│              │                                              │                   │
│              └──────────────────────────────────────────────┘                   │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**Design notes:**
- Modal is 520px wide, vertically centered, closes on ESC or backdrop click
- Asset field opens an inline dropdown with fuzzy search (name, ticker, ISIN)
- Closing price suggestion fetched from financial-data service for the selected date
- Total auto-updates as quantity or price changes (no submit needed to see it)
- `▸ Advanced` disclosure expands inline — no second step, no modal stacking
- Primary button label reflects the selected type: "Record Buy" / "Record Sell" / "Record Income"

---

## Screen 6 — Advanced fields (expanded inline)

```
│              │  ▾ Advanced                                  │
│              │                                              │
│              │  Settlement Date         Brokerage Fee       │
│              │  ┌───────────────────┐  ┌─────────────────┐ │
│              │  │  04 / 17 / 2025   │  │  R$ 0.00        │ │
│              │  │  (D+2)            │  │                 │ │
│              │  └───────────────────┘  └─────────────────┘ │
│              │                                              │
│              │  Price Factor            Broker Note No.     │
│              │  ┌───────────────────┐  ┌─────────────────┐ │
│              │  │  1                │  │  e.g. 123456    │ │
│              │  └───────────────────┘  └─────────────────┘ │
│              │                                              │
│              │  Notes                                       │
│              │  ┌──────────────────────────────────────┐   │
│              │  │  e.g. strategic allocation Q1…        │   │
│              │  └──────────────────────────────────────┘   │
│              │                                              │
│              │  📎 Attach document  (PDF, PNG, JPG)         │
```

---

## Screen 7 — Fixed Income: conditional fields

> Shown when asset type is CDB / LCI / LCA / CRI / CRA / Debentures / Treasury

```
│              │  ─── Bond terms (new position) ────────────  │
│              │                                              │
│              │  Indexer                  % of indexer       │
│              │  ┌───────────────────┐  ┌─────────────────┐ │
│              │  │  CDI          ▾   │  │  110%           │ │
│              │  └───────────────────┘  └─────────────────┘ │
│              │                                              │
│              │  Pre-fixed rate          Issue Date          │
│              │  ┌───────────────────┐  ┌─────────────────┐ │
│              │  │  —                │  │  04 / 10 / 2024  │ │
│              │  └───────────────────┘  └─────────────────┘ │
│              │                                              │
│              │  Maturity Date           Grace Period        │
│              │  ┌───────────────────┐  ┌─────────────────┐ │
│              │  │  04 / 15 / 2027   │  │  10 / 15 / 2025 │ │
│              │  └───────────────────┘  └─────────────────┘ │
│              │                                              │
│              │  ☐  Tax exempt  (LCI / LCA / CRI / CRA)     │
```

---

## Screen 8 — Command palette (⌘K)

> Power-user shortcut accessible from anywhere in the app

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  [dimmed backdrop]                                                               │
│                                                                                  │
│         ┌──────────────────────────────────────────────────────┐                │
│         │  🔍  Type an asset, action, or command…              │                │
│         ├──────────────────────────────────────────────────────┤                │
│         │  Recent                                              │                │
│         │  📈  PETR4 — Petrobras ON                    ↵      │                │
│         │  📈  MXRF11 — Maxi Renda FII                 ↵      │                │
│         │  ─────────────────────────────────────────────────   │                │
│         │  Actions                                             │                │
│         │  ✚   Record new transaction              ⌘N         │                │
│         │  💼  Go to Holdings                                  │                │
│         │  📅  Go to Income Calendar                           │                │
│         │  ─────────────────────────────────────────────────   │                │
│         │  [type "BOVA" to search assets…]                    │                │
│         └──────────────────────────────────────────────────────┘                │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## Screen 9 — Income / Dividend Calendar

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  ▪ invistus                                                    [⌘K Search…]  👤  │
├────────────┬─────────────────────────────────────────────────────────────────────┤
│            │                                                                      │
│  📊 Overview│  Income Calendar                 ◀ Apr 2025 ▶    [List] [Calendar] │
│            │                                                                      │
│  💼 Holdings│  ┌──────────────────────────────────────────────────────────────┐  │
│            │  │  Mon     Tue     Wed     Thu     Fri     Sat     Sun           │  │
│  📋 History │  ├────────┬───────┬───────┬───────┬───────┬───────┬─────────┤  │
│            │  │   7    │   8   │   9   │  10   │  11   │  12   │   13    │  │
│  📅 Income  │  │        │       │       │       │MXRF11 │       │         │  │
│  ← active  │  │        │       │       │       │+0.09  │       │         │  │
│            │  ├────────┼───────┼───────┼───────┼───────┼───────┼─────────┤  │
│  ──────────│  │  14    │  15   │  16   │  17   │  18   │  19   │   20    │  │
│            │  │        │PETR4  │       │       │       │       │ KNRI11  │  │
│  Settings  │  │        │+0.42  │       │       │       │       │ +0.67   │  │
│            │  ├────────┼───────┼───────┼───────┼───────┼───────┼─────────┤  │
│            │  │  21    │  22   │  23   │  24   │  25   │  26   │   27    │  │
│            │  │        │       │ HGLG11│       │       │       │         │  │
│            │  │        │       │ +1.20 │       │       │       │         │  │
│            │  └──────────────────────────────────────────────────────────────┘  │
│            │                                                                      │
│            │  April expected income:  R$ 412.00  across 3 assets                 │
│            │                                                                      │
│            │  ┌──────────────────────────────────────────────────────────────┐  │
│            │  │ Asset    Ex-date    Pay-date    Amount    Yield on cost       │  │
│            │  ├──────────────────────────────────────────────────────────────┤  │
│            │  │ MXRF11   Apr 11    Apr 15     R$ 72.00        8.5% p.a.     │  │
│            │  │ PETR4    Apr 12    Apr 15     R$ 84.00        6.2% p.a.     │  │
│            │  │ KNRI11   Apr 17    Apr 20     R$536.00       12.1% p.a.     │  │
│            │  └──────────────────────────────────────────────────────────────┘  │
│            │                                                                      │
└────────────┴─────────────────────────────────────────────────────────────────────┘
```

---

## Screen 10 — Empty state (first access)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  ▪ invistus                                                    [⌘K Search…]  👤  │
├────────────┬─────────────────────────────────────────────────────────────────────┤
│            │                                                                      │
│  📊 Overview│                                                                     │
│            │                                                                      │
│  💼 Holdings│                                                                     │
│            │                        📈                                            │
│  📋 History │                                                                     │
│            │              Your portfolio is empty                                 │
│  📅 Income  │              Add your first asset to start tracking                 │
│            │              your net worth and returns.                             │
│  ──────────│                                                                      │
│            │              ┌──────────────────────────┐                           │
│  Settings  │              │   ✚  Record first asset  │                           │
│            │              └──────────────────────────┘                           │
│            │                                                                      │
│            │              ┌──────────────────────────┐                           │
│            │              │   View demo portfolio    │  ← no-friction explore    │
│            │              └──────────────────────────┘                           │
│            │                                                                      │
│            │              ──────── or connect automatically ────────              │
│            │                                                                      │
│            │              💡 Connect your brokerage via Open Finance              │
│            │                 to import all positions automatically                │
│            │                 (coming soon)                                        │
│            │                                                                      │
└────────────┴─────────────────────────────────────────────────────────────────────┘
```

---

## Key web-vs-mobile differences

| Concern | Mobile | Web |
|---------|--------|-----|
| Navigation | Bottom tab bar | Left sidebar (collapsible) |
| Transaction entry | Bottom sheet | Centered modal (520px) |
| Position detail | Full-screen push | Slide-over panel (keeps context) |
| Holdings display | Card list | Sortable/filterable data table |
| Data density | 1 metric per line | Multiple columns per row |
| Advanced fields | Scroll down | Inline expand (no modal stacking) |
| Quick actions | FAB | Hover row actions + `⌘K` palette |
| Asset search | Full-screen overlay | Inline dropdown autocomplete |
| Benchmarks | Toggle buttons below chart | Pill checkboxes on chart legend |

## Responsive breakpoints

| Breakpoint | Layout |
|-----------|--------|
| ≥ 1280px | Full sidebar + content + slide-over panel |
| 1024–1279px | Icon-only sidebar (tooltips on hover) |
| 768–1023px | Hamburger sidebar (overlay) |
| < 768px | Switch to mobile layout (bottom tabs) |

## Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `⌘K` | Open command palette |
| `⌘N` | Record new transaction |
| `⌘/` | Focus search |
| `Esc` | Close modal / slide-over |
| `↑ ↓` | Navigate holdings table rows |
| `Enter` | Open position detail for selected row |
| `←` | Close slide-over panel |
