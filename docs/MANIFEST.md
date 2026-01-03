---
title: Specification Manifest
type: index
category: documentation
status: current
version: 3.0.0
tags: [manifest, index, specification, navigation]
ai_optimized: true
search_keywords: [index, manifest, navigation, specification, documents]
---

# NeoTool Specification Manifest

> **Purpose**: Complete index of all specification documents, optimized for RAG indexing and AI assistant navigation.

## Document Organization

This specification is organized into two major sections:

### Core Learning Path (01-12)
Sequential documentation for understanding and building NeoTool:

- **01-overview**: High-level overview documents (architecture, tech stack, principles, quick start)
- **02-architecture**: System & service architecture
- **03-features**: Feature specifications with implementation details
- **04-domain**: Domain model & business logic (domain model, entities, business rules, glossary)
- **05-backend**: Backend implementation patterns and standards (Kotlin, patterns, testing, architecture)
- **06-contracts**: API contracts (GraphQL federation, standards, patterns)
- **07-frontend**: Frontend implementation patterns and standards (React, Next.js, patterns, testing)
- **08-workflows**: Development workflows (feature dev, code review, testing, deployment)
- **09-security**: Security implementation and standards (authentication, authorization)
- **10-observability**: Observability standards (metrics, logging, monitoring)
- **11-infrastructure**: Infrastructure and deployment (placeholder for future content)
- **12-specification-driven-dev**: How to use this documentation system with AI assistants

### Supporting Resources (90-94)
Quick lookup and reference materials:

- **90-examples**: Concrete implementation examples (backend, frontend, full-stack)
- **91-templates**: Reusable templates (feature creation, AI prompts, code templates, documents)
- **92-adr**: Architecture Decision Records
- **93-reference**: Quick reference guides (commands, file structure, GraphQL, API)
- **94-validation**: Checklists & validation scripts

## Overview Documents (01-overview)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Project Overview | `01-overview/project-overview.md` | overview | introduction | overview, introduction, neotool |
| Architecture Overview | `01-overview/architecture-overview.md` | overview | architecture | architecture, overview, system-design |
| Technology Stack | `01-overview/technology-stack.md` | overview | technology | tech-stack, technologies, versions |
| Project Structure | `01-overview/project-structure.md` | overview | structure | monorepo, structure, organization |
| Principles | `01-overview/principles.md` | overview | philosophy | principles, philosophy, guidelines |
| Quick Start | `01-overview/quick-start.md` | overview | getting-started | quick-start, getting-started, setup |

## Architecture Documents (02-architecture)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| System Architecture | `02-architecture/system-architecture.md` | architecture | architecture | architecture, system-design |

## Feature Specifications (03-features)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Assets Service - Specification | `03-features/assets/asset-service/README.md` | feature | assets | assets, upload, cdn, storage, cloudflare-r2, s3, minio |
| Assets Service - Decisions | `03-features/assets/asset-service/decisions.md` | feature | assets | assets, adr, decisions, trade-offs |
| Security Overview | `03-features/security/README.md` | feature | security | security, authentication, authorization |
| Authentication Features | `03-features/security/authentication/README.md` | feature | authentication | authentication, signin, signup, jwt |
| Authorization Features | `03-features/security/authorization/README.md` | feature | authorization | authorization, rbac, permissions |

## Domain Documents (04-domain)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Glossary | `04-domain/glossary.md` | domain | terminology | glossary, terms, definitions |
| Domain Model | `04-domain/domain-model.md` | domain | domain | domain, entities, relationships |
| Concepts | `04-domain/concepts.md` | domain | concepts | concepts, explanations, patterns |
| Database Schema Standards | `04-domain/database-schema-standards.md` | domain | database | database, schema, standards, migration |
| UUID v7 Standard | `04-domain/uuid-v7-standard.md` | domain | database | uuid, uuidv7, primary-key, database |

## Backend Documentation (05-backend)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Backend Overview | `05-backend/README.md` | index | backend | backend, kotlin, patterns, standards |

### Kotlin Standards

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Coding Standards | `05-backend/kotlin/coding-standards.md` | standard | coding | kotlin, code-style, naming |
| Linting Standards | `05-backend/kotlin/linting-standards.md` | standard | coding | linting, lint, code-quality, validation |
| Testing Standards | `05-backend/kotlin/testing-standards.md` | standard | testing | testing, test-patterns, requirements, kotlin |

