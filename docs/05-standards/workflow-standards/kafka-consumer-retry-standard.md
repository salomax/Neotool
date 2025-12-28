---
title: Kafka Consumer Retry Standard
type: standard
category: workflow
status: current
version: 1.0.0
tags: [standard, workflow, kafka, consumer, retry, exponential-backoff, dlq]
ai_optimized: true
search_keywords: [kafka, consumer, retry, exponential backoff, dlq, error handling, resilience]
related:
  - 04-patterns/backend-patterns/kafka-consumer-pattern.md
  - 05-standards/workflow-standards/batch-workflow-standard.md
  - 05-standards/observability-standards.md
  - 06-workflows/spec-context-strategy.md
---

# Kafka Consumer Retry Standard

> **Purpose**: Define mandatory retry mechanisms with exponential backoff for all Kafka consumers (Kotlin and Python) to ensure resilient message processing and proper error handling.

This standard applies to all Kafka consumers in the project, including:
- Kotlin consumers in `service/kotlin/**/batch/**`
- Python consumers in `workflow/**/flows/**` or `workflow/**/consumers/**`

## 1. Error Classification

### 1.1 Retryable Errors (Transient Failures)

**Definition**: Errors that are likely to succeed on retry due to temporary conditions.

**Categories**:
- **Network Errors**: Connection timeouts, connection refused, network unreachable
- **Service Unavailable**: HTTP 503, 502, 504
- **Rate Limiting**: HTTP 429 (with appropriate backoff)
- **Database Transient Errors**: Connection pool exhaustion, deadlock, temporary lock timeouts
- **Kafka Transient Errors**: Broker not available, network errors
- **Timeout Errors**: Request timeouts (not processing timeouts)

**Retry Behavior**: Retry with exponential backoff up to maximum retry attempts.

### 1.2 Non-Retryable Errors (Permanent Failures)

**Definition**: Errors that will not succeed on retry without code or data changes.

**Categories**:
- **Validation Errors**: Invalid message format, missing required fields, type mismatches
- **Authentication/Authorization**: HTTP 401, 403
- **Not Found (Context-Dependent)**: HTTP 404 (retryable if resource might be created later, non-retryable if resource will never exist)
- **Business Logic Errors**: Invalid business state, constraint violations
- **Schema Errors**: Message schema mismatch, deserialization failures
- **Configuration Errors**: Missing configuration, invalid configuration values

**Retry Behavior**: Send to DLQ immediately (no retries).

### 1.3 Implementation Requirements

**All consumers MUST**:
1. Classify errors into retryable vs non-retryable categories
2. Implement error classification logic before retry attempts
3. Log error classification decisions for observability
4. Include error classification in metrics (tagged by error type)

## 2. Retry Configuration

### 2.1 Standard Retry Parameters

**Required Configuration** (must be configurable via environment variables or config files):

| Parameter | Default | Description |
|-----------|---------|-------------|
| `max_retries` / `maxRetries` | 3 | Maximum number of retry attempts (excluding initial attempt) |
| `initial_retry_delay_ms` / `initialRetryDelayMs` | 1000 | Initial delay before first retry (1 second) |
| `max_retry_delay_ms` / `maxRetryDelayMs` | 30000 | Maximum delay between retries (30 seconds) |
| `retry_backoff_multiplier` / `retryBackoffMultiplier` | 2.0 | Exponential backoff multiplier |
| `retry_jitter` / `retryJitter` | true | Add random jitter to prevent thundering herd |

### 2.2 Exponential Backoff Formula

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

### 2.3 Rate Limiting Considerations

**For consumers with external API rate limits**:
- Retry delays MUST respect external rate limits
- Minimum delay between API calls MUST be enforced
- Example: If API allows 5 requests/minute, minimum delay is 12 seconds
- Retry backoff should use: `max(calculated_backoff, rate_limit_minimum_delay)`

## 3. Retry Implementation

### 3.1 Retry Loop Structure

**Required Pattern**:

