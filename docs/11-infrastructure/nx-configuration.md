---
title: Nx Configuration and Usage Guide
category: infrastructure
status: active
version: 2.0.0
tags: [nx, monorepo, build-system, ci-cd, developer-guide]
related:
  - ../92-adr/0003-nx-monorepo-tooling.md
---

# Nx Developer Guide

Nx is our monorepo build system. It orchestrates builds, tests, and releases across
TypeScript (web), Kotlin (API services), and Go (workers) — all from one repository.

**See also**: [ADR-0003](../92-adr/0003-nx-monorepo-tooling.md) for the architectural rationale.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Repository Layout](#repository-layout)
3. [How Nx Discovers Projects](#how-nx-discovers-projects)
4. [Common Commands](#common-commands)
5. [Project Tags](#project-tags)
6. [Releasing & Versioning](#releasing--versioning)
7. [CI/CD Pipelines](#cicd-pipelines)
8. [Adding a New Project](#adding-a-new-project)
9. [Configuration Reference](#configuration-reference)
10. [Troubleshooting](#troubleshooting)

---

## Quick Start

```bash
# Install dependencies (includes Nx)
npm ci

# List all projects
npx nx show projects

# Visualize the dependency graph
npx nx graph

# Build a specific service
npx nx build security

# Run tests for a Go worker
npx nx test bacen-ifdata-cronjob

# See what's affected by your changes
npx nx show projects --affected
```

### Prerequisites

- Node.js 20+
- Java 21 (for Kotlin services — required even to resolve the project graph)
- Go 1.24+ (for Go workers)

---

## Repository Layout

```
.
├── service/kotlin/                  # Gradle multi-module project
│   ├── build.gradle.kts             # Shared config (allprojects/subprojects)
│   ├── settings.gradle.kts          # Includes all Kotlin modules
│   ├── common/                      # Shared lib
│   ├── common-batch/                # Shared lib
│   ├── common-features/             # Shared lib
│   ├── security/                    # API service  (Nx: security)
│   ├── assets/                      # API service  (Nx: assets)
│   ├── financialdata/               # API service  (Nx: financialdata)
│   └── assistant/                   # API service  (Nx: assistant)
├── workers/golang/                  # Go workers (each has its own go.mod)
│   ├── shared/                      # Shared Go packages
│   └── financial_data/
│       ├── bacen_ifdata_job/        # CronJob      (Nx: bacen-ifdata-cronjob)
│       ├── indicators_job/          # Job           (Nx: indicators-job)
│       ├── ratings_job/             # Job           (Nx: ratings-job)
│       └── institution_enhancement_consumer/  # Consumer (Nx: institution-enhancement-consumer)
├── web/                             # Next.js app   (Nx: neotool-web)
├── mobile/                          # React Native  (Nx: neotool-mobile)
├── nx.json                          # Workspace config
└── package.json                     # Root — Nx + plugins
```

Key design decisions:

- **`service/kotlin/`** stays as a Gradle multi-module project (shared `build.gradle.kts`)
- **`workers/golang/`** keeps Go workers separate from API services (different runtime concerns)
- **Nx wraps the existing tools** — it doesn't replace Gradle or `go build`

---

## How Nx Discovers Projects

Nx discovers projects from three sources:

### 1. `@nx/gradle` plugin — Kotlin projects

The plugin reads `service/kotlin/settings.gradle.kts` and auto-discovers all Gradle
modules. Projects that have a `package.json` get their name from the `"name"` field.
Projects without one get a `:` prefix from Gradle conventions.

| Module           |    Has `package.json`?     | Nx project name    |
| ---------------- | :------------------------: | ------------------ |
| security         | Yes (`"name": "security"`) | `security`         |
| assets           |            Yes             | `assets`           |
| financialdata    |            Yes             | `financialdata`    |
| assistant        |            Yes             | `assistant`        |
| common           |             No             | `:common`          |
| common-batch     |             No             | `:common-batch`    |
| common-features  |             No             | `:common-features` |
| _(root project)_ |             —              | `neotool-service`  |

### 2. `@nx/next/plugin` — Web project

Auto-discovers the Next.js app in `web/`. Project name comes from `web/package.json`.

### 3. `project.json` — Go workers + mobile

Any directory with a `project.json` file becomes an Nx project. Go workers and the
mobile app use this approach since there's no auto-discovery plugin for them.

The `package.json` in each Go worker directory provides the Nx project name
(e.g., `"name": "bacen-ifdata-cronjob"`). The `project.json` provides tags and
target definitions:

```json
{
  "tags": ["type:cronjob", "lang:go"],
  "targets": {
    "build": {
      "executor": "nx:run-commands",
      "options": {
        "command": "go build ./...",
        "cwd": "{projectRoot}"
      }
    },
    "test": {
      "executor": "nx:run-commands",
      "options": {
        "command": "go test ./...",
        "cwd": "{projectRoot}"
      }
    },
    "lint": {
      "executor": "nx:run-commands",
      "options": {
        "command": "go vet ./...",
        "cwd": "{projectRoot}"
      }
    }
  }
}
```

---

## Common Commands

### Querying projects

```bash
# List all projects
npx nx show projects

# List projects as JSON
npx nx show projects --json

# Show details for a specific project
npx nx show project security
npx nx show project bacen-ifdata-cronjob

# Show affected projects (compared to main)
npx nx show projects --affected --base=origin/main

# Show affected projects (compared to a specific branch)
npx nx show projects --affected --base=origin/feature-branch
```

### Running targets

```bash
# Pattern: npx nx <target> <project>

# Kotlin services (delegates to Gradle under the hood)
npx nx build security
npx nx test assets
npx nx lint financialdata

# Go workers
npx nx build bacen-ifdata-cronjob
npx nx test ratings-job
npx nx lint indicators-job

# Run a target on multiple projects
npx nx run-many --target=test --projects=security,assets,financialdata

# Run a target on ALL projects
npx nx run-many --target=test --all
```

### Running multiple targets at once

You can pass multiple targets in a single command — Nx runs them in the correct
order (respecting `dependsOn`) and parallelizes independent work:

```bash
# Build + test + lint for a specific project
npx nx run-many --target=build,test,lint --projects=security

# Multiple targets on multiple projects
npx nx run-many --target=build,test,lint --projects=security,assets,financialdata

# Multiple targets on ALL projects
npx nx run-many --target=build,test,lint --all

# Multiple targets on affected projects only
npx nx affected --target=build,test,lint
```

**Web-specific targets** — the web project has more granular targets than other projects:

```bash
# Full CI check for web (typecheck + lint + unit tests)
npx nx run-many --target=typecheck,lint,test:unit --projects=neotool-web

# All available web targets can be listed with:
npx nx show project neotool-web --json | jq '.targets | keys'
```

### Shortcut scripts

Convenience scripts in the root `package.json` for full CI-like checks per stack:

```bash
# Kotlin APIs — runs check (= test + ktlintCheck + koverVerify)
npm run check:api

# Go workers — runs build + test + lint
npm run check:workers

# Web — runs typecheck + lint + unit tests
npm run check:web

# Everything
npm run check:all
```

> **Note**: Nx's `--tag` filter is unreliable with `@nx/gradle` (flags leak to Gradle
> as CLI args). These scripts use explicit project lists instead.

### Affected commands

These run targets only on projects affected by your changes:

```bash
# Test only affected projects
npx nx affected --target=test

# Build only affected
npx nx affected --target=build

# Build + test + lint on affected projects
npx nx affected --target=build,test,lint

# Compare against a specific base
npx nx affected --target=test --base=origin/main --head=HEAD

# Compare against a specific commit
npx nx affected --target=test --base=HEAD~3
```

### Dependency graph

```bash
# Open interactive graph in browser
npx nx graph

# Focus on a specific project
npx nx graph --focus=security

# Show only affected projects in graph
npx nx affected:graph
```

### Cache management

```bash
# Clear local cache and reset Nx daemon
npx nx reset

# Run without cache (force fresh execution)
npx nx test security --skip-nx-cache
```

> **Note**: `npx nx reset` is the go-to fix when Nx behaves unexpectedly — it clears
> the cache and restarts the daemon.

---

## Project Tags

Every project has tags in its `project.json` for classification:

| Tag pattern | Values                                     | Purpose         |
| ----------- | ------------------------------------------ | --------------- |
| `type:*`    | `api`, `lib`, `cronjob`, `job`, `consumer` | Runtime type    |
| `lang:*`    | `kotlin`, `go`, `typescript`               | Language        |
| `scope:*`   | `security`, `assets`, `financialdata`      | Business domain |

Tags are used by CI workflows to classify affected projects into the correct test
matrix (Kotlin vs Go vs web).

> **Known limitation**: `npx nx show projects --tag=lang:kotlin` does not reliably
> filter when combined with `--affected`. CI uses project-name-based classification
> instead.

---

## Releasing & Versioning

Each deployable service has its own independent version, managed by `nx release` with
conventional commits.

### How it works

```
Developer commits → merge to main → release.yml runs nx release
→ analyzes conventional commits → bumps versions → creates per-service git tags
→ per-service tag triggers build-production.yml → builds Docker image → Flux deploys
```

### Conventional commits determine the version bump

| Commit prefix                                  | Version bump | Example                                                |
| ---------------------------------------------- | :----------: | ------------------------------------------------------ |
| `fix:`                                         |  **patch**   | `fix(security): validate token expiry` — 1.0.0 → 1.0.1 |
| `feat:`                                        |  **minor**   | `feat(security): add 2FA support` — 1.0.0 → 1.1.0      |
| `feat!:` or `BREAKING CHANGE:` footer          |  **major**   | `feat!: redesign auth API` — 1.0.0 → 2.0.0             |
| `chore:`, `docs:`, `ci:`, `refactor:`, `test:` | **no bump**  | project is skipped                                     |

The scope (e.g., `security`) in the commit message is optional. What determines which
project gets a version bump is **which files the commit touched**, not the scope.

If there are multiple qualifying commits since the last tag, the **highest** bump wins
(e.g., 3 fixes + 1 feat = minor).

### Tag format

Tags follow the pattern `{projectName}-v{version}`:

```
security-v1.3.0
bacen-ifdata-cronjob-v2.0.1
neotool-web-v1.5.0
```

### Releasable projects

These 9 projects participate in independent versioning:

| Project                            | Tag example                               | Docker image                                         |
| ---------------------------------- | ----------------------------------------- | ---------------------------------------------------- |
| `security`                         | `security-v1.0.0`                         | `neotool-security`                                   |
| `assets`                           | `assets-v1.0.0`                           | `neotool-assets`                                     |
| `financialdata`                    | `financialdata-v1.0.0`                    | `neotool-financialdata`                              |
| `assistant`                        | `assistant-v1.0.0`                        | _(no Dockerfile yet)_                                |
| `bacen-ifdata-cronjob`             | `bacen-ifdata-cronjob-v1.0.0`             | `neotool-bacen-ifdata-cronjob` (+ cli, retry-worker) |
| `indicators-job`                   | `indicators-job-v1.0.0`                   | `neotool-financialdata-indicators`                   |
| `ratings-job`                      | `ratings-job-v1.0.0`                      | `neotool-financialdata-ratings`                      |
| `institution-enhancement-consumer` | `institution-enhancement-consumer-v1.0.0` | `neotool-institution-enhancement-consumer`           |
| `neotool-web`                      | `neotool-web-v1.0.0`                      | `neotool-web`                                        |

Shared libraries (`:common`, `:common-batch`, `:common-features`, Go `shared/`)
are **not** independently released — they are internal dependencies.

### Dry-run a release locally

```bash
# Preview what would happen (no changes made)
npx nx release --dry-run --yes

# First-time bootstrap (creates initial tags)
npx nx release --first-release --skip-publish --yes
```

### Version tracking

Each releasable project has a `package.json` with a `version` field that `nx release`
updates automatically:

```json
{ "name": "security", "version": "1.3.0", "private": true }
```

This file is the source of truth for the current version. The `"private": true` flag
prevents accidental npm publishing.

---

## CI/CD Pipelines

### Pull requests — `ci.yml`

On every PR, the `detect` job runs `npx nx show projects --affected` and classifies
the results into three matrices:

- **Kotlin matrix**: Runs `./gradlew :module:test`, `:module:ktlintCheck`, `:module:koverVerify`
- **Go matrix**: Runs `go vet ./...` and `go test -race ./...` in each worker directory
- **Web**: Runs `pnpm typecheck`, `pnpm lint`, `pnpm test:unit`

Only affected projects are tested. If nothing in `service/kotlin/security/` changed,
the security test job is skipped entirely.

### Release — `release.yml`

Triggered on push to `main`. Runs `npx nx release --skip-publish --yes` which:

1. Analyzes conventional commits since the last tag for each project
2. Bumps the `version` in each affected `package.json`
3. Creates a git commit with the version changes
4. Creates per-service git tags (`security-v1.3.0`)
5. Creates GitHub Releases with changelogs
6. Pushes commits and tags

### Production build — `build-production.yml`

Triggered by per-service tags (`*-v*.*.*`). For each tag:

1. Parses project name and version from the tag
2. Looks up Docker build config (context, Dockerfile, image name)
3. Builds and pushes Docker image(s) to GHCR
4. Cleans up old image versions (keeps 3)
5. Triggers supergraph composition if the service is a GraphQL subgraph

### End-to-end flow

```
feat(security): add 2FA  →  merge to main
                                ↓
                         release.yml: nx release
                                ↓
                         tag: security-v1.3.0
                                ↓
                         build-production.yml
                                ↓
                         Docker: neotool-security:v1.3.0
                                ↓
                         Flux detects new tag → deploys
```

---

## Adding a New Project

### New Kotlin API service

1. Add the Gradle module to `service/kotlin/settings.gradle.kts`:

   ```kotlin
   include(":myservice")
   ```

2. Create the module directory with standard Gradle structure under `service/kotlin/myservice/`.

3. Add `service/kotlin/myservice/package.json` (for `nx release` and naming):

   ```json
   { "name": "myservice", "version": "0.1.0", "private": true }
   ```

4. Add `service/kotlin/myservice/project.json` (for tags):

   ```json
   { "tags": ["type:api", "lang:kotlin", "scope:myservice"] }
   ```

5. Add to `nx.json` → `release.projects` array.

6. Add Docker build config to `.github/workflows/build-production.yml` in the
   `case` statement.

7. Add to `KOTLIN_KNOWN` list in `.github/workflows/ci.yml` detect job.

### New Go worker

1. Create directory under `workers/golang/` (e.g., `workers/golang/financial_data/my_worker/`).

2. Initialize Go module: `cd workers/golang/financial_data/my_worker && go mod init ...`

3. Add `package.json`:

   ```json
   { "name": "my-worker", "version": "0.1.0", "private": true }
   ```

4. Add `project.json`:

   ```json
   {
     "tags": ["type:job", "lang:go"],
     "targets": {
       "build": {
         "executor": "nx:run-commands",
         "options": { "command": "go build ./...", "cwd": "{projectRoot}" }
       },
       "test": {
         "executor": "nx:run-commands",
         "options": { "command": "go test ./...", "cwd": "{projectRoot}" }
       },
       "lint": {
         "executor": "nx:run-commands",
         "options": { "command": "go vet ./...", "cwd": "{projectRoot}" }
       }
     }
   }
   ```

5. Add to `nx.json` → `release.projects` array.

6. Add Docker build config to `.github/workflows/build-production.yml`.

### Checklist after adding any project

- [ ] `npx nx show projects` lists the new project
- [ ] `npx nx build <project>` succeeds
- [ ] `npx nx test <project>` succeeds
- [ ] `npx nx graph` shows the project in the graph
- [ ] Project is in `release.projects` in `nx.json` (if releasable)
- [ ] Build config added to `build-production.yml` (if releasable)
- [ ] CI classification updated in `ci.yml` (if Kotlin, add to `KOTLIN_KNOWN`)

---

## Configuration Reference

### `nx.json`

```jsonc
{
  "$schema": "./node_modules/nx/schemas/nx-schema.json",
  "defaultBase": "main", // Git branch for affected detection
  "cacheDirectory": ".nx/cache", // Local cache location
  "plugins": [
    {
      "plugin": "@nx/gradle", // Auto-discovers Kotlin modules
      "include": ["service/kotlin/**/*"],
    },
    {
      "plugin": "@nx/next/plugin", // Auto-discovers Next.js app
      "include": ["web/**/*"],
    },
  ],
  "targetDefaults": {
    "build": { "cache": true, "dependsOn": ["^build"] },
    "test": { "cache": true },
    "lint": { "cache": true },
  },
  "release": {
    "projects": [
      /* 9 releasable projects */
    ],
    "projectsRelationship": "independent",
    "releaseTagPattern": "{projectName}-v{version}",
    "version": { "conventionalCommits": true },
    "changelog": {
      "workspaceChangelog": false,
      "projectChangelogs": { "createRelease": "github" },
    },
    "git": { "commit": true, "tag": true },
  },
}
```

### `package.json` (root)

```json
{
  "name": "neotool",
  "version": "1.0.0",
  "private": true,
  "scripts": { "nx": "nx" },
  "devDependencies": {
    "@nx/gradle": "^22.5.1",
    "@nx/next": "^22.5.0",
    "nx": "^22.5.1"
  }
}
```

### Key files

| File                              | Purpose                                                     |
| --------------------------------- | ----------------------------------------------------------- |
| `nx.json`                         | Workspace config, plugins, caching, release                 |
| `package.json` (root)             | Nx + plugin dependencies                                    |
| `*/project.json`                  | Per-project tags and targets                                |
| `*/package.json`                  | Project name + version (for releasable projects)            |
| `service/kotlin/build.gradle.kts` | Gradle shared config + `dev.nx.gradle.project-graph` plugin |

---

## Troubleshooting

### Nx daemon is stuck or behaving unexpectedly

```bash
npx nx reset
```

This clears the cache and restarts the daemon. It's the first thing to try for any
unexpected behavior.

### `@nx/gradle` times out during project graph resolution

The `@nx/gradle` plugin calls a `nxProjectGraph` Gradle task to discover modules.
If this hangs:

1. Test Gradle directly: `cd service/kotlin && ./gradlew nxProjectGraph`
2. If Gradle works but Nx doesn't, reset the daemon: `npx nx reset`
3. Ensure Java 21 is available (the Gradle plugin requires it)

### Affected detection doesn't match expectations

```bash
# Check what base Nx is comparing against
npx nx show projects --affected --base=origin/main --verbose

# Ensure full git history is available
git fetch --all
```

In CI, `fetch-depth: 0` is required for accurate affected detection.

### A project doesn't appear in `npx nx show projects`

- **Kotlin**: Check it's in `service/kotlin/settings.gradle.kts`
- **Go/other**: Check the directory has a `project.json` file
- **All**: Run `npx nx reset` to clear stale project graph cache

### `nx release` says "no version bump" for a project

Commits that don't follow conventional commit format (no `feat:`, `fix:`, etc.) don't
trigger version bumps. Verify your commit messages:

```bash
# Check commit format
git log --oneline -10

# Valid: feat(security): add new endpoint
# Valid: fix: resolve null pointer
# Invalid: added new endpoint          ← no prefix
# Invalid: updated security service    ← no prefix
```

---

## Updating Nx

```bash
# Check for available updates
npx nx migrate latest

# Review the generated migrations.json, then apply
npx nx migrate --run-migrations

# Update lock file
npm install

# Verify everything still works
npx nx reset
npx nx show projects
npx nx run-many --target=build --all
```

> **Important**: Keep `nx` and `@nx/gradle` on the same major version. Version
> mismatches between Nx core and plugins cause compatibility issues.

---

**Last Updated**: 2026-02-16
**Maintainer**: Platform Engineering Team
