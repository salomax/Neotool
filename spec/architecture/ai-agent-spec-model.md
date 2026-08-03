---
id: architecture.ai-agent-spec-model
status: canonical
kind: architecture
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# AI Agent Spec Model

The Invistus spec is designed as a graph, not as a documentation tree.

## Goal

Give future agents enough current, evidence-backed context to perform impact analysis and maintenance without reading the whole repository or stale docs.

## Graph Nodes

- Domain
- Feature
- Permission
- GraphQL operation or type
- Backend service or use case
- Database table, migration, or constraint
- UI surface
- Worker or integration
- Test
- Architecture decision

## Graph Edges

- `belongs_to`
- `uses`
- `enforced_by`
- `guarded_by`
- `writes`
- `reads`
- `displayed_by`
- `tested_by`
- `depends_on`
- `affects`
- `supersedes`

## Practical Rule

A feature spec should be useful even when loaded alone, but cheap to expand through related specs and evidence paths.
