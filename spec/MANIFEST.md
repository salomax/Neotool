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

This specification is organized into numbered directories (00-09) for clear hierarchy and navigation:

- **00-core**: Core specification documents (architecture, tech stack, principles)
- **01-rules**: Explicit rules for AI/developers (coding, architecture, API, database, testing, security)
- **02-definitions**: Definitions & terminology (glossary, domain model, concepts)
- **03-patterns**: Implementation patterns (backend, frontend, shared)
- **04-templates**: Reusable templates (feature creation, AI prompts, code, documents)
- **05-examples**: Concrete examples (backend, frontend, full-stack)
- **06-workflows**: Development workflows (feature dev, code review, testing, deployment)
- **07-validation**: Validation & checklists (feature, code review, PR)
- **08-adr**: Architecture Decision Records
- **09-reference**: Quick reference guides (commands, file structure, GraphQL, API)

## Core Documents (00-core)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Architecture Overview | `00-core/architecture.md` | core | architecture | architecture, overview, system-design |
| Technology Stack | `00-core/technology-stack.md` | core | technology | tech-stack, technologies, versions |
| Project Structure | `00-core/project-structure.md` | core | structure | monorepo, structure, organization |
| Principles | `00-core/principles.md` | core | philosophy | principles, philosophy, guidelines |

## Rules (01-rules)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Rules Index | `01-rules/README.md` | rule | index | rules, index, guidelines |
| Coding Standards | `01-rules/coding-standards.md` | rule | coding | code-style, naming, conventions |
| Architecture Rules | `01-rules/architecture-rules.md` | rule | architecture | architecture, constraints, patterns |
| API Rules | `01-rules/api-rules.md` | rule | api | graphql, rest, api, patterns |
| Database Rules | `01-rules/database-rules.md` | rule | database | database, schema, migration |
| Testing Rules | `01-rules/testing-rules.md` | rule | testing | testing, test-patterns, requirements |
| Security Rules | `01-rules/security-rules.md` | rule | security | security, auth, authorization |
| Observability Rules | `01-rules/observability-rules.md` | rule | observability | observability, metrics, logging, monitoring, prometheus, loki, grafana |

## Definitions (02-definitions)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Glossary | `02-definitions/glossary.md` | definition | terminology | glossary, terms, definitions |
| Domain Model | `02-definitions/domain-model.md` | definition | domain | domain, entities, relationships |
| Concepts | `02-definitions/concepts.md` | definition | concepts | concepts, explanations, patterns |

## Patterns (03-patterns)

### Backend Patterns

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Patterns Index | `03-patterns/README.md` | pattern | index | patterns, index |
| Entity Pattern | `03-patterns/backend/entity-pattern.md` | pattern | backend | jpa, entity, kotlin |
| Repository Pattern | `03-patterns/backend/repository-pattern.md` | pattern | backend | repository, data-access |
| Service Pattern | `03-patterns/backend/service-pattern.md` | pattern | backend | service, business-logic |
| Resolver Pattern | `03-patterns/backend/resolver-pattern.md` | pattern | backend | graphql, resolver |
| Testing Pattern | `03-patterns/backend/testing-pattern.md` | pattern | backend | testing, unit-tests, integration |

### Frontend Patterns

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Component Pattern | `03-patterns/frontend/component-pattern.md` | pattern | frontend | component, react, ui |
| Page Pattern | `03-patterns/frontend/page-pattern.md` | pattern | frontend | page, route, nextjs |
| Hook Pattern | `03-patterns/frontend/hook-pattern.md` | pattern | frontend | hooks, react, custom-hooks |
| GraphQL Pattern | `03-patterns/frontend/graphql-pattern.md` | pattern | frontend | graphql, operations, apollo |
| Styling Pattern | `03-patterns/frontend/styling-pattern.md` | pattern | frontend | styling, themes, design-tokens |

### Shared Patterns

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| GraphQL Federation | `03-patterns/shared/graphql-federation.md` | pattern | shared | graphql, federation, apollo |
| Error Handling | `03-patterns/shared/error-handling.md` | pattern | shared | error-handling, exceptions |

## Templates (04-templates)

### Feature Creation

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Templates Index | `04-templates/README.md` | template | index | templates, index |
| Feature Form | `04-templates/feature-creation/feature-form.md` | template | feature | feature-form, feature-creation |
| Questionnaire | `04-templates/feature-creation/questionnaire.md` | template | feature | questionnaire, discovery |
| Workflow | `04-templates/feature-creation/workflow.md` | template | feature | workflow, process |

### AI Prompts

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Step A: Initial Feature Request | `04-templates/ai-prompts/step-a-initial-feature-request.md` | template | ai-prompt | ai-prompt, feature-creation, initial-request |
| Step B: Generate Form and Feature File | `04-templates/ai-prompts/step-b-generate-form-and-feature-file.md` | template | ai-prompt | ai-prompt, feature-creation, form-generation |
| Step C: Generate Implementation Plan | `04-templates/ai-prompts/step-c-generate-implementation-plan.md` | template | ai-prompt | ai-prompt, planning, implementation-plan |
| Step D: Build Implementation | `04-templates/ai-prompts/step-d-build-implementation.md` | template | ai-prompt | ai-prompt, implementation, feature-creation |
| Step E: Validate Implementation | `04-templates/ai-prompts/step-e-validate-implementation.md` | template | ai-prompt | ai-prompt, validation, checklist |

### Code Templates

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Entity Template | `04-templates/code/entity-template.kt` | template | code | kotlin, entity, template |
| Resolver Template | `04-templates/code/resolver-template.kt` | template | code | kotlin, resolver, template |
| Component Template | `04-templates/code/component-template.tsx` | template | code | typescript, component, template |
| Test Template | `04-templates/code/test-template.kt` | template | code | kotlin, test, template |

