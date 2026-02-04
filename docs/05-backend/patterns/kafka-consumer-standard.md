---
title: Kafka Consumer Standard
type: standard
category: workflow
status: current
version: 1.0.0
tags: [standard, workflow, kafka, consumer, retry, exponential-backoff, dlq, error-handling, resilience]
ai_optimized: true
search_keywords: [kafka, consumer, retry, exponential backoff, dlq, error handling, resilience, consumer group, commit strategy]
related:
  - 04-patterns/backend-patterns/kafka-consumer-pattern.md
  - 05-standards/workflow-standards/batch-workflow-standard.md
  - 05-standards/observability-standards.md
  - 06-workflows/spec-context-strategy.md
---

# Kafka Consumer Standard

> **Purpose**: Define mandatory rules and requirements for all Kafka consumers (Kotlin and Python) to ensure resilient message processing, proper error handling, and operational excellence.

This standard applies to all Kafka consumers in the project, including:
- Kotlin consumers in `service/kotlin/**/batch/**`
- Python consumers in `workflow/**/flows/**` or `workflow/**/consumers/**`

## 1. Retry & Error Handling

### 1.1 Error Classification

#### 1.1.1 Retryable Errors (Transient Failures)

**Definition**: Errors that are likely to succeed on retry due to temporary conditions.

**Categories**:
- **Network Errors**: Connection timeouts, connection refused, network unreachable
- **Service Unavailable**: HTTP 503, 502, 504
- **Rate Limiting**: HTTP 429 (with appropriate backoff)
- **Database Transient Errors**: Connection pool exhaustion, deadlock, temporary lock timeouts
- **Kafka Transient Errors**: Broker not available, network errors
- **Timeout Errors**: Request timeouts (not processing timeouts)

**Retry Behavior**: Retry with exponential backoff up to maximum retry attempts.

#### 1.1.2 Non-Retryable Errors (Permanent Failures)

**Definition**: Errors that will not succeed on retry without code or data changes.

**Categories**:
- **Validation Errors**: Invalid message format, missing required fields, type mismatches
- **Authentication/Authorization**: HTTP 401, 403
- **Not Found (Context-Dependent)**: HTTP 404 (retryable if resource might be created later, non-retryable if resource will never exist)
- **Business Logic Errors**: Invalid business state, constraint violations
- **Schema Errors**: Message schema mismatch, deserialization failures
- **Configuration Errors**: Missing configuration, invalid configuration values

**Retry Behavior**: Send to DLQ immediately (no retries).

#### 1.1.3 Error Classification Requirements

**All consumers MUST**:
1. Classify errors into retryable vs non-retryable categories
2. Implement error classification logic before retry attempts
3. Log error classification decisions for observability
4. Include error classification in metrics (tagged by error type)

**Implementation details and code examples**: See [Kafka Consumer Pattern](../../04-patterns/backend-patterns/kafka-consumer-pattern.md).

### 1.2 Retry Configuration

#### 1.2.1 Standard Retry Parameters

**Required Configuration** (must be configurable via environment variables or config files):

| Parameter | Default | Description |
|-----------|---------|-------------|
| `max_retries` / `maxRetries` | 3 | Maximum number of retry attempts (excluding initial attempt) |
| `initial_retry_delay_ms` / `initialRetryDelayMs` | 1000 | Initial delay before first retry (1 second) |
| `max_retry_delay_ms` / `maxRetryDelayMs` | 30000 | Maximum delay between retries (30 seconds) |
| `retry_backoff_multiplier` / `retryBackoffMultiplier` | 2.0 | Exponential backoff multiplier |
| `retry_jitter` / `retryJitter` | true | Add random jitter to prevent thundering herd |

#### 1.2.2 Exponential Backoff Formula

**Standard Formula**:
```
delay = min(initial_delay_ms * (multiplier ^ retry_count), max_retry_delay_ms)
```

