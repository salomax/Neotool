---
title: Architecture Documentation Hub
type: architecture
category: navigation
status: current
version: 3.0.0
tags: [architecture, navigation, index, system-design]
ai_optimized: true
search_keywords: [architecture, system-design, infrastructure, services, api, data, frontend]
related:
  - 01-overview/architecture-at-a-glance.md
  - 01-overview/core-principles.md
  - 92-adr/0001-monorepo-architecture.md
  - 92-adr/0002-containerized-architecture.md
  - 92-adr/0003-kotlin-micronaut-backend.md
last_updated: 2026-01-02
---

# Architecture Documentation Hub

> **Purpose**: Navigate NeoTool's complete architecture documentation - from system overview to detailed service, data, API, frontend, and infrastructure design.

## Overview

This section provides **deep-dive architectural documentation** for all aspects of the NeoTool platform. Each document focuses on a specific architectural domain with detailed diagrams, patterns, and implementation guidance.

### Architecture Philosophy

NeoTool's architecture follows these guiding principles:

1. **Type-Safe End-to-End**: GraphQL schema → TypeScript + Kotlin types → Database
2. **Federated Services**: Independent microservices with composed schema
3. **Cloud-Native**: Containerized, observable, scalable from day one
4. **Vendor Neutral**: Portable across clouds, no lock-in
5. **Domain-Driven**: Code reflects business concepts

**See**: [Core Principles](../01-overview/core-principles.md) for complete philosophy.

---

## Architecture Documents

### 1. [System Architecture](./system-architecture.md)
**High-level system design and technology stack**

**What's Inside**:
- Complete system diagram (Clients → Apollo Router → Services → PostgreSQL 18+)
- Technology stack rationale
- Data flow patterns (read/write)
- Deployment architecture (local + production)
- Observability stack
- Security overview

**When to Read**:
- ✅ First time understanding the system
- ✅ Explaining architecture to stakeholders
- ✅ Onboarding new team members
- ✅ Making technology decisions

**Related**:
- [Architecture at a Glance](../01-overview/architecture-at-a-glance.md) - 5-minute overview
- [ADR-0001: Monorepo Architecture](../92-adr/0001-monorepo-architecture.md)

---

### 2. [Service Architecture](./service-architecture.md)
**Microservice boundaries, communication, and inter-service patterns**

**What's Inside**:
- Service decomposition strategy
- Service communication patterns (GraphQL Federation)
- Inter-service security (JWT propagation, service auth)
- Service discovery and routing
- Shared libraries and code organization
- Service lifecycle and deployment

**When to Read**:
- ✅ Designing a new microservice
- ✅ Understanding service boundaries
- ✅ Implementing inter-service communication
- ✅ Troubleshooting service integration

**Related**:
- [ADR-0003: Kotlin/Micronaut Backend](../92-adr/0003-kotlin-micronaut-backend.md)
- [Backend Patterns](../05-backend/patterns/)

---

### 3. [Data Architecture](./data-architecture.md)
**PostgreSQL 18+ setup, database patterns, and data management**

**What's Inside**:
- PostgreSQL 18+ configuration and features
- Database-per-service pattern
- Connection pooling (HikariCP)
- Caching strategies
- Replication and backup
- Migration strategy (Flyway)
- Data modeling patterns

**When to Read**:
- ✅ Designing database schemas
- ✅ Setting up new databases
- ✅ Implementing caching
- ✅ Planning backup/recovery
- ✅ Troubleshooting database performance

**Related**:
- [ADR-0005: PostgreSQL Database](../92-adr/0005-postgresql-database.md)
- [Database Standards](../05-backend/standards/database-standards.md)

---

### 4. [API Architecture](./api-architecture.md)
**GraphQL Federation, Apollo Router, and API design patterns**

**What's Inside**:
- GraphQL Federation in detail
- Apollo Router configuration and routing
- Subgraph composition and schema evolution
- API versioning strategy
- Error handling patterns
- Performance optimization (DataLoader, caching)
- API security and rate limiting