### Backend Patterns

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Domain Model Pattern | `05-backend/patterns/domain-model-pattern.md` | pattern | backend | domain, model, entity, repository, database, sql, jpa, consolidated |
| Entity Pattern | `05-backend/patterns/entity-pattern.md` | pattern | backend | jpa, entity, kotlin |
| Repository Pattern | `05-backend/patterns/repository-pattern.md` | pattern | backend | repository, data-access |
| Service Pattern | `05-backend/patterns/service-pattern.md` | pattern | backend | service, business-logic |
| Mapper Pattern | `05-backend/patterns/mapper-pattern.md` | pattern | backend | mapper, graphql, dto, mapping, list-handling |
| GraphQL Resolver Pattern | `05-backend/patterns/graphql-resolver-pattern.md` | pattern | backend | graphql, resolver |
| Domain-Entity Conversion | `05-backend/patterns/domain-entity-conversion.md` | pattern | backend | domain, entity, conversion, ddd |
| Pagination Pattern | `05-backend/patterns/pagination-pattern.md` | pattern | backend | pagination, relay, graphql, cursor, connection |
| Kafka Consumer Pattern | `05-backend/patterns/kafka-consumer-pattern.md` | pattern | backend | kafka, consumer, batch, retry, dlq |
| Kafka Operations Guide | `05-backend/patterns/kafka-operations-guide.md` | pattern | backend | kafka, operations, troubleshooting |
| Kafka Monitoring Guide | `05-backend/patterns/kafka-monitoring-guide.md` | pattern | backend | kafka, monitoring, metrics |
| Batch Processing Pattern | `05-backend/patterns/batch-processing-pattern.md` | pattern | backend | batch, workflow, prefect, kafka |

### Backend Standards

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Layer Rules | `05-backend/standards/layer-rules.md` | standard | architecture | architecture, constraints, patterns, layers |

## Contracts Documentation (06-contracts)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Contracts Overview | `06-contracts/README.md` | index | contracts | graphql, api, contracts, federation |
| GraphQL Standards | `06-contracts/graphql-standards.md` | standard | api | graphql, api, patterns |
| GraphQL Query Pattern | `06-contracts/graphql-query-pattern.md` | pattern | api | graphql, query, resolver, api |

## Frontend Documentation (07-frontend)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Frontend Overview | `07-frontend/README.md` | index | frontend | frontend, react, nextjs, patterns, standards |

### Frontend Patterns

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| GraphQL Query Pattern | `07-frontend/patterns/graphql-query-pattern.md` | pattern | frontend | graphql, query, apollo, cache, pagination, relay, relayStylePagination |
| GraphQL Mutation Pattern | `07-frontend/patterns/graphql-mutation-pattern.md` | pattern | frontend | graphql, mutation, apollo, cache, refetch, list, table |
| Mutation Pattern | `07-frontend/patterns/mutation-pattern.md` | pattern | frontend | mutation, hooks, refetch, race-condition, apollo, graphql |
| Management Pattern | `07-frontend/patterns/management-pattern.md` | pattern | frontend | management, hooks, components, reusable, useManagementBase, useDebouncedSearch, useSorting, ErrorAlert, DeleteConfirmationDialog, useToggleStatus, ManagementLayout |
| Shared Components Pattern | `07-frontend/patterns/shared-components-pattern.md` | pattern | frontend | shared components, reusable components, ui components, feedback components, ErrorAlert, DeleteConfirmationDialog, ManagementLayout, useToggleStatus |
| Breadcrumb Pattern | `07-frontend/patterns/breadcrumb-pattern.md` | pattern | frontend | breadcrumb, navigation, page hierarchy, navigation path, breadcrumb component |
| Styling Pattern | `07-frontend/patterns/styling-pattern.md` | pattern | frontend | styling, themes, design-tokens |
| Toast Notification Pattern | `07-frontend/patterns/toast-notification-pattern.md` | pattern | frontend | toast, notification, feedback, user-experience |
| Testing Pattern | `07-frontend/patterns/testing-pattern.md` | pattern | frontend | testing, frontend, react, react-hook-form, vitest, testing-library |
| E2E Testing Pattern | `07-frontend/patterns/e2e-testing-pattern.md` | pattern | frontend | testing, e2e, playwright, end-to-end, browser-testing, integration-testing |

## Workflows (08-workflows)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Workflows Index | `08-workflows/README.md` | index | workflow | workflows, index, spec-driven |
| Feature Development | `08-workflows/feature-development.md` | workflow | development | feature-development, workflow, spec-driven, sdd |
| Code Review | `08-workflows/code-review.md` | workflow | review | code-review, workflow, spec-compliance |
| Testing Workflow | `08-workflows/testing-workflow.md` | workflow | testing | testing, workflow, spec-driven, coverage |
| Deployment Workflow | `08-workflows/deployment-workflow.md` | workflow | deployment | deployment, workflow, ci-cd |
| Spec Context Strategy | `08-workflows/spec-context-strategy.md` | workflow | development | context, ai, optimization, spec, navigation, rag |

