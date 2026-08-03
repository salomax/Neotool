---
title: ADR-0013 Observability Architecture
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/observability
  - infra/observability/prometheus
  - infra/observability/grafana
  - infra/observability/loki
  - infra/observability/tempo
load_when:
  - designing metrics/logs/traces stack
  - extending monitoring namespaces
supersedes:
schema: docs-metadata-v1
---

# ADR-0013: Observability Architecture

Purpose: record the durable decision and consequences for observability architecture.

## Status

accepted

## Context

Services need correlated metrics, logs, and traces without defaulting to a SaaS APM.

## Decision

Run a self-hosted observability stack under `infra/observability`: Prometheus, Grafana, Loki, Promtail, Tempo, and alerts as the three-pillar baseline.

## Consequences

- Matches containerized deployment (ADR-0002).
- Operational ownership of the monitoring stack is required.

## Evidence

- Directories exist for prometheus, grafana, loki, promtail, tempo, and alerts under `infra/observability/`.

## Related

- [README](README.md)
- [Observability architecture doc](../01-architecture/README.md)
