---
title: BACEN IFData Ingestion Runbook
type: runbook
category: operations
status: current
version: 1.0.0
tags: [runbook, operations, bacen, ifdata, prefect, kafka, prefect3, financial-data]
ai_optimized: true
search_keywords: [runbook, bacen, ifdata, operations, troubleshooting, monitoring, prefect3, work-pool, financial-data]
related:
  - 03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.md
  - 05-standards/workflow-standards/batch-workflow-standard.md
---

# BACEN IFData Ingestion Runbook

> **Purpose**: Operations guide for monitoring, troubleshooting, and operating the BACEN IFData ingestion job.

## Overview

This runbook provides operational procedures for the BACEN IFData ingestion job, including monitoring, manual operations, troubleshooting, and incident response.

## Quick Reference

- **Flow Name**: `bacen-ifdata-ingestion`
- **Deployment**: `bacen-ifdata-ingestion/bacen-ifdata-ingestion-deployment`
- **Prefect Version**: 3.6.5+ (uses work pools and work queues)
- **Work Pool**: `default` (type: `process` for local, `docker`/`kubernetes` for production)
- **Work Queue**: `bacen-ifdata`
- **Schedule**: Daily at 2:00 AM UTC (`0 2 * * *`)
- **Kafka Topics**: 
  - Main: `financial-data.institution.upserted`
  - DLQ: `financial-data.institution.upserted.dlq`
- **Database Schema**: `financial_data`
- **Key Tables**: 
  - `institution` - BACEN institution registry
  - `ifdata_extract_task` - Extraction task control
  - `ifdata_load_control` - Coarse-grained load tracking
  - `ifdata_raw` - Raw staging data
  - `bank_report` - Promoted hot table
- **Prefect UI**: http://localhost:4201 (Prefect 3.x includes UI in server)
- **Prefect API**: http://localhost:4200/api

## Monitoring

### Prefect UI

1. **Access Prefect UI**: http://localhost:4201
2. **View Flow Runs**: Navigate to "Flow Runs" and filter by `bacen-ifdata-ingestion`
3. **Check Run Status**: 
   - ✅ Completed (green) - Success
   - ⚠️ Failed (red) - Failure
   - 🔄 Running (blue) - In progress
4. **View Task Details**: Click on a run to see individual task status and logs
5. **View Artifacts**: Click on "Artifacts" tab to see execution summary

### Key Metrics to Monitor

#### Prefect Metrics
- **Flow Run Success Rate**: Should be > 95%
- **Task Duration**: 
  - `determine_target_quarter`: Typically < 1 second
  - `ingest_registry`: Typically 30-120 seconds (depends on registry size)
  - `fetch_report_catalog`: Typically 5-10 seconds
  - `create_extract_tasks`: Typically 1-5 seconds
  - `execute_paginated_extraction`: Variable (depends on data volume, runs in parallel)
  - `check_extract_completion`: Typically < 1 second
  - `promote_raw_to_hot`: Typically 60-300 seconds (depends on data volume)
  - `publish_institution_events`: Typically 10-60 seconds (depends on changed institutions)
- **Task Failure Rate**: Should be < 5%
- **Extraction Loop Iterations**: Should complete within `EXTRACTION_LOOP_MAX_ITERATIONS` (default: 1000)

#### Database Metrics
- **Extract Task Status**: Monitor `financial_data.ifdata_extract_task` for:
  - PENDING tasks (should decrease over time)
  - ERROR tasks (should be minimal)
  - EMPTY tasks (normal - indicates end of pagination)
- **Load Control Status**: Monitor `financial_data.ifdata_load_control` for:
  - `extracted` flag (indicates extraction complete)
  - `promoted` flag (indicates promotion complete)
  - `completed` flag (indicates full load complete)
- **Raw Data Volume**: Monitor `financial_data.ifdata_raw` table size
- **Promoted Data**: Monitor `financial_data.bank_report` record counts

#### Kafka Metrics
- **Consumer Lag**: Check consumer group lag for institution enhancement consumer
- **DLQ Message Count**: Monitor `financial-data.institution.upserted.dlq` topic for poison messages
- **Topic Throughput**: Messages per second on `financial-data.institution.upserted`

