---
title: ADR-0004 TypeScript Next.js Frontend
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - web/package.json
  - package.json
load_when:
  - choosing web frontend stack
  - adding web UI features
supersedes:
schema: docs-metadata-v1
---

# ADR-0004: TypeScript Next.js Frontend

Purpose: record the durable decision and consequences for typescript next.js frontend.

## Status

accepted

## Context

The web client needs a typed React stack integrated with GraphQL and the design system.

## Decision

Use TypeScript + React + Next.js (App Router) with MUI for the web app under `web/`.

## Consequences

- Aligns with Nx `@nx/next` plugin and root `check:web`.
- Mobile remains a separate Expo stack (ADR-0010).

## Evidence

- `web/package.json` present; root scripts run `neotool-web` typecheck/lint/unit tests.
- `nx.json` includes `@nx/next/plugin` for `web/**/*`.

## Related

- [README](README.md)
- [ADR-0010](0010-react-native-expo-mobile.md)
- [Frontend web standards](../02-development/README.md)
