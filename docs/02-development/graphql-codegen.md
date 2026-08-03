---
title: GraphQL Codegen
type: development
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - web/codegen.ts
  - mobile/codegen.ts
  - contracts/graphql/supergraph/supergraph.local.graphql
  - libs/shared-graphql
load_when:
  - regenerating client GraphQL types
  - changing codegen schema source
supersedes:
  - docs/06-contracts/
schema: docs-metadata-v1
---

# GraphQL Codegen

Purpose: how web and mobile generate typed GraphQL artifacts from the supergraph.

## When to load

Load after editing GraphQL operations or when generated types drift.

## Authority

Client `codegen.ts` files and `contracts/graphql/supergraph/supergraph.local.graphql`.

## Rules

1. Default schema source for local codegen is `contracts/graphql/supergraph/supergraph.local.graphql`.
2. Web codegen reads operations under `web/src` and writes generated types beside/near operations per `web/codegen.ts`.
3. Mobile codegen generates into `libs/shared-graphql/src/generated/` from shared documents.
4. Prefer TypedDocumentNode / Apollo Client 4 patterns already configured — do not reintroduce obsolete react-apollo codegen plugins without evidence.
5. Re-run codegen in the client you changed; commit generated outputs when the project expects them tracked.

## Commands

```bash
cd web && npm run codegen
cd mobile && npm run codegen
# optional overrides for web:
# GRAPHQL_SCHEMA_PATH=... NEXT_PUBLIC_GRAPHQL_URL=... npm run codegen
```

## Related

- [GraphQL architecture](../01-architecture/graphql-and-contracts.md)
- [Frontend web](frontend-web.md)
- [Mobile Expo](mobile-expo.md)
