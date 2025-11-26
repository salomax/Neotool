---
title: Project Overview
type: overview
category: introduction
status: current
version: 1.0.0
tags: [overview, introduction, neotool, project]
ai_optimized: true
search_keywords: [overview, introduction, neotool, project, what-is]
related:
  - 00-overview/architecture-overview.md
  - 00-overview/technology-stack.md
  - 00-overview/quick-start.md
---

# NeoTool Project Overview

> **Purpose**: High-level introduction to NeoTool - what it is, what it does, and why it exists.

## What is NeoTool?

NeoTool is a **modular full-stack boilerplate** designed to **accelerate new app development** while keeping **clean architecture and best practices** baked in from day one.

Think of it as a **foundation framework** that helps you spin up new services or apps (backend, frontend, infra, and design system), all wired together and ready to evolve.

## Core Philosophy

NeoTool follows **Spec-Driven Development (SDD)** principles, where comprehensive specifications drive development rather than code driving documentation.

## What NeoTool Provides

NeoTool brings together several layers under one monorepo:

| Layer | Description | Tech Stack |
|-------|-------------|------------|
| **Frontend (`web/`)** | React + Next.js web app scaffold ready to consume GraphQL APIs | React, Next.js, TypeScript |
| **Mobile (`mobile/`)** | Expo + React Native setup for cross-platform mobile apps | React Native, Expo |
| **Backend (`service/`)** | Kotlin + Micronaut with GraphQL, modular architecture, reusable components, and testing setup | Kotlin, Micronaut, GraphQL, Gradle |
| **Contracts (`contracts/`)** | GraphQL Federation + OpenAPI specs for schema standardization | Apollo Federation, OpenAPI |
| **Design (`design/`)** | Brand assets, UI tokens, icons, and design guidelines | Figma, Adobe, Tokens |
| **Infra (`infra/`)** | Docker Compose, Kubernetes (Kustomize), GitOps (ArgoCD), and observability stack | Docker, K8s, Grafana, Prometheus, Loki |
| **Docs (`docs/`)** | Complete specification and documentation | Markdown, RAG-optimized |

## Key Features

### 1. Type-Safe End-to-End
- GraphQL schema as single source of truth
- TypeScript types generated from schema
- Kotlin type system for backend
- Compile-time safety from database to UI

### 2. Modern Technology Stack
- **Backend**: Kotlin + Micronaut (Java 21, Virtual Threads)
- **Frontend**: Next.js 14+ (App Router) + React 18+ + TypeScript
- **API**: GraphQL Federation (Apollo)
- **Database**: PostgreSQL 15+
- **Infrastructure**: Docker + Kubernetes

### 3. Production-Ready
- Containerized services
- Observability (Prometheus, Grafana, Loki)
- CI/CD ready
- Security best practices
- Scalable architecture

### 4. Developer Experience
- Hot reload for fast development
- Comprehensive CLI tools
- Complete specification
- Pattern examples
- Reusable templates

### 5. Spec-Driven Development
- Complete documentation
- Feature specifications
- Architecture patterns
- Coding standards
- Validation checklists

## Use Cases

NeoTool is ideal for:

- **Starting new projects** - Get a production-ready foundation
- **Learning best practices** - See how enterprise applications are structured
- **Rapid prototyping** - Build MVPs quickly with solid architecture
- **Team onboarding** - Consistent patterns and documentation
- **Enterprise applications** - Scalable, maintainable architecture

## Getting Started

1. **Read the Overview**: Start with [Architecture Overview](./architecture-overview.md)
2. **Review Technology Stack**: See [Technology Stack](./technology-stack.md)
3. **Understand Structure**: Check [Project Structure](./project-structure.md)
4. **Follow Quick Start**: Use [Quick Start Guide](./quick-start.md)

## Related Documentation

- [Architecture Overview](./architecture-overview.md) - System architecture
- [Technology Stack](./technology-stack.md) - Technology choices
- [Project Structure](./project-structure.md) - Monorepo organization
- [Core Principles](./principles.md) - Design principles
- [Quick Start](./quick-start.md) - Getting started guide

## Next Steps

- Explore the [Architecture](../01-architecture/) documentation
- Review [Architecture Decision Records](../09-adr/) for technology choices
- Check [Patterns](../04-patterns/) for implementation guidance
- See [Examples](../07-examples/) for working code

---

*NeoTool is designed to help you build better software faster, with clean architecture and best practices from day one.*

