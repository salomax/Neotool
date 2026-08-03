---
title: Frontend Web Standards
type: development
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - web/package.json
  - web/codegen.ts
  - web/src/shared/providers/AuthorizationProvider.tsx
  - web/src/shared/components/authorization/PermissionGate.tsx
load_when:
  - building Next.js UI
  - wiring Apollo operations or permission gates
supersedes: []
schema: docs-metadata-v1
---

# Frontend Web Standards

Purpose: standards for the Next.js web app under `web/`.

## When to load

Load for UI work in `web/src`.

## Authority

`web/package.json`, GraphQL codegen config, and authorization primitives under `web/src/shared`.

## Rules

1. Stack is Next.js 16 + React 18 + Apollo Client 4 + MUI — match existing patterns.
2. Run `npm run codegen` in `web/` after GraphQL operation changes.
3. Use `PermissionGate` / `DisableIfNoPermission` for UI gating; backend remains authoritative.
4. Prefer Vitest for unit tests and Playwright for e2e (`test:e2e`).
5. Follow existing shared providers under `web/src/shared` rather than new parallel auth stacks.

## Commands

```bash
cd web
npm run dev
npm run typecheck
npm run lint
npm run test:unit
npm run codegen
npm run test:e2e
```

## Related

- [GraphQL codegen](graphql-codegen.md)
- [Testing](testing-strategy.md)
- [ADR-0004](../04-decisions/0004-typescript-nextjs-frontend.md)
- [ADR-0006](../04-decisions/0006-frontend-authorization-layer.md)
