---
title: NeoTool Specification
type: overview
category: documentation
status: current
version: 2.0.0
tags: [documentation, specification, neotool, overview, quick-start]
ai_optimized: true
search_keywords: [specification, overview, quick-start, introduction]
related:
  - MANIFEST.md
  - 00-overview/architecture-overview.md
---

# NeoTool Specification

> **Purpose**: Complete specification for the NeoTool platform - the source of truth for architecture, design decisions, and implementation guidelines. Optimized for Spec-Driven Development with AI tools.

Welcome to the NeoTool specification hub. This directory contains all technical documentation, architecture decisions, patterns, templates, and guides for the NeoTool platform.

## Quick Navigation

- **[Specification Manifest](./MANIFEST.md)** - Complete index of all documents
- **[Architecture Overview](./00-overview/architecture-overview.md)** - Start here for system understanding
- **[Technology Stack](./00-overview/technology-stack.md)** - Technology choices and versions
- **[Project Structure](./00-overview/project-structure.md)** - Monorepo organization
- **[Core Principles](./00-overview/principles.md)** - Design philosophy
- **[Quick Start](./00-overview/quick-start.md)** - Getting started guide

## Specification Structure

The specification is organized into numbered directories for clear hierarchy:

```
docs/
├── 00-overview/      # High-level overview (architecture, tech stack, principles)
├── 01-architecture/  # System & service architecture
├── 02-domain/        # Domain model & business logic
├── 03-features/      # Feature specifications (SDD)
├── 04-patterns/      # Implementation patterns (backend, frontend, API, infrastructure)
├── 05-standards/     # Coding standards & rules
├── 06-workflows/     # Development workflows (feature dev, code review, testing, deployment)
├── 07-examples/      # Concrete examples (backend, frontend, full-stack)
├── 08-templates/     # Reusable templates (feature creation, AI prompts, code, documents)
├── 09-adr/           # Architecture Decision Records
├── 10-reference/     # Quick reference guides (commands, file structure, GraphQL, API)
└── 11-validation/    # Checklists & validation (feature, code review, PR)
```

## Quick Start

### For Developers

**First Steps:**
1. **Read the architecture**: Start with [Architecture Overview](./00-overview/architecture-overview.md)
2. **Understand the tech stack**: Review [Technology Stack](./00-overview/technology-stack.md)
3. **Learn the patterns**: Explore [Patterns](./04-patterns/)
4. **See examples**: Check [Examples](./07-examples/)

**Creating a Feature:**
1. **Fill the feature form**: Use [Feature Form](./08-templates/feature-templates/feature-form.md)
2. **Follow the workflow**: See [Feature Development Workflow](./06-workflows/feature-development.md)
3. **Use templates**: Reference [Templates](./08-templates/)
4. **Validate**: Complete [Feature Checklist](./11-validation/feature-checklist.md)

### For AI Assistants

**When Creating Features:**
1. **Read the feature form**: Process [Feature Form](./08-templates/feature-templates/feature-form.md)
2. **Reference standards**: Check [Standards](./05-standards/) for constraints
3. **Follow patterns**: Use [Patterns](./04-patterns/) for implementation
4. **Use templates**: Apply [Templates](./08-templates/) for structure
5. **Reference examples**: See [Examples](./07-examples/) for guidance

**When Answering Questions:**
1. **Check definitions**: Reference [Glossary](./02-domain/glossary.md)
2. **Find patterns**: Search [Patterns](./04-patterns/)
3. **Review standards**: Check [Standards](./05-standards/)
4. **See examples**: Look in [Examples](./07-examples/)

## Key Sections

### Overview (00-overview)
- **Architecture Overview**: System architecture at a glance
- **Technology Stack**: Technology choices and versions
- **Project Structure**: Monorepo organization
- **Principles**: Core design principles
- **Quick Start**: Getting started guide

### Architecture (01-architecture)
- **System Architecture**: Overall system design
- **Service Architecture**: Service-level architecture
- **Frontend Architecture**: Frontend structure and patterns
- **Data Architecture**: Database design and organization
- **API Architecture**: GraphQL Federation architecture
- **Infrastructure Architecture**: Containerization, deployment, observability

### Domain (02-domain)
- **Domain Model**: Domain entities & relationships
- **Glossary**: Complete terminology reference
- **Concepts**: Key concepts explained

### Features (03-features)
- **Feature Specifications**: Complete feature specs with Gherkin
- **Feature Design**: UI/UX design
- **API Contracts**: GraphQL contracts
- **Test Scenarios**: Test scenarios and cases

### Patterns (04-patterns)
- **Backend Patterns**: Entity, repository, service, resolver, testing
- **Frontend Patterns**: Component, page, hook, GraphQL, styling
- **API Patterns**: GraphQL Federation, error handling
- **Infrastructure Patterns**: Container, deployment, monitoring

### Standards (05-standards)
- **Coding Standards**: Code style, naming, conventions
- **Architecture Standards**: Architecture constraints & patterns
- **API Standards**: GraphQL, REST, API patterns
- **Database Standards**: Database, schema, migration rules
- **Testing Standards**: Testing requirements & patterns
- **Security Standards**: Security & auth patterns

### Workflows (06-workflows)
- **Feature Development**: Step-by-step feature creation process
- **Code Review**: Code review process
- **Testing**: Testing workflow
- **Deployment**: Deployment workflow

### Examples (07-examples)
- **Backend Examples**: CRUD, federation examples
- **Frontend Examples**: Page, component examples
- **Full-Stack Examples**: Complete feature examples

### Templates (08-templates)
- **Feature Templates**: Feature form, questionnaire, workflow
- **AI Prompts**: Templates for AI-assisted development
- **Code Templates**: Entity, resolver, component, test templates
- **Document Templates**: Feature request, ADR, technical design

### Architecture Decision Records (09-adr)
- **ADR-0001**: Monorepo architecture
- **ADR-0002**: Containerized architecture
- **ADR-0003**: Kotlin/Micronaut backend
- **ADR-0004**: TypeScript/Next.js frontend
- **ADR-0005**: PostgreSQL database

### Reference (10-reference)
- **Commands**: CLI commands reference
- **File Structure**: File structure reference
- **GraphQL Schema**: GraphQL schema reference
- **API Reference**: API reference

### Validation (11-validation)
- **Feature Checklist**: Feature completion checklist
- **Code Review Checklist**: Code review checklist
- **PR Checklist**: Pull request checklist
- **Validation Scripts**: Validation script documentation

## Feature Creation Flow

The specification enables a streamlined feature creation process:

1. **Fill Feature Form** → Simple human-readable form
2. **AI Processes Form** → Generates questionnaire, documentation, plan
3. **AI Uses Templates** → Generates code following patterns
4. **Developer Validates** → Uses checklists
5. **Review** → Uses code review checklist

See [Feature Development Workflow](./06-workflows/feature-development.md) for details.

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

## Contributing

### Adding New Documentation
1. Create files in the appropriate directory
2. Follow the established naming conventions
3. Include proper YAML frontmatter
4. Add cross-references to related documents
5. Update [MANIFEST.md](./MANIFEST.md)

### Updating Existing Documentation
1. Keep content current with code changes
2. Improve clarity and examples
3. Fix broken links and references
4. Update version information as needed

## Related Resources

- **Project Repository**: See root `README.md`
- **CLI Tool**: `./neotool` for common tasks
- **Contracts**: `contracts/` directory for GraphQL schemas
- **Infrastructure**: `infra/` directory for deployment configs

---

*This specification follows enterprise best practices for technical documentation and is designed to scale with the NeoTool platform. It is optimized for Spec-Driven Development with AI tools.*

