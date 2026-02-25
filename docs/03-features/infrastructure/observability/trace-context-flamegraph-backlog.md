---
title: Trace Context Flamegraph Implementation Backlog
type: backlog
category: observability
status: draft
version: 1.0.0
tags: [observability, tracing, tempo, grafana, backlog, w3c, quota]
ai_optimized: true
search_keywords: [tempo backlog, grafana flame chart backlog, monitoring quota, traceparent backlog]
related:
  - 03-features/infrastructure/observability/trace-context-flamegraph-task.md
  - 03-features/infrastructure/observability/otel-collector-web-phase2-task.md
  - 03-features/infrastructure/observability/implementation-backlog.md
  - 03-features/infrastructure/observability/observability-vps-k3s.md
last_updated: 2026-02-25
---

# Trace Context Flamegraph Implementation Backlog

> Purpose: Implement end-to-end tracing and Grafana flame chart visualization with minimum scope, including namespace quota readiness.

## Assumptions

- Observability workloads run in namespace `monitoring`.
- Target environment is single-node VPS/K3S with limited CPU and memory.
- Goal is request bottleneck analysis, not full APM feature coverage.

## Milestone Plan

| Milestone | Scope | Exit Condition |
|---|---|---|
| M0 | Namespace and quota readiness | `monitoring` quota supports Tempo and tracing pods |
| M1 | Tempo deployment | Tempo is running and reachable from Grafana and OTLP producers |
| M2 | Trace propagation and export | Router and services send coherent traces with W3C headers |
| M3 | Grafana flame chart | Grafana displays trace waterfall/flame chart for real requests |
| M4 | Verification and hardening | End-to-end checks pass and troubleshooting notes are documented |

## M0: Namespace and Quota Readiness

### Task M0-T1: Confirm `monitoring` namespace wiring

Files:
- `invistus-flux/infra/kubernetes/flux/infrastructure/namespace/namespace.yaml`
- `invistus-flux/infra/kubernetes/flux/infrastructure/namespace/monitoring-namespace.yaml`
- `invistus-flux/infra/kubernetes/flux/clusters/production/infrastructure.yaml`

Done when:
- [ ] `monitoring` namespace manifest exists and is applied by Flux.
- [ ] Monitoring kustomization targets `monitoring` resources.

### Task M0-T2: Update quota for tracing components

Files:
- `invistus-flux/infra/kubernetes/flux/infrastructure/namespace/monitoring-resource-quota.yaml`

Recommended minimum additional budget:
- Tempo only: `+100m CPU`, `+256Mi memory` requests
- Tempo + OTel Collector: `+200m CPU`, `+384Mi memory` requests

Done when:
- [ ] Quota explicitly reserves resources for new tracing pods.
- [ ] Quota changes are committed with rationale in PR notes.

## M1: Deploy Tempo

### Task M1-T1: Add Tempo manifests

Files:
- `invistus-flux/infra/kubernetes/flux/apps/monitoring/tempo/` (new)
- `invistus-flux/infra/kubernetes/flux/apps/monitoring/kustomization.yaml`

Done when:
- [ ] Tempo Deployment/StatefulSet, Service, and PVC are defined.
- [ ] Tempo is included in monitoring kustomization.

### Task M1-T2: Configure retention and storage limits

Files:
- `invistus-flux/infra/kubernetes/flux/apps/monitoring/tempo/*`

Done when:
- [ ] Retention is explicitly configured for VPS constraints.
- [ ] Storage request/limit is bounded and reviewed.

## M2: Propagation and OTLP Export

### Task M2-T1: W3C headers through Router

Files:
- `invistus/infra/router/router.yaml`
- `invistus/infra/router/router.local.yaml`
- `invistus/infra/router/router.staging.yaml`

Done when:
- [ ] Router propagates `traceparent`, `tracestate`, and `baggage`.

### Task M2-T2: Export traces from Router and services

Files:
- Router config and deployment env files
- Kotlin service configs/dependencies for OTLP

Done when:
- [ ] Router exports spans via OTLP.
- [ ] Kotlin services export spans via OTLP with `service.name`, `deployment.environment`, `service.version`.

## M3: Grafana Flame Chart

### Task M3-T1: Provision Tempo datasource

Files:
- `invistus/infra/observability/grafana/provisioning/datasources/datasource.yml`
- `invistus-flux/infra/kubernetes/flux/apps/monitoring/grafana/configmap-datasources.yaml`

Done when:
- [ ] Grafana has a working Tempo datasource in local and K3S.

### Task M3-T2: Provision tracing dashboard

Files:
- `invistus/infra/observability/grafana/dashboards/distributed-tracing-flamegraph.json`

Done when:
- [ ] Dashboard supports filtering by `service.name`, operation/route, and duration.
- [ ] Representative request trace can be opened from dashboard and inspected in waterfall/flame chart.

## M4: Verification and Hardening

### Task M4-T1: End-to-end verification

Done when:
- [ ] Request with inbound `traceparent` preserves trace id across Router and two services.
- [ ] Request without `traceparent` creates valid new root trace.
- [ ] Slowest span in a trace is identifiable by duration and service/operation.

### Task M4-T2: Runbook notes

Files:
- `invistus/docs/03-features/infrastructure/observability/observability-vps-k3s.md`

Done when:
- [ ] Troubleshooting notes include missing spans, broken context propagation, datasource connectivity, and quota-related pod scheduling failures.
