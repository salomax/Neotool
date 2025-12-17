---
title: NeoTool Project Rules
description: Comprehensive project rules for NeoTool including specification knowledge base, technology stack, development guidelines, and architectural patterns
alwaysApply: true
---

# NeoTool Project Rules for Cursor AI

## Specification Knowledge Base

This project uses a comprehensive specification located in the `docs/` directory. **The specification is the source of truth for ALL questions and implementations.**

### Core Principle: Spec-Driven Development (SDD)

**ALWAYS reference the specification for:**
- **Any question** about the project, architecture, patterns, or conventions
- **Any implementation** - features, bug fixes, refactoring, or code changes
- **Any architectural decision** - check ADRs and architecture docs first
- **Any code pattern** - use documented patterns from `docs/04-patterns/`
- **Any coding standard** - follow standards from `docs/05-standards/`
- **Any workflow** - follow workflows from `docs/06-workflows/`

**Before answering any question or implementing anything:**
1. **Check the specification first** - Start with `docs/MANIFEST.md` to find relevant documents
2. **Load appropriate context** - Use `docs/06-workflows/spec-context-strategy.md` for context loading guidance
3. **Reference spec documents** - Quote or reference specific spec sections in your responses
4. **Follow spec patterns** - Use documented patterns, don't invent new approaches
5. **Validate against spec** - Ensure answers and implementations comply with specification

### Key Specification Documents

1. **Start Here**: `docs/MANIFEST.md` - Complete index of all specification documents
2. **Architecture**: `docs/00-overview/architecture-overview.md` - System architecture and technology stack
3. **Context Strategy**: `docs/06-workflows/spec-context-strategy.md` - **CRITICAL**: How to efficiently use the spec for questions and implementations
4. **Quick Reference**: `docs/10-reference/` - Common patterns, commands, and conventions
5. **Glossary**: `docs/02-domain/glossary.md` - Terminology and definitions
6. **Project Setup**: `docs/00-overview/quick-start.md` - Setup and configuration guide
7. **Workflows**: `docs/06-workflows/` - Development workflows (feature dev, code review, testing, deployment)

### Architecture Decision Records (ADRs)

All major technical decisions are documented in `docs/09-adr/`:
- `docs/09-adr/0001-monorepo-architecture.md` - Monorepo structure
- `docs/09-adr/0002-containerized-architecture.md` - Containerization strategy
- `docs/09-adr/0003-kotlin-micronaut-backend.md` - Backend technology choices
- `docs/09-adr/0004-typescript-nextjs-frontend.md` - Frontend technology choices
- `docs/09-adr/0005-postgresql-database.md` - Database technology choices

### Service Layer Documentation

- `docs/04-patterns/api-patterns/graphql-query-pattern.md` - GraphQL query patterns (backend)
- `docs/04-patterns/backend-patterns/entity-pattern.md` - JPA entity patterns
- `docs/04-patterns/backend-patterns/pagination-pattern.md` - Pagination patterns
- `docs/04-patterns/backend-patterns/resolver-pattern.md` - GraphQL resolver patterns

### Frontend Documentation

- `docs/00-overview/project-structure.md` - Project structure including frontend directory structure
- `docs/04-patterns/frontend-patterns/graphql-query-pattern.md` - GraphQL query patterns (frontend)
- `docs/04-patterns/frontend-patterns/graphql-mutation-pattern.md` - GraphQL mutation patterns
- `docs/04-patterns/frontend-patterns/shared-components-pattern.md` - Shared component patterns
- `docs/04-patterns/frontend-patterns/management-pattern.md` - Management UI patterns
- `docs/04-patterns/frontend-patterns/hook-pattern.md` - Custom hooks patterns
- `docs/04-patterns/frontend-patterns/testing-pattern.md` - Frontend testing patterns

## Technology Stack

### Backend
- **Language**: Kotlin
- **Framework**: Micronaut
- **Build**: Gradle
- **API**: GraphQL (Apollo Federation)
- **Data Access**: Micronaut Data (JPA/Hibernate)
- **Database**: PostgreSQL 15+

