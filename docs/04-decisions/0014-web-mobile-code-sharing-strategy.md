---
title: ADR-0014 Web Mobile Code Sharing Strategy
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - libs/shared-graphql
  - web/package.json
  - mobile/package.json
load_when:
  - sharing code between web and mobile
  - adding cross-client libraries
supersedes:
schema: docs-metadata-v1
---

# ADR-0014: Web Mobile Code Sharing Strategy

Purpose: record the durable decision and consequences for web mobile code sharing strategy.

## Status

accepted

## Context

Web and mobile should share contracts without forcing a single UI framework.

## Decision

Share GraphQL types/operations through `libs/shared-graphql` (and related generated artifacts). Keep UI, navigation, and platform UX separate per client stack.

## Consequences

- Avoids lowest-common-denominator UI.
- Requires discipline so shared libs stay UI-agnostic.

## Evidence

- `libs/shared-graphql/` exists with generated GraphQL types.
- Distinct `web/` and `mobile/` apps remain.

## Related

- [README](README.md)
- [ADR-0004](0004-typescript-nextjs-frontend.md)
- [ADR-0010](0010-react-native-expo-mobile.md)
