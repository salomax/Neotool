---
title: Invistus Mobile — High-Level Plan
type: plan
category: mobile
status: current
version: 1.0.0
tags: [mobile, react-native, expo, migration, plan, strategy, parity, feature-sliced-design]
ai_optimized: true
search_keywords: [mobile plan, mobile strategy, react native expo plan, web to mobile, mobile umbrella plan, mobile source of truth]
related:
  - 07-frontend/mobile-migration-roadmap.md
  - 07-frontend/mobile-phase-0-discovery-baseline.md
  - 07-frontend/mobile-phase-0-p0-dependency-map.md
  - 07-frontend/mobile-phase-0-p0-backlog.md
  - 07-frontend/standards/mobile-navigation.md
  - 02-architecture/mobile-architecture.md
  - 92-adr/0010-react-native-expo-mobile.md
last_updated: 2026-07-03
---

# Invistus Mobile — High-Level Plan

> **Purpose**: Single strategic source of truth for the Invistus mobile sub-project. It defines *what* we are building (one React Native + Expo app for iOS and Android at full feature parity with the product), *how* we build it (architecture, code-sharing, delivery phases, dev pipeline), and *what "good" means* (mobile-first premium UX, not a squeezed web). It sits **above** the existing Phase 0 migration docs, which remain the detailed execution layer.

## TL;DR

- **Goal**: one codebase (React Native + Expo) delivering **the same features** as web on iOS and Android, with a **mobile-first premium** experience.
- **Foundation is already accepted and scaffolded**: [ADR-0010](../../92-adr/0010-react-native-expo-mobile.md) selected RN + Expo; `mobile/` already has Expo Router, Apollo (offline cache), RN Paper, i18n, SecureStore, biometrics, and theme in place. Most screens are still placeholders.
- **Backend stays the same** (federated GraphQL). We may extend or add mobile-optimized operations, but we do not fork the domain.
- **Web responsiveness is a reference, not the truth.** Mobile hierarchy, navigation, gestures, and edit flows are redesigned for touch.
- **Scope is the full product**, not only P0. Existing docs cover auth + financial-data; this plan adds **personal-finance** and **investments** (the wealth core) to the parity map.
- **Delivery is by vertical slice**, phased P0 → P1 → P2, governed by a parity tracker.
- **Development runs through the existing `neotool-*` GitHub-issue pipeline**, extended with a new mobile implementation skill.

## Guiding Principles

These encode the product requirements for this sub-project.

1. **Same features, different experience.** Feature scope equals web. The *implementation* is native-first: React Native navigation, lifecycle, gestures, offline, and device capabilities.
2. **Web is a reference, not a contract.** Responsive web was optimized for the web's convenience. On mobile we re-decide information hierarchy, primary actions, and edit flows. Copying the desktop/responsive layout is a defect, not a shortcut.
3. **One shared backend, adapted when justified.** The federated GraphQL API is shared. When mobile needs something specific (payload shape, aggregation to cut round-trips, push tokens, deep-link resolution), we extend or add operations rather than bending the client around a web-shaped API.
4. **Share behavior and contracts, not rendering.** GraphQL operations, typed models, validation schemas, formatters, i18n keys, and headless flow logic are shared. UI components are platform-specific (MUI vs RN Paper).
5. **Mobile-first premium from P0.** The first journeys already ship native motion, gestures, haptics, biometrics, offline-first, and polished empty/loading/error states. Quality bar is set at the start, not deferred to a "polish phase".
6. **Vertical slices over layers.** Deliver end-to-end journeys (screen → GraphQL → states → telemetry) instead of horizontal "all screens, no data" passes.

## Relationship to Existing Docs (Assessment)

Phase 0 documentation already exists and is coherent, but it is **fragmented and P0-scoped** (authored 2026-02, mostly `draft`):