### Frontend
- **Framework**: Next.js 15+ (App Router)
- **Language**: TypeScript
- **UI Library**: React 18+
- **Styling**: Material-UI (MUI) + Custom Design System
- **API Client**: Apollo Client (GraphQL)
- **State**: React Context + Custom Hooks + Zustand

### Infrastructure
- **Containers**: Docker
- **Orchestration**: Kubernetes
- **GitOps**: ArgoCD
- **Observability**: Prometheus, Grafana, Loki

## Development Guidelines

### When Answering Questions

**ALWAYS follow this process:**

1. **Check Specification First**:
   - Start with `docs/MANIFEST.md` to locate relevant documents
   - Use `docs/06-workflows/spec-context-strategy.md` for context loading guidance
   - Load essential context: MANIFEST, architecture overview, glossary

2. **Find Relevant Spec Documents**:
   - Search `docs/` for relevant patterns, standards, or workflows
   - Use cross-references (`related:` in frontmatter) to find related docs
   - Reference specific spec sections in your answer

3. **Provide Spec-Based Answers**:
   - Quote or reference specific spec documents
   - Include spec paths in your response
   - Follow spec patterns and standards in your guidance

4. **Validate Against Spec**:
   - Ensure your answer aligns with specification
   - Reference relevant patterns, standards, or workflows
   - If spec doesn't cover the question, note that and suggest checking ADRs

### When Creating New Features

**Follow Spec-Driven Development workflow** (`docs/06-workflows/feature-development.md`):

1. **Reference the Specification**: Always check relevant spec documents before implementing
   - Use `docs/06-workflows/spec-context-strategy.md` for context loading
   - Follow phase-based context loading strategy
   - Load feature context: feature file, memory file, task breakdown

2. **Follow Architecture Patterns**: Use patterns documented in ADRs and architecture docs
   - Reference `docs/04-patterns/` for implementation patterns
   - Follow `docs/09-adr/` for architectural decisions

3. **Follow Workflows**: Use documented workflows from `docs/06-workflows/`
   - Feature Development: `docs/06-workflows/feature-development.md`
   - Code Review: `docs/06-workflows/code-review.md`
   - Testing: `docs/06-workflows/testing-workflow.md`
   - Deployment: `docs/06-workflows/deployment-workflow.md`

4. **Maintain Type Safety**: Ensure end-to-end type safety from database to UI
   - Follow type safety patterns from `docs/04-patterns/`

5. **Follow Directory Structure**: Adhere to structure defined in `docs/00-overview/project-structure.md`

6. **Use GraphQL Patterns**: Follow GraphQL patterns in `docs/04-patterns/api-patterns/graphql-query-pattern.md` (backend) and `docs/04-patterns/frontend-patterns/graphql-query-pattern.md` (frontend)

7. **Follow Component Patterns**: Use component system from `docs/04-patterns/frontend-patterns/shared-components-pattern.md` and `docs/04-patterns/frontend-patterns/management-pattern.md`

8. **Apply Testing Patterns**: Follow testing patterns from `docs/04-patterns/frontend-patterns/testing-pattern.md`

### Code Generation Rules

- **Backend (Kotlin/Micronaut)**:
  - Follow clean architecture: API → Service → Repository → Entity
  - Use dependency injection (Micronaut)
  - Implement GraphQL resolvers following federation patterns
  - Use JPA entities following patterns in `docs/04-patterns/backend-patterns/entity-pattern.md`
  - Follow database schema organization from `docs/01-architecture/data-architecture/schema-organization.md`