#### Application Metrics (if Prometheus configured)
- `bacen.ifdata.extracted.rows` - Total rows extracted
- `bacen.ifdata.promoted.records` - Total records promoted
- `bacen.ifdata.kafka.published` - Kafka events published
- `bacen.ifdata.kafka.failed` - Kafka publish failures
- `bacen.ifdata.tasks.executed` - Extraction tasks executed
- `bacen.ifdata.tasks.error` - Extraction task errors

### Alerting Thresholds

Set up alerts for:
- Flow run failures (immediate)
- Extraction task error rate > 10% (warning)
- DLQ message count > 10 in 1 hour (warning)
- Extraction loop iterations approaching limit (warning)
- Promotion duration > 10 minutes (warning)
- Kafka publish failure rate > 5% (warning)
- Consumer lag > 1000 messages (warning)

## Manual Operations

### Trigger Manual Run

```bash
# Via Prefect CLI (Prefect 3.x)
prefect deployment run bacen-ifdata-ingestion/bacen-ifdata-ingestion-deployment

# With specific target quarter (bypasses publication window checks)
prefect deployment run bacen-ifdata-ingestion/bacen-ifdata-ingestion-deployment \
  --param target_ano_mes=202403

# Force registry refresh even if it exists
prefect deployment run bacen-ifdata-ingestion/bacen-ifdata-ingestion-deployment \
  --param force_registry_refresh=true

# Or via Prefect UI
# Navigate to deployment → Click "Run" button → Set parameters

# Check work queue status
prefect work-queue inspect bacen-ifdata --pool default
```

### Check Flow Status

```bash
# List recent runs
prefect flow-run list --flow-name bacen-ifdata-ingestion --limit 10

# Get specific run details
prefect flow-run inspect <run-id>

# View run logs
prefect flow-run logs <run-id>
```

### View Logs

```bash
# Via Prefect CLI
prefect flow-run logs <run-id>

# Filter by task
prefect flow-run logs <run-id> --task-run-name determine_target_quarter

# Or view in Prefect UI
# Click on flow run → View logs tab
```

### Pause/Resume Schedule

```bash
# Pause scheduled runs (Prefect 3.x)
prefect deployment pause bacen-ifdata-ingestion/bacen-ifdata-ingestion-deployment

# Resume scheduled runs
prefect deployment resume bacen-ifdata-ingestion/bacen-ifdata-ingestion-deployment

# Or pause/resume work queue
prefect work-queue pause bacen-ifdata --pool default
prefect work-queue resume bacen-ifdata --pool default
```

### Manual Data Operations

#### Check Last Processed Quarter

```sql
-- Check last extracted quarter
SELECT MAX(ano_mes) as last_extracted_ano_mes
FROM financial_data.ifdata_load_control
WHERE extracted = TRUE;
```

#### Check Task Status for Quarter

```sql
-- Check extract task status for a specific quarter
SELECT 
    status,
    COUNT(*) as count,
    SUM(CASE WHEN attempt_count > 0 THEN 1 ELSE 0 END) as retried_count
FROM financial_data.ifdata_extract_task
WHERE ano_mes = '202403'
GROUP BY status;
```

#### Check Load Control Status

```sql
-- Check load control status for a quarter
SELECT 
    ano_mes,
    nome_relatorio,
    extracted,
    promoted,
    completed,
    extracted_at,
    promoted_at,
    completed_at
FROM financial_data.ifdata_load_control
WHERE ano_mes = '202403'
ORDER BY nome_relatorio;
```

## Troubleshooting

### Flow Fails at Target Quarter Determination

**Symptoms**: Flow fails during `determine_target_quarter` task

**Possible Causes**:
- Database connection issues
- Invalid publication window logic
- Missing historical start date configuration

**Resolution**:
1. Check database connectivity
2. Verify `DATABASE_URL` environment variable
3. Check Prefect logs for specific error
4. Verify database schema `financial_data` exists
5. Check `ifdata_load_control` table exists and is accessible

### Flow Fails at Registry Ingestion

**Symptoms**: Flow fails during `ingest_registry` task

**Possible Causes**:
- BACEN API is down or rate-limited
- Network connectivity issues
- Invalid BACEN_BASE_URL configuration
- CSV parsing errors
- Database connection issues

**Resolution**:
1. Check BACEN API status: https://olinda.bcb.gov.br/olinda/servico/IFDATA/versao/v1/documentacao
2. Verify network connectivity to BACEN API
3. Check Prefect logs for specific error (HTTP status, CSV parsing errors)
4. Verify `BACEN_BASE_URL` environment variable
5. Check database connection and `institution` table exists
6. Retry the flow run manually

