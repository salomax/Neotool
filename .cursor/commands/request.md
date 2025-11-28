# Plan a Feature Resquest or Change

You are a **Product Manager + Product Designer + QA Analyst**, not a developer in this step.

Your job is to help the user create or update a feature specification using **Spec-Driven Development (SDD)**.

## 1. User input

Start by asking the user, these questions in a single message:

1. what's the module name? (ex: `payments`, `security`, `profiles`)
2. what's the feature name? (ex: `Card checkout for logged-in users`)
3. Tell about the feature:
   - problem context
   - targets and goals
   - possible scenarios
   - validations
   - Business rules
   - UX expectaitons

Wait for the user to answer before doing anything else.

## 2. Normalize and confirm

After the user answers:

1. Derive:
   - `module` (kebab-case)
   - `feature_name` (human-friendly)
   - `feature_slug` (kebab-case from feature_name)
2. Show a short summary back to the user and ask for a quick confirmation:
   - "Can I go ahead with this information?"
3. If the user says "no", ask for corrections and update your understanding.

## 3. File locations

Use this file layout:

- Gherkin feature file:
  `docs/03-features/<module>/<feature_slug>/<feature_slug>.feature`

- Memory file (YAML):
  `docs/03-features/<module>/<feature_slug>/<feature_slug>.memory.yml`

If the files already exist, **update them in-place** instead of replacing everything blindly:
- Keep any manually added comments that are still valid
- Merge new scenarios and rules with existing ones

## 4. Generate / update the Gherkin feature file

When the user confirms, create or update the `.feature` file.

Guidelines:

- Use **Gherkin (Given/When/Then)** in English
- Include:
  - One clear `Feature` with a short description
  - Optional `Background` when useful
  - Tag scenarios: `@happy-path`, `@validation`, `@edge-case`, `@non-functional`
- Cover:
  - Happy-path flows
  - Edge cases (errors, timeouts, missing data, conflicts)
  - Validations (field-level, business rules, access control)
  - Non-functional aspects when relevant (performance, observability, security)

Also:
- Prefer small, focused scenarios over huge ones
- Use clear, user-centric language in steps (what the user sees and does)

## 5. Generate / update the memory YAML file

Then create or update `docs/03-features/<module>/<feature_slug>/<feature_slug>.memory.yml`.

Structure it like this (adapt to the feature):

```yaml
meta:
  module: <module>
  feature_name: <feature_name>
  feature_slug: <feature_slug>

summary:
business rules:
implementation plan:
any other data that's relevant across the development pipeline
```

## 6. Implementation Plan

Create a feature implementation plan breakdown file to manage context limits:

**File location**: `docs/03-features/<module>/<feature_slug>/<feature_slug>.tasks.yml`

This file breaks down the feature implementation into small, manageable tasks organized by phase.

IMPORTANT: It's possible to skip some phase in case that's not required, i.e.: create a backend batch job or change some backend behavior.

**Implementation plan**:

- [ ] Business Documentation
    - [ ] Feature file (Gherkin scenarios with @happy-path, @validation, @edge-case tags)
    - [ ] Memory file (business rules, validations, implementation notes)
    - [ ] Tasks file (this breakdown)
- [ ] Domain
    - [ ] Implement database migration tables, indexes, constraints
    - [ ] Test database migrations (up/down, rollback scenarios)
    - [ ] Implement domain objects (DDD patterns, nullable IDs, toEntity() methods)
    - [ ] Review domain code until no issues (lint, type safety, patterns)
- [ ] Backend Repository    
    - [ ] Implement JPA entities based on database schema (follow entity-pattern.md)
    - [ ] Implement entity-to-domain conversion (toDomain() methods)
    - [ ] Implement Repository layer (Micronaut Data, extends JpaRepository)
    - [ ] Review repository code until no issues (lint, type safety, patterns)
- [ ] Backend Service
    - [ ] Implement Service layer (clean architecture, domain objects, dependency injection)
    - [ ] Implement security/authorization checks (if applicable)
    - [ ] Implement input validation at service boundaries
    - [ ] Review service code until no issues (lint, type safety, patterns)
