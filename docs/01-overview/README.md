---
title: NeoTool - Start Here
type: overview
category: introduction
status: current
version: 3.0.0
tags: [overview, introduction, neotool, start-here, sdd]
ai_optimized: true
search_keywords: [neotool, overview, what-is, introduction, start-here, specification-driven]
related:
  - 01-overview/getting-started.md
  - 01-overview/specification-driven-development.md
  - 01-overview/architecture-at-a-glance.md
  - 12-specification-driven-dev/README.md
last_updated: 2026-01-02
---

# NeoTool: Start Here

> **Your specification-driven full-stack foundation for building production-ready applications faster.**

## What is NeoTool?

NeoTool is a **production-ready monorepo boilerplate** that inverts the traditional development model: **specification drives implementation**, not the other way around.

Instead of documenting what you built, you **specify what you want to build**, and the comprehensive patterns, templates, and AI-optimized workflows guide you to consistent, high-quality implementations.

## The 30-Second Pitch

**Traditional Approach:**
```
Code → Tests → Documentation (maybe) → Hope it scales
```

**NeoTool Approach:**
```
Specification → Patterns → AI-Assisted Implementation → Production-Ready Code
```

**Result:**
- 🚀 **3x faster** feature development
- 📐 **Consistent** architecture patterns
- 🤖 **AI-optimized** documentation for Claude, Cursor, ChatGPT
- ✅ **Production-ready** from day one
- 🎯 **Type-safe** end-to-end (DB → API → UI)

## What You Get

### Complete Technology Stack
- **Backend**: Kotlin + Micronaut + GraphQL Federation
- **Frontend**: Next.js 14+ (App Router) + React 18+ + TypeScript
- **Database**: PostgreSQL 18+ with type-safe access
- **Infrastructure**: Docker + Kubernetes + GitOps (ArgoCD)
- **Observability**: Prometheus + Grafana + Loki

### Comprehensive Specification
- **100+ documents** covering every pattern and standard
- **AI-optimized** for RAG (Retrieval-Augmented Generation)
- **Living documentation** that evolves with your code
- **Feature templates** for rapid development
- **Validation checklists** for quality assurance

### Production-Ready Features
- JWT authentication with refresh token rotation
- GraphQL Federation architecture
- Type-safe end-to-end development
- Containerized services
- Complete observability stack
- CI/CD pipeline templates

## Who is NeoTool For?

### ✅ Perfect For
- **Startups** needing to move fast with solid foundations
- **Enterprise teams** requiring consistent patterns and governance
- **AI-assisted development** with Claude, Cursor, or ChatGPT
- **Solo developers** wanting enterprise-grade architecture
- **Teams scaling up** from prototype to production

### ⚠️ Maybe Not For
- Simple CRUD apps (might be overkill)
- Heavily customized architectures (opinionated by design)
- Teams not ready to adopt GraphQL

## Key Differentiators

| Feature | Traditional Boilerplate | NeoTool |
|---------|------------------------|---------|
| **Documentation** | Minimal, often outdated | 100+ docs, RAG-optimized |
| **Philosophy** | Code-first | Specification-first (SDD) |
| **AI Integration** | Not optimized | Built for Claude/Cursor |
| **Patterns** | Few examples | Comprehensive patterns library |
| **Type Safety** | Partial | End-to-end (DB → UI) |
| **Production Ready** | Basic setup | Full observability + GitOps |
| **Governance** | Ad-hoc | ADRs + validation checklists |

## How NeoTool Works: The SDD Cycle

```
1. Define Feature (Gherkin + README)
   ↓
2. AI Reads Specification
   ↓
3. AI Generates Implementation Plan
   ↓
4. Follow Patterns (Backend/Frontend/Contracts)
   ↓
5. Validate (Checklists + Tests)
   ↓
6. Deploy (GitOps)
   ↓
7. Update Spec (Living Documentation)
```

**See**: [Specification-Driven Development Guide](./specification-driven-development.md) for the complete workflow.

## Quick Navigation

