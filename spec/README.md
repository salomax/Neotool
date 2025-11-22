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
  - 00-core/architecture.md
---

# NeoTool Specification

> **Purpose**: Complete specification for the NeoTool platform - the source of truth for architecture, design decisions, and implementation guidelines. Optimized for Spec-Driven Development with AI tools.

Welcome to the NeoTool specification hub. This directory contains all technical documentation, architecture decisions, patterns, templates, and guides for the NeoTool platform.

## Quick Navigation

- **[Specification Manifest](./MANIFEST.md)** - Complete index of all documents
- **[Architecture Overview](./00-core/architecture.md)** - Start here for system understanding
- **[Technology Stack](./00-core/technology-stack.md)** - Technology choices and versions
- **[Project Structure](./00-core/project-structure.md)** - Monorepo organization
- **[Core Principles](./00-core/principles.md)** - Design philosophy

## Specification Structure

The specification is organized into numbered directories for clear hierarchy:

```
spec/
├── 00-core/          # Core specification (architecture, tech stack, principles)
├── 01-rules/         # Explicit rules (coding, architecture, API, database, testing, security)
├── 02-definitions/   # Definitions & terminology (glossary, domain model, concepts)
├── 03-patterns/      # Implementation patterns (backend, frontend, shared)
├── 04-templates/     # Reusable templates (feature creation, AI prompts, code, documents)
├── 05-examples/      # Concrete examples (backend, frontend, full-stack)
├── 06-workflows/     # Development workflows (feature dev, code review, testing, deployment)
├── 07-validation/    # Validation & checklists (feature, code review, PR)
├── 08-adr/           # Architecture Decision Records
└── 09-reference/     # Quick reference guides (commands, file structure, GraphQL, API)
```

## Quick Start

### For Developers

**First Steps:**
1. **Read the architecture**: Start with [Architecture Overview](./00-core/architecture.md)
2. **Understand the tech stack**: Review [Technology Stack](./00-core/technology-stack.md)
3. **Learn the patterns**: Explore [Patterns](./03-patterns/README.md)
4. **See examples**: Check [Examples](./05-examples/README.md)

**Creating a Feature:**
1. **Fill the feature form**: Use [Feature Form](./04-templates/feature-creation/feature-form.md)
2. **Follow the workflow**: See [Feature Development Workflow](./06-workflows/feature-development.md)
3. **Use templates**: Reference [Templates](./04-templates/README.md)
4. **Validate**: Complete [Feature Checklist](./07-validation/feature-checklist.md)

### For AI Assistants

**When Creating Features:**
1. **Read the feature form**: Process [Feature Form](./04-templates/feature-creation/feature-form.md)
2. **Reference rules**: Check [Rules](./01-rules/README.md) for constraints
3. **Follow patterns**: Use [Patterns](./03-patterns/README.md) for implementation
4. **Use templates**: Apply [Templates](./04-templates/README.md) for structure
5. **Reference examples**: See [Examples](./05-examples/README.md) for guidance

**When Answering Questions:**
1. **Check definitions**: Reference [Glossary](./02-definitions/glossary.md)
2. **Find patterns**: Search [Patterns](./03-patterns/README.md)
3. **Review rules**: Check [Rules](./01-rules/README.md)
4. **See examples**: Look in [Examples](./05-examples/README.md)

## Key Sections

### Core Specification (00-core)
- **Architecture**: System architecture overview
- **Technology Stack**: Technology choices and versions
- **Project Structure**: Monorepo organization
- **Principles**: Core design principles

### Rules (01-rules)
- **Coding Standards**: Code style, naming, conventions
- **Architecture Rules**: Architecture constraints & patterns
- **API Rules**: GraphQL, REST, API patterns
- **Database Rules**: Database, schema, migration rules
- **Testing Rules**: Testing requirements & patterns
- **Security Rules**: Security & auth patterns

### Definitions (02-definitions)
- **Glossary**: Complete terminology reference
- **Domain Model**: Domain entities & relationships
- **Concepts**: Key concepts explained

### Patterns (03-patterns)
- **Backend Patterns**: Entity, repository, service, resolver, testing
- **Frontend Patterns**: Component, page, hook, GraphQL, styling
- **Shared Patterns**: GraphQL Federation, error handling

### Templates (04-templates)
- **Feature Creation**: Feature form, questionnaire, workflow
- **AI Prompts**: Templates for AI-assisted development
- **Code Templates**: Entity, resolver, component, test templates
- **Document Templates**: Feature request, ADR, technical design

### Examples (05-examples)
- **Backend Examples**: CRUD, federation examples
- **Frontend Examples**: Page, component examples
- **Full-Stack Examples**: Complete feature examples

### Workflows (06-workflows)
- **Feature Development**: Step-by-step feature creation process
- **Code Review**: Code review process
- **Testing**: Testing workflow
- **Deployment**: Deployment workflow

### Validation (07-validation)
- **Feature Checklist**: Feature completion checklist
- **Code Review Checklist**: Code review checklist
- **PR Checklist**: Pull request checklist
- **Validation Scripts**: Validation script documentation

### Architecture Decision Records (08-adr)
- **ADR-0001**: Monorepo architecture
- **ADR-0002**: Containerized architecture
- **ADR-0003**: Kotlin/Micronaut backend
- **ADR-0004**: TypeScript/Next.js frontend
- **ADR-0005**: PostgreSQL database

### Reference (09-reference)
- **Commands**: CLI commands reference
- **File Structure**: File structure reference
- **GraphQL Schema**: GraphQL schema reference
- **API Reference**: API reference

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
type: [core|rule|pattern|template|example|workflow|validation|adr|reference]
category: [category-name]
status: [current|deprecated|draft]
version: 1.0.0
tags: [tag1, tag2, tag3]
related:
  - path/to/related-doc.md
ai_optimized: true
search_keywords: [keyword1, keyword2]
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

