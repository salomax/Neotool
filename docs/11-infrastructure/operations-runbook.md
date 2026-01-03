---
title: Operations Runbook
type: infrastructure
category: operations
status: current
version: 1.0.0
tags: [operations, runbook, kubectl, debugging, monitoring]
ai_optimized: true
search_keywords: [operations, runbook, kubectl, logs, debugging, port-forward, database]
related:
  - 11-infrastructure/README.md
  - 11-infrastructure/deployment-guide.md
  - 11-infrastructure/troubleshooting-guide.md
  - 10-observability/observability-overview.md
last_updated: 2026-01-02
---

# Operations Runbook

> **Purpose**: Day-to-day operational procedures for managing NeoTool infrastructure, including common tasks, debugging, and routine maintenance.

## Overview

This runbook provides step-by-step procedures for common operational tasks. Use this guide for:
- Viewing logs and debugging
- Managing pods and deployments
- Database access and queries
- Port forwarding and local access
- Scaling and resource management
- Routine maintenance tasks

**Emergency**: For production incidents, see [Incident Response](./incident-response.md).

---

## Quick Command Reference

### Most Used Commands

```bash
# View logs
kubectl logs -f deployment/backend -n neotool-prod --tail=100

# Port forward
kubectl port-forward svc/backend 8080:8080 -n neotool-prod

# Scale deployment
kubectl scale deployment/backend --replicas=5 -n neotool-prod

# Restart deployment
kubectl rollout restart deployment/backend -n neotool-prod

# Exec into pod
kubectl exec -it deployment/backend -n neotool-prod -- /bin/sh

# View pod status
kubectl get pods -n neotool-prod -o wide

# Recent events
kubectl get events -n neotool-prod --sort-by='.lastTimestamp'
```

---

## Viewing Logs

### Application Logs

**Single pod logs**:
```bash
# Tail logs from deployment
kubectl logs -f deployment/backend -n neotool-prod --tail=100

# Logs from specific pod
kubectl logs backend-7d8f9c5b6-xyz12 -n neotool-prod

# Previous container (if pod crashed)
kubectl logs backend-7d8f9c5b6-xyz12 -n neotool-prod --previous

# Logs since timestamp
kubectl logs deployment/backend -n neotool-prod --since=1h

# Logs with timestamps
kubectl logs deployment/backend -n neotool-prod --timestamps
```

**Multi-pod logs with stern**:
```bash
# Install stern
brew install stern

# Tail all backend pods
stern backend -n neotool-prod

# Filter by log level
stern backend -n neotool-prod | grep ERROR

# Multiple deployments
stern 'backend|frontend' -n neotool-prod

# Exclude noise
stern backend -n neotool-prod --exclude='health check'
```

**Logs from all pods in namespace**:
```bash
# All pods in neotool-prod
kubectl logs --all-containers=true --prefix=true -n neotool-prod

# Save logs to file
kubectl logs deployment/backend -n neotool-prod > backend-logs.txt
```

### Infrastructure Logs

**Kubernetes events**:
```bash
# Recent events
kubectl get events -n neotool-prod --sort-by='.lastTimestamp'

# Events for specific pod
kubectl describe pod backend-7d8f9c5b6-xyz12 -n neotool-prod

# Warning events only
kubectl get events -n neotool-prod --field-selector type=Warning
```

**Database logs**:
```bash
# PostgreSQL logs
kubectl logs postgresql-0 -n neotool-data --tail=100

# Slow query log
kubectl exec -it postgresql-0 -n neotool-data -- \
  tail -f /var/log/postgresql/postgresql.log
```

**Ingress logs**:
```bash
# NGINX Ingress Controller logs
kubectl logs -n ingress-nginx deployment/ingress-nginx-controller --tail=100
```

### Log Aggregation (Grafana Loki)

**Access Loki via Grafana**:
1. Open Grafana: `https://grafana.neotool.io`
2. Navigate to Explore → Loki
3. Use LogQL queries

**Example LogQL queries**:
```logql
# All backend logs
{namespace="neotool-prod", app="backend"}

# Error logs only
{namespace="neotool-prod", app="backend"} |= "ERROR"

# Logs containing specific user ID
{namespace="neotool-prod", app="backend"} |= "userId=123"

# Rate of errors per minute
rate({namespace="neotool-prod"} |= "ERROR" [1m])
```

