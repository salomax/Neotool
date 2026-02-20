---
title: W3C Trace Context and Grafana Flamegraph Task
type: task
category: observability
status: draft
version: 1.0.0
tags: [observability, tracing, w3c, traceparent, grafana, tempo, flamegraph]
ai_optimized: true
search_keywords:
  [
    traceparent,
    tracestate,
    baggage,
    distributed tracing,
    grafana flamegraph,
    tempo,
  ]
related:
  - 03-features/infrastructure/observability/implementation-backlog.md
  - 03-features/infrastructure/observability/observability-vps-k3s.md
  - 10-observability/observability-overview.md
last_updated: 2026-02-17
---

# Task: Implement Industry-Standard Trace Context + Grafana Flamegraph

## Context (Current Project State)

- Apollo Router already propagates `traceparent` in:
  - `infra/router/router.yaml`
  - `infra/router/router.local.yaml`
  - `infra/router/router.staging.yaml`
- The stack already has Prometheus, Loki, and Grafana, but no trace backend datasource (Tempo/Jaeger) in:
  - `infra/observability/grafana/provisioning/datasources/datasource.yml`
  - `neotool-flux/infra/kubernetes/flux/apps/observability/grafana/deployment.yaml`
- Services already log `otel.trace_id` and `otel.span_id`, but end-to-end distributed tracing is not fully standardized.

## Task ID

- `OBS-TRACE-001`

## Goal

Implement W3C Trace Context end-to-end (`traceparent`, `tracestate`, `baggage`) and deliver a Grafana trace dashboard/experience with flamegraph-style span visualization.

## Scope

- [ ] Standardize HTTP trace propagation to W3C Trace Context across Router and services.
- [ ] Add a tracing backend (Grafana Tempo recommended) for local and K3S/Flux environments.
- [ ] Export traces from services and Router to the tracing backend (OTLP).
- [ ] Provision Grafana trace datasource and a flamegraph-focused tracing dashboard.
- [ ] Enable log-trace correlation from Loki logs using `otel_trace_id`.

## Implementation Checklist

### 1) W3C trace propagation

- [ ] Update Router header propagation to include `tracestate` and `baggage` in all env configs:
  - `infra/router/router.yaml`
  - `infra/router/router.local.yaml`
  - `infra/router/router.staging.yaml`
- [ ] Ensure inbound/outbound service propagation uses W3C Trace Context as default.
- [ ] Validate behavior when `traceparent` is missing (new root trace is created).

### 2) Tracing backend (Tempo)

- [ ] Add Tempo container to local stack:
  - `infra/docker/docker-compose.local.yml`
- [ ] Add Tempo manifests to Flux/K3S observability app:
  - `neotool-flux/infra/kubernetes/flux/apps/observability/tempo/`
  - `neotool-flux/infra/kubernetes/flux/apps/observability/kustomization.yaml`
- [ ] Configure retention/storage defaults appropriate for VPS constraints.

### 3) OTEL export and service instrumentation

- [ ] Add/enable tracing dependencies/config in Kotlin services (`security`, `financialdata`, `comms`, `assets`, `assistant`) for OTLP export.
- [ ] Set required resource attributes (`service.name`, `deployment.environment`, `service.version`).
- [ ] Configure sampling strategy by environment (e.g., dev=always_on, staging/prod=parentbased_traceidratio).

### 4) Grafana datasource + flamegraph dashboard

- [ ] Add Tempo datasource provisioning for local Grafana:
  - `infra/observability/grafana/provisioning/datasources/datasource.yml`
- [ ] Add Tempo datasource provisioning for K3S Grafana config:
  - `neotool-flux/infra/kubernetes/flux/apps/observability/grafana/deployment.yaml`
- [ ] Create dashboard JSON for tracing (search + latency + span waterfall/flamegraph style):
  - `infra/observability/grafana/dashboards/distributed-tracing-flamegraph.json`

### 5) Log/trace correlation

- [ ] Configure Loki derived field or Grafana trace-to-logs linking using `otel_trace_id`.
- [ ] Validate one-click navigation: logs -> trace and trace -> related logs.

## Acceptance Criteria

- [ ] A request entering Router with existing `traceparent` keeps the same trace ID across at least two downstream services.
- [ ] `tracestate` and `baggage` are propagated alongside `traceparent`.
- [ ] A request without `traceparent` generates a valid new trace and is visible in Grafana/Tempo.
- [ ] Grafana includes a working Tempo datasource in both local and K3S setups.
- [ ] Flamegraph/waterfall trace visualization is available for service spans.
- [ ] Logs include `otel_trace_id` and can deep-link to the corresponding trace.

## Verification Steps

- [ ] Send test request with explicit `traceparent` through Router and confirm same trace ID in service logs.
- [ ] Query Tempo by trace ID and confirm full span tree is visible.
- [ ] Open Grafana tracing dashboard and verify p95 endpoint latency + representative trace samples.
- [ ] Confirm log-to-trace navigation works from Loki log lines.

## Definition of Done

- [ ] All config and manifests are versioned in repo.
- [ ] Dashboards/datasources are provisioned automatically (no manual Grafana setup).
- [ ] Documentation updated in observability backlog/runbook with final commands and troubleshooting notes.
