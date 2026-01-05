---
title: NeoTool Specification
type: overview
category: documentation
status: current
version: 3.0.0
tags: [documentation, specification, neotool, overview, quick-start]
ai_optimized: true
search_keywords: [specification, overview, quick-start, introduction]
related:
  - manifest.md
  - 01-overview/architecture-overview.md
last_updated: 2026-01-02
---

# NeoTool Specification

> **Purpose**: Complete specification for the NeoTool platform - the source of truth for architecture, design decisions, and implementation guidelines. Optimized for Spec-Driven Development with AI tools.

Welcome to the NeoTool specification hub. This directory contains all technical documentation, architecture decisions, patterns, templates, and guides for the NeoTool platform.

## Quick Navigation

- **[Specification Manifest](./manifest.md)** - Complete index of all documents
- **[Architecture Overview](./01-overview/architecture-overview.md)** - Start here for system understanding
- **[Technology Stack](./01-overview/technology-stack.md)** - Technology choices and versions
- **[Project Structure](./01-overview/project-structure.md)** - Monorepo organization
- **[Core Principles](./01-overview/principles.md)** - Design philosophy
- **[Quick Start](./01-overview/quick-start.md)** - Getting started guide

## Documentation Philosophy

### Core Learning Path (01-12)
Sequential documentation organized by learning flow:

```
01-overview/          → "What is NeoTool?"
02-architecture/      → "How is it designed?"
03-features/          → "What can it do?"
04-domain/            → "What are we modeling?"
05-backend/           → "How do we build backend?"
06-contracts/         → "How do services communicate?"
07-frontend/          → "How do we build frontend?"
08-workflows/         → "How do we work?"
09-security/          → "How do we stay secure?"
10-observability/     → "How do we monitor?"
11-infrastructure/    → "How do we deploy?"
12-specification-driven-dev/ → "How do we use this documentation?"
```

### Supporting Resources (90-94)
Quick reference and validation materials:

```
90-examples/          → "Show me code!"
91-templates/         → "Give me boilerplate!"
92-adr/               → "Why did we decide?"
93-reference/         → "Quick lookup"
94-validation/        → "Did I do it right?"
```

## Quick Start

### For Developers

**First Steps:**
1. **Read the architecture**: Start with [Architecture Overview](./01-overview/architecture-overview.md)
2. **Understand the tech stack**: Review [Technology Stack](./01-overview/technology-stack.md)
3. **Learn the patterns**: Explore [Backend Patterns](./05-backend/patterns/) or [Frontend Patterns](./07-frontend/patterns/)
4. **See examples**: Check [Examples](./90-examples/)

**Creating a Feature:**
1. **Fill the feature form**: Use [Feature Form](./91-templates/feature-templates/feature-form.md)
2. **Follow the workflow**: See [Feature Development Workflow](./08-workflows/feature-development.md)
3. **Use templates**: Reference [Code Templates](./91-templates/code-templates/)
4. **Validate**: Complete [Feature Checklist](./94-validation/feature-checklist.md)

### For AI Assistants (Cursor, Claude Code)

**When Creating Features:**
1. **Read the feature spec**: Process feature from [03-features/](./03-features/)
2. **Reference domain model**: Check [04-domain/domain-model.md](./04-domain/domain-model.md)
3. **Follow patterns**: Use [05-backend/patterns/](./05-backend/patterns/) or [07-frontend/patterns/](./07-frontend/patterns/)
4. **Apply standards**: Check [Backend Standards](./05-backend/kotlin/) or [Frontend Standards](./07-frontend/standards/)
5. **Use templates**: Apply [Code Templates](./91-templates/code-templates/) for structure
6. **Reference examples**: See [Examples](./90-examples/) for guidance

**When Answering Questions:**
1. **Check definitions**: Reference [Glossary](./04-domain/glossary.md)
2. **Find patterns**: Search relevant pattern directories
3. **Review standards**: Check coding and architecture standards
4. **See examples**: Look in [Examples](./90-examples/)

**SDD Workflow:**
- See [Specification-Driven Development](./12-specification-driven-dev/) for complete AI integration guide

## Core Documentation Sections