## Security Documentation (09-security)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Authentication | `09-security/authentication.md` | standard | security | security, auth, authorization, jwt |

## Observability Documentation (10-observability)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Observability Overview | `10-observability/observability-overview.md` | standard | observability | observability, metrics, logging, monitoring, prometheus, loki, grafana |

## Infrastructure Documentation (11-infrastructure)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Infrastructure | `11-infrastructure/` | placeholder | infrastructure | infrastructure, deployment, kubernetes, docker |

*Note: This section is a placeholder for future infrastructure documentation.*

## Specification-Driven Development (12-specification-driven-dev)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| SDD Overview | `12-specification-driven-dev/README.md` | guide | methodology | sdd, specification-driven, ai-assisted, cursor, claude-code |

## Examples (90-examples)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Examples Index | `90-examples/README.md` | example | index | examples, index |
| CRUD Example | `90-examples/backend/crud-example/README.md` | example | backend | crud, complete, flow, entity, repository, service, resolver |
| SWAPI ETL Workflow | `90-examples/backend/batch-workflows/swapi-etl-workflow.md` | example | workflow | swapi, etl, batch, prefect, kafka, workflow |
| SWAPI ETL Runbook | `90-examples/backend/batch-workflows/swapi-etl-runbook.md` | runbook | operations | swapi, etl, runbook, operations, troubleshooting |
| Optimistic Toggle Pattern | `90-examples/frontend/frontend-examples/optimistic-toggle-pattern.md` | example | frontend | optimistic-update, toggle, frontend, apollo |

## Templates (91-templates)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Templates Index | `91-templates/README.md` | template | index | templates, index |
| Feature Form | `91-templates/feature-templates/feature-creation/feature-form.md` | template | feature | feature-form, feature-creation |
| Questionnaire | `91-templates/feature-templates/feature-creation/questionnaire.md` | template | feature | questionnaire, discovery |
| Workflow Template | `91-templates/feature-templates/feature-creation/workflow.md` | template | feature | workflow, process |
| Feature Guide Template | `91-templates/feature-templates/feature-guide-template.md` | template | feature | feature-guide, template |
| Batch Workflow Template | `91-templates/document-templates/batch-workflow-template.md` | template | workflow | batch, workflow, template |

### AI Prompts

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Step A: Initial Feature Request | `91-templates/ai-prompts/step-a-initial-feature-request.md` | template | ai-prompt | ai-prompt, feature-creation, step-a |
| Step B: Generate Form and Feature | `91-templates/ai-prompts/step-b-generate-form-and-feature-file.md` | template | ai-prompt | ai-prompt, feature-creation, step-b |
| Step C: Generate Implementation Plan | `91-templates/ai-prompts/step-c-generate-implementation-plan.md` | template | ai-prompt | ai-prompt, feature-creation, step-c |
| Step D: Build Implementation | `91-templates/ai-prompts/step-d-build-implementation.md` | template | ai-prompt | ai-prompt, feature-creation, step-d |
| Step E: Validate Implementation | `91-templates/ai-prompts/step-e-validate-implementation.md` | template | ai-prompt | ai-prompt, feature-creation, step-e |

## Architecture Decision Records (92-adr)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| ADR Index | `92-adr/README.md` | index | adr | adr, index, architecture-decisions |
| ADR-0001: Monorepo | `92-adr/0001-monorepo-architecture.md` | adr | architecture | monorepo, architecture |
| ADR-0002: Containers | `92-adr/0002-containerized-architecture.md` | adr | infrastructure | containers, docker, kubernetes |
| ADR-0003: Kotlin Backend | `92-adr/0003-kotlin-micronaut-backend.md` | adr | backend | kotlin, micronaut, backend |
| ADR-0004: TypeScript Frontend | `92-adr/0004-typescript-nextjs-frontend.md` | adr | frontend | typescript, nextjs, frontend |
| ADR-0005: PostgreSQL | `92-adr/0005-postgresql-database.md` | adr | database | postgresql, database |
| ADR-0006: Frontend Authorization | `92-adr/0006-frontend-authorization-layer.md` | adr | frontend | authorization, rbac, permissions |
| ADR-0007: Asset Service with R2 | `92-adr/0007-asset-service-cloudflare-r2.md` | adr | backend | assets, storage, cdn, cloudflare-r2, s3 |
| ADR-0008: Interservice Security | `92-adr/0008-interservice-security.md` | adr | security | interservice, security, authentication |
| ADR-0008: Interservice Security Migration | `92-adr/0008-interservice-security-migration-plan.md` | adr | security | interservice, security, migration |

