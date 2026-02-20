# Observability Plan (VPS + K3S)

**Type**: Infrastructure Feature (Observability)  
**Module**: infrastructure  
**Status**: Draft

## Problem/Context

We need a consistent, production-grade observability plan for a VPS running K3S. The plan must define:
- What to measure (metrics) across host, cluster, and application layers
- What to visualize (dashboards)
- What to alert on (alerts + thresholds)
- How to structure labels and logs to avoid cardinality issues

The plan follows industry best practices: Golden Signals, Prometheus labeling, Kubernetes system metrics, and Loki label cardinality guidance.

## Solution/Approach

Adopt a layered observability model:
1. **Host/VPS layer**: node and OS metrics (CPU, memory, disk, network)
2. **Kubernetes control plane**: apiserver, scheduler, controller-manager, kubelet/cadvisor
3. **Kubernetes state**: kube-state-metrics (deployments, pods, jobs, PV/PVCs)
4. **Workload layer**: app metrics (Golden Signals)
5. **Logging layer**: Loki with disciplined labels to avoid high cardinality
6. **Alerting layer**: actionable alerts with grouping and suppression to reduce noise

## Success Criteria

### Metrics
- [ ] Node metrics available (node-exporter)
- [ ] Kubernetes system metrics scraped from control plane and kubelet endpoints
- [ ] Kubernetes object state metrics available (kube-state-metrics)
- [ ] Application metrics cover Golden Signals (latency, traffic, errors, saturation)
- [ ] Logs are centralized in Loki with low-cardinality labels

### Dashboards
- [ ] Cluster Overview dashboard (health + capacity)
- [ ] Node/Host dashboard (CPU, memory, disk, network)
- [ ] Workloads dashboard (deployments, pods, restarts)
- [ ] App SLI dashboard (latency, errors, traffic)
- [ ] Logs dashboard (errors, spikes, top noisy pods)

### Alerts
- [ ] Alerts are grouped and de-duplicated via Alertmanager
- [ ] Alerts use `for` clauses to avoid flapping
- [ ] Clear severity levels (warning vs critical)

## Metrics Plan

### 1) Golden Signals (All user-facing services)
Golden Signals are the baseline for service health.

- **Latency**: p50/p95/p99 request latency (success vs error)
- **Traffic**: request rate (RPS/QPS)
- **Errors**: error rate (5xx, 4xx policy errors)
- **Saturation**: CPU, memory, disk, or queue pressure

```AAA
# Example PromQL patterns (to adapt per service)
# Latency
histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket[5m])) by (le, service))

# Traffic
sum(rate(http_requests_total[5m])) by (service)

# Errors
sum(rate(http_requests_total{status=~"5.."}[5m])) by (service)

# Saturation (CPU)
sum(rate(container_cpu_usage_seconds_total[5m])) by (pod, namespace)
```

### 2) VPS / Host Metrics (node-exporter)
- CPU usage, load average, steal time
- Memory usage, swap activity
- Disk utilization, I/O latency
- Filesystem inode usage
- Network throughput, errors, drops

### 3) Kubernetes System Metrics
Kubernetes exposes metrics via `/metrics` on control-plane components and kubelet endpoints (including `/metrics/cadvisor` and `/metrics/probes`).

- API server: request rate, error rate, latency
- Scheduler/controller-manager health
- Kubelet/cAdvisor: container CPU/memory, filesystem usage

### 4) Kubernetes State Metrics (kube-state-metrics)
Kube-state-metrics provides object state metrics from the Kubernetes API.

- Deployments: desired vs available replicas
- Pods: phase, readiness, restarts, OOMKilled
- Jobs/CronJobs: success/failure counts
- PV/PVC: capacity, phase, binding state

## Dashboards

### A) Cluster Overview
- Node Ready status
- Total CPU/Memory/Disk capacity vs usage
- Pods running vs pending
- Control plane health

### B) Node/Host Health
- CPU usage + load average
- Memory usage + swap
- Disk usage + I/O latency
- Network traffic + drops