```python
# Python example
retry_count = 0
max_retries = config.max_retries

while retry_count <= max_retries:
    try:
        result = process_message(message)
        if result["success"]:
            return result
        # Processing failed - check if retryable
        if not is_retryable_error(result["error"]):
            # Non-retryable - send to DLQ immediately
            publish_to_dlq(message, result["error"])
            return result
    except RetryableException as e:
        if retry_count >= max_retries:
            # Max retries exceeded - send to DLQ
            publish_to_dlq(message, e)
            raise
        # Calculate backoff delay
        delay = calculate_backoff(retry_count, config)
        logger.warning(f"Retryable error, retrying in {delay}ms: {e}")
        time.sleep(delay / 1000.0)  # Convert to seconds
        retry_count += 1
    except NonRetryableException as e:
        # Non-retryable - send to DLQ immediately
        publish_to_dlq(message, e)
        raise
```

### 3.2 Error Classification Implementation

**Required Error Classification Function**:

```python
# Python example
def is_retryable_error(error: Exception) -> bool:
    """Classify error as retryable or non-retryable."""
    # Network/connection errors
    if isinstance(error, (ConnectionError, TimeoutError, requests.exceptions.Timeout)):
        return True
    
    # HTTP errors
    if isinstance(error, requests.exceptions.HTTPError):
        if error.response.status_code in [429, 502, 503, 504]:
            return True
        if error.response.status_code in [400, 401, 403, 404]:
            return False
    
    # Database transient errors
    if isinstance(error, psycopg2.OperationalError):
        # Connection errors are retryable
        return True
    if isinstance(error, psycopg2.IntegrityError):
        # Constraint violations are not retryable
        return False
    
    # Validation errors are not retryable
    if isinstance(error, ValueError):
        return False
    
    # Default: assume retryable for unknown errors
    return True
```

### 3.3 Backoff Calculation

**Required Backoff Calculation**:

```python
# Python example
import random

def calculate_backoff(retry_count: int, config: Config) -> int:
    """Calculate exponential backoff delay with jitter."""
    base_delay = min(
        config.initial_retry_delay_ms * (config.retry_backoff_multiplier ** retry_count),
        config.max_retry_delay_ms
    )
    
    if config.retry_jitter:
        jitter = random.uniform(0, base_delay * 0.1)  # 10% jitter
        return int(base_delay + jitter)
    
    return int(base_delay)
```

## 4. DLQ Routing

### 4.1 DLQ Routing Rules

**Messages MUST be sent to DLQ when**:
1. Non-retryable errors occur (immediately, no retries)
2. Maximum retry attempts exceeded for retryable errors
3. Processing timeout exceeded (if timeout is configured)

**Messages MUST NOT be sent to DLQ when**:
1. Retryable error occurs and retry attempts remain
2. Message is successfully processed

### 4.2 DLQ Message Structure

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

### 4.3 DLQ Publishing

**Requirements**:
- DLQ publishing MUST be retried (with backoff) if it fails
- If DLQ publishing fails after retries, message MUST NOT be committed (will be retried by Kafka)
- DLQ publishing failures MUST be logged and monitored
- DLQ topic MUST be created before consumer starts

## 5. Kafka Consumer Configuration

### 5.1 Session and Poll Intervals

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

### 5.2 Commit Strategy

**Requirements**:
- Manual commit only (`enable_auto_commit=false`)
- Commit only after successful processing OR successful DLQ publish
- Do NOT commit if DLQ publish fails (message will be retried by Kafka)
- Commit synchronously to ensure durability

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

## 7. Implementation Examples

### 7.1 Python Consumer Pattern

