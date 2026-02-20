---
title: ADR-0013 Observability Architecture
type: adr
category: infrastructure
status: accepted
version: 1.0.0
tags:
  [
    observability,
    monitoring,
    prometheus,
    grafana,
    loki,
    opentelemetry,
    tempo,
    tracing,
    metrics,
    logs,
  ]
related:
  - adr/0002-containerized-architecture.md
  - adr/0003-kotlin-micronaut-backend.md
  - adr/0004-typescript-nextjs-frontend.md
  - adr/0012-web-analytics-tooling.md
---

# ADR-0013: Observability Architecture

## Status

Accepted

## Context

neotool runs on a single K3S node (Hostinger VPS, 8 CPU / 31Gi RAM) with a federated GraphQL architecture: Next.js → Cloudflare → Traefik → Apollo Router → Micronaut subgraphs → PostgreSQL. The previous observability stack (Prometheus + Grafana + Loki + Promtail) was disabled because it consumed ~3.2Gi RAM and 1600m CPU — roughly 20% of the node's resources — while running in the `production` namespace, directly competing with application workloads.

Beyond resource consumption, the previous setup had architectural gaps:

- No node-level metrics (no node-exporter, no kube-state-metrics)
- No visibility into whether the VPS needs scaling (CPU/memory/disk pressure)
- No distributed tracing — all GraphQL requests hit `POST /graphql`, making it impossible to identify which operations or subgraphs are slow
- No log-level controls — Micronaut services were emitting DEBUG-level logs in production
- No log retention policy — unbounded storage growth

### Requirements

- **Node health visibility**: CPU, memory, disk, network usage to decide when to add worker nodes
- **Kubernetes resource visibility**: Pod status, restarts, resource consumption per pod
- **Centralized logging**: Aggregated logs from all pods, queryable by namespace/pod/level
- **Log retention**: Automatic purge mechanism to prevent unbounded storage growth
- **Distributed tracing**: End-to-end request traces across the full federated GraphQL stack
- **GraphQL operation visibility**: Per-operation latency, error rates, and subgraph breakdown
- **Lightweight footprint**: Total o11y stack must use < 1.5Gi RAM and < 1 CPU to preserve production headroom
- **Isolation**: Observability must not compete with production workloads for resources

### Constraints

- **Single VPS**: All workloads share one node — resource efficiency is critical
- **Flux CD GitOps**: All deployments are managed via Flux Kustomizations in the `neotool-flux` repository
- **No Prometheus Operator**: Using vanilla Prometheus with static scrape configs (lighter than kube-prometheus-stack)
- **Budget**: Prefer open-source, self-hosted tools over SaaS
- **Cloudflare limitation**: Cloudflare does not participate in W3C `traceparent` propagation — traces start at the Traefik ingress layer

## Decision

We will implement a **phased observability architecture** using the **Grafana open-source stack** (Prometheus, Loki, Tempo) in a dedicated `monitoring` namespace, with OpenTelemetry for distributed tracing.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        monitoring namespace                          │
│                                                                      │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────────────┐  │
│  │  Prometheus   │  │     Loki      │  │    Grafana Tempo         │  │
│  │  (metrics)    │  │    (logs)     │  │    (traces)              │  │
│  │  256Mi/200m   │  │  128Mi/100m   │  │    128Mi/100m            │  │
│  │  7d retention │  │  7d retention │  │    7d retention           │  │
│  └──────┬────────┘  └──────┬────────┘  └──────────┬───────────────┘  │
│         │                  │                       │                  │
│    scrapes from       receives from           receives from          │
│         │                  │                       │                  │
│  ┌──────▼────────┐  ┌──────▼────────┐  ┌──────────▼───────────────┐  │
│  │ Node Exporter  │  │   Promtail   │  │  OpenTelemetry Collector │  │
│  │ (DaemonSet)    │  │ (DaemonSet)  │  │  128Mi/100m              │  │
│  │ 64Mi/50m       │  │  64Mi/50m    │  └──────────────────────────┘  │
│  └────────────────┘  └──────────────┘                                │
│  ┌────────────────┐                                                  │
│  │kube-state-      │         ┌────────────────────────┐              │
│  │metrics 64Mi/50m │         │       Grafana           │              │
│  └────────────────┘         │  Metrics + Logs + Traces │              │
│                              │  Flame charts, Waterfall │              │
│                              │  128Mi/100m              │              │
│                              └────────────────────────┘              │
└─────────────────────────────────────────────────────────────────────┘
```

### Dedicated Monitoring Namespace

All observability components run in a `monitoring` namespace with a dedicated ResourceQuota:

```yaml
spec:
  hard:
    requests.cpu: "700m"
    limits.cpu: "2"
    requests.memory: "1Gi"
    limits.memory: "2Gi"
    pods: "10"
    persistentvolumeclaims: "5"
    requests.storage: "20Gi"
