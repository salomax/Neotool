---
title: System Architecture
type: architecture
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - package.json
  - nx.json
  - service/kotlin/settings.gradle.kts
  - web/package.json
  - mobile/package.json
  - infra/router/router.local.yaml
  - contracts/graphql/supergraph
load_when:
  - understanding the end-to-end Invistus system shape
  - onboarding to the monorepo topology
supersedes:
  - docs/02-architecture/
  - docs/01-overview/
schema: docs-metadata-v1
---

# System Architecture

Purpose: describe the current end-to-end system shape from repo layout and runtime config.

## Context

Invistus is a polyglot monorepo delivering personal-finance and related products through federated GraphQL APIs, a Next.js web client, an Expo mobile client, and batch workers.

## System map

```text
Clients (web/, mobile/)
    -> Apollo Router (infra/router)
        -> Subgraphs: security, banking, assets, financialdata, assistant, comms, mcp
Workers (workers/golang, workers/python) -> security token endpoint + domain services
Infra: docker, kubernetes, postgres, pgbouncer, vault, unleash, observability, terraform
```

## Authority

- Root scripts orchestrate `check:api`, `check:workers`, `check:web`, `check:mobile`.
- Gradle includes: common, common-batch, common-features, common-finance, security, assets, comms, assistant, financialdata, banking, mcp.
- Web: Next.js 16 / React 18 / Apollo / MUI. Mobile: Expo 52 / RN 0.76 / Apollo / Paper.
- Router config: `infra/router/router*.yaml`. Supergraph/subgraphs under `contracts/graphql/`.

## Rules

1. Treat the monorepo paths `web/`, `mobile/`, `service/kotlin/`, `workers/`, `contracts/`, and `infra/` as canonical — not legacy NeoTool `apps/` sketches.
2. Client traffic reaches subgraphs through Apollo Router (`infra/router`), except trusted internal hops such as MCP→banking (ADR-0017).
3. Kotlin Micronaut modules under `service/kotlin/` own domain APIs; Go/Python workers live under `workers/`.
4. Durable engineering docs live in `docs/`; feature behavior memory is out of scope here.
5. Verify module lists against `settings.gradle.kts` and root `package.json` scripts before documenting new services.

## Related

- [Service boundaries](service-boundaries.md)
- [GraphQL and contracts](graphql-and-contracts.md)
- [Infrastructure](infrastructure.md)
- [ADRs](../04-decisions/README.md)
- [README](README.md)
