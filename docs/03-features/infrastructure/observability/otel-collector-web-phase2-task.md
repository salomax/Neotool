---
title: OTel Collector Gateway + Web Tracing (Phase 2)
type: task
category: observability
status: draft
version: 1.0.0
tags: [observability, tracing, otel-collector, web, security, tempo, grafana]
ai_optimized: true
search_keywords: [otel collector gateway, browser telemetry, public otlp endpoint, web tracing security, monitoring quota]
related:
  - 03-features/infrastructure/observability/trace-context-flamegraph-task.md
  - 03-features/infrastructure/observability/trace-context-flamegraph-backlog.md
  - 03-features/infrastructure/observability/observability-vps-k3s.md
last_updated: 2026-02-25
---

# Phase 2 Task: Public Ingestion for Web Traces

## Goal

Add secure browser-to-backend trace ingestion so Grafana Tempo shows full request flow:

- user click in web app
- GraphQL Router
- downstream Kotlin services
- (optional) DB and external calls

This phase starts after Phase 1 (Tempo + backend traces) is stable.

## Why this phase exists

Tempo is internal storage/query for traces, not a hardened public ingestion tier. Browser telemetry requires internet-facing controls (CORS, rate limit, auth strategy, abuse controls, data minimization). Industry practice is to expose a gateway/collector, not Tempo directly.

## February 2026 Industry Patterns (applied here)

1. Public telemetry endpoint is a collector/gateway, not the trace store.
2. Browser SDK uses OTLP/HTTP; server-side services usually use OTLP/gRPC.
3. W3C `traceparent`/`tracestate` is propagated end-to-end; `baggage` is optional and tightly controlled.
4. Collector pipeline applies security and hygiene:
   - memory limiter
   - batch processor
   - attribute redaction/allowlist
   - per-tenant/per-origin enforcement
5. Tempo stays private (`ClusterIP`) and receives traces only from trusted in-cluster producers (services, router, collector).
6. Sampling is explicit and environment-based (always-on only for low volume; otherwise parent-based ratio and/or tail sampling in gateway tier).
7. Frontend telemetry payloads avoid PII/secrets; sensitive fields are dropped before export.

## Target Architecture

1. Browser sends spans via OTLP/HTTP to public endpoint:
   - `https://telemetry.invistus.com.br/v1/traces`
2. Public route forwards only to OTel Collector gateway in `monitoring`.
3. Collector validates/processes/rate-limits and exports to Tempo:
   - `tempo.monitoring.svc.cluster.local:4317`
4. Grafana queries Tempo and renders trace waterfall/flame chart.

## Security Controls (required)

- Keep Tempo service internal (`ClusterIP` only, no public ingress).
- Restrict collector ingress:
  - HTTPS only
  - strict CORS allowlist for production web origin(s)
  - request body size limit
  - rate limit per IP and/or token
  - WAF rules at edge if available
- Do not treat browser token/DSN as secret:
  - use it for tenant attribution/abuse control only
  - rotate if abused
- Drop sensitive attributes in collector:
  - auth headers
  - cookies
  - user identifiers not needed for tracing
  - free-text fields that may include PII
- Enforce baggage policy:
  - allowlist known safe keys
  - drop unknown keys
  - never include secrets in baggage

## Implementation Tasks

## A. Collector Gateway in `monitoring`

- Create Flux app manifests under:
  - `invistus-flux/infra/kubernetes/flux/apps/monitoring/otel-collector/`
- Include in:
  - `invistus-flux/infra/kubernetes/flux/apps/monitoring/kustomization.yaml`
- Manifests required:
  - `configmap.yaml` (collector pipeline config)
  - `deployment.yaml` (gateway deployment, 1 replica initially)
  - `service.yaml` (`4317` gRPC, `4318` HTTP)
  - `hpa.yaml` (optional for phase 2b)

Collector config baseline:

- receivers:
  - `otlp`:
    - `http` on `0.0.0.0:4318` (browser)
    - `grpc` on `0.0.0.0:4317` (in-cluster future use)
- processors:
  - `memory_limiter`
  - `batch`
  - `attributes` (drop/redact sensitive keys)
  - `resource` (set `deployment.environment=production`)
- exporters:
  - `otlp` to Tempo (`tempo.monitoring.svc.cluster.local:4317`)
- extensions:
  - health check
  - pprof (internal only)
  - zpages (internal only, optional)

## B. Public route for browser telemetry

- Add dedicated route (do not reuse Tempo service directly):
  - example: `telemetry.invistus.com.br`
