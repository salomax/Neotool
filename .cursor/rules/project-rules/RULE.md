---
title: NeoTool Project Rules
description: Comprehensive project rules for NeoTool including specification knowledge base, technology stack, development guidelines, and architectural patterns
alwaysApply: true
---

# NeoTool Project Rules for Cursor AI

## Specification Knowledge Base

This project uses a comprehensive specification located in the `docs/` directory. **ALWAYS** reference the specification when:
- Creating new features
- Making architectural decisions
- Writing code that follows project patterns
- Understanding the technology stack
- Following coding conventions

### Key Specification Documents

1. **Start Here**: `docs/MANIFEST.md` - Complete index of all specification documents
2. **Architecture**: `docs/00-overview/architecture-overview.md` - System architecture and technology stack
3. **Quick Reference**: `docs/10-reference/` - Common patterns, commands, and conventions
4. **Glossary**: `docs/02-domain/glossary.md` - Terminology and definitions
5. **Project Setup**: `docs/00-overview/quick-start.md` - Setup and configuration guide

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

### When Creating New Features

1. **Reference the Specification**: Always check relevant spec documents before implementing
2. **Follow Architecture Patterns**: Use patterns documented in ADRs and architecture docs
3. **Maintain Type Safety**: Ensure end-to-end type safety from database to UI
4. **Follow Directory Structure**: Adhere to structure defined in `docs/00-overview/project-structure.md` (frontend) and service docs (backend)
5. **Use GraphQL Patterns**: Follow GraphQL patterns in `docs/04-patterns/api-patterns/graphql-query-pattern.md` (backend) and `docs/04-patterns/frontend-patterns/graphql-query-pattern.md` (frontend)
6. **Follow Component Patterns**: Use component system from `docs/04-patterns/frontend-patterns/shared-components-pattern.md` and `docs/04-patterns/frontend-patterns/management-pattern.md`
7. **Apply Testing Patterns**: Follow testing patterns from `docs/04-patterns/frontend-patterns/testing-pattern.md`

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

1. **Always reference the spec**: Check `docs/` folder for relevant documentation
2. **Follow established patterns**: Use patterns from ADRs and architecture docs
3. **Maintain consistency**: Follow existing code structure and conventions
4. **Ensure type safety**: Maintain end-to-end type safety
5. **Consider architecture**: Respect the layered architecture and boundaries
6. **Use design system**: Apply components and themes from the design system
7. **Follow GraphQL patterns**: Use federation patterns for API development

## RAG Integration

The specification is optimized for RAG indexing with:
- YAML frontmatter metadata in all documents
- Cross-references between related documents
- Semantic tags for search
- Structured manifest for document discovery
- Clear hierarchy and categorization

When searching for information:
1. Start with `docs/MANIFEST.md` for document discovery
2. Use `docs/00-overview/architecture-overview.md` for system understanding
3. Reference `docs/02-domain/glossary.md` for terminology
4. Follow cross-references in documents for related context
5. Use category tags to find related documents

## Example: Creating a New Feature

When asked to create a new feature:

1. **Understand Requirements**: Review the feature request
2. **Check Specification**: Search `docs/` for relevant patterns and guidelines
3. **Follow Architecture**: Use patterns from architecture docs and ADRs
4. **Implement Backend** (if needed):
   - Create GraphQL schema in appropriate subgraph
   - Implement resolver following federation patterns
   - Create service layer following clean architecture
   - Add repository and entity following JPA patterns
   - Add database migration if needed
5. **Implement Frontend** (if needed):
   - Create components using design system
   - Use theme tokens for styling
   - Implement GraphQL operations
   - Add i18n support
   - Follow directory structure
6. **Ensure Type Safety**: Generate types and maintain end-to-end type safety
7. **Follow Testing Patterns**: Add appropriate tests

---

**Remember**: The `docs/` folder is the source of truth. Always reference it when making decisions or implementing features.
