---
title: Investment Portfolio Module Blueprint
type: feature-spec
category: investment-portfolio
status: draft
version: 1.0.0
tags: [investment, portfolio, crm, proposal, compliance, retail, advisory, b2c, b2b]
ai_optimized: true
search_keywords: [portfolio, investment, assessor, carteira, advisor, crm, proposal, suitability, rebalancing, benchmark]
related:
  - 02-architecture/service-architecture.md
  - 03-features/personal-finance/personal-finance-overview.md
  - 03-features/billing/billing-blueprint.md
  - 03-features/security/resource-ownership/resource-ownership.md
  - 04-domain/domain-model.md
last_updated: 2026-02-26
---

# Investment Portfolio Module Blueprint

## Executive Summary

The investment portfolio module delivers end-to-end portfolio management across two distinct but architecturally unified segments:

- **Retail (B2C)** — Individual investors building, tracking, and analyzing their own real or draft investment portfolios.
- **Advisory (B2B/Wholesale)** — Financial advisors (assessores de investimentos) managing portfolios on behalf of multiple clients, with CRM, proposal generation, compliance documentation, and white-label client reporting.

The module is designed to be the Brazilian-market "Orion equivalent" that does not exist today: an independent, broker-agnostic stack combining portfolio consolidation + CRM + proposal workflow + suitability compliance in a single integrated product.

---

## Market Context (2026)

### Global Competitive Landscape

**Retail reference players:**
| Platform | Key Differentiator |
|---|---|
| Empower (US) | Deepest free aggregation + retirement simulator |
| Sharesight | 240+ brokers, 40 exchanges — best multi-market tracker |
| Kubera | Only platform natively handling crypto, real estate, PE, domains |
| Portfolio Visualizer | Backtesting, Monte Carlo, factor analysis — DIY quant |
| Wealthfront / Betterment | Automated tax-loss harvesting at retail scale |

**Advisory reference players:**
| Platform | Key Differentiator |
|---|---|
| Orion Advisor Solutions | All-in-one: portfolio + CRM + planning + compliance (5.8T AUA) |
| SS&C Black Diamond | Industry benchmark for white-label client portal UX |
| Addepar | Unmatched depth for alternative assets and family offices |
| Nitrogen (Riskalyze) | Quantified risk number (1-99) — standard suitability language |
| Vise | AI-individualized portfolios per client at scale |
| Altruist "Hazel" | First agentic AI for autonomous tax planning and rebalancing |

### Brazilian Competitive Landscape

**Retail (B2C):**
| Platform | Key Differentiator |
|---|---|
| Kinvo | Deepest feature set for intermediate investors; come-cotas, IR calc |
| Gorila | Best UX for beginners; R$100M raised; GorilaVIEW B2B product |
| TradeMap | Only multi-broker with integrated order execution |
| Nu Invest | Massive distribution via Nubank base (100M+ clients) |

**Advisory (B2B):**
| Platform | Key Differentiator |
|---|---|
| Smartbrain | Market leader — 127k portfolios/day, R$100B tracked, AI Data Reader |
| Gorila B2B | White-label consolidation; equity-stake model with partner offices |
| XP (proprietary) | Largest AAI network in Brazil; full ecosystem lock-in |
| BTG Pactual (proprietary) | Proposal simulator + reports; strong private/wealth segment |
| Ability Invest | Compliance-first: KYC/AML/PLD + CVM suitability workflow |

### Identified Market Gaps (the opportunity)