| Doc | Role going forward | State |
| --- | --- | --- |
| [`0010-react-native-expo-mobile.md`](../../92-adr/0010-react-native-expo-mobile.md) | Technology decision (accepted) | Keep as-is (authoritative ADR) |
| [`mobile-architecture.md`](../../02-architecture/mobile-architecture.md) | Architecture deep-dive | Keep; reconcile code-sharing section with this plan |
| [`mobile-migration-roadmap.md`](../mobile-migration-roadmap.md) | Phased delivery model | Keep as execution roadmap; this plan is its strategic umbrella |
| [`mobile-phase-0-discovery-baseline.md`](../mobile-phase-0-discovery-baseline.md) | P0 parity baseline | Keep as P0 input; superseded by the extended matrix below for scope |
| [`mobile-phase-0-p0-dependency-map.md`](../mobile-phase-0-p0-dependency-map.md) | P0 dependencies | Keep; still valid for P0 execution |
| [`mobile-phase-0-p0-backlog.md`](../mobile-phase-0-p0-backlog.md) | P0 task backlog | Keep; migrate into GitHub issues (see pipeline) |
| [`standards/mobile-navigation.md`](../standards/mobile-navigation.md) | Nav spec (describes **web** mobile nav) | Keep as reference; write a native equivalent for the app |

**Decision: complement, do not rewrite.** This document is the entry point and strategic source of truth. The Phase 0 docs remain valid detailed inputs and become the P0 execution layer beneath it. Their two structural gaps are closed here: (a) full-product scope, and (b) the mobile dev pipeline. As a follow-up, the flat `mobile-*.md` files may be relocated under this `mobile/` folder to consolidate the home (tracked as a chore, not blocking).

## Current State (July 2026)

**Already in place in `mobile/`:**

- Expo SDK 52, Expo Router with route groups `(auth)` and `(tabs)`, `+not-found`, root layout with providers.
- Apollo Client with auth link (JWT from SecureStore), retry link, error link, and **persisted offline cache** (AsyncStorage) + network detection (NetInfo).
- Providers: Auth, Apollo, i18n (i18next + expo-localization), Theme (RN Paper MD3 light/dark aligned to brand colors).
- GraphQL codegen wired; SecureStore, biometrics (`expo-local-authentication`), notifications, and deep linking dependencies installed.
- Brand assets, EAS config, Jest + RN Testing Library.

**Gaps:**

- Most screens are placeholders (dashboard, profile, settings show stub content).
- `AuthProvider` still uses a **mock** login/signup flow — real GraphQL auth not wired.
- No shared packages yet: `mobile/` **duplicates** Apollo client and i18n config instead of consuming shared modules (the `packages/*` proposed in ADR-0010 do not exist).
- Financial-data, personal-finance, and investments domains are absent from mobile routes.
- No native mobile navigation spec (existing standard describes the web responsive nav).
- No mobile-specific implementation skill in the `neotool-*` pipeline.

## Target Architecture

### Stack (per ADR-0010)

TypeScript · React Native · Expo (managed) · Expo Router · React Native Paper · Apollo Client · Zustand + Context · React Hook Form + Zod · i18next · Jest + RNTL · EAS Build/Submit.

### Feature-Sliced Design, shared mental model

Keep the same slice model as web (`shared`, `entities`, `features`, `widgets`, route layer) so domain boundaries and ownership match across platforms. UI and platform integrations stay mobile-specific.

### Code-sharing strategy (key architectural decision)

The biggest open decision. ADR-0010 proposes `packages/shared-*`; today nothing is shared and `mobile/` duplicates config. Recommended target:

- **Share:** GraphQL documents + generated types, domain/validation schemas (Zod), formatters and pure business logic, i18n message catalogs, headless flow/state logic.
- **Do not share:** rendered components, navigation, theming implementation, platform integrations.
- **Mechanism:** introduce Nx-managed shared libraries consumed by both `web/` and `mobile/`. Start **narrow** — one shared library for GraphQL operations + types and one for validation/formatters — before generalizing. Avoid a premature cross-platform UI abstraction (explicitly discouraged by the roadmap).
- **Interim:** until shared libs land, keep mobile self-contained but mirror the web's operation and schema definitions to minimize later merge cost. Treat contract drift as a tracked risk.

This decision is ratified in [ADR-0014: Web/Mobile Code-Sharing Strategy](../../92-adr/0014-web-mobile-code-sharing-strategy.md) (accepted): share behavior and contracts, not rendering; contracts are identical across web and mobile.

### Backend adaptation policy

Same federated GraphQL router and Kotlin services. Mobile-driven backend work is allowed but must be **additive and justified**:

