---
title: Validation Scripts
type: validation
category: scripts
status: current
version: 2.0.0
tags: [validation, scripts, automation]
ai_optimized: true
search_keywords: [validation, scripts, automation]
---

# Validation Scripts

> **Purpose**: Documentation for validation scripts and tools.

## Available Scripts

### GraphQL Schema Validation
```bash
./neotool graphql validate
```

### Backend Pattern Validation
```bash
# To be implemented
./scripts/validate-backend-patterns.sh
```

### Frontend Pattern Validation
```bash
# To be implemented
./scripts/validate-frontend-patterns.sh
```

## Pre-commit Hooks

Pre-commit hooks run automatically on `git commit`:
- GraphQL schema validation
- Linting
- Type checking

## CI/CD Validation

CI/CD pipelines validate:
- Test coverage
- Linting
- Type checking
- Schema validation

