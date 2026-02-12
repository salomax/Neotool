# Frontend Documentation

This section contains all frontend development documentation for NeoTool web applications.

## Organization

### Roadmaps
- **[Web to mobile migration roadmap](mobile-migration-roadmap.md)** — Phased plan for parity-driven migration from `web/` to `mobile/`
- **[Phase 0 discovery baseline](mobile-phase-0-discovery-baseline.md)** — Initial parity matrix and migration priority baseline
- **[Phase 0 P0 dependency map](mobile-phase-0-p0-dependency-map.md)** — Concrete dependency mapping for auth and financial-data P0 slices
- **[Phase 0 P0 backlog](mobile-phase-0-p0-backlog.md)** — Detailed task-by-task execution backlog for P0

### [patterns/](patterns/)
React and Next.js implementation patterns:
- **GraphQL query pattern** — Apollo Client queries with caching
- **GraphQL mutation pattern** — Mutations with optimistic updates
- **Mutation pattern** — Advanced mutation handling
- **Management pattern** — Reusable management page patterns
- **Shared components pattern** — Component library usage
- **Breadcrumb pattern** — Navigation breadcrumbs
- **Styling pattern** — Design tokens and theming
- **Toast notification pattern** — User feedback

### [testing/](testing/)
Frontend testing strategies:
- **Unit testing** — Component and hook tests with Vitest
- **E2E testing** — End-to-end tests with Playwright

### [standards/](standards/)
Frontend code quality and compliance:
- **Coding standards** — TypeScript and React conventions
- **Accessibility standards** — WCAG compliance and a11y patterns
- **Performance standards** — Performance budgets and optimization

## Tech Stack

- **Framework**: Next.js 14+ with App Router
- **Language**: TypeScript
- **GraphQL Client**: Apollo Client with Code Generator
- **Testing**: Vitest (unit), Playwright (E2E)
- **Component Docs**: Storybook
- **Linting**: ESLint with TypeScript rules
- **Styling**: Design tokens from `/design/`

## Quick Reference

### Common Tasks
- **Plan web-to-mobile migration**: See [Web to Mobile Migration Roadmap](mobile-migration-roadmap.md)
- **Start Phase 0 discovery**: See [Mobile Migration Phase 0 Discovery Baseline](mobile-phase-0-discovery-baseline.md)
- **Map P0 dependencies**: See [Mobile Migration Phase 0 P0 Dependency Map](mobile-phase-0-p0-dependency-map.md)
- **Execute P0 backlog**: See [Mobile Migration Phase 0 P0 Backlog](mobile-phase-0-p0-backlog.md)
- **Fetch data with GraphQL**: See [GraphQL Query Pattern](patterns/graphql-query-pattern.md)
- **Submit mutations**: See [GraphQL Mutation Pattern](patterns/graphql-mutation-pattern.md)
- **Build management page**: See [Management Pattern](patterns/management-pattern.md)
- **Add shared component**: See [Shared Components Pattern](patterns/shared-components-pattern.md)
- **Show notifications**: See [Toast Notification Pattern](patterns/toast-notification-pattern.md)
- **Style components**: See [Styling Pattern](patterns/styling-pattern.md)

### Testing
- **Write unit tests**: See [Unit Testing](testing/unit-testing.md)
- **Write E2E tests**: See [E2E Testing](testing/e2e-testing.md)

### Code Quality
- **TypeScript conventions**: See [Coding Standards](standards/coding-standards.md) (to be created)
- **Accessibility**: See [Accessibility Standards](standards/accessibility-standards.md) (to be created)
- **Performance**: See [Performance Standards](standards/performance-standards.md) (to be created)

## Related Documentation

- **API contracts**: See [06-contracts/](../06-contracts/)
- **Architecture**: See [02-architecture/frontend-architecture.md](../02-architecture/) (to be created)
- **Examples**: See [90-examples/frontend/](../90-examples/frontend/)
- **Workflows**: See [08-workflows/](../08-workflows/)

## Coverage Requirements

- **Minimum**: 80% for branches, functions, lines, and statements
- **Enforced in**: CI/CD pipeline
- See [Testing Workflow](../08-workflows/testing-workflow.md) for details