### C) Workload Health
- Deployment availability (desired vs ready)
- Pod restarts and OOMKills
- HPA metrics (if enabled)

### D) Application Golden Signals
- Latency (p50/p95/p99)
- Error rate (5xx/4xx)
- Traffic volume
- Saturation (CPU/memory, queue depth)

### E) Logs Overview (Loki)
- Error log rate by app/namespace
- Top noisy pods
- High-volume streams

## Alerts & Thresholds

Use grouping and inhibition in Alertmanager to reduce alert storms.
Use `for` clauses to avoid flapping and alert fatigue.

### Severity levels
- **critical**: immediate action needed
- **warning**: investigate soon

### Host / VPS Alerts
```AAA
# Disk usage (warn at 80%, critical at 90%)
warning: disk_usage > 0.80 for 10m
critical: disk_usage > 0.90 for 10m

# CPU saturation
warning: cpu_usage > 0.80 for 10m
critical: cpu_usage > 0.90 for 10m

# Memory saturation
warning: mem_usage > 0.85 for 10m
critical: mem_usage > 0.95 for 10m
```

### Kubernetes Control Plane Alerts
```AAA
# API server error rate
critical: apiserver_5xx_rate > 1% for 5m

# API server latency
warning: apiserver_p95_latency > 1s for 10m
```

### Kubernetes State Alerts
```AAA
# Deployments below desired replicas (alert on deployment, not individual pods)
warning: available_replicas < desired_replicas for 10m
critical: available_replicas < desired_replicas for 30m

# Pods in CrashLoopBackOff
warning: crashloop_rate > 0 for 10m
```

### Application Alerts (Golden Signals)
```AAA
# Error rate
warning: http_5xx_rate > 1% for 10m
critical: http_5xx_rate > 5% for 5m

# Latency
warning: p95_latency > 500ms for 10m
critical: p95_latency > 1s for 5m

# Saturation
warning: cpu_or_queue_saturation > 80% for 10m
critical: cpu_or_queue_saturation > 90% for 10m
```

## Logging & Labels (Loki)

Loki label best practices require **low-cardinality** labels. Avoid labels like request ID, user ID, or trace ID. Prefer stable labels (app, namespace, environment, cluster).

```AAA
# Recommended labels
{cluster="prod", namespace="production", app="router", environment="prod"}

# Avoid (high cardinality)
{request_id="...", user_id="...", trace_id="..."}
```

## Metric Naming & Labels (Prometheus)

Follow Prometheus naming and labeling conventions:
- Use base units in metric names (e.g., `_seconds`, `_bytes`, `_total`)
- Avoid high-cardinality labels (user IDs, request IDs)
- Ensure labels are meaningful and bounded

```AAA
# Good
http_requests_total{service="api", method="GET", status="200"}

# Bad (high cardinality)
http_requests_total{user_id="12345"}
```

## Alerting Hygiene

- Group alerts by cluster/service to reduce noise.
- Use `for` durations to avoid flapping alerts.
- Review thresholds quarterly; tune based on real usage.

## References

- Kubernetes observability overview: https://kubernetes.io/docs/concepts/cluster-administration/observability/
- Kubernetes system metrics: https://kubernetes.io/docs/concepts/cluster-administration/system-metrics
- kube-state-metrics overview: https://kubernetes.io/docs/concepts/cluster-administration/kube-state-metrics
- Prometheus naming best practices: https://prometheus.io/docs/practices/naming/
- Prometheus instrumentation practices: https://prometheus.io/docs/practices/instrumentation/
- Alertmanager grouping: https://prometheus.io/docs/alerting/latest/alertmanager/
- Golden Signals (Google SRE book reference): https://linkedin.github.io/school-of-sre/level101/metrics_and_monitoring/introduction/
- Loki label best practices: https://grafana.com/docs/loki/latest/get-started/labels/bp-labels/
- Loki label cardinality: https://grafana.com/docs/loki/latest/get-started/labels/cardinality/
