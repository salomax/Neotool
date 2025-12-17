---
title: Feature Development Workflow
type: workflow
category: development
status: current
version: 2.1.0
tags: [workflow, feature-development, process, spec-driven]
ai_optimized: true
search_keywords: [workflow, feature-development, process, spec-driven, sdd]
related:
  - 08-templates/feature-templates/feature-creation/workflow.md
  - 11-validation/feature-checklist.md
  - 05-standards/coding-standards/linting-standards.md
  - 06-workflows/spec-context-strategy.md
---

# Feature Development Workflow

> **Purpose**: Complete Spec-Driven Development workflow from idea to production, optimized for AI-assisted development with context management.

## Overview

This workflow guides feature development using **Spec-Driven Development (SDD)** principles, where the specification drives implementation rather than code driving documentation. The workflow is optimized for AI assistants with clear context navigation strategies.

## Spec-Driven Development Principles

1. **Specification First**: Always reference specification before implementation
2. **Pattern-Based**: Use documented patterns from `docs/04-patterns/`
3. **Template-Driven**: Leverage templates from `docs/08-templates/`
4. **Validation-Focused**: Use checklists from `docs/11-validation/`
5. **Context-Optimized**: Reference only relevant spec sections to manage context limits

## Workflow Phases

### Phase 1: Specification & Planning

**Goal**: Create complete feature specification before any code.

#### Step 1.1: Feature Discovery
- **Action**: Use `/request` command or follow [Feature Creation Workflow](../08-templates/feature-templates/feature-creation/workflow.md)
- **Output**: Feature specification files:
  - `docs/03-features/<module>/<feature>/<feature>.feature` (Gherkin)
  - `docs/03-features/<module>/<feature>/<feature>.memory.yml` (Business rules)
  - `docs/03-features/<module>/<feature>/<feature>.tasks.yml` (Task breakdown)

#### Step 1.2: Spec Context Loading
**For AI Assistants**: Load relevant specification context:

**Essential Context** (Always load):
- `docs/MANIFEST.md` - Document index
- `docs/00-overview/architecture-overview.md` - System architecture
- `docs/02-domain/glossary.md` - Terminology

**Phase-Specific Context** (Load as needed):
- **Domain Phase**: 
  - `docs/05-standards/database-standards/schema-standards.md`
  - `docs/04-patterns/backend-patterns/entity-pattern.md`
  - `docs/04-patterns/backend-patterns/uuid-v7-pattern.md`
- **Backend Phase**:
  - `docs/04-patterns/backend-patterns/resolver-pattern.md`
  - `docs/04-patterns/backend-patterns/service-pattern.md`
  - `docs/04-patterns/backend-patterns/repository-pattern.md`
  - `docs/05-standards/coding-standards/kotlin-standards.md`
- **Frontend Phase**:
  - `docs/04-patterns/frontend-patterns/component-pattern.md`
  - `docs/04-patterns/frontend-patterns/graphql-query-pattern.md`
  - `docs/04-patterns/frontend-patterns/graphql-mutation-pattern.md`
  - `docs/04-patterns/frontend-patterns/hook-pattern.md`

**Context Strategy**: See [Spec Context Strategy](./spec-context-strategy.md) for detailed guidance.

#### Step 1.3: Implementation Planning
- **Action**: Review task breakdown file (`.tasks.yml`)
- **Validation**: Ensure all phases are properly sequenced
- **Reference**: Use [Feature Creation Workflow](../08-templates/feature-templates/feature-creation/workflow.md) Step C

### Phase 2: Implementation

**Goal**: Build feature following specification and patterns.

#### Step 2.1: Domain Implementation
- **Context**: Load domain-related specs (see Step 1.2)
- **Actions**:
  - Create database migrations
  - Implement domain objects (DDD patterns)
  - Test migrations
- **Validation**: Follow [Entity Pattern](../04-patterns/backend-patterns/entity-pattern.md)

#### Step 2.2: Backend Implementation
- **Context**: Load backend-related specs (see Step 1.2)
- **Actions**:
  - Implement JPA entities
  - Implement repositories
  - Implement services
  - Implement GraphQL resolvers
