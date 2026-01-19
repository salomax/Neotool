# DLQ Retry Consumer - Operations Runbook

## Overview

This runbook provides step-by-step procedures for operating the DLQ Retry Consumer in production.

## Prerequisites

- Access to Kubernetes cluster or Docker environment
- Access to Kafka cluster
- Permissions to view logs and metrics
- kubectl or docker CLI installed

## Quick Reference

### Common Commands

```bash
# Inspect DLQ (see what's in queue)
docker compose -f infra/docker/docker-compose.local.yml run --rm institution-enhancement-dlq-inspector

# Trigger DLQ retry manually
kubectl create job --from=cronjob/dlq-retry-consumer manual-retry-$(date +%s)

# View consumer logs
kubectl logs -f -l app=dlq-retry-consumer

# Check DLQ depth
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --describe --group dlq-retry-consumer-institution-enhancement
```

## Daily Operations

### Morning Checklist

1. **Check DLQ Depth**
   ```bash
   # Check number of messages in each DLQ
   ./scripts/check-dlq-depth.sh
   ```

   Expected: <100 messages per DLQ

   If >1000 messages:
   - Alert: Potential systemic issue
   - Action: Run DLQ inspector to analyze errors

2. **Review Retry Success Rate**
   ```bash
   # Check Grafana dashboard or query Prometheus
   curl "http://prometheus:9090/api/v1/query?query=dlq_retry_success_rate"
   ```

   Expected: >70% success rate

   If <50%:
   - Alert: Retries not effective
   - Action: Investigate error patterns

3. **Check for Permanent Errors**
   ```bash
   # Count permanent errors
   ./scripts/count-permanent-errors.sh
   ```

   If increasing:
   - Action: Review and fix root causes

### Weekly Checklist

1. **Review Error Patterns**
   - Run DLQ inspector for each service
   - Document new error patterns
   - Update configuration if needed

2. **Audit Retry Configuration**
   - Review retry strategies for each DLQ
   - Adjust based on success rates
   - Update documentation

3. **Cleanup Old Permanent Errors**
   - Review permanent errors >30 days old
   - Archive or delete if no longer relevant

## Common Scenarios

### Scenario 1: High DLQ Depth Alert

**Alert:** `DLQDepthHigh - financial-data.institution.upserted.enhancement.dlq has 2500 messages`

#### Step 1: Inspect DLQ
```bash
# Run inspector to understand error breakdown
docker compose run --rm institution-enhancement-dlq-inspector
```

Expected output:
```
=== DLQ Analysis Results ===
Total messages in DLQ: 2500
  Retryable: 2100 (84%)
  Non-retryable: 400 (16%)

=== Error Breakdown ===
1. http_timeout
   Count: 1800 (72%)
   Retryable: true
   Example error: context deadline exceeded calling CNPJá API
```

#### Step 2: Determine Root Cause

Check if error is:
- **Transient spike** (last 1-2 hours) → Normal, wait for auto-retry
- **Sustained issue** (>6 hours) → Investigate external dependencies
- **New error pattern** → May need code fix

#### Step 3: Take Action

**If transient:**
```bash
# Wait for next scheduled retry (runs every hour)
# Or trigger manual retry now
kubectl create job --from=cronjob/dlq-retry-consumer manual-retry-$(date +%s)
```

**If systemic issue:**
```bash
# Check external dependency status
curl -I https://api.cnpja.com  # Check if API is up

# Check service logs for patterns
kubectl logs -l app=institution-enhancement-consumer --tail=1000 | grep ERROR

# If external API is down, wait for recovery
# If bug in code, deploy fix first, then retry DLQ
```

#### Step 4: Monitor Recovery

```bash
# Watch DLQ depth decrease
watch -n 60 './scripts/check-dlq-depth.sh'

# Watch retry success rate
watch -n 60 'curl -s "http://prometheus:9090/api/v1/query?query=dlq_retry_success_rate"'
```

### Scenario 2: Low Retry Success Rate

**Alert:** `DLQRetrySuccessRateLow - institution-enhancement retry success rate is 25%`

#### Step 1: Analyze Failures
```bash
# Check consumer logs for recent errors
kubectl logs -l app=dlq-retry-consumer --tail=500 | grep "Failed to republish"
```

#### Step 2: Identify Pattern

Common causes:
- External API still down → Wait for recovery
- Rate limiting → Increase retry delays
- Invalid messages → Convert to permanent
- Bug in code → Deploy fix

#### Step 3: Adjust Strategy

