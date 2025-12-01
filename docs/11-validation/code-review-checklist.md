---
title: Code Review Checklist
type: validation
category: checklist
status: current
version: 3.0.0
tags: [checklist, code-review, validation]
ai_optimized: true
search_keywords: [checklist, code-review, validation, layers]
related:
  - 11-validation/feature-checklist.md
  - 11-validation/pr-checklist.md
  - 05-standards/architecture-standards/layer-rules.md
  - 05-standards/coding-standards/linting-standards.md
  - 04-patterns/backend-patterns/uuid-v7-pattern.md
---

# Code Review Checklist

> **Purpose**: Standardized code review process against specification, organized by architectural layers.

## Domain Layer

- [ ] Domain objects use nullable IDs (`UUID?` or `Int?`) for new entities
- [ ] Domain objects implement `toEntity()` method for conversion
- [ ] Domain objects with UUID IDs pass `null` to entity (let database generate UUID v7)
- [ ] Domain objects do NOT generate UUIDs in `toEntity()` method (no `UUID.randomUUID()`)
- [ ] Domain objects follow DDD principles (rich domain models)
- [ ] Domain logic is separated from infrastructure concerns
- [ ] Domain objects are in `domain` package, not mixed with entities
- [ ] Domain objects are immutable data classes where appropriate
- [ ] Business rules are encapsulated in domain objects

## Repository Layer

- [ ] Repository extends `JpaRepository<Entity, ID>` or `CrudRepository<Entity, ID>`
- [ ] Repository interface is in correct package structure
- [ ] Query methods follow Micronaut Data naming conventions (`findBy...`, `existsBy...`)
- [ ] Custom queries use `@Query` annotation with proper SQL
- [ ] Repository methods return appropriate types (nullable for single results)
- [ ] Repository does not contain business logic (only data access)
- [ ] Repository methods are properly annotated with `@Repository`

## Service Layer

- [ ] Service follows clean architecture (depends on Repository, not API layer)
- [ ] Service uses dependency injection correctly (`@Singleton`, `@Inject`)
- [ ] Service contains business logic, not just pass-through to repository
- [ ] Service methods handle domain-to-entity conversion correctly
- [ ] Service methods use domain objects, not entities, for business operations
- [ ] Service methods do NOT generate UUIDs (no `UUID.randomUUID()` for new entities)
- [ ] Service methods have proper error handling and validation
- [ ] Service methods return domain objects or appropriate DTOs
- [ ] Service follows single responsibility principle
- [ ] Service is testable (no hard dependencies on infrastructure)

## GraphQL Layer

### Schema

- [ ] GraphQL schema is in `src/main/resources/graphql/schema.graphqls`
- [ ] Entities use `@key(fields: "id")` directive for federation
- [ ] Schema follows federation patterns (see `docs/01-architecture/api-architecture/graphql-federation.md`)
- [ ] Schema uses appropriate GraphQL types (ID!, String!, Int!, etc.)
- [ ] Schema includes proper nullability (`!` for required fields)
- [ ] Schema is documented with descriptions
- [ ] Schema synced to contracts (`./neotool graphql sync`)

### Resolvers

- [ ] Resolver is named `{Type}Resolver`
- [ ] Resolver is in separate file from mapper/converter
- [ ] Resolver depends on Service layer, not Repository
- [ ] Resolver uses mapper/converter for data transformation (not inline mapping)
- [ ] Resolver handles GraphQL-specific concerns only
- [ ] Resolver has proper error handling
- [ ] Resolver uses `GraphQLPayload` for mutations where appropriate
- [ ] Resolver follows resolver patterns (see `docs/04-patterns/backend-patterns/`)

### Mappers/Converters

- [ ] Mapper/converter is in separate file from resolver
- [ ] Mapper/converter is in `mapper` or `converter` package
- [ ] Mapper/converter is singleton bean when stateless
- [ ] All mapping logic (entity to DTO, DTO to entity, input to DTO) is in mapper
- [ ] Mapper handles null values correctly
- [ ] Mapper has dedicated unit tests

## Entity Layer