- Aggregated/coarser queries to reduce round-trips on constrained networks.
- Payload shaping for mobile screens (avoid overfetching for battery/bandwidth).
- Push notification registration + delivery (device tokens, preferences).
- Deep-link / reset-token resolution suitable for mobile.
- Session/refresh-token lifecycle validated for long-lived mobile sessions and biometric re-auth.

Any backend change follows the standard issue → solution-design → backlog pipeline and loads `neotool-kotlin-service`.

## Feature Parity Scope (Full Product)

Priority tiers: **P0** = first usable release (core journeys), **P1** = high-value parity, **P2** = deferred depth/polish. This extends the Phase 0 baseline to the whole product surface.

| Domain | Web reference | Priority | Notes for mobile |
| --- | --- | --- | --- |
| **Shell / Home** | `/` + responsive bottom nav | **P0** | Native tab bar + drawer, safe-area, deep-link entry. Redesign, don't port web nav. |
| **Auth** | signin, signup, forgot, reset | **P0** | Real GraphQL wiring, SecureStore, biometric unlock, deep-link reset token. |
| **Auth (email verification)** | verify-email, verify-email-link | P1 | Deep-link driven. |
| **Financial data** | institutions list + detail | **P0** | Virtualized lists, search, detail with charts. |
| **Financial data** | national indicators | P1 | Chart-heavy; native chart lib. |
| **Personal finance** | transactions, accounts, categories | **P0/P1** | Core wealth journeys. Transactions list/entry is P0; rules/recurrences P1. Redesign entry flows for touch. |
| **Personal finance** | bills, recurring payments, prices-consumption | P1 | |
| **Personal finance** | dashboard/summary | **P0** | The authenticated home for a wealth user; native cards, offline-first. |
| **Investments** | portfolio, fixed-income comparison | **P0/P1** | Portfolio view P0; comparison tooling P1. |
| **Settings / Authorization** | users, groups, roles, permissions | P2 | Confirm whether needed for external release or internal-only. |
| **Profile** | profile | P1 | |

> Open product decision inherited from Phase 0: is authorization management required for first external release, or internal-only? This drives whether Settings/Authorization stays P2.

## What "Mobile-First Premium" Means (P0 quality bar)

Concrete, enforceable expectations for every P0 slice — not a later polish phase:

- **Navigation & motion**: native stack/tab transitions, shared-element or fade transitions where meaningful, gesture-based back, bottom-sheet flows for create/edit instead of full-page forms where it fits.
- **Feedback**: haptics on key actions, optimistic UI with rollback, skeleton loaders (not spinners) for data screens, and first-class empty/error states per screen.
- **Trust & speed**: biometric unlock, offline-first reads from persisted Apollo cache, background refresh, and graceful degradation on poor networks.
- **Input**: touch-optimized forms (numeric keypads for money, masked inputs, inline validation), pull-to-refresh, swipe actions on list items.
- **Platform fit**: respects iOS/Android conventions and safe areas; dark mode; dynamic type/accessibility (WCAG-aligned, screen-reader labels).
- **Native capabilities where they add value**: push notifications, deep links, share sheet, camera/QR for imports (web already uses `jsqr`).

Each slice's Definition of Done includes these, not just functional parity.

## Delivery Phases

Reconciled with the migration roadmap, extended to full scope.

