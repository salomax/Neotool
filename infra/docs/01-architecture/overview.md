# Neotool Infrastructure Architecture Overview

## System Architecture

Neotool is a microservices platform deployed on Kubernetes (K3S) with the following components:

### Application Layer

- **Kotlin Services**: 4 microservices (app, security, assistant, assets)
- **Frontend**: Next.js web application
- **Gateway**: Apollo Router (GraphQL Federation)

### Data Layer

- **PostgreSQL 18rc1**: Primary database
- **PgBouncer**: Connection pooling
- **Kafka 3.7.0**: Message broker (KRaft mode)

### Storage

- **Development**: MinIO (S3-compatible)
- **Production**: Cloudflare R2

### Security

- **HashiCorp Vault 1.21.1**: JWT key management and secrets
- **JWT Authentication**: RS256 with 4096-bit RSA keys

### Observability

- **Prometheus v2.55.1**: Metrics collection
- **Grafana 11.1.4**: Visualization and dashboards
- **Loki 2.9.0**: Log aggregation
- **Promtail 2.9.0**: Log collection

## Component Relationships

```
┌─────────────┐
│   Next.js   │
│  Frontend   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Apollo    │
│   Router    │
└──────┬──────┘
       │
   ┌───┴───┬──────────┬──────────┐
   │       │          │          │
   ▼       ▼          ▼          ▼
┌─────┐ ┌──────┐  ┌────────┐ ┌───────┐
│ App │ │Security│ │Assistant│ │Assets │
└──┬──┘ └───┬──┘  └────┬───┘ └───┬────┘
   │        │          │         │
   └────────┴──────────┴─────────┘
            │
    ┌───────┴────────┐
    │                │
    ▼                ▼
┌─────────┐    ┌──────────┐
│PostgreSQL│    │  Kafka   │
│PgBouncer │    │          │
└─────────┘    └──────────┘
```

## Namespace Organization

- **neotool-app**: Application services
- **neotool-web**: Frontend and gateway
- **neotool-data**: PostgreSQL and PgBouncer
- **neotool-messaging**: Kafka
- **neotool-storage**: MinIO
- **neotool-security**: Vault
- **neotool-workflows**: CronJobs
- **neotool-observability**: Prometheus, Grafana, Loki

## Deployment Environments

### Local Development

- Single-node K3S cluster
- Local-path storage
- Reduced resource limits
- MinIO for storage

### Production

- Multi-node K3S cluster in VPC
- 8 CPU / 32GB RAM per node
- Cloudflare R2 storage
- High availability configuration
- Production resource limits

## Infrastructure as Code

### Terraform Modules

- **K3S Module**: Cluster provisioning
- **Networking Module**: VPC and networking (AWS/GCP/Azure)
- **Storage Module**: Storage classes and Cloudflare R2

### Kubernetes Manifests

- **Base Manifests**: Kustomize base configurations
- **Overlays**: Environment-specific patches (local/prod)

### Helm Charts

- **neotool-service**: Reusable chart for Kotlin services
- Includes Vault init container pattern
- HPA and ServiceMonitor support

## Security Architecture

### JWT Key Management

1. Vault init job generates RSA keys
2. Keys stored in Vault KV v2
3. Services read keys via KeyManager
4. No key rotation initially (manual process documented)

### Network Security

- Services communicate via Kubernetes DNS
- Network policies (to be implemented)
- Private subnets in production
- Security groups/firewall rules

## Observability Stack

### Metrics

- Prometheus scrapes all services
- ServiceMonitor CRDs for auto-discovery
- Grafana dashboards for visualization

### Logs

- Promtail collects logs from all pods
- Loki aggregates and stores logs
- Grafana for log querying

## Storage Architecture

### Development

- Local-path storage class (K3S default)
- MinIO for S3-compatible storage

### Production

- Cloudflare R2 storage class
- S3-compatible API
- Persistent volumes for stateful services

## Deployment Flow

1. **Infrastructure**: Terraform provisions K3S cluster
2. **Base Manifests**: Kubernetes resources deployed
3. **Environment Patches**: Kustomize applies environment-specific configs
4. **Services**: Helm charts deploy application services
5. **Verification**: Health checks and smoke tests

## Scaling Strategy

### Horizontal Pod Autoscaling (HPA)

- CPU utilization: 80%
- Memory utilization: 80%
- Min replicas: 2 (local), 5 (production)
- Max replicas: 10 (local), 50 (production)

### Manual Scaling

```bash
kubectl scale deployment <name> --replicas=<count> -n <namespace>
```

## Backup and Recovery

### Database

- PostgreSQL persistent volumes
- Regular backup procedures (to be implemented)

### Vault

- Dev mode: In-memory (no persistence)
- Production: Raft storage with backups

## Future Enhancements

- Network policies for pod-to-pod communication
- Service mesh (Istio/Linkerd)
- Advanced monitoring and alerting
- Automated backup procedures
- Multi-region deployment
- Disaster recovery procedures

