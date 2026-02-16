# DLQ Retry Consumer - Design Document

## Overview

The DLQ Retry Consumer is a unified service that automatically retries transient failures from Dead Letter Queues (DLQs) across all services in the platform. It implements industry best practices from Amazon, Google, and Uber for handling failed messages with intelligent categorization, cooling-off periods, and progressive retry strategies.

## Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Original Topic                            │
│         financial-data.institution.upserted                  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
                 ┌───────────────┐
                 │   Consumer    │
                 │  (3 retries)  │
                 └───┬───────┬───┘
                     │       │
              Success│       │Failure (after 3 retries)
                     │       │
                     ▼       ▼
              ┌─────────┐  ┌──────────────────────────────────┐
              │   DB    │  │        DLQ Topic                 │
              └─────────┘  │  financial-data.*.dlq            │
                           │                                  │
                           │  DLQMessage {                    │
                           │    original_message              │
                           │    error_category: "transient"   │
                           │    retry_count: 0                │
                           │    next_retry_after: 1h          │
                           │  }                               │
                           └──────────────────────────────────┘
                                         │
                                         ▼
                           ┌──────────────────────────────────┐
                           │   DLQ Retry Consumer             │
                           │   (Scheduled: every 1 hour)      │
                           │                                  │
                           │   1. Read all DLQ messages       │
                           │   2. Check error category        │
                           │   3. Check cooling-off period    │
                           │   4. Republish to original topic │
                           └─────┬────────────────────┬───────┘
                                 │                    │
                       Success   │                    │ Failure
                    (消息重新进入主流程)  │                    │ (retry < 3)
                                 │                    │
                                 ▼                    ▼
                   ┌──────────────────────┐  ┌────────────────────┐
                   │  Message removed     │  │  Republish to DLQ  │
                   │  from DLQ (commit)   │  │  retry_count++     │
                   │                      │  │  next_retry: 6h    │
                   └──────────────────────┘  └────────────────────┘
                                                        │
                                                        ▼
                                              ┌─────────────────────┐
                                              │ After 3 DLQ retries │
                                              │ → permanent error   │
                                              │ (manual only)       │
                                              └─────────────────────┘
```

## Core Concepts

### 1. Error Categorization

Errors are automatically classified into two categories:

#### Transient Errors (Auto-Retry)
- Network timeouts
- Connection failures
- Rate limiting (429, 503)
- Temporary service unavailability (502, 504)
- Database pool exhaustion
- Temporary API failures

#### Permanent Errors (Manual Intervention)
- Validation errors
- Malformed data (400)
- Authentication failures (401)
- Authorization failures (403)
- Resource not found (404)
- Business logic violations
- Messages that exceeded 3 DLQ retry attempts

### 2. Retry Strategy

#### Total Retry Attempts
```
┌─────────────────────────────────────────────────┐
│ Phase 1: Immediate Retries (Main Consumer)     │
│ • Retry 1: 1 second delay                      │
│ • Retry 2: 2 seconds delay                     │
│ • Retry 3: 4 seconds delay                     │
│ Total: 3 attempts over ~7 seconds              │
└─────────────────────────────────────────────────┘
                    ↓ (fails)
┌─────────────────────────────────────────────────┐
│ Phase 2: DLQ Retries (Cooling-Off)             │
│ • DLQ Retry 1: After 1 hour  (retry_count=0)   │
│ • DLQ Retry 2: After 6 hours (retry_count=1)   │
│ • DLQ Retry 3: After 24 hours (retry_count=2)  │
│ Total: 3 attempts over 31 hours                │
└─────────────────────────────────────────────────┘
                    ↓ (fails)
┌─────────────────────────────────────────────────┐
│ Phase 3: Permanent Error (Manual)              │
│ • Error category changed to "permanent"        │
│ • Requires manual investigation and retry      │
│ • Can be retried via neotool CLI               │
└─────────────────────────────────────────────────┘

Total Automatic Attempts: 6
```

#### Backoff Strategy

Default backoff strategy (configurable per DLQ):

```yaml
retry_attempts:
  - retry_count: 0
    delay: 1h
    description: "Short cooling-off for transient network issues"

  - retry_count: 1
    delay: 6h
    description: "Medium cooling-off for service degradation"

  - retry_count: 2
    delay: 24h
    description: "Long cooling-off for infrastructure issues"

  - retry_count: 3
    category: permanent
    description: "Convert to permanent, manual intervention required"