### Flow Fails at Extraction Stage

**Symptoms**: Flow fails during `execute_paginated_extraction` task

**Possible Causes**:
- BACEN API is down or rate-limited
- Network connectivity issues
- CSV parsing errors
- Database connection issues
- Task retry exhaustion

**Resolution**:
1. Check BACEN API status
2. Verify network connectivity
3. Check Prefect logs for specific error
4. Check `ifdata_extract_task` table for ERROR tasks:
   ```sql
   SELECT 
       ano_mes,
       nome_relatorio,
       status,
       attempt_count,
       last_error,
       last_http_status
   FROM financial_data.ifdata_extract_task
   WHERE status = 'ERROR'
   ORDER BY created_at DESC
   LIMIT 10;
   ```
5. Retry failed tasks manually or wait for automatic retry
6. Check if extraction loop hit max iterations limit

### Flow Fails at Promotion Stage

**Symptoms**: Flow fails during `promote_raw_to_hot` task

**Possible Causes**:
- Database connection issues
- Insufficient database resources
- Data transformation errors
- Missing active institutions in registry

**Resolution**:
1. Check database connectivity and resources
2. Verify `ifdata_raw` table has data for the quarter
3. Check Prefect logs for specific error
4. Verify `institution` table has active institutions (situacao='A')
5. Check database disk space and memory
6. Retry the flow run manually

### Flow Fails at Kafka Publishing

**Symptoms**: Flow fails during `publish_institution_events` task

**Possible Causes**:
- Kafka broker is down
- Invalid KAFKA_BROKERS configuration
- Topic doesn't exist
- Network connectivity to Kafka
- Serialization errors

**Resolution**:
1. Check Kafka broker status:
   ```bash
   docker ps | grep kafka
   ```
2. Verify Kafka connectivity:
   ```bash
   kafka-console-producer --bootstrap-server localhost:9092 --topic financial-data.institution.upserted
   ```
3. Check topic exists:
   ```bash
   kafka-topics --bootstrap-server localhost:9092 --list | grep financial-data
   ```
4. Verify `KAFKA_BROKERS` environment variable
5. Check Prefect logs for specific Kafka error
6. Check DLQ topic for failed messages

### High DLQ Message Count

**Symptoms**: Many messages in `financial-data.institution.upserted.dlq` topic

**Possible Causes**:
- Invalid message schema
- Kafka broker issues
- Network connectivity problems
- Serialization errors

**Resolution**:
1. Inspect DLQ messages:
   ```bash
   kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic financial-data.institution.upserted.dlq --from-beginning
   ```
2. Check error patterns in DLQ messages
3. Review Prefect logs for Kafka publish errors
4. Verify Kafka broker health
5. Fix root cause (schema, connectivity, broker issues)
6. Reprocess DLQ messages if needed (see "Reprocessing DLQ Messages")

### Extraction Loop Not Completing

**Symptoms**: Extraction loop runs for many iterations without completing

**Possible Causes**:
- Too many PENDING tasks
- Tasks stuck in RUNNING state
- Worker not processing tasks
- Database locking issues

**Resolution**:
1. Check PENDING task count:
   ```sql
   SELECT COUNT(*) FROM financial_data.ifdata_extract_task WHERE status = 'PENDING';
   ```
2. Check RUNNING tasks (may be stuck):
   ```sql
   SELECT * FROM financial_data.ifdata_extract_task 
   WHERE status = 'RUNNING' 
   AND started_at < NOW() - INTERVAL '1 hour';
   ```
3. Verify Prefect workers are running and processing tasks
4. Check work queue status: `prefect work-queue inspect bacen-ifdata --pool default`
5. Check for database locks or connection issues
6. Manually mark stuck RUNNING tasks as ERROR if needed (use with caution)

### Data Quality Issues

**Symptoms**: Data in `bank_report` appears incorrect or incomplete

**Possible Causes**:
- Promotion logic errors
- Active institution filter too restrictive
- Raw data quality issues
- Transformation errors

**Resolution**:
1. Check raw data in `ifdata_raw`:
   ```sql
   SELECT COUNT(*) FROM financial_data.ifdata_raw WHERE ano_mes = '202403';
   ```
