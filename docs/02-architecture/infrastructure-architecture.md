---
title: Infrastructure Architecture
type: architecture
category: infrastructure
status: current
version: 1.0.0
tags: [architecture, infrastructure, docker, kubernetes, terraform, cicd, deployment]
ai_optimized: true
search_keywords: [infrastructure, docker, kubernetes, k8s, terraform, deployment, cicd, github-actions]
related:
  - 02-architecture/README.md
  - 02-architecture/system-architecture.md
  - 08-workflows/deployment-workflow.md
  - 92-adr/0002-containerized-architecture.md
last_updated: 2026-01-02
---

# Infrastructure Architecture

> **Purpose**: Deep-dive into containerization (Docker), orchestration (Kubernetes), Infrastructure as Code (Terraform), CI/CD pipelines (GitHub Actions), and deployment strategies.

## Overview

NeoTool follows **cloud-native** principles with **Docker** containers orchestrated by **Kubernetes**, infrastructure managed with **Terraform**, and deployments automated through **GitHub Actions** CI/CD pipelines.

### Key Characteristics

- **Containerized**: All services packaged as Docker images
- **Kubernetes**: Production orchestration with auto-scaling
- **Infrastructure as Code**: Terraform for reproducible infrastructure
- **CI/CD**: GitHub Actions for automated build, test, deploy
- **Multi-Environment**: Dev, staging, production with environment parity
- **Vendor Neutral**: Portable across cloud providers
- **Observable**: Prometheus, Grafana, Loki built-in

**See**: [ADR-0002: Containerized Architecture](../92-adr/0002-containerized-architecture.md)

---

## Containerization Strategy

### Docker Architecture

```mermaid
graph TB
    subgraph Services["Service Images"]
        Frontend[neotool/frontend<br/>Next.js]
        Backend[neotool/backend<br/>Kotlin + Micronaut]
        Router[neotool/router<br/>Apollo Router]
    end

    subgraph Base["Base Images"]
        NodeBase[node:20-alpine]
        JVMBase[eclipse-temurin:21-jre-alpine]
        RouterBase[ghcr.io/apollographql/router]
    end

    subgraph Registry["Container Registry"]
        GHCR[GitHub Container Registry<br/>ghcr.io/org/neotool]
    end

    Frontend -.->|FROM| NodeBase
    Backend -.->|FROM| JVMBase
    Router -.->|FROM| RouterBase

    Frontend -->|Push| GHCR
    Backend -->|Push| GHCR
    Router -->|Push| GHCR

    style Frontend fill:#61dafb
    style Backend fill:#7f52ff
    style Router fill:#311c87
    style GHCR fill:#4caf50
```

### Dockerfile Examples

**Backend Service** (Kotlin + Micronaut):
```dockerfile
# Multi-stage build for smaller images
FROM gradle:8-jdk21-alpine AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/app-service-*.jar app.jar

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Frontend** (Next.js):
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 3000
ENV NODE_ENV=production
CMD ["node", "server.js"]
```

**Benefits of Multi-Stage Builds**:
- ✅ Smaller final images (no build tools)
- ✅ Faster deployments
- ✅ Better security (minimal attack surface)

---

## Local Development Environment

### Docker Compose

```yaml
# infra/docker/docker-compose.local.yml
version: '3.8'

services:
  postgres:
    image: postgres:18-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: neotool_dev
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3001:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
    depends_on:
      - prometheus

  loki:
    image: grafana/loki:latest
    ports:
      - "3100:3100"
    volumes:
      - ./loki-config.yml:/etc/loki/local-config.yaml
      - loki_data:/loki
    command: -config.file=/etc/loki/local-config.yaml

volumes:
  postgres_data:
  prometheus_data:
  grafana_data:
  loki_data:
```

**Commands**:
```bash
# Start infrastructure
docker-compose -f infra/docker/docker-compose.local.yml up -d

# View logs
docker-compose logs -f postgres

# Stop and clean up
docker-compose down -v
```

**Native Processes** (for hot reload):
- Backend: `./gradlew run` (port 8080)
- Frontend: `npm run dev` (port 3000)
- Apollo Router: `./router` (port 4000)

**See**: [Getting Started](../01-overview/getting-started.md)

---

## Kubernetes Architecture

### Cluster Topology