---

## Pod Management

### Viewing Pods

```bash
# List all pods
kubectl get pods -n neotool-prod

# Detailed pod info
kubectl get pods -n neotool-prod -o wide

# Pod resource usage
kubectl top pods -n neotool-prod

# Describe pod (events, status, containers)
kubectl describe pod backend-7d8f9c5b6-xyz12 -n neotool-prod

# Watch pod status
kubectl get pods -n neotool-prod -w
```

### Pod Lifecycle

**Restart pods**:
```bash
# Restart deployment (rolling restart)
kubectl rollout restart deployment/backend -n neotool-prod

# Delete specific pod (will be recreated)
kubectl delete pod backend-7d8f9c5b6-xyz12 -n neotool-prod

# Force delete stuck pod
kubectl delete pod backend-7d8f9c5b6-xyz12 -n neotool-prod --grace-period=0 --force
```

**Execute commands in pod**:
```bash
# Interactive shell
kubectl exec -it deployment/backend -n neotool-prod -- /bin/sh

# Run single command
kubectl exec deployment/backend -n neotool-prod -- env

# Run in specific container (multi-container pod)
kubectl exec -it backend-7d8f9c5b6-xyz12 -c sidecar -n neotool-prod -- /bin/sh
```

**Copy files to/from pod**:
```bash
# Copy from pod
kubectl cp neotool-prod/backend-7d8f9c5b6-xyz12:/app/config.yaml ./config.yaml

# Copy to pod
kubectl cp ./local-file.txt neotool-prod/backend-7d8f9c5b6-xyz12:/tmp/
```

---

## Deployment Management

### Deployment Status

```bash
# List deployments
kubectl get deployments -n neotool-prod

# Deployment details
kubectl describe deployment backend -n neotool-prod

# Rollout status
kubectl rollout status deployment/backend -n neotool-prod

# Rollout history
kubectl rollout history deployment/backend -n neotool-prod

# Revision details
kubectl rollout history deployment/backend --revision=3 -n neotool-prod
```

### Scaling

**Manual scaling**:
```bash
# Scale to 5 replicas
kubectl scale deployment/backend --replicas=5 -n neotool-prod

# Verify scaling
kubectl get deployment backend -n neotool-prod
kubectl get pods -n neotool-prod -l app=backend
```

**Horizontal Pod Autoscaler (HPA)**:
```bash
# Create HPA
kubectl autoscale deployment backend \
  --cpu-percent=70 \
  --min=3 \
  --max=10 \
  -n neotool-prod

# View HPA status
kubectl get hpa -n neotool-prod

# HPA details
kubectl describe hpa backend -n neotool-prod

# Delete HPA
kubectl delete hpa backend -n neotool-prod
```

### Updating Deployments

**Update image**:
```bash
# Set new image
kubectl set image deployment/backend \
  backend=ghcr.io/org/neotool/backend:v1.2.4 \
  -n neotool-prod

# Monitor rollout
kubectl rollout status deployment/backend -n neotool-prod
```

**Update environment variables**:
```bash
# Update env var
kubectl set env deployment/backend LOG_LEVEL=DEBUG -n neotool-prod

# Remove env var
kubectl set env deployment/backend LOG_LEVEL- -n neotool-prod
```

**Pause/Resume rollout**:
```bash
# Pause (for making multiple changes)
kubectl rollout pause deployment/backend -n neotool-prod

# Make changes...
kubectl set image deployment/backend backend=new-image
kubectl set env deployment/backend NEW_VAR=value

# Resume
kubectl rollout resume deployment/backend -n neotool-prod
```

---

## Database Access

### PostgreSQL Access

**Interactive psql session**:
```bash
# Connect to database
kubectl exec -it postgresql-0 -n neotool-data -- \
  psql -U postgres -d neotool

# In psql:
\l                  # List databases
\c neotool          # Connect to database
\dt                 # List tables
\d users            # Describe table
\q                  # Quit
```

