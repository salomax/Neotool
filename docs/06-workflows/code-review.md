---
title: Code Review Workflow
type: workflow
category: review
status: current
version: 2.1.0
tags: [workflow, code-review, process, spec-driven]
ai_optimized: true
search_keywords: [workflow, code-review, spec-compliance, validation]
related:
  - 11-validation/code-review-checklist.md
  - 06-workflows/spec-context-strategy.md
  - 05-standards/coding-standards/kotlin-standards.md
  - 05-standards/coding-standards/linting-standards.md
---

# Code Review Workflow

> **Purpose**: Spec-Driven code review process ensuring implementation follows specification, patterns, and standards.

## Overview

Code review in NeoTool is **Spec-Driven**, meaning every review validates against:
1. **Feature Specification**: Gherkin feature file and memory file
2. **Patterns**: Implementation patterns from `docs/04-patterns/`
3. **Standards**: Coding standards from `docs/05-standards/`
4. **Architecture**: Architecture decisions from `docs/09-adr/`

## Review Process

### Step 1: Pre-Review Preparation

**For Reviewers**:
1. **Load Feature Context**:
   - Read feature file: `docs/03-features/<module>/<feature>/<feature>.feature`
   - Read memory file: `docs/03-features/<module>/<feature>/<feature>.memory.yml`
   - Understand business rules and requirements

2. **Load Relevant Specs**:
   - **Backend Changes**: Load backend patterns and standards
   - **Frontend Changes**: Load frontend patterns and standards
   - **Database Changes**: Load database standards and entity patterns
   - **API Changes**: Load GraphQL standards and federation patterns

3. **Review PR Description**:
   - Verify PR references feature specification
   - Check that task breakdown is complete
   - Confirm all phases are addressed

### Step 2: Spec Compliance Review

**Check against Specification**:

1. **Feature File Compliance**:
   - [ ] All Gherkin scenarios implemented
   - [ ] Happy paths covered
   - [ ] Edge cases handled
   - [ ] Validations implemented
   - [ ] Error handling matches scenarios

2. **Business Rules Compliance**:
   - [ ] Business rules from memory file implemented
   - [ ] Validations match requirements
   - [ ] Authorization rules followed
   - [ ] Domain logic correct

3. **Pattern Compliance**:
   - [ ] Follows appropriate pattern from `docs/04-patterns/`
   - [ ] Matches existing codebase patterns
   - [ ] Uses established conventions

### Step 3: Code Quality Review

**Use Code Review Checklist**:
- Reference: [Code Review Checklist](../11-validation/code-review-checklist.md)

**Key Areas**:
1. **Architecture**:
   - [ ] Follows clean architecture layers
   - [ ] Respects module boundaries
   - [ ] No circular dependencies

2. **Type Safety**:
   - [ ] No `any` types (TypeScript)
   - [ ] Proper null handling (Kotlin)
   - [ ] Type-safe GraphQL operations

3. **Code Style**:
   - [ ] Follows coding standards
   - [ ] Consistent naming conventions
   - [ ] Proper formatting

4. **Testing**:
   - [ ] Unit tests present (90%+ coverage)
   - [ ] Integration tests present (80%+ coverage)
   - [ ] All branches tested
   - [ ] E2E tests for critical flows

### Step 4: Spec Reference Validation

**For AI-Assisted Reviews**:

1. **Pattern Validation**:
   - Compare implementation against pattern docs
   - Check for pattern violations
   - Suggest pattern improvements

2. **Standard Validation**:
   - Verify compliance with coding standards
   - Check linting standards adherence
   - Validate architecture standards

3. **Cross-Reference Check**:
   - Verify related spec documents referenced
   - Check for missing spec references
   - Ensure spec links are accurate

### Step 5: Feedback & Iteration

**Provide Constructive Feedback**:

1. **Spec Violations** (Must Fix):
   - Reference specific spec document
   - Quote relevant section
   - Provide clear fix guidance

2. **Pattern Improvements** (Should Fix):
   - Suggest better pattern usage
   - Reference pattern document
   - Show example if helpful

3. **Quality Improvements** (Nice to Have):
   - Code clarity suggestions
   - Performance optimizations
   - Documentation improvements

### Step 6: Approval Criteria

**Approve when**:
- ✅ All spec compliance checks pass
- ✅ All code quality checks pass
- ✅ All tests passing
- ✅ Linting passes
- ✅ Feature checklist complete
- ✅ No blocking issues

## Spec-Driven Review Checklist

### Backend Review
- [ ] Entity follows [Entity Pattern](../04-patterns/backend-patterns/entity-pattern.md)
- [ ] Repository follows [Repository Pattern](../04-patterns/backend-patterns/repository-pattern.md)
- [ ] Service follows [Service Pattern](../04-patterns/backend-patterns/service-pattern.md)
- [ ] Resolver follows [Resolver Pattern](../04-patterns/backend-patterns/resolver-pattern.md)
- [ ] Follows [Kotlin Standards](../05-standards/coding-standards/kotlin-standards.md)
- [ ] Database schema follows [Schema Standards](../05-standards/database-standards/schema-standards.md)

### Frontend Review
- [ ] Components follow [Shared Components Pattern](../04-patterns/frontend-patterns/shared-components-pattern.md)
- [ ] GraphQL queries follow [Query Pattern](../04-patterns/frontend-patterns/graphql-query-pattern.md)
- [ ] GraphQL mutations follow [Mutation Pattern](../04-patterns/frontend-patterns/graphql-mutation-pattern.md)
- [ ] Hooks follow [Management Pattern](../04-patterns/frontend-patterns/management-pattern.md)
- [ ] Uses design system components
- [ ] i18n support added

### API Review
- [ ] GraphQL schema follows [GraphQL Standards](../05-standards/api-standards/graphql-standards.md)
- [ ] Query resolver naming follows [GraphQL Query Pattern](../04-patterns/api-patterns/graphql-query-pattern.md)
- [ ] Schema synced to contracts
- [ ] No breaking changes (or documented)

## Context Strategy for Reviewers

**For AI Assistants**:

1. **Load Feature Context First**:
   - Feature file and memory file
   - Task breakdown file

2. **Load Phase-Specific Specs**:
   - Only load specs relevant to changed files
   - Use MANIFEST.md for quick lookup

3. **Reference, Don't Copy**:
   - Reference spec paths in comments
   - Quote specific sections when needed
   - Link to full spec documents

See [Spec Context Strategy](./spec-context-strategy.md) for detailed guidance.

## Review Guidelines

### Do's
- ✅ Reference specific spec documents
- ✅ Quote relevant spec sections
- ✅ Suggest pattern improvements
- ✅ Be constructive and respectful
- ✅ Focus on spec compliance
- ✅ Verify test coverage

### Don'ts
- ❌ Review without spec context
- ❌ Make style-only changes without spec basis
- ❌ Approve without spec compliance check
- ❌ Skip pattern validation
- ❌ Ignore test requirements

## Related Documentation

- [Code Review Checklist](../11-validation/code-review-checklist.md) - Detailed checklist
- [Spec Context Strategy](./spec-context-strategy.md) - Context optimization
- [Feature Development Workflow](./feature-development.md) - Development process
- [Linting Standards](../05-standards/coding-standards/linting-standards.md) - Code quality rules

