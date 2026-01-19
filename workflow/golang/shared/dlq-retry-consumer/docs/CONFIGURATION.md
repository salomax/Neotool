# DLQ Retry Consumer - Configuration Reference

## Configuration File

The DLQ Retry Consumer uses a YAML configuration file to define DLQ mappings and retry strategies.

### Default Location

```
/config/dlq-mappings.yaml
```

Override with environment variable:
```bash
export DLQ_CONFIG_PATH=/custom/path/dlq-mappings.yaml
```

## Complete Configuration Example

```yaml
version: "1.0"

# Global default settings applied to all DLQs unless overridden
defaults:
  retry_strategy:
    max_retries: 3
    backoff:
      - retry_count: 0
        delay: 1h
      - retry_count: 1
        delay: 6h
      - retry_count: 2
        delay: 24h

  error_categorization:
    # Patterns that indicate transient (retryable) errors
    transient_patterns:
      - "timeout"
      - "deadline exceeded"
      - "connection refused"
      - "connection reset"
      - "connection pool exhausted"
      - "rate limit"
      - "too many requests"
      - "429"
      - "503"
      - "502"
      - "504"
      - "temporary failure"
      - "service unavailable"
      - "database pool exhausted"

    # Patterns that indicate permanent (non-retryable) errors
    permanent_patterns:
      - "validation"
      - "invalid"
      - "malformed"
      - "400"
      - "401"
      - "403"
      - "404"
      - "not found"
      - "unauthorized"
      - "forbidden"
      - "bad request"

# DLQ mappings - list of DLQs to monitor
dlq_mappings:
  # Example 1: Institution Enhancement (uses defaults)
  - name: institution-enhancement
    enabled: true
    dlq_topic: financial-data.institution.upserted.enhancement.dlq
    original_topic: financial-data.institution.upserted
    consumer_group: dlq-retry-consumer-institution-enhancement
    description: "Retries failed institution enhancement messages"
    # retry_strategy not specified - uses defaults
    # error_categorization not specified - uses defaults

  # Example 2: Payment Processor (custom retry strategy)
  - name: payment-processor
    enabled: true
    dlq_topic: payments.transaction.processed.dlq
    original_topic: payments.transaction.processed
    consumer_group: dlq-retry-consumer-payment-processor
    description: "Retries failed payment processing messages"
    retry_strategy:
      max_retries: 5  # More retries for critical payments
      backoff:
        - retry_count: 0
          delay: 30m    # Faster first retry
        - retry_count: 1
          delay: 2h
        - retry_count: 2
          delay: 6h
        - retry_count: 3
          delay: 12h
        - retry_count: 4
          delay: 24h

  # Example 3: User Notifications (custom error patterns)
  - name: user-notifications
    enabled: true
    dlq_topic: notifications.email.sent.dlq
    original_topic: notifications.email.sent
    consumer_group: dlq-retry-consumer-user-notifications
    description: "Retries failed email notification messages"
    error_categorization:
      transient_patterns:
        - "timeout"
        - "smtp connection failed"
        - "email server unavailable"
        - "rate limit exceeded"
      permanent_patterns:
        - "invalid email"
        - "email address not found"
        - "mailbox full"
        - "blocked recipient"

  # Example 4: Disabled DLQ
  - name: legacy-service
    enabled: false
    dlq_topic: legacy.events.dlq
    original_topic: legacy.events
    consumer_group: dlq-retry-consumer-legacy-service
    description: "Legacy service - disabled during migration"
```

## Configuration Fields

### Top-Level Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `version` | string | Yes | Configuration schema version (currently "1.0") |
| `defaults` | object | Yes | Default settings for all DLQs |
| `dlq_mappings` | array | Yes | List of DLQ configurations |

### Defaults Object

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `retry_strategy` | object | Yes | Default retry behavior |
| `error_categorization` | object | Yes | Default error classification rules |

### Retry Strategy Object

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `max_retries` | int | Yes | Maximum number of DLQ retry attempts (0-10) |
| `backoff` | array | Yes | Backoff delays for each retry attempt |

### Backoff Object

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `retry_count` | int | Yes | Retry attempt number (0-indexed) |
| `delay` | duration | Yes | Time to wait before retry (e.g., "1h", "30m", "24h") |