2. Check promoted data:
   ```sql
   SELECT COUNT(*) FROM financial_data.bank_report WHERE ano_mes = '202403';
   ```
3. Compare counts to identify missing data
4. Check active institution filter:
   ```sql
   SELECT COUNT(*) FROM financial_data.institution WHERE situacao = 'A' AND ano_mes_ref = '202403';
   ```
5. Review promotion task logs for errors
6. Re-run promotion if needed (see "Reprocessing Data")

## Incident Response

### Flow Run Failure

1. **Immediate Actions**:
   - Check Prefect UI for error details
   - Review flow run logs
   - Identify failing task

2. **Investigation**:
   - Check external dependencies (BACEN API, Kafka, Database)
   - Verify configuration (environment variables)
   - Review recent changes
   - Check database and Kafka connectivity

3. **Resolution**:
   - Fix root cause
   - Trigger manual run to verify fix
   - Monitor next scheduled run

### Database Connection Issues

1. **Immediate Actions**:
   - Check database service status
   - Verify database connectivity
   - Check connection pool settings

2. **Investigation**:
   - Review database logs
   - Check connection string configuration
   - Verify network connectivity
   - Check database resource usage

3. **Resolution**:
   - Fix connection issues
   - Restart database if needed
   - Update connection configuration
   - Retry flow run

### Kafka Connection Issues

1. **Immediate Actions**:
   - Check Kafka broker status
   - Verify Kafka connectivity
   - Check topic existence

2. **Investigation**:
   - Review Kafka logs
   - Verify broker configuration
   - Check network connectivity
   - Verify topic configuration

3. **Resolution**:
   - Fix Kafka connectivity
   - Restart Kafka if needed
   - Update broker configuration
   - Retry flow run

### BACEN API Issues

1. **Immediate Actions**:
   - Check BACEN API status
   - Verify API connectivity
   - Check rate limiting

2. **Investigation**:
   - Review API response logs
   - Check HTTP status codes
   - Verify API endpoint URLs
   - Check for API changes

3. **Resolution**:
   - Wait for API recovery if temporary
   - Update API endpoints if changed
   - Adjust retry logic if needed
   - Retry flow run after API recovery

## Maintenance Tasks

### Reprocessing Data

#### Re-promote Existing Raw Data

```bash
# Use the promote_data.py script
cd workflow/financial_data/bacen_ifdata
python scripts/promote_data.py --ano-mes 202403

# Promote specific report
python scripts/promote_data.py --ano-mes 202403 --report "Ativo"

# Verbose output
python scripts/promote_data.py --ano-mes 202403 --verbose
```

#### Re-extract and Re-promote

```bash
# Use the run_full_ingestion.py script
cd workflow/financial_data/bacen_ifdata
python scripts/run_full_ingestion.py 202403

# Purge and re-extract from scratch
python scripts/run_full_ingestion.py 202403 --purge

# Verbose output
python scripts/run_full_ingestion.py 202403 --verbose
```

### Reprocessing DLQ Messages

1. **Inspect DLQ Messages**:
   ```bash
   kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic financial-data.institution.upserted.dlq --from-beginning --max-messages 10
   ```

2. **Extract Original Messages**: DLQ messages contain original message in `originalMessage` field

3. **Republish to Main Topic**: 
   - Fix root cause first
   - Republish corrected messages to `financial-data.institution.upserted`
   - Monitor processing

### Topic Management

**Create Topics** (if needed):
```bash
kafka-topics --bootstrap-server localhost:9092 --create \
  --topic financial-data.institution.upserted --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 --create \
  --topic financial-data.institution.upserted.dlq --partitions 1 --replication-factor 1
```

**Check Topic Status**:
```bash
kafka-topics --bootstrap-server localhost:9092 --describe --topic financial-data.institution.upserted
```

### Consumer Group Management

**Check Consumer Group Status**:
```bash
# Using NeoTool CLI (recommended)
./neotool kafka --consumer-group <consumer-group-name>

# Direct Kafka CLI
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group <consumer-group-name> --describe
```

**Reset Consumer Group Offset** (if needed):

**Using NeoTool CLI** (recommended):
```bash
# Preview what would happen (dry-run)
./neotool kafka --reset-offsets --group <consumer-group-name> \
  --topic financial-data.institution.upserted --to-earliest

# Actually reset offsets to beginning (reprocess all messages)
./neotool kafka --reset-offsets --group <consumer-group-name> \
  --topic financial-data.institution.upserted --to-earliest --execute
```