- **Frontend (TypeScript/Next.js)**:
  - Use App Router structure (Next.js 15+)
  - Follow directory structure from `docs/00-overview/project-structure.md`
  - Use components from design system (`docs/04-patterns/frontend-patterns/shared-components-pattern.md`)
  - Use GraphQL queries following `docs/04-patterns/frontend-patterns/graphql-query-pattern.md`
  - Use GraphQL mutations following `docs/04-patterns/frontend-patterns/graphql-mutation-pattern.md`
  - Use management patterns from `docs/04-patterns/frontend-patterns/management-pattern.md`
  - Use custom hooks patterns from `docs/04-patterns/frontend-patterns/hook-pattern.md`
  - Follow testing patterns from `docs/04-patterns/frontend-patterns/testing-pattern.md`

### GraphQL Development

- Use Apollo Federation for distributed GraphQL
- Follow GraphQL query patterns in `docs/04-patterns/api-patterns/graphql-query-pattern.md` (backend)
- Follow GraphQL query patterns in `docs/04-patterns/frontend-patterns/graphql-query-pattern.md` (frontend)
- Follow GraphQL mutation patterns in `docs/04-patterns/frontend-patterns/graphql-mutation-pattern.md` (frontend)
- Generate TypeScript types from GraphQL schema
- Use code generation for type safety
- Reference `contracts/graphql/` for contract details

### Database Development

- Use Flyway for migrations
- Follow JPA entity patterns from `docs/04-patterns/backend-patterns/entity-pattern.md`
- Follow pagination patterns from `docs/04-patterns/backend-patterns/pagination-pattern.md`
- Maintain domain-driven design principles

## Project Structure

```
neotool/
├── service/          # Backend services (Kotlin/Micronaut)
│   └── kotlin/      # Main service application (app, assistant, security, common modules)
├── web/              # Web frontend (Next.js)
├── infra/            # Infrastructure as Code
│   ├── docker/      # Docker Compose configurations
│   ├── router/      # Apollo Router configuration
│   └── observability/ # Prometheus, Grafana, Loki configs
├── contracts/        # API contracts (GraphQL schemas)
├── design/           # Design system and assets
├── docs/             # Specification and documentation (THIS IS THE KNOWLEDGE BASE)
└── scripts/          # Build and utility scripts
```

## Important Patterns

### Monorepo Architecture
- Clear separation of concerns
- Shared contracts and design system
- Independent service deployment

### GraphQL Federation
- Distributed schema development
- Type composition across services
- Apollo Router as gateway (configured in `infra/router/`)

### Domain-Driven Design
- Domain entities, services, repositories
- Clear boundaries and testability

### Clean Architecture
- Layered architecture
- Separation of concerns
- Testability

### Component-Driven Development
- Atomic design system
- Reusable UI components
- Design consistency

## When Suggesting Solutions

**ALWAYS follow Spec-Driven Development approach:**

1. **Always reference the spec first**: 
   - Check `docs/MANIFEST.md` to locate relevant documents
   - Use `docs/06-workflows/spec-context-strategy.md` for efficient context loading
   - Load phase-specific context as needed

2. **Follow established patterns**: 
   - Use patterns from `docs/04-patterns/`
   - Reference ADRs from `docs/09-adr/` for architectural decisions
   - Follow workflows from `docs/06-workflows/`

3. **Maintain consistency**: 
   - Follow existing code structure and conventions from spec
   - Reference `docs/05-standards/` for coding standards
   - Use templates from `docs/08-templates/`

4. **Ensure type safety**: 
   - Maintain end-to-end type safety as documented in patterns
   - Follow type safety patterns from `docs/04-patterns/`

5. **Consider architecture**: 
   - Respect the layered architecture from `docs/00-overview/architecture-overview.md`
   - Follow architecture standards from `docs/05-standards/architecture-standards/`

6. **Use design system**: 
   - Apply components and themes from the design system
   - Follow component patterns from `docs/04-patterns/frontend-patterns/`

7. **Follow GraphQL patterns**: 
   - Use federation patterns from `docs/04-patterns/api-patterns/graphql-federation.md`
   - Follow query/mutation patterns from `docs/04-patterns/frontend-patterns/`

8. **Validate against spec**: 
   - Use checklists from `docs/11-validation/`
   - Ensure compliance with all relevant spec documents

## Context Loading Strategy

