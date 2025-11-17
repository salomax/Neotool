---
title: NeoTool Specification
type: overview
category: documentation
status: current
version: 1.0.0
tags: [documentation, specification, neotool, overview]
related:
  - ARCHITECTURE_OVERVIEW.md
  - SPECIFICATION_MANIFEST.md
  - GLOSSARY.md
  - PROJECT_SETUP.md
---

# NeoTool Specification

> **Purpose**: This directory contains the complete NeoTool specification - the source of truth for architecture, design decisions, and implementation guidelines. Optimized for RAG indexing and AI IDE integration.

Welcome to the NeoTool specification hub. This directory contains all technical documentation, architecture decisions, and guides for the NeoTool platform.

## 🎯 Quick Navigation

- **[Architecture Overview](./ARCHITECTURE_OVERVIEW.md)** - Start here for system understanding
- **[Specification Manifest](./SPECIFICATION_MANIFEST.md)** - Complete index of all documents
- **[Cursor Integration](./CURSOR_INTEGRATION.md)** - How to use this spec with Cursor AI
- **[Glossary](./GLOSSARY.md)** - Terminology and definitions
- **[Quick Reference](./QUICK_REFERENCE.md)** - Common commands and patterns
- **[Project Setup](./PROJECT_SETUP.md)** - Getting started guide

## 📋 Specification Index

For a complete index of all documents, see the [Specification Manifest](./SPECIFICATION_MANIFEST.md).

## 📚 Documentation Structure

### Architecture Decision Records (ADRs)
- [`adr/`](./adr/) - Architecture Decision Records documenting key technical decisions
- [`0001-monorepo-architecture.md`](./adr/0001-monorepo-architecture.md) - Monorepo structure and organization
- [`0002-containerized-architecture.md`](./adr/0002-containerized-architecture.md) - Containerization strategy
- [`0003-kotlin-micronaut-backend.md`](./adr/0003-kotlin-micronaut-backend.md) - Backend technology choices
- [`0004-typescript-nextjs-frontend.md`](./adr/0004-typescript-nextjs-frontend.md) - Frontend technology choices
- [`0005-postgresql-database.md`](./adr/0005-postgresql-database.md) - Database technology choices

### Service Documentation
- [`service/`](./service/) - Backend service documentation
- [`service/graphql-federation-architecture.md`](./service/graphql-federation-architecture.md) - GraphQL Federation architecture and Apollo GraphOS integration
- [`service/database-schema-organization.md`](./service/database-schema-organization.md) - Database schema organization rules and best practices
- [`service/kotlin/`](./service/kotlin/) - Kotlin-specific documentation

### Web Frontend Documentation
- [`web/`](./web/) - Frontend-specific documentation
- [`web/web-src-structure.md`](./web/web-src-structure.md) - Frontend directory structure and best practices
- [`web/web-themes.md`](./web/web-themes.md) - Theme system guide (design tokens, customization, usage)
- [`web/web-themes-quick-reference.md`](./web/web-themes-quick-reference.md) - Quick reference for theme values and patterns
- [`web/web-graphql-operations.md`](./web/web-graphql-operations.md) - GraphQL operations in frontend
- [`web/web-i18n-architecture.md`](./web/web-i18n-architecture.md) - Internationalization architecture and patterns
- [`web/web-components.md`](./web/web-components.md) - Shared components design system and usage guide

### Contracts Documentation
- [`contracts/`](./contracts/) - API contracts and schemas
- _Note: GraphQL Federation documentation is in [`service/graphql-federation-architecture.md`](./service/graphql-federation-architecture.md)_

### Documentation Site
- [`site/`](./site/) - Docusaurus documentation site
- [`site/docs/`](./site/docs/) - Public-facing documentation
- [`site/docusaurus.config.ts`](./site/docusaurus.config.ts) - Docusaurus configuration

## 🚀 Quick Start

### For Developers

**First Steps:**
1. **Check system requirements:**
   ```bash
   ./neotool --version
   ```
   Verifies Node.js, Docker, and JVM installations.

2. **Set up your project:**
   ```bash
   # Edit project.config.json with your project details
   ./neotool init
   ```
   This will rename the project and optionally clean up example code.

3. **Continue with documentation:**
   - Start with [Architecture Overview](./ARCHITECTURE_OVERVIEW.md) for system understanding
   - **Using Cursor AI?** Check [Cursor Integration Guide](./CURSOR_INTEGRATION.md) to leverage the spec for AI-assisted development
   - Review [Project Setup Guide](./PROJECT_SETUP.md) for detailed setup instructions
   - Review [Architecture Decision Records](./adr/) for technical decisions
   - Review [Backend Documentation](./service/) for service layer details
   - Understand [GraphQL Federation Architecture](./service/graphql-federation-architecture.md)
   - Check [Frontend Documentation](./web/) for frontend patterns
   - Understand [Frontend Structure](./web/web-src-structure.md)
   - Learn [Theme System](./web/web-themes.md) - Design tokens, theming, and customization
   - Learn [Shared Components System](./web/web-components.md)
   - Learn [i18n Architecture](./web/web-i18n-architecture.md)
   - Review [GraphQL Operations](./web/web-graphql-operations.md)

### Neotool CLI

The project includes a CLI tool for common tasks. See [Project Setup Guide](./PROJECT_SETUP.md#neotool-cli) for full documentation.

**Quick Reference:**
```bash
# Project Setup
./neotool --version        # Check system requirements
./neotool setup            # Setup project (rename from neotool)
./neotool clean [--dry-run] # Clean up example code
./neotool init             # Initialize project

# GraphQL Schema Management
./neotool graphql sync      # Interactive schema sync
./neotool graphql validate  # Validate schema consistency
./neotool graphql generate  # Generate supergraph schema
./neotool graphql all       # Run complete workflow

# Help
./neotool help             # Show help
./neotool graphql --help   # Show GraphQL command help
```

### For DevOps
1. Review [Containerization Strategy](./adr/0002-containerized-architecture.md)
2. Check [Infrastructure Documentation](./infra/)
3. Understand [Deployment Process](./adr/0002-containerized-architecture.md)

## 📖 Documentation Standards

### Writing Guidelines
- Use clear, concise language
- Include code examples where helpful
- Follow markdown best practices
- Include diagrams for complex concepts
- Keep documentation up-to-date with code changes

### File Organization
- Group related documentation in appropriate directories
- Use descriptive filenames
- Include README files in each major directory
- Cross-reference related documentation

### Review Process
- All documentation changes require review
- Keep documentation in sync with code changes
- Update documentation when making architectural changes
- Include documentation updates in pull requests

## 🔄 Contributing to Documentation

### Adding New Documentation
1. Create files in the appropriate directory
2. Follow the established naming conventions
3. Include proper cross-references
4. Update this README if adding new sections

### Updating Existing Documentation
1. Keep content current with code changes
2. Improve clarity and examples
3. Fix broken links and references
4. Update version information as needed

## 📞 Getting Help

- **Technical Questions**: Check relevant ADRs and service documentation
- **Design Questions**: Review design documentation and guidelines
- **Process Questions**: Contact the development team
- **Documentation Issues**: Create an issue or pull request

---

*This documentation follows enterprise best practices for technical documentation and is designed to scale with the NeoTool platform.*
