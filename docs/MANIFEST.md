---
title: Specification Manifest
type: index
category: documentation
status: current
version: 2.0.0
tags: [manifest, index, specification, navigation]
ai_optimized: true
search_keywords: [index, manifest, navigation, specification, documents]
---

# NeoTool Specification Manifest

> **Purpose**: Complete index of all specification documents, optimized for RAG indexing and AI assistant navigation.

## Document Organization

This specification is organized into numbered directories (00-11) for clear hierarchy and navigation:

- **00-overview**: High-level overview documents (architecture, tech stack, principles, quick start)
- **01-architecture**: System & service architecture (system, services, frontend, data, API, infrastructure)
- **02-domain**: Domain model & business logic (domain model, entities, business rules, glossary)
- **03-features**: Feature specifications (SDD) with Gherkin files
- **04-patterns**: Implementation patterns (backend, frontend, API, infrastructure)
- **05-standards**: Coding standards & rules (coding, architecture, API, database, testing, security)
- **06-workflows**: Development workflows (feature dev, code review, testing, deployment)
- **07-examples**: Concrete examples (backend, frontend, full-stack)
- **08-templates**: Reusable templates (feature creation, AI prompts, code, documents)
- **09-adr**: Architecture Decision Records
- **10-reference**: Quick reference guides (commands, file structure, GraphQL, API)
- **11-validation**: Checklists & validation (feature, code review, PR)

## Overview Documents (00-overview)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Project Overview | `00-overview/project-overview.md` | overview | introduction | overview, introduction, neotool |
| Architecture Overview | `00-overview/architecture-overview.md` | overview | architecture | architecture, overview, system-design |
| Technology Stack | `00-overview/technology-stack.md` | overview | technology | tech-stack, technologies, versions |
| Project Structure | `00-overview/project-structure.md` | overview | structure | monorepo, structure, organization |
| Principles | `00-overview/principles.md` | overview | philosophy | principles, philosophy, guidelines |
| Quick Start | `00-overview/quick-start.md` | overview | getting-started | quick-start, getting-started, setup |

## Architecture Documents (01-architecture)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| System Architecture | `01-architecture/system-architecture.md` | architecture | architecture | architecture, system-design |

## Domain Documents (02-domain)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Glossary | `02-domain/glossary.md` | domain | terminology | glossary, terms, definitions |
| Domain Model | `02-domain/domain-model.md` | domain | domain | domain, entities, relationships |
| Concepts | `02-domain/concepts.md` | domain | concepts | concepts, explanations, patterns |

## Features (03-features)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Sign In | `03-features/authentication/signin/feature.feature` | feature | authentication | signin, authentication, login |
| Sign Up | `03-features/authentication/signup/feature.feature` | feature | authentication | signup, registration, authentication |
| Forgot Password | `03-features/authentication/forgot-password/feature.feature` | feature | authentication | password, reset, authentication |

## Patterns (04-patterns)

### Backend Patterns

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Entity Pattern | `04-patterns/backend-patterns/entity-pattern.md` | pattern | backend | jpa, entity, kotlin |
| Repository Pattern | `04-patterns/backend-patterns/repository-pattern.md` | pattern | backend | repository, data-access |
| Service Pattern | `04-patterns/backend-patterns/service-pattern.md` | pattern | backend | service, business-logic |
| Mapper Pattern | `04-patterns/backend-patterns/mapper-pattern.md` | pattern | backend | mapper, graphql, dto, mapping, list-handling |
| Resolver Pattern | `04-patterns/backend-patterns/resolver-pattern.md` | pattern | backend | graphql, resolver |
| Testing Pattern | `04-patterns/backend-patterns/testing-pattern.md` | pattern | backend | testing, unit-tests, integration |
| UUID v7 Pattern | `04-patterns/backend-patterns/uuid-v7-pattern.md` | pattern | backend | uuid, uuidv7, primary-key, database |
| Domain-Entity Conversion | `04-patterns/backend-patterns/domain-entity-conversion.md` | pattern | backend | domain, entity, conversion, ddd |
| Pagination Pattern | `04-patterns/backend-patterns/pagination-pattern.md` | pattern | backend | pagination, relay, graphql, cursor, connection |

### Frontend Patterns

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Component Pattern | `04-patterns/frontend-patterns/component-pattern.md` | pattern | frontend | component, react, ui |
| Page Pattern | `04-patterns/frontend-patterns/page-pattern.md` | pattern | frontend | page, route, nextjs |
| Hook Pattern | `04-patterns/frontend-patterns/hook-pattern.md` | pattern | frontend | hooks, react, custom-hooks |
| GraphQL Pattern | `04-patterns/frontend-patterns/graphql-pattern.md` | pattern | frontend | graphql, operations, apollo |
| Styling Pattern | `04-patterns/frontend-patterns/styling-pattern.md` | pattern | frontend | styling, themes, design-tokens |
| Toast Notification Pattern | `04-patterns/frontend-patterns/toast-notification-pattern.md` | pattern | frontend | toast, notification, feedback, user-experience |

### API Patterns

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| GraphQL Federation | `04-patterns/api-patterns/graphql-federation.md` | pattern | api | graphql, federation, apollo |
| Error Handling | `04-patterns/api-patterns/error-handling-pattern.md` | pattern | api | error-handling, exceptions |

