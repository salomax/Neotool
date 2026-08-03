---
title: ADR-0002 Containerized Architecture
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/docker/docker-compose.local.yml
  - infra/kubernetes
  - infra/observability
load_when:
  - choosing deployment topology
  - comparing containers vs serverless defaults
supersedes:
schema: docs-metadata-v1
---

# ADR-0002: Containerized Architecture

Purpose: record the durable decision and consequences for containerized architecture.

## Status

accepted

## Context

The platform needs portable local and production runtimes across services without defaulting to serverless per function.

## Decision

Use containers as the primary deployment model: Docker Compose for local stacks, Kubernetes for deployed environments, with observability co-located under `infra/observability`.

## Consequences

- Consistent local/prod shapes for JVM and worker services.
- Operational cost of cluster/Compose maintenance.
- Serverless remains non-default unless a future ADR changes this.

## Evidence

- `infra/docker/docker-compose.local.yml` and `infra/docker/docker-compose.staging.yml` exist.
- `infra/kubernetes/` and `infra/observability/` exist.

## Related

- [README](README.md)
- [Infrastructure architecture](../01-architecture/README.md)
- [Runbooks](../05-runbooks/README.md)
