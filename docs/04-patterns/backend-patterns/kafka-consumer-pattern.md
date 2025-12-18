---
title: Kafka Consumer Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [pattern, backend, kafka, consumer, batch]
ai_optimized: true
search_keywords: [kafka, consumer, pattern, batch, retry, dlq]
related:
  - 05-standards/workflow-standards/batch-workflow-standard.md
  - 07-examples/batch-workflows/swapi-etl-workflow.md
  - 08-templates/code/kafka-consumer-template.kt
---

# Kafka Consumer Pattern

> **Purpose**: Standard pattern for implementing Kafka consumers in Kotlin/Micronaut for batch processing workflows.

## Overview

This pattern defines the structure and best practices for implementing Kafka consumers that process messages from batch ETL jobs. It includes retry logic, DLQ handling, metrics, and observability.

## Architecture

```
┌─────────────┐
│   Kafka     │
│   Topic     │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│  Consumer       │
│  (@KafkaListener)│
└──────┬──────────┘
       │ submits work
       ▼
┌─────────────────────┐
│  Virtual Thread Pool│
│  (ProcessingExecutor)│
└──────┬──────────────┘
       │
       ▼
┌─────────────────┐
│ Retry + DLQ     │
│ (blocking code  │
│  on virtual     │
│  threads)       │
└──────┬──────────┘
       │
       ├─── Success ───► Processor ───► Pending Commit Queue ──► Commit Offset (listener thread)
       │
       └─── Failure ───► DLQ Publisher ───► Pending Commit Queue ──► Commit Offset
```

**Key Design Principles:**
- Uses virtual threads (Project Loom) to run business logic outside the Kafka listener thread
- Listener hands work to `ProcessingExecutor` immediately and returns to polling, keeping heartbeats healthy
- Sequential processing per partition enforced by per-partition locks before dispatching to the executor
- Retry logic remains simple blocking code (`Thread.sleep`) because it now runs on virtual threads
- Commits still occur on the listener thread via a pending-commit queue to keep the Kafka consumer thread-safe
- DLQ publishing and metrics happen inside the worker tasks; results are marshalled back to the listener

## Implementation Structure

### Package Organization

```
service/kotlin/<module>/batch/<domain>/
├── <Domain>Consumer.kt          # Main consumer with @KafkaListener
├── <Domain>Message.kt           # Data classes matching Kafka schema
├── <Domain>Processor.kt          # Business logic processor
├── DlqPublisher.kt               # DLQ message publisher
├── metrics/
│   └── <Domain>Metrics.kt       # Micrometer metrics
└── config/
    └── KafkaConsumerConfig.kt    # Consumer configuration
```

### Key Components

1. **Consumer** (`<Domain>Consumer.kt`):
   - `@KafkaListener` annotation
   - Extends `AbstractKafkaConsumer` which hands work to the shared `ProcessingExecutor`
   - Regular `fun receive()` that submits work to the executor and immediately returns to Kafka
   - Manual commit after successful processing via the pending-commit queue
   - Retry logic with exponential backoff using `Thread.sleep()` (virtual threads park efficiently)
   - DLQ publishing for poison messages
   - Error handling and logging
   - Sequential processing per partition
   - Graceful shutdown waits for executor tasks to complete before closing

2. **Message Models** (`<Domain>Message.kt`):
   - Data classes matching Kafka schema
   - JSON serialization/deserialization
   - Validation helpers

3. **Processor** (`<Domain>Processor.kt`):
   - Business logic implementation
   - Validation
   - Error handling
   - Metrics recording

4. **DLQ Publisher** (`DlqPublisher.kt`):
   - Publishes failed messages to DLQ topic
   - Includes original message + error metadata
   - Error handling for DLQ publishing itself

5. **Metrics** (`metrics/<Domain>Metrics.kt`):
   - Processed count
   - Processing duration
   - DLQ count
   - Retry count
   - Error count
6. **Processing Executor** (`ProcessingExecutor.kt`):
   - Singleton virtual-thread executor
   - Provides `submit` method that returns `CompletableFuture`
   - Shut down gracefully during application stop to drain in-flight work

## Code Pattern

### Consumer Implementation

Consumers should extend `AbstractKafkaConsumer` which provides virtual thread-based processing with retry logic, DLQ handling, and metrics. The base class handles all the complexity:

