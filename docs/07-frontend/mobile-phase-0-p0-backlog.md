---
title: Mobile Migration Phase 0 P0 Backlog
type: backlog
category: frontend
status: draft
version: 1.0.0
tags: [frontend, mobile, migration, backlog, p0]
ai_optimized: true
search_keywords: [mobile p0 backlog, migration task list, auth financial-data tasks]
related:
  - 07-frontend/mobile-phase-0-p0-dependency-map.md
  - 07-frontend/mobile-phase-0-discovery-baseline.md
  - 07-frontend/mobile-migration-roadmap.md
last_updated: 2026-02-12
---

# Mobile Migration Phase 0 P0 Backlog

> **Purpose**: Task-by-task execution backlog for P0 migration slices (shell/home + auth + financial data).

## Backlog Usage Rules

- Status values: `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`.
- Each task must have one owner and one completion date.
- No feature task starts before its foundation dependencies are `DONE`.
- Keep parity tracker updated when any task status changes.

## P0 Execution Order

1. Shell and home first slice (landing page + bottom navigation parity).
2. Auth flows.
3. Financial-data flows.
4. Cross-slice quality gates and release readiness.

## Epic A: Planning and Governance

- [ ] `A-01` Create canonical parity tracker sheet (columns: feature, route, priority, owner, status, risks, target date).
- [ ] `A-02` Assign owners for shell/home, auth, and financial-data streams.
- [ ] `A-03` Confirm P0 scope freeze (shell/home + auth + financial-data list/detail).
- [ ] `A-04` Document explicit non-goals for P0 (settings parity, national indicators, verify-email flows).

## Epic B: Foundation Stabilization

Dependencies: `A-03`

- [ ] `B-01` Finalize mobile route guard behavior for auth vs authenticated tabs.
- [ ] `B-02` Define token storage contract in mobile (`accessToken`, `refreshToken`, user snapshot).
- [ ] `B-03` Define refresh-token lifecycle behavior and failure handling rules.
- [ ] `B-04` Normalize GraphQL error mapping contract (network, auth, business validation, unknown).
- [ ] `B-05` Confirm mobile environment configuration for GraphQL endpoint by environment.
- [ ] `B-06` Define i18n namespace structure for auth and financial-data domains in mobile locales.
- [ ] `B-07` Define analytics event naming for P0 user journeys.
- [ ] `B-08` Confirm shared-UI boundary policy for migration PR reviews (share behavior, not rendering).

## Epic S: Shell and Home P0 - First Slice

Dependencies: `A-03`, `B-01`, `B-06`

- [ ] `S-01` Capture start-page parity contract from web mobile viewport
- [ ] `S-02` Define bottom navigation parity contract (`Inicio`, `Dados Financeiros`, `Entrar/Perfil`, `Menu`).
- [ ] `S-03` Implement bottom navigation shell behavior in mobile route layout.
- [ ] `S-04` Implement landing/start page content parity (logo, headline, subtitle, footer blocks).
- [ ] `S-05` Wire nav targets to placeholder or existing routes (non-blocking for downstream features).
- [ ] `S-06` Validate safe-area and fixed-bottom-nav behavior across Android emulator sizes.
- [ ] `S-07` Add shell/home i18n keys and translations parity.
- [ ] `S-08` Add analytics events for home view and bottom-nav taps.
- [ ] `S-09` Add shell/home QA checklist with visual parity criteria.
- [ ] `S-10` Complete milestone sign-off for Shell/Home first slice.

## Epic C: Auth P0 - Sign In

Dependencies: `S-10`, `B-02`, `B-04`, `B-06`

- [ ] `C-01` Map web signin UX states to mobile equivalents (idle/loading/error/success).
- [ ] `C-02` Implement signin mutation integration contract in mobile auth provider.
- [ ] `C-03` Implement signin form validation parity (email/password semantics).
- [ ] `C-04` Implement signin error state and user feedback parity.
- [ ] `C-05` Implement remember-session behavior decision for mobile (explicitly keep or drop for P0).
- [ ] `C-06` Validate post-login routing behavior parity.
- [ ] `C-07` Add signin i18n keys and translations parity.
- [ ] `C-08` Add analytics events for signin success/failure.
- [ ] `C-09` Add test scenarios for signin happy/error/validation states.

## Epic D: Auth P0 - Sign Up

Dependencies: `S-10`, `B-02`, `B-04`, `B-06`

- [ ] `D-01` Map web signup UX states to mobile equivalents.
- [ ] `D-02` Implement signup mutation integration contract in mobile auth provider.
- [ ] `D-03` Implement signup form fields parity (name/email/password/confirm).
- [ ] `D-04` Implement password rules parity and messaging.
- [ ] `D-05` Implement signup error handling parity.
- [ ] `D-06` Define and implement post-signup route behavior (home vs verification branch decision for P0).
- [ ] `D-07` Add signup i18n keys and translations parity.
- [ ] `D-08` Add analytics events for signup success/failure.
- [ ] `D-09` Add test scenarios for signup validation and server errors.

