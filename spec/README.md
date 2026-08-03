---
id: spec.root
status: canonical
kind: agent-spec
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Invistus Agent Spec

This directory is the operational memory for AI-assisted engineering in Invistus.

`spec/` is not narrative documentation. It is a compact, evidence-based graph of the current system behavior, optimized for impact analysis, maintenance, and future implementation work with low token usage.

## Source Of Truth

The source of truth is the current system, in this order:

1. Backend Kotlin code: business rules, permissions, validation, idempotency, invariants, transactions.
2. Database migrations and constraints: persisted model and hard data rules.
3. GraphQL schema and resolvers: external API contract.
4. Automated tests: protected expected behavior.
5. Frontend and mobile code: UX, field visibility, permission hints, client flows.
6. Historical docs: historical context only.

`removed historical docs` is not canonical. It may be stale, partially correct, or wrong. Use it only as `legacy_refs` when it helps locate historical intent.

## Non-Negotiable Rules

1. Specs are derived from code, tests, schemas, migrations, and runtime contracts.
2. Backend remains authoritative for permission enforcement, validation, idempotency, and domain invariants.
3. Frontend and mobile may expose permission hints for UX, but never become the enforcement boundary.
4. Every feature spec must cite evidence paths. If there is no evidence, mark the behavior as inferred or a known gap.
5. Specs must be short. Prefer precise bullets and stable IDs over long explanations.
6. Specs must describe current behavior, not desired behavior, unless a section explicitly says `Proposed`.
7. Architecture decisions and long-lived conventions belong in `spec/decisions/` or `spec/architecture/`, not inside feature narratives.
8. A delivery is not closed until affected specs are updated or explicitly marked as not impacted.

## Directory Contract

- `spec/index.md`: agent loading map and domain entrypoints.
- `spec/domains/`: domain boundaries and feature inventory.
- `spec/features/`: feature-level reverse specs, grouped by domain.
- `spec/contracts/`: metadata, confidence, evidence, and loading contracts.
- `spec/architecture/`: durable architecture and graph-engineering rules.
- `spec/decisions/`: ADR-style decisions that affect future analysis.
- `spec/templates/`: reusable templates and prompts for agents.

## Status Model

- `draft`: newly created and not reviewed.
- `canonical`: accepted as current operational memory.
- `deprecated`: describes behavior that no longer exists or should not be used.

## Confidence Model

- `verified`: confirmed by code plus relevant contract or test evidence.
- `partial`: confirmed by some code evidence, but coverage or surface mapping is incomplete.
- `inferred`: deduced from implementation shape, naming, or UI flow; needs validation.
- `stale-risk`: inherited from legacy documentation or conflicting evidence.

## Maintenance

Feature work updates `spec/features/**`.

Architecture, conventions, and durable cross-cutting decisions update `spec/architecture/**` or `spec/decisions/**`.

Human-facing explanatory docs may remain in `docs/`, but future agents should load `spec/` first for technical impact analysis.