### Document Templates

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Feature Request | `04-templates/documents/feature-request.md` | template | document | feature-request, template |
| ADR Template | `04-templates/documents/adr-template.md` | template | document | adr, template |
| Technical Design | `04-templates/documents/technical-design.md` | template | document | technical-design, template |

## Examples (05-examples)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Examples Index | `05-examples/README.md` | example | index | examples, index |
| CRUD Example | `05-examples/backend/crud-example/` | example | backend | crud, example, backend |
| Federation Example | `05-examples/backend/federation-example/` | example | backend | federation, example |
| Page Example | `05-examples/frontend/page-example/` | example | frontend | page, example, frontend |
| Component Example | `05-examples/frontend/component-example/` | example | frontend | component, example |
| Feature Example | `05-examples/full-stack/feature-example/` | example | full-stack | feature, example, full-stack |

## Workflows (06-workflows)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Workflows Index | `06-workflows/README.md` | workflow | index | workflows, index |
| Feature Development | `06-workflows/feature-development.md` | workflow | development | feature-development, workflow |
| Code Review | `06-workflows/code-review.md` | workflow | review | code-review, workflow |
| Testing Workflow | `06-workflows/testing-workflow.md` | workflow | testing | testing, workflow |
| Deployment Workflow | `06-workflows/deployment-workflow.md` | workflow | deployment | deployment, workflow |

## Validation (07-validation)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Validation Index | `07-validation/README.md` | validation | index | validation, index |
| Feature Checklist | `07-validation/feature-checklist.md` | validation | checklist | feature, checklist |
| Code Review Checklist | `07-validation/code-review-checklist.md` | validation | checklist | code-review, checklist |
| PR Checklist | `07-validation/pr-checklist.md` | validation | checklist | pr, checklist |
| Validation Scripts | `07-validation/validation-scripts.md` | validation | scripts | validation, scripts |

## Architecture Decision Records (08-adr)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| ADR Index | `08-adr/README.md` | adr | index | adr, index |
| ADR-0001: Monorepo | `08-adr/0001-monorepo.md` | adr | architecture | monorepo, architecture |
| ADR-0002: Containers | `08-adr/0002-containers.md` | adr | infrastructure | containers, docker, kubernetes |
| ADR-0003: Kotlin Backend | `08-adr/0003-kotlin-backend.md` | adr | backend | kotlin, micronaut, backend |
| ADR-0004: TypeScript Frontend | `08-adr/0004-typescript-frontend.md` | adr | frontend | typescript, nextjs, frontend |
| ADR-0005: PostgreSQL | `08-adr/0005-postgresql.md` | adr | database | postgresql, database |

## Reference (09-reference)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Commands | `09-reference/commands.md` | reference | commands | cli, commands, reference |
| File Structure | `09-reference/file-structure.md` | reference | structure | file-structure, reference |
| GraphQL Schema | `09-reference/graphql-schema.md` | reference | graphql | graphql, schema, reference |
| API Reference | `09-reference/api-reference.md` | reference | api | api, reference |

## Document Categories

### By Type
- **Core**: System architecture, technology stack, principles
- **Rules**: Explicit rules and constraints
- **Definitions**: Terminology, domain model, concepts
- **Patterns**: Implementation patterns and examples
- **Templates**: Reusable templates for features, code, documents
- **Examples**: Concrete working examples
- **Workflows**: Development processes and procedures
- **Validation**: Checklists and validation tools
- **ADR**: Architecture Decision Records
- **Reference**: Quick reference guides

### By Layer
- **Infrastructure**: Docker, Kubernetes, deployment
- **Backend**: Kotlin, Micronaut, services
- **Frontend**: React, Next.js, TypeScript
- **Database**: PostgreSQL, schema, migrations
- **API**: GraphQL, Federation, contracts
- **Shared**: Cross-cutting concerns

### By Audience
- **Developers**: Implementation guides, code examples, patterns
- **Architects**: ADRs, system design, architecture rules
- **DevOps**: Infrastructure, deployment, workflows
- **AI Assistants**: Rules, patterns, templates, examples

## Search Optimization

### Technology Stack Keywords
- `kotlin`, `micronaut`, `gradle`, `java21`, `virtual-threads`
- `typescript`, `react`, `nextjs`, `app-router`
- `postgresql`, `graphql`, `apollo`, `federation`
- `docker`, `kubernetes`, `argo-cd`
- `prometheus`, `grafana`, `loki`

### Concept Keywords
- `monorepo`, `microservices`, `federation`
- `domain-driven-design`, `clean-architecture`
- `type-safety`, `code-generation`, `spec-driven-development`
- `observability`, `monitoring`, `logging`
- `ci-cd`, `gitops`, `deployment`

### Pattern Keywords
- `dependency-injection`, `repository-pattern`
- `graphql-resolvers`, `federation-directives`
- `virtual-threads`, `@ExecuteOn`, `blocking-executor`
- `server-components`, `client-components`
- `design-tokens`, `component-system`

## Navigation Guidelines

### For AI Assistants
1. **Start with**: `00-core/architecture.md` for system understanding
2. **Reference**: `02-definitions/glossary.md` for terminology
3. **Follow**: Cross-references in documents for related topics
4. **Use**: Category tags to find related documents
5. **For Feature Creation**: Use `04-templates/feature-creation/feature-form.md` and related templates

### For Developers
1. **Quick Start**: Read `README.md` and `00-core/architecture.md`
2. **Implementation**: Check `03-patterns/` for patterns
3. **Rules**: Review `01-rules/` for constraints
4. **Examples**: See `05-examples/` for working code
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

