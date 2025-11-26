---
title: Step E: Validate Implementation
type: template
category: ai-prompt
status: current
version: 2.0.0
tags: [ai-prompt, validation, checklist]
ai_optimized: true
search_keywords: [ai-prompt, validation, checklist, rules]
related:
  - 07-validation/feature-checklist.md
  - 04-templates/ai-prompts/step-d-build-implementation.md
---

# Step E: Validate Implementation

> **Purpose**: AI prompt to validate the implementation against all rules and checklists.

## Usage

Use this prompt after implementation is complete from Step D.

## Prompt Template

```
Validate the feature implementation against all NeoTool rules and checklists.

## Implementation

[Brief description of what was implemented]

## Feature Form

[Paste Feature Form from Step B]

## Feature File

[Paste feature file from docs/03-features/[category]/[feature-name].feature]

## Specification References

- Feature Checklist: spec/07-validation/feature-checklist.md
- Code Review Checklist: spec/07-validation/code-review-checklist.md
- Rules: spec/05-standards/
- Patterns: spec/04-patterns/

## Validation Requirements

1. Run the Feature Checklist (spec/07-validation/feature-checklist.md):
   - Check all backend items
   - Check all frontend items
   - Check all documentation items
   - Check all validation items

2. Validate against all rules (spec/05-standards/):
   - Coding Standards: spec/05-standards/coding-standards.md
   - Architecture Rules: spec/05-standards/architecture-rules.md
   - API Rules: spec/05-standards/api-rules.md
   - Database Rules: spec/05-standards/database-rules.md
   - Testing Rules: spec/05-standards/testing-rules.md
   - Security Rules: spec/05-standards/security-rules.md

3. Validate against patterns (spec/04-patterns/):
   - Backend patterns (entity, repository, service, resolver, testing)
   - Frontend patterns (component, page, hook, GraphQL, styling)
   - Shared patterns (GraphQL federation, error handling)

4. Verify implementation matches feature file:
   - All scenarios from feature file are implemented
   - All rules from feature file are implemented
   - E2E tests cover all scenarios

5. Run automated validations:
   - Pre-commit hooks
   - Linting
   - Type checking
   - Tests (unit, integration, E2E)
   - Schema validation
   - GraphQL schema sync verification

6. Check for common issues:
   - Missing tests
   - Missing error handling
   - Missing validation
   - Missing i18n
   - Missing accessibility
   - Type safety issues
   - Security vulnerabilities

## Output Format

Provide a validation report with:
- ✅ Passed checks (with evidence)
- ❌ Failed checks (with details and file locations)
- ⚠️ Warnings (with recommendations)
- 📝 Missing items (with what needs to be added)

For each failed check or warning, provide:
- Rule/pattern violated
- File and line number
- What needs to be fixed
- How to fix it

Run the complete validation now.
```