### 01-overview — Project Overview
High-level introduction to NeoTool:
- **Architecture Overview**: System architecture at a glance
- **Technology Stack**: Technology choices and versions
- **Project Structure**: Monorepo organization
- **Principles**: Core design principles
- **Quick Start**: Getting started guide

### 02-architecture — System Design
Comprehensive architecture documentation:
- **System Architecture**: Overall system design
- Service architecture, data architecture, API architecture, frontend architecture, infrastructure architecture (to be created)

### 03-features — Feature Specifications
Complete feature specs with Gherkin scenarios:
- **Authentication**: Sign in, sign up, password reset
- **Assets**: File storage and management
- **Security**: Authorization and user management
- Each feature includes README, Gherkin scenarios, and decisions

### 04-domain — Domain Modeling
Domain-driven design documentation:
- **Domain Model**: Entity relationships and boundaries
- **Glossary**: Complete terminology reference
- **Concepts**: Key domain concepts
- **Database Schema Standards**: Schema design rules
- **UUID v7 Standard**: Primary key strategy
- Data modeling patterns (to be created)

### 05-backend — Backend Development
Backend implementation guide organized by:
- **[kotlin/](./05-backend/kotlin/)**: Language-specific standards (coding, linting, testing)
- **[patterns/](./05-backend/patterns/)**: Cross-language patterns (entity, repository, service, GraphQL, Kafka, pagination, error handling)
- **[testing/](./05-backend/testing/)**: Testing strategies (unit, integration, test data builders, mocking)
- **[standards/](./05-backend/standards/)**: Architectural rules and constraints

**Future**: Ready for Go with `05-backend/go/` when needed

### 06-contracts — API Contracts
Service communication patterns:
- **GraphQL Standards**: GraphQL API conventions
- **GraphQL Query Pattern**: Resolver patterns
- Federation patterns, router config, schema evolution, REST standards (to be created)

### 07-frontend — Frontend Development
React and Next.js implementation guide:
- **[patterns/](./07-frontend/patterns/)**: Component patterns (GraphQL queries/mutations, management pages, styling, breadcrumbs, notifications)
- **[testing/](./07-frontend/testing/)**: Testing strategies (unit with Vitest, E2E with Playwright)
- **[standards/](./07-frontend/standards/)**: Coding, accessibility, performance standards (to be created)

### 08-workflows — Development Processes
Step-by-step workflow guides:
- **Feature Development**: Complete feature creation process
- **Code Review**: Review checklist and process
- **Testing Workflow**: Testing strategy and execution
- **Deployment Workflow**: CI/CD pipeline steps
- **Spec Context Strategy**: Documentation navigation for AI

### 09-security — Security Practices
Security implementation across all layers:
- **Authentication**: AuthN patterns and standards
- Authorization, RBAC/ABAC, JWT, mTLS, audit logging, secret management (to be created)

### 10-observability — Monitoring & Observability
Production monitoring and debugging:
- **Observability Overview**: Metrics, logging, tracing philosophy
- Metrics standards, logging standards, tracing, alerting, SLO definitions (to be created)

### 11-infrastructure — Deployment & Operations
Infrastructure and deployment:
- Docker Compose setup, Kubernetes manifests, GitOps workflow, CI/CD pipeline, environment management, disaster recovery (to be created)

### 12-specification-driven-dev — Meta-Documentation
How to use this documentation system:
- **[README](./12-specification-driven-dev/README.md)**: Complete SDD philosophy and workflow
- Cursor workflow, Claude Code workflow, prompt engineering, context management, safeguards, validation (to be created)

## Supporting Materials