- [ ] Entity extends `BaseEntity<T>` where appropriate
- [ ] Entity class is `open` (not `final`) for JPA proxy generation
- [ ] Entity includes `@Table(schema = "...")` annotation (never in `public` schema)
- [ ] Entity includes `@Version` field for optimistic locking
- [ ] Entity includes `createdAt` and `updatedAt` timestamp fields
- [ ] Entity implements `toDomain()` method
- [ ] Entity uses snake_case for column names
- [ ] Entity explicitly specifies `nullable = false` for required fields
- [ ] Entity uses `columnDefinition = "uuid"` for UUID columns
- [ ] **Entity with UUID primary key uses UUID v7 pattern** (see `docs/04-patterns/backend-patterns/uuid-v7-pattern.md`)
  - [ ] Entity uses nullable `UUID?` type for ID
  - [ ] Entity uses `@GeneratedValue(strategy = GenerationType.IDENTITY)`
  - [ ] Entity ID default is `null` (not `UUID.randomUUID()`)
  - [ ] Entity extends `BaseEntity<UUID?>` (nullable type parameter)
- [ ] Entity uses `@Enumerated(EnumType.STRING)` for enums
- [ ] Entity ID type matches domain object ID type (with proper conversion)

## Database/Migrations

- [ ] Migration files follow format: `V{version}__{description}.sql`
- [ ] Migration sets search path and uses explicit schema qualification
- [ ] Migration is idempotent (uses `IF NOT EXISTS`, `IF EXISTS`)
- [ ] Migration creates schema if needed: `CREATE SCHEMA IF NOT EXISTS {schema}`
- [ ] **Migration uses UUID v7 for UUID primary keys** (see `docs/04-patterns/backend-patterns/uuid-v7-pattern.md`)
  - [ ] Migration installs extension: `CREATE EXTENSION IF NOT EXISTS pg_uuidv7;`
  - [ ] Tables with UUID primary keys use `DEFAULT uuidv7()`
  - [ ] No use of `gen_random_uuid()` or `UUID.randomUUID()` for primary keys
- [ ] Migration creates indexes on foreign keys
- [ ] Migration uses proper index naming: `idx_{table}_{columns}`
- [ ] Migration includes proper constraints (NOT NULL, UNIQUE, FOREIGN KEY)
- [ ] Migration is tested (can be run multiple times safely)

## Backend Tests

### Unit Tests

- [ ] Unit tests achieve 90%+ line coverage and 85%+ branch coverage
- [ ] Security services achieve 100% coverage (lines and branches)
- [ ] Test methods return `Unit` (not `= runBlocking { }`)
- [ ] Test names use descriptive backticks: `` `should [behavior] when [condition]` ``
- [ ] Tests follow Arrange-Act-Assert pattern
- [ ] Tests are isolated (no dependencies on other tests)
- [ ] Tests use test data builders for consistent test data
- [ ] Tests use unique identifiers to avoid conflicts
- [ ] All conditional branches (if/when/switch/guard) are tested
- [ ] Both success and failure scenarios are tested
- [ ] Tests assert on behavior, not implementation details

### Integration Tests

- [ ] Integration tests achieve 80%+ line coverage and 75%+ branch coverage
- [ ] Integration tests use `@Tag("integration")` annotation
- [ ] Integration tests use `entityManager.runTransaction` for data setup when needed
- [ ] Integration tests test database interactions
- [ ] Integration tests are properly isolated (cleanup in `@BeforeEach` or `@AfterEach`)

## Linting and Code Quality

- [ ] **Lint checks pass** (verified by CI or local run)
- [ ] **No new lint errors introduced** by this change
- [ ] Backend: `./gradlew ktlintCheck` passes with zero errors
- [ ] Frontend: `pnpm run lint` passes with zero errors and zero warnings
- [ ] Frontend: `pnpm run typecheck` passes with zero errors
- [ ] Auto-fixable lint issues have been resolved
- [ ] Lint errors in modified files have been fixed (even if file had pre-existing errors)

## Code Patterns