**Supported duration formats:**
- `30s` - 30 seconds
- `5m` - 5 minutes
- `2h` - 2 hours
- `1h30m` - 1 hour 30 minutes
- `24h` - 24 hours

### Error Categorization Object

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `transient_patterns` | array[string] | Yes | Substring patterns for transient errors |
| `permanent_patterns` | array[string] | Yes | Substring patterns for permanent errors |

**Pattern matching:**
- Case-insensitive substring match
- First match wins (transient checked first)
- If no match, defaults to permanent (safer)

### DLQ Mapping Object

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Unique identifier for this DLQ |
| `enabled` | bool | Yes | Whether to process this DLQ |
| `dlq_topic` | string | Yes | Kafka topic name for DLQ |
| `original_topic` | string | Yes | Kafka topic to republish successful retries |
| `consumer_group` | string | Yes | Kafka consumer group ID |
| `description` | string | No | Human-readable description |
| `retry_strategy` | object | No | Override default retry strategy |
| `error_categorization` | object | No | Override default error categorization |

## Environment Variable Overrides

You can override any configuration value using environment variables.

### Syntax

```
DLQ_<MAPPING_NAME>_<FIELD>=<VALUE>
```

### Examples

```bash
# Enable/disable specific DLQ
export DLQ_institution-enhancement_ENABLED=true
export DLQ_payment-processor_ENABLED=false

# Override max retries for specific DLQ
export DLQ_institution-enhancement_MAX_RETRIES=5

# Override backoff delay for specific retry count
export DLQ_institution-enhancement_BACKOFF_0_DELAY=30m
export DLQ_institution-enhancement_BACKOFF_1_DELAY=2h

# Override global defaults
export DLQ_DEFAULT_MAX_RETRIES=4
export DLQ_DEFAULT_BACKOFF_0_DELAY=2h
export DLQ_DEFAULT_BACKOFF_1_DELAY=8h
export DLQ_DEFAULT_BACKOFF_2_DELAY=24h
```

### Priority Order

1. Environment variable override (highest priority)
2. DLQ-specific configuration
3. Default configuration (lowest priority)

## Validation Rules

### Required Fields

- All top-level fields must be present
- Each DLQ mapping must have: `name`, `enabled`, `dlq_topic`, `original_topic`, `consumer_group`
- Retry strategy must have `max_retries` and at least one `backoff` entry
- Error categorization must have both `transient_patterns` and `permanent_patterns`

### Constraints

- `max_retries`: Must be between 0 and 10
- `backoff` array: Must have exactly `max_retries` entries
- `retry_count` in backoff: Must be sequential starting from 0
- `delay`: Must be a valid duration string
- `name`: Must be unique across all mappings
- `dlq_topic` and `original_topic`: Must be valid Kafka topic names
- `consumer_group`: Must be a valid Kafka consumer group name

### Validation Errors

The consumer will fail to start if configuration is invalid:

```
ERROR: Invalid configuration
  - DLQ mapping "institution-enhancement": missing required field "dlq_topic"
  - DLQ mapping "payment-processor": max_retries (12) exceeds limit (10)
  - DLQ mapping "user-notifications": backoff array has 2 entries but max_retries is 3
  - Duplicate DLQ name: "institution-enhancement"
```

## Configuration Examples

### Minimal Configuration

```yaml
version: "1.0"

defaults:
  retry_strategy:
    max_retries: 3
    backoff:
      - retry_count: 0
        delay: 1h
      - retry_count: 1
        delay: 6h
      - retry_count: 2
        delay: 24h
  error_categorization:
    transient_patterns: ["timeout", "503"]
    permanent_patterns: ["validation", "404"]

dlq_mappings:
  - name: my-service
    enabled: true
    dlq_topic: my-service.events.dlq
    original_topic: my-service.events
    consumer_group: dlq-retry-consumer-my-service
```

### Aggressive Retry Strategy

```yaml
# Fast retries for low-latency services
dlq_mappings:
  - name: real-time-processor
    enabled: true
    dlq_topic: realtime.events.dlq
    original_topic: realtime.events
    consumer_group: dlq-retry-consumer-realtime
    retry_strategy:
      max_retries: 4
      backoff:
        - retry_count: 0
          delay: 5m     # Very fast first retry
        - retry_count: 1
          delay: 15m
        - retry_count: 2
          delay: 1h
        - retry_count: 3
          delay: 6h
```

