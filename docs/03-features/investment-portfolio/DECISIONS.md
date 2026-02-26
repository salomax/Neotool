---
title: Investment Portfolio — Open Decisions
type: decisions
category: investment-portfolio
status: pending
version: 1.0.0
tags: [decisions, open-decisions, investment, portfolio, advisory, retail]
last_updated: 2026-02-26
---

# Investment Portfolio — Open Decisions

> Decisions that must be resolved before or during implementation of specific phases.
> Update the `status` field of each decision as they are resolved.

---

## Priority Summary

| # | Decision | Blocks | Urgency |
|---|---|---|---|
| D7 | Multi-advisor office model | Phase 3 schema — painful migration if deferred | **Resolve before Phase 3** |
| D2 | Suitability assessment ownership | Full architecture of A3 | **Resolve before Phase 3** |
| D1 | Proposal document format | Architecture of A5 and A6 | **Resolve before Phase 4** |
| D4 | Crypto tracking scope | Scope of R4 (tax engine) | **Resolve before Phase 2** |
| D8 | Data retention for tax records | Schema of `TaxRecord` and `Transaction` | **Resolve before Phase 2** |
| D3 | AI copilot scope | Architecture of A10 | Low urgency — Phase 6 |
| D5 | Offshore asset tracking scope | Scope of R4 only (manual is safe default) | Low urgency |
| D6 | Order execution integration | Nothing blocked — strategic decision only | Can be deferred indefinitely |
| D9 | ESG scoring | Nothing blocked — Phase 6+ | Low urgency |

---

## D1 — Proposal Document Format

**Status:** `pending`
**Blocks:** Phase 4 (A5 — Proposal Workflow, A6 — Client Portal)

### Options

#### Option A — PDF-only
| Pros | Cons |
|---|---|
| Simple to implement (server-side PDF library) | Static — data may be outdated if market moves between generation and presentation |
| Predictable and auditable output | No open tracking without external integration |
| Easy to archive and digitally sign | E-signature requires external integration |
| Universally accepted by regulators as compliance evidence | Lower wow factor for advisors |

#### Option B — Interactive Web Proposal (link-based)
| Pros | Cons |
|---|---|
| High perceived value — data updated in real time at presentation | 3–4x higher development cost |
| Built-in open/accept tracking | Requires authenticated link with expiry — security surface grows |
| Native e-signature possible | Requires A6 (client portal) as a prerequisite |
| Strong competitive differentiator | Harder to version and archive for compliance |

### Domain Impact
- **Option A:** `Proposal.documentUrl` points to static PDF stored in R2.
- **Option B:** `Proposal` needs `accessToken`, `expiresAt`, `viewedAt`, `interactionLog`.

### Recommendation
PDF in v1, interactive as a follow-on. The two are not mutually exclusive — PDF can be generated from the same data as an interactive view.

### Decision
> _Pending._

---

## D2 — Suitability Assessment Ownership

**Status:** `pending`
**Blocks:** Phase 3 (A3 — Suitability & Compliance)

### Options

#### Option A — In-house engine
| Pros | Cons |
|---|---|
| Full control over UX and questionnaire flow | Questions require regulatory validation (ANBIMA/CVM) |
| Questions adapted to Brazilian context and CVM categories | Risk of miscalibrated scoring without scientific base |
| No vendor lock-in or license cost | Ongoing maintenance and update cost |
| Can evolve to ANBIMA Open Finance suitability portability | More complex regulatory audit trail |

#### Option B — Third-party integration (Nitrogen-style)
| Pros | Cons |
|---|---|
| Much faster time-to-market | Recurring license cost |
| Psychometrics and scientific validation already built | Questionnaire in English — requires translation and BR adaptation |
| Score calibrated on thousands of users | Score may not map cleanly to CVM categories (conservative, moderate, aggressive) |
| Recognized methodology among advisors | Dependency on third-party API availability |

### Domain Impact
- **Option A:** `SuitabilityProfile` has `rawResponses`, `riskScore` calculated internally.
- **Option B:** `SuitabilityProfile` has `providerRef`, `externalScore`, mapped to internal categories.

### Regulatory Impact
Both options require documenting assessment criteria for CVM/ANBIMA. Option A gives more control but demands more justification work upfront.

### Recommendation
In-house engine. The Brazilian regulatory context is too specific (CVM categories, local product types, ANBIMA compliance language) for a US-calibrated tool to be used as-is.

### Decision
> _Pending._

