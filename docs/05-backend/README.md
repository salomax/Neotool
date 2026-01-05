# Backend Documentation

This section contains all backend development documentation for NeoTool services.

## Organization

### [kotlin/](kotlin/)
Language-specific standards and guidelines for Kotlin development:
- Coding standards
- Linting rules
- Testing practices

### [patterns/](patterns/)
Cross-language implementation patterns:
- **Entity pattern** — Database entity design
- **Repository pattern** — Data access layer
- **Service pattern** — Business logic layer
- **Mapper pattern** — DTO and domain mapping
- **GraphQL resolver pattern** — Query and mutation resolvers
- **Kafka consumer pattern** — Event-driven consumers
- **Kafka producer pattern** — Event publishing
- **Pagination pattern** — Cursor-based pagination
- **Batch processing pattern** — Background job workflows
- **Error handling pattern** — Exception management
- **Domain-entity conversion** — DDD patterns

### [testing/](testing/)
Testing strategies and best practices:
- Unit testing patterns
- Integration testing patterns
- Test data builders
- Mocking strategies

### [standards/](standards/)
Architectural constraints and rules:
- Architecture standards
- Layer rules and boundaries
- Dependency management

## Quick Reference

### Common Tasks
- **Create new entity**: See [Entity Pattern](patterns/entity-pattern.md)
- **Add repository**: See [Repository Pattern](patterns/repository-pattern.md)
- **Implement service**: See [Service Pattern](patterns/service-pattern.md)
- **Add GraphQL resolver**: See [GraphQL Resolver Pattern](patterns/graphql-resolver-pattern.md)
- **Create Kafka consumer**: See [Kafka Consumer Pattern](patterns/kafka-consumer-pattern.md)

### Testing
- **Write unit tests**: See [Unit Testing](testing/unit-testing.md)
- **Integration tests**: See [Testing Standards](kotlin/testing-standards.md)

### Code Quality
- **Kotlin style**: See [Coding Standards](kotlin/coding-standards.md)
- **Linting**: See [Linting Standards](kotlin/linting-standards.md)

## Related Documentation

- **Domain modeling**: See [04-domain/](../04-domain/)
- **GraphQL contracts**: See [06-contracts/](../06-contracts/)
- **Security implementation**: See [09-security/](../09-security/)
- **Observability**: See [10-observability/](../10-observability/)
- **Examples**: See [90-examples/backend/](../90-examples/backend/)
- **Templates**: See [91-templates/code-templates/](../91-templates/code-templates/)

## Future Languages

This structure is prepared for additional backend languages. When adding Go:
```
05-backend/
├── kotlin/         # Kotlin-specific
├── go/             # Go-specific (future)
├── patterns/       # Cross-language patterns
├── testing/        # Cross-language testing
└── standards/      # Cross-language standards
```
