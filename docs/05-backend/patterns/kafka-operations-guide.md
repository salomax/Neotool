---
title: Kafka Operations Guide
type: guide
category: backend
status: current
version: 1.0.0
tags: [guide, backend, kafka, operations, troubleshooting]
ai_optimized: true
search_keywords: [kafka, operations, troubleshooting, topic management, consumer groups, setup]
related:
  - 04-patterns/backend-patterns/kafka-consumer-pattern.md
  - 04-patterns/backend-patterns/kafka-monitoring-guide.md
---

# Kafka Operations Guide

> **Purpose**: Operational guide for managing Kafka infrastructure, topics, consumer groups, and troubleshooting common issues in the NeoTool project.

## Overview

This guide provides practical instructions for operating Kafka in the NeoTool project, including setup, topic management, consumer group operations, and troubleshooting.

## Local Development Setup

### Starting Kafka

Kafka is configured in Docker Compose with KRaft mode (no Zookeeper needed).

```bash
# Start Kafka
docker-compose -f infra/docker/docker-compose.yml --profile kafka up -d

# Or for local development
docker-compose -f infra/docker/docker-compose.local.yml --profile kafka up -d

# Verify Kafka is running
docker ps | grep kafka
```

### Kafka Configuration

Kafka is configured with:
- **Bootstrap Server**: `localhost:9092`
- **KRaft Mode**: No Zookeeper required
- **Replication Factor**: 1 (single broker for local development)
- **Data Directory**: `/var/lib/kafka/data` (persisted in Docker volume)

### Environment Variables

Set `KAFKA_BROKERS` environment variable:
```bash
export KAFKA_BROKERS=localhost:9092
```

Or in `.env` file:
```
KAFKA_BROKERS=localhost:9092
```

## Topic Management

### Listing Topics

```bash
# List all topics (excluding internal topics)
./neotool kafka --topic

# Or using Docker
./neotool kafka --topic --docker
```

### Creating Topics

Topics are typically created automatically when first message is published, but you can create them manually:

```bash
# Using Kafka CLI tools
docker exec neotool-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --topic swapi.people.v1 \
  --partitions 3 \
  --replication-factor 1

# Create DLQ topic
docker exec neotool-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --topic swapi.people.dlq \
  --partitions 3 \
  --replication-factor 1
```

### Topic Configuration

Recommended topic settings for batch processing:

```bash
# Create topic with retention policy
docker exec neotool-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --topic swapi.people.v1 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000 \  # 7 days
  --config segment.ms=86400000       # 1 day segments
```

**Configuration Options**:
- `retention.ms`: How long to keep messages (default: 7 days for batch topics)
- `segment.ms`: Segment rollover interval
- `compression.type`: Compression algorithm (snappy, gzip, lz4, zstd)
- `min.insync.replicas`: Minimum replicas for writes (for production)

### Describing Topics

```bash
# Describe specific topic
./neotool kafka --topic swapi.people.v1

# Shows:
# - Partition count
# - Replication factor
# - Leader assignments
# - ISR (In-Sync Replicas)
# - Configuration
```

### Deleting Topics

**Warning**: This is a destructive operation!

```bash
# Delete topic
docker exec neotool-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --delete \
  --topic swapi.people.v1
```

## Consumer Group Management

### Listing Consumer Groups

```bash
# List all consumer groups
./neotool kafka --consumer-group

# Or using Docker
./neotool kafka --consumer-group --docker
```

### Describing Consumer Groups

```bash
# Describe specific consumer group
./neotool kafka --consumer-group swapi-people-consumer-group

# Shows:
# - Partition assignments
# - Current offsets
# - Log end offsets
# - Consumer lag
```

### Resetting Consumer Group Offsets

**Warning**: This causes message reprocessing!