1. **No independent all-in-one advisory stack in Brazil.** Advisors combine broker platform (XP/BTG) + Smartbrain/Gorila for consolidation — no integrated CRM + portfolio + proposal + compliance exists independently of a custodian.
2. **No draft-vs-real portfolio proposal workflow** with branded PDF generation in Brazilian independent tooling.
3. **No financial planning engine** integrated with a portfolio tracker (goal-based investing, retirement simulation) — Brazilian trackers excel at looking backward, not forward.
4. **No what-if / stress testing** for Brazilian macro events (Copom, IPCA shocks, BRL volatility) in retail or advisory tools.
5. **Incomplete tax automation** for crypto (especially DEX), offshore assets under Lei 14.754/2023, and complex HNW structures (FIP, holdings).
6. **No ESG scoring** for Brazilian assets (FIIs, domestic equities, funds) in any existing platform.
7. **Agentic AI for BR portfolios** — event-driven rebalancing (Copom, IPCA), automated tax-loss harvesting — does not exist in the Brazilian market.

---

## Goals

- Deliver a Retail portfolio tracker with Brazilian-native tax features, benchmarks, and Open Finance integration.
- Deliver an Advisory platform with CRM, multi-client management, proposal workflow (draft vs. real), suitability/compliance, and white-label client portal.
- Be broker-agnostic and custodian-agnostic — aggregate data from B3, Open Finance, and manual entry equally.
- Build a shared data layer and domain model that serves both segments without duplicating core logic.
- Position the platform as the only independent Brazilian advisory stack integrating the full workflow: consolidation → insight → proposal → compliance → client report.

## Non-Goals (v1)

- Actual order execution (routing to brokers/custodians) — proposals generate recommendations only.
- Fund administration (CVM gestora licensing is out of scope).
- Full tax return filing — platform generates DARF input data, not the return itself.
- DREX / tokenized assets (tracked as future milestone).
- International multi-currency portfolio management beyond BRL + USD (deferred).

---

## Segment Definitions

### Retail (B2C)

**Who:** Individual investors (pessoa física) who want to:
- Consolidate positions across multiple brokerages and asset classes.
- Track portfolio performance against Brazilian benchmarks (CDI, IPCA, Ibovespa, IFIX).
- Build and compare draft portfolios before committing capital.
- Understand tax obligations (IR, DARF, come-cotas).
- Set financial goals and simulate how the current portfolio progresses toward them.

**Profile levels:**
- **Beginner:** Simple allocation view, benchmark comparison, goal tracking.
- **Intermediate:** Performance attribution, benchmark deviation, IR calculator, dividend tracking.
- **Advanced:** Monte Carlo simulation, what-if stress testing, rebalancing alerts, tax-loss harvesting alerts.

### Advisory (B2B/Wholesale)

**Who:** Assessores de Investimentos (AAI) registered with ANCORD/CVM, independent wealth managers, multi-family offices, and boutique asset managers who:
- Manage portfolios for multiple clients across multiple custodians.
- Need to produce client-facing proposals, reports, and compliance documentation.
- Operate independently of a single broker ecosystem (or supplement broker tools with independent analytics).
- Require suitability documentation to comply with CVM/ANBIMA regulations.

**Advisory tiers:**
- **Solo advisor:** Individual AAI managing 20–200 clients.
- **Advisory office:** Team of advisors sharing a client base and model portfolio library.
- **Multi-family office:** Complex structures — multiple entities, offshore positions, alternative assets per client.

---

## Bounded Context and Ownership

`investment-portfolio` owns:
- Portfolio entities (holdings, positions, transactions, asset classes)
- Draft portfolios and proposal workflows
- Model portfolio library (advisory only)
- Suitability profiles and compliance records (advisory only)
- CRM client records (advisory only)
- Performance metrics, benchmarks, and attribution
- Tax calculation engine (IR, come-cotas, DARF)
- Alert and notification rules (rebalancing drift, tax events)
- Client portal content (advisory white-label)
- Open Finance investment data ingestion

`investment-portfolio` does **not** own:
- Authentication and identity (security module)
- Authorization policy evaluation (security module)
- Account billing and plan access (billing module)
- Order execution (deferred — no custodian routing in v1)
- Raw Open Finance provider adapters (financial-data module owns adapters; this module consumes events)

---

## Core Domain Model

