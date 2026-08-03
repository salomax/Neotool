---
id: contract.confidence-model
status: canonical
kind: quality-contract
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Confidence Model

Confidence tells future agents how much they can rely on a spec without re-reading broad code.

## Levels

`verified`

Use only when the behavior is confirmed by implementation evidence plus at least one relevant contract or test surface. Examples: backend service plus GraphQL resolver, backend service plus integration test, migration plus repository plus test.

`partial`

Use when meaningful code evidence exists, but the behavior is not fully covered across backend, contract, tests, or UI surfaces.

`inferred`

Use when behavior is deduced from names, UI flow, entity shape, or partial implementation without enough direct evidence.

`stale-risk`

Use when the behavior comes from historical docs, conflicts with code, or cannot be found in the current implementation.

## Promotion Rules

- `inferred` can become `partial` after code evidence is cited.
- `partial` can become `verified` after enough contract or test evidence is cited.
- `stale-risk` must not become `canonical` without code evidence.
- `canonical` specs may still have `partial` confidence for isolated subsections if gaps are explicit.
