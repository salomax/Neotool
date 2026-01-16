# DLQ Retry Consumer

A unified service for automatically retrying transient failures from Dead Letter Queues (DLQs) across all services in the platform.

## Features

- ✅ **Automatic Error Categorization** - Intelligently classifies errors as transient or permanent
- ✅ **Cooling-Off Periods** - Configurable delays between retries (1h, 6h, 24h)
- ✅ **Progressive Retry** - Automatic conversion to permanent after exhausting retries
- ✅ **Multi-Service Support** - Unified consumer handles DLQs from multiple services
- ✅ **Configurable Strategy** - Per-DLQ retry strategies and error patterns
- ✅ **Audit Trail** - Complete history of retry attempts
- ✅ **Production-Ready** - Based on industry best practices from Amazon, Google, Uber

## Quick Start

### 1. Create Configuration

Create `config/dlq-mappings.yaml`:

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
  - name: institution-enhancement
    enabled: true
    dlq_topic: financial-data.institution.upserted.enhancement.dlq
    original_topic: financial-data.institution.upserted
    consumer_group: dlq-retry-consumer-institution-enhancement
    description: "Retries failed institution enhancement messages"
```

### 2. Run Locally

```bash
# Build
cd workflow/go/shared/dlq-retry-consumer
make build

# Set environment variables
export KAFKA_BROKERS=localhost:9094
export DLQ_CONFIG_PATH=config/dlq-mappings.yaml

# Run
./bin/dlq-retry-consumer
```

### 3. Deploy to Kubernetes

```bash
# Create ConfigMap
kubectl create configmap dlq-retry-consumer-config \
  --from-file=dlq-mappings.yaml=config/dlq-mappings.yaml

# Deploy CronJob
kubectl apply -f deployments/k8s/cronjob.yaml

# Verify
kubectl get cronjob dlq-retry-consumer
kubectl get jobs | grep dlq-retry
```

## How It Works

```
Message fails → 3 immediate retries → DLQ
                                      ↓
                            Auto-categorized as:
                         transient OR permanent
                                      ↓
                            DLQ Retry Consumer
                              (runs hourly)
                                      ↓
                           ┌──────────┴──────────┐
                           │                     │
                      Transient              Permanent
                    (auto-retry)           (manual only)
                           │                     │
                  ┌────────┴────────┐           │
                  │                 │           │
            Cooling-off       Republish to      │
            (1h, 6h, 24h)    original topic     │
                  │                 │           │
                  └─────────────────┴───────────┘
                           Success or
                        permanent after
                         3 DLQ retries
```

## Configuration

### Minimal Example

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
    transient_patterns: ["timeout"]
    permanent_patterns: ["validation"]

dlq_mappings:
  - name: my-service
    enabled: true
    dlq_topic: my-service.events.dlq
    original_topic: my-service.events
    consumer_group: dlq-retry-consumer-my-service
```

### Custom Retry Strategy

```yaml
dlq_mappings:
  - name: payment-processor
    enabled: true
    dlq_topic: payments.dlq
    original_topic: payments.transactions
    consumer_group: dlq-retry-consumer-payments
    retry_strategy:
      max_retries: 5  # More retries for critical service
      backoff:
        - retry_count: 0
          delay: 5m     # Fast first retry
        - retry_count: 1
          delay: 30m
        - retry_count: 2
          delay: 2h
        - retry_count: 3
          delay: 6h
        - retry_count: 4
          delay: 24h
```

### Custom Error Patterns

```yaml
dlq_mappings:
  - name: email-service
    enabled: true
    dlq_topic: email.dlq
    original_topic: email.sent
    consumer_group: dlq-retry-consumer-email
    error_categorization:
      transient_patterns:
        - "smtp timeout"
        - "connection failed"
        - "greylisted"
      permanent_patterns:
        - "invalid email"
        - "mailbox does not exist"
```

## Usage

### Inspect DLQ

```bash
# Build inspector
make build-inspector

# Run inspector for specific DLQ
export KAFKA_BROKERS=localhost:9094
export KAFKA_DLQ_TOPIC=financial-data.institution.upserted.enhancement.dlq
./bin/dlq-inspector
```

Output:
```
=== DLQ Analysis Results ===
Total messages in DLQ: 2847
  Retryable: 2156 (75.7%)
  Non-retryable: 691 (24.3%)

=== Error Breakdown ===
1. http_timeout
   Count: 1205 (42.3%)
   Retryable: true
   Example error: context deadline exceeded
   Sample CNPJs: [28195667000106, ...]
```

### Manual Retry

**Option 1: Trigger scheduled job manually**
```bash
# Create job from CronJob
kubectl create job --from=cronjob/dlq-retry-consumer manual-retry-$(date +%s)

# Watch progress
kubectl logs -f job/manual-retry-<timestamp>
```

**Option 2: Run locally**
```bash
# Process DLQ once and exit
./bin/dlq-retry-consumer --run-once
```