### Shared Entities (Retail + Advisory)

```
Portfolio
  id, ownerAccountId, name, type (REAL | DRAFT), currency, createdAt

Holding
  id, portfolioId, assetId, quantity, averageCost, custodianId, updatedAt

Transaction
  id, portfolioId, assetId, type (BUY | SELL | DIVIDEND | SPLIT | BONUS | TRANSFER_IN | TRANSFER_OUT),
  quantity, unitPrice, totalValue, currency, tradeDate, settlementDate, taxableEvent

Asset
  id, ticker, name, assetClass, exchange, currency, isinCode, bdrUnderlying, assetType
  (STOCK | ETF | FII | BDR | FIXED_INCOME | TESOURO_DIRETO | FUND | CRYPTO | PRIVATE)

PerformanceSnapshot
  id, portfolioId, date, totalValue, totalCost, gainLoss, gainLossPct,
  benchmarkValues{CDI, IPCA, IBOV, IFIX}, twrReturn, mwrReturn

TaxRecord
  id, portfolioId, assetId, year, month, taxableGain, taxLoss, darf, comeCotas,
  exemptSaleVolume, exemptionApplied

RebalancingAlert
  id, portfolioId, triggeredAt, alertType (DRIFT | TAX_LOSS | OPPORTUNITY), payload

Goal
  id, portfolioId, name, targetAmount, targetDate, currentProgress, status
```

### Advisory-Only Entities

```
AdvisoryClient
  id, advisorAccountId, personalData{name, cpf, email, phone}, riskProfile, suitabilityId,
  kycStatus, onboardingDate, lastContactDate, aum, status

AdvisoryPortfolio
  id, advisorAccountId, clientId, portfolios[], managedSince, notes

SuitabilityProfile
  id, clientId, assessmentDate, expiryDate, riskScore, riskCategory
  (CONSERVATIVE | MODERATE | MODERATE_AGGRESSIVE | AGGRESSIVE | SUPER_AGGRESSIVE),
  investmentHorizon, liquidityNeed, incomeNeed, rawResponses, providerRef, regulatoryVersion

ModelPortfolio
  id, advisorAccountId, name, description, allocationTargets[], riskCategory,
  recommendedHorizon, benchmarkId, version, lastReviewedAt

Proposal
  id, advisorAccountId, clientId, status (DRAFT | SENT | ACCEPTED | REJECTED | EXPIRED),
  currentPortfolioSnapshot, proposedPortfolioConfig, gapAnalysis,
  complianceDocRef, sentAt, acceptedAt, expiresAt

ComplianceRecord
  id, clientId, proposalId, recordType (SUITABILITY_MATCH | KNOW_YOUR_CLIENT | DISCLOSURE),
  advisorId, generatedAt, documentUrl, signature, auditHash

CRMNote
  id, clientId, advisorId, content, contactType (MEETING | CALL | EMAIL | WHATSAPP), createdAt

CRMTask
  id, clientId, advisorId, dueDate, title, status (PENDING | DONE | SNOOZED), priority

ClientPortalAccess
  id, clientId, accessToken, expiresAt, whitelabelThemeId, permissions[]
```

---

## Feature Catalog

### Retail (B2C) Features

#### R1 — Portfolio Consolidation
- Connect custodians via Open Finance (B3/Área do Investidor API, Open Investment).
- Manual position entry and CSV/OFX import as fallback.
- Support all Brazilian asset classes: stocks, ETFs, FIIs, BDRs, Tesouro Direto, fixed income (CDB, LCI, LCA, CRI, CRA, debêntures), funds, crypto.
- Automatic daily position and price sync.
- Multi-portfolio support (e.g., separate portfolios per custodian or per goal).

#### R2 — Draft Portfolio
- Create a simulated portfolio without real money — a "what if I invested like this?" sandbox.
- Compare draft vs. real portfolio side by side.
- Support copy-from-real and share-as-link for draft portfolios.

