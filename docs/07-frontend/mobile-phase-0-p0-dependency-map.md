---
title: Mobile Migration Phase 0 P0 Dependency Map
type: discovery
category: frontend
status: draft
version: 1.0.0
tags: [frontend, mobile, migration, phase-0, p0, dependency-map]
ai_optimized: true
search_keywords: [p0 dependency map, auth migration dependencies, financial-data mobile migration]
related:
  - 07-frontend/mobile-phase-0-discovery-baseline.md
  - 07-frontend/mobile-migration-roadmap.md
  - 02-architecture/frontend-architecture.md
  - 02-architecture/mobile-architecture.md
last_updated: 2026-02-12
---

# Mobile Migration Phase 0 P0 Dependency Map

> **Purpose**: Identify concrete dependencies required to migrate P0 slices from `web/` to `mobile/` with predictable scope.

## P0 Scope Covered

- Shell and home:
  - landing/start page (`/`)
  - bottom navigation shell
- Authentication:
  - `signin`
  - `signup`
  - `forgot-password`
  - `reset-password`
- Financial data:
  - module entry (`/financial-data`)
  - institutions list (`/financial-data/bacen-institutions`)
  - institution detail (`/financial-data/bacen-institutions/[bacen-cod-inst]`)

## Cross-Slice Foundations Required

These are shared prerequisites across P0 slices:

- Apollo GraphQL client stabilization in mobile:
  `mobile/src/lib/apollo-client.ts`
- Auth/session lifecycle hardening in mobile:
  `mobile/src/providers/AuthProvider.tsx`
- Mobile i18n expansion:
  `mobile/src/locales/en.json`, `mobile/src/locales/pt.json`
- Route groups and guards in Expo Router:
  `mobile/app/_layout.tsx`, `mobile/app/(auth)/*`, `mobile/app/(tabs)/*`

Current baseline observation:

- Mobile has provider structure and route shell in place.
- Mobile auth provider still uses mock login/signup flow.
- Financial-data domain keys and routes are not yet present on mobile.

## Shared UI Boundary (Web vs Mobile)

Decision rule for this migration:

- Share behavior and contracts.
- Do not share direct UI rendering components.

### What should be shared

- GraphQL operations and typed models.
- Domain logic and formatting utilities.
- Validation schemas and form rules.
- i18n keys/messages.
- Headless state and flow logic (when UI-agnostic).

### What should stay platform-specific

- UI primitives (`Button`, `Input`, `Container`, `Page`, `Box`).
- Layout components tied to DOM/CSS or React Native layout constraints.
- Visual form field components and interaction widgets.

### Practical guidance for this backlog

- Prioritize feature parity over cross-platform component reuse.
- Keep naming parity between web and mobile components for discoverability.
- Extract shared headless logic only after repetition appears in at least two slices.
- Avoid forcing MUI abstractions into React Native or vice versa.

## Auth P0 Dependency Map

| Flow | Web Reference Route | Primary Web Components | GraphQL Dependencies | Shared Dependencies | Mobile Baseline |
| --- | --- | --- | --- | --- | --- |
| Sign in | `web/src/app/(authentication)/signin/page.tsx` | `web/src/shared/components/auth/SignInForm.tsx` | `SIGN_IN`, `SIGN_IN_WITH_OAUTH` from `web/src/lib/graphql/operations/auth/mutations.ts` | `web/src/shared/providers/AuthProvider.tsx`, `web/src/shared/hooks/auth/useOAuth.ts`, `extractErrorMessage`, toast provider, auth i18n | `mobile/app/(auth)/login.tsx` exists; no real GraphQL auth yet |
| Sign up | `web/src/app/(authentication)/signup/page.tsx` | `web/src/shared/components/auth/SignUpForm.tsx` | `SIGN_UP` from `web/src/lib/graphql/operations/auth/mutations.ts` | `validatePassword`, `passwordValidationRules`, auth provider, toast provider, signup i18n | `mobile/app/(auth)/signup.tsx` is placeholder |
| Forgot password | `web/src/app/(authentication)/forgot-password/page.tsx` | `web/src/shared/components/auth/ForgotPasswordForm.tsx` | `RequestPasswordResetDocument` from `web/src/lib/graphql/operations/auth/mutations.generated.ts` | `extractErrorMessage`, toast provider, forgot-password i18n | `mobile/app/(auth)/forgot-password.tsx` is placeholder |
| Reset password | `web/src/app/(authentication)/reset-password/page.tsx` | `web/src/shared/components/auth/ResetPasswordForm.tsx` | `ResetPasswordDocument` from `web/src/lib/graphql/operations/auth/mutations.generated.ts` | password validation utilities, toast provider, reset-password i18n, token-from-link handling | Missing mobile route and screen |

