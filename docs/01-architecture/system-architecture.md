---
title: System Architecture
type: architecture
category: architecture
status: current
version: 2.0.0
tags: [architecture, system-design, neotool]
ai_optimized: true
search_keywords: [architecture, system-design, layers, patterns]
related:
  - 00-overview/technology-stack.md
  - 00-overview/project-structure.md
  - 00-overview/principles.md
  - 09-adr/0001-monorepo-architecture.md
  - 09-adr/0002-containerized-architecture.md
  - 09-adr/0003-kotlin-micronaut-backend.md
  - 09-adr/0004-typescript-nextjs-frontend.md
  - 09-adr/0005-postgresql-database.md
  - 04-patterns/api-patterns/graphql-federation.md
---

# System Architecture

> **Purpose**: High-level architectural overview of the NeoTool platform, designed for quick understanding and RAG indexing.

## What is NeoTool?

NeoTool is a **modular full-stack boilerplate** designed to accelerate new app development while maintaining clean architecture and best practices. It serves as a foundation framework that helps spin up new services or apps (backend, frontend, infrastructure, and design system), all wired together and ready to evolve.

## Core Principles

1. **Modularity**: Clear separation of concerns with well-defined boundaries
2. **Type Safety**: End-to-end type safety from database to UI
3. **Developer Experience**: Fast feedback loops, excellent tooling, clear patterns
4. **Cloud-Native**: Containerized, scalable, observable from day one
5. **Vendor Neutral**: Portable across cloud providers, no lock-in

See [Core Principles](./principles.md) for detailed explanation.

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Client Layer                          │
├─────────────────────┬───────────────────────────────────────┤
│   Web (Next.js)     │      Mobile (React Native)            │
│   TypeScript        │      TypeScript                       │
└──────────┬──────────┴──────────────┬────────────────────────┘
           │                         │
           │      GraphQL            │
           └──────────┬──────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                    API Gateway Layer                         │
