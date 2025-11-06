# ADR-0002: Containerized Architecture (vs Serverless)

## Status
Accepted

## Context
NeoTool needs to choose between containerized deployment (Docker/Kubernetes) and serverless deployment (AWS Lambda, GCP Cloud Functions, Azure Functions) as the primary deployment strategy.

The project aims to be a **modular full-stack boilerplate** that works across different environments and cloud providers while maintaining simplicity and developer velocity.

Key considerations:
- Portability across cloud providers
- Local development experience
- Operational complexity
- Cost predictability
- Performance characteristics
- Vendor lock-in risks

## Decision
We will use a **containerized architecture** with Docker and Kubernetes as the primary deployment strategy, avoiding serverless as the default approach.

### Architecture Components
- **Local Development**: Docker Compose for local development
- **Production**: Kubernetes with GitOps (ArgoCD)
- **Observability**: Prometheus, Grafana, Loki stack
- **Container Runtime**: Docker containers for all services

## Consequences

### Positive
- **Portability & vendor freedom**: Avoids cloud provider lock-in, enables multi-cloud strategies
- **Consistent environments**: Same container images run locally and in production
- **Predictable costs**: Container billing is simpler and more predictable than per-invocation pricing
- **Network control**: Easier access to VPCs, databases, and internal APIs
- **First-class observability**: Prometheus, Grafana, Loki work out of the box
- **Solid patterns**: 12-factor apps, stateless containers, environment configs, health/readiness probes
- **Future-ready**: Micronaut supports low cold-start latency; selective serverless migration possible later

### Negative
- **Auto-scaling per request**: FaaS can scale instantly per event; K8s requires HPA/VPA fine-tuning
- **Zero idle cost**: Functions don't consume resources when idle; containers always reserve some resources
- **Managed operations**: In FaaS, OS/runtime patching is invisible; in K8s you own the base image lifecycle
- **Time-to-first-request**: FaaS can have cold starts; warm containers are more predictable but still consume resources

### Risks
- **Resource overhead**: Containers always consume some resources even when idle
- **Operational complexity**: More infrastructure to manage compared to serverless
- **Scaling complexity**: Need to configure and tune auto-scaling policies

### Mitigation Strategies
- **Efficient base images**: Use minimal base images and optimize container size
- **Horizontal Pod Autoscaler (HPA)**: Configure proper scaling policies
- **Resource limits**: Set appropriate CPU and memory limits
- **Health checks**: Implement proper liveness and readiness probes

## Alternatives Considered

### Serverless-first approach
- **Pros**: Auto-scaling, zero idle cost, managed operations
- **Cons**: Vendor lock-in, complex local development, unpredictable costs for steady workloads, network limitations

### Serverless containers (Cloud Run, App Runner)
- **Pros**: Keep Docker artifacts but gain aggressive auto-scaling and low idle cost
- **Cons**: Still some vendor lock-in, limited control over runtime environment

### Hybrid approach
- **Pros**: Use containers for core services, serverless for event-driven workloads
- **Cons**: Increased complexity, inconsistent patterns across the application

## When to Reconsider Serverless
- **Event-driven jobs**: Isolated pipelines, short tasks, or lightweight workers triggered by events
- **Spiky workloads**: Endpoints with unpredictable traffic that tolerate cold starts
- **ETL/batch triggers**: Short jobs launched by uploads, queues, or scheduled triggers
- **Glue logic**: Small connectors between SaaS services with minimal state

## Decision Drivers
- NeoTool's goal of being vendor-neutral and portable
- Need for consistent local and production environments
- Focus on developer velocity and operational simplicity
- Desire to avoid vendor lock-in
- Preference for predictable costs over per-invocation pricing
- Need for tight integration with databases and internal services

## Implementation Notes
- All services are containerized using Docker
- Local development uses Docker Compose
- Production deployment uses Kubernetes with Kustomize
- GitOps workflow with ArgoCD for deployment automation
- Observability stack (Prometheus, Grafana, Loki) is containerized and included
