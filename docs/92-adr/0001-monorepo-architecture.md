---
title: ADR-0001 Monorepo Architecture
type: adr
category: architecture
status: accepted
version: 2.0.0
tags: [monorepo, architecture, repository-structure, organization, nx]
related:
  - ARCHITECTURE_OVERVIEW.md
  - adr/0002-containerized-architecture.md
  - adr/0003-nx-monorepo-tooling.md
---

# ADR-0001: Monorepo Architecture

## Status
Accepted

## Context
NeoTool is designed as a **modular full-stack boilerplate** that accelerates new app development while maintaining clean architecture and best practices. The project needs to support multiple layers including backend services, frontend applications, mobile apps, infrastructure, contracts, design system, and documentation.

The key challenge is how to organize and manage these different components while maintaining:
- Code reusability across layers
- Consistent development experience
- Unified tooling and CI/CD
- Easy local development setup
- Clear separation of concerns

## Decision
We will use a **monorepo architecture** powered by **Nx** to organize all NeoTool components under a single repository.

The monorepo structure includes:
```
neotool/
├── apps/
│   ├── web/            # Next.js application
│   └── mobile/         # React Native / Expo mobile app
├── services/
│   ├── security/       # Kotlin authentication service
│   ├── assets/         # Kotlin assets management service
│   ├── financialdata/  # Kotlin financial data service
│   └── indicators/     # Go indicators processing service
├── libs/               # Shared libraries
│   ├── kotlin-common/  # Common Kotlin utilities
│   ├── ts-utils/       # TypeScript utilities
│   └── go-shared/      # Go shared packages
├── infra/              # Docker, K8s, GitOps, observability
├── contracts/          # GraphQL + OpenAPI contracts
├── docs/               # ADRs and docs site
├── nx.json             # Nx workspace configuration
├── package.json        # Root package.json with Nx
└── README.md
```

**Tooling**: We use **Nx** as our monorepo build system to manage dependencies, orchestrate tasks, and provide intelligent caching across our polyglot codebase (TypeScript, Kotlin, Go). See [ADR-0003](./0003-nx-monorepo-tooling.md) for detailed rationale.

## Consequences

### Positive
- **Unified development experience**: Single repository for all components reduces context switching
- **Shared contracts and design system**: Easy to maintain consistency across frontend, backend, and mobile
- **Atomic changes**: Can make changes across multiple layers in a single commit
- **Simplified CI/CD**: Single pipeline can test and deploy all components together
- **Code reuse**: Common utilities, types, and configurations can be shared easily
- **Consistent tooling**: Single set of linting, formatting, and testing tools across all components
- **Easier local development**: Single `docker-compose` setup for the entire stack
- **Intelligent builds with Nx**: Only affected projects are built/tested, dramatically reducing CI time
- **Remote caching**: Nx Cloud provides free remote caching, avoiding redundant builds across team members
- **Dependency graph**: Visual representation of project dependencies helps understand system architecture

### Negative
- **Repository size**: Can grow large over time, potentially affecting clone times
- **Build complexity**: Need to manage dependencies and build order across multiple components
- **Access control**: Harder to implement fine-grained permissions for different teams
- **Tooling complexity**: Some tools may not work well with large monorepos

### Risks
- **Coupling risk**: Teams might create tight coupling between components that should be independent
- **Build performance**: Large monorepos can have slower build times
- **Git performance**: Large repositories can impact git operations

### Mitigation Strategies
- **Clear boundaries**: Enforce clear module boundaries and API contracts using Nx project tags and constraints
- **Incremental builds**: Nx provides affected detection based on dependency graph analysis
- **Modular deployment**: Deploy components independently despite shared repository using independent versioning
- **Regular cleanup**: Periodically review and remove unused code to keep repository size manageable
- **Build optimization**: Nx computation caching (local + remote) ensures fast builds even in large codebases
- **CI optimization**: Use `nx affected` commands to run only necessary tests/builds in CI pipeline

## Alternatives Considered

### Multi-repo approach
- **Pros**: Independent versioning, smaller repositories, team autonomy
- **Cons**: Harder to maintain consistency, complex dependency management, multiple CI/CD pipelines

### Hybrid approach (some components in monorepo, others separate)
- **Pros**: Balance between monorepo benefits and independence
- **Cons**: Inconsistent development experience, complex tooling setup

## Decision Drivers
- NeoTool's goal of being a "foundation framework" that helps spin up new services
- Need for tight integration between design system, contracts, and implementations
- Desire for simplified local development setup
- Focus on developer velocity and consistency