**With Jitter** (recommended):
```
base_delay = min(initial_delay_ms * (multiplier ^ retry_count), max_retry_delay_ms)
jitter = random(0, base_delay * 0.1)  # 10% jitter
delay = base_delay + jitter
```

**Example Progression** (with defaults):
- Retry 1: ~1s (1000ms * 2^0 = 1000ms)
- Retry 2: ~2s (1000ms * 2^1 = 2000ms)
- Retry 3: ~4s (1000ms * 2^2 = 4000ms)
- Retry 4: ~8s (1000ms * 2^3 = 8000ms, capped at 30000ms)

#### 1.2.3 Rate Limiting Considerations

**For consumers with external API rate limits**:
- Retry delays MUST respect external rate limits
- Minimum delay between API calls MUST be enforced
- Example: If API allows 5 requests/minute, minimum delay is 12 seconds
- Retry backoff should use: `max(calculated_backoff, rate_limit_minimum_delay)`

### 1.3 Retry Implementation Requirements

#### 1.3.1 Retry Loop Requirements

**All consumers MUST**:
1. Implement retry loop that attempts processing up to `max_retries + 1` times (initial attempt + retries)
2. Classify errors before retrying (see section 1.1.3)
3. For retryable errors: retry with exponential backoff up to `max_retries`
4. For non-retryable errors: send to DLQ immediately (no retries)
5. After max retries exceeded: send to DLQ
6. Log retry attempts with appropriate log levels (see section 5.2)
7. Track retry metrics (see section 5.1)

**Implementation details and code examples**: See [Kafka Consumer Pattern](../../04-patterns/backend-patterns/kafka-consumer-pattern.md).

#### 1.3.2 Backoff Calculation Requirements

**All consumers MUST**:
1. Calculate backoff delays using exponential backoff formula (section 1.2.2)
2. Apply jitter if `retry_jitter` is enabled (recommended)
3. Respect rate limits when applicable (section 1.2.3)
4. Cap delays at `max_retry_delay_ms`

**Implementation details and code examples**: See [Kafka Consumer Pattern](../../04-patterns/backend-patterns/kafka-consumer-pattern.md).

### 1.4 Shared Python Consumer Runner

All Python batch consumers **should use** the shared `KafkaConsumerRunner` implementation in `workflow/python/common/src/neotool_common/consumer_base.py`:
- It wires retry loops, error classification, DLQ publishing, safe commits, and graceful shutdown in a single place.
- Plug in domain-specific hooks (`message_factory`, `process_message`, DLQ publisher, rate-limit provider, health/cleanup callbacks) to keep each consumer file focused on business logic.
- Shares `workflow/python/common/src/neotool_common/error_classification.py` and `workflow/python/common/src/neotool_common/retry_utils.py` so classification/backoff logic stays consistent.
- Covered by `workflow/python/common/tests/test_consumer_base.py`, fulfilling the testing requirements listed later in this standard.

Following this pattern ensures every Python consumer complies with the retry and DLQ rules from sections 1–4 without duplicating boilerplate.

## 2. DLQ Routing

### 2.1 DLQ Routing Rules

**Messages MUST be sent to DLQ when**:
1. Non-retryable errors occur (immediately, no retries)
2. Maximum retry attempts exceeded for retryable errors
3. Processing timeout exceeded (if timeout is configured)

**Messages MUST NOT be sent to DLQ when**:
1. Retryable error occurs and retry attempts remain
2. Message is successfully processed

### 2.2 DLQ Message Structure

**Required DLQ Message Fields**:

```json
{
  "original_message": { /* original Kafka message */ },
  "error_type": "ValueError",
  "error_message": "Invalid bacen_cod_inst format",
  "failed_at": "2024-01-01T00:00:00Z",
  "retry_count": 3,
  "error_classification": "non-retryable",
  "stack_trace": "..." // Optional, for debugging
}
```

### 2.3 DLQ Publishing

