---
title: Pull Request Checklist
type: validation
category: checklist
status: current
version: 2.0.0
tags: [checklist, pr, pull-request, validation]
ai_optimized: true
search_keywords: [checklist, pr, pull-request]
related:
  - 11-validation/feature-checklist.md
  - 11-validation/code-review-checklist.md
  - 05-standards/coding-standards/linting-standards.md
---

# Pull Request Checklist

> **Purpose**: Checklist for creating pull requests.

## Before Creating PR

- [ ] Feature checklist completed
- [ ] Code review checklist completed
- [ ] **Lint checks pass** (see [Linting Standards](../05-standards/coding-standards/linting-standards.md))
  - [ ] Backend: `./gradlew ktlintCheck` passes with zero errors
  - [ ] Frontend: `pnpm run lint` passes with zero errors and zero warnings
  - [ ] Frontend: `pnpm run typecheck` passes with zero errors
- [ ] All tests passing
- [ ] Documentation updated

## PR Description

- [ ] Clear description of changes
- [ ] Link to feature form/questionnaire
- [ ] Link to implementation plan (if applicable)
- [ ] Breaking changes documented
- [ ] Migration guide (if database changes)

## Review

- [ ] Code review completed
- [ ] All feedback addressed
- [ ] CI/CD checks passing
- [ ] Ready to merge