- [ ] Follows naming conventions (PascalCase for classes, camelCase for functions/variables)
- [ ] Uses proper package naming: `io.github.salomax.neotool.{module}`
- [ ] One class per file (with exceptions for companion objects, small related classes)
- [ ] File name matches class name
- [ ] Uses 4 spaces for indentation
- [ ] Maximum line length is 120 characters
- [ ] Imports are organized (standard library, third-party, project)
- [ ] Domain-entity conversion handles nullable IDs correctly (see `docs/04-patterns/backend-patterns/domain-entity-conversion.md`)
- [ ] No cross-layer dependencies (dependencies point inward)
- [ ] Shared code is in `common` module, not duplicated

## Contracts

- [ ] GraphQL schema is synced to `contracts/graphql/subgraphs/{service}/`
- [ ] Contract changes are backward compatible (or breaking changes documented)
- [ ] Contract follows federation patterns
- [ ] Contract includes proper documentation/descriptions
- [ ] Contract types match implementation types

## UI/Frontend

### Components

- [ ] Components follow structure (see `docs/04-patterns/frontend-patterns/component-pattern.md`)
- [ ] Components use design system components
- [ ] Components apply theme tokens (not hardcoded colors/sizes)
- [ ] Components are properly typed (TypeScript)
- [ ] Components follow atomic design principles (atoms, molecules, organisms)
- [ ] Components are in correct directory structure (see `docs/01-architecture/frontend-architecture/nextjs-structure.md`)

### GraphQL Operations

- [ ] GraphQL operations follow patterns (see `docs/04-patterns/frontend-patterns/graphql-pattern.md`)
- [ ] GraphQL operations use generated TypeScript types
- [ ] GraphQL operations have proper error handling
- [ ] GraphQL operations use Apollo Client correctly

### Styling

- [ ] Uses theme tokens from design system
- [ ] Follows styling patterns (see `docs/04-patterns/frontend-patterns/styling-pattern.md`)
- [ ] No hardcoded colors, sizes, or spacing values
- [ ] Responsive design considerations applied

### Internationalization

- [ ] i18n support added for user-facing text
- [ ] Translation keys follow naming conventions
- [ ] Translations are in `public/locales/` directory

### TypeScript

- [ ] TypeScript types are generated from GraphQL schema
- [ ] Types are used throughout (no `any` types)
- [ ] Types are properly imported and used

### Frontend Tests

- [ ] Tests written for components with business logic
- [ ] Tests follow testing patterns
- [ ] Coverage meets requirements (with exclusions for thin wrappers and presentational components)

## Infrastructure

### Observability

- [ ] Prometheus metrics endpoint enabled at `/prometheus`
- [ ] Micrometer Prometheus exporter configured in `application.yml`
- [ ] Service registered in Prometheus scrape config (`infra/observability/prometheus/prometheus.yml`)
- [ ] Loki appender configured in `logback-production.xml`
- [ ] Structured JSON logging enabled with required fields
- [ ] Service name and environment labels configured in logs
- [ ] Required metrics exposed (JVM, HTTP, GraphQL, database)

### Docker/Kubernetes

- [ ] Dockerfile follows best practices
- [ ] Docker image is properly tagged
- [ ] Kubernetes manifests are up to date (if applicable)
- [ ] Resource limits and requests are configured
- [ ] Health checks are configured

### Configuration

- [ ] Configuration uses `application.yml` or environment variables
- [ ] Sensitive values are not hardcoded
- [ ] Configuration follows environment-specific patterns

## Security

- [ ] Authentication/authorization is properly implemented
- [ ] Input validation at API boundaries
- [ ] SQL injection prevention (parameterized queries)
- [ ] No sensitive data in logs
- [ ] Security standards followed (see `docs/05-standards/security-standards/`)

## Documentation

- [ ] Code is self-documenting
- [ ] Complex logic has comments explaining why (not what)
- [ ] GraphQL schema is documented with descriptions
- [ ] README updated if needed
- [ ] Breaking changes documented

## Architecture Compliance

- [ ] Follows patterns from relevant ADRs
- [ ] Respects layer boundaries (API → Service → Repository → Entity)
- [ ] Uses dependency injection correctly
- [ ] Follows domain-driven design principles
- [ ] Follows clean architecture principles
- [ ] No circular dependencies