**Run SQL queries**:
```bash
# Single query
kubectl exec -it postgresql-0 -n neotool-data -- \
  psql -U postgres -d neotool -c "SELECT COUNT(*) FROM users;"

# From file
kubectl exec -i postgresql-0 -n neotool-data -- \
  psql -U postgres -d neotool < query.sql

# Output to CSV
kubectl exec -it postgresql-0 -n neotool-data -- \
  psql -U postgres -d neotool -c "COPY (SELECT * FROM users) TO STDOUT WITH CSV HEADER;" > users.csv
```

**Database info**:
```bash
# Database size
kubectl exec -it postgresql-0 -n neotool-data -- \
  psql -U postgres -d neotool -c "\l+"

# Table sizes
kubectl exec -it postgresql-0 -n neotool-data -- \
  psql -U postgres -d neotool -c "
    SELECT
      schemaname,
      tablename,
      pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
    FROM pg_tables
    WHERE schemaname = 'public'
    ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;"

# Active connections
kubectl exec -it postgresql-0 -n neotool-data -- \
  psql -U postgres -d neotool -c "SELECT * FROM pg_stat_activity;"
```

### Database Backups

**Manual backup**:
```bash
# Full database dump
kubectl exec -it postgresql-0 -n neotool-data -- \
  pg_dump -U postgres neotool > backup-$(date +%Y%m%d-%H%M%S).sql

# Compressed backup
kubectl exec -it postgresql-0 -n neotool-data -- \
  pg_dump -U postgres neotool | gzip > backup-$(date +%Y%m%d-%H%M%S).sql.gz

# Schema only
kubectl exec -it postgresql-0 -n neotool-data -- \
  pg_dump -U postgres -s neotool > schema-$(date +%Y%m%d).sql

# Specific table
kubectl exec -it postgresql-0 -n neotool-data -- \
  pg_dump -U postgres -t users neotool > users-backup.sql
```

**Restore from backup**:
```bash
# Restore full database
kubectl exec -i postgresql-0 -n neotool-data -- \
  psql -U postgres neotool < backup.sql

# Restore compressed backup
gunzip -c backup.sql.gz | kubectl exec -i postgresql-0 -n neotool-data -- \
  psql -U postgres neotool
```

**See**: [Disaster Recovery](./disaster-recovery.md) for automated backup procedures.

---

## Port Forwarding

### Service Port Forwarding

**Access services locally**:
```bash
# Backend service
kubectl port-forward svc/backend 8080:8080 -n neotool-prod
# Access: http://localhost:8080

# Frontend service
kubectl port-forward svc/frontend 3000:3000 -n neotool-prod
# Access: http://localhost:3000

# PostgreSQL database
kubectl port-forward svc/postgresql 5432:5432 -n neotool-data
# Connect: psql -h localhost -U postgres -d neotool

# GraphQL Playground
kubectl port-forward svc/apollo-router 4000:4000 -n neotool-prod
# Access: http://localhost:4000/graphql
```

**Background port forwarding**:
```bash
# Run in background
kubectl port-forward svc/backend 8080:8080 -n neotool-prod &

# List background jobs
jobs

# Kill background port-forward
kill %1  # Job number from jobs command

# Or kill by port
lsof -ti:8080 | xargs kill
```

**Port forwarding to pod**:
```bash
# Specific pod
kubectl port-forward backend-7d8f9c5b6-xyz12 8080:8080 -n neotool-prod

# Any pod in deployment
kubectl port-forward deployment/backend 8080:8080 -n neotool-prod
```

### Multi-Port Forwarding

**Forward multiple ports**:
```bash
# Backend + database
kubectl port-forward svc/backend 8080:8080 -n neotool-prod &
kubectl port-forward svc/postgresql 5432:5432 -n neotool-data &

# Grafana + Prometheus
kubectl port-forward -n observability svc/grafana 3001:3000 &
kubectl port-forward -n observability svc/prometheus 9090:9090 &
```

---

## ConfigMaps and Secrets

### ConfigMaps

**View ConfigMaps**:
```bash
# List ConfigMaps
kubectl get configmaps -n neotool-prod

# View ConfigMap contents
kubectl describe configmap app-config -n neotool-prod

# Get as YAML
kubectl get configmap app-config -n neotool-prod -o yaml

# Get specific key
kubectl get configmap app-config -n neotool-prod -o jsonpath='{.data.config\.yaml}'
```

