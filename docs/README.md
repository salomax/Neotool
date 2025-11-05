# NeoTool Documentation

Welcome to the NeoTool documentation hub. This directory contains all technical documentation, architecture decisions, and guides for the NeoTool platform.

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

### Design Documentation
- [`design/`](./design/) - Design system and assets documentation
- [`design/assets.md`](./design/assets.md) - Design assets organization and workflow

### Contracts Documentation
- [`contracts/`](./contracts/) - API contracts and schemas
- [`contracts/graphql-federation.md`](./contracts/graphql-federation.md) - GraphQL Federation setup and architecture

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
   - Start with [Architecture Overview](./adr/0001-monorepo-architecture.md)
   - Review [Project Setup Guide](./PROJECT_SETUP.md) for detailed setup instructions
   - Review [Backend Documentation](./service/)
   - Understand [GraphQL Federation Architecture](./service/graphql-federation-architecture.md)
   - Check [Frontend Documentation](./adr/0004-typescript-nextjs-frontend.md)
   - Understand [Frontend Structure](./web/web-src-structure.md)
   - Learn [Theme System](./web/web-themes.md) - Design tokens, theming, and customization
   - Learn [Shared Components System](./web/web-components.md)
   - Learn [i18n Architecture](./web/web-i18n-architecture.md)
   - Review [GraphQL Operations](./web/web-graphql-operations.md)

### Neotool CLI

The project includes a CLI tool for common tasks. See [Project Setup Guide](./PROJECT_SETUP.md#neotool-cli) for full documentation.

**Quick Reference:**
```bash
./neotool --version        # Check system requirements
./neotool rename-project   # Rename project
./neotool clean-examples   # Clean up example code
./neotool init             # Initialize project
./neotool help             # Show help
```

### For Designers
1. Review [Design Assets Guide](./design/assets.md)
2. Check [Design System Documentation](./design/)
3. Understand [Asset Workflow](./design/assets.md#workflow-guidelines)

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
