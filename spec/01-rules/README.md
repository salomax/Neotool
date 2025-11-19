---
title: Rules Index
type: rule
category: index
status: current
version: 2.0.0
tags: [rules, index, guidelines, constraints]
ai_optimized: true
search_keywords: [rules, guidelines, constraints, standards]
related:
  - 00-core/principles.md
---

# Rules Index

> **Purpose**: Index of all rules and constraints that must be followed in NeoTool development.

## Overview

Rules are explicit constraints and guidelines that must be followed when developing features in NeoTool. These rules ensure consistency, quality, and maintainability across the codebase.

## Rule Categories

### [Coding Standards](./coding-standards.md)
- Code style and formatting
- Naming conventions
- File organization
- Code structure

### [Architecture Rules](./architecture-rules.md)
- Architecture constraints
- Layer boundaries
- Dependency rules
- Module organization

### [API Rules](./api-rules.md)
- GraphQL schema rules
- REST API rules (if applicable)
- Federation patterns
- API versioning

### [Database Rules](./database-rules.md)
- Schema organization
- Migration rules
- Entity patterns
- Query patterns

### [Testing Rules](./testing-rules.md)
- Test requirements
- Coverage requirements
- Test patterns
- Test organization

### [Security Rules](./security-rules.md)
- Authentication patterns
- Authorization rules
- Data protection
- Security best practices

## Rule Format

Each rule follows this format:

1. **Rule**: Clear statement of what must be done
2. **Rationale**: Why this rule exists
3. **Example**: Code example showing correct usage
4. **Exception**: When the rule may not apply (if any)

## Rule Enforcement

- **Pre-commit hooks**: Automated validation where possible
- **Code review**: Manual review against rules
- **CI/CD**: Automated checks in pipelines
- **Documentation**: Rules documented here

## Related Documentation

- [Core Principles](../00-core/principles.md) - Design philosophy
- [Patterns](../03-patterns/) - Implementation patterns
- [Validation](../07-validation/) - Validation checklists