**Option 3: neotool CLI (future)**
```bash
# Retry specific message
neotool kafka dlq retry --service institution-enhancement --key 28195667000106

# Retry all permanent errors
neotool kafka dlq retry --service institution-enhancement --category permanent
```

## Monitoring

### Metrics

```
# DLQ depth
kafka_dlq_message_count{topic="financial-data.institution.upserted.enhancement.dlq"}

# Retry success rate
dlq_retry_success_rate{dlq="institution-enhancement"}

# Messages by category
dlq_messages_by_category{dlq="institution-enhancement", category="transient"}
```

### Alerts

```yaml
- alert: DLQDepthHigh
  expr: kafka_dlq_message_count > 1000
  annotations:
    summary: "DLQ {{ $labels.topic }} has {{ $value }} messages"

- alert: DLQRetrySuccessRateLow
  expr: dlq_retry_success_rate < 0.5
  annotations:
    summary: "DLQ {{ $labels.dlq }} retry rate is {{ $value }}"
```

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Service Consumers                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Institution  │  │   Payment    │  │    Email     │      │
│  │  Consumer    │  │  Consumer    │  │  Consumer    │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                 │                 │               │
│         └─────────────────┴─────────────────┘               │
│                           │                                 │
│                    Failed Messages                          │
└───────────────────────────┼─────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       DLQ Topics                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  financial-data.institution.*.dlq                     │  │
│  │  payments.*.dlq                                       │  │
│  │  email.*.dlq                                          │  │
│  └──────────────────────────────────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              DLQ Retry Consumer (This Service)              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 1. Read all configured DLQs                          │  │
│  │ 2. Check error category & cooling-off                │  │
│  │ 3. Republish transient errors to original topics     │  │
│  │ 4. Track retry history & convert to permanent        │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

```
1. Original Message → Consumer → Fails (after 3 retries) → DLQ
   {
     "original_message": {...},
     "error_category": "transient",
     "retry_count": 0,
     "next_retry_after": "2025-12-30T11:00:00Z"
   }

2. DLQ Retry Consumer (1 hour later)
   - Reads DLQ message
   - Checks: category=transient, cooling-off elapsed
   - Republishes original_message to original topic

3a. Success → Message removed from DLQ (offset committed)

3b. Failure → Republish to DLQ with updated metadata
   {
     "original_message": {...},
     "error_category": "transient",
     "retry_count": 1,  // Incremented
     "next_retry_after": "2025-12-30T17:00:00Z"  // +6 hours
   }

4. After 3 DLQ retries → Convert to permanent
   {
     "error_category": "permanent",  // Changed
     "retry_count": 3
   }
```

## Development

### Build

```bash
make build          # Build all binaries
make build-consumer # Build DLQ retry consumer only
make build-inspector # Build DLQ inspector only
```

### Test

```bash
make test                  # Unit tests
make test-integration      # Integration tests (requires Docker)
```

### Run Locally

```bash
# Start dependencies (Kafka, PostgreSQL)
docker compose -f infra/docker/docker-compose.local.yml up -d postgres kafka

# Run consumer
export KAFKA_BROKERS=localhost:9094
export DLQ_CONFIG_PATH=config/dlq-mappings.yaml
./bin/dlq-retry-consumer

# Run inspector
export KAFKA_DLQ_TOPIC=financial-data.institution.upserted.enhancement.dlq
./bin/dlq-inspector
```

## Documentation

- **[Design Document](docs/DESIGN.md)** - Architecture and design decisions
- **[Configuration Reference](docs/CONFIGURATION.md)** - Complete configuration guide
- **[Operations Runbook](docs/RUNBOOK.md)** - Day-to-day operations guide
- **[API Reference](docs/API.md)** - Code-level documentation

## FAQ

### Q: How many times will a message be retried?

**A:** 6 times total:
- 3 immediate retries (by main consumer)
- 3 DLQ retries (by this consumer: 1h, 6h, 24h)

### Q: What happens after 3 DLQ retries?

**A:** Message is marked as "permanent" and requires manual intervention via neotool CLI or fixing the root cause.

### Q: Can I retry permanent errors?

**A:** Yes, via neotool CLI (future) or by fixing the root cause and manually republishing.

### Q: How often does the consumer run?

**A:** Default: Every hour (configurable via CronJob schedule)

### Q: Can I have different retry strategies per DLQ?

**A:** Yes! Each DLQ mapping can override the default retry strategy.

### Q: What if I want to stop automatic retries?

**A:** Set `enabled: false` in the DLQ mapping or suspend the CronJob.

### Q: How do I add a new DLQ?

**A:** Add a new mapping to `config/dlq-mappings.yaml` and redeploy.

### Q: Can I change retry delays without restart?

**A:** Yes, with hot reload (future) or restart the consumer.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.

## License

Proprietary - Invistus Internal Use Only

## Support

- GitHub Issues: [invistus/invistus/issues](https://github.com/invistus/invistus/issues)
- Slack: #platform-engineering
- Email: platform@invistus.com

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes.