#### R3 — Performance & Benchmarks
- Time-weighted return (TWR) and money-weighted return (MWR) calculations.
- Performance charted against CDI, IPCA, Ibovespa, IFIX, IMA-B.
- Period selectors: 1M, 3M, 6M, YTD, 1Y, 3Y, 5Y, custom.
- Asset-class breakdown: by type, by sector, by geography.
- Top gainers and losers within the portfolio.

#### R4 — Tax Engine (Brazil-native)
- **Come-cotas:** Automatic calculation for fixed-income funds (May and November levy).
- **IR renda variável:** Monthly taxable gain/loss per asset, exemption threshold (BRL 20k/month for stocks), DARF generation input.
- **Average cost tracking:** Including corporate events (splits, reverse splits, bonus shares, subscriptions).
- **FII dividend tracking:** Monthly income tracking; IR exemption for qualifying FIIs.
- **Crypto IR:** Monthly gain/loss tracking; exemption threshold (BRL 35k/month); OECD CARF-aligned reporting for DeCripto (July 2026).
- **Offshore + Lei 14.754/2023:** Asset tracking for positions held abroad; annual income provisioning.

#### R5 — Goals & Planning
- Named financial goals (retirement, education, travel, house).
- Target amount + target date inputs.
- Simulation engine: projects current portfolio forward at configurable return assumptions.
- Gap analysis: how much more to invest monthly to reach the goal on time.
- Progress ring / timeline visualization.

#### R6 — What-If Scenario Analysis
- Macro stress tests: Selic +200bps, Ibovespa -30%, BRL/USD +40%, IPCA spike.
- Historical scenario replay: apply 2008 crisis, 2020 COVID crash, 2013 taper tantrum to current portfolio.
- Custom scenario builder: user-defined allocation change + market assumption.
- Drawdown visualization and maximum loss estimate.

#### R7 — Rebalancing Assistant
- User sets target allocation (e.g., 40% fixed income, 35% equities, 15% FIIs, 10% crypto).
- Drift detection: alerts when actual allocation deviates beyond threshold.
- Buy/sell recommendation list to restore target (no execution — advisory only).
- Tax-aware rebalancing suggestions: prefer buying underweight assets over selling overweight.

#### R8 — Dividend & Income Tracker
- Monthly income calendar: upcoming FII distribution dates, CDB maturities, fund redemptions.
- Projected annual passive income at current portfolio composition.
- Income by asset and by asset class breakdown.
- Reinvestment simulator: what if all dividends were reinvested?

#### R9 — Alerts & Notifications
- Significant portfolio movement (e.g., position drops > X% in a day).
- Rebalancing drift threshold crossed.
- Tax event approaching (come-cotas weeks ahead, DARF due date).
- Goal progress milestone (e.g., "You're 50% of the way to your retirement goal").
- Dividend received / maturity event.

---

### Advisory (B2B/Wholesale) Features

#### A1 — CRM Client Management
- Full client registry: personal data, CPF, contact info, onboarding date, status.
- Activity timeline: notes, calls, meetings, WhatsApp interactions logged.
- Task management: follow-up reminders, review deadlines, compliance renewal alerts.
- Client segmentation: by AUM, by risk profile, by last contact date, by product type.
- AUM dashboard: total book of business, AUM per client tier, AUM evolution over time.

#### A2 — Multi-Client Portfolio Consolidation
- Manage N client portfolios from a single advisor dashboard.
- Aggregate view of total book AUM, performance, and allocation drift.
- Per-client drilldown with full portfolio detail (same engine as Retail R1–R4).
- Multi-custodian support: B3, XP, BTG, Rico, and manual positions in a single client view.
- Offline/manual position support for assets not available via Open Finance (private equity, real estate, offshore).

