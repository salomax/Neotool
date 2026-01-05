---
title: Kafka Monitoring Guide
type: guide
category: backend
status: current
version: 1.0.0
tags: [guide, backend, kafka, monitoring, metrics, alerting]
ai_optimized: true
search_keywords: [kafka, monitoring, metrics, alerting, prometheus, grafana, observability]
related:
  - 04-patterns/backend-patterns/kafka-consumer-pattern.md
  - 04-patterns/backend-patterns/kafka-operations-guide.md
---

# Kafka Monitoring Guide

> **Purpose**: Comprehensive guide for monitoring Kafka consumers, setting up alerts, and ensuring observability in the NeoTool project.

## Overview

This guide covers monitoring strategies, metrics to track, alerting thresholds, and how to set up observability for Kafka consumers in the NeoTool project.

## Metrics Overview

### Processing Metrics

These metrics track message processing performance:

| Metric | Type | Description | Labels |
|--------|------|-------------|--------|
| `{domain}.{entity}.processed` | Counter | Total messages processed successfully | - |
| `{domain}.{entity}.processing.duration` | Timer | Time taken to process a message | - |
| `{domain}.{entity}.error.count` | Counter | Total processing errors | `type` (validation, processing, dlq) |
| `{domain}.{entity}.retry.count` | Counter | Total retry attempts | - |
| `{domain}.{entity}.dlq.count` | Counter | Total messages sent to DLQ | - |
| `{domain}.{entity}.dlq.publish.failure` | Counter | DLQ publish failures | - |

**Example**: `swapi.people.processed`, `swapi.people.processing.duration`

### Consumer Metrics

These metrics track Kafka consumer behavior:

| Metric | Type | Description | Source |
|--------|------|-------------|--------|
| `kafka.consumer.lag` | Gauge | Consumer lag per partition | Kafka consumer metrics |
| `kafka.consumer.records.consumed.rate` | Gauge | Messages consumed per second | Kafka consumer metrics |
| `kafka.consumer.fetch.rate` | Gauge | Fetch requests per second | Kafka consumer metrics |
| `kafka.consumer.commit.rate` | Gauge | Offset commits per second | Kafka consumer metrics |

### Infrastructure Metrics

These metrics track application infrastructure:

| Metric | Type | Description | Source |
|--------|------|-------------|--------|
| `jvm.memory.used` | Gauge | JVM memory usage | Micrometer |
| `jvm.threads.live` | Gauge | Number of live threads | Micrometer |
| `process.cpu.usage` | Gauge | CPU usage percentage | Micrometer |
| `http.server.requests` | Timer | HTTP request duration | Micrometer |

## Metrics Collection

### Micrometer Configuration

Metrics are automatically collected via Micrometer when configured in `application.yml`:

```yaml
micronaut:
  metrics:
    enabled: true
    export:
      prometheus:
        enabled: true
        step: PT15S  # 15 second intervals
```

### Accessing Metrics

Metrics are exposed via the `/metrics` endpoint:

```bash
# Prometheus format
curl http://localhost:8081/metrics

# Prometheus endpoint (if enabled)
curl http://localhost:8081/prometheus
```

### Custom Metrics Implementation

Example metrics implementation:

```kotlin
@Singleton
class DomainMetrics(
    private val meterRegistry: MeterRegistry
) : ConsumerMetrics {
    private val processedCounter = Counter.builder("domain.entity.processed")
        .description("Total number of messages processed successfully")
        .register(meterRegistry)
    
    private val processingTimer = Timer.builder("domain.entity.processing.duration")
        .description("Time taken to process a message")
        .register(meterRegistry)
    
    private val errorCounter = Counter.builder("domain.entity.error.count")
        .description("Total number of processing errors")
        .tag("type", "processing")
        .register(meterRegistry)
    
    // ... other metrics
}
```

## Alerting Thresholds

### Critical Alerts

These alerts require immediate attention:

#### High Error Rate

**Condition**: Error rate exceeds 5% of processing rate for 5 minutes

**Query**:
```promql
rate(domain_entity_error_count_total[5m]) / rate(domain_entity_processed_total[5m]) > 0.05
```

**Action**: 
- Check application logs for error patterns
- Verify downstream system health
- Check for data quality issues

#### High DLQ Rate

**Condition**: DLQ rate exceeds 1% of processing rate for 5 minutes

**Query**:
```promql
rate(domain_entity_dlq_count_total[5m]) / rate(domain_entity_processed_total[5m]) > 0.01
```