```mermaid
graph TB
    subgraph Internet["External Traffic"]
        Users[Users]
        DNS[DNS]
    end

    subgraph K8sCluster["Kubernetes Cluster"]
        subgraph Ingress["Ingress Layer"]
            IngressCtrl[Ingress Controller<br/>NGINX]
            LB[LoadBalancer Service]
        end

        subgraph AppNS["Namespace: neotool-prod"]
            FrontendDep[Deployment: frontend<br/>Replicas: 3]
            RouterDep[Deployment: router<br/>Replicas: 2]
            BackendDep[Deployment: backend<br/>Replicas: 3]

            FrontendSvc[Service: frontend]
            RouterSvc[Service: router]
            BackendSvc[Service: backend]

            HPA[HorizontalPodAutoscaler<br/>CPU/Memory based]
        end

        subgraph DataNS["Namespace: neotool-data"]
            PostgreSQLSS[StatefulSet: postgresql<br/>Primary + Replicas]
            PVC[PersistentVolumeClaim]
        end

        subgraph ObsNS["Namespace: observability"]
            PrometheusSS[StatefulSet: prometheus]
            GrafanaDep[Deployment: grafana]
            LokiSS[StatefulSet: loki]
        end
    end

    Users --> DNS
    DNS --> LB
    LB --> IngressCtrl
    IngressCtrl --> FrontendSvc
    IngressCtrl --> RouterSvc
    FrontendSvc --> FrontendDep
    RouterSvc --> RouterDep
    RouterDep --> BackendSvc
    BackendSvc --> BackendDep
    BackendDep --> PostgreSQLSS
    PostgreSQLSS --> PVC

    HPA -.->|Scales| FrontendDep
    HPA -.->|Scales| BackendDep

    BackendDep -.->|Metrics| PrometheusSS
    PrometheusSS --> GrafanaDep
    LokiSS --> GrafanaDep

    style Users fill:#90caf9
    style IngressCtrl fill:#f44336
    style FrontendDep fill:#61dafb
    style RouterDep fill:#311c87
    style BackendDep fill:#7f52ff
    style PostgreSQLSS fill:#336791
    style PrometheusSS fill:#e6522c
```

### Kubernetes Resources

**Deployment** (stateless services):
```yaml
# infra/k8s/base/backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
  namespace: neotool-prod
spec:
  replicas: 3
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
      - name: backend
        image: ghcr.io/org/neotool/backend:latest
        ports:
        - containerPort: 8080
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: url
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: jwt-secret
              key: secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health/readiness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

**Service** (load balancing):
```yaml
# infra/k8s/base/backend-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: backend
  namespace: neotool-prod
spec:
  type: ClusterIP
  selector:
    app: backend
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
```

**HorizontalPodAutoscaler** (auto-scaling):
```yaml
# infra/k8s/base/backend-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-hpa
  namespace: neotool-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**StatefulSet** (stateful services like databases):
```yaml
# infra/k8s/base/postgresql-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgresql
  namespace: neotool-data
spec:
  serviceName: postgresql
  replicas: 1
  selector:
    matchLabels:
      app: postgresql
  template:
    metadata:
      labels:
        app: postgresql
    spec:
      containers:
      - name: postgresql
        image: postgres:18-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 100Gi
```

---

## Infrastructure as Code (Terraform)

### Terraform Structure

```
infra/terraform/
├── modules/                    # Reusable modules
│   ├── kubernetes/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── database/
│   └── networking/
│
├── environments/               # Environment configs
│   ├── dev/
│   │   ├── main.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf
│   ├── staging/
│   └── prod/
│
└── global/                     # Shared resources
    └── dns/
```

### Example Module

**Kubernetes Cluster** (simplified):
```hcl
# modules/kubernetes/main.tf
resource "google_container_cluster" "primary" {
  name     = var.cluster_name
  location = var.region

  initial_node_count = 1
  remove_default_node_pool = true

  network    = var.network
  subnetwork = var.subnetwork

  master_auth {
    client_certificate_config {
      issue_client_certificate = false
    }
  }
}

resource "google_container_node_pool" "primary_nodes" {
  name       = "${var.cluster_name}-node-pool"
  location   = var.region
  cluster    = google_container_cluster.primary.name
  node_count = var.node_count

  node_config {
    preemptible  = var.preemptible
    machine_type = var.machine_type

    metadata = {
      disable-legacy-endpoints = "true"
    }

    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
  }

  autoscaling {
    min_node_count = var.min_node_count
    max_node_count = var.max_node_count
  }
}
```

**Environment Configuration**:
```hcl
# environments/prod/main.tf
module "kubernetes" {
  source = "../../modules/kubernetes"

  cluster_name    = "neotool-prod"
  region          = "us-central1"
  node_count      = 3
  min_node_count  = 3
  max_node_count  = 10
  machine_type    = "n1-standard-4"
  preemptible     = false
}
```

**Commands**:
```bash
cd infra/terraform/environments/prod

# Initialize
terraform init

# Plan changes
terraform plan

# Apply changes
terraform apply

# Destroy (careful!)
terraform destroy
```

---

## CI/CD Pipeline

### GitHub Actions Workflow

