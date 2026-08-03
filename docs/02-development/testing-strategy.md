---
title: Testing Strategy
type: development
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - package.json
  - web/package.json
  - web/playwright.config.ts
  - mobile/package.json
  - service/kotlin/settings.gradle.kts
load_when:
  - choosing unit vs e2e vs Gradle tests
  - running the correct check suite
supersedes: []
schema: docs-metadata-v1
---

# Testing Strategy

Purpose: map current test tooling by layer without feature-specific plans.

## When to load

Load when planning verification for a change.

## Authority

Root and package scripts plus Playwright config and Gradle modules.

## Rules

1. Backend: Gradle module tests (JUnit/Micronaut/Testcontainers as used by the module).
2. Web unit: Vitest via `web` `test` / `test:unit`.
3. Web e2e: Playwright via `web` `test:e2e` and specs under `web/tests/e2e`.
4. Mobile unit: Jest via `mobile` `test`.
5. Prefer targeted tests for the changed layer; use `npm run check:*` for broader gates.
6. Do not paste feature scenario matrices from removed feature docs into this doc.

## Commands

```bash
npm run check:api
npm run check:web
npm run check:mobile
cd web && npm run test:unit && npm run test:e2e
cd mobile && npm test
cd service/kotlin && ./gradlew :banking:test --console=plain
```

## Related

- [Repo commands](repo-commands.md)
- [Review and closeout](../03-agent-workflow/README.md)
- [Frontend web](frontend-web.md)
- [Backend Kotlin](backend-kotlin.md)
