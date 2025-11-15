---
title: NeoTool Architecture Overview
type: architecture
category: overview
status: current
version: 1.0.0
tags: [architecture, overview, system-design, neotool]
related:
  - adr/0001-monorepo-architecture.md
  - adr/0002-containerized-architecture.md
  - adr/0003-kotlin-micronaut-backend.md
  - adr/0004-typescript-nextjs-frontend.md
  - adr/0005-postgresql-database.md
---

# NeoTool Architecture Overview

> **Purpose**: High-level architectural overview of the NeoTool platform, designed for quick understanding and RAG indexing.

## What is NeoTool?

NeoTool is a **modular full-stack boilerplate** designed to accelerate new app development while maintaining clean architecture and best practices. It serves as a foundation framework that helps spin up new services or apps (backend, frontend, infrastructure, and design system), all wired together and ready to evolve.

## Core Principles

1. **Modularity**: Clear separation of concerns with well-defined boundaries
2. **Type Safety**: End-to-end type safety from database to UI
3. **Developer Experience**: Fast feedback loops, excellent tooling, clear patterns
4. **Cloud-Native**: Containerized, scalable, observable from day one
5. **Vendor Neutral**: Portable across cloud providers, no lock-in

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

## Monorepo Structure

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
├── design/           # Design system and assets
├── spec/             # Specification and documentation
└── scripts/          # Build and utility scripts
```

## Key Architectural Patterns

### 1. GraphQL Federation
- **Pattern**: Apollo Federation for distributed GraphQL
- **Benefit**: Decentralized schema development, type composition
- **See**: [GraphQL Federation Architecture](./service/graphql-federation-architecture.md)

### 2. Domain-Driven Design
- **Pattern**: Domain entities, services, repositories
- **Benefit**: Clear boundaries, testable code
- **See**: [Database Schema Organization](./service/database-schema-organization.md)

### 3. Clean Architecture
- **Pattern**: Layered architecture (API → Service → Repository → Entity)
- **Benefit**: Separation of concerns, testability
- **See**: [Backend ADR](./adr/0003-kotlin-micronaut-backend.md)

### 4. Component-Driven Development
- **Pattern**: Atomic design system (atoms, molecules, organisms)
- **Benefit**: Reusable UI components, design consistency
- **See**: [Web Components](./web/web-components.md)

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

## Related Documentation

- [Monorepo Architecture](./adr/0001-monorepo-architecture.md)
- [Containerized Architecture](./adr/0002-containerized-architecture.md)
- [Backend Technology Stack](./adr/0003-kotlin-micronaut-backend.md)
- [Frontend Technology Stack](./adr/0004-typescript-nextjs-frontend.md)
- [Database Technology](./adr/0005-postgresql-database.md)
- [GraphQL Federation](./service/graphql-federation-architecture.md)
- [Project Setup Guide](./PROJECT_SETUP.md)

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
1. Read [Project Setup Guide](./PROJECT_SETUP.md)
2. Review [Architecture Decision Records](./adr/)
3. Explore [Service Documentation](./service/)
4. Check [Web Documentation](./web/)

