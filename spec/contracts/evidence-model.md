---
id: contract.evidence-model
status: canonical
kind: evidence-contract
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Evidence Model

Evidence is the bridge between source code and agent memory.

## Evidence Sources

- `backend`: Kotlin services, domain logic, repositories, resolvers, security wiring.
- `graphql`: schema files, operations, generated contracts, resolver entrypoints.
- `database`: Flyway migrations, schema constraints, indexes, data model.
- `tests`: unit, integration, contract, E2E, mobile, or frontend tests.
- `frontend`: Next.js pages, components, hooks, GraphQL operations, UX permission hints.
- `mobile`: Expo screens, hooks, navigation, GraphQL operations, offline behavior.
- `workers`: jobs, importers, schedulers, external data ingestion.

## Citation Rules

- Cite repository-relative paths.
- Prefer specific files over directories.
- Include line numbers when a behavior is narrow or easy to misread.
- Do not cite generated files unless generated output is the only consumed contract.
- Do not cite historical docs as evidence for current behavior.

## Gap Rules

If a behavior is implemented but untested, add it to `Known Gaps`.

If frontend assumes a permission or field that backend does not enforce, document the mismatch explicitly.

If historical docs describe behavior not found in code, mark it as `stale-risk` or `deprecated`.