```

**Rationale**: Resource isolation prevents o11y from starving production workloads. If Prometheus has a memory spike, it hits the monitoring quota — not the production quota. This also provides a clean RBAC boundary (monitoring needs cluster-wide read access) and independent lifecycle management (upgrade monitoring without affecting production).

### Three Pillars of Observability

#### 1. Metrics (Prometheus + Node Exporter + kube-state-metrics)

Prometheus scrapes four metric sources:

| Source                               | What it provides                                                          |
| ------------------------------------ | ------------------------------------------------------------------------- |
| **Node Exporter** (DaemonSet)        | Hardware/OS metrics: CPU, memory, disk, network, filesystem               |
| **kube-state-metrics** (Deployment)  | Kubernetes object metrics: pod status, restarts, resource requests/limits |
| **kubelet** (built-in)               | Node-level Kubernetes metrics                                             |
| **cAdvisor** (built-in, via kubelet) | Per-container CPU, memory, network, disk I/O                              |

Two pre-provisioned Grafana dashboards:

- **Node Health Overview**: Gauges and time series for CPU%, Memory%, Disk%, Load Average, Network I/O, Disk I/O — directly answers "Do I need another VPS?"
- **K8s Resource Usage**: Pod status, restarts, top CPU/memory consumers, requests vs limits vs actual — answers "What's eating my resources?"

#### 2. Logs (Loki + Promtail)

**Promtail** (DaemonSet) scrapes container stdout logs from all pods and ships them to **Loki**. No application code changes needed — services write to stdout, Promtail handles collection.

**Retention/Purge**: Loki's compactor runs every 10 minutes with `retention_enabled: true` and a 7-day retention period. Expired chunks are automatically deleted.

**Log Level Fix**: Micronaut services were emitting DEBUG logs in production because `MICRONAUT_ENVIRONMENTS` was not set, causing the default `logback.xml` (with DEBUG) to be used instead of `logback-production.xml` (with INFO + structured JSON). Fixed by adding `MICRONAUT_ENVIRONMENTS=production` to all Kotlin service deployments.

**Pipeline stages**: Promtail extracts labels (`namespace`, `pod`, `container`, `app`, `component`) and parses JSON logs from Micronaut services to extract the `level` field for filtering.

#### 3. Traces (OpenTelemetry + Grafana Tempo)

Distributed tracing solves the fundamental GraphQL observability problem: all requests hit `POST /graphql` returning `200 OK`, so traditional HTTP metrics provide zero operation-level visibility.

**How a trace flows through the federation:**

```
User clicks "Sign In" in browser
 └─ Browser (Next.js fetch) ──────────────────── 1.2s total
   └─ Cloudflare Edge ·························· (gap — no W3C support)
     └─ Traefik Ingress ──────────────────────── 2ms
       └─ Apollo Router ──────────────────────── 850ms
         ├─ Query Planning ────────────────────── 3ms
         └─ Subgraph: security (SignIn) ──────── 845ms
           ├─ HikariCP: acquire connection ────── 1ms
           ├─ Hibernate: SELECT users ─────────── 12ms
           ├─ BCrypt: verify password ─────────── 800ms  ← bottleneck found
           └─ JWT: generate token ─────────────── 30ms