## Standards (05-standards)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Kotlin Standards | `05-standards/coding-standards/kotlin-standards.md` | standard | coding | kotlin, code-style, naming |
| Linting Standards | `05-standards/coding-standards/linting-standards.md` | standard | coding | linting, lint, code-quality, validation |
| Architecture Standards | `05-standards/architecture-standards/layer-rules.md` | standard | architecture | architecture, constraints, patterns |
| GraphQL Standards | `05-standards/api-standards/graphql-standards.md` | standard | api | graphql, api, patterns |
| Database Standards | `05-standards/database-standards/schema-standards.md` | standard | database | database, schema, migration |
| Testing Standards | `05-standards/testing-standards/unit-test-standards.md` | standard | testing | testing, test-patterns, requirements |
| Security Standards | `05-standards/security-standards/authentication-standards.md` | standard | security | security, auth, authorization |
| Observability Standards | `05-standards/observability-standards.md` | standard | observability | observability, metrics, logging, monitoring |

## Workflows (06-workflows)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Feature Development | `06-workflows/feature-development.md` | workflow | development | feature-development, workflow |
| Code Review | `06-workflows/code-review.md` | workflow | review | code-review, workflow |
| Testing Workflow | `06-workflows/testing-workflow.md` | workflow | testing | testing, workflow |
| Deployment Workflow | `06-workflows/deployment-workflow.md` | workflow | deployment | deployment, workflow |

## Examples (07-examples)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Examples Index | `07-examples/README.md` | example | index | examples, index |
| CRUD Example | `07-examples/backend/crud-example/README.md` | example | backend | crud, complete, flow, entity, repository, service, resolver |

## Templates (08-templates)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Feature Form | `08-templates/feature-templates/feature-form.md` | template | feature | feature-form, feature-creation |
| Questionnaire | `08-templates/feature-templates/questionnaire.md` | template | feature | questionnaire, discovery |
| Workflow | `08-templates/feature-templates/workflow.md` | template | feature | workflow, process |
| AI Prompts | `08-templates/ai-prompts/` | template | ai-prompt | ai-prompt, feature-creation |

## Architecture Decision Records (09-adr)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| ADR-0001: Monorepo | `09-adr/0001-monorepo-architecture.md` | adr | architecture | monorepo, architecture |
| ADR-0002: Containers | `09-adr/0002-containerized-architecture.md` | adr | infrastructure | containers, docker, kubernetes |
| ADR-0003: Kotlin Backend | `09-adr/0003-kotlin-micronaut-backend.md` | adr | backend | kotlin, micronaut, backend |
| ADR-0004: TypeScript Frontend | `09-adr/0004-typescript-nextjs-frontend.md` | adr | frontend | typescript, nextjs, frontend |
| ADR-0005: PostgreSQL | `09-adr/0005-postgresql-database.md` | adr | database | postgresql, database |

## Reference (10-reference)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Commands | `10-reference/commands.md` | reference | commands | cli, commands, reference |
| File Structure | `10-reference/file-structure.md` | reference | structure | file-structure, reference |
| GraphQL Schema | `10-reference/graphql-schema.md` | reference | graphql | graphql, schema, reference |
| API Endpoints | `10-reference/api-endpoints.md` | reference | api | api, reference |
| Backend Quick Reference | `10-reference/backend-quick-reference.md` | reference | backend | backend, kotlin, quick-reference, cheat-sheet, imports, annotations |

## Validation (11-validation)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Feature Checklist | `11-validation/feature-checklist.md` | validation | checklist | feature, checklist |
| Code Review Checklist | `11-validation/code-review-checklist.md` | validation | checklist | code-review, checklist |
| PR Checklist | `11-validation/pr-checklist.md` | validation | checklist | pr, checklist |
| Validation Scripts | `11-validation/validation-scripts.md` | validation | scripts | validation, scripts |

## Navigation Guidelines

### For AI Assistants
1. **Start with**: `00-overview/architecture-overview.md` for system understanding
2. **Reference**: `02-domain/glossary.md` for terminology
3. **Follow**: Cross-references in documents for related topics
4. **Use**: Category tags to find related documents
5. **For Feature Creation**: Use `08-templates/feature-templates/feature-form.md` and related templates

### For Developers
1. **Quick Start**: Read `README.md` and `00-overview/architecture-overview.md`
2. **Implementation**: Check `04-patterns/` for patterns
3. **Standards**: Review `05-standards/` for constraints
4. **Examples**: See `07-examples/` for working code
5. **Workflows**: Follow `06-workflows/` for processes

### For RAG Systems
1. **Index all markdown files** with YAML frontmatter
2. **Extract metadata** from frontmatter for filtering
3. **Build semantic embeddings** for each document
4. **Maintain cross-reference graph** for navigation
5. **Index code examples** separately for code search

## Versioning

- Documents are versioned independently via frontmatter
- ADRs track decision history
- Breaking changes require new ADRs
- Specification version tracks overall structure (currently 2.0.0)

## Maintenance

- Update manifest when adding/removing documents
- Keep cross-references current
- Maintain keyword consistency
- Review and update categories quarterly

---

*This manifest is the source of truth for all specification documents. Keep it updated as the specification evolves.*

