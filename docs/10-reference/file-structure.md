---
title: File Structure Reference
type: reference
category: structure
status: current
version: 2.0.0
tags: [reference, file-structure, directories]
ai_optimized: true
search_keywords: [file-structure, directories, reference]
related:
  - 00-overview/project-structure.md
---

# File Structure Reference

> **Purpose**: Quick reference for file and directory structure.

## Backend Structure

```
service/kotlin/[module]/
├── src/main/kotlin/.../
│   ├── api/          # REST controllers
│   ├── domain/       # Domain models
│   ├── entity/       # JPA entities
│   ├── graphql/      # GraphQL resolvers
│   ├── repo/         # Repositories
│   └── service/      # Business logic
├── src/main/resources/
│   ├── graphql/schema.graphqls
│   └── db/migration/
└── src/test/kotlin/
```

## Frontend Structure

```
web/src/
├── app/              # Next.js App Router
├── shared/           # Shared components
├── lib/              # Libraries
└── styles/           # Global styles
```

## Related Documentation

- [Project Structure](../00-overview/project-structure.md)

