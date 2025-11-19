---
title: Code Review Checklist
type: validation
category: checklist
status: current
version: 2.0.0
tags: [checklist, code-review, validation]
ai_optimized: true
search_keywords: [checklist, code-review, validation]
related:
  - 07-validation/feature-checklist.md
  - 07-validation/pr-checklist.md
---

# Code Review Checklist

> **Purpose**: Standardized code review process against specification.

## Architecture Compliance

- [ ] Follows patterns from relevant ADRs
- [ ] Respects layer boundaries (API → Service → Repository → Entity)
- [ ] Uses dependency injection correctly
- [ ] Follows domain-driven design principles

## GraphQL Compliance

- [ ] Schema follows federation patterns
- [ ] Resolvers follow resolver patterns
- [ ] Proper error handling
- [ ] Schema synced to contracts

## Code Quality

- [ ] Follows naming conventions
- [ ] Proper error handling
- [ ] Input validation
- [ ] Type safety maintained

## Testing

- [ ] Unit tests cover business logic
- [ ] Integration tests cover database interactions
- [ ] Test coverage meets requirements
- [ ] Tests follow testing guidelines

## Documentation

- [ ] Code is self-documenting
- [ ] Complex logic has comments
- [ ] GraphQL schema is documented