#### A3 — Suitability & Compliance
- Suitability questionnaire (CVM/ANBIMA compliant): quantified risk score and risk category assignment.
- Suitability profile storage with expiry tracking and renewal alerts (annual re-assessment).
- Open Finance suitability portability: accept externally issued suitability profiles when ANBIMA API is available (2026 roadmap).
- KYC document management: identity verification status, PLD/AML checklist per client.
- Compliance audit trail: every portfolio recommendation linked to a suitability record.
- Regulatory reporting export: CVM-ready data extracts.

#### A4 — Model Portfolio Library
- Advisor-created model portfolios (conservative, moderate, aggressive, thematic).
- Allocation targets by asset class, sector, and individual security.
- Version history: see how a model has evolved over time and which clients are on which version.
- Benchmark assignment per model (CDI+X, IPCA+X, Ibovespa, custom).
- Drift monitoring: which clients have drifted from their assigned model.

#### A5 — Proposal Workflow (Draft vs. Real)
- **Step 1 — Current State:** Import client's current portfolio (via Open Finance, PDF statement scan via AI, or manual).
- **Step 2 — Proposed Portfolio:** Select a model or build a custom allocation for the client.
- **Step 3 — Gap Analysis:** Side-by-side comparison across 5 dimensions:
  - Asset allocation change (current vs. proposed)
  - Risk level change (risk score current vs. proposed)
  - Expected return change (based on historical model returns)
  - Cost/fee comparison (expense ratios, advisory fee impact)
  - Estimated tax impact of transition trades
- **Step 4 — Suitability Check:** System validates that proposed portfolio is within client's suitability risk category. Flags mismatches.
- **Step 5 — Proposal Document:** Generate a white-labeled PDF/interactive presentation with the advisor's branding, gap analysis visualization, and compliance disclosure language.
- **Step 6 — Client Delivery:** Send proposal via email or client portal link. Track open/accept/reject status.
- **Step 7 — Acceptance Recording:** On acceptance, proposal becomes the reference allocation. Compliance record auto-generated.

#### A6 — White-Label Client Portal
- Secure client-facing portal branded with the advisory office's logo and colors.
- Client view: portfolio summary, performance chart, benchmark comparison, allocation breakdown, income tracker.
- Document vault: proposals, compliance disclosures, statements, tax records.
- Secure messaging thread with the advisor.
- Mobile-responsive; biometric login supported on mobile app.

#### A7 — Rebalancing Orchestration
- Bulk drift detection: see all clients deviating from their model beyond a configured threshold.
- Rebalancing priority queue: sort clients by drift severity, last rebalanced date, tax position.
- Trade recommendation list per client: buy/sell list to restore model alignment.
- Tax-aware rebalancing: prefer harvesting losses when available; flag wash-sale risk.
- Proposal auto-generation from rebalancing recommendations (connects to A5).

#### A8 — Performance Reporting
- Per-client and aggregate book-of-business reporting.
- Performance attribution: allocation effect vs. selection effect (Brinson model).
- Benchmark deviation analysis per client and per model.
- Customizable reporting periods and benchmark choices.
- Branded PDF report generation for client delivery (monthly, quarterly, annual).
- IR-adjusted performance display (net-of-tax return).

#### A9 — Fee Management
- Advisory fee configuration: flat fee, AUM percentage, tiered AUM percentage.
- Automated fee calculation per client per billing period.
- Fee invoice generation integrated with billing module.
- Performance fee support (high-water mark, hurdle rate) for qualified structures.

#### A10 — AI Copilot (Advisory)
- Natural language queries: "Which clients have equity exposure above 70% and haven't been contacted in 60 days?"
- Client briefing generation: "Prepare a monthly review summary for client João Silva with the Copom impact on his fixed-income allocation."
- Rebalancing reasoning: AI explains why a specific rebalancing action is recommended in plain language for client communication.
- Tax opportunity detection: proactively surfaces clients with harvestable losses or approaching DARF thresholds.

---

## Brazilian Regulatory Requirements

