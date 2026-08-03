---
id: spec.domains
status: canonical
kind: domain-index
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Domains

Domains are the first routing layer for agent context. Each domain file defines boundaries, known feature groups, evidence entrypoints, and ownership of cross-cutting behavior.

## Domain List

- `security` — **pilot complete** (feature tree under `spec/features/security/`)
- `transactions`
- `investment-portfolio`
- `market-data`
- `financial-planning`

Avoid an `others` domain. If a feature does not fit, either create a precise domain or place it in `financial-planning` only when it is a planning or utility feature.

## Domain File Contract

Each domain file must include:

- Boundary: what belongs and what does not.
- Feature groups: expected feature specs under `spec/features/<domain>/`.
- Source entrypoints: backend, GraphQL, database, tests, frontend, mobile.
- Cross-cutting concerns: permissions, idempotency, external integrations, observability.
- Known gaps: missing specs, missing tests, or unresolved evidence.
