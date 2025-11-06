---
title: Cursor AI Integration Guide
type: guide
category: documentation
status: current
version: 1.0.0
tags: [cursor, ai, integration, ide, rag]
related:
  - RAG_OPTIMIZATION.md
  - SPECIFICATION_MANIFEST.md
  - ARCHITECTURE_OVERVIEW.md
---

# Cursor AI Integration Guide

> **Purpose**: This guide explains how the NeoTool specification is integrated with Cursor AI for context-aware code assistance.

## Overview

The NeoTool specification has been integrated with Cursor AI to provide intelligent, context-aware code suggestions based on the project's architecture, patterns, and best practices.

## How It Works

### 1. `.cursorrules` File

A `.cursorrules` file has been created in the project root that:
- References the `spec/` directory as the knowledge base
- Provides instructions to Cursor on how to use the specification
- Lists key documents and their purposes
- Defines development guidelines based on the spec
- Specifies code generation rules for backend and frontend

### 2. Automatic Indexing

Cursor automatically indexes files in your workspace, including:
- All markdown files in the `spec/` directory
- Code files that follow the patterns documented in the spec
- Cross-references between documents

### 3. Context-Aware Suggestions

When you write prompts or ask questions, Cursor will:
- Search the specification for relevant patterns and guidelines
- Reference architecture decisions from ADRs
- Follow established code patterns
- Maintain consistency with the project structure

## Using the Integration

### Example: Creating a New Feature

**Prompt Example:**
```
Create a new feature for user profile management that includes:
- Backend API endpoint
- Frontend UI component
- Database schema changes
```

**What Cursor Will Do:**
1. Reference `spec/ARCHITECTURE_OVERVIEW.md` for system understanding
2. Check `spec/adr/0003-kotlin-micronaut-backend.md` for backend patterns
3. Review `spec/service/graphql-federation-architecture.md` for GraphQL patterns
4. Follow `spec/web/web-components.md` for frontend component patterns
5. Use `spec/service/database-schema-organization.md` for database patterns
6. Generate code following all established patterns and conventions

### Example: Understanding Architecture

**Prompt Example:**
```
How should I structure a new GraphQL resolver for product management?
```

**What Cursor Will Do:**
1. Reference `spec/service/graphql-federation-architecture.md`
2. Check `spec/adr/0003-kotlin-micronaut-backend.md` for backend structure
3. Provide examples following the documented patterns
4. Ensure federation compliance

### Example: Frontend Development

**Prompt Example:**
```
Create a new dashboard component that displays user statistics
```

**What Cursor Will Do:**
1. Reference `spec/web/web-components.md` for component patterns
2. Use `spec/web/web-themes.md` for styling with design tokens
3. Follow `spec/web/web-src-structure.md` for directory structure
4. Apply `spec/web/web-graphql-operations.md` for data fetching
5. Include i18n support following `spec/web/web-i18n-architecture.md`

## Best Practices for Prompts

### 1. Be Specific About the Layer

Mention whether you're working on:
- Backend (Kotlin/Micronaut)
- Frontend (TypeScript/Next.js)
- Database (PostgreSQL)
- Infrastructure (Docker/Kubernetes)

**Example:**
```
Create a backend service for handling payment processing
```

### 2. Reference Specific Patterns

If you know the pattern name, mention it:
- GraphQL Federation
- Clean Architecture
- Domain-Driven Design
- Component-Driven Development

**Example:**
```
Implement a new domain entity following Domain-Driven Design patterns
```

### 3. Ask for Architecture Guidance

When unsure about approach:
```
What's the recommended way to handle authentication in this project?
```

### 4. Request Pattern Examples

Ask for examples of existing patterns:
```
Show me an example of a GraphQL resolver following the project patterns
```

## Key Documents for Common Tasks

### Backend Development
- **Architecture**: `spec/ARCHITECTURE_OVERVIEW.md`
- **Backend ADR**: `spec/adr/0003-kotlin-micronaut-backend.md`
- **GraphQL**: `spec/service/graphql-federation-architecture.md`
- **Database**: `spec/service/database-schema-organization.md`
- **JPA Entities**: `spec/service/kotlin/jpa-entity.md`

### Frontend Development
- **Architecture**: `spec/ARCHITECTURE_OVERVIEW.md`
- **Frontend ADR**: `spec/adr/0004-typescript-nextjs-frontend.md`
- **Structure**: `spec/web/web-src-structure.md`
- **Components**: `spec/web/web-components.md`
- **Themes**: `spec/web/web-themes.md`
- **GraphQL**: `spec/web/web-graphql-operations.md`
- **i18n**: `spec/web/web-i18n-architecture.md`
- **Hooks**: `spec/web/web-custom-hooks.md`

### Infrastructure
- **Containerization**: `spec/adr/0002-containerized-architecture.md`
- **Monorepo**: `spec/adr/0001-monorepo-architecture.md`

### Quick Reference
- **Commands**: `spec/QUICK_REFERENCE.md`
- **Glossary**: `spec/GLOSSARY.md`
- **Manifest**: `spec/SPECIFICATION_MANIFEST.md`

## Troubleshooting

### Cursor Not Using Specification

If Cursor doesn't seem to be referencing the spec:

1. **Check `.cursorrules` file**: Ensure it exists in the project root
2. **Restart Cursor**: Sometimes a restart is needed to pick up new rules
3. **Be explicit**: Mention "according to the specification" or "following the spec" in your prompts
4. **Reference specific files**: Point to specific spec documents in your prompts

**Example:**
```
According to spec/web/web-components.md, create a new button component
```

### Finding Information

If you need to find specific information:

1. **Use the Manifest**: Check `spec/SPECIFICATION_MANIFEST.md` for document index
2. **Search by Category**: Documents are categorized (architecture, backend, frontend, etc.)
3. **Follow Cross-References**: Documents link to related topics
4. **Check Glossary**: Use `spec/GLOSSARY.md` for terminology

## Advanced Usage

### Custom Context Files

You can create custom context files in `.cursor/` directory (if needed) to provide additional context for specific features or modules.

### Prompt Templates

Create reusable prompt templates that reference the spec:

**Template Example:**
```
Create a new [FEATURE] following the NeoTool specification:
- Backend: Follow patterns in spec/adr/0003-kotlin-micronaut-backend.md
- Frontend: Use components from spec/web/web-components.md
- GraphQL: Follow spec/service/graphql-federation-architecture.md
- Database: Apply spec/service/database-schema-organization.md
```

## Maintenance

### Updating the Integration

When adding new specification documents:

1. Update `spec/SPECIFICATION_MANIFEST.md` with the new document
2. Add cross-references in related documents
3. Update `.cursorrules` if the new document is particularly important
4. Ensure YAML frontmatter is present for RAG indexing

### Keeping It Current

- Keep specification documents up-to-date with code changes
- Update `.cursorrules` when patterns change significantly
- Maintain cross-references between documents
- Review and update quarterly

## Related Documentation

- [RAG Optimization Guide](./RAG_OPTIMIZATION.md) - How the spec is optimized for RAG
- [Specification Manifest](./SPECIFICATION_MANIFEST.md) - Complete index of all documents
- [Architecture Overview](./ARCHITECTURE_OVERVIEW.md) - System architecture
- [Quick Reference](./QUICK_REFERENCE.md) - Common patterns and commands

---

*This integration enables Cursor AI to provide intelligent, context-aware assistance based on the NeoTool specification, ensuring consistency and adherence to established patterns and best practices.*