```kotlin
@Singleton
@KafkaListener(
    groupId = "<domain>-consumer-group",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "false"),
        Property(name = "max.poll.records", value = "100"),
        Property(name = "session.timeout.ms", value = "30000"),
        Property(name = "heartbeat.interval.ms", value = "10000")
    ]
)
class DomainConsumer(
    @Inject processor: DomainProcessor,
    @Inject dlqPublisher: DomainDlqPublisher,
    @Inject metrics: DomainMetrics,
    @Inject config: ConsumerConfig
) : AbstractKafkaConsumer<DomainMessage, DomainProcessor, DomainDlqPublisher, DomainMetrics>(
    processor,
    dlqPublisher,
    metrics,
    config
) {
    override fun getTopicName(): String {
        return "<domain>.entity.v1"
    }
    
    @Topic("<domain>.entity.v1")
    fun receive(
        @KafkaKey key: String,
        message: DomainMessage,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long
    ) {
        super.receive(key, message, consumer, partition, offset)
    }
}
```

**Key Points:**
- Extends `AbstractKafkaConsumer` which provides virtual thread-based processing
- `receive()` is a regular function (no suspend needed - virtual threads handle blocking efficiently)
- All retry logic, DLQ handling, and offset commits are handled by the base class
- Sequential processing per partition is guaranteed
- Uses virtual threads for efficient blocking I/O (configured in `application.yml`)

### Processor Implementation

```kotlin
@Singleton
class DomainProcessor(
    private val metrics: DomainMetrics
) {
    private val logger = KotlinLogging.logger {}
    
    fun process(message: DomainMessage) {
        val startTime = System.currentTimeMillis()
        
        try {
            validate(message)
            // Business logic here
            val duration = System.currentTimeMillis() - startTime
            metrics.recordProcessingDuration(duration.toDouble())
            metrics.incrementProcessed()
        } catch (e: ValidationException) {
            metrics.incrementError()
            throw ProcessingException("Validation failed: ${e.message}", e)
        } catch (e: Exception) {
            metrics.incrementError()
            throw ProcessingException("Processing failed: ${e.message}", e)
        }
    }
    
    private fun validate(message: DomainMessage) {
        // Validation logic
    }
}
```

## Configuration

### application.yml

```yaml
micronaut:
  executors:
    consumer:
      type: virtual  # Use virtual threads for Kafka consumers (requires JDK 21+)

kafka:
  bootstrap:
    servers: ${KAFKA_BROKERS}
  consumers:
    <domain>-entity:
      group-id: <domain>-entity-consumer-group
      enable-auto-commit: false
      auto-offset-reset: earliest
      max-poll-records: 100
      session-timeout-ms: 30000
      heartbeat-interval-ms: 10000
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.micronaut.serde.kafka.KafkaSerdeDeserializer
```

**Virtual Threads Configuration:**
- `micronaut.executors.consumer.type: virtual` enables virtual threads for Kafka consumers
- Requires JDK 21+ and Micronaut 4+
- Virtual threads make blocking operations (Thread.sleep, blocking I/O) efficient
- No need for suspend functions or coroutines - simple blocking code works efficiently

## Retry Strategy

### Retry Configuration

Configured via `ConsumerConfig`:
- **Max Retries**: 3 attempts (configurable via `batch.consumer.max-retries`)
- **Initial Delay**: 1 second (configurable via `batch.consumer.initial-retry-delay-ms`)
- **Max Delay**: 10 seconds (configurable via `batch.consumer.max-retry-delay-ms`)
- **Backoff**: Exponential (configurable via `batch.consumer.retry-backoff-multiplier`)

### Retry Behavior

1. **Transient Errors**: Retry with exponential backoff using `Thread.sleep()` (virtual thread parks efficiently)
2. **Validation Errors**: No retry, send to DLQ immediately
3. **Persistent Errors**: Retry up to max, then send to DLQ

### Implementation Details

- Uses virtual threads (Project Loom) for efficient blocking operations
- Retry delays use `Thread.sleep()` - virtual threads park instead of blocking platform threads
- Processing happens sequentially per partition
- Offset commits occur on the listener thread after successful processing/DLQ publish
- Virtual threads make blocking I/O operations cheap and scalable

### Memory Management

