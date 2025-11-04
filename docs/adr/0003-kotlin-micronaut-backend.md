# ADR-0003: Kotlin + Micronaut Backend Technology Stack

## Status
Accepted

## Context
NeoTool needs to choose a backend technology stack that supports:
- Fast startup and low memory footprint for containerized deployments
- Modern, type-safe language with good developer experience
- Cloud-native architecture with modular design
- Strong integration with GraphQL and observability tools
- Excellent testing capabilities
- Good performance in both local development and production

The backend will serve as the foundation for GraphQL APIs, business logic, and data persistence layers.

## Decision
We will use **Kotlin + Micronaut** as the primary backend technology stack.

### Technology Components
- **Language**: Kotlin
- **Framework**: Micronaut
- **Build Tool**: Gradle
- **API**: GraphQL with Apollo Federation
- **Data Access**: Micronaut Data (JPA/Hibernate)
- **Testing**: JUnit 5 + Testcontainers
- **Observability**: Micrometer + Prometheus

## Consequences

### Positive
- **Fast startup & low memory**: DI and AOP are computed at compile time (not reflection-heavy), resulting in snappy startup and small footprints in Docker/K8s
- **Modern, safe language**: Kotlin's null-safety, data classes, sealed types, and coroutines reduce entire classes of bugs and simplify async flows
- **Cloud-native fit**: Works great in containers, scales with HPA, and is friendly to serverless/native-image builds when needed
- **Modular architecture**: Micronaut encourages small, testable modules (HTTP, GraphQL, Data, Validation, Security), aligning with NeoTool's "compose & reuse" philosophy
- **Developer experience**: Clear annotations, first-class validation, and Micronaut Data for concise repositories without heavy ORMs
- **Observability by default**: Micrometer, HTTP filters/interceptors, and log context (MDC) integrate cleanly with Prometheus/Grafana/Loki
- **Testing that scales**: Fast integration tests (Testcontainers), plus clean separation of unit vs. integration layers

### Negative
- **Learning curve**: Developers need to learn Kotlin and Micronaut-specific patterns
- **Ecosystem maturity**: Smaller ecosystem compared to Spring Boot
- **Community size**: Smaller community compared to Java/Spring Boot
- **Tooling**: Some IDE features and tooling may be less mature than Spring Boot

### Risks
- **Talent availability**: Fewer developers familiar with Micronaut compared to Spring Boot
- **Long-term support**: Dependency on Micronaut's continued development and support
- **Migration complexity**: If we need to migrate away from Micronaut, it could be complex

### Mitigation Strategies
- **Comprehensive documentation**: Provide clear examples and patterns for common use cases
- **Training materials**: Create internal documentation and examples for team onboarding
- **Gradual adoption**: Start with simple services and gradually adopt more advanced features
- **Community engagement**: Contribute to Micronaut community and stay updated with best practices

## Problems This Choice Solves
- **Cold start & resource constraints**: Services boot quickly and stay lean — ideal for dense clusters and CI ephemeral environments
- **Reliability & safety**: Kotlin's type system + validation reduce runtime surprises for API and GraphQL resolvers
- **Consistency across services**: Common modules (logging, errors, GraphQL wiring, DTO validation) keep conventions uniform
- **Operational clarity**: Structured logs + metrics + health checks are standard, not add-ons
- **Evolvability**: Compile-time DI/AOP and modular packages keep refactors predictable as the codebase grows

## Alternatives Considered

### Spring Boot + Java
- **Pros**: Mature ecosystem, large community, extensive documentation
- **Cons**: Slower startup, higher memory usage, more reflection-heavy, less type safety

### Spring Boot + Kotlin
- **Pros**: Mature ecosystem, Kotlin benefits, large community
- **Cons**: Still reflection-heavy, slower startup than Micronaut, more complex configuration

### Node.js + TypeScript
- **Pros**: Single language across frontend and backend, large ecosystem
- **Cons**: Runtime type safety issues, less mature for enterprise applications, different performance characteristics

### Go
- **Pros**: Excellent performance, simple deployment, good for microservices
- **Cons**: Less mature ecosystem for enterprise applications, different development patterns

### .NET Core + C#
- **Pros**: Mature platform, good performance, strong typing
- **Cons**: Microsoft ecosystem lock-in, different from frontend stack

## Decision Drivers
- NeoTool's focus on developer velocity and clean architecture
- Need for fast startup and low resource usage in containerized environments
- Desire for type safety and modern language features
- Requirement for modular, testable architecture
- Need for excellent observability and monitoring integration
- Preference for compile-time optimizations over runtime reflection

## Implementation Notes
- Use Micronaut's compile-time dependency injection
- Implement GraphQL resolvers using Micronaut's GraphQL support
- Use Micronaut Data for database access with minimal boilerplate
- Configure Micrometer for metrics collection
- Implement health checks and readiness probes for Kubernetes
- Use Testcontainers for integration testing
- Follow domain-driven design patterns (API → Service → Repository → Entity)