```yaml
# .github/workflows/deploy.yml
name: Build, Test, and Deploy

on:
  push:
    branches: [main, staging]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  test-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run tests
        run: ./gradlew test
        working-directory: service/kotlin

      - name: Upload coverage
        uses: codecov/codecov-action@v3

  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install dependencies
        run: npm ci
        working-directory: web

      - name: Run tests
        run: npm test
        working-directory: web

  build-and-push:
    needs: [test-backend, test-frontend]
    runs-on: ubuntu-latest
    if: github.event_name == 'push'
    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Log in to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/backend
          tags: |
            type=sha,prefix={{branch}}-
            type=ref,event=branch
            type=semver,pattern={{version}}

      - name: Build and push backend
        uses: docker/build-push-action@v5
        with:
          context: service/kotlin
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy-production:
    needs: build-and-push
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    environment: production

    steps:
      - uses: actions/checkout@v4

      - name: Set up kubectl
        uses: azure/setup-kubectl@v3

      - name: Configure kubectl
        run: |
          echo "${{ secrets.KUBE_CONFIG }}" | base64 -d > kubeconfig
          export KUBECONFIG=kubeconfig

      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/backend \
            backend=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/backend:main-${{ github.sha }} \
            -n neotool-prod

      - name: Verify deployment
        run: |
          kubectl rollout status deployment/backend -n neotool-prod
```

### Deployment Strategy

**Rolling Update** (default):
```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1           # Max pods above desired
      maxUnavailable: 0     # Always maintain availability
```

**Blue-Green Deployment** (manual):
1. Deploy new version (green) alongside old (blue)
2. Test green deployment
3. Switch traffic to green
4. Remove blue deployment

**Canary Deployment** (planned):
1. Deploy new version to small % of pods
2. Monitor metrics
3. Gradually increase traffic
4. Full rollout or rollback

---

## Environment Management

### Environment Parity

| Aspect | Development | Staging | Production |
|--------|------------|---------|------------|
| **Infrastructure** | Docker Compose | Kubernetes (small) | Kubernetes (full) |
| **Database** | PostgreSQL 18 (local) | PostgreSQL 18 (cloud) | PostgreSQL 18 (cloud) |
| **Replicas** | 1 | 2 | 3+ |
| **Auto-scaling** | No | Yes (limited) | Yes (full) |
| **Monitoring** | Basic | Full | Full + Alerts |
| **Secrets** | `.env` file | Kubernetes Secrets | Kubernetes Secrets |

### Configuration Management

**Environment Variables**:
```yaml
# Kubernetes Secret
apiVersion: v1
kind: Secret
metadata:
  name: app-secrets
  namespace: neotool-prod
type: Opaque
stringData:
  DATABASE_URL: postgresql://user:pass@host:5432/db
  JWT_SECRET: super-secret-key
  GRAPHQL_ENDPOINT: https://api.neotool.io/graphql
```

**ConfigMap** (non-sensitive):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: neotool-prod
data:
  LOG_LEVEL: "INFO"
  MAX_CONNECTIONS: "50"
```

---

## Monitoring & Observability

### Observability Stack

```mermaid
graph TB
    subgraph Apps["Applications"]
        Frontend[Frontend]
        Backend[Backend]
        Router[Router]
    end

    subgraph Collection["Collection"]
        Promtail[Promtail<br/>Log Collector]
    end

    subgraph Storage["Storage"]
        Prometheus[Prometheus<br/>Metrics DB]
        Loki[Loki<br/>Log DB]
    end

    subgraph Visualization["Visualization"]
        Grafana[Grafana<br/>Dashboards]
    end

    Frontend -.->|Logs| Promtail
    Backend -.->|Metrics| Prometheus
    Backend -.->|Logs| Promtail
    Router -.->|Metrics| Prometheus

    Promtail --> Loki
    Prometheus --> Grafana
    Loki --> Grafana

    style Frontend fill:#61dafb
    style Backend fill:#7f52ff
    style Router fill:#311c87
    style Prometheus fill:#e6522c
    style Loki fill:#f5c542
    style Grafana fill:#f46800
```

**See**: [Observability Overview](../10-observability/observability-overview.md)

---

## Security

### Security Best Practices

**Container Security**:
- ✅ Non-root user in containers
- ✅ Minimal base images (alpine)
- ✅ Multi-stage builds (no build tools in final image)
- ✅ Regular image scanning (Trivy, Snyk)
- ✅ No secrets in images (use Kubernetes Secrets)

**Kubernetes Security**:
- ✅ NetworkPolicies (restrict pod-to-pod communication)
- ✅ RBAC (role-based access control)
- ✅ Pod Security Standards
- ✅ Secrets encryption at rest
- ✅ Regular updates (CVE patching)

**Network Security**:
- ✅ TLS/HTTPS everywhere
- ✅ Ingress with SSL termination
- ✅ Internal service-to-service encryption (mTLS planned)

---

## Related Documentation

### Architecture
- [Architecture Hub](./README.md)
- [System Architecture](./system-architecture.md)
- [Service Architecture](./service-architecture.md)

### Operations
- [Deployment Workflow](../08-workflows/deployment-workflow.md)
- [Observability Overview](../10-observability/observability-overview.md)
- [Commands Reference](../93-reference/commands.md)

### Decisions
- [ADR-0002: Containerized Architecture](../92-adr/0002-containerized-architecture.md)

---

**Version**: 1.0.0 (2026-01-02)
**Stack**: Docker + Kubernetes + Terraform + GitHub Actions
**Philosophy**: Infrastructure as Code. Automated deployment. Observable and scalable.

*Build infrastructure that is reproducible, scalable, and resilient.*