```bash
# Dry run (preview what would happen)
./neotool kafka --reset-offsets \
  --group swapi-people-consumer-group \
  --topic swapi.people.v1 \
  --to-earliest

# Reset to beginning (reprocess all messages)
./neotool kafka --reset-offsets \
  --group swapi-people-consumer-group \
  --topic swapi.people.v1 \
  --to-earliest \
  --execute

# Reset to end (skip all existing messages)
./neotool kafka --reset-offsets \
  --group swapi-people-consumer-group \
  --topic swapi.people.v1 \
  --to-latest \
  --execute

# Reset to specific offset
./neotool kafka --reset-offsets \
  --group swapi-people-consumer-group \
  --topic swapi.people.v1 \
  --to-offset 1000 \
  --execute

# Reset to specific datetime
./neotool kafka --reset-offsets \
  --group swapi-people-consumer-group \
  --topic swapi.people.v1 \
  --to-datetime 2024-01-01T00:00:00 \
  --execute
```

**Important**: Consumer group must be inactive (no running consumers) before resetting offsets.

### Consumer Group States

Consumer groups can be in different states:
- **Empty**: No members, no offsets committed
- **Dead**: No members, but has committed offsets
- **Stable**: Active members, partitions assigned
- **Rebalancing**: Members joining/leaving, partitions being reassigned

## Message Operations

### Producing Test Messages

```bash
# Produce message using Kafka console producer
docker exec -it neotool-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic swapi.people.v1 \
  --property "parse.key=true" \
  --property "key.separator=:"

# Then type messages in format: key:value
# Example:
# person-1:{"batchId":"test-123","recordId":"person-1","ingestedAt":"2024-01-01T00:00:00Z","payload":{"name":"Luke Skywalker"}}
```

### Consuming Messages

```bash
# Consume messages from beginning
docker exec -it neotool-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic swapi.people.v1 \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true

# Consume from specific offset
docker exec -it neotool-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic swapi.people.v1 \
  --partition 0 \
  --offset 100
```

## Troubleshooting

### Consumer Not Processing Messages

**Symptoms**: No messages being processed, consumer group shows no activity

**Diagnosis**:
1. Check consumer group status:
   ```bash
   ./neotool kafka --consumer-group swapi-people-consumer-group
   ```

2. Verify topic has messages:
   ```bash
   ./neotool kafka --topic swapi.people.v1
   ```

3. Check application logs:
   ```bash
   # Check for errors, rebalance issues, connectivity problems
   tail -f logs/application.log | grep -i kafka
   ```

4. Verify consumer is running:
   ```bash
   # Check if consumer application is running
   ps aux | grep java
   ```

**Solutions**:
- Restart consumer application
- Check Kafka connectivity: `telnet localhost 9092`
- Verify consumer group configuration in `application.yml`
- Check for rebalance issues in logs
- Verify topic exists and is accessible

### High Partition Lag

**Symptoms**: Consumer lag increasing, messages not being processed fast enough

**Diagnosis**:
1. Check partition lag:
   ```bash
   ./neotool kafka --consumer-group swapi-people-consumer-group
   # Look for LAG column
   ```

2. Monitor processing duration:
   ```bash
   # Check metrics endpoint
   curl http://localhost:8081/metrics | grep processing.duration
   ```

3. Check for stuck partitions:
   ```bash
   # Look for partitions with high lag that aren't decreasing
   ```

**Solutions**:
- Scale out consumers (increase consumer instances)
- Optimize processing logic to reduce processing time
- Reduce retry delays in configuration
- Check for poison messages blocking partitions
- Increase `max.poll.records` for higher throughput

### Frequent Rebalances

**Symptoms**: Consumer group frequently rebalancing, processing interrupted

**Diagnosis**:
1. Check `max.poll.interval.ms` configuration:
   ```yaml
   # In application.yml
   kafka:
     consumers:
       swapi-people:
         max-poll-interval-ms: 300000  # 5 minutes
   ```

2. Monitor processing duration:
   ```bash
   curl http://localhost:8081/metrics | grep processing.duration
   ```

3. Check for long retry loops:
   ```bash
   # Review retry configuration
   # batch.consumer.max-retries
   # batch.consumer.max-retry-delay-ms
   ```

**Solutions**:
- Increase `max.poll.interval.ms` to accommodate processing time
- Reduce retry delays to shorten retry cycles
- Optimize processing time
- Check for network issues causing slow processing
- Reduce `max.poll.records` to process fewer messages per poll

### DLQ Publishing Failures

**Symptoms**: Messages not appearing in DLQ, DLQ publish failure metrics increasing