```

### 3. DLQ Message Format

```go
type DLQMessage struct {
    // Original message data
    OriginalMessage  json.RawMessage `json:"original_message"`   // The actual business message
    OriginalKey      string          `json:"original_key"`       // Message key (e.g., CNPJ)
    OriginalTopic    string          `json:"original_topic"`     // Original Kafka topic

    // Error information
    ErrorMessage     string          `json:"error_message"`      // Human-readable error
    ErrorType        string          `json:"error_type"`         // Error classification
    ErrorCategory    string          `json:"error_category"`     // "transient" or "permanent"

    // Retry tracking
    RetryCount       int             `json:"retry_count"`        // Number of DLQ retry attempts
    FailedTimestamp  time.Time       `json:"failed_timestamp"`   // When message first failed
    NextRetryAfter   *time.Time      `json:"next_retry_after"`   // When to retry next (nil = don't retry)

    // Audit trail
    RetryHistory     []RetryAttempt  `json:"retry_history"`      // History of all retry attempts
}

type RetryAttempt struct {
    Timestamp     time.Time `json:"timestamp"`       // When retry was attempted
    RetryCount    int       `json:"retry_count"`     // Retry count at this attempt
    ErrorMessage  string    `json:"error_message"`   // Error that occurred
    ErrorType     string    `json:"error_type"`      // Error classification
    WaitDuration  string    `json:"wait_duration"`   // How long waited before retry
}
```

## Configuration

### DLQ Mapping Configuration

The consumer uses a configuration file to define which DLQs to monitor and their retry strategies.

#### Configuration File: `config/dlq-mappings.yaml`

```yaml
version: "1.0"

# Global default settings (can be overridden per DLQ)
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
    transient_patterns:
      - "timeout"
      - "deadline exceeded"
      - "connection refused"
      - "connection reset"
      - "rate limit"
      - "429"
      - "503"
      - "502"
      - "504"
    permanent_patterns:
      - "validation"
      - "invalid"
      - "400"
      - "401"
      - "403"
      - "404"

# DLQ mappings
dlq_mappings:
  # Institution Enhancement Service
  - name: institution-enhancement
    enabled: true
    dlq_topic: financial-data.institution.upserted.enhancement.dlq
    original_topic: financial-data.institution.upserted
    consumer_group: dlq-retry-consumer-institution-enhancement
    description: "Retries failed institution enhancement messages"
    # Uses default retry strategy

  # Another service with custom retry strategy
  - name: payment-processor
    enabled: true
    dlq_topic: payments.transaction.processed.dlq
    original_topic: payments.transaction.processed
    consumer_group: dlq-retry-consumer-payment-processor
    description: "Retries failed payment processing messages"
    retry_strategy:
      max_retries: 5  # Custom: more retries for critical payments
      backoff:
        - retry_count: 0
          delay: 30m    # Custom: faster first retry
        - retry_count: 1
          delay: 2h
        - retry_count: 2
          delay: 6h
        - retry_count: 3
          delay: 12h
        - retry_count: 4
          delay: 24h
    error_categorization:
      # Custom transient patterns for payments
      transient_patterns:
        - "timeout"
        - "payment gateway unavailable"
        - "insufficient funds"  # Could be transient if balance changes

  # Disabled DLQ (not processed)
  - name: legacy-service
    enabled: false
    dlq_topic: legacy.events.dlq
    original_topic: legacy.events
    description: "Legacy service - disabled for migration"
```

#### Environment Variable Overrides

You can override configuration via environment variables:

```bash
# Override config file path
DLQ_CONFIG_PATH=/custom/path/dlq-mappings.yaml

# Override specific DLQ settings
DLQ_institution-enhancement_ENABLED=true
DLQ_institution-enhancement_MAX_RETRIES=5
DLQ_institution-enhancement_BACKOFF_0_DELAY=30m

# Global overrides
DLQ_DEFAULT_MAX_RETRIES=4
DLQ_DEFAULT_BACKOFF_0_DELAY=2h
```

### Configuration Schema

```go
type Config struct {
    Version  string           `yaml:"version"`
    Defaults DefaultConfig    `yaml:"defaults"`
    Mappings []DLQMapping     `yaml:"dlq_mappings"`
}