### 📖 I Want to Understand NeoTool
- [Getting Started Guide](./getting-started.md) - Your first 15 minutes
- [Architecture at a Glance](./architecture-at-a-glance.md) - System design in 5 minutes
- [Core Principles](./core-principles.md) - Design philosophy
- [Specification-Driven Development](./specification-driven-development.md) - The SDD approach

### 🏗️ I Want to Build Features
- [Feature Development Workflow](../08-workflows/feature-development.md)
- [Backend Patterns](../05-backend/patterns/)
- [Frontend Patterns](../07-frontend/patterns/)
- [Code Templates](../91-templates/code-templates/)
- [Examples](../90-examples/)

### 🤖 I'm an AI Assistant
- [Specification Manifest](../manifest.md) - Complete document index
- [SDD Workflow Guide](./specification-driven-development.md) - How to use this spec
- [Context Strategy](../08-workflows/spec-context-strategy.md) - Build effective context
- [Patterns Index](../05-backend/patterns/) - Implementation patterns

### 🎯 I Need Quick Reference
- [Commands Reference](../93-reference/commands.md)
- [GraphQL Schema](../93-reference/graphql-schema.md)
- [Backend Quick Reference](../93-reference/backend-quick-reference.md)
- [Validation Checklists](../94-validation/)

## Technology Decisions

All major technology choices are documented with rationale:
- [ADR-0001: Monorepo Architecture](../92-adr/0001-monorepo-architecture.md)
- [ADR-0002: Containerized Architecture](../92-adr/0002-containerized-architecture.md)
- [ADR-0003: Kotlin/Micronaut Backend](../92-adr/0003-kotlin-micronaut-backend.md)
- [ADR-0004: TypeScript/Next.js Frontend](../92-adr/0004-typescript-nextjs-frontend.md)
- [ADR-0005: PostgreSQL Database](../92-adr/0005-postgresql-database.md)

## Documentation Structure

```
docs/
├── 01-overview/               ← You are here
├── 02-architecture/           → Deep-dive system design
├── 03-features/               → Feature specifications
├── 04-domain/                 → Domain modeling
├── 05-backend/                → Backend patterns
├── 06-contracts/              → API contracts
├── 07-frontend/               → Frontend patterns
├── 08-workflows/              → Development workflows
├── 09-security/               → Security practices
├── 10-observability/          → Monitoring
├── 11-infrastructure/         → Deployment
├── 12-specification-driven-dev/ → SDD meta-docs
├── 90-examples/               → Code examples
├── 91-templates/              → Boilerplate
├── 92-adr/                    → Architecture decisions
├── 93-reference/              → Quick lookup
└── 94-validation/             → Checklists
```

**See**: [Documentation README](../README.md) for complete navigation.

## Next Steps

### For Developers
1. **Start**: [Getting Started Guide](./getting-started.md)
2. **Understand**: [Architecture at a Glance](./architecture-at-a-glance.md)
3. **Build**: [Feature Development Workflow](../08-workflows/feature-development.md)

### For Architects
1. **Design**: [Core Principles](./core-principles.md)
2. **Architecture**: [System Architecture](../02-architecture/system-architecture.md)
3. **Decisions**: [Architecture Decision Records](../92-adr/)

### For AI Assistants
1. **Workflow**: [Specification-Driven Development](./specification-driven-development.md)
2. **Index**: [Specification Manifest](../manifest.md)
3. **Context**: [Context Strategy](../08-workflows/spec-context-strategy.md)

## Community & Support

- **Repository**: [github.com/salomax/neotool](https://github.com/salomax/neotool)
- **Issues**: Report bugs or request features
- **Discussions**: Architecture questions and best practices
- **Contributing**: See [CONTRIBUTING.md](../../CONTRIBUTING.md)

---

**Version**: 3.0.0 (2026-01-02)
**Philosophy**: Specification drives implementation. Code follows patterns. Quality is built-in.
**Optimized for**: Human developers + AI assistants working together

*Build better software faster with NeoTool.*