**When to Read**:
- ✅ Designing GraphQL schemas
- ✅ Implementing new API endpoints
- ✅ Understanding federation
- ✅ Optimizing API performance
- ✅ Troubleshooting query issues

**Related**:
- [GraphQL Standards](../06-contracts/graphql-standards.md)
- [GraphQL Patterns](../05-backend/patterns/graphql-patterns.md)

---

### 5. [Frontend Architecture](./frontend-architecture.md)
**Next.js 14+ App Router, React patterns, and UI architecture**

**What's Inside**:
- Next.js 14+ App Router architecture
- Server vs Client Components strategy
- State management (Context + Hooks)
- Component organization (functional component structure)
- Apollo Client integration and caching
- Routing and navigation patterns
- Performance optimization (RSC, streaming)

**When to Read**:
- ✅ Building new UI features
- ✅ Understanding component structure
- ✅ Implementing state management
- ✅ Optimizing frontend performance
- ✅ Setting up Apollo Client

**Related**:
- [ADR-0004: TypeScript/Next.js Frontend](../92-adr/0004-typescript-nextjs-frontend.md)
- [Frontend Patterns](../07-frontend/patterns/)

---

### 6. [Infrastructure Architecture](./infrastructure-architecture.md)
**Docker, Kubernetes, Terraform, and deployment infrastructure**

**What's Inside**:
- Docker containerization strategy
- Kubernetes orchestration and manifests
- Terraform Infrastructure as Code
- CI/CD with GitHub Actions
- Observability stack (Prometheus, Grafana, Loki)
- Environment management (dev, staging, prod)
- Scaling and auto-scaling strategies

**When to Read**:
- ✅ Setting up infrastructure
- ✅ Deploying to production
- ✅ Implementing CI/CD
- ✅ Scaling the system
- ✅ Troubleshooting deployments

**Related**:
- [ADR-0002: Containerized Architecture](../92-adr/0002-containerized-architecture.md)
- [Deployment Workflow](../08-workflows/deployment-workflow.md)

---

## Quick Navigation

### By Role

#### Software Architect
1. Start: [System Architecture](./system-architecture.md)
2. Services: [Service Architecture](./service-architecture.md)
3. Decisions: [All ADRs](../92-adr/)

#### Backend Developer
1. Start: [Service Architecture](./service-architecture.md)
2. Data: [Data Architecture](./data-architecture.md)
3. API: [API Architecture](./api-architecture.md)
4. Patterns: [Backend Patterns](../05-backend/patterns/)

#### Frontend Developer
1. Start: [Frontend Architecture](./frontend-architecture.md)
2. API: [API Architecture](./api-architecture.md)
3. Patterns: [Frontend Patterns](../07-frontend/patterns/)

#### DevOps Engineer
1. Start: [Infrastructure Architecture](./infrastructure-architecture.md)
2. System: [System Architecture](./system-architecture.md)
3. Workflows: [Deployment Workflow](../08-workflows/deployment-workflow.md)

#### Full-Stack Developer
1. Start: [System Architecture](./system-architecture.md)
2. Backend: [Service Architecture](./service-architecture.md) + [Data Architecture](./data-architecture.md)
3. Frontend: [Frontend Architecture](./frontend-architecture.md)
4. Infrastructure: [Infrastructure Architecture](./infrastructure-architecture.md)

### By Task

#### Understanding the System
- [System Architecture](./system-architecture.md) - Complete overview
- [Architecture at a Glance](../01-overview/architecture-at-a-glance.md) - 5-minute version

#### Building Features
- [Service Architecture](./service-architecture.md) - Backend services
- [API Architecture](./api-architecture.md) - GraphQL APIs
- [Frontend Architecture](./frontend-architecture.md) - UI components

#### Managing Data
- [Data Architecture](./data-architecture.md) - Database design
- [Database Standards](../05-backend/standards/database-standards.md) - Schema rules

#### Deploying
- [Infrastructure Architecture](./infrastructure-architecture.md) - Deployment setup
- [Deployment Workflow](../08-workflows/deployment-workflow.md) - Deploy process