type DefaultConfig struct {
    RetryStrategy        RetryStrategyConfig        `yaml:"retry_strategy"`
    ErrorCategorization  ErrorCategorizationConfig  `yaml:"error_categorization"`
}

type DLQMapping struct {
    Name                 string                      `yaml:"name"`
    Enabled              bool                        `yaml:"enabled"`
    DLQTopic             string                      `yaml:"dlq_topic"`
    OriginalTopic        string                      `yaml:"original_topic"`
    ConsumerGroup        string                      `yaml:"consumer_group"`
    Description          string                      `yaml:"description"`
    RetryStrategy        *RetryStrategyConfig        `yaml:"retry_strategy,omitempty"`        // nil = use default
    ErrorCategorization  *ErrorCategorizationConfig  `yaml:"error_categorization,omitempty"`  // nil = use default
}

type RetryStrategyConfig struct {
    MaxRetries int             `yaml:"max_retries"`
    Backoff    []BackoffConfig `yaml:"backoff"`
}

type BackoffConfig struct {
    RetryCount int           `yaml:"retry_count"`
    Delay      time.Duration `yaml:"delay"`
}

type ErrorCategorizationConfig struct {
    TransientPatterns []string `yaml:"transient_patterns"`
    PermanentPatterns []string `yaml:"permanent_patterns"`
}
```

## Processing Logic

### Consumer Workflow

```go
// Pseudocode for DLQ Retry Consumer
func processDLQMessage(dlqMsg DLQMessage, config DLQMapping) error {
    // Step 1: Check if message is permanent
    if dlqMsg.ErrorCategory == "permanent" {
        logger.Info("Skipping permanent error", "key", dlqMsg.OriginalKey)
        return nil  // Skip, requires manual intervention
    }

    // Step 2: Check retry limit
    if dlqMsg.RetryCount >= config.RetryStrategy.MaxRetries {
        logger.Warn("Converting to permanent after max retries",
            "key", dlqMsg.OriginalKey,
            "retry_count", dlqMsg.RetryCount)

        // Convert to permanent
        dlqMsg.ErrorCategory = "permanent"
        republishToDLQ(dlqMsg)
        return nil
    }

    // Step 3: Check cooling-off period
    if dlqMsg.NextRetryAfter != nil && time.Now().Before(*dlqMsg.NextRetryAfter) {
        logger.Debug("Message not ready for retry",
            "key", dlqMsg.OriginalKey,
            "next_retry", dlqMsg.NextRetryAfter)
        return nil  // Not ready yet, skip this round
    }

    // Step 4: Republish to original topic (unwrap DLQ envelope)
    err := producer.Publish(
        dlqMsg.OriginalTopic,
        dlqMsg.OriginalKey,
        dlqMsg.OriginalMessage,  // Unwrap - send original message
    )

    if err != nil {
        // Step 5: Handle failure - republish to DLQ with updated metadata
        nextRetryCount := dlqMsg.RetryCount + 1
        nextRetryDelay := getBackoffDelay(nextRetryCount, config.RetryStrategy)
        nextRetryTime := time.Now().Add(nextRetryDelay)

        // Add to retry history
        dlqMsg.RetryHistory = append(dlqMsg.RetryHistory, RetryAttempt{
            Timestamp:    time.Now(),
            RetryCount:   dlqMsg.RetryCount,
            ErrorMessage: err.Error(),
            ErrorType:    categorizeError(err, config.ErrorCategorization),
            WaitDuration: nextRetryDelay.String(),
        })

        dlqMsg.RetryCount = nextRetryCount
        dlqMsg.NextRetryAfter = &nextRetryTime

        republishToDLQ(dlqMsg)
        return err
    }

    // Step 6: Success - message back in main flow
    logger.Info("Successfully republished to original topic",
        "key", dlqMsg.OriginalKey,
        "retry_count", dlqMsg.RetryCount)

    return nil
}
```

### Error Categorization Logic

```go
func categorizeError(err error, config ErrorCategorizationConfig) string {
    if err == nil {
        return "permanent"  // Should not happen, but safe default
    }

    errStr := strings.ToLower(err.Error())

    // Check transient patterns first (more specific)
    for _, pattern := range config.TransientPatterns {
        if strings.Contains(errStr, strings.ToLower(pattern)) {
            return "transient"
        }
    }

    // Check permanent patterns
    for _, pattern := range config.PermanentPatterns {
        if strings.Contains(errStr, strings.ToLower(pattern)) {
            return "permanent"
        }
    }

    // Default to permanent (safer - requires manual review)
    return "permanent"
}
```

## Deployment

### Kubernetes CronJob (Recommended)

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: dlq-retry-consumer
  namespace: data-platform
spec:
  schedule: "0 * * * *"  # Every hour
  concurrencyPolicy: Forbid  # Don't run concurrent jobs
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
          - name: dlq-retry-consumer
            image: dlq-retry-consumer:latest
            env:
              - name: KAFKA_BROKERS
                value: "kafka:9092"
              - name: DLQ_CONFIG_PATH
                value: "/config/dlq-mappings.yaml"
            volumeMounts:
              - name: config
                mountPath: /config
                readOnly: true
          volumes:
            - name: config
              configMap:
                name: dlq-retry-consumer-config
```