The `AbstractKafkaConsumer` tracks assigned partitions, active consumers, and in-flight work to coordinate shutdown and prevent memory leaks.

**Partition Assignment Tracking:**
- `assignedPartitions` set tracks all partitions that have been assigned (persists across processing cycles)
- Used during shutdown to pause all assigned partitions, even when idle
- Updated when messages are received (indicating partition assignment)
- Separate from active processing tracking to handle shutdown when consumer is idle

**Partition Lifecycle Tracking:**
- `activeConsumers` map tracks consumer instances per partition for shutdown coordination
- Entries are automatically cleaned up in `finally` block after processing completes (success or failure)
- This prevents memory leaks from unbounded map growth and stale/closed consumer references
- The map only contains partitions with currently active processing (not all assigned partitions)

**In-Flight Work Tracking:**
- `inFlightWork` map tracks count of messages currently being processed per partition
- Used during graceful shutdown to wait for all in-flight messages to complete
- Counter is incremented at start of `receive()`, decremented in `finally` block
- Entries are removed when counter reaches zero to keep map clean

## DLQ Strategy

### DLQ Topic Naming

- Pattern: `<domain>.<entity>.dlq`
- Example: `swapi.people.dlq`

### DLQ Message Structure

```kotlin
data class DlqMessage(
    val originalMessage: DomainMessage,
    val errorMessage: String,
    val errorType: String,
    val failedAt: String,
    val retryCount: Int,
    val stackTrace: String?
)
```

### DLQ Publishing

- Always include original message
- Include error metadata
- Include retry count
- Include stack trace for debugging

## Graceful Shutdown

The consumer implements graceful shutdown with draining to ensure in-flight work completes before closing.

### Shutdown Process

1. **Set Shutdown Flag**: Prevents new messages from being accepted
2. **Pause All Partitions**: Stops receiving new messages from Kafka
3. **Drain In-Flight Work**: Waits for all currently processing messages to complete
4. **Close**: Application context closes after draining completes

### Draining Mechanism

**Problem**: Previously, shutdown just set a flag and paused partitions, but didn't wait for in-flight messages to complete. If the app stopped while a record was mid-retry, the consumer could exit without committing, causing duplicates or unprocessed records. Additionally, `drainInFlightWork()` just polls counters while sleeping; it cannot actually interrupt the virtual thread that is sleeping inside `processWithRetry`, so if a retry is in the middle of a long backoff, shutdown will sit in this loop until the sleep completes or the timeout expires.

**Solution**: 
- Track in-flight work per partition, pause all assigned partitions (not just active ones), then wait for all in-flight messages to complete (with configurable timeout)
- `processWithRetry` now checks the shutdown flag during retry loops, allowing in-flight work to abort quickly when shutdown starts
- This ensures graceful draining before close, with early abort capability

**Configuration:**
- `batch.consumer.shutdown-timeout-seconds`: Maximum time to wait for in-flight work (default: 30 seconds)
- If timeout is reached, shutdown proceeds anyway (messages may be reprocessed by Kafka)
- Progress is logged periodically during draining
- Note: In-flight threads may still be running after timeout and may attempt to commit against a closing consumer

**Implementation:**
- In-flight work is tracked using `ConcurrentHashMap<Int, AtomicInteger>` per partition
- Counter incremented at start of `receive()`, decremented in `finally` block
- Shutdown waits until all counters reach zero or timeout expires
- `processWithRetry` checks `isShuttingDown` flag before and after retry delays to allow early abort

## max.poll.interval.ms Limitations

The consumer attempts to mitigate `max.poll.interval.ms` violations, but has important limitations.

### Problem

Long retry loops with `Thread.sleep()` can exceed `max.poll.interval.ms`, causing Kafka to think the consumer is dead and trigger rebalances. Even with virtual threads, the listener thread is blocked until `receive()` returns, preventing heartbeats.

### Important Limitations

**Pause/Resume Does NOT Solve Heartbeat Problem:**
- Pausing a partition only stops fetching new records; it does NOT keep the consumer in the group
- The listener thread still sits inside `receive()` until `processWithRetry` returns
- Kafka doesn't send heartbeats while `poll()` is blocked, regardless of partition pause state
- Long sleeps in `Thread.sleep()` can still exceed `max.poll.interval.ms`

