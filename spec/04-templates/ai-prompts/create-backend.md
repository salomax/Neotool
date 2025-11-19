---
title: Create Backend Feature AI Prompt
type: template
category: ai-prompt
status: current
version: 2.0.0
tags: [ai-prompt, backend, feature-creation]
ai_optimized: true
search_keywords: [ai-prompt, backend, kotlin, micronaut, feature]
related:
  - 03-patterns/backend/
  - 01-rules/database-rules.md
  - 01-rules/api-rules.md
---

# Create Backend Feature AI Prompt

> **Purpose**: AI prompt template for creating backend features.

## Usage

Use this prompt when implementing backend features. Reference the feature form and implementation plan.

## Prompt Template

```
Create a backend feature for NeoTool following the specification.

## Feature Details

[Feature name and description from feature form]

## Backend Requirements

[Backend section from feature form]

## Specification References

- Entity Pattern: spec/03-patterns/backend/entity-pattern.md
- Repository Pattern: spec/03-patterns/backend/repository-pattern.md
- Service Pattern: spec/03-patterns/backend/service-pattern.md
- Resolver Pattern: spec/03-patterns/backend/resolver-pattern.md
- Testing Pattern: spec/03-patterns/backend/testing-pattern.md
- Database Rules: spec/01-rules/database-rules.md
- API Rules: spec/01-rules/api-rules.md
- Testing Rules: spec/01-rules/testing-rules.md

## Implementation Requirements

1. Create entity following entity-pattern.md
2. Create repository following repository-pattern.md
3. Create service following service-pattern.md
4. Create resolver following resolver-pattern.md
5. Create GraphQL schema following api-rules.md
6. Create database migration following database-rules.md
7. Create tests following testing-pattern.md and testing-rules.md

## Deliverables

- Entity class
- Repository interface
- Service class
- Resolver class
- GraphQL schema
- Database migration
- Unit tests
- Integration tests

Generate the complete backend implementation.
```