**Update ConfigMap**:
```bash
# Edit interactively
kubectl edit configmap app-config -n neotool-prod

# Update from file
kubectl create configmap app-config --from-file=config.yaml -n neotool-prod --dry-run=client -o yaml | kubectl apply -f -

# After updating, restart pods
kubectl rollout restart deployment/backend -n neotool-prod
```

### Secrets

**View Secrets** (be careful with sensitive data):
```bash
# List secrets
kubectl get secrets -n neotool-prod

# Secret details (values are base64 encoded)
kubectl describe secret database-credentials -n neotool-prod

# Decode secret value
kubectl get secret database-credentials -n neotool-prod -o jsonpath='{.data.password}' | base64 -d
```

**Update Secret**:
```bash
# Create/update from literal
kubectl create secret generic database-credentials \
  --from-literal=username=postgres \
  --from-literal=password=newpassword \
  -n neotool-prod \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart pods to pick up new secret
kubectl rollout restart deployment/backend -n neotool-prod
```

**Rotate JWT Secret**:
```bash
# Generate new secret
NEW_SECRET=$(openssl rand -base64 32)

# Update Kubernetes secret
kubectl create secret generic jwt-secret \
  --from-literal=secret=$NEW_SECRET \
  -n neotool-prod \
  --dry-run=client -o yaml | kubectl apply -f -

# Rolling restart (graceful token transition)
kubectl rollout restart deployment/security-service -n neotool-prod
```

---

## Monitoring & Metrics

### Resource Usage

**Node metrics**:
```bash
# Node resource usage
kubectl top nodes

# Detailed node info
kubectl describe node <node-name>
```

**Pod metrics**:
```bash
# Pod CPU/Memory usage
kubectl top pods -n neotool-prod

# Sort by CPU
kubectl top pods -n neotool-prod --sort-by=cpu

# Sort by memory
kubectl top pods -n neotool-prod --sort-by=memory

# Container-level metrics
kubectl top pods -n neotool-prod --containers
```

### Grafana Dashboards

**Access Grafana**:
```bash
# Port forward Grafana
kubectl port-forward -n observability svc/grafana 3001:3000

# Open: http://localhost:3001
# Default: admin/admin (change on first login)
```

**Key Dashboards**:
- **NeoTool Overview**: Application health, RPS, errors, latency
- **Kubernetes Cluster**: Node health, pod status, resource usage
- **PostgreSQL**: Connections, queries, cache hit ratio
- **GraphQL**: Query performance, resolver metrics
- **JVM Metrics**: Heap, GC, threads (backend)

### Prometheus Queries

**Access Prometheus**:
```bash
kubectl port-forward -n observability svc/prometheus 9090:9090
# Open: http://localhost:9090
```

**Example PromQL queries**:
```promql
# Request rate (per second)
rate(http_requests_total[5m])

# Error rate
rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m])

# 95th percentile latency
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))

# Memory usage by pod
container_memory_usage_bytes{namespace="neotool-prod", pod=~"backend.*"}

# CPU usage by pod
rate(container_cpu_usage_seconds_total{namespace="neotool-prod", pod=~"backend.*"}[5m])
```

---

## Troubleshooting

### Pod Not Starting

**Diagnosis**:
```bash
# Check pod status
kubectl get pod backend-7d8f9c5b6-xyz12 -n neotool-prod

# Describe pod for events
kubectl describe pod backend-7d8f9c5b6-xyz12 -n neotool-prod

# Check logs
kubectl logs backend-7d8f9c5b6-xyz12 -n neotool-prod

# Previous container logs (if crashed)
kubectl logs backend-7d8f9c5b6-xyz12 -n neotool-prod --previous
```

**Common Issues**:

1. **Image pull error**:
   ```bash
   # Check imagePullSecrets
   kubectl get pod backend-7d8f9c5b6-xyz12 -n neotool-prod -o yaml | grep imagePullSecrets

   # Verify secret exists
   kubectl get secret ghcr-secret -n neotool-prod
   ```

