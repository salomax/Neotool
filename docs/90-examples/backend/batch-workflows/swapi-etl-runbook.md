---
title: SWAPI ETL Runbook
type: runbook
category: operations
status: current
version: 1.1.0
tags: [runbook, operations, swapi, etl, prefect, kafka, prefect3]
ai_optimized: true
search_keywords: [runbook, swapi, etl, operations, troubleshooting, monitoring, prefect3, work-pool]
related:
  - 07-examples/batch-workflows/swapi-etl-workflow.md
  - 05-standards/workflow-standards/batch-workflow-standard.md
---

# SWAPI ETL Runbook

> **Purpose**: Operations guide for monitoring, troubleshooting, and operating the SWAPI People ETL job.

## Overview

This runbook provides operational procedures for the SWAPI People ETL job, including monitoring, manual operations, troubleshooting, and incident response.

## Quick Reference

- **Flow Name**: `swapi-people-etl`
- **Deployment**: `swapi-people-etl/swapi-people-etl-deployment`
- **Prefect Version**: 3.6.5+ (uses work pools and work queues)
- **Work Pool**: `default` (type: `process` for local, `docker`/`kubernetes` for production)
- **Work Queue**: `swapi-etl`
- **Schedule**: Daily at 2:00 AM UTC (`0 2 * * *`)
- **Kafka Topics**: 
  - Main: `swapi.people.v1`
  - DLQ: `swapi.people.dlq`
- **Consumer Group**: `swapi-people-consumer-group`
- **Prefect UI**: http://localhost:4201 (Prefect 3.x includes UI in server)
- **Prefect API**: http://localhost:4200/api

## Monitoring

### Prefect UI

1. **Access Prefect UI**: http://localhost:4201
2. **View Flow Runs**: Navigate to "Flow Runs" and filter by `swapi-people-etl`
3. **Check Run Status**: 
   - ✅ Completed (green) - Success
   - ⚠️ Failed (red) - Failure
   - 🔄 Running (blue) - In progress
4. **View Task Details**: Click on a run to see individual task status and logs

### Key Metrics to Monitor

#### Prefect Metrics
- **Flow Run Success Rate**: Should be > 95%
- **Task Duration**: 
  - `extract_people`: Typically 5-10 seconds
  - `transform_people`: Typically < 1 second
  - `publish_to_kafka`: Typically 10-30 seconds
- **Task Failure Rate**: Should be < 5%

#### Kafka Metrics
- **Consumer Lag**: Check consumer group lag for `swapi-people-consumer-group`
- **DLQ Message Count**: Monitor `swapi.people.dlq` topic for poison messages
- **Topic Throughput**: Messages per second on `swapi.people.v1`

#### Application Metrics (Prometheus)
- `swapi.people.processed` - Total processed messages
- `swapi.people.processing.duration` - Processing time histogram
- `swapi.people.dlq.count` - DLQ message count
- `swapi.people.retry.count` - Retry attempt count
- `swapi.people.error.count` - Error count

### Alerting Thresholds

Set up alerts for:
- Flow run failures (immediate)
- Consumer lag > 1000 messages (warning)
- DLQ message count > 10 in 1 hour (warning)
- Processing duration > 5 minutes (warning)
- Error rate > 10% (critical)

## Manual Operations

### Trigger Manual Run

```bash
# Via Prefect CLI (Prefect 3.x)
prefect deployment run swapi-people-etl/swapi-people-etl-deployment

# Or via Prefect UI
# Navigate to deployment → Click "Run" button

# Check work queue status
prefect work-queue inspect swapi-etl --pool default
```

### Check Flow Status

```bash
# List recent runs
prefect flow-run list --flow-name swapi-people-etl --limit 10

# Get specific run details
prefect flow-run inspect <run-id>
```

### View Logs

```bash
# Via Prefect CLI
prefect flow-run logs <run-id>

# Or view in Prefect UI
# Click on flow run → View logs tab
```

### Pause/Resume Schedule