### Auth Notes

- Web auth state and storage behavior are in:
  `web/src/shared/providers/AuthProvider.tsx`
- Mobile equivalents currently need parity for:
  token persistence, refresh handling, signup verification branch, and error propagation.
- OAuth is implemented on web (Google) and should be explicitly deferred or scoped for mobile P0 decision.

## Shell and Home P0 Dependency Map

| Slice | Web Reference Route / Component | Primary Web Dependencies | Shared Dependencies | Mobile Baseline |
| --- | --- | --- | --- | --- |
| Landing page | `web/src/app/page.tsx` | landing hero structure, footer content, responsive behavior | landing i18n domain (`web/src/app/i18n/locales/*.json`) | `mobile/app/index.tsx` currently contains temporary hello-world screen |
| Bottom navigation shell | `web/src/shared/ui/navigation/BottomNavigationBar.tsx` | nav items (`Home`, `Financial Data`, `Sign In/Profile`, `Menu`), active state behavior | auth state for profile/sign-in label, route mapping semantics | mobile tab shell exists but parity contract for labels/items/behavior not locked |
| Menu entry behavior | `web/src/shared/ui/navigation/MobileNavigationDrawer.tsx` | bottom-sheet menu actions and quick links | settings/auth action contract | no mobile equivalent mapped yet for menu behavior |

### Shell/Home Notes

- This is the first migration milestone to reduce complexity before auth/data flows.
- Scope for first pass is structural and navigational parity, not full feature completion behind each menu item.
- Primary target viewport for parity validation: `360x780`.

## Financial Data P0 Dependency Map

| Slice | Web Reference Route | Primary Web Feature Hooks | GraphQL Dependencies | Widget / Entity Dependencies | Mobile Baseline |
| --- | --- | --- | --- | --- | --- |
| Module entry | `web/src/app/financial-data/page.tsx` | none (navigation entry screen) | none (content-level) | module cards, financial-data i18n domain | Missing mobile route/screen |
| Institutions list | `web/src/app/financial-data/bacen-institutions/page.tsx` | `useBacenInstitutionsSearch`, `useBacenInstitutionsAutocompleteQuery` from `web/src/features/financial-data/search-institutions/hooks/useBacenInstitutionsSearch.ts` | `SEARCH_BACEN_INSTITUTIONS_AUTOCOMPLETE`, `BACEN_INSTITUTIONS` from `web/src/features/financial-data/search-institutions/api/index.ts` | `BacenInstitutionCard`, `createSemanticPath`, pagination hook (`useRelayPagination`), financial-data i18n | Missing mobile route/screen |
| Institution detail | `web/src/app/financial-data/bacen-institutions/[bacen-cod-inst]/client.tsx` | `useBacenInstitutionDetail` from `web/src/features/financial-data/view-institution-detail/hooks/useBacenInstitutionDetail.ts`; `useBacenInstitutionBalanceSummary` via widget | `BACEN_INSTITUTION`, `BACEN_INSTITUTION_REPORTS`, `BACEN_INSTITUTION_REPORT_DETAILS` from `web/src/features/financial-data/view-institution-detail/api/index.ts` | Widgets: summary paper, report detail, balance summary grid, restriction notice, footer; entity metrics thresholds; `extractIdFromSemanticPath`, id obfuscation decode | Missing mobile route/screen |

### Financial Data Notes

- Financial-data translations are domain-driven in web:
  `web/src/shared/i18n/domains/financial-data/index.ts`
- Mobile locale files currently do not include financial-data domain keys.
- Detail page behavior includes restricted-data fallback path and report switching (sidebar/tabs) that should be preserved functionally.

## P0 Dependency Backlog (Execution-Ready)

1. Shell and home first slice:
   landing page structure, footer content, bottom navigation items, and menu entry behavior.
2. Auth GraphQL integration in mobile:
   wire `signIn`, `signUp`, `requestPasswordReset`, `resetPassword`.
3. Auth route completion:
   add reset-password route with token flow.
4. Financial-data route group creation in mobile:
   module entry, institutions list, institution detail.
5. Financial-data GraphQL hooks in mobile:
   port search and detail query logic.
6. Financial-data widget equivalents:
   institution card, summary, report details, restriction notice, footer.
7. i18n parity for shell/home, auth, and financial-data domains.
8. URL semantic ID strategy decision:
   keep web-style encoded semantic path or simplify for mobile routing.

## Out of Scope for This P0 Map

- Settings authorization management parity.
- National indicators screen.
- Verify-email and verify-email-link flows (currently P1 in baseline).