**Current Approach:**
- Partition is only paused when actually retrying (not on first attempt/happy path)
- Pause is for backpressure (preventing new fetches during retries), not heartbeat protection
- Shutdown flag is checked during retry loops to allow early abort during graceful shutdown
- Accepts that very long retry cycles may cause rebalances (tune `maxRetries`/delays accordingly)

**To Truly Avoid Rebalances:**
- Hand work off to another thread/coroutine (but this breaks sequential per-partition processing)
- Call `consumer.wakeup()/poll()` periodically (complex, requires coordination)
- Tune retry configuration to keep total retry time well below `max.poll.interval.ms`

**Configuration Recommendations:**
- Ensure `max.poll.interval.ms` is set appropriately for your retry strategy
- Consider: `max.poll.interval.ms >= (maxRetries * maxRetryDelayMs) + processingTime + buffer`
- Default Kafka `max.poll.interval.ms` is 5 minutes (300000ms)
- With default retry config (3 retries, max 10s delay), total retry time can be ~20-30s, well within default
- For longer retry cycles, increase `max.poll.interval.ms` or reduce retry delays

## Backpressure and Poison Messages

The consumer processes messages sequentially per partition, which means poison messages can block partition processing.

### Behavior

**Poison Message Impact:**
- A single poison message (one that always fails) will block processing for its partition
- Processing is blocked until all retries are exhausted and the message is sent to DLQ
- Virtual threads make `Thread.sleep()` cheap, but they don't solve starvation
- Newer messages in the same partition can't progress until the poison message completes its retry cycle

**Why This Design:**
- Sequential per-partition processing ensures at-least-once delivery semantics
- Maintains message ordering within partitions
- Simple, predictable behavior

### Mitigation Strategies

1. **Tune Retry Configuration:**
   - Reduce `maxRetries` to fail faster for poison messages
   - Reduce `maxRetryDelayMs` to shorten retry cycles
   - Consider: `maxRetries: 1` for faster DLQ routing

2. **DLQ Routing:**
   - Quickly move poison messages to DLQ to unblock partition
   - Monitor DLQ for patterns indicating systemic issues

3. **Monitoring and Alerting:**
   - Monitor partition lag metrics
   - Alert on stuck partitions (no progress for extended period)
   - Track retry counts and DLQ rates

4. **High-Volume Scenarios:**
   - For high-volume scenarios, consider per-partition queues with limited concurrency
   - This allows skipping or DLQ routing of poison messages sooner
   - Document the behavior and provide metrics to alert on stuck partitions

5. **Configuration Tuning:**
   - Consider limiting total retry duration
   - Add configuration to cap maximum time spent on a single message
   - Balance between resilience and throughput

### Metrics to Monitor

- Partition lag (messages waiting to be processed)
- Processing duration per message
- Retry counts (high retry counts may indicate poison messages)
- DLQ rates (sudden spikes may indicate systemic issues)
- In-flight work counts (during normal operation and shutdown)

## Metrics

### Required Metrics

- `{domain}.{entity}.processed` - Counter
- `{domain}.{entity}.processing.duration` - Timer
- `{domain}.{entity}.dlq.count` - Counter
- `{domain}.{entity}.retry.count` - Counter
- `{domain}.{entity}.error.count` - Counter

### Metrics Implementation

```kotlin
@Singleton
class DomainMetrics(
    private val meterRegistry: MeterRegistry
) {
    private val processedCounter = Counter.builder("domain.entity.processed")
        .register(meterRegistry)
    
    private val processingTimer = Timer.builder("domain.entity.processing.duration")
        .register(meterRegistry)
    
    // ... other metrics
}
```

## Testing

### Unit Tests

- Test successful processing
- Test retry scenarios
- Test DLQ publishing
- Test validation errors
- Mock Kafka consumer

### Integration Tests

- Use Testcontainers Kafka
- End-to-end message processing
- DLQ flow testing
- Consumer group testing

## Best Practices

1. **Manual Commit**: Always use manual commit for control
2. **Idempotency**: Ensure processing is idempotent
3. **Error Handling**: Comprehensive error handling at all levels
4. **Observability**: Logging and metrics for all operations
5. **Validation**: Validate messages before processing
6. **DLQ**: Always have DLQ for poison messages
7. **Retries**: Retry transient errors, not validation errors
8. **Metrics**: Expose all key metrics for monitoring