### Key Regulations

| Regulation | Applicability | Impact |
|---|---|---|
| CVM Resolution 178 (AAI) | Advisory segment | AAI registration and conduct — audit trail, suitability linkage |
| CVM Instrução 539 (Suitability) | Advisory segment | Risk classification before any recommendation |
| ANBIMA Código de Distribuição | Advisory segment | Product distribution governance |
| PLD/AML (Anti-Money Laundering) | Advisory segment | KYC documents, suspicious activity flags |
| Open Finance Phase 4 (Open Investment) | Retail + Advisory | Data sharing APIs for investment positions |
| CVM Resolution 210/229 (Portability) | Retail + Advisory | Digital asset portability between custodians (effective Jan 2026) |
| B3 Área do Investidor APIs | Retail + Advisory | Official source for BV custody positions |
| Lei 14.754/2023 (Offshore assets) | Retail (advanced) | Annual income provisioning for offshore positions |
| DeCripto / OECD CARF (Jul 2026) | Retail (crypto) | New crypto reporting framework — monthly exchange reporting |
| LGPD | Both | Personal data minimization, consent, export/delete |

### Open Finance Integration Roadmap

- **Phase 1 (MVP):** B3 Área do Investidor API — equities, FIIs, BDRs, ETFs.
- **Phase 2:** Open Investment APIs — positions at XP, BTG, Rico, Itaú, Bradesco.
- **Phase 3:** ANBIMA portable suitability API (when available, 2026).
- **Phase 4:** Asset portability orchestration — guide and track portfolio migration between custodians (CVM Res. 210).

---

## Feature Flag Strategy

All features are gated by segment (retail vs. advisory) via the billing plan access system:

```
portfolio:retail:consolidation          — Retail plan R1
portfolio:retail:draft_portfolio        — Retail plan R2
portfolio:retail:tax_engine             — Retail Pro plan R4
portfolio:retail:goals_planning         — Retail Pro plan R5
portfolio:retail:what_if_scenarios      — Retail Pro plan R6
portfolio:advisory:crm                  — Advisory plan A1
portfolio:advisory:multi_client         — Advisory plan A2
portfolio:advisory:suitability          — Advisory plan A3
portfolio:advisory:model_portfolios     — Advisory plan A4
portfolio:advisory:proposals            — Advisory plan A5
portfolio:advisory:client_portal        — Advisory plan A6
portfolio:advisory:ai_copilot          — Advisory Pro plan A10
```

---

## Architecture Considerations

### Service Boundaries

- **portfolio-service** (new) — Core portfolio domain: holdings, transactions, performance calculations, tax engine, goals.
- **advisory-service** (new or collocated) — CRM, proposal workflow, model portfolios, compliance records, client portal.
- **financial-data integration** — Consumes asset prices and economic indicators via internal events (Kafka); does not own this data.
- **open-finance adapter** — Separate adapter consuming B3 and Open Finance APIs, publishing `InvestmentPositionSynced` events to portfolio-service.

### Key Integration Points

- **Billing module** → plan access gates via `PlanAccessSnapshot` (determines which features are enabled per account).
- **Security module** → account context, RBAC, and resource ownership for all portfolio and advisory resources.
- **Comms module** → proposal delivery emails, alert notifications, client portal invitations.
- **LLM orchestrator** → AI copilot queries, briefing generation, tax opportunity detection (A10).
- **Asset service** → document storage for compliance records and proposal PDFs (Cloudflare R2).

### Data Privacy (LGPD)

- Advisory client personal data (CPF, name, contact) stored encrypted at rest.
- Consent records required before Open Finance data ingestion.
- Data export and deletion endpoints required for LGPD compliance.
- Client portal access logs retained for regulatory audit purposes.

---

## Rollout Plan

### Phase 0 — Foundation
- Core domain model: `Portfolio`, `Holding`, `Transaction`, `Asset`.
- Manual position entry (CRUD).
- Basic performance calculation (total value, gain/loss, simple return).
- Segment flag infrastructure (retail vs. advisory account types).

