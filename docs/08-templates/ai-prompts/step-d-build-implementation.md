---
title: Step D: Build Implementation
type: template
category: ai-prompt
status: current
version: 2.0.0
tags: [ai-prompt, implementation, feature-creation]
ai_optimized: true
search_keywords: [ai-prompt, implementation, build, feature]
related:
  - 04-templates/ai-prompts/step-c-generate-implementation-plan.md
  - 04-templates/ai-prompts/step-e-validate-implementation.md
---

# Step D: Build Implementation

> **Purpose**: AI prompt to build the complete feature implementation following the approved plan.

## Usage

Use this prompt after manual review of implementation plan from Step C.

## Prompt Template

```
Build the complete feature implementation for NeoTool following the approved implementation plan.

## Implementation Plan

[Paste approved implementation plan from Step C]

## Feature Form

[Paste Feature Form from Step B]

## Feature File

[Paste feature file from docs/03-features/[category]/[feature-name].feature]

## Specification References

- Architecture: spec/00-overview/architecture.md
- Technology Stack: spec/00-overview/technology-stack.md
- Rules: spec/05-standards/
- Patterns: spec/04-patterns/
- Examples: spec/05-examples/

## Implementation Requirements

1. Follow the implementation plan exactly:
   - Implement artifacts in the specified order
   - Use exact file paths from the plan
   - Follow all spec references

2. Backend Implementation (if applicable):
   - **CRITICAL**: Reference working examples in `service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/`
   - Follow entity-pattern.md for entities (docs/04-patterns/backend-patterns/entity-pattern.md)
   - Follow repository-pattern.md for repositories (docs/04-patterns/backend-patterns/repository-pattern.md)
   - Follow service-pattern.md for services (docs/04-patterns/backend-patterns/service-pattern.md)
   - Follow resolver-pattern.md for resolvers (docs/04-patterns/backend-patterns/resolver-pattern.md)
   - Use code templates from docs/08-templates/code/ as starting points
   - Reference backend-quick-reference.md for common patterns and imports (docs/10-reference/backend-quick-reference.md)
   - Follow database-rules.md for migrations
   - Follow api-rules.md for GraphQL schema
   - Follow testing-pattern.md and testing-rules.md for tests
   
   **Backend Error Prevention Checklist**:
   - [ ] All entities have `@Entity`, `@Table`, `@Id`, `@Version` annotations
   - [ ] All entity classes are `open` (not final)
   - [ ] All entity properties are `open var` (not val or final)
   - [ ] All entities extend `BaseEntity<T>` where T is ID type
   - [ ] All entities have `toDomain()` method
   - [ ] Domain-entity conversion handles nullable IDs correctly (see domain-entity-conversion.md)
   - [ ] All repositories have `@Repository` annotation
   - [ ] All repositories extend `JpaRepository<Entity, ID>`
   - [ ] All services have `@Singleton` annotation
   - [ ] All services have `open class` (for transaction proxies)
   - [ ] Write operations (create, update, delete) have `@Transactional` and `open fun`
   - [ ] Read operations (list, get) have NO `@Transactional`
   - [ ] All resolvers have `@Singleton` annotation
   - [ ] All resolvers extend `GenericCrudResolver<Domain, InputDTO, ID>`
   - [ ] All InputDTOs have `@Introspected`, `@Serdeable`, and extend `BaseInputDTO`
   - [ ] All mappers have `@Singleton` annotation
   - [ ] Package names follow `io.github.salomax.neotool.{module}.{layer}` pattern
   - [ ] All required imports are present (see backend-quick-reference.md)

3. Frontend Implementation (if applicable):
   - Follow component-pattern.md for components
   - Follow page-pattern.md for pages
   - Follow hook-pattern.md for hooks
   - Follow graphql-pattern.md for GraphQL operations
   - Follow styling-pattern.md for styling
   - Add i18n support
   - Follow testing patterns for tests

4. Ensure type safety end-to-end:
   - Generate GraphQL types after schema changes
   - Use TypeScript types throughout frontend
   - Use Kotlin types throughout backend

5. Include comprehensive tests (REQUIRED):
   - Unit tests for all business logic (spec/05-standards/testing-rules.md)
     * Must achieve 90%+ line coverage and 85%+ branch coverage
     * Test all conditional branches (if/when/switch/guard clauses)
     * Use descriptive test names with backticks
     * Follow Arrange-Act-Assert pattern
   - Integration tests for API endpoints (spec/05-standards/testing-rules.md)
     * Must achieve 80%+ line coverage and 75%+ branch coverage
     * Test all GraphQL mutations/queries
     * Test error cases and edge cases
   - E2E tests for user flows from feature file
     * Cover all scenarios from the feature file
     * Test both success and failure paths

6. Follow all rules from spec/05-standards/:
   - Coding standards
   - Architecture rules
   - API rules
   - Database rules
   - Testing rules (REQUIRED - see above)
   - Security rules
   - Observability rules (REQUIRED - see below)

7. Include observability (REQUIRED - spec/05-standards/observability-rules.md):
   - Metrics are automatically tracked via GraphQLMetricsInstrumentation
   - Create Grafana dashboard for business metrics (if applicable):
     * Location: `infra/observability/grafana/dashboards/{feature-name}-metrics.json`
     * Include success/failure rates, latency percentiles, and business-specific metrics
     * Use PromQL queries filtering by operation name and module
     * Example: For signup, track `graphql_operation_success_count{module="security", operation="signUp"}`
   - Verify metrics are exposed at `/prometheus` endpoint
   - Dashboard should visualize:
     * Success/failure rates over time
     * Latency percentiles (P50, P95, P99)
     * Total operations count
     * Error rate percentage

8. After each phase:
   - Run schema sync if GraphQL schema changed
   - Generate types if schema changed
   - Run tests to verify phase completion

9. **Before completing, verify compilation and linting**:
   - Backend: Run `./gradlew ktlintCheck` to verify no lint errors
   - Backend: Run `./gradlew compileKotlin` to verify no compilation errors
   - Frontend: Run `pnpm run lint` to verify no lint errors
   - Frontend: Run `pnpm run typecheck` to verify no TypeScript errors
   - Fix any errors before marking implementation as complete

10. **Common Errors to Avoid**:
    - **Entity**: Missing `@Entity`, `@Version`, class not `open`, properties not `open var`
    - **Repository**: Missing `@Repository`, wrong generic types
    - **Service**: Missing `@Singleton`, missing `@Transactional` on writes, methods not `open`
    - **Resolver**: Missing `@Singleton`, missing `CrudService` adapter, missing validator
    - **InputDTO**: Missing `@Introspected`, `@Serdeable`, or `BaseInputDTO` extension
    - **Domain-Entity**: UUID? to UUID conversion without `id ?: UUID.randomUUID()`
    - **Package**: Wrong package name (must be `io.github.salomax.neotool.{module}.{layer}`)
    - **Imports**: Missing required imports (see backend-quick-reference.md)

## Deliverables

Implement all artifacts from the implementation plan:
- [ ] All backend artifacts (entities, repositories, services, resolvers, migrations, schemas)
- [ ] All frontend artifacts (pages, components, hooks, GraphQL operations, i18n)
- [ ] **Unit tests** for all business logic (90%+ coverage, all branches tested)
- [ ] **Integration tests** for all API endpoints (80%+ coverage)
- [ ] **E2E tests** for user flows from feature file
- [ ] **Grafana dashboard** for business metrics (if applicable)
- [ ] Schema synced to contracts
- [ ] TypeScript types generated
- [ ] All tests passing

Build the complete implementation now, following the plan phase by phase.
```

