---
title: Web to Mobile Migration Roadmap
type: roadmap
category: frontend
status: current
version: 1.0.0
tags: [frontend, mobile, migration, roadmap, react-native, expo, feature-sliced-design]
ai_optimized: true
search_keywords: [mobile migration, web to mobile, feature sliced, expo roadmap, parity roadmap]
related:
  - 02-architecture/frontend-architecture.md
  - 02-architecture/mobile-architecture.md
  - 92-adr/0010-react-native-expo-mobile.md
  - 07-frontend/README.md
last_updated: 2026-02-12
---

# Web to Mobile Migration Roadmap

> **Purpose**: Plan and govern migration from `web/` to `mobile/` with clear parity targets, phased delivery, and platform-appropriate implementation decisions.

## Objective

Replicate core product capabilities from `web/` into `mobile/` while preserving:

- Domain behavior and business rules.
- Product language and user outcomes.
- Feature ownership boundaries.

At the same time, adapt implementation to mobile platform constraints and strengths:

- React Native + Expo navigation and lifecycle.
- Mobile-first interaction patterns.
- Native performance, offline, and device capabilities.

## Architecture Direction

Use the same Feature-Sliced Design mental model across web and mobile.

- Keep slices aligned by domain: `shared`, `entities`, `features`, `widgets`, and route/page layer.
- Keep UI and platform integrations mobile-specific.
- Avoid forced cross-platform UI abstraction when it harms clarity or UX.
- Share contracts and business logic where feasible, not component implementations.

This gives consistent architecture across teams while respecting MUI versus React Native differences.

## Migration Principles

- Build by vertical slice, not by technical layer.
- Prioritize user-critical flows before broad parity.
- Define explicit parity level for each feature: `P0`, `P1`, `P2`.
- Keep a visible parity tracker as the source of truth.
- Treat responsive web behavior as a reference, not a strict UI copy.

## Phase Plan

### Phase 0: Discovery and Parity Baseline

**Goal**: Define what to migrate, in what order, and with which parity expectation.

**Key outcomes**

- Web inventory of routes, responsive/mobile behavior, and dependency graph.
- Feature parity matrix with priorities (`P0`, `P1`, `P2`).
- Migration backlog with acceptance criteria per feature.
- List of intentional web/mobile divergences approved by product and design.

**Exit criteria**

- Signed-off parity matrix.
- Signed-off milestone scope for Phase 1 and Phase 2.

### Phase 1: Foundation Lock-In

**Goal**: Stabilize shared technical foundations before broad feature migration.

**Key outcomes**

- Navigation architecture and route guard model finalized.
- Auth and session lifecycle finalized.
- API client contract and environment strategy finalized.
- Global error handling and fallback UX finalized.
- i18n strategy and locale handling finalized.
- Theme token mapping from web design system to mobile components finalized.

**Exit criteria**

- Foundation checklist complete and documented.
- P0 features can plug in without reworking base architecture.

### Phase 2: P0 Vertical Slices

**Goal**: Deliver end-to-end core user journeys first.

**Key outcomes**

- P0 auth flows working end-to-end.
- P0 home/dashboard and top business actions working end-to-end.
- Data loading, empty states, and error states complete for each migrated slice.
- Tracking/telemetry events added for critical journeys.
- Internal dogfooding release available.

**Exit criteria**

- All P0 journeys pass functional QA and smoke tests.
- Crash and blocker rate acceptable for controlled rollout.

### Phase 3: P1 Feature Parity

**Goal**: Expand to high-value non-core feature coverage.

**Key outcomes**

- P1 features migrated by vertical slice.
- Mobile-specific UX adjustments completed per feature.
- Performance passes for list-heavy and data-heavy screens.
- Usability and polish improvements based on dogfooding feedback.

**Exit criteria**

- P1 parity target reached and documented.
- Remaining P2 scope clearly staged.

### Phase 4: Hardening and Release Readiness

**Goal**: Ensure quality, observability, and operational readiness for broader release.

**Key outcomes**

- Device matrix validation completed.
- Regression and smoke automation in CI completed.
- Crash reporting and runtime telemetry monitored with thresholds.
- Rollout plan executed: internal, beta, then production.

**Exit criteria**

- Release criteria met.
- Known gaps and follow-up backlog documented.

## Core Workstreams

Run these workstreams continuously across phases:

- Navigation and deep linking.
- Auth and token lifecycle.
- API integration and contract drift control.
- Error handling and recovery UX.
- i18n and localization consistency.
- Design-system translation from web tokens to mobile components.
- Performance and perceived responsiveness.
- Quality gates, test strategy, and release operations.

## Parity Tracker Model

Track each feature with at least:

- Feature name and owning domain slice.
- Web reference route or screen.
- Mobile target route or screen.
- Parity level target (`P0`, `P1`, `P2`).
- Current status (`Not Started`, `In Progress`, `Ready for QA`, `Done`).
- Open risks and unresolved platform differences.

## Definition of Done for Each Migrated Slice

- Functional parity target met for that slice.
- Error, loading, and empty states validated.
- i18n and accessibility checks completed.
- Analytics events verified.
- Test coverage updated for critical behavior.
- Documentation and parity tracker updated.

## Governance and Cadence

- Weekly migration review on progress, blockers, and scope drift.
- Bi-weekly parity tracker reconciliation against `web/`.
- Explicit decision log for intentional divergence.
- Milestone retrospectives focused on architecture and execution quality.

## Recommended Next Actions

1. Lock the first vertical slice as Shell + Home:
   landing page parity and bottom navigation parity based on web mobile view (`360x780`).
2. Build the parity matrix from existing `web/` responsive/mobile behavior.
3. Lock the Phase 1 foundation backlog:
   navigation, auth/session lifecycle, API client, error handling, and i18n.
