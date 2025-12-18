---
title: Project Structure
type: overview
category: structure
status: current
version: 2.0.0
tags: [monorepo, structure, organization, directories]
ai_optimized: true
search_keywords: [structure, monorepo, directories, organization, file-structure]
related:
  - 00-overview/architecture-overview.md
  - 09-adr/0001-monorepo-architecture.md
  - 10-reference/file-structure.md
---

# Project Structure

> **Purpose**: Detailed explanation of the NeoTool monorepo structure and organization.

## Overview

NeoTool uses a **monorepo architecture** to organize all components (backend, frontend, mobile, infrastructure, contracts, and documentation) in a single repository.

See [ADR-0001: Monorepo Architecture](../09-adr/0001-monorepo-architecture.md) for the decision rationale.

## Root Structure

```
neotool/
├── service/          # Backend services (Kotlin/Micronaut)
├── web/              # Web frontend (Next.js)
├── mobile/           # Mobile app (React Native/Expo)
├── infra/            # Infrastructure as Code
├── contracts/        # API contracts (GraphQL schemas)
├── docs/             # Specification and documentation
├── design/           # Design system and assets
├── docs/             # Project documentation
├── scripts/          # Build and utility scripts
├── project.config.json  # Project configuration
└── README.md         # Project README
```

## Backend Services (`service/`)

### Structure
```
service/
├── kotlin/           # Main Kotlin service modules
│   ├── app/          # Main application module
│   ├── assistant/    # Assistant service module
│   ├── security/     # Security service module
│   ├── common/       # Shared common code
│   ├── build.gradle.kts  # Root build file
│   └── settings.gradle.kts  # Module settings
└── gateway/          # Apollo Router configuration
```

### Module Organization

#### `app/` Module
- Main application service
- Domain entities
- GraphQL resolvers
- Business logic
- Database migrations

#### `assistant/` Module
- Assistant service (if applicable)
- Separate domain
- Independent GraphQL schema

#### `security/` Module
- Authentication
- Authorization
- JWT handling
- User management

#### `common/` Module
- Shared utilities
- Common types
- Base classes
- Shared GraphQL types

### Backend File Structure
```
service/kotlin/[module]/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── io/github/salomax/neotool/
│   │   │       ├── api/          # REST controllers (if any)
│   │   │       ├── domain/       # Domain models
│   │   │       ├── entity/       # JPA entities
│   │   │       ├── graphql/      # GraphQL resolvers
│   │   │       ├── repo/         # Repositories
│   │   │       ├── service/      # Business logic
│   │   │       └── Application.kt
│   │   ├── resources/
│   │   │   ├── application.yml   # Configuration
│   │   │   ├── graphql/
│   │   │   │   └── schema.graphqls  # GraphQL schema
│   │   │   └── db/
│   │   │       └── migration/    # Flyway migrations
│   │   └── db/
│   │       └── changelog/        # Liquibase (if used)
│   └── test/
│       └── kotlin/               # Tests
├── build.gradle.kts
└── Dockerfile
```

## Web Frontend (`web/`)

### Structure
```
web/
├── src/
│   ├── app/          # Next.js App Router
│   ├── shared/       # Shared components and utilities
│   ├── lib/          # Libraries and utilities
│   ├── styles/       # Global styles and themes
│   └── types/        # TypeScript types
├── public/           # Static assets
├── tests/            # E2E tests
├── package.json
├── next.config.mjs
└── Dockerfile
```

### App Router Structure
```
web/src/app/
├── (neotool)/        # Route group
│   ├── layout.tsx    # Root layout
│   ├── page.tsx      # Home page
│   └── [routes]/     # Feature routes
├── api/              # API routes (if any)
└── globals.css       # Global styles
```

### Shared Components
```
web/src/shared/
├── components/       # Reusable components
│   ├── atoms/        # Atomic components
│   ├── molecules/    # Composite components
│   └── organisms/    # Complex components
├── hooks/            # Custom React hooks
├── utils/            # Utility functions
└── constants/        # Constants
```

## Mobile App (`mobile/`)

### Structure (Planned)
```
mobile/
├── src/
│   ├── app/          # App screens
│   ├── components/   # Reusable components
│   ├── hooks/        # Custom hooks
│   └── lib/          # Libraries
├── app.json          # Expo configuration
└── package.json
```

## Infrastructure (`infra/`)

### Structure
```
infra/
├── docker/
│   ├── docker-compose.yml        # Production compose
│   └── docker-compose.local.yml  # Local development
├── k8s/              # Kubernetes manifests
│   ├── deployments/
│   ├── services/
│   └── configmaps/
├── observability/
│   ├── prometheus/   # Prometheus configs
│   ├── grafana/      # Grafana dashboards
│   └── loki/         # Loki configs
└── router/           # Apollo Router configs
```

## Contracts (`contracts/`)

### Structure
```
contracts/
└── graphql/
    ├── subgraphs/    # Individual subgraph schemas
    │   ├── app/
    │   ├── assistant/
    │   └── security/
    └── supergraph/   # Composed supergraph
        ├── supergraph.graphql
        └── supergraph.yaml
```

## Specification (`docs/`)

### Structure
```
docs/
├── 00-overview/          # Core specification
├── 05-standards/         # Rules
├── 02-definitions/   # Definitions
├── 04-patterns/      # Patterns
├── 04-templates/     # Templates
├── 05-examples/      # Examples
├── 06-workflows/     # Workflows
├── 07-validation/    # Validation
├── 09-adr/           # Architecture Decision Records
└── 10-reference/     # Reference guides
```

See [Specification Manifest](./MANIFEST.md) for complete structure.

## Design System (`design/`)

### Structure
```
design/
├── assets/
│   ├── icons/        # Icon assets
│   ├── images/       # Image assets
│   ├── logos/        # Logo assets
│   └── illustrations/ # Illustration assets
└── prototypes/       # Design prototypes
    ├── figma/
    └── adobe/
```

## Scripts (`scripts/`)

### Structure
```
scripts/
├── cli/              # CLI tool
│   ├── cli           # Main CLI script
│   ├── commands/     # Command scripts
│   └── utils.sh      # Utilities
└── [other-scripts]/  # Other utility scripts
```

## Naming Conventions

### Directories
- **kebab-case**: For most directories (`service/`, `web/`, `infra/`)
- **camelCase**: For source directories (`src/main/kotlin/`)
- **PascalCase**: For route groups in Next.js (`(neotool)/`)

### Files
- **kebab-case**: For configuration files (`docker-compose.yml`)
- **camelCase**: For source files (`Application.kt`)
- **PascalCase**: For React components (`MyComponent.tsx`)

### Packages
- **kebab-case**: For npm packages (`my-project-web`)
- **reverse-domain**: For Java/Kotlin packages (`io.github.salomax.neotool`)

## Module Dependencies

### Backend Modules
```
app → common
assistant → common
security → common
```

### Frontend
```
app → shared → lib
```

## Build Artifacts

### Excluded from Repository
- `node_modules/` - Node.js dependencies
- `build/` - Build outputs
- `.next/` - Next.js build output
- `.gradle/` - Gradle cache
- `dist/` - Distribution files

### Included
- `package-lock.json` / `pnpm-lock.yaml` - Dependency locks
- `gradle/wrapper/` - Gradle wrapper

## Related Documentation

- [Architecture Overview](./architecture.md)
- [ADR-0001: Monorepo Architecture](../09-adr/0001-monorepo.md)
- [File Structure Reference](../10-reference/file-structure.md)
- [Backend Patterns](../04-patterns/backend/)
- [Frontend Patterns](../04-patterns/frontend/)

