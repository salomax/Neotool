# Observability Implementation Backlog (VPS + K3S)

**Type**: Infrastructure Feature (Observability)  
**Module**: infrastructure  
**Status**: Draft

## Problem/Context

This backlog defines an implementation plan for observability on a VPS running K3S. It is organized into goals/milestones with detailed tasks and no time constraints. It is intended to be executed by an LLM or a developer in small, verifiable steps.

## Guiding Principles

- Prefer low-cardinality labels for metrics and logs.
- Keep alerts actionable and grouped to reduce noise.
- Make dashboards align with the Golden Signals and platform health.
- Use existing Flux and manifest patterns already in the repo.

## Checkpoints / Goals

### Goal 1: Foundation and namespaces

- Create a dedicated `observability` namespace for the stack (Prometheus, Grafana, Loki, Promtail, Alertmanager).
- Document the cross-namespace integration model (`production` emits metrics/logs, `observability` scrapes/collects).
- Ensure resource quotas and limits for observability components.
- Define RBAC for Prometheus, Promtail, and any exporters that need K8S discovery.
- Validate storage classes and PVC strategy for Prometheus, Grafana, and Loki.
- Add NetworkPolicy rules for observability components (ingress/egress), including cross-namespace scrape access.

### Goal 2: Metrics collection (cluster + host)

- Deploy **node-exporter** as a DaemonSet.
- Deploy **kube-state-metrics** as a Deployment with RBAC.
- Configure Prometheus to scrape:
  - node-exporter
  - kube-state-metrics
  - kubelet `/metrics` and `/metrics/cadvisor`
  - apiserver metrics
- Ensure Prometheus has ClusterRole permissions to discover and scrape targets across namespaces.
- Add K8S service discovery rules and relabeling to standardize labels.
- Validate Prometheus targets are healthy and visible in UI.

### Goal 3: Application metrics and exporters

- Standardize service-level metrics endpoints (e.g., `/metrics` or `/prometheus`).
- Ensure Router, API services, and any worker services expose Golden Signal metrics.
- Add exporters where needed:
  - Postgres exporter
  - PgBouncer exporter (if used)
  - Kafka exporter (if used)
  - Vault metrics endpoint
- Update Prometheus scrape configs for these services.
- Validate metric names and labels align with Prometheus conventions.

### Goal 4: Logging (Loki + Promtail)

- Confirm Loki StatefulSet storage and retention configuration.
- Configure Promtail to scrape pod logs reliably.
- Ensure Promtail runs as a DaemonSet and reads logs from host paths (`/var/log/pods`, `/var/lib/docker/containers`).
- Add Promtail config for host logs (systemd/k3s) if required.
- Define a label policy for logs (app, namespace, environment, cluster).
- Validate logs are queryable in Grafana (Loki data source).

### Goal 5: Dashboards

- Create dashboards for:
  - Cluster Overview
  - Node/Host Health
  - Workload Health
  - Application Golden Signals
  - Logs Overview
- Add dashboards as JSON files in the repo and wire them into Grafana provisioning.
- Validate dashboards load automatically on Grafana startup.
- Ensure dashboards use consistent labels and variables (namespace, app, node).

### Goal 6: Alerting and rules

- Add Alertmanager deployment/config.
- Define alerting rules for:
  - Host saturation (CPU, memory, disk)
  - Control-plane errors/latency
  - Pod restarts and CrashLoopBackOff
  - Deployment availability
  - App error rates and latency
- Add `for` durations and severity labels (`warning`, `critical`).
- Configure Alertmanager routes (email/Slack/etc.) with grouping and inhibition.

### Goal 7: Runbooks and verification

- Add a short runbook section per alert (what it means, common fixes).
- Define a validation checklist:
  - All targets up in Prometheus
  - Logs visible in Loki
  - Dashboards load
  - Alerts can be fired and routed
- Provide examples of queries to confirm each goal.

### Goal 8: Distributed tracing and flamegraph visualization

- Implement W3C Trace Context propagation (`traceparent`, `tracestate`, `baggage`) across Router and services.
- Add a trace backend and Grafana trace datasource (Tempo recommended).
- Add a Grafana trace dashboard with flamegraph/waterfall-style span visualization.
- Ensure log-to-trace correlation using `otel_trace_id`.
- Detailed task: `neotool/docs/03-features/infrastructure/observability/trace-context-flamegraph-task.md`

## How To (Implementation Steps)

### Goal 1: Foundation and namespaces

**Namespace**

```AAA
1) Create `observability` namespace:
   - File: neotool-flux/infra/kubernetes/flux/infrastructure/namespace/namespace.yaml
   - Add a new Namespace manifest or extend existing kustomization to include it.

2) Add the namespace to kustomization:
   - File: neotool-flux/infra/kubernetes/flux/infrastructure/namespace/kustomization.yaml
```

**Cross-namespace integration model**

```AAA
1) Add a short doc note:
   - File: neotool/docs/03-features/infrastructure/observability/observability-vps-k3s.md
   - Explain: Prometheus scrapes across namespaces via RBAC; Promtail reads host logs.
```

**Resource quotas / limits**

```AAA
1) Create or extend ResourceQuota in `observability`:
   - File: neotool-flux/infra/kubernetes/flux/infrastructure/namespace/resource-quota.yaml
   - Add limits for CPU/memory/storage based on VPS capacity.
```

**RBAC**

```AAA
1) Prometheus:
   - Create ServiceAccount + ClusterRole + ClusterRoleBinding.
   - Permissions: get/list/watch on pods, endpoints, services, nodes, nodes/proxy.
   - Place under: neotool-flux/infra/kubernetes/flux/apps/observability/prometheus/

2) Promtail:
   - ServiceAccount + ClusterRole + ClusterRoleBinding already exists in promtail-daemonset.yaml.
   - Ensure it remains cluster-wide for node/pod discovery.
```

