---
title: NeoTool Glossary
type: reference
category: terminology
status: current
version: 1.0.0
tags: [glossary, terminology, definitions, reference]
---

# NeoTool Glossary

> **Purpose**: Comprehensive glossary of terms, concepts, and technologies used in the NeoTool specification. Optimized for RAG indexing and AI assistant understanding.

## A

### ADR (Architecture Decision Record)
A document that captures an important architectural decision made along with its context and consequences. See [ADR Directory](./adr/).

### Apollo Federation
A GraphQL architecture pattern that allows multiple GraphQL services to compose a single unified schema. See [GraphQL Federation Architecture](./service/graphql-federation-architecture.md).

### Apollo Router
The GraphQL gateway/router that composes federated schemas and routes queries to appropriate services. Part of Apollo GraphOS.

### App Router
Next.js 13+ routing system that uses React Server Components and file-based routing. See [Frontend ADR](./adr/0004-typescript-nextjs-frontend.md).

## B

### Backend for Frontend (BFF)
A pattern where a separate backend service is created for each frontend application. NeoTool uses GraphQL Federation instead to avoid duplication.

## C

### Client Component
In Next.js App Router, a React component that runs on the client side and can use browser APIs and interactivity. Marked with `'use client'` directive.

### Code Generation
The process of automatically generating TypeScript types from GraphQL schemas, ensuring type safety between backend and frontend.

## D

### Domain-Driven Design (DDD)
An approach to software development that centers the development on programming a domain model that has a rich understanding of the business domain.

### Docker Compose
Tool for defining and running multi-container Docker applications. Used for local development in NeoTool.

## E

### Entity
In JPA/Micronaut Data, a class that represents a database table. See [JPA Entity Guide](./service/kotlin/jpa-entity.md).

## F

### Federation
See Apollo Federation.

### Flyway
Database migration tool used in NeoTool to version control database schema changes.

## G

### GitOps
A methodology for managing infrastructure and application deployments using Git as the single source of truth. NeoTool uses ArgoCD for GitOps.

### GraphQL
A query language and runtime for APIs that allows clients to request exactly the data they need. NeoTool uses GraphQL with Apollo Federation.

### GraphQL Resolver
A function that resolves a GraphQL field by fetching data from a data source (database, API, etc.).

### GraphQL Schema
The type system that defines the structure of data available through a GraphQL API.

## H

### HikariCP
High-performance JDBC connection pooling library used in NeoTool for database connections.

## I

### i18n (Internationalization)
The process of designing and developing software so it can be adapted to different languages and regions. See [i18n Architecture](./web/web-i18n-architecture.md).

## J

### JPA (Java Persistence API)
A Java specification for accessing, persisting, and managing data between Java objects and relational databases. Used via Micronaut Data in NeoTool.

### JSONB
PostgreSQL's binary JSON data type that stores JSON data in a decomposed binary format, allowing efficient querying and indexing.

## K

### Kubernetes (K8s)
Container orchestration platform used for production deployments in NeoTool.

### Kotlin
Modern programming language used for backend services in NeoTool. See [Backend ADR](./adr/0003-kotlin-micronaut-backend.md).

## M

### Micronaut
Modern, JVM-based, full-stack framework for building microservice applications. Used as the backend framework in NeoTool.

### Micronaut Data
A database access toolkit that provides a simple API for database operations without the overhead of traditional ORMs.

### Monorepo
A single repository containing multiple projects or packages. NeoTool uses a monorepo structure. See [Monorepo ADR](./adr/0001-monorepo-architecture.md).

## N

### Next.js
React framework for production with features like server-side rendering, static site generation, and API routes. Used for web frontend in NeoTool.

## O

### Observability
The ability to understand the internal state of a system by examining its outputs. NeoTool uses Prometheus, Grafana, and Loki for observability.

## P

### PostgreSQL
Open-source relational database management system used as the primary database in NeoTool. See [Database ADR](./adr/0005-postgresql-database.md).

### Prometheus
Open-source monitoring and alerting toolkit used in NeoTool for metrics collection.

## R

### React Server Components
React components that render on the server, reducing JavaScript bundle size and improving performance. Used in Next.js App Router.

### Repository Pattern
A design pattern that abstracts data access logic and provides a more object-oriented view of the persistence layer.

## S

### Server Component
In Next.js App Router, a React component that renders on the server by default, reducing client-side JavaScript.

### Supergraph
In Apollo Federation, the composed unified schema created from multiple federated subgraph schemas.

## T

### Testcontainers
Java library that provides lightweight, throwaway instances of common databases, Selenium web browsers, or anything else that can run in a Docker container. Used for integration testing.

### Type Safety
The extent to which a programming language prevents type errors. NeoTool emphasizes end-to-end type safety from database to UI.

## U

### Use Case
A specific way of using the system, often represented as a function or service method that implements business logic.

## Related Documentation

- [Architecture Overview](./ARCHITECTURE_OVERVIEW.md)
- [Specification Manifest](./SPECIFICATION_MANIFEST.md)
- [All ADRs](./adr/)

