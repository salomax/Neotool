# NeoTool Specification Manifest

> **Purpose**: This manifest serves as the source of truth index for the NeoTool specification, optimized for RAG indexing and AI IDE integration.

## Metadata

- **Specification Version**: 1.0.0
- **Last Updated**: 2024
- **Format**: Markdown with YAML frontmatter
- **Target Audience**: Developers, AI assistants, RAG systems
- **Indexing Strategy**: Semantic search, keyword indexing, cross-reference mapping

## Specification Structure

### Core Documents

| Document | Path | Type | Category | Keywords |
|----------|------|------|----------|----------|
| Architecture Overview | `ARCHITECTURE_OVERVIEW.md` | Overview | Architecture | architecture, overview, system design |
| Glossary | `GLOSSARY.md` | Reference | Terminology | glossary, terms, definitions |
| Quick Reference | `QUICK_REFERENCE.md` | Reference | Quick Reference | quick reference, cheat sheet, patterns |
| RAG Optimization | `RAG_OPTIMIZATION.md` | Meta | Documentation | rag, indexing, ai, optimization |
| Specification Manifest | `SPECIFICATION_MANIFEST.md` | Index | Documentation | manifest, index, specification |
| Quick Start | `README.md` | Guide | Getting Started | quick start, setup, introduction |
| Project Setup | `PROJECT_SETUP.md` | Guide | Setup | setup, configuration, initialization |

### Architecture Decision Records (ADRs)

| ADR | Path | Status | Category | Keywords |
|-----|------|--------|----------|----------|
| ADR-0001 | `adr/0001-monorepo-architecture.md` | Accepted | Architecture | monorepo, repository structure |
| ADR-0002 | `adr/0002-containerized-architecture.md` | Accepted | Infrastructure | docker, kubernetes, containers |
| ADR-0003 | `adr/0003-kotlin-micronaut-backend.md` | Accepted | Backend | kotlin, micronaut, backend |
| ADR-0004 | `adr/0004-typescript-nextjs-frontend.md` | Accepted | Frontend | typescript, nextjs, react, frontend |
| ADR-0005 | `adr/0005-postgresql-database.md` | Accepted | Database | postgresql, database, data storage |

### Service Layer Documentation

| Document | Path | Category | Keywords |
|----------|------|----------|----------|
| GraphQL Federation | `service/graphql-federation-architecture.md` | API | graphql, federation, apollo |
| Database Schema | `service/database-schema-organization.md` | Database | database, schema, organization |
| JPA Entities | `service/kotlin/jpa-entity.md` | Backend | jpa, entity, kotlin |

### Web Frontend Documentation

| Document | Path | Category | Keywords |
|----------|------|----------|----------|
| Source Structure | `web/web-src-structure.md` | Frontend | structure, organization, best practices |
| Themes | `web/web-themes.md` | UI | themes, design tokens, styling |
| Themes Quick Ref | `web/web-themes-quick-reference.md` | Reference | themes, quick reference |
| GraphQL Operations | `web/web-graphql-operations.md` | API | graphql, operations, frontend |
| i18n Architecture | `web/web-i18n-architecture.md` | Frontend | internationalization, i18n, localization |
| Components | `web/web-components.md` | UI | components, design system, ui |
| Custom Hooks | `web/web-custom-hooks.md` | Frontend | hooks, react, custom hooks |

### Contracts Documentation

| Document | Path | Category | Keywords |
|----------|------|----------|----------|
| GraphQL Federation | `contracts/graphql-federation.md` | API | graphql, federation, contracts |

### Design Documentation

| Document | Path | Category | Keywords |
|----------|------|----------|----------|
| Assets | `design/assets.md` | Design | assets, design system, branding |

## Document Categories

### By Type
- **Architecture**: System design, decisions, patterns
- **Guides**: Step-by-step instructions, tutorials
- **Reference**: Quick lookups, APIs, specifications
- **Best Practices**: Conventions, standards, patterns

### By Layer
- **Infrastructure**: Docker, Kubernetes, deployment
- **Backend**: Kotlin, Micronaut, services
- **Frontend**: React, Next.js, TypeScript
- **Database**: PostgreSQL, schema, migrations
- **API**: GraphQL, Federation, contracts
- **Design**: UI, components, assets

### By Audience
- **Developers**: Implementation guides, code examples
- **Architects**: ADRs, system design
- **DevOps**: Infrastructure, deployment
- **Designers**: Design system, assets

## Cross-Reference Map

### Architecture Flow
```
ARCHITECTURE_OVERVIEW.md
  ├── adr/0001-monorepo-architecture.md
  ├── adr/0002-containerized-architecture.md
  ├── adr/0003-kotlin-micronaut-backend.md
  ├── adr/0004-typescript-nextjs-frontend.md
  └── adr/0005-postgresql-database.md
```

### Backend Flow
```
adr/0003-kotlin-micronaut-backend.md
  ├── service/graphql-federation-architecture.md
  ├── service/database-schema-organization.md
  ├── service/kotlin/jpa-entity.md
  └── contracts/graphql-federation.md
```

### Frontend Flow
```
adr/0004-typescript-nextjs-frontend.md
  ├── web/web-src-structure.md
  ├── web/web-components.md
  ├── web/web-themes.md
  ├── web/web-graphql-operations.md
  └── web/web-i18n-architecture.md
```

## Search Optimization Tags

### Technology Stack
- `kotlin`, `micronaut`, `gradle`
- `typescript`, `react`, `nextjs`
- `postgresql`, `graphql`, `apollo`
- `docker`, `kubernetes`, `argo-cd`
- `prometheus`, `grafana`, `loki`

### Concepts
- `monorepo`, `microservices`, `federation`
- `domain-driven-design`, `clean-architecture`
- `type-safety`, `code-generation`
- `observability`, `monitoring`, `logging`
- `ci-cd`, `gitops`, `deployment`

### Patterns
- `dependency-injection`, `repository-pattern`
- `graphql-resolvers`, `federation-directives`
- `server-components`, `client-components`
- `design-tokens`, `component-system`

## RAG Indexing Guidelines

### For AI Assistants
1. **Start with**: `ARCHITECTURE_OVERVIEW.md` for system understanding
2. **Reference**: `GLOSSARY.md` for terminology
3. **Follow**: Cross-references in documents for related topics
4. **Use**: Category tags to find related documents

### For RAG Systems
1. **Index all markdown files** with YAML frontmatter
2. **Extract metadata** from frontmatter for filtering
3. **Build semantic embeddings** for each document
4. **Maintain cross-reference graph** for navigation
5. **Index code examples** separately for code search

### For Context7 Integration
1. **Use manifest** for document discovery
2. **Leverage categories** for scoped searches
3. **Follow cross-references** for context expansion
4. **Respect document hierarchy** for relevance ranking

## Versioning

- Documents are versioned independently
- ADRs track decision history
- Breaking changes require new ADRs
- Specification version tracks overall structure

## Maintenance

- Update manifest when adding/removing documents
- Keep cross-references current
- Maintain keyword consistency
- Review and update categories quarterly