**Warning**: Only reset offsets if you understand the implications. This will reprocess all messages.

### Database Maintenance

#### Check Table Sizes

```sql
-- Check table sizes
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'financial_data'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

#### Purge Old Raw Data (if needed)

**Warning**: Only purge raw data after careful consideration. Prefer keeping raw data for audit and reprocessing.

```sql
-- Example: Purge raw data older than 2 years (adjust as needed)
DELETE FROM financial_data.ifdata_raw
WHERE ano_mes < '202203'  -- Adjust date as needed
AND ano_mes IN (
    SELECT DISTINCT ano_mes 
    FROM financial_data.ifdata_load_control 
    WHERE completed = TRUE
);
```

## Useful Commands

### Prefect Commands

```bash
# List all deployments
prefect deployment ls

# View deployment details
prefect deployment inspect bacen-ifdata-ingestion/bacen-ifdata-ingestion-deployment

# View flow run history
prefect flow-run ls --flow-name bacen-ifdata-ingestion --limit 20

# Cancel a running flow
prefect flow-run cancel <run-id>

# View work queue status
prefect work-queue inspect bacen-ifdata --pool default
```

### Kafka Commands

**Using NeoTool CLI** (recommended):
```bash
# List topics (excluding internals)
./neotool kafka --topic

# Describe topic
./neotool kafka --topic financial-data.institution.upserted

# List consumer groups
./neotool kafka --consumer-group

# Check consumer group
./neotool kafka --consumer-group <consumer-group-name>
```

**Direct Kafka CLI** (alternative):
```bash
# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Describe topic
kafka-topics --bootstrap-server localhost:9092 --describe --topic financial-data.institution.upserted

# Consume messages
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic financial-data.institution.upserted --from-beginning

# Check consumer group
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group <consumer-group-name> --describe
```

### Database Commands

```bash
# Connect to database
psql $DATABASE_URL

# Or with individual connection parameters
psql -h localhost -p 5432 -U neotool -d neotool_db
```

**Useful SQL Queries**:

```sql
-- Check last processed quarter
SELECT MAX(ano_mes) FROM financial_data.ifdata_load_control WHERE extracted = TRUE;

-- Check task status summary
SELECT status, COUNT(*) FROM financial_data.ifdata_extract_task GROUP BY status;

-- Check load control status
SELECT ano_mes, COUNT(*) as reports, 
       SUM(CASE WHEN extracted THEN 1 ELSE 0 END) as extracted_count,
       SUM(CASE WHEN promoted THEN 1 ELSE 0 END) as promoted_count,
       SUM(CASE WHEN completed THEN 1 ELSE 0 END) as completed_count
FROM financial_data.ifdata_load_control
GROUP BY ano_mes
ORDER BY ano_mes DESC;

-- Check raw data volume
SELECT ano_mes, COUNT(*) as raw_rows 
FROM financial_data.ifdata_raw 
GROUP BY ano_mes 
ORDER BY ano_mes DESC;

-- Check promoted data volume
SELECT ano_mes, COUNT(*) as promoted_records 
FROM financial_data.bank_report 
GROUP BY ano_mes 
ORDER BY ano_mes DESC;
```

## Escalation

If issues cannot be resolved using this runbook:

1. **Check Documentation**: Review [BACEN IFData Ingestion Feature Documentation](../../03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.md)
2. **Review Logs**: Check Prefect, Kafka, and database logs
3. **Contact Team**: Escalate to Financial Data team or Platform team
4. **Create Incident**: Document issue and resolution steps

## Related Documentation

- [BACEN IFData Ingestion Feature](../../03-features/financial_data/bacen-ifdata-ingestion/bacen-ifdata-ingestion.md) - Complete feature documentation
- [Batch Workflow Standard](../../05-standards/workflow-standards/batch-workflow-standard.md) - Standards and requirements
- [Prefect Documentation](https://docs.prefect.io/) - Prefect official docs
- [Kafka Documentation](https://kafka.apache.org/documentation/) - Kafka official docs
- [BACEN IFData API Documentation](https://olinda.bcb.gov.br/olinda/servico/IFDATA/versao/v1/documentacao) - BACEN API docs