#### Troubleshooting
- [System Architecture](./system-architecture.md) - Data flows
- [Service Architecture](./service-architecture.md) - Service communication
- [Observability](../10-observability/observability-overview.md) - Monitoring

---

## Architecture Decision Records (ADRs)

All major architectural decisions are documented with rationale:

### Core Architecture
- [ADR-0001: Monorepo Architecture](../92-adr/0001-monorepo-architecture.md)
- [ADR-0002: Containerized Architecture](../92-adr/0002-containerized-architecture.md)

### Technology Stack
- [ADR-0003: Kotlin/Micronaut Backend](../92-adr/0003-kotlin-micronaut-backend.md)
- [ADR-0004: TypeScript/Next.js Frontend](../92-adr/0004-typescript-nextjs-frontend.md)
- [ADR-0005: PostgreSQL Database](../92-adr/0005-postgresql-database.md)

### Security
- [ADR-0006: Frontend Authorization Layer](../92-adr/0006-frontend-authorization-layer.md)
- [ADR-0007: Asset Service Cloudflare R2](../92-adr/0007-asset-service-cloudflare-r2.md)
- [ADR-0008: Interservice Security](../92-adr/0008-interservice-security.md)

**See**: [All ADRs](../92-adr/) for complete decision history.

---

## Mermaid Diagrams

All architecture documents include **Mermaid diagrams** for visual clarity:

- **System diagrams**: Client → Gateway → Services → Database
- **Sequence diagrams**: Request/response flows
- **Component diagrams**: Service internals
- **Deployment diagrams**: Infrastructure topology
- **Data flow diagrams**: Read/write patterns

**Benefits**:
- ✅ Version-controlled (text-based)
- ✅ Rendered in GitHub/Markdown viewers
- ✅ Easy to update
- ✅ AI-friendly (parseable)

---

## Common Patterns

### Architectural Patterns

| Pattern | Used In | Description |
|---------|---------|-------------|
| **Microservices** | Service Architecture | Independent, deployable services |
| **GraphQL Federation** | API Architecture | Composed schema from multiple subgraphs |
| **Clean Architecture** | Service Architecture | Layered architecture (API → Service → Repository → Entity) |
| **Domain-Driven Design** | Service + Data | Domain models reflect business concepts |
| **Database per Service** | Data Architecture | Each service owns its database |
| **API Gateway** | API Architecture | Single entry point (Apollo Router) |
| **CQRS** | Data Architecture | Separate read/write models (planned) |
| **Event Sourcing** | Data Architecture | Event log as source of truth (planned) |

**See**: [All Patterns](../05-backend/patterns/) for implementation details.

---

## Technology Stack Summary

### Backend
- **Language**: Kotlin
- **Framework**: Micronaut 4.x
- **API**: GraphQL (Apollo Federation)
- **ORM**: Micronaut Data (JPA/Hibernate)
- **Database**: PostgreSQL 18+
- **Migrations**: Flyway

### Frontend
- **Framework**: Next.js 14+ (App Router)
- **Library**: React 18+
- **Language**: TypeScript
- **API Client**: Apollo Client
- **Styling**: Material-UI (MUI)
- **State**: React Context + Hooks

### Infrastructure
- **Containers**: Docker
- **Orchestration**: Kubernetes
- **IaC**: Terraform
- **CI/CD**: GitHub Actions
- **Observability**: Prometheus, Grafana, Loki

**See**: [Architecture at a Glance](../01-overview/architecture-at-a-glance.md) for detailed stack.

---

## Reading Order

### For New Team Members

**Day 1**: Understand the system
1. [Architecture at a Glance](../01-overview/architecture-at-a-glance.md) (15 min)
2. [System Architecture](./system-architecture.md) (30 min)
3. [Core Principles](../01-overview/core-principles.md) (30 min)

**Day 2**: Dive into your area
- Backend: [Service Architecture](./service-architecture.md) + [Data Architecture](./data-architecture.md)
- Frontend: [Frontend Architecture](./frontend-architecture.md) + [API Architecture](./api-architecture.md)
- DevOps: [Infrastructure Architecture](./infrastructure-architecture.md)

