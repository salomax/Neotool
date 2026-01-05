---
title: AI Context - How to Use Documentation
type: ai-guide
category: meta
status: current
version: 1.0.0
date: 2026-01-04
tags: [ai, llm, context, patterns, guardrails]
---

# AI Context - How to Use This Documentation

> **Purpose**: Guide AI assistants (Cursor, Claude Code) on how to effectively use the documentation in this repository.

## Overview

This directory (`docs/99-ai-context/`) provides optimized entry points for AI assistants to:
- Understand architectural patterns quickly
- Load only relevant documentation
- Enforce non-negotiable guardrails
- Find reference implementations

## How AI Should Use Documentation

### 1. Read This First

When implementing a new feature:
1. Read the feature spec in `spec/features/[module]/[feature].md`
2. Check the "Context for AI" section in the spec
3. Load the referenced docs from this section
4. **Always load** `guardrails.md` (mandatory for all implementations)
5. Explore the code integration points listed in the spec
6. Implement following patterns + guardrails

### 2. What to Load When

See [load-by-scenario.md](./load-by-scenario.md) for scenario-specific loading instructions:
- Backend CRUD features
- GraphQL APIs
- Frontend components
- Database migrations
- Refactoring tasks

### 3. Guardrails (Always Enforce)

See [guardrails.md](./guardrails.md) for non-negotiable rules:
- Security requirements
- Testing standards
- Code boundaries
- Logging practices

**These are MANDATORY. If implementation violates guardrails, STOP and ask for clarification.**

### 4. Reference Implementations

See [examples.md](./examples.md) for annotated pointers to best-in-class code:
- AssetService (CRUD with audit)
- AuthenticationService (Security patterns)
- Settings UI (Form patterns)

## Priority Order

When there's a conflict:

```
1. Guardrails (docs/99-ai-context/guardrails.md)
   ↓ MANDATORY - never violate
2. Feature Spec (spec/features/[module]/[feature].md)
   ↓ What to build
3. Pattern Docs (docs/02-architecture/, docs/03-features/)
   ↓ How to structure
4. Code Examples (integration points in spec)
   ↓ Source of truth for implementation
5. If still unclear → Ask user
```

## Documentation Structure

```
docs/
├── 01-overview/              # Project overview, architecture
├── 02-architecture/          # Backend, frontend, infrastructure structure
├── 03-features/              # Feature-specific documentation
├── 04-development/           # Dev setup, tooling
├── 05-testing/               # Testing strategies
├── 06-deployment/            # Deployment guides
├── 07-operations/            # Monitoring, logging
├── 08-workflows/             # Development workflows (SDD, etc.)
├── 09-security/              # Security practices
├── 93-reference/             # API references, commands
└── 99-ai-context/            # THIS DIRECTORY - AI entry points
    ├── README.md             # This file
    ├── load-by-scenario.md   # What to load when
    ├── guardrails.md         # Non-negotiable rules
    └── examples.md           # Reference implementations
```

## Key Principles

### ✅ Do

- **Always load guardrails** - Security and testing rules are mandatory
- **Load only what's relevant** - Don't read entire docs/ directory
- **Verify against code** - If docs and code conflict, code is truth
- **Follow existing patterns** - Learn from reference implementations
- **Ask when unclear** - Better to clarify than guess

### ❌ Don't

- **Don't skip guardrails** - Even for "quick fixes"
- **Don't hallucinate patterns** - Use actual examples from codebase
- **Don't load everything** - Be selective based on scenario
- **Don't ignore spec context** - It tells you exactly what to load
- **Don't assume** - Verify unclear patterns against code

## Example Workflow

```
User: /spec-implement user-profile-management

AI:
1. ✅ Read spec/features/user/profile-management.md
2. ✅ Load docs/99-ai-context/guardrails.md (ALWAYS)
3. ✅ Check spec "Context for AI" section:
   - Load docs/02-architecture/backend-structure.md
   - Load docs/03-features/security/authorization.md
4. ✅ Explore code integration points:
   - /service/kotlin/asset/service/AssetService.kt
   - /service/kotlin/security/service/AuthenticationService.kt
5. ✅ Create implementation plan
6. ✅ Validate against guardrails
7. ✅ Implement following patterns + guardrails
```

## When to Load What

**For quick reference:**

| Scenario | Load These Docs | Plus Code Examples |
|----------|----------------|-------------------|
| Backend CRUD | architecture/backend-structure, security/authorization, 99-ai-context/guardrails | AssetService.kt |
| GraphQL API | architecture/backend-structure, features/graphql/federation, guardrails | Asset resolvers |
| Frontend Form | architecture/frontend-structure, features/forms, guardrails | Settings UI |
| Database Schema | architecture/database, migration-guide, guardrails | Recent migrations |
| Refactoring | Depends on scope | Similar refactors |

Full details: [load-by-scenario.md](./load-by-scenario.md)

## Maintenance

**When to update these files:**

- **guardrails.md** - When security/testing requirements change
- **load-by-scenario.md** - When new patterns emerge or structure changes
- **examples.md** - When better reference implementations are created
- **This README** - When AI usage patterns change

**Who updates:**
- Tech leads update guardrails
- Architects update load instructions
- Anyone can suggest better examples

---

## Quick Start

**For AI implementing a feature:**

1. Read the feature spec
2. Load `guardrails.md` (mandatory)
3. Load docs listed in spec's "Context for AI" section
4. Explore code examples from spec
5. Implement following patterns + guardrails
6. Validate against spec success criteria

**That's it.** Don't overthink it.

---

**Version**: 1.0.0
**Date**: 2026-01-04
**Maintained by**: Tech Leads & Architects

*Optimized for: Cursor, Claude Code, and other LLM-based development tools*