**Requirements**:
- DLQ publishing MUST be retried (with backoff) if it fails
- If DLQ publishing fails after retries, message MUST NOT be committed (will be retried by Kafka)
- DLQ publishing failures MUST be logged and monitored
- DLQ topic MUST be created before consumer starts

### 2.4 DLQ Topic Naming

**Required Pattern**: `<domain>.<entity>.dlq`

**Examples**:
- `swapi.people.dlq`
- `financial_data.institution.dlq`

## 3. Consumer Configuration

### 3.1 Session and Poll Intervals

**Required Configuration** (to prevent consumer group eviction):

| Parameter | Minimum | Recommended | Description |
|-----------|---------|-------------|-------------|
| `session_timeout_ms` | 30000 | 60000 | Time before consumer is considered dead |
| `heartbeat_interval_ms` | 10000 | 10000 | Heartbeat frequency (must be < session_timeout_ms / 3) |
| `max_poll_interval_ms` | 300000 | 600000 | Max time between poll() calls (5-10 minutes) |

**Calculation**:
```
max_poll_interval_ms >= (max_retries * max_retry_delay_ms) + processing_time + buffer
```

**Example**:
- 3 retries × 30s max delay = 90s
- Processing time: ~10s
- Buffer: 200s
- **Total**: ~300s (5 minutes minimum)

### 3.2 Additional Consumer Settings

**Required Settings**:
- `enable_auto_commit=false` (manual commit only)
- `auto_offset_reset=earliest` (or `latest` based on use case)
- `max_poll_records` (set based on processing requirements, often 1 for sequential processing)

**Recommended Settings**:
- `fetch_min_bytes=1` (low latency)
- `fetch_max_wait_ms=500` (balance latency vs throughput)

## 4. Commit Strategy

### 4.1 Commit Requirements

**Requirements**:
- Manual commit only (`enable_auto_commit=false`)
- Commit only after successful processing OR successful DLQ publish
- Do NOT commit if DLQ publish fails (message will be retried by Kafka)
- Commit synchronously to ensure durability

### 4.2 Commit Timing

**Best Practices**:
- Commit immediately after successful processing
- Commit immediately after successful DLQ publish
- Do not batch commits across multiple messages (unless processing is idempotent)
- Handle commit failures gracefully (log and retry)

## 5. Consumer Group Management

### 5.1 Consumer Group Requirements

**All consumers MUST**:
1. Use dedicated consumer groups per job/consumer
2. Consumer group names MUST be unique and descriptive
3. Consumer group names SHOULD follow pattern: `<domain>.<entity>.<version>` or `<service>.<consumer-name>`
4. Document consumer group name in consumer configuration/README

**Examples**:
- `financial_data.parent_institution.v1`
- `app.user_sync.consumer`

### 5.2 Partition Assignment

**Requirements**:
- Use default partition assignment strategy (range or round-robin) unless specific ordering requirements exist
- For ordered processing: ensure messages with same key go to same partition
- Monitor partition assignment and rebalancing events

**Best Practices**:
- Avoid custom partition assignment unless necessary
- Monitor consumer group rebalancing frequency
- Ensure sufficient partitions for desired parallelism

## 6. Metrics and Observability

### 6.1 Required Metrics

**All consumers MUST expose**:

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `{domain}.{entity}.processed` | Counter | `status=success\|failure` | Total messages processed |
| `{domain}.{entity}.processing.duration` | Timer | - | Processing duration |
| `{domain}.{entity}.retry.count` | Counter | `retry_attempt=1\|2\|3` | Retry attempts |
| `{domain}.{entity}.retry.delay` | Timer | `retry_attempt=1\|2\|3` | Retry delay duration |
| `{domain}.{entity}.dlq.count` | Counter | `error_type`, `error_classification` | Messages sent to DLQ |
| `{domain}.{entity}.error.count` | Counter | `error_type`, `error_classification` | Total errors |