## Known Limitations

### max.poll.interval.ms Risk

**Issue**: Long retry loops can exceed `max.poll.interval.ms`, causing Kafka to think the consumer is dead and trigger rebalances. Even with virtual threads, the listener thread is blocked until `receive()` returns, preventing heartbeats.

**Current Mitigation**: 
- Partition pausing during retries (for backpressure, not heartbeat protection)
- Shutdown flag checks during retry loops
- Configuration recommendations to tune retry timing

**Limitation**: Pausing partitions does NOT solve the heartbeat problem - the listener thread is still blocked. To truly avoid rebalances, you must either:
- Tune retry configuration to keep total retry time well below `max.poll.interval.ms`
- Accept that very long retry cycles may cause rebalances
- Use alternative architectures (e.g., hand work off to separate thread, but this breaks sequential processing)

**Recommendation**: Ensure `max.poll.interval.ms >= (maxRetries * maxRetryDelayMs) + processingTime + buffer`. Default is 5 minutes (300000ms), which is usually sufficient for default retry config.

### Poison Message Blocking

**Issue**: Single poison messages block entire partition processing until all retries are exhausted.

**Impact**: 
- Newer messages in the same partition cannot progress
- Partition lag increases
- Throughput degrades for affected partitions

**Mitigation**:
- Tune retry configuration (reduce `maxRetries` and `maxRetryDelayMs`)
- Monitor partition lag and alert on stuck partitions
- Route poison messages to DLQ quickly
- Consider per-partition queues with limited concurrency for high-volume scenarios

### Commit Queue Race Condition

**Issue**: `processPendingCommits()` is called at the start of `receive()`. If no new messages arrive, commits may be delayed.

**Impact**: 
- Commits may not happen immediately after processing completes
- In rare cases, commits could be delayed until next message arrives

**Mitigation**: 
- Commits are queued and processed on next message arrival
- For low-throughput scenarios, consider periodic commit flushing
- Current implementation is acceptable for most use cases

### Shutdown Timeout Behavior

**Issue**: If shutdown timeout expires, in-flight threads may still be running and may attempt commits against a closing consumer.

**Impact**:
- Commits may fail silently
- Messages may be reprocessed by Kafka
- No guarantee of exactly-once semantics during shutdown

**Mitigation**:
- Set appropriate `shutdown-timeout-seconds` based on processing time
- Monitor shutdown duration metrics
- Accept that some messages may be reprocessed during shutdown

### DLQ Fallback Not Implemented

**Issue**: `handleDlqFallback()` is a TODO - no actual fallback storage when DLQ publishing fails.

**Impact**:
- If DLQ publishing fails after retries, message is lost
- No alternative storage mechanism

**Recommendation**: Implement fallback storage (local file, database, or S3) for critical messages.

### No Circuit Breaker

**Issue**: No protection against cascading failures when downstream systems are unavailable.

**Impact**:
- Retries continue even when downstream is completely down
- Wastes resources and increases latency

**Recommendation**: Implement circuit breaker pattern to fail fast when downstream is unavailable.

## Common Pitfalls

1. **Auto-commit Enabled**: Disable auto-commit for manual control
2. **No DLQ**: Always implement DLQ for failed messages
3. **Infinite Retries**: Set max retries to prevent infinite loops
4. **No Metrics**: Always expose metrics for observability
5. **Poor Error Handling**: Handle all exception types appropriately
6. **Memory Leaks from Partition Tracking**: Not cleaning up `activeConsumers` entries causes unbounded map growth and memory leaks. Always clean up in `finally` blocks.
7. **Shutdown Without Draining**: Shutting down without waiting for in-flight work can cause message loss or duplicates. Always implement graceful shutdown with draining.
8. **Exceeding max.poll.interval.ms**: Long retry loops can exceed poll interval, causing rebalances. Pausing partitions does NOT solve this - the listener thread is still blocked. Tune retry config to keep total retry time well below `max.poll.interval.ms`, or accept that very long retries may cause rebalances.
9. **Poison Messages Blocking Partitions**: Single poison messages can block partition processing. Tune retry config, monitor lag, and route to DLQ quickly.
10. **Pausing Partitions Unnecessarily**: Pausing every partition before any attempt and only resuming after completion prevents pipelining and adds unnecessary protocol calls. Only pause when actually retrying, not on the happy path.
11. **Shutdown When Idle**: If shutdown starts while consumer is idle, `activeConsumers` is empty, so no partitions get paused even though they remain assigned. Track assigned partitions separately from active processing.
12. **Commit Retry Logic**: Only retries commit once - may need more resilience for unreliable networks
13. **No Idempotency Mechanism**: While `record_id` exists, no explicit idempotency check in processor - relies on business logic
14. **Limited Backpressure**: Only pauses partitions during retries, not based on queue depth or downstream health

