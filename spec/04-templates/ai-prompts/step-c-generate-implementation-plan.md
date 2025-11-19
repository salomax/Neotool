---
title: Step C: Generate Implementation Plan
type: template
category: ai-prompt
status: current
version: 2.0.0
tags: [ai-prompt, planning, implementation-plan]
ai_optimized: true
search_keywords: [ai-prompt, plan, implementation, checklist]
related:
  - 04-templates/ai-prompts/step-b-generate-form-and-feature-file.md
  - 04-templates/ai-prompts/step-d-build-implementation.md
---

# Step C: Generate Implementation Plan

> **Purpose**: AI prompt to generate implementation plan from Feature Form + feature file + spec after manual review.

## Usage

Use this prompt after manual review of Feature Form and feature file from Step B.

## Prompt Template

```
Generate a comprehensive implementation plan for a NeoTool feature.

## Feature Form

[Paste completed Feature Form from Step B]

## Feature File

[Paste feature file content from docs/features/[category]/[feature-name].feature]

## Specification References

- Architecture: spec/00-core/architecture.md
- Project Structure: spec/00-core/project-structure.md
- Technology Stack: spec/00-core/technology-stack.md
- Rules: spec/01-rules/
- Patterns: spec/03-patterns/
- Workflows: spec/06-workflows/
- Examples: spec/05-examples/

## Requirements

Generate an implementation plan that includes:

1. Complete artifact checklist (all files, tests, schemas, migrations)
   - Backend artifacts (entities, repositories, services, resolvers, migrations)
   - Frontend artifacts (pages, components, hooks, GraphQL operations)
   - GraphQL schema changes
   - Test files (unit, integration, E2E)
   - Documentation files

2. Implementation phases in correct order:
   - Phase 1: Backend (if applicable)
     - Database migration
     - Entity
     - Repository
     - Service
     - Resolver
     - GraphQL schema
   - Phase 2: Schema & Types
     - Sync GraphQL schema to contracts
     - Generate TypeScript types
   - Phase 3: Frontend (if applicable)
     - GraphQL operations
     - Components
     - Pages
     - Hooks
     - i18n
   - Phase 4: Integration & Testing
     - Integration tests
     - E2E tests
     - Manual testing

3. Exact file paths for all artifacts:
   - Use absolute paths or paths relative to workspace root
   - Follow directory structure from spec/00-core/project-structure.md

4. Spec references for each artifact:
   - Reference specific pattern documents
   - Reference specific rule documents
   - Reference example files if applicable

5. Dependencies between artifacts:
   - Show what must be created before what
   - Show what depends on what

6. Validation steps:
   - When to run schema sync
   - When to run type generation
   - When to run tests
   - When to validate against checklist

## Plan Format

- Use markdown format
- Include checkboxes for tracking progress
- Reference specific spec documents with paths
- Provide exact file paths
- Include implementation order
- Group by phase

Generate the complete implementation plan.
```