**If rate limiting:**
```yaml
# Edit config/dlq-mappings.yaml
retry_strategy:
  max_retries: 3
  backoff:
    - retry_count: 0
      delay: 2h  # Increased from 1h
    - retry_count: 1
      delay: 12h  # Increased from 6h
    - retry_count: 2
      delay: 48h  # Increased from 24h
```

```bash
# Reload configuration
kubectl rollout restart deployment/dlq-retry-consumer
```

**If permanent errors:**
```bash
# Inspect messages
docker compose run --rm institution-enhancement-dlq-inspector

# If many validation errors, fix upstream and prevent new DLQ entries
```

### Scenario 3: Adding New DLQ Mapping

**Task:** Add DLQ processing for new payment service

#### Step 1: Add Configuration

Edit `config/dlq-mappings.yaml`:
```yaml
dlq_mappings:
  # ... existing mappings ...

  # New payment service
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
          delay: 5m
        - retry_count: 1
          delay: 30m
        - retry_count: 2
          delay: 2h
        - retry_count: 3
          delay: 6h
        - retry_count: 4
          delay: 24h
```

#### Step 2: Validate Configuration

```bash
# Validate YAML syntax
yamllint config/dlq-mappings.yaml

# Dry-run validation
./scripts/validate-config.sh config/dlq-mappings.yaml
```

#### Step 3: Deploy

**Development:**
```bash
# Update ConfigMap
kubectl create configmap dlq-retry-consumer-config \
  --from-file=dlq-mappings.yaml=config/dlq-mappings.yaml \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart consumer to pick up new config
kubectl rollout restart deployment/dlq-retry-consumer
```

**Production:**
```bash
# Follow change management process
# 1. Create PR with config change
# 2. Get approval
# 3. Deploy via CI/CD pipeline
```

#### Step 4: Verify

```bash
# Check logs for new DLQ being processed
kubectl logs -l app=dlq-retry-consumer | grep "payment-processor"

# Should see:
# "Loaded DLQ mapping" dlq="payment-processor" enabled=true
```

### Scenario 4: Manual Retry of Permanent Errors

**Task:** Retry permanent errors after fixing root cause

#### Step 1: Identify Messages to Retry

```bash
# Inspect DLQ
docker compose run --rm institution-enhancement-dlq-inspector

# Note CNPJs with permanent errors
# Example: validation_error affecting CNPJs [12345678000190, ...]
```

#### Step 2: Verify Fix is Deployed

```bash
# Ensure code fix is deployed
kubectl rollout status deployment/institution-enhancement-consumer

# Verify fix with standalone tool
cd workflow/go/financial_data/institution_enhancement
make run-standalone CNPJ=12345678000190
```

#### Step 3: Retry via neotool CLI (Future)

```bash
# Retry specific CNPJ
neotool kafka dlq retry \
  --service institution-enhancement \
  --key 12345678000190

# Retry all permanent errors with specific error type
neotool kafka dlq retry \
  --service institution-enhancement \
  --filter "validation_error" \
  --category permanent \
  --confirm
```

#### Step 4: Current Workaround (Manual)

Until neotool CLI is implemented:

```bash
# Option 1: Republish message manually via Kafka
kafka-console-consumer --bootstrap-server kafka:9092 \
  --topic financial-data.institution.upserted.enhancement.dlq \
  --from-beginning \
  | jq -r 'select(.original_key == "12345678000190") | .original_message' \
  | kafka-console-producer --bootstrap-server kafka:9092 \
  --topic financial-data.institution.upserted

# Option 2: Modify DLQ message to transient and let consumer retry
# (Advanced - requires Kafka admin access)
```

### Scenario 5: Emergency Stop

**Alert:** DLQ retry consumer is causing issues (e.g., overloading downstream)

#### Step 1: Immediate Stop

**Kubernetes:**
```bash
# Scale to zero replicas
kubectl scale deployment/dlq-retry-consumer --replicas=0

# Or suspend CronJob
kubectl patch cronjob/dlq-retry-consumer -p '{"spec":{"suspend":true}}'
```

**Docker Compose:**
```bash
# Stop container
docker stop neotool-dlq-retry-consumer
```

#### Step 2: Investigate

```bash
# Check logs for errors
kubectl logs -l app=dlq-retry-consumer --tail=1000

# Check downstream service health
kubectl get pods -l app=institution-enhancement-consumer
kubectl logs -l app=institution-enhancement-consumer --tail=500

# Check metrics
curl "http://prometheus:9090/api/v1/query?query=rate(http_requests_total[5m])"
```

