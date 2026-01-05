---
title: AI Context Loading by Scenario
type: ai-guide
category: patterns
status: current
version: 1.0.0
date: 2026-01-04
tags: [ai, llm, context, scenarios, patterns]
---

# AI Context Loading by Scenario

> **Purpose**: Tell AI exactly what documentation to load based on the type of work being done.

## Overview

Different scenarios require different context. This guide maps scenarios to relevant documentation to minimize context loading while maximizing relevance.

**Rule of thumb:** Load less, learn more. Focus on what's essential.

---

## Scenario 1: Backend CRUD Feature

**Examples:**
- User management
- Team management
- Asset management
- Any entity with create/read/update/delete operations

### Load These Docs

**Required:**
- [docs/99-ai-context/guardrails.md](./guardrails.md) - Security, testing, boundaries (MANDATORY)
- [docs/02-architecture/backend-structure.md](../02-architecture/backend-structure.md) - Repository → Service → Resolver pattern

**Recommended:**
- [docs/03-features/security/authorization.md](../03-features/security/authorization.md) - Permission annotations
- [docs/09-security/authentication.md](../09-security/authentication.md) - Getting current user

**If audit trail needed:**
- [docs/03-features/audit/README.md](../03-features/audit/README.md) - Audit logging patterns

### Code Examples

**Must explore:**
- `/service/kotlin/asset/service/AssetService.kt` - Complete CRUD with audit
- `/service/kotlin/asset/entity/Asset.kt` - Entity pattern with UUID v7
- `/service/kotlin/asset/repository/AssetRepository.kt` - Repository pattern

**Pattern to follow:**
```
Migration → Entity → Repository → Service → Resolver → GraphQL Schema → Tests
```

---

## Scenario 2: GraphQL API (Backend Only)

**Examples:**
- Adding mutations to existing entities
- Creating new GraphQL types
- Implementing federation

### Load These Docs

**Required:**
- [docs/99-ai-context/guardrails.md](./guardrails.md) - Security, testing (MANDATORY)
- [docs/02-architecture/backend-structure.md](../02-architecture/backend-structure.md) - Resolver patterns

**Recommended:**
- [docs/03-features/graphql/README.md](../03-features/graphql/README.md) - GraphQL conventions
- [docs/03-features/graphql/federation.md](../03-features/graphql/federation.md) - If using federation

### Code Examples

**Must explore:**
- `/service/kotlin/asset/resolver/AssetResolver.kt` - Resolver with permissions
- `/service/kotlin/asset/src/main/resources/schema/asset.graphqls` - GraphQL schema

**Pattern to follow:**
```
GraphQL Schema → Resolver → Service (if new logic) → Tests
```

---

## Scenario 3: Frontend Component

**Examples:**
- Form components
- Data display components
- Reusable UI elements

### Load These Docs

**Required:**
- [docs/99-ai-context/guardrails.md](./guardrails.md) - Security (XSS), testing (MANDATORY)
- [docs/02-architecture/frontend-structure.md](../02-architecture/frontend-structure.md) - Component patterns

**Recommended:**
- [docs/03-features/forms/README.md](../03-features/forms/README.md) - Form handling with React Hook Form + Zod

**If data fetching:**
- [docs/03-features/graphql/client.md](../03-features/graphql/client.md) - GraphQL client usage

### Code Examples

**Must explore:**
- `/client/src/features/settings/` - Form patterns, validation
- `/client/src/components/ui/` - Reusable component patterns

**Pattern to follow:**
```
Component → Props/Types → Hooks → GraphQL Operations → Tests
```

---

## Scenario 4: Frontend Page/Feature

**Examples:**
- New user-facing pages
- Complete workflows (create team, invite members, etc.)

### Load These Docs

**Required:**
- [docs/99-ai-context/guardrails.md](./guardrails.md) - Security, testing (MANDATORY)
- [docs/02-architecture/frontend-structure.md](../02-architecture/frontend-structure.md) - Page structure

**Recommended:**
- [docs/03-features/forms/README.md](../03-features/forms/README.md) - Forms
- [docs/03-features/graphql/client.md](../03-features/graphql/client.md) - Data fetching
- [docs/03-features/routing/README.md](../03-features/routing/README.md) - Routing patterns

### Code Examples

**Must explore:**
- `/client/src/features/settings/` - Complete feature with pages, forms, hooks
- `/client/src/app/` - Routing structure

**Pattern to follow:**
```
Page → Layout → Components → Hooks → GraphQL → Tests
```

---

## Scenario 5: Database Migration

**Examples:**
- New tables
- Schema changes
- Data migrations

### Load These Docs

**Required:**
- [docs/99-ai-context/guardrails.md](./guardrails.md) - Data safety (MANDATORY)
- [docs/02-architecture/database.md](../02-architecture/database.md) - Database patterns

**Recommended:**
- [docs/04-development/migrations.md](../04-development/migrations.md) - Migration best practices

### Code Examples

**Must explore:**
- Recent migrations in `/service/kotlin/[module]/src/main/resources/db/migration/`
- Entity mappings in `/service/kotlin/[module]/entity/`

**Pattern to follow:**
```
Migration SQL → Update Entity → Update Repository (if needed) → Tests
```

---

## Scenario 6: Full-Stack Feature

**Examples:**
- Complete features spanning backend + frontend
- User profile management
- Team management with UI

### Load These Docs

**Required:**
- [docs/99-ai-context/guardrails.md](./guardrails.md) - All guardrails (MANDATORY)
- [docs/02-architecture/backend-structure.md](../02-architecture/backend-structure.md) - Backend patterns
- [docs/02-architecture/frontend-structure.md](../02-architecture/frontend-structure.md) - Frontend patterns

