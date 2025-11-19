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

- [ ] GraphQL schema follows federation patterns (spec/03-patterns/shared/graphql-federation.md)
- [ ] Entity follows JPA patterns (spec/03-patterns/backend/entity-pattern.md)
- [ ] Entity includes schema in @Table annotation (spec/01-rules/database-rules.md)
- [ ] Service follows clean architecture
- [ ] Repository extends appropriate base class
- [ ] Resolver follows resolver patterns (spec/03-patterns/backend/resolver-pattern.md)
- [ ] Database migration created and tested
- [ ] Schema synced to contracts (`./neotool graphql sync`)
- [ ] Unit tests written (spec/01-rules/testing-rules.md)
- [ ] Integration tests written
- [ ] Test coverage meets requirements (90%+ unit, 80%+ integration)
- [ ] All branches tested (if/when/switch/guard clauses)

## Frontend

- [ ] Components follow structure (spec/03-patterns/frontend/component-pattern.md)
- [ ] Uses design system components
- [ ] Applies theme tokens
- [ ] GraphQL operations follow patterns (spec/03-patterns/frontend/graphql-pattern.md)
- [ ] i18n support added
- [ ] TypeScript types generated and used
- [ ] Tests written

## Documentation

- [ ] GraphQL schema documented
- [ ] API changes documented
- [ ] Breaking changes noted (if any)

## Validation

- [ ] All pre-commit hooks pass
- [ ] CI checks pass
- [ ] Manual testing completed
- [ ] Code review checklist completed

