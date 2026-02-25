---
title: Grafana Flame Chart via W3C Trace Context
type: task
category: observability
status: draft
version: 1.2.0
tags: [observability, tracing, w3c, traceparent, grafana, tempo, flamegraph]
ai_optimized: true
search_keywords: [traceparent, tracestate, baggage, distributed tracing, grafana flamegraph, tempo]
related:
  - 03-features/infrastructure/observability/trace-context-flamegraph-backlog.md
  - 03-features/infrastructure/observability/otel-collector-web-phase2-task.md
  - 03-features/infrastructure/observability/implementation-backlog.md
  - 03-features/infrastructure/observability/observability-vps-k3s.md
  - 10-observability/observability-overview.md
last_updated: 2026-02-25
---

# Task: Deliver Request Bottleneck Flame Chart in Grafana

## Goal

Provide a Grafana trace view (waterfall/flame chart) that shows end-to-end request timing across Router and services so bottlenecks are visible.

## Decision: Is Tempo Needed in Dev or Prod?

- Tempo is required anywhere we want real bottleneck analysis.
- Local-only Tempo is useful for developer debugging, but it is not enough for production bottleneck analysis.
- Recommended for this project:
  - Local: enable Tempo for fast iteration.
  - K3S: enable Tempo in the `monitoring` namespace with conservative retention and sampling.

## Required Scope (Only What Is Needed)

- [ ] Propagate W3C headers end-to-end in Router and services:
  - `traceparent`
  - `tracestate`
  - `baggage`
- [ ] Export spans from Router and Kotlin services to Tempo over OTLP.
- [ ] Provision Tempo in:
  - local docker stack: `infra/docker/docker-compose.local.yml`
  - K3S/Flux monitoring stack: `invistus-flux/infra/kubernetes/flux/apps/monitoring/tempo/` and `invistus-flux/infra/kubernetes/flux/apps/monitoring/kustomization.yaml`
- [ ] Update ResourceQuota for `monitoring` namespace to include Tempo (and OTel Collector if enabled):
  - `invistus-flux/infra/kubernetes/flux/infrastructure/namespace/monitoring-resource-quota.yaml`
- [ ] Provision Grafana Tempo datasource in:
  - `infra/observability/grafana/provisioning/datasources/datasource.yml`
  - `invistus-flux/infra/kubernetes/flux/apps/monitoring/grafana/configmap-datasources.yaml`
- [ ] Add one tracing dashboard JSON with filters by `service.name`, operation/route, and duration:
  - `infra/observability/grafana/dashboards/distributed-tracing-flamegraph.json`

## Acceptance Criteria

- [ ] A single request is visible in Grafana as one trace containing:
  - Router span
  - at least two service spans
  - downstream spans (HTTP/DB) where applicable
- [ ] Existing inbound `traceparent` is preserved across hops.
- [ ] `tracestate` and `baggage` are forwarded with the trace context.
- [ ] Missing inbound `traceparent` creates a valid new root trace.
- [ ] Grafana Tempo datasource works in both local and K3S.
- [ ] `monitoring` namespace quota is updated and allows scheduling Tempo and related tracing pods.
- [ ] Bottleneck identification is possible from flame chart (largest span duration is obvious).

## Verification Steps

- [ ] Send a request with explicit `traceparent` through Router and verify one trace ID across services.
- [ ] Send a request without `traceparent` and verify a new trace is created.
- [ ] Open Grafana trace view for that trace and confirm waterfall/flame chart contains full span tree.
- [ ] Confirm the slowest span is identifiable by duration and service/operation labels.

## Definition of Done

- [ ] All required config/manifests/dashboards are committed.
- [ ] Grafana datasources and dashboard are provisioned automatically.
- [ ] Team can open a trace in Grafana and identify request bottlenecks without manual setup.

## Header Semantics in This Task

- `traceparent`: trace identity and parent span linkage.
- `tracestate`: vendor-specific trace metadata that must be forwarded unchanged.
- `baggage`: request-scoped key/value context; keep small and do not place secrets or PII.