```bash
# Pause scheduled runs (Prefect 3.x)
prefect deployment pause swapi-people-etl/swapi-people-etl-deployment

# Resume scheduled runs
prefect deployment resume swapi-people-etl/swapi-people-etl-deployment

# Or pause/resume work queue
prefect work-queue pause swapi-etl --pool default
prefect work-queue resume swapi-etl --pool default
```

## Troubleshooting

### Flow Fails at Extract Stage

**Symptoms**: Flow fails during `extract_people` task

**Possible Causes**:
- SWAPI API is down or rate-limited
- Network connectivity issues
- Invalid SWAPI_BASE_URL configuration

**Resolution**:
1. Check SWAPI status: https://swapi.dev/
2. Verify network connectivity
3. Check Prefect logs for specific error
4. Verify `SWAPI_BASE_URL` environment variable
5. Retry the flow run manually

### Flow Fails at Publish Stage

**Symptoms**: Flow fails during `publish_to_kafka` task

**Possible Causes**:
- Kafka broker is down
- Invalid KAFKA_BROKERS configuration
- Topic doesn't exist
- Network connectivity to Kafka

**Resolution**:
1. Check Kafka broker status:
   ```bash
   docker ps | grep kafka
   ```
2. Verify Kafka connectivity:
   ```bash
   kafka-console-producer --bootstrap-server localhost:9092 --topic swapi.people.v1
   ```
3. Check topic exists:
   ```bash
   kafka-topics --bootstrap-server localhost:9092 --list | grep swapi
   ```
4. Verify `KAFKA_BROKERS` environment variable
5. Check Prefect logs for specific Kafka error

### Consumer Not Processing Messages

**Symptoms**: Messages in Kafka but not being consumed

**Possible Causes**:
- Consumer service is down
- Consumer group rebalancing
- Deserialization errors
- Consumer lag too high

**Resolution**:
1. Check consumer service status:
   ```bash
   # Check if app service is running
   curl http://localhost:8081/health
   ```
2. Check consumer group status:
   ```bash
   kafka-consumer-groups --bootstrap-server localhost:9092 \
     --group swapi-people-consumer-group --describe
   ```
3. Check application logs for errors
4. Verify consumer configuration in `application.yml`
5. Restart consumer service if needed

### High DLQ Message Count

**Symptoms**: Many messages in `swapi.people.dlq` topic

**Possible Causes**:
- Invalid message schema
- Processing logic errors
- Validation failures
- Poison messages

**Resolution**:
1. Inspect DLQ messages:
   ```bash
   kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic swapi.people.dlq --from-beginning
   ```
2. Check error patterns in DLQ messages
3. Review application logs for processing errors
4. Fix root cause (schema, validation, processing logic)
5. Reprocess DLQ messages if needed (see "Reprocessing DLQ Messages")

### Consumer Lag Increasing

**Symptoms**: Consumer lag continuously increasing

**Possible Causes**:
- Consumer processing too slowly
- Consumer service down
- High message volume
- Processing errors causing retries

**Resolution**:
1. Check consumer processing rate:
   ```bash
   # Monitor metrics
   curl http://localhost:8081/metrics | grep swapi.people
   ```
2. Scale up consumer instances if needed
3. Optimize processing logic
4. Check for processing bottlenecks
5. Review retry configuration

## Incident Response

### Flow Run Failure

1. **Immediate Actions**:
   - Check Prefect UI for error details
   - Review flow run logs
   - Identify failing task

2. **Investigation**:
   - Check external dependencies (SWAPI, Kafka)
   - Verify configuration
   - Review recent changes

3. **Resolution**:
   - Fix root cause
   - Trigger manual run to verify fix
   - Monitor next scheduled run

### Consumer Service Down

1. **Immediate Actions**:
   - Restart consumer service
   - Check service health endpoint
   - Verify Kafka connectivity

2. **Investigation**:
   - Check application logs
   - Review system resources
   - Check for OOM errors

3. **Resolution**:
   - Fix root cause
   - Monitor consumer lag
   - Verify message processing resumes