**Storage**

```AAA
1) Ensure PVCs exist for Prometheus, Grafana, Loki:
   - Files:
     - neotool-flux/infra/kubernetes/flux/apps/observability/prometheus/deployment.yaml
     - neotool-flux/infra/kubernetes/flux/apps/observability/grafana/deployment.yaml
     - neotool-flux/infra/kubernetes/flux/apps/observability/loki/loki-statefulset.yaml
2) Verify `storageClassName` matches cluster (e.g., local-path).
```

**NetworkPolicy**

```AAA
1) Add policies in observability namespace:
   - Allow Prometheus -> scrape targets in production namespace (metrics ports).
   - Allow Promtail -> Loki (port 3100).
   - Deny all other ingress/egress by default (optional).
2) Place policies under:
   - neotool-flux/infra/kubernetes/flux/infrastructure/network-policies/
```

### Goal 2: Metrics collection (cluster + host)

**node-exporter**

```AAA
1) Add DaemonSet + Service:
   - New path: neotool-flux/infra/kubernetes/flux/apps/observability/node-exporter/
2) Mount /proc, /sys, / (as read-only) and set hostNetwork if needed.
3) Expose port 9100 with a Service.
```

**kube-state-metrics**

```AAA
1) Add Deployment + Service + RBAC:
   - New path: neotool-flux/infra/kubernetes/flux/apps/observability/kube-state-metrics/
2) Expose port 8080 or 8081 via Service.
```

**Prometheus scrape configs**

```AAA
1) Update scrape configs:
   - File: neotool-flux/infra/kubernetes/flux/apps/observability/prometheus/deployment.yaml
2) Add jobs:
   - node-exporter (Service/Pod discovery)
   - kube-state-metrics (Service discovery)
   - kubelet metrics via nodes/proxy (requires RBAC)
   - apiserver metrics (Service in kube-system)
3) Ensure relabeling adds namespace/pod/node labels consistently.
```

**Verification**

```AAA
1) Check targets in Prometheus UI (Status -> Targets).
2) Confirm node-exporter and kube-state-metrics are UP.
```

### Goal 3: Application metrics and exporters

**Standardize endpoints**

```AAA
1) Decide a standard metrics path:
   - Prefer /metrics for all services.
2) Ensure app deployments expose the port and path.
```

**Exporters**

```AAA
1) Postgres exporter:
   - Ensure service + credentials in Vault/ExternalSecret.
2) PgBouncer exporter (if used):
   - Add deployment + Service, use admin credentials.
3) Kafka exporter (if used):
   - Add deployment + Service; point to brokers.
4) Vault metrics:
   - Enable /v1/sys/metrics endpoint in Vault config.
```

**Prometheus jobs**

```AAA
1) Add scrape configs for each exporter in Prometheus config.
2) Use consistent `job_name` and labels per service.
```

**Verification**

```AAA
1) Run sample PromQL queries for each exporter.
2) Confirm series cardinality remains bounded.
```

### Goal 4: Logging (Loki + Promtail)

**Loki storage**

```AAA
1) Check Loki PVC and storageClass:
   - File: neotool-flux/infra/kubernetes/flux/apps/observability/loki/loki-statefulset.yaml
2) Set retention if needed in config.
```

**Promtail**

```AAA
1) Ensure host paths are mounted:
   - /var/log and /var/lib/docker/containers
2) Keep the DaemonSet in observability namespace.
3) Add scrape configs for:
   - /var/log/pods (K8S pod logs)
   - systemd/k3s logs if needed
```

**Verification**

```AAA
1) Query Loki from Grafana:
   - `{namespace="production"} |= "error"`
```

### Goal 5: Dashboards

```AAA
1) Create JSON dashboards:
   - Store under: neotool/infra/observability/grafana/dashboards/
2) Wire dashboards into Grafana:
   - ConfigMap: neotool-flux/infra/kubernetes/flux/apps/observability/grafana/deployment.yaml
3) Validate dashboards auto-load in Grafana UI.
```

### Goal 6: Alerting and rules

```AAA
1) Deploy Alertmanager:
   - Add manifests under: neotool-flux/infra/kubernetes/flux/apps/observability/alertmanager/
2) Add PrometheusRule groups (or rules in ConfigMap):
   - Host saturation, control plane, pod restarts, app errors.
3) Add Alertmanager routes:
   - Group by cluster/namespace/service.
4) Verify alerts fire by creating a test rule.
```

### Goal 7: Runbooks and verification

```AAA
1) Add runbook notes per alert:
   - File: neotool/docs/03-features/infrastructure/observability/observability-vps-k3s.md
2) Add validation checklist:
   - Prometheus targets UP
   - Loki logs searchable
   - Dashboards visible
   - Alerts routed
```

### Goal 8: Distributed tracing and flamegraph visualization

```AAA
1) Execute detailed task:
   - File: neotool/docs/03-features/infrastructure/observability/trace-context-flamegraph-task.md
2) Ensure Router propagates W3C trace context headers (traceparent, tracestate, baggage).
3) Provision Tempo datasource and tracing dashboard in Grafana.
4) Validate end-to-end trace propagation and log correlation.
```

## Implementation Notes

- Prefer Kustomize overlays where possible.
- Reuse existing Flux patterns in `neotool-flux/infra/kubernetes/flux`.
- Keep changes incremental and easy to review.

## Backlog Execution Checklist

- [ ] Goal 1 complete
- [ ] Goal 2 complete
- [ ] Goal 3 complete
- [ ] Goal 4 complete
- [ ] Goal 5 complete
- [ ] Goal 6 complete
- [ ] Goal 7 complete
- [ ] Goal 8 complete
