---
title: Observability Architecture
type: architecture
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - infra/observability
  - infra/observability/prometheus
  - infra/observability/grafana
  - infra/observability/loki
  - infra/observability/tempo
  - infra/router/router.local.yaml
load_when:
  - adding metrics logs or traces
  - correlating router and subgraph telemetry
supersedes:
  - docs/10-observability/
schema: docs-metadata-v1
---

# Observability Architecture

Purpose: describe the self-hosted metrics, logs, and traces stack.

## Context

Observability is operated under `infra/observability` and correlated through Grafana. The router exports OTLP traces to Tempo in local config.

## Authority

- Directories: prometheus, grafana, loki, promtail, tempo, alerts.
- `router.local.yaml` enables OTLP export to `http://tempo:4317`.
- ADR-0013 records the stack decision.

## Rules

1. Prefer the in-repo stack (Prometheus, Grafana, Loki/Promtail, Tempo, alerts) over undocumented SaaS defaults.
2. Preserve W3C `traceparent` propagation on the router allowlist so client→router→subgraph traces can correlate.
3. Add alerts under `infra/observability/alerts` rather than one-off undocumented scrapes.
4. Operational incident steps belong in runbooks; this doc stays architectural.

## Related

- [Infrastructure](infrastructure.md)
- [ADR-0013](../04-decisions/0013-observability-architecture.md)
- [Runbooks](../05-runbooks/README.md)
