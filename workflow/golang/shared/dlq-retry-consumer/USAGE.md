# DLQ Retry Consumer - Usage Guide

This guide explains how to use the DLQ Retry Consumer in various deployment scenarios.

## Table of Contents

- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [Running with Docker Compose](#running-with-docker-compose)
- [Running Locally](#running-locally)
- [Deployment Options](#deployment-options)
- [Configuration](#configuration)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)

## Quick Start

### 1. Run On-Demand (Docker Compose)

```bash
# From project root
docker compose -f infra/docker/docker-compose.local.yml run --rm dlq-retry-consumer
```

This will:
- Start Kafka if not running
- Process all enabled DLQs
- Exit when complete

### 2. Run on Schedule (Cron-like)

Add to your crontab or scheduler:

```bash
# Every 30 minutes
*/30 * * * * docker compose -f /path/to/infra/docker/docker-compose.local.yml run --rm dlq-retry-consumer >> /var/log/dlq-retry.log 2>&1
```

## How It Works

### Cooling-off Period Implementation

**Question:** How does the consumer "wait" for 1 hour, 6 hours, or 24 hours?

**Answer:** It doesn't wait! The implementation uses **timestamp-based scheduling**:

1. **When a message fails** (in the original service):
   ```
   NextRetryAfter = CurrentTime + BackoffDelay
   Example: 10:00 AM + 1 hour = 11:00 AM
   ```

2. **When the consumer runs** (e.g., every 30 minutes):
   - Reads all messages from DLQ
   - For each message:
     - If `CurrentTime > NextRetryAfter` → Retry now ✓
     - If `CurrentTime < NextRetryAfter` → Skip (will check later)
   - Exits when done

3. **Timeline Example:**
   ```
   10:00 AM - Message fails, NextRetryAfter = 11:00 AM
   10:30 AM - Consumer runs, skips message (not ready)
   11:00 AM - Consumer runs, retries message ✓
   ```

This design is:
- **Stateless** - No state management needed
- **Idempotent** - Safe to run multiple times
- **Efficient** - No blocking/waiting

### Parallel Processing

The consumer processes multiple DLQs **in parallel using Go goroutines**:

```go
// Each DLQ is processed in its own goroutine
for _, dlq := range enabledDLQs {
    go func(dlq) {
        // Process this DLQ
        consumer.ProcessDLQ(ctx)
    }(dlq)
}

// Wait for all DLQs to complete
wg.Wait()
```

**Benefits:**
- Faster processing when multiple DLQs exist
- Better resource utilization
- Shared Kafka connections (thread-safe)

## Running with Docker Compose

### On-Demand Execution

```bash
# From project root
docker compose -f infra/docker/docker-compose.local.yml run --rm dlq-retry-consumer
```

**Output:**
```json
{"level":"INFO","msg":"Starting DLQ Retry Consumer"}
{"level":"INFO","msg":"Configuration loaded","version":"1.0","dlq_mappings":1}
{"level":"INFO","msg":"Starting parallel DLQ processing","total_dlqs":1,"parallelism":1}
{"level":"INFO","msg":"Processing DLQ","dlq":"institution-enhancement","dlq_topic":"financial-data.institution.upserted.enhancement.dlq"}
{"level":"INFO","msg":"Successfully processed DLQ","dlq":"institution-enhancement"}
{"level":"INFO","msg":"DLQ Retry Consumer completed","total_dlqs_processed":1,"total_errors":0}
```

### Build Docker Image

```bash
# Build the image
docker compose -f infra/docker/docker-compose.local.yml build dlq-retry-consumer

# Or build directly
cd workflow/go/shared/dlq-retry-consumer
docker build -t dlq-retry-consumer:latest .
```

### Custom Configuration

Override the default config by mounting a custom YAML file:

```yaml
# docker-compose.override.yml
services:
  dlq-retry-consumer:
    volumes:
      - ./my-custom-dlq-config.yaml:/app/config/dlq-mappings.yaml:ro
```

## Running Locally

### Prerequisites

- Go 1.23+
- Kafka running (localhost:9092)
- DLQ topics created

### Build and Run

```bash
cd workflow/go/shared/dlq-retry-consumer

# Install dependencies
go mod download

# Build
make build-retry-consumer

# Set environment variables
export KAFKA_BROKERS=localhost:9092
export DLQ_CONFIG_PATH=./config/dlq-mappings.yaml

# Run
./bin/dlq-retry-consumer
```

### Run Tests

```bash
# Unit tests
make test

# Integration tests (requires Docker)
make test-integration

# Coverage report
make test-coverage
```

## Deployment Options

### Option 1: Kubernetes CronJob (Recommended)

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: dlq-retry-consumer
spec:
  schedule: "*/30 * * * *"  # Every 30 minutes
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: dlq-retry-consumer
            image: dlq-retry-consumer:latest
            env:
            - name: KAFKA_BROKERS
              value: "kafka:9092"
            - name: DLQ_CONFIG_PATH
              value: "/app/config/dlq-mappings.yaml"
            volumeMounts:
            - name: config
              mountPath: /app/config
          volumes:
          - name: config
            configMap:
              name: dlq-mappings
          restartPolicy: OnFailure
```

### Option 2: System Cron

```bash
# Add to crontab
*/30 * * * * /usr/local/bin/dlq-retry-consumer >> /var/log/dlq-retry.log 2>&1
```

### Option 3: Docker Compose with External Scheduler

Use an external scheduler (like Airflow, Prefect, or Jenkins) to trigger:

```bash
docker compose -f infra/docker/docker-compose.local.yml run --rm dlq-retry-consumer
```

### Option 4: Continuous Daemon (Not Recommended)

While you can run it as a daemon with sleep loops, this is **not recommended** because:
- Wastes resources during sleep
- Harder to monitor
- Less flexible scheduling

Use CronJob or external scheduler instead.

## Configuration

### Main Configuration File

Edit `config/dlq-mappings.yaml`:

```yaml
version: "1.0"

defaults:
  retry_strategy:
    max_retries: 3
    backoff:
      - retry_count: 0
        delay: 1h     # First retry after 1 hour
      - retry_count: 1
        delay: 6h     # Second retry after 6 hours
      - retry_count: 2
        delay: 24h    # Third retry after 24 hours

  error_categorization:
    transient_patterns:
      - "timeout"
      - "connection refused"
      - "503"
      - "429"
    permanent_patterns:
      - "validation"
      - "404"
      - "invalid"

dlq_mappings:
  - name: institution-enhancement
    enabled: true
    dlq_topic: financial-data.institution.upserted.enhancement.dlq
    original_topic: financial-data.institution.upserted
    consumer_group: dlq-retry-consumer-institution-enhancement
    description: "Retries failed institution enhancement messages"

  # Add more DLQ mappings here
  - name: another-service
    enabled: false  # Disabled for now
    dlq_topic: another-service.dlq
    original_topic: another-service.events
    consumer_group: dlq-retry-consumer-another
```

### Per-DLQ Custom Retry Strategy

Override default strategy for specific DLQs:

```yaml
dlq_mappings:
  - name: high-priority-service
    enabled: true
    dlq_topic: high-priority.dlq
    original_topic: high-priority.events
    consumer_group: dlq-retry-consumer-high-priority
    # Custom retry strategy - faster retries
    retry_strategy:
      max_retries: 2
      backoff:
        - retry_count: 0
          delay: 15m   # Retry after 15 minutes
        - retry_count: 1
          delay: 1h    # Retry after 1 hour
```

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `KAFKA_BROKERS` | Yes | - | Kafka broker addresses (comma-separated) |
| `DLQ_CONFIG_PATH` | Yes | - | Path to dlq-mappings.yaml |
| `LOG_LEVEL` | No | `info` | Log level (debug, info, warn, error) |

## Monitoring

### Logs

The consumer outputs structured JSON logs:

```json
{"level":"INFO","msg":"Processing DLQ message","key":"12345678000190","error_category":"transient","retry_count":1,"age":"2h30m"}
{"level":"INFO","msg":"Republishing to original topic","key":"12345678000190","original_topic":"financial-data.institution.upserted"}
{"level":"WARN","msg":"Message exceeded max retries, converting to permanent","key":"98765432000199","retry_count":3}
```

### Metrics to Monitor

1. **Total DLQs processed**: Number of DLQs scanned
2. **Messages retried**: Count of messages sent back to original topic
3. **Messages skipped**: Not ready for retry (cooling-off)
4. **Messages converted to permanent**: Exceeded max retries
5. **Processing duration**: Time to process all DLQs
6. **Error rate**: Failed DLQ processing

### Alerting

Set up alerts for:
- High number of permanent errors (indicates systemic issue)
- Low retry success rate (messages keep failing)
- Processing failures (consumer errors)

## Troubleshooting

### No messages being retried

**Check:**
1. Are messages ready for retry?
   ```bash
   # Inspect DLQ messages
   docker compose -f infra/docker/docker-compose.local.yml run --rm institution-enhancement-dlq-inspector
   ```

2. Is the DLQ enabled in config?
   ```yaml
   dlq_mappings:
     - name: institution-enhancement
       enabled: true  # ← Check this
   ```

3. Are messages categorized correctly?
   - Permanent errors won't retry
   - Check error categorization patterns

### Messages keep failing

**Possible causes:**
1. Underlying issue not fixed (external API still down)
2. Message is actually permanently broken
3. Need to adjust error categorization

**Actions:**
- Review error logs from original service
- Manually inspect failing messages
- Consider converting to permanent for manual review

### Consumer crashes

**Check:**
1. Kafka connection
   ```bash
   # Test Kafka connectivity
   docker exec neotool-kafka kafka-topics.sh --list --bootstrap-server localhost:9092
   ```

2. Configuration syntax
   ```bash
   # Validate YAML
   yamllint config/dlq-mappings.yaml
   ```

3. Resource limits (memory, connections)

### High DLQ depth

**Meaning:** Many messages in DLQ not being retried

**Possible causes:**
- Messages not ready (cooling-off period)
- All messages are permanent errors
- Consumer not running frequently enough

**Actions:**
- Increase consumer run frequency
- Review message categories
- Check if original service is healthy

## Best Practices

1. **Run Frequently**: Every 15-30 minutes in production
2. **Monitor Permanent Errors**: Set up alerts for permanent error increases
3. **Tune Backoff Delays**: Adjust based on your service characteristics
4. **Review Error Patterns**: Periodically update transient/permanent patterns
5. **Test Configuration**: Always test config changes in dev environment first
6. **Set Resource Limits**: In K8s, set appropriate CPU/memory limits
7. **Use Structured Logging**: Makes debugging much easier
8. **Document Custom Strategies**: When using per-DLQ overrides

## Example Workflows

### Adding a New DLQ

1. Update `config/dlq-mappings.yaml`:
   ```yaml
   dlq_mappings:
     - name: new-service
       enabled: true
       dlq_topic: new-service.dlq
       original_topic: new-service.events
       consumer_group: dlq-retry-consumer-new-service
   ```

2. Test in development:
   ```bash
   docker compose -f infra/docker/docker-compose.local.yml run --rm dlq-retry-consumer
   ```

3. Deploy to production (K8s ConfigMap update)

### Emergency: Stop All Retries

Set all DLQs to `enabled: false` in config:

```yaml
dlq_mappings:
  - name: institution-enhancement
    enabled: false  # ← Disable
```

Or stop the CronJob:
```bash
kubectl patch cronjob dlq-retry-consumer -p '{"spec":{"suspend":true}}'
```

### Manual Retry of Permanent Errors

Use neotool CLI (future implementation):
```bash
neotool kafka retry-dlq \
  --dlq-topic financial-data.institution.upserted.enhancement.dlq \
  --original-topic financial-data.institution.upserted \
  --filter "key=12345678000190"
```

## Performance

### Parallel Processing

The consumer uses goroutines to process multiple DLQs in parallel:

- **1 DLQ**: ~5-10 seconds
- **5 DLQs**: ~8-15 seconds (parallel)
- **10 DLQs**: ~12-20 seconds (parallel)

Actual time depends on:
- Number of messages per DLQ
- Message size
- Network latency to Kafka

### Resource Usage

Typical resource consumption:
- **CPU**: 0.1-0.5 cores during execution
- **Memory**: 50-100 MB
- **Network**: Depends on message size and count

### Scaling

For very large DLQ volumes:
1. Increase consumer run frequency
2. Add more partitions to DLQ topics
3. Consider splitting into multiple consumer instances (different DLQ groups)