## Reference (93-reference)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Reference Index | `93-reference/README.md` | index | reference | reference, index |
| Commands | `93-reference/commands.md` | reference | commands | cli, commands, reference |
| File Structure | `93-reference/file-structure.md` | reference | structure | file-structure, reference |
| GraphQL Schema | `93-reference/graphql-schema.md` | reference | graphql | graphql, schema, reference |
| API Endpoints | `93-reference/api-endpoints.md` | reference | api | api, reference |
| Backend Quick Reference | `93-reference/backend-quick-reference.md` | reference | backend | backend, kotlin, quick-reference, cheat-sheet, imports, annotations |
| Google OAuth Setup | `93-reference/google-oauth-setup.md` | reference | authentication | oauth, google, authentication, setup |

## Validation (94-validation)

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Validation Index | `94-validation/README.md` | index | validation | validation, index, checklists |
| Feature Checklist | `94-validation/feature-checklist.md` | validation | checklist | feature, checklist |
| Code Review Checklist | `94-validation/code-review-checklist.md` | validation | checklist | code-review, checklist |
| PR Checklist | `94-validation/pr-checklist.md` | validation | checklist | pr, checklist |
| Validation Scripts | `94-validation/validation-scripts.md` | validation | scripts | validation, scripts |

## Navigation Guidelines

### For AI Assistants
1. **Start with**: `01-overview/architecture-overview.md` for system understanding
2. **Reference**: `04-domain/glossary.md` for terminology
3. **Learn SDD**: `12-specification-driven-dev/README.md` for how to use this documentation
4. **Follow**: Cross-references in documents for related topics
5. **Use**: Category tags to find related documents
6. **For Feature Creation**: Use `91-templates/feature-templates/feature-creation/feature-form.md` and related templates

### For Developers
1. **Quick Start**: Read `README.md` and `01-overview/architecture-overview.md`
2. **Implementation**: Check `05-backend/patterns/` or `07-frontend/patterns/` for patterns
3. **Standards**: Review `05-backend/kotlin/` or `07-frontend/standards/` for constraints
4. **Examples**: See `90-examples/` for working code
5. **Workflows**: Follow `08-workflows/` for processes
6. **Validation**: Use `94-validation/` checklists before submitting PRs

### For RAG Systems
1. **Index all markdown files** with YAML frontmatter
2. **Extract metadata** from frontmatter for filtering
3. **Build semantic embeddings** for each document
4. **Maintain cross-reference graph** for navigation
5. **Index code examples** separately for code search
6. **Use section hierarchy** (01-12 for learning, 90-94 for reference)

## Versioning

- Documents are versioned independently via frontmatter
- ADRs track decision history
- Breaking changes require new ADRs
- Specification version tracks overall structure (currently 3.0.0)

## Maintenance

- Update manifest when adding/removing documents
- Keep cross-references current
- Maintain keyword consistency
- Review and update categories quarterly

## Migration Notes

**Version 3.0.0 (2026-01-02)**: Major reorganization from 00-11 structure to 01-12 + 90-94 taxonomy:

### Changes
- **Removed**: Directories 00-11 (old structure)
- **Added**: Core Learning Path (01-12) for sequential understanding
- **Added**: Supporting Resources (90-94) for quick reference
- **Reorganized**: Backend patterns moved from `04-patterns/backend-patterns/` to `05-backend/patterns/`
- **Reorganized**: Frontend patterns moved from `04-patterns/frontend-patterns/` to `07-frontend/patterns/`
- **Reorganized**: API patterns moved from `04-patterns/api-patterns/` to `06-contracts/`
- **Reorganized**: Standards distributed to relevant sections (backend, frontend, domain)
- **New**: 12-specification-driven-dev section documenting AI-assisted development workflows

### Benefits
- **Clearer hierarchy**: Core learning path (01-12) vs supporting resources (90-94)
- **Better organization**: Related content grouped together (backend patterns + standards, frontend patterns + standards)
- **AI-friendly**: Easier for AI assistants to navigate and understand document purpose
- **Scalable**: Room for growth (01-12 can extend to 01-99, supporting can extend 90-99)

### Backward Compatibility
- All document content preserved
- Cross-references updated to new paths
- YAML frontmatter maintained for RAG indexing
- Old paths documented in git history

---

*This manifest is the source of truth for all specification documents. Keep it updated as the specification evolves.*
