---
id: architecture.reverse-spec-from-code
status: canonical
kind: architecture
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Reverse Spec From Code

Reverse spec means writing the operational specification from the current implementation.

## Authority Order

1. Kotlin backend behavior.
2. Database migrations and constraints.
3. GraphQL schema and resolver contracts.
4. Automated tests.
5. Web and mobile UX.
6. Historical docs as historical references only.

## Process

1. Pick one domain.
2. Map evidence entrypoints for backend, GraphQL, database, tests, frontend, mobile, and workers.
3. Identify feature candidates from implemented behavior.
4. Write one feature spec per stable behavior cluster.
5. Mark confidence based on evidence quality.
6. Record gaps instead of inventing missing intent.
7. Promote to `canonical` only after review against cited evidence.

## Anti-Patterns

- Copying `removed historical docs` into `spec/features`.
- Writing desired behavior as if it already exists.
- Treating frontend permission hints as backend enforcement.
- Creating broad feature specs that require agents to load too much context.
- Omitting evidence because the behavior seems obvious.
