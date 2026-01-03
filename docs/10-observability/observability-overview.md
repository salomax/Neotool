---
title: Observability Rules
type: rule
category: observability
status: current
version: 1.0.0
tags: [observability, rules, metrics, logging, monitoring, prometheus, loki, grafana]
ai_optimized: true
search_keywords: [observability, metrics, logging, monitoring, prometheus, loki, grafana, dashboards]
related:
  - 00-core/architecture.md
  - 07-validation/feature-checklist.md
---

# Observability Rules

> **Purpose**: Rules for ensuring all services expose metrics and logs to Prometheus and Loki, and have appropriate Grafana dashboards.

## Overview

All services in NeoTool **MUST** implement comprehensive observability to enable monitoring, alerting, and debugging. This includes:

1. **Metrics**: Expose Prometheus-compatible metrics
2. **Logs**: Send structured logs to Loki
3. **Dashboards**: Have Grafana dashboards for visualization

## Metrics Rules

### Rule: Prometheus Metrics Endpoint

**Rule**: Every service MUST expose a Prometheus metrics endpoint at `/prometheus`.

**Rationale**: Enables centralized metrics collection and monitoring.

**Implementation Requirements**:

1. **Micrometer Configuration**: Services MUST use Micrometer with Prometheus exporter
2. **Endpoint Configuration**: The `/prometheus` endpoint MUST be enabled and non-sensitive
3. **Metrics Path**: Prometheus MUST be configured to scrape from `/prometheus` path

**Example Configuration** (`application.yml`):
```yaml
micronaut:
  metrics:
    enabled: true
    export:
      prometheus:
        enabled: true
        step: PT15S
  observation:
    enabled: true

endpoints:
  all:
    enabled: true
  prometheus:
    enabled: true
    sensitive: false
```

**Example Prometheus Scrape Config**:
```yaml
scrape_configs:
  - job_name: 'service-name'
    static_configs:
      - targets: ['service-name:8080']
    metrics_path: '/prometheus'
```

### Rule: Required Metrics

**Rule**: Every service MUST expose the following metric categories:

1. **JVM Metrics**: Memory, CPU, threads, GC
2. **HTTP Metrics**: Request rate, latency, error rate, status codes
3. **Database Metrics**: Connection pool, query duration, transaction metrics
4. **Business Metrics**: Custom metrics for business logic (if applicable)

**Rationale**: Provides comprehensive visibility into service health and performance.

**Implementation**: Micrometer automatically exposes JVM and HTTP metrics when enabled. Database metrics require HikariCP or Micronaut Data integration.

**Verification**: Metrics MUST be visible in Prometheus and queryable via PromQL.

### Rule: Metrics Labels

**Rule**: All metrics MUST include appropriate labels for filtering and aggregation:

- `service`: Service name (e.g., `app`, `assistant`, `security`)
- `instance`: Instance identifier (e.g., hostname, pod name)
- `environment`: Environment name (e.g., `development`, `staging`, `production`)

**Rationale**: Enables multi-service and multi-instance monitoring.

**Implementation**: Labels are typically added via Micrometer configuration or instrumentation.

## Logging Rules

### Rule: Loki Log Shipping

**Rule**: Every service MUST send logs to Loki.

**Rationale**: Enables centralized log aggregation and querying.

**Implementation Requirements**:

1. **Logback Configuration**: Services MUST use Logback with Loki appender
2. **Structured Logging**: Logs MUST be in structured JSON format
3. **Loki Endpoint**: Logs MUST be sent to `http://loki:3100/loki/api/v1/push`

**Example Configuration** (`logback-production.xml`):
```xml
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
  <http>
    <url>http://loki:3100/loki/api/v1/push</url>
  </http>
  <format>
    <label>
      <pattern>service=${SERVICE_NAME},environment=${ENVIRONMENT},level=%level,logger=%logger{20}</pattern>
    </label>
    <message>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{correlationId:-}] %-5level %logger{36} - %msg%n</pattern>
    </message>
  </format>
</appender>

<root level="INFO">
  <appender-ref ref="STDOUT" />
  <appender-ref ref="LOKI" />
</root>
```

### Rule: Structured Logging

**Rule**: All logs MUST be in structured JSON format with required fields.

**Required Fields**:
- `timestamp`: ISO 8601 timestamp
- `level`: Log level (INFO, WARN, ERROR, etc.)
- `logger`: Logger name
- `message`: Log message
- `service`: Service name
- `environment`: Environment name
- `version`: Application version
- `instance`: Instance identifier
- `trace_id`: Distributed tracing ID (if available)
- `span_id`: Span ID (if available)

**Rationale**: Enables efficient log parsing, filtering, and correlation.

**Implementation**: Use `LoggingEventCompositeJsonEncoder` from `logstash-logback-encoder`.

### Rule: Log Levels

**Rule**: Services MUST use appropriate log levels:

- **ERROR**: Errors that require immediate attention
- **WARN**: Warning conditions that may need attention
- **INFO**: Informational messages about normal operation
- **DEBUG**: Detailed diagnostic information (development only)

**Rationale**: Ensures appropriate log verbosity and reduces noise.

**Exception**: DEBUG level may be disabled in production.

