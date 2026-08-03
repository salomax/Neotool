---
title: Repo Commands
type: development
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - package.json
  - nx.json
  - scripts/cli/gradlew-dev.sh
  - RTK.md
load_when:
  - running checks locally
  - finding the canonical npm/nx/gradle entrypoints
supersedes:
  - docs/93-reference/
schema: docs-metadata-v1
---

# Repo Commands

Purpose: list the verified top-level commands contributors and agents should prefer.

## When to load

Load when starting local validation or choosing between npm, nx, and Gradle wrappers.

## Authority

Root `package.json` scripts, `nx.json`, `scripts/cli/gradlew-dev.sh`, and RTK guidance in `RTK.md`.

## Rules

1. Prefer root `npm run check:*` for cross-project validation.
2. Prefer `rtk` prefixes for noisy shell commands when RTK is on PATH.
3. Run Kotlin module tasks from `service/kotlin` via `./gradlew` or `scripts/cli/gradlew-dev.sh`.
4. Do not invent scripts that are not in package manifests.

## Commands

```bash
npm run check:api
npm run check:workers
npm run check:web
npm run check:mobile
npm run check:all
rtk ./gradlew :banking:testUnit --console=plain   # from service/kotlin, with rtk
scripts/cli/gradlew-dev.sh :banking:run
```

## Related

- [Backend Kotlin](backend-kotlin.md)
- [Frontend web](frontend-web.md)
- [Mobile Expo](mobile-expo.md)
- [Testing](testing-strategy.md)
- [Local runbook](../05-runbooks/README.md)