### 90-examples — Code Examples
Concrete implementations:
- **[backend/](./90-examples/backend/)**: CRUD examples, batch workflows
- **[frontend/](./90-examples/frontend/)**: Component and page examples
- **full-stack/**: Complete feature implementations (to be created)

### 91-templates — Boilerplate & Templates
Reusable starting points:
- **[feature-templates/](./91-templates/feature-templates/)**: Feature form, questionnaire, workflow
- **[code-templates/](./91-templates/code-templates/)**: Entity, repository, service, resolver templates
- **[document-templates/](./91-templates/document-templates/)**: Documentation templates
- **[ai-prompts/](./91-templates/ai-prompts/)**: AI assistant prompt templates

### 92-adr — Architecture Decision Records
Key technical decisions with rationale:
- **ADR-0001**: Monorepo architecture
- **ADR-0002**: Containerized architecture
- **ADR-0003**: Kotlin/Micronaut backend
- **ADR-0004**: TypeScript/Next.js frontend
- **ADR-0005**: PostgreSQL database
- **ADR-0006**: Frontend authorization layer
- **ADR-0007**: Asset service with Cloudflare R2

### 93-reference — Quick Reference
Fast lookup guides:
- **Commands**: CLI commands reference
- **File Structure**: Project organization
- **GraphQL Schema**: Schema reference
- **API Endpoints**: API documentation
- **Backend Quick Reference**: Common imports, annotations, patterns

### 94-validation — Quality Assurance
Checklists and validation:
- **Feature Checklist**: Feature completion verification
- **Code Review Checklist**: Review quality gates
- **PR Checklist**: Pull request requirements
- **Validation Scripts**: Automated validation tools

## Feature Creation Flow

The specification enables streamlined Spec-Driven Development:

1. **Fill Feature Form** → [Feature Form Template](./91-templates/feature-templates/feature-form.md)
2. **AI Processes Form** → Generates questionnaire, documentation, implementation plan
3. **AI Uses Patterns** → Follows [Backend Patterns](./05-backend/patterns/) or [Frontend Patterns](./07-frontend/patterns/)
4. **AI Applies Standards** → Adheres to coding and architecture standards
5. **Developer Validates** → Uses [Validation Checklists](./94-validation/)
6. **Code Review** → Uses [Code Review Checklist](./94-validation/code-review-checklist.md)

See [Feature Development Workflow](./08-workflows/feature-development.md) and [SDD Guide](./12-specification-driven-dev/) for complete process.

## Document Format

All documents follow a standard format with YAML frontmatter:

```yaml
---
title: Document Title
type: [overview|architecture|domain|feature|pattern|standard|workflow|example|template|adr|reference|validation]
category: [category-name]
status: [current|deprecated|draft]
version: 1.0.0
tags: [tag1, tag2, tag3]
related:
  - path/to/related-doc.md
ai_optimized: true
search_keywords: [keyword1, keyword2]
last_updated: YYYY-MM-DD
---
```

## Migration Notes

**Version 3.0.0 (2026-01-02)**: Major documentation reorganization
- Renumbered from `00-11` to `01-12` + `90-94` taxonomy
- Separated core learning path from supporting resources
- Added cross-cutting concerns (security, observability, infrastructure)
- Organized backend for multi-language support (Kotlin + future Go)
- Created SDD meta-documentation section
- Backup available at `docs.backup-20260102/`

See [documentation-reorg-proposal.md](./documentation-reorg-proposal.md) and [migration-plan.md](./migration-plan.md) for details.

## Contributing

### Adding New Documentation
1. Determine appropriate section (01-12 for learning, 90-94 for reference)
2. Create file in the correct directory
3. Include proper YAML frontmatter
4. Add cross-references to related documents
5. Update [manifest.md](./manifest.md)

### Updating Existing Documentation
1. Keep content synchronized with code changes
2. Improve clarity and add examples
3. Fix broken links and references
4. Update version and last_updated fields
5. Update manifest.md if structure changes

## Related Resources

- **Project Repository**: See root [README.md](../README.md)
- **CLI Tool**: `./neotool` for validation, schema sync, and common tasks
- **Contracts**: [contracts/](../contracts/) directory for GraphQL schemas
- **Infrastructure**: [infra/](../infra/) directory for deployment configs

---

*This specification follows enterprise best practices for technical documentation and is designed to scale with the NeoTool platform. It is optimized for Spec-Driven Development with AI tools like Cursor and Claude Code.*

**Version**: 3.0.0 (2026-01-02)
**Organization**: `01-12` (core learning) + `90-94` (supporting resources)
**Philosophy**: Documentation drives implementation. Code follows specification.