```python
def process_with_retry(message: InstitutionUpsertedMessage) -> Dict[str, Any]:
    """Process message with retry logic and exponential backoff."""
    retry_count = 0
    max_retries = config.max_retries
    
    while retry_count <= max_retries:
        try:
            result = process_message(message)
            if result["success"]:
                metrics.increment_processed("success")
                return result
            
            # Check if error is retryable
            error = result.get("error")
            if not is_retryable_error(error):
                metrics.increment_error(error_type=type(error).__name__, classification="non-retryable")
                publish_to_dlq(message, error, retry_count=retry_count)
                return result
            
            # Retryable error - check if we should retry
            if retry_count >= max_retries:
                metrics.increment_error(error_type=type(error).__name__, classification="retryable")
                publish_to_dlq(message, error, retry_count=retry_count)
                return result
            
            # Calculate and apply backoff
            delay_ms = calculate_backoff(retry_count, config)
            logger.warning(
                f"Retryable error (attempt {retry_count + 1}/{max_retries + 1}), "
                f"retrying in {delay_ms}ms: {error}"
            )
            metrics.increment_retry(retry_attempt=retry_count + 1)
            time.sleep(delay_ms / 1000.0)
            retry_count += 1
            
        except RetryableException as e:
            if retry_count >= max_retries:
                metrics.increment_error(error_type=type(e).__name__, classification="retryable")
                publish_to_dlq(message, e, retry_count=retry_count)
                raise
            
            delay_ms = calculate_backoff(retry_count, config)
            logger.warning(f"Retryable exception, retrying in {delay_ms}ms: {e}")
            metrics.increment_retry(retry_attempt=retry_count + 1)
            time.sleep(delay_ms / 1000.0)
            retry_count += 1
            
        except NonRetryableException as e:
            metrics.increment_error(error_type=type(e).__name__, classification="non-retryable")
            publish_to_dlq(message, e, retry_count=0)
            raise
    
    # Should not reach here, but handle gracefully
    raise Exception("Max retries exceeded")
```

### 7.2 Kotlin Consumer Pattern

```kotlin
// Kotlin consumers should use AbstractKafkaConsumer base class
// which implements retry logic with exponential backoff
// See: 04-patterns/backend-patterns/kafka-consumer-pattern.md
```

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

## 9. Configuration Examples

### 9.1 Python Consumer Configuration

```python
# config.py or environment variables
@dataclass
class RetryConfig:
    max_retries: int = int(os.getenv("MAX_RETRIES", "3"))
    initial_retry_delay_ms: int = int(os.getenv("INITIAL_RETRY_DELAY_MS", "1000"))
    max_retry_delay_ms: int = int(os.getenv("MAX_RETRY_DELAY_MS", "30000"))
    retry_backoff_multiplier: float = float(os.getenv("RETRY_BACKOFF_MULTIPLIER", "2.0"))
    retry_jitter: bool = os.getenv("RETRY_JITTER", "true").lower() == "true"
```

### 9.2 Kotlin Consumer Configuration

```yaml
# application.yml
batch:
  consumer:
    max-retries: 3
    initial-retry-delay-ms: 1000
    max-retry-delay-ms: 30000
    retry-backoff-multiplier: 2.0
    retry-jitter: true
```

## 10. Best Practices

### 10.1 Retry Strategy

1. **Start Conservative**: Begin with 3 retries, 1-30s delays
2. **Monitor and Tune**: Adjust based on error patterns and downstream SLAs
3. **Respect Rate Limits**: Always enforce minimum delays for rate-limited APIs
4. **Use Jitter**: Prevent thundering herd problems
5. **Classify Errors**: Don't retry permanent failures

### 10.2 DLQ Management

1. **Monitor DLQ Size**: Alert when DLQ message count exceeds threshold
2. **Analyze Patterns**: Group DLQ messages by error type to identify systemic issues
3. **Reprocess Selectively**: Don't bulk reprocess without fixing root cause
4. **Archive Old Messages**: Move old DLQ messages to cold storage

### 10.3 Performance Considerations

1. **Sequential Processing**: Process messages sequentially per partition for ordering
2. **Avoid Blocking**: Use async/threading for I/O operations where possible
3. **Tune Timeouts**: Set appropriate timeouts to fail fast on permanent errors
4. **Monitor Lag**: Track consumer lag to detect processing bottlenecks

## 11. Compliance

- All new Kafka consumers MUST implement this retry standard
- Existing consumers MUST be updated to comply within next release cycle
- PRs introducing consumers MUST include retry logic implementation
- Retry configuration MUST be documented in consumer README
- Metrics MUST be exposed for all retry operations

## Related Documentation

- [Kafka Consumer Pattern](../../04-patterns/backend-patterns/kafka-consumer-pattern.md) - Kotlin consumer implementation pattern
- [Batch Workflow Standard](./batch-workflow-standard.md) - Overall batch workflow standards
- [Observability Standards](../observability-standards.md) - Metrics and logging requirements
- [Spec Context Strategy](../../06-workflows/spec-context-strategy.md) - Context loading strategy (includes Kafka Consumer phase)