## Missing Features & Implementation Recommendations

### High Priority

1. **DLQ Fallback Implementation**: Implement `handleDlqFallback()` to store messages when DLQ publishing fails
   - Options: Local file, database table, or S3 bucket
   - Should include same metadata as DLQ messages
   - Should be queryable for manual replay

2. **Health Check Endpoint**: Expose consumer health status
   - Track consumer group membership
   - Monitor partition assignments
   - Check for stuck partitions
   - Verify DLQ connectivity

3. **Partition Lag Metrics**: Track consumer lag per partition
   - Use Kafka consumer metrics API
   - Alert on high lag thresholds
   - Monitor lag trends over time

4. **Improved Commit Reliability**: More robust commit retry logic
   - Multiple retry attempts with exponential backoff
   - Separate metrics for commit failures
   - Circuit breaker for commit failures

5. **Processing Timeout**: Per-message processing timeout
   - Prevent stuck processing
   - Configurable timeout per message
   - Send to DLQ if timeout exceeded

### Medium Priority

1. **Circuit Breaker Pattern**: Protect against cascading failures
   - Fail fast when downstream is unavailable
   - Configurable failure thresholds
   - Automatic recovery when downstream recovers

2. **DLQ Replay Mechanism**: Ability to replay DLQ messages
   - Query DLQ topic
   - Replay selected messages
   - Support for bulk replay

3. **Better Shutdown Handling**: More graceful handling of in-flight work
   - Interrupt virtual threads more gracefully
   - Better coordination between shutdown and commits
   - Metrics for shutdown duration

4. **Rate Limiting**: Prevent overwhelming downstream systems
   - Per-partition rate limits
   - Global rate limits
   - Configurable throttling

### Low Priority

1. **Batch Processing**: Process multiple messages in single transaction
   - Improve throughput for high-volume scenarios
   - Atomic processing of message batches
   - Rollback on batch failure

2. **Schema Registry Integration**: Avro/Protobuf schema validation
   - Validate message schemas
   - Schema evolution support
   - Schema compatibility checking

3. **Distributed Tracing**: OpenTelemetry integration
   - Trace message processing across services
   - Correlate logs and metrics
   - Performance analysis

## Operational Runbook

### Troubleshooting Common Issues

#### Consumer Not Processing Messages

**Symptoms**: No messages being processed, consumer group shows no activity

**Diagnosis**:
1. Check consumer group status: `./neotool kafka --consumer-group <group-name>`
2. Verify topic has messages: `./neotool kafka --topic <topic-name>`
3. Check application logs for errors
4. Verify consumer is running and healthy

**Solutions**:
- Restart consumer application
- Check Kafka connectivity
- Verify consumer group configuration
- Check for rebalance issues

#### High Partition Lag

**Symptoms**: Consumer lag increasing, messages not being processed fast enough

**Diagnosis**:
1. Check partition lag metrics
2. Monitor processing duration
3. Check for stuck partitions
4. Review retry counts

**Solutions**:
- Scale out consumers (increase consumer instances)
- Optimize processing logic
- Reduce retry delays
- Check for poison messages blocking partitions

#### Frequent Rebalances

**Symptoms**: Consumer group frequently rebalancing, processing interrupted

**Diagnosis**:
1. Check `max.poll.interval.ms` configuration
2. Monitor processing duration
3. Check for long retry loops
4. Review session timeout settings

**Solutions**:
- Increase `max.poll.interval.ms`
- Reduce retry delays
- Optimize processing time
- Check for network issues

#### DLQ Publishing Failures

**Symptoms**: Messages not appearing in DLQ, DLQ publish failure metrics increasing