- **Phase 0 — Discovery & parity baseline** *(mostly done)*: finalize the extended parity tracker (this doc's matrix), confirm priorities with product, close the code-sharing ADR addendum.
- **Phase 1 — Foundation lock-in**: real auth/session lifecycle, route guards, API client + env strategy, error mapping, i18n namespaces, theme-token mapping, and the first shared library. Exit: P0 slices can plug in without reworking the base.
- **Phase 2 — P0 vertical slices**: shell/home, auth, financial-data list/detail, personal-finance dashboard + transactions, investments portfolio — each end-to-end with the premium bar. Exit: internal dogfooding release, journeys pass QA.
- **Phase 3 — P1 parity**: national indicators, personal-finance breadth (bills, recurrences, rules), investment comparison, verify-email, profile. Exit: P1 target reached.
- **Phase 4 — Hardening & release**: device matrix, crash/telemetry thresholds, CI smoke automation, staged rollout (internal → beta → production via EAS). Exit: release criteria met.

**Continuous workstreams** (all phases): navigation & deep linking, auth/token lifecycle, API/contract-drift control, error/recovery UX, i18n consistency, design-token translation, performance, and quality gates.

## Development Pipeline & Skills

Mobile development runs through the **existing `neotool-*` GitHub-issue pipeline**, so mobile work is tracked and executed exactly like the rest of Invistus:

```
neotool-open-issue → neotool-solution-design → neotool-backlog-gen → neotool-implement
```

`neotool-implement` loads scope skills based on the backlog. Today the frontend skill is **`neotool-nextjs-app`**, which claims web *or* mobile but is Next.js + MUI + next-intl specific — it does not encode React Native / Expo patterns.

### Proposed extension: `neotool-expo-mobile` skill

Create a mobile sibling to `neotool-nextjs-app`, at `.agents/skills/neotool-expo-mobile/`, encoding the RN/Expo implementation patterns and non-negotiables:

- Expo Router structure and route groups/guards; native navigation and deep linking.
- RN Paper theming via tokens (zero hardcoded colors), mapping web design tokens to MD3.
- Apollo with offline cache/persistence, SecureStore, biometrics, network status.
- `FlatList`/virtualization, safe-area, haptics, bottom sheets, gestures, skeletons.
- Forms with React Hook Form + Zod on native inputs; i18n with i18next (no hardcoded strings).
- Push notifications and deep-link handling; EAS build/submit awareness.
- Jest + RN Testing Library patterns.
- The **"mobile is not squeezed desktop"** rule elevated to a first-class checklist.

Then update **`neotool-implement`** routing so mobile-tagged backlog tasks load `neotool-expo-mobile` (+ `neotool-conventions`, and `neotool-finance-domain` for wealth features) instead of `neotool-nextjs-app`. `neotool-conventions` (auth/errors/i18n) and `neotool-finance-domain` are reused as-is.

### GitHub Project "Mobile" — epic structure

Use the existing project. Suggested epics (each an issue, broken down via the pipeline):

1. **Foundation** — real auth/session, route guards, API/env, error mapping, i18n namespaces, theme mapping, first shared GraphQL library.
2. **Shell & Home** — native tab/drawer nav, safe-area, deep-link entry, home dashboard.
3. **Auth** — signin, signup, forgot, reset (deep link), biometric unlock.
4. **Financial Data** — institutions list/detail, search, charts.
5. **Personal Finance (core)** — dashboard/summary, transactions list/entry, accounts.
6. **Investments (core)** — portfolio view.
7. **Tooling & Pipeline** — `neotool-expo-mobile` skill, `neotool-implement` routing, mobile CI (typecheck/lint/test), EAS pipeline.
8. **Release Readiness** — device matrix, crash/telemetry, staged rollout.

P1/P2 epics (national indicators, PF breadth, investment comparison, verify-email, profile, authorization) follow after P0 freeze.

## Risks & Mitigations

- **Contract drift** between web and mobile GraphQL usage → land the shared GraphQL library early; treat drift as a tracked risk with bi-weekly reconciliation.
- **Premium bar slowing P0** → define a reusable native component/interaction kit (skeletons, bottom sheets, list swipe, money inputs) in Phase 1 so slices compose, not reinvent.
- **Expo managed-workflow limits** → prefer Expo modules/config plugins before considering bare workflow (per ADR-0010 mitigations).
- **Scope creep beyond features** → features equal web; new mobile-only surface requires explicit product approval and a divergence log entry.
- **Auth/session on long-lived mobile sessions** → validate refresh-token lifecycle + biometric re-auth in Phase 1 before feature slices depend on it.

## Next Actions

1. ~~Ratify the code-sharing decision~~ — **Done**: [ADR-0014](../../92-adr/0014-web-mobile-code-sharing-strategy.md) (shared Nx libraries for GraphQL + validation; identical contracts).
2. Confirm the **extended parity matrix** and the authorization-management product decision.
3. Create the **GitHub epics** above in the Mobile project and run the first (Foundation) through `neotool-solution-design` → `neotool-backlog-gen`.
4. Scaffold the **`neotool-expo-mobile`** skill and update `neotool-implement` routing.
5. Kick off **Phase 1 Foundation**: replace the mock `AuthProvider` with real GraphQL auth as the first vertical proof.