**Diagnosis**:
1. Check DLQ topic exists:
   ```bash
   ./neotool kafka --topic swapi.people.dlq
   ```

2. Verify DLQ publisher configuration:
   ```yaml
   # Check Kafka producer configuration
   kafka:
     producers:
       default:
         acks: all
   ```

3. Check Kafka producer errors in logs:
   ```bash
   tail -f logs/application.log | grep -i "dlq\|publish"
   ```

**Solutions**:
- Verify DLQ topic exists and is accessible
- Check Kafka producer connectivity
- Increase DLQ retry attempts in configuration
- Implement DLQ fallback mechanism
- Check for Kafka broker issues

### Shutdown Timeouts

**Symptoms**: Application shutdown taking too long, timeout warnings in logs

**Diagnosis**:
1. Check `shutdown-timeout-seconds` configuration:
   ```yaml
   batch:
     consumer:
       shutdown-timeout-seconds: 30
   ```

2. Monitor in-flight work during shutdown:
   ```bash
   # Check logs for shutdown progress
   tail -f logs/application.log | grep -i shutdown
   ```

**Solutions**:
- Increase `shutdown-timeout-seconds` if processing takes longer
- Optimize processing time to reduce shutdown duration
- Check for long retry loops that delay shutdown
- Monitor shutdown metrics to identify bottlenecks

### Consumer Group Not Found

**Symptoms**: Error when describing consumer group, "does not exist"

**Diagnosis**:
1. Consumer group may not have consumed any messages yet
2. Consumer group may have been deleted
3. Consumer application may not have started

**Solutions**:
- Start consumer application to create consumer group
- Verify consumer group name matches configuration
- Check if consumer has consumed at least one message

## Common Operations

### Viewing Consumer Lag

```bash
# Describe consumer group to see lag
./neotool kafka --consumer-group swapi-people-consumer-group

# Lag is shown in the LAG column
# Positive lag = messages waiting to be processed
# Zero lag = all messages processed
```

### Pausing Consumer Processing

To pause consumer processing:
1. Stop the consumer application
2. Consumer group will remain, but no processing occurs
3. Messages will accumulate (lag will increase)

### Resuming Consumer Processing

To resume consumer processing:
1. Start the consumer application
2. Consumer will rejoin group and resume processing
3. Processing will continue from last committed offset

### Clearing Consumer Group

To clear consumer group (remove all offsets):
1. Stop all consumer instances
2. Delete consumer group:
   ```bash
   docker exec neotool-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 \
     --delete \
     --group swapi-people-consumer-group
   ```
3. Restart consumers (they will start from `auto-offset-reset` setting)

### Viewing Topic Messages

```bash
# View last 10 messages
docker exec neotool-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic swapi.people.v1 \
  --from-beginning \
  --max-messages 10

# View messages with timestamps
docker exec neotool-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic swapi.people.v1 \
  --from-beginning \
  --property print.timestamp=true
```

## Production Considerations

### Topic Configuration

For production, consider:
- **Replication Factor**: At least 3 for high availability
- **Partition Count**: Based on throughput requirements (more partitions = more parallelism)
- **Retention**: Based on business requirements (compliance, replay needs)
- **Compression**: Enable compression (snappy or zstd) for better throughput

### Consumer Configuration

For production, consider:
- **Session Timeout**: Balance between fast failure detection and network tolerance
- **Max Poll Records**: Balance between throughput and memory usage
- **Max Poll Interval**: Set based on processing time + retry time + buffer
- **Enable Auto Commit**: Always `false` for at-least-once semantics

### Monitoring

Set up monitoring for:
- Consumer lag per partition
- Processing rate and duration
- Error rates and DLQ rates
- Consumer group membership and rebalances
- Topic message rates and sizes

See [Kafka Monitoring Guide](./kafka-monitoring-guide.md) for detailed monitoring setup.

## Related Documentation

- [Kafka Consumer Pattern](./kafka-consumer-pattern.md) - Implementation pattern
- [Kafka Monitoring Guide](./kafka-monitoring-guide.md) - Monitoring and alerting
- [Batch Workflow Standard](../05-standards/workflow-standards/batch-workflow-standard.md) - Batch processing standards
