---
id: decision.0001-code-is-source-of-truth-for-agent-spec
status: canonical
kind: decision
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# ADR-0001: Code Is The Source Of Truth For Agent Specs

## Status

Accepted.

## Context

Existing feature documentation under `removed historical docs` may be outdated, partially correct, or contain broken links. Future AI-assisted development needs compact and reliable context for impact analysis.

## Decision

Create `spec/` as the canonical operational memory for agents.

Specs are derived from the current codebase, tests, GraphQL contracts, migrations, and implemented UI flows. Legacy feature docs may be cited only as historical references.

## Consequences

- Future feature work updates `spec/features/**`.
- Durable architecture decisions move to `spec/architecture/**` or `spec/decisions/**`.
- `removed historical docs` is not used as a source of truth.
- Reverse specs may start with partial confidence and become verified incrementally.
- Agents can load less context by following stable IDs, evidence paths, and graph edges.