### Data Quality Issues

1. **Immediate Actions**:
   - Pause flow schedule
   - Investigate DLQ messages
   - Review recent data

2. **Investigation**:
   - Check SWAPI data changes
   - Review transformation logic
   - Verify schema compatibility

3. **Resolution**:
   - Update transformation logic if needed
   - Fix schema issues
   - Resume schedule after verification

## Maintenance Tasks

### Reprocessing DLQ Messages

1. **Inspect DLQ Messages**:
   ```bash
   kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic swapi.people.dlq --from-beginning --max-messages 10
   ```

2. **Extract Original Messages**: DLQ messages contain original message in `originalMessage` field

3. **Republish to Main Topic**: 
   - Fix root cause first
   - Republish corrected messages to `swapi.people.v1`
   - Monitor processing

### Topic Management

**Create Topics** (if needed):
```bash
kafka-topics --bootstrap-server localhost:9092 --create \
  --topic swapi.people.v1 --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 --create \
  --topic swapi.people.dlq --partitions 1 --replication-factor 1
```

**Check Topic Status**:
```bash
kafka-topics --bootstrap-server localhost:9092 --describe --topic swapi.people.v1
```

### Consumer Group Management

**Reset Consumer Group Offset** (if needed):

**Using NeoTool CLI** (recommended):
```bash
# Preview what would happen (dry-run)
./neotool kafka --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-earliest

# Actually reset offsets to beginning (reprocess all messages)
./neotool kafka --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-earliest --execute

# Reset to latest (skip existing messages)
./neotool kafka --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-latest --execute
```

**Direct Kafka CLI** (alternative):
```bash
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group swapi-people-consumer-group --reset-offsets \
  --to-earliest --topic swapi.people.v1 --execute
```

**Warning**: Only reset offsets if you understand the implications. This will reprocess all messages.

## Useful Commands

### Prefect Commands

```bash
# List all deployments
prefect deployment ls

# View deployment details
prefect deployment inspect swapi-people-etl/swapi-people-etl-deployment

# View flow run history
prefect flow-run ls --flow-name swapi-people-etl --limit 20

# Cancel a running flow
prefect flow-run cancel <run-id>
```

### Kafka Commands

**Using NeoTool CLI** (recommended):
```bash
# List topics (excluding internals)
./neotool kafka --topic

# Describe topic
./neotool kafka --topic swapi.people.v1

# List consumer groups
./neotool kafka --consumer-group

# Check consumer group
./neotool kafka --consumer-group swapi-people-consumer-group

# Reset offsets (preview)
./neotool kafka --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-earliest

# Reset offsets (execute)
./neotool kafka --reset-offsets --group swapi-people-consumer-group --topic swapi.people.v1 --to-earliest --execute
```

**Direct Kafka CLI** (alternative):
```bash
# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Describe topic
kafka-topics --bootstrap-server localhost:9092 --describe --topic swapi.people.v1

# Consume messages
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic swapi.people.v1 --from-beginning

# Check consumer group
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group swapi-people-consumer-group --describe
```

### Application Commands

```bash
# Health check
curl http://localhost:8081/health

# Metrics
curl http://localhost:8081/metrics | grep swapi

# Prometheus metrics
curl http://localhost:8081/prometheus | grep swapi
```

## Escalation

If issues cannot be resolved using this runbook:

1. **Check Documentation**: Review [SWAPI ETL Workflow](./swapi-etl-workflow.md)
2. **Review Logs**: Check Prefect, Kafka, and application logs
3. **Contact Team**: Escalate to Platform Team
4. **Create Incident**: Document issue and resolution steps

## Related Documentation

- [SWAPI ETL Workflow](./swapi-etl-workflow.md) - Complete workflow documentation
- [Batch Workflow Standard](../../05-standards/workflow-standards/batch-workflow-standard.md) - Standards and requirements
- [Prefect Documentation](https://docs.prefect.io/) - Prefect official docs
- [Kafka Documentation](https://kafka.apache.org/documentation/) - Kafka official docs
