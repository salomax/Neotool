---
title: Mobile Migration Phase 0 Discovery Baseline
type: discovery
category: frontend
status: draft
version: 1.0.0
tags: [frontend, mobile, migration, discovery, parity]
ai_optimized: true
search_keywords: [phase 0 discovery, mobile parity baseline, web mobile mapping]
related:
  - 07-frontend/mobile-migration-roadmap.md
  - 02-architecture/frontend-architecture.md
  - 02-architecture/mobile-architecture.md
last_updated: 2026-02-12
---

# Mobile Migration Phase 0 Discovery Baseline

> **Purpose**: Establish the initial web-to-mobile parity baseline and identify the first migration priorities.

## Scope Reviewed

### Web route pages reviewed

Reference: `web/src/app/**/page.tsx`

- `/(authentication)/signin`
- `/(authentication)/signup`
- `/(authentication)/forgot-password`
- `/(authentication)/reset-password`
- `/(authentication)/verify-email`
- `/(authentication)/verify-email-link`
- `/(settings)/settings`
- `/financial-data`
- `/financial-data/bacen-institutions`
- `/financial-data/bacen-institutions/[bacen-cod-inst]`
- `/financial-data/national-indicators`
- `/` (home)

Total web pages: `12`

### Mobile screens reviewed

Reference: `mobile/app/**/*.tsx`

- `/` (`mobile/app/index.tsx`)
- `/(auth)/login`
- `/(auth)/signup`
- `/(auth)/forgot-password`
- `/(tabs)/index`
- `/(tabs)/profile`
- `/(tabs)/settings`
- layout and not-found screens

Total mobile screens: `11` (including layouts and not-found)

## Initial Parity Matrix

| Domain | Web Route / Screen | Mobile Route / Screen | Current Status | Target Priority |
| --- | --- | --- | --- | --- |
| Authentication | `/(authentication)/signin` | `/(auth)/login` | Partial (mobile exists, web-level behavior parity not confirmed) | P0 |
| Authentication | `/(authentication)/signup` | `/(auth)/signup` | Partial (placeholder in mobile) | P0 |
| Authentication | `/(authentication)/forgot-password` | `/(auth)/forgot-password` | Partial (placeholder in mobile) | P0 |
| Authentication | `/(authentication)/reset-password` | Missing | Missing | P0 |
| Authentication | `/(authentication)/verify-email` | Missing | Missing | P1 |
| Authentication | `/(authentication)/verify-email-link` | Missing | Missing | P1 |
| Settings / Authorization | `/(settings)/settings` (users/groups/roles tabs) | `/(tabs)/settings` | Partial (theme/language/logout only) | P1 |
| Financial Data | `/financial-data` | Missing | Missing | P0 |
| Financial Data | `/financial-data/bacen-institutions` | Missing | Missing | P0 |
| Financial Data | `/financial-data/bacen-institutions/[bacen-cod-inst]` | Missing | Missing | P0 |
| Financial Data | `/financial-data/national-indicators` | Missing | Missing | P1 |
| Home / Shell | `/` + mobile web bottom navigation | `/(tabs)/index` and `/` | Partial (mobile dashboard is placeholder and nav parity not locked) | P0 |

## Initial Findings

- Mobile already has the auth shell and tab shell, but several screens are placeholders.
- Financial data flows exist in web with dedicated feature slices and are absent in mobile routes.
- Settings parity is low for authorization management behavior.
- Existing mobile architecture supports Phase 1 foundation lock-in (routing/auth/theme/i18n providers are present).

## Proposed Priority Cut for Migration

### P0 (first delivery slice)

- Start page/landing page parity and bottom navigation shell parity.
- Sign in, sign up, forgot password, reset password.
- Financial data module entry and institution list/detail.
- Core app shell behavior for authenticated navigation.

### P1

- Verify email and verify email-link flows.
- National indicators module.
- Settings parity expansion for users/groups/roles management.

### P2 (defer until after P1)

- Deep parity improvements and advanced UX refinements not needed for first release.

## Phase 0 Deliverables (to complete now)

1. Finalize parity tracker fields and workflow.
2. Confirm route mapping and priority assignments with product.
3. Add dependency mapping for each P0 feature:
   GraphQL queries/mutations, auth dependencies, translation domains, and shared widgets.
4. Produce a risk register:
   platform deltas, missing APIs, and UX divergence decisions.
5. Define exit criteria sign-off for Phase 0.

## Open Questions

- Is authorization management in settings required for first external mobile release, or internal-only?
- Should `/financial-data/national-indicators` move into P0 for business reasons?
- For authentication, do we need strict 1:1 UX parity or behavior parity only?

## Next Step

Create the detailed P0 dependency map per slice:

- shell/home (`landing`, `bottom navigation`)
- auth (`signin`, `signup`, `forgot-password`, `reset-password`)
- financial data (`module index`, `institutions list`, `institution detail`)