## Dashboard Rules

### Rule: Service Dashboard

**Rule**: Every service MUST have a Grafana dashboard that visualizes:

1. **Service Health**: Uptime, health status
2. **JVM Metrics**: CPU usage, memory usage (heap, non-heap), thread count, GC metrics
3. **HTTP Metrics**: Request rate, latency (p50, p95, p99), error rate, status code distribution
4. **Database Metrics**: Connection pool usage, query duration, transaction rate
5. **Environment Metrics**: Container/pod CPU, memory, network I/O (if available)

**Rationale**: Provides comprehensive visibility into service performance and health.

**Implementation Requirements**:

1. **Dashboard Location**: Dashboards MUST be stored in `infra/observability/grafana/dashboards/`
2. **Dashboard Naming**: Dashboards MUST be named `{service-name}-metrics.json`
3. **Dashboard Provisioning**: Dashboards MUST be automatically provisioned via Grafana provisioning
4. **Dashboard UID**: Each dashboard MUST have a unique UID

**Example Dashboard Structure**:
- **Row 1**: Service health and status
- **Row 2**: JVM metrics (CPU, Memory, Threads)
- **Row 3**: HTTP metrics (Request rate, Latency, Errors)
- **Row 4**: Database metrics (Connections, Query duration)
- **Row 5**: Environment metrics (Container CPU, Memory)

**Dashboard Variables**:
- `service`: Service name selector
- `instance`: Instance selector (optional)
- `environment`: Environment selector (optional)

### Rule: Dashboard Metrics Queries

**Rule**: Dashboard panels MUST use PromQL queries that:

1. Filter by service name
2. Aggregate across instances when appropriate
3. Use appropriate time ranges and intervals
4. Include error handling for missing metrics

**Example PromQL Queries**:

```promql
# JVM Memory Heap Used
jvm_memory_used_bytes{service="app", area="heap"}

# HTTP Request Rate
rate(http_server_requests_seconds_count{service="app"}[5m])

# HTTP Request Latency (p95)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{service="app"}[5m]))

# Error Rate
rate(http_server_requests_seconds_count{service="app", status=~"5.."}[5m])

# Thread Count
jvm_threads_live_threads{service="app"}

# Database Connection Pool Active
hikari_connections_active{service="app"}
```

## Service-Specific Requirements

### Rule: New Service Setup

**Rule**: When creating a new service, the following MUST be configured:

1. **Metrics**:
   - [ ] Micrometer Prometheus exporter enabled in `application.yml`
   - [ ] `/prometheus` endpoint enabled and non-sensitive
   - [ ] Prometheus scrape config added to `infra/observability/prometheus/prometheus.yml`

2. **Logging**:
   - [ ] Loki appender configured in `logback-production.xml`
   - [ ] Structured JSON logging enabled
   - [ ] Service name and environment labels configured

3. **Dashboard**:
   - [ ] Grafana dashboard created in `infra/observability/grafana/dashboards/`
   - [ ] Dashboard includes all required metric categories
   - [ ] Dashboard provisioning configured

**Verification**: Use the [Feature Checklist](../07-validation/feature-checklist.md) to verify compliance.

## Prometheus Configuration

### Rule: Service Registration

**Rule**: Every service MUST be registered in Prometheus scrape configuration.

**Location**: `infra/observability/prometheus/prometheus.yml`

**Format**:
```yaml
scrape_configs:
  - job_name: 'service-name'
    static_configs:
      - targets: ['service-name:8080']
    metrics_path: '/prometheus'
```

**Rationale**: Ensures Prometheus collects metrics from all services.

## Grafana Configuration

### Rule: Dashboard Provisioning

**Rule**: All service dashboards MUST be automatically provisioned.

**Location**: `infra/observability/grafana/provisioning/dashboards/dashboard.yml`

**Format**:
```yaml
apiVersion: 1
providers:
  - name: 'Service Dashboards'
    orgId: 1
    folder: 'Services'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/dashboards
      foldersFromFilesStructure: true
```

**Rationale**: Ensures dashboards are automatically loaded and available.

## Verification

### Rule: Observability Verification

**Rule**: Before deploying a service, verify:

1. **Metrics**:
   - [ ] Metrics endpoint accessible at `http://service:port/prometheus`
   - [ ] Metrics visible in Prometheus UI
   - [ ] Required metrics present (JVM, HTTP, Database)

2. **Logs**:
   - [ ] Logs visible in Loki
   - [ ] Logs include required fields
   - [ ] Logs can be queried by service name

3. **Dashboard**:
   - [ ] Dashboard visible in Grafana
   - [ ] All panels display data
   - [ ] Dashboard filters work correctly

**Tools**:
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`
- Loki: `http://localhost:3100`

## Related Documentation

- [Feature Checklist](../07-validation/feature-checklist.md) - Includes observability verification
- [Architecture Overview](../00-core/architecture.md) - System architecture
- [Observability Infrastructure](../../infra/observability/README.md) - Infrastructure setup

## Exceptions

**Development Environment**: In local development, services may use simplified logging configuration, but MUST still expose metrics endpoint.

**Legacy Services**: Existing services without observability MUST be updated to comply with these rules.