```

Each layer is instrumented via OpenTelemetry and exports traces (OTLP protocol) to the **OTel Collector**, which writes to **Grafana Tempo**. Grafana renders traces as waterfall diagrams and flame charts.

**Instrumentation per layer:**

| Layer                 | Instrumentation                                                        | Protocol                    |
| --------------------- | ---------------------------------------------------------------------- | --------------------------- |
| Next.js (browser)     | `@opentelemetry/sdk-trace-web`                                         | OTLP/HTTP → Collector       |
| Cloudflare            | Not traceable (adds `cf-ray` header only)                              | —                           |
| Traefik (K3S ingress) | Native OpenTelemetry support via K3S args                              | OTLP/gRPC → Collector       |
| Apollo Router         | Built-in OTLP export (`router.yaml` config)                            | OTLP/gRPC → Collector       |
| Micronaut subgraphs   | `micronaut-tracing-opentelemetry` (auto-instruments HTTP, JDBC, Kafka) | OTLP/gRPC → Collector       |
| PostgreSQL            | Traced via Micronaut JDBC instrumentation (shows actual SQL queries)   | Included in Micronaut spans |
| Kafka                 | Traced via Micronaut Kafka instrumentation (producer/consumer spans)   | Included in Micronaut spans |

**The Cloudflare gap**: Cloudflare does not participate in W3C `traceparent` propagation. Traces start at the Traefik ingress layer. In practice this is acceptable — Cloudflare adds < 5ms latency and the `cf-ray` header can be captured as a span attribute for correlation.

### Three-Pillar Correlation in Grafana

With metrics, logs, and traces in the same Grafana instance, we get cross-pillar correlation:

```
See a latency spike on a dashboard (metric)
  → click → jump to traces from that time window
    → see which span is slow
      → click on the span → see logs from that exact request
```

This is enabled by:

- **Exemplars**: Prometheus metrics link to trace IDs
- **Trace-to-logs**: Tempo traces link to Loki logs via trace ID
- **Log-to-trace**: Loki log entries link to Tempo traces via `trace_id` field (already present in `logback-production.xml`)

## Consequences

### Positive

- ✅ **6x resource reduction**: Total o11y footprint drops from ~3.2Gi/1600m to ~960Mi/750m (including tracing)
- ✅ **Resource isolation**: Dedicated `monitoring` namespace with its own quota prevents o11y from starving production
- ✅ **Node health visibility**: Gauges showing CPU/Memory/Disk % directly answer "Do I need another VPS?"
- ✅ **Log retention**: Automatic 7-day purge prevents unbounded storage growth
- ✅ **GraphQL operation visibility**: Distributed traces show per-operation and per-subgraph latency — the only way to debug federated GraphQL
- ✅ **Cross-pillar correlation**: Click from metric → trace → log in a single Grafana instance
- ✅ **Future-proofed for scaling**: Node Exporter DaemonSet automatically runs on new worker nodes
- ✅ **No vendor lock-in**: Fully open-source stack (Prometheus, Loki, Tempo, OTel, Grafana)
- ✅ **Structured JSON logs**: `logback-production.xml` with `MICRONAUT_ENVIRONMENTS=production` enables efficient log parsing

### Negative

- ❌ **Cloudflare trace gap**: Traces start at Traefik, not at the edge — Cloudflare latency is not captured in the trace tree
- ❌ **Operational overhead**: Six monitoring components to maintain (Prometheus, Grafana, Loki, Promtail, OTel Collector, Tempo)
- ❌ **No alerting in Phase 1-3**: Alertmanager is not included — alerts require manual dashboard checks until Phase 4
- ❌ **Port-forward access only**: Grafana is not exposed via ingress until Phase 4 — requires `kubectl port-forward` to access
- ❌ **Loki appender noise**: Production logback files have a LOKI appender pointing to `http://loki:3100` which won't resolve from the production namespace. loki4j handles this gracefully (silent drop) but it's a minor impurity until the appender is removed from the service code