### Conservative Retry Strategy

```yaml
# Slow retries for batch processing
dlq_mappings:
  - name: batch-processor
    enabled: true
    dlq_topic: batch.jobs.dlq
    original_topic: batch.jobs
    consumer_group: dlq-retry-consumer-batch
    retry_strategy:
      max_retries: 2
      backoff:
        - retry_count: 0
          delay: 6h     # Long initial delay
        - retry_count: 1
          delay: 24h    # Very long second delay
```

### Service-Specific Error Patterns

```yaml
# Email service with custom error patterns
dlq_mappings:
  - name: email-service
    enabled: true
    dlq_topic: email.sent.dlq
    original_topic: email.sent
    consumer_group: dlq-retry-consumer-email
    error_categorization:
      transient_patterns:
        - "smtp timeout"
        - "connection to mail server failed"
        - "greylisted"
        - "rate limit"
      permanent_patterns:
        - "invalid email address"
        - "mailbox does not exist"
        - "recipient blocked"
        - "spam detected"
```

## Hot Reload

The consumer supports hot-reloading configuration without restart (future enhancement):

```bash
# Send SIGHUP to reload config
kill -HUP <consumer-pid>

# Or use kubectl
kubectl exec dlq-retry-consumer -- kill -HUP 1
```

Changes that take effect on reload:
- ✅ Enable/disable DLQs
- ✅ Update retry strategies
- ✅ Update error patterns
- ❌ Add/remove DLQ mappings (requires restart)
- ❌ Change Kafka connection settings (requires restart)

## Configuration Best Practices

### 1. Start Conservative

Begin with default retry strategy and adjust based on metrics:

```yaml
retry_strategy:
  max_retries: 3
  backoff:
    - retry_count: 0
      delay: 1h
    - retry_count: 1
      delay: 6h
    - retry_count: 2
      delay: 24h
```

### 2. Service-Specific Tuning

Adjust based on service characteristics:

| Service Type | Suggested Strategy |
|--------------|-------------------|
| Real-time API | Fast retries (5m, 15m, 1h) |
| Batch processing | Slow retries (6h, 24h, 48h) |
| Payment processing | More retries (5 attempts) |
| Notifications | Moderate retries (default) |

### 3. Error Pattern Discovery

Use DLQ inspector to discover common error patterns:

```bash
# Inspect DLQ to find patterns
docker compose run --rm institution-enhancement-dlq-inspector

# Add discovered patterns to config
error_categorization:
  transient_patterns:
    - "cnpja api timeout"  # Discovered pattern
    - "database connection pool full"  # Discovered pattern
```

### 4. Monitor and Adjust

Track metrics to optimize configuration:

```
# If retry success rate is low, increase delays
dlq_retry_success_rate < 30% → Increase backoff delays

# If DLQ depth keeps growing, check error patterns
dlq_message_count increasing → Review permanent vs transient classification

# If retries succeed quickly, decrease delays
dlq_retry_success_on_first_attempt > 80% → Reduce initial delay
```

## Troubleshooting

### Configuration Not Loading

```bash
# Check config file path
echo $DLQ_CONFIG_PATH

# Validate YAML syntax
yamllint /config/dlq-mappings.yaml

# Check file permissions
ls -la /config/dlq-mappings.yaml
```

### DLQ Not Being Processed

```yaml
# Ensure enabled is true
- name: my-service
  enabled: true  # Must be true

# Check consumer group is unique
consumer_group: dlq-retry-consumer-my-service  # Must be unique

# Verify topic names
dlq_topic: my-service.events.dlq  # Must exist in Kafka
original_topic: my-service.events  # Must exist in Kafka
```

### Retry Strategy Not Working

```yaml
# Ensure backoff array matches max_retries
retry_strategy:
  max_retries: 3
  backoff:  # Must have exactly 3 entries
    - retry_count: 0
      delay: 1h
    - retry_count: 1
      delay: 6h
    - retry_count: 2
      delay: 24h
```

## See Also

- [Design Document](./DESIGN.md) - Architecture and concepts
- [Operations Runbook](./RUNBOOK.md) - Day-to-day operations
- [API Reference](./API.md) - Code-level documentation