2. **CrashLoopBackOff**:
   ```bash
   # View crash logs
   kubectl logs backend-7d8f9c5b6-xyz12 -n neotool-prod --previous

   # Check liveness/readiness probes
   kubectl describe pod backend-7d8f9c5b6-xyz12 -n neotool-prod | grep -A 10 Liveness
   ```

3. **Resource limits**:
   ```bash
   # Check resource requests/limits
   kubectl describe pod backend-7d8f9c5b6-xyz12 -n neotool-prod | grep -A 5 Limits

   # View node capacity
   kubectl describe node <node-name>
   ```

### Database Connection Issues

**Check connectivity**:
```bash
# Test from backend pod
kubectl exec -it deployment/backend -n neotool-prod -- \
  nc -zv postgresql.neotool-data.svc.cluster.local 5432

# Check DATABASE_URL secret
kubectl get secret database-credentials -n neotool-prod -o jsonpath='{.data.url}' | base64 -d

# Verify PostgreSQL is running
kubectl get pods -n neotool-data
kubectl logs postgresql-0 -n neotool-data
```

**Connection pool issues**:
```bash
# Check active connections
kubectl exec -it postgresql-0 -n neotool-data -- \
  psql -U postgres -d neotool -c "SELECT count(*) FROM pg_stat_activity;"

# Max connections
kubectl exec -it postgresql-0 -n neotool-data -- \
  psql -U postgres -c "SHOW max_connections;"
```

### High CPU/Memory Usage

**Identify resource-heavy pods**:
```bash
# Top CPU consumers
kubectl top pods -n neotool-prod --sort-by=cpu

# Top memory consumers
kubectl top pods -n neotool-prod --sort-by=memory

# Resource limits
kubectl describe pod backend-7d8f9c5b6-xyz12 -n neotool-prod | grep -A 10 Limits
```

**Take heap dump (JVM)**:
```bash
# Exec into pod
kubectl exec -it deployment/backend -n neotool-prod -- /bin/sh

# Inside pod: Generate heap dump
jmap -dump:live,format=b,file=/tmp/heap.hprof $(pgrep java)

# Copy heap dump locally
kubectl cp neotool-prod/backend-7d8f9c5b6-xyz12:/tmp/heap.hprof ./heap.hprof

# Analyze with VisualVM, Eclipse MAT, or JProfiler
```

**See**: [Troubleshooting Guide](./troubleshooting-guide.md) for detailed procedures.

---

## Maintenance Tasks

### Certificate Renewal

**Check certificate expiry**:
```bash
# TLS secret
kubectl get secret tls-cert -n neotool-prod -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -noout -dates

# Cert-manager certificates
kubectl get certificates -n neotool-prod
kubectl describe certificate neotool-tls -n neotool-prod
```

**Manual renewal** (if not using cert-manager):
```bash
# Update secret with new cert
kubectl create secret tls tls-cert \
  --cert=new-cert.pem \
  --key=new-key.pem \
  -n neotool-prod \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart ingress controller
kubectl rollout restart deployment/ingress-nginx-controller -n ingress-nginx
```

### Cleanup

**Delete old ReplicaSets**:
```bash
# List old ReplicaSets
kubectl get replicasets -n neotool-prod

# Delete ReplicaSets with 0 desired/current replicas
kubectl delete replicaset $(kubectl get replicaset -n neotool-prod -o jsonpath='{.items[?(@.spec.replicas==0)].metadata.name}') -n neotool-prod
```

**Clean up completed jobs**:
```bash
# Delete completed jobs
kubectl delete jobs --field-selector status.successful=1 -n neotool-prod

# Delete failed jobs
kubectl delete jobs --field-selector status.failed=1 -n neotool-prod
```

**Prune unused images** (on nodes):
```bash
# SSH to node (if accessible)
docker image prune -a --filter "until=168h"  # Older than 7 days
```

---

## Related Documentation

- [Infrastructure Overview](./README.md)
- [Deployment Guide](./deployment-guide.md)
- [Troubleshooting Guide](./troubleshooting-guide.md)
- [Disaster Recovery](./disaster-recovery.md)
- [Monitoring Setup](./monitoring-setup.md)

---

**Version**: 1.0.0 (2026-01-02)
**Review Frequency**: Monthly
**On-Call Contact**: See internal wiki

*Run operations smoothly. Debug efficiently. Maintain proactively.*
