---
title: GraphQL and Contracts
type: architecture
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - contracts/graphql/supergraph
  - contracts/graphql/subgraphs
  - infra/router/router.local.yaml
  - infra/router/router.yaml
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingGraphQLFactory.kt
load_when:
  - changing GraphQL schema or federation wiring
  - debugging router header propagation
supersedes:
  - docs/06-contracts/
  - docs/02-architecture/
schema: docs-metadata-v1
---

# GraphQL and Contracts

Purpose: describe how GraphQL contracts and the Apollo Router compose subgraphs.

## Context

API contracts are GraphQL-first. Subgraph schemas compose into a supergraph served by Apollo Router. Clients and codegen consume the composed contract.

## Authority

- Subgraph folders: assets, assistant, banking, comms, financialdata, mcp, security.
- Router configs: `infra/router/router.yaml`, `router.local.yaml`, `router.staging.yaml`.
- Local router notes document propagate allowlist and Tempo export.

## Rules

1. Edit the owning subgraph schema under `contracts/graphql/subgraphs/<service>/` (and matching service resources) — not ad-hoc client-only types for server fields.
2. Keep supergraph artifacts under `contracts/graphql/supergraph/` in sync with composition tooling used by the repo.
3. Router propagates only allowlisted headers (`authorization`, `x-request-id`, `traceparent`, …). Do not put forgeable trust headers on the client edge (ADR-0017).
4. Subgraphs load schema via Micronaut GraphQL factories (example: banking, mcp).
5. Service-to-service GraphQL may use `GraphQLServiceClient` with service tokens; MCP→banking may bypass the router for provenance.

## Related

- [System](system.md)
- [GraphQL codegen](../02-development/README.md)
- [ADR-0017](../04-decisions/0017-mcp-direct-banking-dispatch-for-provenance.md)
- [Security](security.md)
