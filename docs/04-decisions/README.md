---
title: Architecture Decisions
type: index
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - docs/04-decisions/
  - package.json
  - service/kotlin/settings.gradle.kts
load_when:
  - researching why a technical choice was made
  - adding or normalizing an ADR
supersedes:
schema: docs-metadata-v1
---

# Architecture Decisions

Purpose: index for Architecture Decision Records.

## Authority

- Durable decisions are the files in this directory after verification against current code/config.
- ADR consequences cite repo evidence.

## Rules

1. One canonical file per ADR number.
2. Every ADR has an explicit status (`accepted`, `superseded`, `proposed`, or `rejected`).
3. Kotlin/Micronaut keeps `0003`; Nx tooling is `0018`.
4. Interservice security is `0008`.
5. ADRs record decisions and consequences, not implementation guides.

## Documents

| ADR | Status | File |
|---|---|---|
| Template | active | [adr-template.md](adr-template.md) |
| 0001 Monorepo Architecture | accepted | [0001-monorepo-architecture.md](0001-monorepo-architecture.md) |
| 0002 Containerized Architecture | accepted | [0002-containerized-architecture.md](0002-containerized-architecture.md) |
| 0003 Kotlin Micronaut Backend | accepted | [0003-kotlin-micronaut-backend.md](0003-kotlin-micronaut-backend.md) |
| 0004 TypeScript Next.js Frontend | accepted | [0004-typescript-nextjs-frontend.md](0004-typescript-nextjs-frontend.md) |
| 0005 PostgreSQL Database | accepted | [0005-postgresql-database.md](0005-postgresql-database.md) |
| 0006 Frontend Authorization Layer | accepted | [0006-frontend-authorization-layer.md](0006-frontend-authorization-layer.md) |
| 0007 Asset Service Cloudflare R2 | accepted | [0007-asset-service-cloudflare-r2.md](0007-asset-service-cloudflare-r2.md) |
| 0008 Interservice Security | accepted | [0008-interservice-security.md](0008-interservice-security.md) |
| 0009 Resource Ownership Strategy | superseded | [0009-resource-ownership-strategy.md](0009-resource-ownership-strategy.md) |
| 0010 React Native Expo Mobile | accepted | [0010-react-native-expo-mobile.md](0010-react-native-expo-mobile.md) |
| 0011 LLM Orchestrator Service | accepted | [0011-llm-orchestrator-service.md](0011-llm-orchestrator-service.md) |
| 0012 Web Analytics Tooling | accepted | [0012-web-analytics-tooling.md](0012-web-analytics-tooling.md) |
| 0013 Observability Architecture | accepted | [0013-observability-architecture.md](0013-observability-architecture.md) |
| 0014 Web/Mobile Code Sharing | accepted | [0014-web-mobile-code-sharing-strategy.md](0014-web-mobile-code-sharing-strategy.md) |
| 0015 MCP Tool Surface / Threat Model | accepted | [0015-mcp-tool-surface-and-agent-threat-model.md](0015-mcp-tool-surface-and-agent-threat-model.md) |
| 0016 OAuth Login/Consent UI | accepted | [0016-oauth-login-consent-ui-rendering.md](0016-oauth-login-consent-ui-rendering.md) |
| 0017 MCP Direct Banking Dispatch | accepted | [0017-mcp-direct-banking-dispatch-for-provenance.md](0017-mcp-direct-banking-dispatch-for-provenance.md) |
| 0018 Nx Monorepo Tooling | accepted | [0018-nx-monorepo-tooling.md](0018-nx-monorepo-tooling.md) |
| 0019 AccountId Tenant Boundary | accepted | [0019-accountid-tenant-boundary.md](0019-accountid-tenant-boundary.md) |
| 0020 External GitOps Inventory Boundary | accepted | [0020-external-gitops-inventory-boundary.md](0020-external-gitops-inventory-boundary.md) |
| 0021 Dual Metadata Schema (docs/ vs spec/) | proposed | [0021-dual-metadata-schema-docs-vs-spec.md](0021-dual-metadata-schema-docs-vs-spec.md) |

## Related

- [../README.md](../README.md)
- [../01-architecture/README.md](../01-architecture/README.md)