### Phase 1 — Retail MVP
- B3 Área do Investidor API integration (equities, FIIs, BDRs, ETFs).
- Performance vs. CDI and Ibovespa benchmarks.
- Asset allocation breakdown (by class, sector).
- Come-cotas calculation (basic).
- IR renda variável — monthly gain/loss + DARF input.

### Phase 2 — Retail Pro
- Multi-custodian via Open Finance (Phase 4 APIs).
- Draft portfolio builder with side-by-side comparison.
- Goals and retirement simulation engine.
- Full tax engine (crypto, offshore, FII dividends, average cost with corporate events).
- Dividend income calendar and passive income projections.
- What-if scenario analysis (macro stress tests + historical replays).
- Rebalancing drift alerts.

### Phase 3 — Advisory Foundation
- Advisory account type and CRM client management (A1).
- Multi-client portfolio consolidation (A2).
- Suitability questionnaire and profile storage (A3).
- Model portfolio library (A4).

### Phase 4 — Advisory Proposal Workflow
- Proposal workflow end-to-end: current → proposed → gap analysis → document → send → accept (A5).
- Compliance record auto-generation linked to suitability and proposal.
- White-label client portal (A6).
- Branded PDF report generation.

### Phase 5 — Advisory Operations
- Rebalancing orchestration: bulk drift detection, priority queue, trade recommendations (A7).
- Performance attribution reporting (Brinson model) (A8).
- Fee management and invoice integration with billing module (A9).
- PDF proposal document scanning via AI (statement import for current state in proposal workflow).

### Phase 6 — AI & Advanced Features
- AI copilot for advisory natural language queries (A10).
- Client briefing generation via LLM orchestrator.
- Tax opportunity proactive alerts.
- Agentic rebalancing suggestions (event-driven: Copom, IPCA release, earnings).
- ANBIMA portable suitability API integration (when available).
- Asset portability orchestration (CVM Res. 210 — guide migrations between custodians).

---

## Open Decisions

- **Proposal document format:** PDF-only vs. interactive web proposal (link-based) — interactive is higher value but higher dev cost.
- **Suitability assessment ownership:** Build proprietary questionnaire engine vs. integrate third-party (Nitrogen-style) for advisory segment.
- **AI copilot scope in v1:** Predefined query templates vs. full free-text natural language — full NL has higher RAG/LLM complexity.
- **Crypto tracking scope:** On-chain (DEX) positions require wallet address input — privacy and data sensitivity to define.
- **Offshore asset tracking scope:** Manual-only for v1 or integrate with offshore custodians (deferred).
- **Order execution integration:** Whether Phase 6+ includes routing rebalancing recommendations directly to broker APIs (significant regulatory and compliance scope expansion).
- **Multi-advisor office model:** Whether an advisory office account supports role hierarchy (office owner + advisor members) or is always solo-advisor scoped in v1.
- **Data retention for tax records:** Minimum 5 years required by Receita Federal — define archival vs. hot-storage strategy.
- **ESG scoring:** In-house score construction vs. third-party data feed (B3 Bridgewise, Refinitiv ESG).

## Suggested Next Artifacts

- `docs/03-features/investment-portfolio/retail-portfolio.md` (retail features, scenarios, and behaviors)
- `docs/03-features/investment-portfolio/advisory-proposal.md` (proposal workflow — scenarios and behaviors)
- `docs/03-features/investment-portfolio/tax-engine-spec.md` (IR calculation rules, edge cases)
- `docs/03-features/investment-portfolio/open-finance-integration.md` (B3 + Open Investment adapter design)
- `docs/03-features/investment-portfolio/DECISIONS.md` (trade-off records) ✅
- `docs/03-features/investment-portfolio/market-research-2026.md` (source material and competitive analysis) ✅