### 6.2 Logging Requirements

**Required Log Statements**:
- Retry attempt: `WARN` level with retry count and delay
- DLQ routing: `INFO` level with error type and classification
- Max retries exceeded: `ERROR` level
- Non-retryable error: `WARN` level (immediate DLQ)

**Log Fields**:
- `retry_count`: Current retry attempt number
- `error_type`: Exception type name
- `error_classification`: "retryable" or "non-retryable"
- `backoff_delay_ms`: Calculated delay before retry
- `consumer_group`: Consumer group name
- `partition`: Partition number
- `offset`: Message offset

## 7. Configuration Requirements

### 7.1 Configuration Parameters

**All consumers MUST**:
1. Make all retry parameters configurable (section 1.2.1)
2. Use environment variables (Python) or configuration files (Kotlin)
3. Provide sensible defaults matching standard defaults (section 1.2.1)
4. Validate configuration on startup

**Configuration examples and implementation details**: See [Kafka Consumer Pattern](../../04-patterns/backend-patterns/kafka-consumer-pattern.md).

**Docker Deployment**: Python consumers should be containerized using Docker. See [Kafka Consumer Pattern - Docker Deployment](../../04-patterns/backend-patterns/kafka-consumer-pattern.md#docker-deployment) for Dockerfile template and deployment best practices.

## 8. Testing Requirements

### 8.1 Unit Tests

**MUST test**:
- Retry logic with different error types
- Exponential backoff calculation
- Error classification (retryable vs non-retryable)
- DLQ routing decisions
- Max retries behavior

### 8.2 Integration Tests

**MUST test**:
- End-to-end retry flow with real Kafka
- DLQ message structure and content
- Consumer group behavior during retries
- Commit behavior after retries

## 9. Best Practices

### 9.1 Retry Strategy

**Recommended practices**:
1. Start with standard defaults (3 retries, 1-30s delays)
2. Monitor and tune based on error patterns and downstream SLAs
3. Always enforce minimum delays for rate-limited APIs
4. Enable jitter to prevent thundering herd problems
5. Classify errors correctly - don't retry permanent failures

### 9.2 DLQ Management

**Recommended practices**:
1. Monitor DLQ size and alert when message count exceeds threshold
2. Analyze patterns by grouping DLQ messages by error type
3. Reprocess selectively only after fixing root cause
4. Archive old DLQ messages to cold storage

### 9.3 Performance Considerations

**Recommended practices**:
1. Process messages sequentially per partition for ordering guarantees
2. Use async/threading for I/O operations where possible
3. Set appropriate timeouts to fail fast on permanent errors
4. Monitor consumer lag to detect processing bottlenecks

### 9.4 Consumer Group Management

**Recommended practices**:
1. Use descriptive, unique consumer group names
2. Monitor consumer group rebalancing events
3. Ensure sufficient partitions for desired parallelism
4. Document consumer group names in configuration

**For detailed implementation guidance**: See [Kafka Consumer Pattern](../../04-patterns/backend-patterns/kafka-consumer-pattern.md).

## 10. Compliance

- All new Kafka consumers MUST implement this standard
- Existing consumers MUST be updated to comply within next release cycle
- PRs introducing consumers MUST include retry logic implementation
- Consumer configuration MUST be documented in consumer README
- Metrics MUST be exposed for all retry operations
- Consumer group names MUST be documented

## Related Documentation

- **[Kafka Consumer Pattern](../../04-patterns/backend-patterns/kafka-consumer-pattern.md)** - **Implementation guide with code examples** - Use this for "how to implement"
- [Batch Workflow Standard](./batch-workflow-standard.md) - Overall batch workflow standards
- [Observability Standards](../observability-standards.md) - Metrics and logging requirements
- [Spec Context Strategy](../../06-workflows/spec-context-strategy.md) - Context loading strategy (includes Kafka Consumer phase)
