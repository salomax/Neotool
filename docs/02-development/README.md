---
title: Development Docs
type: index
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - package.json
  - web/package.json
  - mobile/package.json
  - service/kotlin/settings.gradle.kts
load_when:
  - building, testing, or extending backend/web/mobile
  - looking up repo commands or codegen
supersedes: []
schema: docs-metadata-v1
---

# Development Docs

Purpose: index for short, stack-specific development standards that match current tooling.

## Authority

- Root and package scripts, Gradle modules, and current test tooling.
- Commands in leaf docs must match real scripts/tasks.

## Rules

1. Split backend, web, and mobile guidance by actual stack boundaries.
2. Exclude aspirational process that is not enforced or observable.
3. Prefer pointers to skills and code over long pattern essays.

## Documents

| Doc | Purpose |
|---|---|
| [repo-commands.md](repo-commands.md) | Canonical commands |
| [backend-kotlin.md](backend-kotlin.md) | Kotlin/Micronaut standards |
| [frontend-web.md](frontend-web.md) | Next.js web standards |
| [mobile-expo.md](mobile-expo.md) | Expo mobile standards |
| [graphql-codegen.md](graphql-codegen.md) | GraphQL codegen workflow |
| [testing-strategy.md](testing-strategy.md) | Testing tools and expectations |

## Related

- [../README.md](../README.md)
- [../01-architecture/README.md](../01-architecture/README.md)
- [../03-agent-workflow/README.md](../03-agent-workflow/README.md)
- [../05-runbooks/README.md](../05-runbooks/README.md)