│              Apollo Router (GraphQL Federation)              │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                    Service Layer                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Micronaut Services (Kotlin)                        │   │
│  │  - GraphQL Resolvers                                │   │
│  │  - Business Logic                                   │   │
│  │  - Data Access (JPA/Hibernate)                      │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                    Data Layer                                │
│              PostgreSQL Database                             │
│              (with Flyway Migrations)                        │
└─────────────────────────────────────────────────────────────┘
```

## Technology Stack

See [Technology Stack](./technology-stack.md) for detailed technology choices and versions.

### Frontend
- **Web**: Next.js 14+ (App Router), React 18+, TypeScript
- **Mobile**: React Native, Expo
- **State**: React Context + Custom Hooks
- **Styling**: Material-UI + Custom Design System
- **API Client**: Apollo Client (GraphQL)

### Backend
- **Language**: Kotlin
- **Framework**: Micronaut
- **Build**: Gradle
- **API**: GraphQL (Apollo Federation)
- **Data Access**: Micronaut Data (JPA/Hibernate)
- **Testing**: JUnit 5 + Testcontainers

### Infrastructure
- **Containers**: Docker
- **Orchestration**: Kubernetes
- **GitOps**: ArgoCD
- **Observability**: Prometheus, Grafana, Loki
- **CI/CD**: GitHub Actions (configurable)

### Database
- **Primary**: PostgreSQL 15+
- **Migrations**: Flyway
- **Connection Pooling**: HikariCP

## Project Structure

See [Project Structure](./project-structure.md) for detailed monorepo organization.

```
neotool/
├── service/          # Backend services (Kotlin/Micronaut)
│   ├── kotlin/      # Main service application
│   └── gateway/     # Apollo Router configuration
├── web/              # Web frontend (Next.js)
├── mobile/           # Mobile app (React Native/Expo)
├── infra/            # Infrastructure as Code
│   ├── docker/      # Docker Compose configs
│   └── k8s/         # Kubernetes manifests
├── contracts/        # API contracts (GraphQL schemas)
├── spec/             # Specification and documentation
└── scripts/          # Build and utility scripts
```

## Key Architectural Patterns

### 1. GraphQL Federation
- **Pattern**: Apollo Federation for distributed GraphQL
- **Benefit**: Decentralized schema development, type composition
- **See**: [GraphQL Federation Pattern](../04-patterns/api-patterns/graphql-federation.md)
- **ADR**: [ADR-0003: Kotlin Backend](../09-adr/0003-kotlin-micronaut-backend.md)

### 2. Domain-Driven Design
- **Pattern**: Domain entities, services, repositories
- **Benefit**: Clear boundaries, testable code
- **See**: [Database Rules](../05-standards/database-standards/schema-standards.md)
- **Pattern**: [Repository Pattern](../04-patterns/backend-patterns/repository-pattern.md)

### 3. Clean Architecture
- **Pattern**: Layered architecture (API → Service → Repository → Entity)
- **Benefit**: Separation of concerns, testability
- **See**: [Architecture Rules](../05-standards/architecture-rules.md)
- **ADR**: [ADR-0003: Kotlin Backend](../09-adr/0003-kotlin-micronaut-backend.md)

### 4. Component-Driven Development
- **Pattern**: Atomic design system (atoms, molecules, organisms)
- **Benefit**: Reusable UI components, design consistency
- **See**: [Component Pattern](../04-patterns/frontend-patterns/component-pattern.md)
- **ADR**: [ADR-0004: TypeScript Frontend](../09-adr/0004-typescript-nextjs-frontend.md)

## Data Flow

### Read Flow
```
Client → Apollo Router → Federated Service → Repository → PostgreSQL
```

### Write Flow
```
Client → Apollo Router → Federated Service → Service Layer → Repository → PostgreSQL
```

### Type Safety Flow
```
GraphQL Schema → Code Generation → TypeScript Types → React Components
```

## Deployment Architecture

### Local Development
- Docker Compose for all services
- Hot reload for frontend and backend
- Local PostgreSQL instance

### Production
- Kubernetes clusters
- GitOps with ArgoCD
- Horizontal Pod Autoscaling
- Read replicas for database

## Observability

### Metrics
- **Collection**: Micrometer → Prometheus
- **Visualization**: Grafana dashboards
- **Coverage**: HTTP, GraphQL, database, JVM

### Logging
- **Collection**: Structured logging → Loki
- **Visualization**: Grafana LogQL queries
- **Context**: MDC for request tracing

### Tracing
- **Future**: OpenTelemetry integration planned

## Security

### Authentication & Authorization
- **JWT-based authentication**: 
  - Access tokens: Short-lived (default: 15 minutes), stateless JWT tokens for API requests
  - Refresh tokens: Long-lived (default: 7 days), stored in database for token refresh and revocation
  - HMAC-SHA256 (HS256) algorithm for token signing
  - Configurable expiration times via `JwtConfig`
- Role-based access control (RBAC) - *planned*
- GraphQL field-level authorization - *planned*

See [Security Rules](../05-standards/security-standards/authentication-standards.md) for detailed security patterns.

### Data Protection
- Encrypted connections (TLS)
- Parameterized queries (SQL injection prevention)
- Input validation at API boundaries

## Scalability

### Horizontal Scaling
- Stateless services enable horizontal scaling
- Kubernetes HPA for auto-scaling
- Database read replicas

### Performance
- GraphQL query optimization
- Database indexing strategies
- CDN for static assets
- Edge caching for API responses

## Development Workflow

1. **Feature Development**: Create GraphQL schema → Generate types → Implement resolvers → Build UI
2. **Testing**: Unit tests → Integration tests → E2E tests
3. **Deployment**: Git push → CI/CD → GitOps → Production

See [Feature Development Workflow](../06-workflows/feature-development.md) for detailed process.

## Related Documentation

### Architecture Decision Records
- [ADR-0001: Monorepo Architecture](../09-adr/0001-monorepo-architecture.md)
- [ADR-0002: Containerized Architecture](../09-adr/0002-containerized-architecture.md)
- [ADR-0003: Kotlin/Micronaut Backend](../09-adr/0003-kotlin-micronaut-backend.md)
- [ADR-0004: TypeScript/Next.js Frontend](../09-adr/0004-typescript-nextjs-frontend.md)
- [ADR-0005: PostgreSQL Database](../09-adr/0005-postgresql-database.md)

### Patterns
- [GraphQL Federation](../04-patterns/api-patterns/graphql-federation.md)
- [Backend Patterns](../04-patterns/backend-patterns/)
- [Frontend Patterns](../04-patterns/frontend-patterns/)

### Rules
- [Architecture Rules](../05-standards/architecture-rules.md)
- [API Rules](../05-standards/api-rules.md)
- [Security Rules](../05-standards/security-standards/authentication-standards.md)

## Quick Reference

### Key Technologies
- **Backend**: Kotlin + Micronaut
- **Frontend**: TypeScript + Next.js + React
- **API**: GraphQL (Apollo Federation)
- **Database**: PostgreSQL
- **Infrastructure**: Docker + Kubernetes

### Key Concepts
- Monorepo architecture
- GraphQL Federation
- Domain-Driven Design
- Type-safe end-to-end
- Cloud-native patterns

### Getting Started
1. Read [Project Structure](./project-structure.md)
2. Review [Architecture Decision Records](../09-adr/)
3. Explore [Patterns](../04-patterns/)
4. Check [Rules](../05-standards/)