#### Step 3: Fix and Resume

```bash
# After fixing issue, resume
kubectl scale deployment/dlq-retry-consumer --replicas=1
# Or
kubectl patch cronjob/dlq-retry-consumer -p '{"spec":{"suspend":false}}'
```

## Monitoring

### Key Metrics

| Metric | Alert Threshold | Action |
|--------|----------------|--------|
| `kafka_dlq_message_count` | >1000 | Investigate error patterns |
| `dlq_retry_success_rate` | <50% | Check external dependencies |
| `dlq_messages_by_category{category="permanent"}` | >100 | Review and fix root causes |
| `dlq_processing_duration_seconds` | >60s | Optimize consumer or scale |

### Dashboards

**Grafana Dashboard: DLQ Retry Consumer**

Panels:
1. DLQ Depth (all DLQs) - Time series
2. Retry Success Rate - Gauge
3. Messages by Category - Pie chart
4. Error Type Distribution - Bar chart
5. Processing Time - Histogram
6. Consumer Lag - Time series

### Logs

**Important log patterns to monitor:**

```bash
# Success
"Successfully republished to original topic"

# Transient failure (will retry)
"Failed to republish, will retry later"

# Permanent conversion
"Converting to permanent after max retries"

# Configuration load
"Loaded DLQ mapping"

# Errors
"Failed to unmarshal DLQ message"
"Failed to connect to Kafka"
```

## Troubleshooting

### Consumer Not Processing Messages

**Symptoms:**
- DLQ depth not decreasing
- No logs from consumer

**Checks:**
```bash
# 1. Check if consumer is running
kubectl get pods -l app=dlq-retry-consumer

# 2. Check CronJob schedule
kubectl get cronjob dlq-retry-consumer

# 3. Check last run
kubectl get jobs | grep dlq-retry-consumer

# 4. Check consumer logs
kubectl logs -l app=dlq-retry-consumer --tail=100
```

**Common causes:**
- CronJob suspended
- Configuration error preventing startup
- Kafka connection issues
- Consumer group rebalancing

### Messages Not Being Retried

**Symptoms:**
- Messages stay in DLQ
- Consumer logs show "Skipping" messages

**Checks:**
```bash
# Check message category
kafka-console-consumer --bootstrap-server kafka:9092 \
  --topic financial-data.institution.upserted.enhancement.dlq \
  --max-messages 1 \
  | jq '.error_category'

# Check cooling-off period
kafka-console-consumer --bootstrap-server kafka:9092 \
  --topic financial-data.institution.upserted.enhancement.dlq \
  --max-messages 1 \
  | jq '.next_retry_after'
```

**Common causes:**
- Message marked as permanent (requires manual retry)
- Cooling-off period not elapsed yet
- Retry count exceeded max
- DLQ mapping disabled in config

### High Processing Time

**Symptoms:**
- Consumer takes >5 minutes to process DLQ
- Timeout errors in logs

**Checks:**
```bash
# Check DLQ depth
./scripts/check-dlq-depth.sh

# Check consumer resources
kubectl top pod -l app=dlq-retry-consumer

# Check Kafka lag
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --describe --group dlq-retry-consumer-institution-enhancement
```

**Solutions:**
- Increase consumer resources (CPU/memory)
- Process in batches instead of one-by-one
- Scale horizontally (run multiple consumers)

## Maintenance

### Weekly Tasks

1. Review and acknowledge alerts
2. Check for new error patterns
3. Update configuration if needed
4. Clean up old permanent errors
5. Review retry success rates

### Monthly Tasks

1. Audit DLQ configuration
2. Review and optimize retry strategies
3. Analyze trends in DLQ depth
4. Update runbook with new scenarios
5. Performance tuning based on metrics

### Quarterly Tasks

1. Review architecture and design
2. Evaluate new features and enhancements
3. Disaster recovery testing
4. Capacity planning
5. Security audit

## Emergency Contacts

| Role | Responsibility | Contact |
|------|---------------|---------|
| Platform Engineer | DLQ infrastructure | platform-team@company.com |
| Service Owner | Business logic issues | service-team@company.com |
| SRE On-Call | Production incidents | oncall@company.com |
| Kafka Admin | Kafka cluster issues | kafka-team@company.com |

## References

- [Design Document](./DESIGN.md)
- [Configuration Reference](./CONFIGURATION.md)
- [Troubleshooting Guide](./TROUBLESHOOTING.md)
- [Architecture Diagram](./diagrams/architecture.png)

## Changelog

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-12-30 | 1.0 | Initial runbook | - |