### Risks

| Risk                                                               | Likelihood | Impact | Mitigation                                                                         |
| ------------------------------------------------------------------ | ---------- | ------ | ---------------------------------------------------------------------------------- |
| Prometheus OOM on single node under high cardinality               | Low        | High   | ResourceQuota caps memory; 7-day retention limits TSDB size                        |
| Loki storage growth exceeds quota                                  | Low        | Medium | Compactor retention enforced at 7 days; 5Gi PVC with monitoring                    |
| OTel Collector becomes a bottleneck                                | Low        | Medium | Tail-based sampling; Collector has its own resource limits                         |
| `MICRONAUT_ENVIRONMENTS=production` causes unexpected app behavior | Low        | Medium | Production logback and application configs already exist and are tested            |
| Promtail scraping all pods generates excessive log volume          | Medium     | Low    | Pipeline stages filter and label efficiently; Loki ingestion rate limited to 4MB/s |

## Alternatives Considered

### kube-prometheus-stack (Helm chart)

- **Pros**: All-in-one Helm chart with Prometheus Operator, Grafana, Alertmanager, node-exporter, kube-state-metrics, and pre-built dashboards. Industry standard. ServiceMonitor CRDs for service discovery
- **Cons**: Heavy — requires Prometheus Operator (multiple CRDs, webhook, admission controller). Minimum ~2Gi RAM overhead just for the operator components. Over-engineered for a single-node cluster. Complex upgrade path. Helm chart abstractions make debugging harder
- **Verdict**: Rejected. Too heavy for a single VPS. Vanilla Prometheus with static scrape configs is simpler, lighter, and sufficient for the current scale

### Datadog / New Relic / Grafana Cloud (SaaS)

- **Pros**: Zero infrastructure overhead. Pre-built integrations for Kubernetes, GraphQL, PostgreSQL. Advanced alerting, anomaly detection, APM
- **Cons**: Recurring cost ($15-50/host/month). Data leaves the infrastructure. Vendor lock-in. Overkill for a single-node cluster
- **Verdict**: Rejected. Cost and data sovereignty concerns. Open-source stack provides equivalent functionality for this scale

### Jaeger (instead of Tempo for traces)

- **Pros**: Mature, CNCF graduated project. Feature-rich UI with trace comparison. Well-documented
- **Cons**: Requires Elasticsearch or Cassandra for production storage — heavy dependencies. Doesn't integrate natively with Grafana's explore view. Separate UI from metrics and logs
- **Verdict**: Rejected. Tempo is purpose-built for Grafana integration and uses object storage or local filesystem — no external database needed. The unified Grafana experience (metrics + logs + traces) is a major advantage

### OpenTelemetry Collector → Loki (logs via OTel instead of Promtail)

- **Pros**: Single collector for both traces and logs. OTel is the future standard. Reduces DaemonSet count
- **Cons**: OTel log collection is less mature than Promtail for Kubernetes pod logs. Promtail has superior Kubernetes label extraction and pipeline stages. OTel log support in Loki is still evolving
- **Verdict**: Deferred. Use Promtail for logs (mature, proven) and OTel Collector for traces. Revisit when OTel log collection reaches GA stability

## Implementation Phases

### Phase 1: Node Health Monitoring ✅ (Implemented)

**Components**: Prometheus + Node Exporter + kube-state-metrics + Grafana

**Delivers**:

- CPU, memory, disk, network gauges and time series
- Kubernetes pod status, restarts, resource usage
- Two pre-provisioned dashboards
- Dedicated `monitoring` namespace with ResourceQuota

**Resource cost**: ~512Mi RAM, 400m CPU