- **Validation**: Follow [Resolver Pattern](../04-patterns/backend-patterns/resolver-pattern.md)

#### Step 2.3: Contract Synchronization
- **Action**: Sync GraphQL schema (`./neotool graphql sync`)
- **Action**: Generate TypeScript types (`npm run codegen`)
- **Validation**: Verify no breaking changes

#### Step 2.4: Frontend Implementation
- **Context**: Load frontend-related specs (see Step 1.2)
- **Actions**:
  - Implement GraphQL operations
  - Implement hooks
  - Implement components
  - Apply design system
  - Add i18n support
- **Validation**: Follow [Component Pattern](../04-patterns/frontend-patterns/component-pattern.md)

### Phase 3: Testing

**Goal**: Ensure feature meets specification and quality standards.

#### Step 3.1: Unit Tests
- **Standard**: 90%+ coverage, all branches tested
- **Reference**: [Testing Standards](../05-standards/testing-standards/unit-test-standards.md)
- **Pattern**: [Backend Testing Pattern](../04-patterns/backend-patterns/testing-pattern.md)

#### Step 3.2: Integration Tests
- **Standard**: 80%+ coverage
- **Reference**: [Testing Standards](../05-standards/testing-standards/unit-test-standards.md)

#### Step 3.3: E2E Tests
- **Coverage**: All Gherkin scenarios from feature file
- **Pattern**: [E2E Testing Pattern](../04-patterns/frontend-patterns/e2e-testing-pattern.md)

### Phase 4: Quality Assurance

**Goal**: Ensure code quality and spec compliance.

#### Step 4.1: Linting
- **Action**: Run lint checks
- **Standard**: [Linting Standards](../05-standards/coding-standards/linting-standards.md)
- **Requirement**: Fix all errors before proceeding

#### Step 4.2: Code Review
- **Checklist**: [Code Review Checklist](../11-validation/code-review-checklist.md)
- **Focus**: Spec compliance, pattern adherence, type safety

#### Step 4.3: Feature Validation
- **Checklist**: [Feature Checklist](../11-validation/feature-checklist.md)
- **Validation**: All requirements met

### Phase 5: Deployment

**Goal**: Deploy feature to production.

- **Process**: Follow [Deployment Workflow](./deployment-workflow.md)
- **Validation**: All checks pass, manual testing complete

## Context Management for AI Assistants

### Context Loading Strategy

1. **Start Narrow**: Load only essential context initially
2. **Expand as Needed**: Load phase-specific context when entering that phase
3. **Reference, Don't Copy**: Reference spec paths rather than copying full content
4. **Use Cross-References**: Follow `related:` links in spec frontmatter

### Context Optimization

- **Use MANIFEST.md**: Quick lookup for document locations
- **Load Patterns, Not Examples**: Load pattern docs, reference examples
- **Phase-Based Loading**: Only load specs relevant to current phase
- **Spec Chunking**: Break large specs into focused sections

See [Spec Context Strategy](./spec-context-strategy.md) for detailed guidance.

## Quick Reference

### Key Commands
```bash
# Feature specification
/request  # Create feature specification

# Implementation
/implement <phase> from <tasks.yml> using <memory.yml>

# Schema sync
./neotool graphql sync

# Type generation
npm run codegen

# Testing
./gradlew test
npm test

# Linting
./gradlew ktlintCheck
npm run lint
```

### Key Documents
- **Specification Index**: `docs/MANIFEST.md`
- **Architecture**: `docs/00-overview/architecture-overview.md`
- **Patterns**: `docs/04-patterns/`
- **Standards**: `docs/05-standards/`
- **Templates**: `docs/08-templates/`
- **Validation**: `docs/11-validation/`

## Related Documentation

- [Feature Creation Workflow](../08-templates/feature-templates/feature-creation/workflow.md) - Detailed 5-step process
- [Spec Context Strategy](./spec-context-strategy.md) - AI context optimization
- [Feature Checklist](../11-validation/feature-checklist.md) - Completion validation
- [Code Review Workflow](./code-review.md) - Review process
- [Testing Workflow](./testing-workflow.md) - Testing process