**Diagnosis**:
1. Check DLQ topic exists and is accessible
2. Verify DLQ publisher configuration
3. Check Kafka producer errors
4. Review DLQ retry configuration

**Solutions**:
- Verify DLQ topic configuration
- Check Kafka producer connectivity
- Increase DLQ retry attempts
- Implement DLQ fallback mechanism

#### Shutdown Timeouts

**Symptoms**: Application shutdown taking too long, timeout warnings in logs

**Diagnosis**:
1. Check `shutdown-timeout-seconds` configuration
2. Monitor in-flight work during shutdown
3. Review processing duration
4. Check for stuck processing

**Solutions**:
- Increase `shutdown-timeout-seconds`
- Optimize processing time
- Check for long retry loops
- Monitor shutdown metrics

### Performance Tuning

#### High-Throughput Scenarios

**Recommendations**:
1. **Increase `max.poll.records`**: Fetch more messages per poll (default: 100)
2. **Optimize Processing**: Minimize processing time per message
3. **Scale Out**: Add more consumer instances
4. **Partition Count**: Ensure topic has enough partitions for parallelism
5. **Batch Processing**: Consider processing multiple messages in single transaction

#### Low-Latency Scenarios

**Recommendations**:
1. **Reduce Retry Delays**: Minimize retry backoff times
2. **Reduce `max.poll.records`**: Fetch fewer messages per poll
3. **Optimize Processing**: Minimize processing time
4. **Direct Processing**: Avoid unnecessary queuing

#### Resource-Constrained Environments

**Recommendations**:
1. **Reduce `max.poll.records`**: Lower memory usage
2. **Reduce Retry Counts**: Fail faster to free resources
3. **Monitor Memory**: Track virtual thread count and memory usage
4. **Tune JVM**: Optimize heap size and GC settings

## Monitoring Checklist

### Required Metrics to Monitor

1. **Processing Metrics**:
   - `{domain}.{entity}.processed` - Processing rate
   - `{domain}.{entity}.processing.duration` - Processing latency
   - `{domain}.{entity}.error.count` - Error rate

2. **Retry Metrics**:
   - `{domain}.{entity}.retry.count` - Retry frequency
   - Retry rate trends

3. **DLQ Metrics**:
   - `{domain}.{entity}.dlq.count` - DLQ rate
   - `{domain}.{entity}.dlq.publish.failure` - DLQ publish failures
   - DLQ topic message count

4. **Consumer Metrics**:
   - Partition lag per partition
   - Consumer group membership
   - Partition assignments
   - Commit failures

5. **Infrastructure Metrics**:
   - Virtual thread count
   - Memory usage
   - CPU usage
   - Network I/O

### Alerting Thresholds

1. **High Error Rate**: Alert when error rate exceeds 5% of processing rate
2. **High DLQ Rate**: Alert when DLQ rate exceeds 1% of processing rate
3. **High Partition Lag**: Alert when lag exceeds 10,000 messages per partition
4. **Stuck Partitions**: Alert when partition lag increases without processing
5. **DLQ Publish Failures**: Alert when DLQ publish failure rate exceeds 10%
6. **Frequent Rebalances**: Alert when rebalances occur more than once per hour
7. **Shutdown Timeouts**: Alert when shutdown exceeds configured timeout

### Health Check Indicators

1. **Consumer Health**:
   - Consumer group membership active
   - Partitions assigned and processing
   - No frequent rebalances
   - Processing rate stable

2. **Processing Health**:
   - Error rate within acceptable range
   - Processing duration stable
   - Retry rate within expected range
   - No stuck partitions

3. **DLQ Health**:
   - DLQ rate within acceptable range
   - DLQ publishing successful
   - DLQ topic accessible
   - DLQ message count monitored

## Related Patterns

- [Batch Workflow Standard](../05-standards/workflow-standards/batch-workflow-standard.md)
- [Service Pattern](./service-pattern.md)
- [Repository Pattern](./repository-pattern.md)
- [Kafka Operations Guide](./kafka-operations-guide.md)
- [Kafka Monitoring Guide](./kafka-monitoring-guide.md)

## Examples

- [SWAPI People Consumer](../../../service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/app/batch/swapi/PeopleConsumer.kt)
- [SWAPI ETL Workflow](../../07-examples/batch-workflows/swapi-etl-workflow.md)
