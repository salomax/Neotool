---
id: contract.agent-loading
status: canonical
kind: context-contract
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Agent Loading Contract

This contract exists to reduce token usage while preserving technical quality.

## Default Loading Order

1. `spec/README.md`
2. `spec/index.md`
3. One domain file from `spec/domains/`
4. Only impacted feature specs from `spec/features/<domain>/`
5. Only relevant contracts or architecture docs

## Do Not Load By Default

- All of `docs/`
- All of `spec/features/`
- Legacy `removed historical docs`
- Full source files when a cited narrow function or class is enough

## When To Read Code

Read code when:

- the spec confidence is not `verified`;
- the task changes behavior, permission, validation, idempotency, persistence, or contract;
- evidence paths are missing or stale;
- the implementation contradicts the spec.

## Graph Engineering Rules

Agents should think in nodes and edges:

- Feature -> uses -> Contract
- Feature -> enforced_by -> Backend Service
- Feature -> writes -> Database Object
- Feature -> guarded_by -> Permission
- Feature -> displayed_by -> UI Surface
- Feature -> tested_by -> Test
- Decision -> affects -> Feature

Load neighboring nodes only when the task crosses that edge.