---

## D3 — AI Copilot Scope in v1

**Status:** `pending`
**Blocks:** Phase 6 (A10 — AI Copilot)

### Options

#### Option A — Predefined query templates
| Pros | Cons |
|---|---|
| Predictable and testable — no hallucination risk | Rigid — advisor cannot formulate queries outside templates |
| Controlled LLM cost | Template maintenance overhead over time |
| Guided UX avoids frustration with no-result queries | Lower wow factor |
| Faster to deliver | |

#### Option B — Free-text natural language (NL)
| Pros | Cons |
|---|---|
| High perceived value — advisor types any question | RAG pipeline complexity (indexing advisor data + market data) |
| Strong competitive differentiator | Hallucination risk in financial queries — can surface incorrect recommendations |
| Scales to new use cases without new code | Higher token cost per query; latency |

### Domain Impact
- **Option A:** `CopilotQuery` with `templateId`, `params`, `result`.
- **Option B:** `CopilotQuery` with `freeText`, `vectorContext`, `llmResponse`, `confidenceScore`.

### Risk Note
Incorrect financial outputs carry regulatory and reputational consequences. Any NL implementation requires guardrails, disclaimer display, and confidence thresholds.

### Recommendation
Templates in v1, evolving to NL. Hybrid is the safest path: suggested templates + open field with prominent disclaimer.

### Decision
> _Pending._

---

## D4 — Crypto Tracking Scope

**Status:** `pending`
**Blocks:** Phase 2 (R4 — Tax Engine)

### Options

#### Option A — Centralized exchanges only (CEX)
| Pros | Cons |
|---|---|
| Well-documented APIs (Binance, Coinbase, Mercado Bitcoin) | Ignores all DeFi activity |
| Reliable data | Advanced crypto users have an incomplete portfolio view |
| More straightforward IR mapping | CEX BR requires secure API key management |
| Aligned with DeCripto reporting scope (exchange-reported) | |

#### Option B — On-chain (wallets + DEX)
| Pros | Cons |
|---|---|
| Full coverage — DeFi, NFTs, staking, LP positions | Much higher technical complexity (multichain: ETH, SOL, BTC, etc.) |
| Unique differentiator in the Brazilian market | Wallet address is highly sensitive privacy data |
| Aligned with OECD CARF intent | Risk of inaccurate data (illiquid tokens, NFTs without price feeds) |
| | High infra cost for on-chain indexing |

### Regulatory Impact
DeCripto (Receita Federal, July 2026) requires reporting of exchange-based operations — CEX-only already satisfies this. Receita Federal has no clear guidance for DEX reconciliation yet.

### Recommendation
CEX-only in v1 (Phase 2). On-chain as opt-in in Phase 5+, with manual wallet address entry and explicit privacy disclaimer.

### Decision
> _Pending._

---

## D5 — Offshore Asset Tracking Scope

**Status:** `pending`
**Blocks:** Phase 2 (R4 — Tax Engine, offshore component)

### Options

#### Option A — Manual entry only in v1
| Pros | Cons |
|---|---|
| Zero integration cost | Data can become stale |
| Covers most cases (user knows what they hold) | Inferior experience for HNW with large foreign positions |
| Sufficient for Lei 14.754 compliance (annual declaration, not daily) | Asset prices require manual update or external ticker lookup |

#### Option B — Integrate offshore custodians
| Pros | Cons |
|---|---|
| Accurate and up-to-date data | Integration complexity (Interactive Brokers, Schwab, Fidelity — each has different APIs) |
| Differentiator for HNW/private wealth segment | Ongoing maintenance cost |
| Eliminates manual entry friction | Regulatory questions around accessing non-resident data |

### Regulatory Impact
Lei 14.754/2023 requires reporting the value of offshore assets as of December 31 annually. Manual entry at year-end is sufficient for basic compliance.

### Recommendation
Manual in v1, with custodian name and currency fields. Automation is Phase 5+.

### Decision
> _Pending._

---

## D6 — Order Execution Integration

**Status:** `pending`
**Blocks:** Nothing currently — strategic decision only.

### Options

#### Option A — Recommendations only (permanent core scope)
| Pros | Cons |
|---|---|
| Safe and controlled scope | Advisor still must execute manually on broker platforms — part of the value is not captured |
| No liability for execution errors | |
| Outside the regulatory scope of fund manager license (CVM 558) | |
| Deliverable at any phase | |