**Recommended:**
- [docs/03-features/security/authorization.md](../03-features/security/authorization.md) - Auth
- [docs/03-features/forms/README.md](../03-features/forms/README.md) - Forms
- [docs/03-features/graphql/README.md](../03-features/graphql/README.md) - GraphQL

### Code Examples

**Must explore:**
- `/service/kotlin/asset/` - Complete backend module
- `/client/src/features/settings/` - Complete frontend feature

**Pattern to follow:**
```
Backend: Migration → Entity → Repository → Service → Resolver → GraphQL Schema
Frontend: GraphQL Operations → Hooks → Components → Pages
Tests: Backend (unit, integration) + Frontend (component, E2E)
```

---

## Scenario 7: Refactoring

**Examples:**
- Migrating to new library
- Changing architectural patterns
- Code cleanup

### Load These Docs

**Required:**
- [docs/99-ai-context/guardrails.md](./guardrails.md) - Ensure no regressions (MANDATORY)

**Recommended:**
- Docs related to the area being refactored
- ADRs in [docs/10-decisions/](../10-decisions/) if they exist

### Code Examples

**Must explore:**
- Code being refactored
- Similar refactoring PRs (if available)

**Pattern to follow:**
```
Understand current → Plan migration → Implement → Test (ensure no regressions) → Update docs
```

---

## Scenario 8: Bug Fix

**Examples:**
- Fixing specific bugs
- Addressing edge cases
- Performance issues

### Load These Docs

**Usually not needed** - explore code directly

**Exception - load if:**
- Bug involves security → [docs/09-security/](../09-security/)
- Bug involves testing gaps → [docs/05-testing/](../05-testing/)

### Code Examples

**Must explore:**
- Code where bug exists
- Tests that should have caught it
- Similar fixes (if known)

**Pattern to follow:**
```
Reproduce → Understand root cause → Fix → Add test to prevent regression
```

---

## Scenario 9: Security Feature

**Examples:**
- Authentication flows
- Authorization changes
- Security enhancements

### Load These Docs

**Required:**
- [docs/99-ai-context/guardrails.md](./guardrails.md) - Security section (MANDATORY)
- [docs/09-security/](../09-security/) - All security docs
- [docs/03-features/security/](../03-features/security/) - Security features

**Recommended:**
- [docs/02-architecture/backend-structure.md](../02-architecture/backend-structure.md) - How security integrates

### Code Examples

**Must explore:**
- `/service/kotlin/security/` - All security services
- `/service/kotlin/common/security/` - Common security utilities

**Pattern to follow:**
```
Understand threat model → Implement defense → Test edge cases → Security review
```

---

## Scenario 10: Testing

**Examples:**
- Adding missing tests
- Improving coverage
- Testing edge cases

### Load These Docs

**Required:**
- [docs/99-ai-context/guardrails.md](./guardrails.md) - Testing standards (MANDATORY)
- [docs/05-testing/](../05-testing/) - Testing strategies

**Recommended:**
- [docs/05-testing/coverage.md](../05-testing/coverage.md) - Coverage requirements

### Code Examples

**Must explore:**
- Existing tests in same module
- High-coverage test files as examples

**Pattern to follow:**
```
Unit tests → Integration tests → E2E tests (if needed)
```

---

## Quick Reference Table

| Scenario | Guardrails | Docs to Load | Code Examples | Time Saved |
|----------|-----------|--------------|---------------|------------|
| Backend CRUD | ✅ Always | backend-structure, security/authorization | AssetService.kt | ~2 min |
| GraphQL API | ✅ Always | backend-structure, graphql/ | AssetResolver.kt | ~1 min |
| Frontend Component | ✅ Always | frontend-structure, forms/ | Settings UI | ~1 min |
| Frontend Page | ✅ Always | frontend-structure, forms/, routing/ | Settings feature | ~2 min |
| Database Migration | ✅ Always | database, migrations | Recent migrations | ~1 min |
| Full-Stack Feature | ✅ Always | All backend + frontend | Asset module + Settings | ~3 min |
| Refactoring | ✅ Always | Area-specific | Code being changed | Varies |
| Bug Fix | Sometimes | Usually none | Bug location | ~0 min |
| Security Feature | ✅ Always | All security docs | security/ module | ~3 min |
| Testing | ✅ Always | testing/ | Similar tests | ~1 min |

---

## Loading Strategy

### Minimal Loading (Fastest)

For well-understood patterns:
1. Guardrails (always)
2. One architecture doc
3. One code example

**Time:** ~30 seconds load + implementation

### Standard Loading (Recommended)

For most features:
1. Guardrails (always)
2. 2-3 architecture/feature docs
3. 2-3 code examples

**Time:** ~1-2 minutes load + implementation

### Comprehensive Loading (When Needed)

For complex/new patterns:
1. Guardrails (always)
2. All related docs
3. Multiple code examples
4. ADRs if they exist

**Time:** ~3-5 minutes load + implementation

---

## Tips for Efficient Loading

### ✅ Do

- **Start with guardrails** - Always load first
- **Load progressively** - Start minimal, load more if needed
- **Verify against code** - Docs explain WHY, code shows HOW
- **Cache patterns** - Remember patterns across similar features (within same conversation)

### ❌ Don't

- **Load everything** - Wastes time and context
- **Skip guardrails** - They're non-negotiable
- **Ignore code examples** - They're the source of truth
- **Load unrelated docs** - Stay focused on scenario

---

**Version**: 1.0.0
**Date**: 2026-01-04
**Maintained by**: Tech Leads

*Optimized for fast, focused context loading*