### Phase 2: Centralized Logging ✅ (Implemented)

**Components**: Loki + Promtail + Grafana Loki datasource

**Delivers**:

- Aggregated logs from all pods, queryable by namespace/pod/level
- Automatic 7-day retention with compactor purge
- Structured JSON logs from Micronaut services (DEBUG → INFO fix)
- Grafana Explore for ad-hoc log queries

**Resource cost**: +192Mi RAM, +150m CPU

### Phase 3: Distributed Tracing

**Components**: OpenTelemetry Collector + Grafana Tempo + Grafana Tempo datasource

**Delivers**:

- End-to-end traces across Apollo Router → Micronaut subgraphs → PostgreSQL
- Per-GraphQL-operation latency and error breakdown
- Flame charts and waterfall views in Grafana
- Trace-to-log correlation via trace ID

**Instrumentation required**:

- Apollo Router: Add `telemetry.exporters.tracing.otlp` config to `router.yaml`
- Micronaut services: Add `micronaut-tracing-opentelemetry-http` dependency + `OTEL_EXPORTER_OTLP_ENDPOINT` env var
- Traefik: Add `--tracing.otlp.grpc.endpoint` to K3S server args

**Resource cost**: +256Mi RAM, +200m CPU

### Phase 4: Alerting, Ingress & Browser Tracing

**Components**: Alertmanager + Grafana Ingress + Next.js OTel SDK

**Delivers**:

- Automated alerts (Slack/email) for CPU > 80%, memory > 85%, pod CrashLoopBackOff
- Grafana credentials in Vault (via External Secrets)
- Browser-side traces from Next.js for true end-to-end visibility

**Resource cost**: +128Mi RAM, +100m CPU

### Total Resource Budget (all phases)

| Component          | RAM Request | CPU Request | Phase |
| ------------------ | ----------- | ----------- | ----- |
| Prometheus         | 256Mi       | 200m        | 1     |
| Grafana            | 128Mi       | 100m        | 1     |
| Node Exporter      | 64Mi        | 50m         | 1     |
| kube-state-metrics | 64Mi        | 50m         | 1     |
| Loki               | 128Mi       | 100m        | 2     |
| Promtail           | 64Mi        | 50m         | 2     |
| OTel Collector     | 128Mi       | 100m        | 3     |
| Grafana Tempo      | 128Mi       | 100m        | 3     |
| Alertmanager       | 64Mi        | 50m         | 4     |
| **Total**          | **1024Mi**  | **800m**    | —     |

This is **3x less RAM** than the previous disabled setup, while providing significantly more functionality (tracing, node metrics, log retention, structured logging).

## Decision Drivers

- Single VPS constraint demands resource-efficient monitoring — every Mi counts
- Federated GraphQL makes distributed tracing essential, not optional — `POST /graphql` returns `200 OK` even on errors
- Phased approach lets us validate each layer before adding complexity
- Grafana as the single pane of glass for metrics, logs, and traces reduces context switching
- Open-source stack (Prometheus + Loki + Tempo + OTel) avoids vendor lock-in and recurring costs
- Dedicated namespace follows industry best practices for resource isolation and RBAC

## Related Documentation

- [Observability Rules](../10-observability/observability-overview.md) — Service-level o11y requirements
- [ADR-0002: Containerized Architecture](0002-containerized-architecture.md) — K3S cluster setup
- [ADR-0012: Web Analytics Tooling](0012-web-analytics-tooling.md) — Client-side analytics (complementary)

## References

- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Grafana Tempo Documentation](https://grafana.com/docs/tempo/latest/)
- [Apollo Router Telemetry](https://www.apollographql.com/docs/router/configuration/telemetry/overview)
- [Micronaut OpenTelemetry Tracing](https://micronaut-projects.github.io/micronaut-tracing/latest/guide/)
- [Loki Retention Configuration](https://grafana.com/docs/loki/latest/operations/storage/retention/)
