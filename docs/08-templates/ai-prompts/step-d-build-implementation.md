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
   - Follow entity-pattern.md for entities
   - Follow repository-pattern.md for repositories
   - Follow service-pattern.md for services
   - Follow resolver-pattern.md for resolvers
   - Follow database-rules.md for migrations
   - Follow api-rules.md for GraphQL schema
   - Follow testing-pattern.md and testing-rules.md for tests

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