**Action**:
- Review DLQ messages for patterns
- Check for systemic issues
- Verify DLQ topic accessibility

#### High Partition Lag

**Condition**: Consumer lag exceeds 10,000 messages per partition for 10 minutes

**Query**:
```promql
kafka_consumer_lag_sum > 10000
```

**Action**:
- Scale out consumers
- Optimize processing logic
- Check for stuck partitions
- Verify consumer health

#### DLQ Publish Failures

**Condition**: DLQ publish failure rate exceeds 10% for 5 minutes

**Query**:
```promql
rate(domain_entity_dlq_publish_failure_total[5m]) / rate(domain_entity_dlq_count_total[5m]) > 0.10
```

**Action**:
- Check DLQ topic accessibility
- Verify Kafka producer configuration
- Check for Kafka broker issues
- Implement DLQ fallback mechanism

### Warning Alerts

These alerts indicate potential issues:

#### Frequent Rebalances

**Condition**: Consumer group rebalances more than once per hour

**Query**:
```promql
increase(kafka_consumer_rebalance_total[1h]) > 1
```

**Action**:
- Check `max.poll.interval.ms` configuration
- Review processing duration
- Check for network issues
- Optimize retry configuration

#### High Processing Duration

**Condition**: P95 processing duration exceeds 5 seconds for 10 minutes

**Query**:
```promql
histogram_quantile(0.95, rate(domain_entity_processing_duration_seconds_bucket[10m])) > 5
```

**Action**:
- Optimize processing logic
- Check for downstream system slowness
- Review database query performance
- Check for resource constraints

#### High Retry Rate

**Condition**: Retry rate exceeds 10% of processing rate for 10 minutes

**Query**:
```promql
rate(domain_entity_retry_count_total[10m]) / rate(domain_entity_processed_total[10m]) > 0.10
```

**Action**:
- Review error patterns
- Check for transient issues
- Verify downstream system health
- Consider circuit breaker pattern

#### Shutdown Timeouts

**Condition**: Shutdown duration exceeds configured timeout

**Query**:
```promql
application_shutdown_duration_seconds > batch_consumer_shutdown_timeout_seconds
```

**Action**:
- Increase shutdown timeout if needed
- Optimize processing time
- Check for long retry loops
- Review graceful shutdown implementation

## Monitoring Dashboards

### Recommended Dashboard Panels

#### Processing Overview

- **Processing Rate**: `rate(domain_entity_processed_total[5m])`
- **Error Rate**: `rate(domain_entity_error_count_total[5m])`
- **DLQ Rate**: `rate(domain_entity_dlq_count_total[5m])`
- **Retry Rate**: `rate(domain_entity_retry_count_total[5m])`

#### Performance Metrics

- **P50 Processing Duration**: `histogram_quantile(0.50, rate(domain_entity_processing_duration_seconds_bucket[5m]))`
- **P95 Processing Duration**: `histogram_quantile(0.95, rate(domain_entity_processing_duration_seconds_bucket[5m]))`
- **P99 Processing Duration**: `histogram_quantile(0.99, rate(domain_entity_processing_duration_seconds_bucket[5m]))`

#### Consumer Health

- **Consumer Lag**: `kafka_consumer_lag_sum`
- **Consumer Lag per Partition**: `kafka_consumer_lag`
- **Consumer Group Membership**: `kafka_consumer_group_members`
- **Rebalance Count**: `kafka_consumer_rebalance_total`

#### Error Analysis

- **Error Rate by Type**: `rate(domain_entity_error_count_total[5m])` by `type`
- **DLQ Publish Failures**: `rate(domain_entity_dlq_publish_failure_total[5m])`
- **Error Trends**: `rate(domain_entity_error_count_total[1h])`

### Grafana Dashboard Example

```json
{
  "dashboard": {
    "title": "Kafka Consumer - Domain Entity",
    "panels": [
      {
        "title": "Processing Rate",
        "targets": [
          {
            "expr": "rate(domain_entity_processed_total[5m])"
          }
        ]
      },
      {
        "title": "Error Rate",
        "targets": [
          {
            "expr": "rate(domain_entity_error_count_total[5m])"
          }
        ]
      },
      {
        "title": "Consumer Lag",
        "targets": [
          {
            "expr": "kafka_consumer_lag_sum"
          }
        ]
      }
    ]
  }
}
```

## Health Checks

### Consumer Health Check

Implement health check to verify consumer status:

```kotlin
@Singleton
class DomainConsumerHealth(
    @Inject private val consumer: DomainConsumer,
    @Inject private val meterRegistry: MeterRegistry
) : HealthIndicator {
    override fun getResult(): HealthResult {
        // Check consumer group membership
        // Check partition assignments
        // Check for stuck partitions
        // Check DLQ connectivity
        
        val isHealthy = // health check logic
        
        return if (isHealthy) {
            HealthResult.up("Consumer is healthy")
        } else {
            HealthResult.down("Consumer is unhealthy")
        }
    }
}
```

### Health Check Endpoints

Access health checks via Micronaut health endpoint:

```bash
# Overall health
curl http://localhost:8081/health

# Specific health check
curl http://localhost:8081/health/domain-consumer
```

## Logging

### Recommended Log Levels

- **INFO**: Processing start/completion, DLQ publishing
- **WARN**: Retries, DLQ publish failures, shutdown warnings
- **ERROR**: Processing failures, validation errors, commit failures
- **DEBUG**: Detailed processing steps, retry attempts

### Structured Logging

Use structured logging for better observability:

```kotlin
logger.info {
    "Processing message: recordId=${message.recordId}, " +
    "partition=$partition, offset=$offset, " +
    "batchId=${message.batchId}"
}
```

### Log Aggregation

Configure log aggregation (e.g., Loki) for centralized logging:

```yaml
# application.yml
logging:
  level:
    io.github.salomax.neotool: INFO
    org.apache.kafka: WARN
```

## Performance Monitoring

### Key Performance Indicators (KPIs)

1. **Throughput**: Messages processed per second
2. **Latency**: P50, P95, P99 processing duration
3. **Error Rate**: Percentage of messages that fail
4. **DLQ Rate**: Percentage of messages sent to DLQ
5. **Consumer Lag**: Messages waiting to be processed

### Performance Baselines

Establish performance baselines:
- **Normal Processing Rate**: Messages per second under normal load
- **Normal Processing Duration**: Expected processing time per message
- **Normal Error Rate**: Expected error rate under normal conditions
- **Normal Consumer Lag**: Expected lag under normal conditions

### Performance Optimization

Monitor these metrics for optimization opportunities:
- **Processing Duration**: Identify slow processing paths
- **Retry Rate**: Identify frequently failing messages
- **Consumer Lag**: Identify bottlenecks
- **Memory Usage**: Identify memory leaks or high usage
- **CPU Usage**: Identify CPU-intensive operations

## Troubleshooting with Metrics

### High Error Rate

1. **Check Error Types**: Review `error.count` by `type` label
2. **Check Error Trends**: Look for sudden spikes or gradual increases
3. **Correlate with Events**: Check for deployments, configuration changes
4. **Review Logs**: Check application logs for error details

### High Consumer Lag

1. **Check Processing Rate**: Compare with message production rate
2. **Check Processing Duration**: Identify slow processing
3. **Check Partition Distribution**: Verify even partition assignment
4. **Check for Stuck Partitions**: Identify partitions with no progress

### Frequent Rebalances

1. **Check Processing Duration**: Verify processing completes within `max.poll.interval.ms`
2. **Check Retry Configuration**: Verify retry delays don't exceed poll interval
3. **Check Network**: Verify network connectivity and latency
4. **Check Consumer Instances**: Verify all instances are healthy

## Best Practices

### Metrics Collection

1. **Use Consistent Naming**: Follow naming conventions (`domain.entity.metric`)
2. **Add Descriptive Labels**: Use labels for filtering and grouping
3. **Avoid High Cardinality**: Don't use high-cardinality labels (e.g., record IDs)
4. **Document Metrics**: Document all custom metrics and their purpose

### Alerting

1. **Set Appropriate Thresholds**: Base thresholds on historical data
2. **Avoid Alert Fatigue**: Don't alert on every minor issue
3. **Use Alert Severity**: Distinguish between critical and warning alerts
4. **Test Alerts**: Regularly test alerting to ensure it works

### Monitoring

1. **Monitor Trends**: Look for trends, not just current values
2. **Set Baselines**: Establish performance baselines
3. **Review Regularly**: Regularly review metrics and adjust thresholds
4. **Correlate Metrics**: Correlate different metrics to identify root causes

## Related Documentation

- [Kafka Consumer Pattern](./kafka-consumer-pattern.md) - Implementation pattern
- [Kafka Operations Guide](./kafka-operations-guide.md) - Operational procedures
- [Batch Workflow Standard](../05-standards/workflow-standards/batch-workflow-standard.md) - Batch processing standards