**For efficient spec usage, follow `docs/06-workflows/spec-context-strategy.md`:**

### Essential Context (Always Load)
- `docs/MANIFEST.md` - Document index for navigation
- `docs/00-overview/architecture-overview.md` - System understanding
- `docs/02-domain/glossary.md` - Terminology

### Phase-Specific Context (Load as Needed)
- **For Questions**: Load relevant spec sections based on question topic
- **For Implementation**: Load phase-specific specs (domain, backend, frontend, testing)
- **For Code Review**: Load feature context + relevant pattern docs

### Context Optimization
- Start narrow, expand as needed
- Reference spec paths, don't copy full content
- Use cross-references (`related:` in frontmatter)
- Load patterns, reference examples

See `docs/06-workflows/spec-context-strategy.md` for detailed implementation flow and context management.

## RAG Integration

The specification is optimized for RAG indexing with:
- YAML frontmatter metadata in all documents
- Cross-references between related documents
- Semantic tags for search
- Structured manifest for document discovery
- Clear hierarchy and categorization

When searching for information:
1. Start with `docs/MANIFEST.md` for document discovery
2. Use `docs/06-workflows/spec-context-strategy.md` for context loading strategy
3. Use `docs/00-overview/architecture-overview.md` for system understanding
4. Reference `docs/02-domain/glossary.md` for terminology
5. Follow cross-references in documents for related context
6. Use category tags to find related documents

## Example: Answering Questions

When asked any question about the project:

1. **Load Essential Context**: 
   - `docs/MANIFEST.md` - Find relevant documents
   - `docs/00-overview/architecture-overview.md` - System understanding
   - `docs/02-domain/glossary.md` - Terminology

2. **Search Specification**: 
   - Use MANIFEST.md to locate relevant spec documents
   - Search `docs/` for patterns, standards, or workflows related to the question
   - Use cross-references to find related documents

3. **Load Relevant Specs**: 
   - Load only relevant spec sections (see `docs/06-workflows/spec-context-strategy.md`)
   - Reference specific spec documents in your answer
   - Quote relevant spec sections when appropriate

4. **Provide Spec-Based Answer**: 
   - Reference specific spec documents with paths
   - Follow spec patterns and standards in your guidance
   - Ensure answer aligns with specification

## Example: Creating a New Feature

When asked to create a new feature, follow the complete Spec-Driven Development workflow:

1. **Load Context Strategy**: 
   - Reference `docs/06-workflows/spec-context-strategy.md` for context loading
   - Follow the implementation flow diagram

2. **Feature Specification Phase**:
   - Use `/request` command or follow `docs/08-templates/feature-templates/feature-creation/workflow.md`
   - Create feature file, memory file, and task breakdown
   - Load feature context: feature file, memory file, task breakdown

3. **Implementation Phase** (follow `docs/06-workflows/feature-development.md`):
   - **Domain Phase**: Load domain specs, implement migrations and domain objects
   - **Backend Phase**: Load backend specs, implement entities, repositories, services, resolvers
   - **Frontend Phase**: Load frontend specs, implement components, hooks, GraphQL operations
   - **Testing Phase**: Load testing specs, implement unit, integration, E2E tests
   - **QA Phase**: Load QA specs, run linting, complete checklists

4. **Follow Patterns**: 
   - Use patterns from `docs/04-patterns/`
   - Follow standards from `docs/05-standards/`
   - Use workflows from `docs/06-workflows/`

5. **Validate**: 
   - Use checklists from `docs/11-validation/`
   - Ensure spec compliance at each phase

---

## Critical Rules

1. **The `docs/` folder is the source of truth for EVERYTHING**
2. **ALWAYS check the spec before answering any question**
3. **ALWAYS reference the spec when implementing anything**
4. **Use `docs/06-workflows/spec-context-strategy.md` for efficient context loading**
5. **Follow Spec-Driven Development (SDD) principles in all work**
6. **Reference specific spec documents with paths in all responses**
7. **Never invent patterns or approaches - use documented patterns from the spec**