### Docker Compose (Development)

```yaml
services:
  dlq-retry-consumer:
    build:
      context: .
      dockerfile: Dockerfile
      target: dlq-retry-consumer
    volumes:
      - ./config/dlq-mappings.yaml:/config/dlq-mappings.yaml:ro
    environment:
      KAFKA_BROKERS: kafka:9092
      DLQ_CONFIG_PATH: /config/dlq-mappings.yaml
    profiles:
      - tools  # On-demand only
```

## Monitoring & Observability

### Metrics to Track

```
# DLQ depth by topic
kafka_dlq_message_count{topic="financial-data.institution.upserted.enhancement.dlq"}

# Retry success rate
dlq_retry_success_rate{dlq="institution-enhancement"}

# Messages by category
dlq_messages_by_category{dlq="institution-enhancement", category="transient"}
dlq_messages_by_category{dlq="institution-enhancement", category="permanent"}

# Retry attempts distribution
dlq_retry_count_distribution{dlq="institution-enhancement", retry_count="0"}

# Processing time
dlq_processing_duration_seconds{dlq="institution-enhancement"}
```

### Alerts

```yaml
# Alert when DLQ depth is high
- alert: DLQDepthHigh
  expr: kafka_dlq_message_count > 1000
  for: 10m
  annotations:
    summary: "DLQ {{ $labels.topic }} has {{ $value }} messages"

# Alert when retry success rate is low
- alert: DLQRetrySuccessRateLow
  expr: dlq_retry_success_rate < 0.5
  for: 1h
  annotations:
    summary: "DLQ {{ $labels.dlq }} retry success rate is {{ $value }}"

# Alert when too many permanent errors
- alert: DLQPermanentErrorsHigh
  expr: dlq_messages_by_category{category="permanent"} > 100
  for: 1h
  annotations:
    summary: "DLQ {{ $labels.dlq }} has {{ $value }} permanent errors"
```

## Security Considerations

1. **Least Privilege**: Consumer should only have read access to DLQ topics and write access to original topics
2. **Audit Trail**: All retry attempts are logged in `retry_history` for compliance
3. **Rate Limiting**: Consumer respects original service rate limits
4. **Data Privacy**: PII in messages should be encrypted or masked in logs

## Testing Strategy

### Unit Tests
- Error categorization logic
- Backoff calculation
- Configuration parsing
- Message envelope/unwrapping

### Integration Tests
- End-to-end flow: DLQ → Consumer → Original Topic
- Retry count increment
- Permanent conversion after max retries
- Cooling-off period enforcement

### Load Tests
- Process 10k+ DLQ messages
- Concurrent DLQ processing
- Memory usage under load

## Future Enhancements

1. **Circuit Breaker**: Stop retrying if success rate drops below threshold
2. **Adaptive Backoff**: Adjust delays based on error patterns
3. **Batch Processing**: Process messages in batches for efficiency
4. **Dead DLQ**: Separate topic for messages that exhausted all retries
5. **Web UI**: Dashboard for DLQ monitoring and manual retry
6. **ML-based Categorization**: Use ML to improve error classification

## References

- [Amazon SQS Dead Letter Queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)
- [Google Cloud Pub/Sub Dead Letter Topics](https://cloud.google.com/pubsub/docs/dead-letter-topics)
- [Uber's Kafka Consumer Architecture](https://eng.uber.com/kafka/)
- [LinkedIn's Kafka Reliability](https://engineering.linkedin.com/kafka/kafka-new-messaging-paradigm)

## Changelog

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-12-30 | 1.0 | Initial design | - |
