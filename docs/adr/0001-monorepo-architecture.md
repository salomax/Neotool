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
We will use a **monorepo architecture** to organize all NeoTool components under a single repository.

The monorepo structure includes:
```
neotool/
├── service/        # Kotlin backend modules (gateway, common, app)
├── web/            # Web frontend (Next.js)
├── mobile/         # Mobile app (React Native / Expo)
├── infra/          # Docker, K8s, GitOps, observability
├── contracts/      # GraphQL + OpenAPI contracts
├── design/         # Brand assets & design system
├── docs/           # ADRs and docs site
└── README.md
```

## Consequences

### Positive
- **Unified development experience**: Single repository for all components reduces context switching
- **Shared contracts and design system**: Easy to maintain consistency across frontend, backend, and mobile
- **Atomic changes**: Can make changes across multiple layers in a single commit
- **Simplified CI/CD**: Single pipeline can test and deploy all components together
- **Code reuse**: Common utilities, types, and configurations can be shared easily
- **Consistent tooling**: Single set of linting, formatting, and testing tools across all components
- **Easier local development**: Single `docker-compose` setup for the entire stack

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
- **Clear boundaries**: Enforce clear module boundaries and API contracts
- **Incremental builds**: Use build tools that support incremental compilation
- **Modular deployment**: Deploy components independently despite shared repository
- **Regular cleanup**: Periodically review and remove unused code to keep repository size manageable

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