- Route only `/v1/traces` to collector HTTP receiver.
- Add edge protections:
  - TLS termination
  - CORS allowlist
  - rate limiting
  - request size cap

## C. Web instrumentation setup

- Add OpenTelemetry JS packages in web app:
  - `@opentelemetry/sdk-trace-web`
  - `@opentelemetry/instrumentation-fetch`
  - `@opentelemetry/instrumentation-xml-http-request`
  - `@opentelemetry/exporter-trace-otlp-http`
  - `@opentelemetry/propagator-b3` (optional; not default)
- Configure provider:
  - service name: `invistus-web`
  - resource attrs: env/version
  - sampler: parent-based + ratio for production
- Configure exporter endpoint:
  - `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=https://telemetry.invistus.com.br/v1/traces`
- Inject `traceparent` on GraphQL/API calls and preserve server continuation.
- Add denylist/allowlist for custom span attributes from frontend code.

## D. Router/service continuity checks

- Keep header propagation in router:
  - `traceparent`
  - `tracestate`
  - `baggage`
- Validate every backend service continues context (no trace restarts).
- Confirm trace has frontend root span plus downstream spans.

## E. Quota update for `monitoring` namespace

Current quota file:

- `invistus-flux/infra/kubernetes/flux/infrastructure/namespace/monitoring-resource-quota.yaml`

Current post-Tempo envelope (expected steady-state):

- requests: ~`700m CPU`, ~`1184Mi memory`
- limits: ~`2700m CPU`, ~`2944Mi memory`
- pods: ~`8`
- PVC: `4`
- storage requests: `19Gi`

Collector minimum profile (phase 2 initial):

- requests: `50m CPU`, `128Mi memory`
- limits: `200m CPU`, `256Mi memory`
- pods: `+1`
- PVC/storage: none required

Result with collector:

- requests: ~`750m CPU`, ~`1312Mi memory`
- limits: ~`2900m CPU`, ~`3200Mi memory`
- pods: ~`9`

Quota adjustment needed (minimal safe):

- `requests.memory`: raise from `1200Mi` to at least `1400Mi`
- `limits.memory`: raise from `3200Mi` to `3400Mi` (or `3600Mi` for margin)
- `pods`: keep `11` (still enough)
- CPU quota can remain unchanged for phase 2 minimum profile

## F. Validation and rollout

- Pre-prod checks:
  - collector health endpoint ready
  - trace ingestion volume accepted under rate limits
  - no PII/secrets in exported spans
- Functional checks:
  - click in web creates client span
  - same trace id appears in router and service spans
  - Grafana trace view shows full waterfall/flame path
- Load checks:
  - 50 RPS synthetic browser traces for 10 min
  - monitor collector CPU/memory and dropped spans
- Rollout:
  - start 10% web traffic sampling
  - increase to target sampling after 24h clean run

## Acceptance Criteria

- Public telemetry endpoint exists and points to collector, not Tempo.
- Tempo remains private and only reachable in-cluster.
- Web app emits traces with W3C context and links to backend trace tree.
- Collector applies memory limiter, batching, and attribute redaction.
- Monitoring quota is updated to schedule collector without starving workloads.
- Grafana flame chart can identify bottlenecks from click to backend response.

## Out of Scope (this phase)

- Full RUM product replacement (session replay, frontend logs, errors in one suite).
- Long retention for high-volume frontend traces.
- Multi-region trace ingestion.

## References (checked on 2026-02-25)

- OpenTelemetry JS exporters: https://opentelemetry.io/docs/languages/js/exporters/
- OpenTelemetry Collector deployment patterns: https://opentelemetry.io/docs/collector/deployment/
- OpenTelemetry Collector scaling guidance: https://opentelemetry.io/docs/collector/scaling/
- OTel security guidance: https://opentelemetry.io/docs/security/
- OTel hosting best practices: https://opentelemetry.io/docs/security/hosting-best-practices/
- OTel sensitive data handling: https://opentelemetry.io/docs/security/handling-sensitive-data/
- Grafana Tempo deployment modes: https://grafana.com/docs/tempo/latest/setup/deployment/
- Grafana Tempo sizing guide: https://grafana.com/docs/tempo/latest/setup/size/
- Grafana Alloy Faro receiver (public intake controls): https://grafana.com/docs/alloy/latest/reference/components/faro/faro.receiver/
- W3C Trace Context: https://www.w3.org/TR/trace-context/
- W3C Baggage: https://www.w3.org/TR/baggage/