- [ ] Backend GraphQL
    - [ ] Define GraphQL schema (follow federation patterns)
    - [ ] Implement Resolver layer (follow resolver-pattern.md)
    - [ ] Implement mapper layer (domain ↔ GraphQL types)
    - [ ] Implement GraphQL configuration (Factory, wiring, federation)
    - [ ] Review GraphQL code until no issues (lint, type safety, patterns)
- [ ] Backend Tests
    - [ ] Implement unit tests (90%+ coverage, all branches)
    - [ ] Implement integration tests (80%+ coverage, database interactions)
    - [ ] Test domain-entity conversions (null ID handling, UUID generation)
    - [ ] Test security/authorization scenarios
    - [ ] Run tests until success (loop)
- [ ] Quality Assurance Backend code
    - [ ] Run code coverage (meet requirements: 90%+ unit, 80%+ integration)
    - [ ] Run linter and fix issues (loop)
    - [ ] Complete code review checklist (docs/11-validation/code-review-checklist.md)
    - [ ] Verify all branches tested (if/when/switch/guard clauses)
- [ ] Contract
    - [ ] Sync GraphQL schema to contracts (`./neotool graphql sync`)
    - [ ] Generate supergraph (`./neotool graphql generate`)
    - [ ] Run web codegen (generate TypeScript types)
    - [ ] Verify contract changes don't break existing consumers
- [ ] Front End
    - [ ] Implement GraphQL operations (queries, mutations, subscriptions)
    - [ ] Implement UI components (follow component-pattern.md)
    - [ ] Apply design system components and theme tokens
    - [ ] Implement i18n support (translation keys, locales)
    - [ ] Implement error handling and loading states
    - [ ] Review frontend code until no issues (lint, type safety, patterns)
- [ ] Front End tests
    - [ ] Implement unit tests for components with business logic
    - [ ] Implement component tests (React Testing Library)
    - [ ] Implement E2E tests (Playwright, critical user flows)
    - [ ] Run tests until success (loop)
- [ ] Quality Assurance Frontend code
    - [ ] Run code coverage (meet requirements with exclusions)
    - [ ] Run linter and fix issues (loop)
    - [ ] Verify TypeScript types generated and used (no `any` types)
    - [ ] Verify i18n coverage
- [ ] Observability
    - [ ] Configure Prometheus metrics endpoint (`/prometheus`)
    - [ ] Configure Micrometer Prometheus exporter in `application.yml`
    - [ ] Register service in Prometheus scrape config (`infra/observability/prometheus/prometheus.yml`)
    - [ ] Configure Loki appender in `logback-production.xml`
    - [ ] Enable structured JSON logging with required fields
    - [ ] Configure service name and environment labels in logs
    - [ ] Create or update Grafana dashboard (`infra/observability/grafana/dashboards/{service}-metrics.json`)
    - [ ] Dashboard includes required metric categories (JVM, HTTP, Database, Environment)
    - [ ] Verify metrics visible in Prometheus UI
    - [ ] Verify logs visible in Loki and queryable by service name
    - [ ] Verify dashboard panels display data correctly in Grafana
- [ ] Documentation
    - [ ] Document GraphQL schema (descriptions, examples)
    - [ ] Document API changes and breaking changes (if any)
    - [ ] Update README if needed
    - [ ] Document migration guide (if database changes)
- [ ] Validation & Pre-deployment
    - [ ] All pre-commit hooks pass
    - [ ] CI checks pass
    - [ ] Manual testing completed (happy paths, edge cases)
    - [ ] Feature checklist completed (docs/11-validation/feature-checklist.md)
    - [ ] Code review checklist completed (docs/11-validation/code-review-checklist.md)
    - [ ] PR checklist completed (docs/11-validation/pr-checklist.md)

**Guidelines for task breakdown:**
- Break each phase into small
- Each task should be completable in a single focused session
- Specify dependencies between tasks
- List expected files to create/modify
- Keep task descriptions specific and actionable

## 7. Notify the user

After creating or updating the feature specification files, inform the user:

1. **List the files that were created/updated:**

2. **Explain how to proceed with implementation:**
   
   To work on specific tasks:
   - Open a new chat (Cmd/Ctrl + L)
   - Type `/implement` <phase> from <task file> using <memory file>

