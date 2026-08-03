---
title: ADR-0010 React Native Expo Mobile
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - mobile/package.json
  - package.json
load_when:
  - choosing mobile stack
  - adding mobile screens
supersedes:
schema: docs-metadata-v1
---

# ADR-0010: React Native Expo Mobile

Purpose: record the durable decision and consequences for react native expo mobile.

## Status

accepted

## Context

Invistus needs a native mobile client sharing GraphQL contracts with web without forcing a single UI codebase.

## Decision

Use React Native + Expo for `mobile/`, with Apollo and React Native Paper, validated by root `check:mobile`.

## Consequences

- Parallel mobile delivery alongside Next.js web.
- Shared GraphQL types via `libs/shared-graphql` where applicable (see ADR-0014).

## Evidence

- `mobile/package.json` exists; root script `check:mobile` targets `neotool-mobile`.

## Related

- [README](README.md)
- [ADR-0014](0014-web-mobile-code-sharing-strategy.md)
- [Mobile standards](../02-development/README.md)