#### Option B — Route recommendations to broker APIs
| Pros | Cons |
|---|---|
| Eliminates advisor friction from recommendation to execution | Requires broker credentials management |
| Massive competitive differentiator | Fiduciary liability for execution errors |
| Enables full automation | May trigger CVM 558 scope (portfolio management license) — serious legal risk |
| | Each broker has a different API; maintenance cost is high |

### Regulatory Impact
Routing orders on behalf of clients may require a CVM 558 (portfolio manager) license depending on the model. This is a significant legal scope expansion that requires formal legal review before any commitment.

### Recommendation
Recommendations-only as the permanent core product. Execution as a separate product if and when there is regulatory appetite and legal clearance.

### Decision
> _Pending._

---

## D7 — Multi-Advisor Office Model

**Status:** `pending`
**Blocks:** Phase 3 schema — deferring after implementation will require a painful data migration.

### Options

#### Option A — Solo-advisor scoped only in v1
| Pros | Cons |
|---|---|
| Simple schema — `advisorAccountId` is always the logged-in user | Most offices have 2–10 advisors — N independent accounts with no consolidated view |
| No conflict with existing resource-ownership model | Each advisor recreates the same model portfolios independently |
| Fast to deliver | Unviable for multi-family offices |

#### Option B — Office hierarchy (owner + advisor members)
| Pros | Cons |
|---|---|
| Reflects real market structure | Resource ownership complexity (does a client belong to the office or the advisor?) |
| Consolidated AUM dashboard for the office | Cross-access permissions |
| Shared model portfolio library across office members | Multi-seat billing integration |
| | More complex to model correctly upfront |

### Domain Impact
- **Option A:** `AdvisoryClient.advisorAccountId` is a direct user reference.
- **Option B:** Requires `AdvisoryOffice`, `OfficeMembership`, and `officeId` on `AdvisoryClient`, `AdvisoryPortfolio`, `ModelPortfolio`. Access control needs two levels: office-owner vs. advisor-member.

### Billing Impact
- **Option A:** Plan per individual advisor.
- **Option B:** Plan per office with N seats — requires multi-seat integration with the billing module.

### Recommendation
Solo in v1, but model the schema with a **nullable `officeId`** from day one. This avoids a painful migration when the office model is introduced.

### Decision
> _Pending._

---

## D8 — Data Retention for Tax Records

**Status:** `pending`
**Blocks:** Phase 2 schema design (`TaxRecord`, `Transaction`).

### Options

#### Option A — Hot storage only (PostgreSQL)
| Pros | Cons |
|---|---|
| Simple — everything in the same database | Storage cost grows indefinitely |
| Tax history queries work without joins to external systems | `TaxRecord` and `Transaction` tables grow significantly over 5 years at scale |
| No recall latency | May impact performance of recent queries as tables grow |

#### Option B — Tiered archival strategy
| Pros | Cons |
|---|---|
| Lower long-term cost | Implementation complexity (cold storage in R2/S3 + recall mechanism) |
| PostgreSQL stays lean and performant | Latency when user queries old data |
| Receita Federal requires 5 years — old data is rarely queried | Harder to run analytical queries across historical data |

### Recommendation
Hot storage in v1. Define an archival policy when scale demands it (estimated: >100k users with 2+ years of history). Prepare the schema with an `archiveStatus` field from the start to make future tiering non-breaking.

### Decision
> _Pending._

---

## D9 — ESG Scoring

**Status:** `pending`
**Blocks:** Nothing — Phase 6+ only.

### Options

#### Option A — In-house score construction
| Pros | Cons |
|---|---|
| Full methodological control | Collecting and maintaining ESG data is expensive and labor-intensive |
| Can be calibrated to Brazilian context (Amazon impact, board diversity, etc.) | Requires a specialized team |
| No license cost | Coverage of FIIs and smaller companies would be low initially |

#### Option B — Third-party data feed (B3 Bridgewise, Refinitiv ESG)
| Pros | Cons |
|---|---|
| Much faster time-to-market | High license cost (Refinitiv, MSCI ESG can cost tens of thousands per year) |
| Already validated and recognized methodology | Data may not cover FIIs and Brazilian small caps well |
| Broader asset coverage | Vendor lock-in; opaque methodology |
| Automatic updates | |

### Recommendation
Defer entirely to Phase 6+. Low priority relative to other gaps. When the time comes, evaluate B3 Bridgewise first — most aligned with the local market.

### Decision
> _Pending._
