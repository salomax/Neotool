---
title: Feature Completion Checklist
type: validation
category: checklist
status: current
version: 2.0.0
tags: [checklist, feature, validation]
ai_optimized: true
search_keywords: [checklist, feature, validation, completion]
related:
  - 07-validation/code-review-checklist.md
  - 07-validation/pr-checklist.md
---

# Feature Completion Checklist

> **Purpose**: Checklist to ensure all feature requirements are met before PR.

## Backend

- [ ] GraphQL schema follows federation patterns (spec/04-patterns/shared/graphql-federation.md)
- [ ] Entity follows JPA patterns (spec/04-patterns/backend/entity-pattern.md)
- [ ] Entity includes schema in @Table annotation (spec/05-standards/database-rules.md)
- [ ] Service follows clean architecture
- [ ] Repository extends appropriate base class
- [ ] Resolver follows resolver patterns (spec/04-patterns/backend/resolver-pattern.md)
- [ ] Database migration created and tested
- [ ] Schema synced to contracts (`./neotool graphql sync`)
- [ ] Unit tests written (spec/05-standards/testing-rules.md)
- [ ] Integration tests written
- [ ] Test coverage meets requirements (90%+ unit, 80%+ integration)
- [ ] All branches tested (if/when/switch/guard clauses)

## Frontend

- [ ] Components follow structure (spec/04-patterns/frontend/component-pattern.md)
- [ ] Uses design system components
- [ ] Applies theme tokens
- [ ] GraphQL operations follow patterns (spec/04-patterns/frontend/graphql-pattern.md)
- [ ] i18n support added
- [ ] TypeScript types generated and used
- [ ] Toast notifications implemented for all mutations (spec/04-patterns/frontend-patterns/toast-notification-pattern.md)
  - [ ] Success toasts for create, update, delete operations
  - [ ] Error toasts with extracted error messages
  - [ ] Toast messages internationalized (i18n)
- [ ] Tests written

## Observability

- [ ] Prometheus metrics endpoint enabled at `/prometheus` (spec/05-standards/observability-rules.md)
- [ ] Micrometer Prometheus exporter configured in `application.yml`
- [ ] Service registered in Prometheus scrape config (`infra/observability/prometheus/prometheus.yml`)
- [ ] Loki appender configured in `logback-production.xml`
- [ ] Structured JSON logging enabled with required fields
- [ ] Service name and environment labels configured in logs
- [ ] Grafana dashboard created or updated (`infra/observability/grafana/dashboards/{service-name}-metrics.json`)
- [ ] Dashboard includes all required metric categories (JVM, HTTP, Database, Environment)
- [ ] Metrics visible in Prometheus UI
- [ ] Logs visible in Loki and queryable by service name
- [ ] Dashboard panels display data correctly in Grafana

## Documentation

- [ ] GraphQL schema documented
- [ ] API changes documented
- [ ] Breaking changes noted (if any)

## Validation

- [ ] All pre-commit hooks pass
- [ ] CI checks pass
- [ ] Manual testing completed
- [ ] Code review checklist completed