**Week 1**: Study patterns and examples
- [Backend Patterns](../05-backend/patterns/)
- [Frontend Patterns](../07-frontend/patterns/)
- [Code Examples](../90-examples/)

### For AI Assistants

**Context Building**:
1. [System Architecture](./system-architecture.md) - System overview
2. [Service Architecture](./service-architecture.md) - Service patterns
3. [API Architecture](./api-architecture.md) - GraphQL conventions
4. [Backend Patterns](../05-backend/patterns/) - Implementation patterns
5. [Frontend Patterns](../07-frontend/patterns/) - UI patterns

**See**: [Spec Context Strategy](../08-workflows/spec-context-strategy.md) for optimal context.

---

## Best Practices

### When Designing New Features

1. **Identify the domain**: Which service owns this feature?
2. **Design the API**: GraphQL schema (type-safe contract)
3. **Model the data**: Database schema + migrations
4. **Plan the UI**: Component structure + state management
5. **Consider deployment**: Infrastructure requirements

**See**: [Feature Development Workflow](../08-workflows/feature-development.md)

### When Making Architecture Changes

1. **Propose**: Create ADR document
2. **Discuss**: Review with team
3. **Document**: Update architecture docs
4. **Implement**: Follow the decision
5. **Validate**: Ensure compliance

**See**: [ADR Template](../91-templates/adr-template.md)

### When Troubleshooting

1. **System level**: [System Architecture](./system-architecture.md) - data flows
2. **Service level**: [Service Architecture](./service-architecture.md) - service communication
3. **Data level**: [Data Architecture](./data-architecture.md) - database queries
4. **API level**: [API Architecture](./api-architecture.md) - GraphQL errors
5. **Infrastructure level**: [Infrastructure Architecture](./infrastructure-architecture.md) - deployment issues

**See**: [Observability](../10-observability/observability-overview.md) for monitoring tools.

---

## FAQ

### Q: Which document should I read first?

**A**: Depends on your goal:
- **Understand the system**: [System Architecture](./system-architecture.md)
- **Build backend features**: [Service Architecture](./service-architecture.md)
- **Build frontend features**: [Frontend Architecture](./frontend-architecture.md)
- **Deploy to production**: [Infrastructure Architecture](./infrastructure-architecture.md)

### Q: Where are the diagrams?

**A**: Every architecture document includes Mermaid diagrams. They're rendered automatically in GitHub and most Markdown viewers.

### Q: How do I propose architecture changes?

**A**:
1. Create an ADR (Architecture Decision Record)
2. Follow the [ADR Template](../91-templates/adr-template.md)
3. Submit for review
4. Update architecture docs after approval

### Q: What's the difference between Architecture and Patterns?

**A**:
- **Architecture** (this section): High-level system design, technology choices, infrastructure
- **Patterns** ([Backend](../05-backend/patterns/), [Frontend](../07-frontend/patterns/)): Concrete implementation patterns and code examples

---

## Related Documentation

### Overview
- [Project Overview](../01-overview/README.md)
- [Architecture at a Glance](../01-overview/architecture-at-a-glance.md)
- [Core Principles](../01-overview/core-principles.md)

### Implementation
- [Backend Patterns](../05-backend/patterns/)
- [Frontend Patterns](../07-frontend/patterns/)
- [GraphQL Standards](../06-contracts/graphql-standards.md)

### Operations
- [Deployment Workflow](../08-workflows/deployment-workflow.md)
- [Observability](../10-observability/observability-overview.md)
- [Commands Reference](../93-reference/commands.md)

### Decisions
- [All ADRs](../92-adr/)
- [Technology Stack](../01-overview/architecture-at-a-glance.md)

---

**Version**: 3.0.0 (2026-01-02)
**Documents**: 6 architecture deep-dives + this navigation hub
**Philosophy**: Document architecture decisions. Make them visible. Keep them current.

*Navigate with confidence. Build with clarity.*
