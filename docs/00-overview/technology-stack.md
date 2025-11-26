---
title: Technology Stack
type: overview
category: technology
status: current
version: 2.0.0
tags: [tech-stack, technologies, versions, dependencies]
ai_optimized: true
search_keywords: [technology, stack, versions, kotlin, typescript, nextjs, micronaut, postgresql]
related:
  - 00-overview/architecture-overview.md
  - 09-adr/0003-kotlin-micronaut-backend.md
  - 09-adr/0004-typescript-nextjs-frontend.md
  - 09-adr/0005-postgresql-database.md
---

# Technology Stack

> **Purpose**: Complete list of technologies, versions, and dependencies used in NeoTool.

## Overview

NeoTool uses a modern, type-safe technology stack optimized for developer experience, performance, and scalability.

## Backend Stack

### Language & Runtime
- **Kotlin**: Latest stable version
  - JVM Target: Java 21
  - Language Version: Latest
- **Java**: 21+ (for JVM runtime)
  - Virtual Threads support
  - Pattern matching
  - Records support

### Framework
- **Micronaut**: Latest stable version
  - Dependency Injection
  - AOP support
  - HTTP server
  - GraphQL support
  - Data access (JPA/Hibernate)

### Build Tool
- **Gradle**: Latest stable version
  - Kotlin DSL
  - Multi-module support
  - Dependency management

### Data Access
- **Micronaut Data**: Latest version
  - JPA/Hibernate integration
  - Repository pattern
  - Query methods
- **Hibernate**: Via Micronaut Data
  - JPA implementation
  - Entity management
- **HikariCP**: Connection pooling

### Database
- **PostgreSQL**: 15+
  - Primary database
  - ACID compliance
  - JSON support
- **Flyway**: Database migrations
  - Version control for schema
  - Migration scripts

### API
- **GraphQL**: Apollo Federation
  - Distributed schema
  - Type composition
  - Query optimization
- **Apollo Router**: Gateway/router
  - Schema composition
  - Query routing
  - Federation support

### Testing
- **JUnit 5**: Unit testing
- **Testcontainers**: Integration testing
  - PostgreSQL container
  - Isolated test environments
- **MockK**: Kotlin mocking library
- **AssertJ**: Fluent assertions

### Logging & Observability
- **SLF4J**: Logging facade
- **Logback**: Logging implementation
- **Micrometer**: Metrics collection
- **Prometheus**: Metrics storage
- **Loki**: Log aggregation

## Frontend Stack

### Framework
- **Next.js**: 14+ (App Router)
  - Server Components
  - Client Components
  - File-based routing
  - API routes
- **React**: 18+
  - Server Components
  - Client Components
  - Hooks
  - Context API

### Language
- **TypeScript**: Latest stable version
  - Strict mode
  - Type safety
  - Code generation from GraphQL

### State Management
- **React Context**: Global state
- **Custom Hooks**: State logic
- **Apollo Client**: GraphQL state

### Styling
- **Material-UI (MUI)**: Component library
  - Theme system
  - Design tokens
  - Responsive design
- **Emotion**: CSS-in-JS
  - Styled components
  - Theme integration
- **Custom Design System**: Design tokens
  - Colors
  - Typography
  - Spacing
  - Components

### API Client
- **Apollo Client**: GraphQL client
  - Query caching
  - Mutations
  - Subscriptions
  - Type generation

### Code Generation
- **GraphQL Code Generator**: Type generation
  - TypeScript types from schema
  - React hooks
  - Operations

### Testing
- **Vitest**: Unit testing
- **React Testing Library**: Component testing
- **Playwright**: E2E testing
- **MSW**: API mocking

### Internationalization
- **next-intl**: i18n for Next.js
  - Server Components support
  - Type-safe translations
  - Locale routing

## Mobile Stack (Planned)

### Framework
- **React Native**: Latest version
- **Expo**: Development platform

### Language
- **TypeScript**: Same as web

### State & API
- **Apollo Client**: GraphQL client
- **React Context**: State management

## Infrastructure Stack

### Containers
- **Docker**: Latest version
  - Multi-stage builds
  - Docker Compose for local dev

### Orchestration
- **Kubernetes**: Latest stable version
  - Deployments
  - Services
  - ConfigMaps
  - Secrets

### GitOps
- **ArgoCD**: Continuous deployment
  - Git-based configuration
  - Automated sync
  - Rollback support

### Observability
- **Prometheus**: Metrics collection
- **Grafana**: Visualization
  - Dashboards
  - Alerts
- **Loki**: Log aggregation
- **Promtail**: Log shipping

### CI/CD
- **GitHub Actions**: CI/CD pipelines
  - Build
  - Test
  - Deploy
  - (Configurable for other platforms)

## Development Tools

### Code Quality
- **ESLint**: JavaScript/TypeScript linting
- **Prettier**: Code formatting
- **ktlint**: Kotlin linting
- **Detekt**: Kotlin static analysis

### Version Control
- **Git**: Version control
- **GitHub**: Repository hosting

### Package Management
- **npm/pnpm**: Node.js packages
- **Gradle**: Java/Kotlin dependencies

## Version Management

### Backend
- Kotlin: Latest stable
- Micronaut: Latest stable
- Java: 21+
- Gradle: Latest stable

### Frontend
- Node.js: 18+ (LTS)
- Next.js: 14+
- React: 18+
- TypeScript: Latest stable

### Infrastructure
- Docker: Latest
- Kubernetes: Latest stable
- PostgreSQL: 15+

## Dependency Updates

### Update Strategy
- **Regular updates**: Monthly dependency updates
- **Security patches**: Immediate updates
- **Major versions**: Planned migrations
- **Testing**: All updates tested before merge

### Version Pinning
- **Production**: Pinned versions
- **Development**: Latest compatible versions
- **CI/CD**: Locked versions

## Technology Decisions

See Architecture Decision Records for detailed technology choices:
- [ADR-0003: Kotlin/Micronaut Backend](../09-adr/0003-kotlin-micronaut-backend.md)
- [ADR-0004: TypeScript/Next.js Frontend](../09-adr/0004-typescript-nextjs-frontend.md)
- [ADR-0005: PostgreSQL Database](../09-adr/0005-postgresql-database.md)

## Related Documentation

- [Architecture Overview](./architecture-overview.md)
- [Project Structure](./project-structure.md)
- [Backend Patterns](../04-patterns/backend-patterns/)
- [Frontend Patterns](../04-patterns/frontend-patterns/)

