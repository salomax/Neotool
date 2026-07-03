---
title: ADR-0014 Web/Mobile Code-Sharing Strategy
type: adr
category: frontend
status: accepted
version: 1.0.0
tags: [code-sharing, monorepo, nx, graphql, mobile, web, contracts, react-native]
related:
  - 92-adr/0010-react-native-expo-mobile.md
  - 92-adr/0004-typescript-nextjs-frontend.md
  - 92-adr/0003-nx-monorepo-tooling.md
  - 02-architecture/mobile-architecture.md
  - 07-frontend/mobile/README.md
---

# ADR-0014: Web/Mobile Code-Sharing Strategy

## Status

Accepted

## Context

Invistus now targets two clients — the Next.js web app (`web/`) and the React Native + Expo
mobile app (`mobile/`) — against the **same federated GraphQL backend**. Both are TypeScript + React,
but their UI stacks differ (MUI vs React Native Paper) and their platform integrations differ
(navigation, storage, notifications, lifecycle).

Today `mobile/` **duplicates** cross-cutting concerns (Apollo client config, i18n) and has no shared
code with `web/`. ADR-0010 anticipated shared `packages/*` but they do not exist yet. Left unmanaged,
the two clients drift: the same query or validation rule gets written twice, and a backend change can
leave one client silently out of sync.

We must decide **how much to share** between web and mobile, and where the boundary sits.

The options considered lie on a spectrum of "what to share":

- **A. Duplicate (status quo)** — nothing shared; mobile mirrors web by hand.
- **B. Narrow shared libraries** — share GraphQL operations + generated types, Zod validation
  schemas, formatters, and pure domain logic. UI, navigation, theming, and platform integrations
  stay per-platform.
- **C. Broad sharing** — everything in B plus headless state/flow logic and UI-agnostic hooks.
- **D. Cross-platform UI layer** — share rendered components across web and mobile.

## Decision

We adopt **Option B: share behavior and contracts, not rendering.**

Concretely:

- **Shared (single source of truth):** GraphQL documents and generated types, Zod validation
  schemas, formatters, and pure business/domain logic.
- **Not shared (per-platform):** rendered components, navigation, theming implementation, and
  platform integrations (storage, notifications, biometrics, deep links).
- **Mechanism:** Nx-managed shared libraries consumed by both `web/` and `mobile/`.
- **Sequencing:** start narrow. Extract **one** library for GraphQL operations + types and **one**
  for validation/formatters first. Generalize only after those prove the pattern.
- **Interim (until the libs land):** `mobile/` mirrors the **shape** of web operations and schemas so
  the later extraction is a lift, not a rewrite. Contract drift is tracked as an explicit risk.

### Contract invariant (non-negotiable)

**The API contract is identical for both clients.** A backend change to a GraphQL operation must
update the shared operation/type in the same change so both web and mobile stay in sync. Divergent
per-client copies of a shared contract are not allowed. This is why GraphQL operations + generated
types are the first shared library.

## Consequences

### Positive

- Kills contract drift where it hurts most (API operations and validation rules).
- Backend changes propagate to both clients through one shared definition.
- UI stays 100% free per platform — mobile-first UX is not constrained by web components.
- Small initial scope; low coupling; easy to reason about ownership.
- End-to-end type safety preserved from schema to both UIs via codegen.

### Negative / costs

- Requires Nx library setup and discipline about the sharing boundary.
- A change to a shared library affects both apps, so CI must validate web **and** mobile on shared-lib
  changes.
- Requires review vigilance to keep genuinely-platform logic out of shared libs.

### Rejected alternatives

- **A. Duplicate** — works early but every new feature pays the cost twice; since the backend is
  shared, query/rule divergence becomes a silent production bug. Rejected.
- **C. Broad sharing** — the extra reuse (flow/state hooks) is small and fragile; most "flow" hooks end
  up coupled to navigation, storage, or toasts, which differ per platform. Couples release cycles for
  little gain. Deferred, not adopted now.
- **D. Cross-platform UI** — MUI ≠ RN Paper; a shared render layer destroys mobile-first UX and is
  explicitly discouraged by the mobile roadmap. Rejected.

## Implementation Notes

1. Create the first shared Nx library: **GraphQL operations + generated types**. Point both `web/`
   and `mobile/` codegen/consumption at it. This is a Foundation-phase deliverable (see
   `docs/07-frontend/mobile/README.md`, Phase 1).
2. Create the second shared library: **validation (Zod) + formatters + pure domain logic**.
3. Add CI so changes to a shared library run affected checks for both `web` and `mobile` (Nx affected).
4. Keep the boundary documented; anything platform-specific must not enter a shared library.
5. Revisit Option C only if a concrete, genuinely UI-agnostic flow proves worth extracting.

## Related Documentation

- [ADR-0010: React Native + Expo Mobile](./0010-react-native-expo-mobile.md)
- [ADR-0003: Nx Monorepo Tooling](./0003-nx-monorepo-tooling.md)
- [Mobile High-Level Plan](../07-frontend/mobile/README.md)
- [Mobile Architecture](../02-architecture/mobile-architecture.md)
