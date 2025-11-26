---
title: Core Principles
type: overview
category: philosophy
status: current
version: 2.0.0
tags: [principles, philosophy, guidelines, design]
ai_optimized: true
search_keywords: [principles, philosophy, guidelines, design, architecture]
related:
  - 00-overview/architecture-overview.md
  - 05-standards/architecture-standards/layer-rules.md
---

# Core Principles

> **Purpose**: Core design principles and philosophy that guide NeoTool development.

## Overview

NeoTool is built on a set of core principles that guide all architectural decisions, code patterns, and development practices. These principles ensure consistency, maintainability, and scalability.

## 1. Modularity

**Principle**: Clear separation of concerns with well-defined boundaries.

### Application
- **Monorepo structure**: Organized by domain and layer
- **Module boundaries**: Clear interfaces between modules
- **Dependency management**: Explicit dependencies, avoid circular dependencies
- **Service boundaries**: Independent services with clear APIs

### Benefits
- Easier to understand and maintain
- Independent development and deployment
- Better testability
- Reduced coupling

### Examples
- Backend modules: `app`, `assistant`, `security`, `common`
- Frontend: `app`, `shared`, `lib` separation
- GraphQL Federation: Independent subgraphs

## 2. Type Safety

**Principle**: End-to-end type safety from database to UI.

### Application
- **Database**: Strongly typed entities and migrations
- **Backend**: Kotlin type system, GraphQL schema types
- **API**: GraphQL schema as contract
- **Frontend**: TypeScript types generated from GraphQL schema
- **Code Generation**: Automated type generation

### Benefits
- Catch errors at compile time
- Better IDE support and autocomplete
- Self-documenting code
- Refactoring safety

### Examples
- GraphQL schema → TypeScript types
- JPA entities → Kotlin types
- Type-safe GraphQL operations

## 3. Developer Experience

**Principle**: Fast feedback loops, excellent tooling, clear patterns.

### Application
- **Hot Reload**: Fast development cycles
- **Clear Patterns**: Well-documented patterns and examples
- **Tooling**: Comprehensive CLI and scripts
- **Documentation**: Complete specification and guides
- **Code Generation**: Reduce boilerplate

### Benefits
- Faster development
- Lower learning curve
- Consistent code quality
- Better onboarding

### Examples
- NeoTool CLI for common tasks
- GraphQL code generation
- Comprehensive specification
- Pattern examples

## 4. Cloud-Native

**Principle**: Containerized, scalable, observable from day one.

### Application
- **Containers**: Docker for all services
- **Orchestration**: Kubernetes-ready
- **Observability**: Metrics, logging, tracing
- **Scalability**: Horizontal scaling support
- **Stateless**: Stateless services

### Benefits
- Easy deployment
- Scalable architecture
- Production-ready
- Vendor-neutral

### Examples
- Docker Compose for local dev
- Kubernetes manifests
- Prometheus metrics
- Grafana dashboards

## 5. Vendor Neutral

**Principle**: Portable across cloud providers, no lock-in.

### Application
- **Standard Technologies**: Industry-standard tools
- **No Vendor Lock-in**: Avoid proprietary solutions
- **Portable Configs**: Standard formats (Docker, K8s)
- **Open Source**: Prefer open-source tools

### Benefits
- Flexibility in deployment
- Avoid vendor lock-in
- Easier migration
- Community support

### Examples
- Docker (not AWS ECS-specific)
- Kubernetes (not GKE-specific)
- PostgreSQL (not RDS-specific)
- Standard observability stack

## 6. Clean Architecture

**Principle**: Layered architecture with clear separation of concerns.

### Application
- **Layers**: API → Service → Repository → Entity
- **Dependencies**: Dependencies point inward
- **Domain Logic**: Business logic in service layer
- **Data Access**: Repository pattern

### Benefits
- Testability
- Maintainability
- Flexibility
- Clear responsibilities

### Examples
- Backend: Resolver → Service → Repository → Entity
- Frontend: Page → Component → Hook → API

## 7. Domain-Driven Design

**Principle**: Domain entities, services, and repositories reflect business domain.

### Application
- **Domain Entities**: Rich domain models
- **Domain Services**: Business logic in services
- **Repositories**: Data access abstraction
- **Bounded Contexts**: Clear domain boundaries

### Benefits
- Business-aligned code
- Better understanding
- Maintainable domain logic
- Clear boundaries

### Examples
- Domain entities with business logic
- Service layer for business operations
- Repository pattern for data access

## 8. Component-Driven Development

**Principle**: Reusable UI components following atomic design.

### Application
- **Atomic Design**: Atoms, molecules, organisms
- **Design System**: Consistent components
- **Reusability**: Shared component library
- **Composition**: Build complex UIs from simple components

### Benefits
- Consistency
- Reusability
- Faster development
- Easier maintenance

### Examples
- Atomic components (Button, Input)
- Molecular components (Form, Card)
- Organism components (Header, Sidebar)

## 9. Spec-Driven Development

**Principle**: Specification drives development, not the other way around.

### Application
- **Complete Specification**: Comprehensive documentation
- **Patterns**: Documented patterns and examples
- **Templates**: Reusable templates
- **Validation**: Checklists and validation

### Benefits
- Consistency
- Faster development
- Better quality
- Easier onboarding

### Examples
- Feature creation from specification
- Pattern-based development
- Template-driven code generation

## 10. Testing First

**Principle**: Tests are first-class citizens, not afterthoughts.

### Application
- **Unit Tests**: Test business logic
- **Integration Tests**: Test integrations
- **E2E Tests**: Test user flows
- **Test Coverage**: Maintain coverage thresholds

### Benefits
- Confidence in changes
- Documentation through tests
- Regression prevention
- Better design

### Examples
- Unit tests for services
- Integration tests with Testcontainers
- E2E tests with Playwright

## Applying Principles

### When Making Decisions
1. **Check principles**: Does this align with core principles?
2. **Consider trade-offs**: What are the trade-offs?
3. **Document decisions**: Record in ADRs if significant
4. **Update patterns**: Update patterns if new approach

### When Reviewing Code
1. **Modularity**: Is code properly separated?
2. **Type Safety**: Are types used correctly?
3. **Patterns**: Does code follow patterns?
4. **Testing**: Are tests adequate?

### When Adding Features
1. **Follow patterns**: Use existing patterns
2. **Maintain principles**: Don't compromise principles
3. **Update spec**: Update specification if needed
4. **Add examples**: Add examples for new patterns

## Related Documentation

- [Architecture Overview](./architecture-overview.md)
- [Architecture Standards](../05-standards/architecture-standards/layer-rules.md)
- [Coding Standards](../05-standards/coding-standards/)
- [Patterns](../04-patterns/)