## Epic E: Auth P0 - Forgot Password

Dependencies: `S-10`, `B-04`, `B-06`

- [ ] `E-01` Implement request password reset mutation integration.
- [ ] `E-02` Implement forgot-password form and validation parity.
- [ ] `E-03` Implement success and error feedback states parity.
- [ ] `E-04` Confirm locale handling for request payload.
- [ ] `E-05` Add forgot-password i18n keys and translations parity.
- [ ] `E-06` Add analytics events for reset-link request success/failure.
- [ ] `E-07` Add test scenarios for forgot-password flow.

## Epic F: Auth P0 - Reset Password

Dependencies: `S-10`, `B-01`, `B-04`, `B-06`

- [ ] `F-01` Create mobile reset-password route and screen.
- [ ] `F-02` Define reset token ingestion mechanism for mobile (deep link/query param strategy).
- [ ] `F-03` Implement reset password mutation integration.
- [ ] `F-04` Implement reset-password form with password rules and confirm parity.
- [ ] `F-05` Implement success transition behavior (redirect to login timing).
- [ ] `F-06` Implement invalid/missing token UX state.
- [ ] `F-07` Add reset-password i18n keys and translations parity.
- [ ] `F-08` Add analytics events for reset success/failure.
- [ ] `F-09` Add test scenarios for reset flow (token missing/invalid, success, server error).

## Epic G: Financial Data P0 - Module Entry

Dependencies: `S-10`, `B-06`

- [ ] `G-01` Create mobile financial-data module entry route/screen.
- [ ] `G-02` Map web module cards to mobile-native card interactions.
- [ ] `G-03` Implement route navigation to institutions list from module entry.
- [ ] `G-04` Add financial-data base i18n keys and translations.
- [ ] `G-05` Add analytics events for module entry navigation.

## Epic H: Financial Data P0 - Institutions List

Dependencies: `S-10`, `B-04`, `B-06`, `G-01`

- [ ] `H-01` Create institutions list route/screen in mobile.
- [ ] `H-02` Port search/autocomplete query integration (`search autocomplete`).
- [ ] `H-03` Port institutions paginated query integration.
- [ ] `H-04` Implement institutions search state machine parity (search text, selection, pagination cursor).
- [ ] `H-05` Implement institution card mobile component equivalent.
- [ ] `H-06` Implement list loading, empty, and error states parity.
- [ ] `H-07` Define semantic path strategy in mobile for institution detail navigation.
- [ ] `H-08` Implement list-to-detail navigation using chosen ID strategy.
- [ ] `H-09` Add i18n parity for list screen and card labels.
- [ ] `H-10` Add analytics events for search, card click, pagination load.
- [ ] `H-11` Add test scenarios for list search/filter/pagination and error states.

## Epic I: Financial Data P0 - Institution Detail

Dependencies: `S-10`, `B-04`, `B-06`, `H-08`

- [ ] `I-01` Create institution detail route/screen in mobile.
- [ ] `I-02` Port institution detail query integration.
- [ ] `I-03` Port reports catalog query integration.
- [ ] `I-04` Port report detail query integration.
- [ ] `I-05` Implement institution summary component equivalent (name/legal/address/site).
- [ ] `I-06` Implement restricted-data notice path parity.
- [ ] `I-07` Implement report selector behavior (mobile tabs/list pattern).
- [ ] `I-08` Implement balance summary metric grid equivalent.
- [ ] `I-09` Implement report detail visualization equivalent (table/chart/metric details pattern).
- [ ] `I-10` Implement footer/source notes parity.
- [ ] `I-11` Add i18n parity for detail/report/balance keys.
- [ ] `I-12` Add analytics events for report selection and detail interactions.
- [ ] `I-13` Add test scenarios for restricted vs unrestricted data paths and report switching.

## Epic J: Quality Gates and Release Readiness for P0

Dependencies: `C-09`, `D-09`, `E-07`, `F-09`, `H-11`, `I-13`

- [ ] `J-01` Create P0 smoke-test checklist for auth and financial-data flows.
- [ ] `J-02` Validate key flows on at least two Android emulator profiles.
- [ ] `J-03` Validate deep-link reset-password flow on emulator.
- [ ] `J-04` Validate offline/online transitions for financial-data queries.
- [ ] `J-05` Validate crash/error logging signal quality for P0 flows.
- [ ] `J-06` Update parity tracker statuses to reflect delivered behavior.
- [ ] `J-07` Write P0 completion report with unresolved gaps and P1 carry-over.

## Definition of Done for Each Task

- Scope is implemented and peer-reviewed.
- Parity impact is updated in tracker.
- i18n coverage updated where applicable.
- Test evidence attached (automated or checklist reference).
- Risks and follow-ups captured when full parity is intentionally deferred.
