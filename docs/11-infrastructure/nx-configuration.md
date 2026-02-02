---
title: Nx Configuration and Usage Guide
category: infrastructure
status: active
version: 1.0.0
tags: [nx, monorepo, build-system, ci-cd, developer-guide]
related:
  - ../92-adr/0003-nx-monorepo-tooling.md
  - ci-cd-pipeline.md
---

# Nx Configuration and Usage Guide

## Overview

This document provides comprehensive guidance on configuring, using, and maintaining Nx in the NeoTool monorepo. Nx serves as our build system and task orchestrator for managing a polyglot codebase (TypeScript, Kotlin, Go).

**See also**: [ADR-0003: Nx as Monorepo Build System](../92-adr/0003-nx-monorepo-tooling.md) for architectural rationale.

---

## Table of Contents

1. [Installation & Setup](#installation--setup)
2. [Workspace Configuration](#workspace-configuration)
3. [Project Configuration](#project-configuration)
4. [Common Commands](#common-commands)
5. [CI/CD Integration](#cicd-integration)
6. [Remote Caching](#remote-caching)
7. [Dependency Graph](#dependency-graph)
8. [Troubleshooting](#troubleshooting)
9. [Best Practices](#best-practices)

---

## Installation & Setup

### Prerequisites
- Node.js 20.x or later
- npm or yarn
- Git
- Language-specific tools:
  - Java 21 (for Kotlin services)
  - Go 1.22+ (for Go services)

### Initial Installation

```bash
# Clone repository
git clone git@github.com:invistus/neotool.git
cd neotool

# Install dependencies (includes Nx)
npm install

# Verify Nx installation
npx nx --version
```

### First-Time Setup

```bash
# View dependency graph
npx nx graph

# Check what's affected by your changes
npx nx affected:graph

# Run affected tests
npx nx affected --target=test
```

---

## Workspace Configuration

### `nx.json` - Root Configuration

Located at the repository root, this file defines workspace-wide settings:

```json
{
  "$schema": "./node_modules/nx/schemas/nx-schema.json",

  "targetDefaults": {
    "build": {
      "dependsOn": ["^build"],
      "cache": true
    },
    "test": {
      "cache": true,
      "inputs": ["default", "^production"]
    },
    "lint": {
      "cache": true
    },
    "docker-build": {
      "dependsOn": ["build"],
      "cache": false
    }
  },

  "namedInputs": {
    "default": ["{projectRoot}/**/*", "sharedGlobals"],
    "production": [
      "default",
      "!{projectRoot}/**/?(*.)+(spec|test).[jt]s?(x)?(.snap)",
      "!{projectRoot}/tsconfig.spec.json"
    ],
    "sharedGlobals": []
  },

  "affected": {
    "defaultBase": "origin/main"
  },

  "tasksRunnerOptions": {
    "default": {
      "runner": "nx-cloud",
      "options": {
        "cacheableOperations": ["build", "test", "lint"],
        "accessToken": "YOUR_NX_CLOUD_TOKEN"
      }
    }
  },

  "generators": {
    "@nx/next": {
      "application": {
        "style": "css",
        "linter": "eslint"
      }
    }
  }
}
```

#### Key Configuration Sections

**`targetDefaults`**: Define default behavior for common tasks
- `dependsOn: ["^build"]`: Run dependencies' build first
- `cache: true`: Enable caching for this target
- `inputs`: Define what affects cache invalidation

**`namedInputs`**: Reusable input definitions
- `default`: All project files
- `production`: Exclude test files
- `sharedGlobals`: Root-level files affecting all projects

**`affected`**: Configure affected project detection
- `defaultBase`: Git ref to compare against (usually `origin/main`)

**`tasksRunnerOptions`**: Configure task execution and caching
- `runner`: Use `nx-cloud` for remote caching
- `cacheableOperations`: Which targets can be cached

---

## Project Configuration

### Project Types

**Apps** (`apps/`): Deployable applications
- Next.js web application
- React Native mobile app

**Services** (`services/`): Backend microservices
- Kotlin services (security, assets, financialdata)
- Go services (indicators)

**Libraries** (`libs/`): Shared code
- `kotlin-common`: Shared Kotlin utilities
- `ts-utils`: TypeScript utilities
- `go-shared`: Go shared packages

### `project.json` Structure

Each project has a `project.json` file defining its configuration:

#### Example: Kotlin Service (`services/security/project.json`)

```json
{
  "$schema": "../../node_modules/nx/schemas/project-schema.json",
  "name": "security",
  "sourceRoot": "services/security/src",
  "projectType": "application",
  "targets": {
    "build": {
      "executor": "@nx/gradle:build",
      "options": {
        "task": "bootJar",
        "args": ["--no-daemon"]
      },
      "configurations": {
        "production": {
          "args": ["--no-daemon", "-Pprofile=prod"]
        }
      }
    },
    "test": {
      "executor": "@nx/gradle:test",
      "options": {
        "task": "test",
        "args": ["--no-daemon"]
      },
      "outputs": ["{projectRoot}/build/reports/tests"]
    },
    "integration-test": {
      "executor": "@nx/gradle:test",
      "options": {
        "task": "integrationTest",
        "args": ["--no-daemon"]
      },
      "dependsOn": ["build"]
    },
    "lint": {
      "executor": "@nx/gradle:run-task",
      "options": {
        "task": "ktlintCheck"
      }
    },
    "docker-build": {
      "executor": "@nx-tools/nx-container:build",
      "options": {
        "context": "services/security",
        "dockerfile": "services/security/Dockerfile",
        "tags": [
          "ghcr.io/invistus/neotool-security:latest",
          "ghcr.io/invistus/neotool-security:{args.version}"
        ],
        "push": false
      },
      "dependsOn": ["build"]
    }
  },
  "tags": ["type:service", "lang:kotlin", "domain:security"],
  "implicitDependencies": ["kotlin-common"]
}
```

#### Example: Go Service (`services/indicators/project.json`)

```json
{
  "$schema": "../../node_modules/nx/schemas/project-schema.json",
  "name": "indicators",
  "sourceRoot": "services/indicators",
  "projectType": "application",
  "targets": {
    "build": {
      "executor": "@nx/go:build",
      "options": {
        "outputPath": "dist/services/indicators",
        "main": "services/indicators/cmd/main.go"
      },
      "configurations": {
        "production": {
          "ldflags": ["-s", "-w"]
        }
      }
    },
    "test": {
      "executor": "@nx/go:test",
      "options": {
        "coverProfile": "coverage.out",
        "race": true
      }
    },
    "lint": {
      "executor": "@nx/go:lint",
      "options": {
        "linter": "golangci-lint"
      }
    },
    "docker-build": {
      "executor": "@nx-tools/nx-container:build",
      "options": {
        "context": "services/indicators",
        "dockerfile": "services/indicators/Dockerfile",
        "tags": [
          "ghcr.io/invistus/neotool-indicators:latest"
        ],
        "push": false
      },
      "dependsOn": ["build"]
    }
  },
  "tags": ["type:service", "lang:go", "domain:financialdata"]
}
```

#### Example: Next.js App (`apps/web/project.json`)

```json
{
  "$schema": "../../node_modules/nx/schemas/project-schema.json",
  "name": "web",
  "sourceRoot": "apps/web",
  "projectType": "application",
  "targets": {
    "build": {
      "executor": "@nx/next:build",
      "options": {
        "outputPath": "dist/apps/web"
      },
      "configurations": {
        "production": {
          "outputPath": "dist/apps/web"
        }
      }
    },
    "serve": {
      "executor": "@nx/next:server",
      "options": {
        "buildTarget": "web:build",
        "dev": true,
        "port": 3000
      },
      "configurations": {
        "production": {
          "buildTarget": "web:build:production",
          "dev": false
        }
      }
    },
    "test": {
      "executor": "@nx/jest:jest",
      "options": {
        "jestConfig": "apps/web/jest.config.ts",
        "passWithNoTests": true
      }
    },
    "lint": {
      "executor": "@nx/eslint:lint",
      "options": {
        "lintFilePatterns": ["apps/web/**/*.{ts,tsx,js,jsx}"]
      }
    },
    "docker-build": {
      "executor": "@nx-tools/nx-container:build",
      "options": {
        "context": "apps/web",
        "dockerfile": "apps/web/Dockerfile",
        "tags": ["ghcr.io/invistus/neotool-web:latest"],
        "push": false
      },
      "dependsOn": ["build"]
    }
  },
  "tags": ["type:app", "lang:typescript", "framework:nextjs"],
  "implicitDependencies": ["ts-utils"]
}
```

### Project Tags

Tags enforce architectural boundaries and enable filtering:

```json
{
  "tags": [
    "type:app",           // Project type: app, service, lib
    "lang:typescript",    // Language: typescript, kotlin, go
    "framework:nextjs",   // Framework/tech stack
    "domain:security"     // Business domain
  ]
}
```

Use tags to enforce constraints in `.eslintrc.json`:

```json
{
  "overrides": [
    {
      "files": ["*.ts", "*.tsx"],
      "rules": {
        "@nx/enforce-module-boundaries": [
          "error",
          {
            "allow": [],
            "depConstraints": [
              {
                "sourceTag": "type:app",
                "onlyDependOnLibsWithTags": ["type:lib"]
              },
              {
                "sourceTag": "lang:typescript",
                "bannedExternalImports": ["java.*", "kotlin.*"]
              }
            ]
          }
        ]
      }
    }
  ]
}
```

---

## Common Commands

### Development Workflow

```bash
# Run specific project target
npx nx <target> <project>

# Examples
npx nx build security           # Build security service
npx nx test web                 # Test web app
npx nx serve web                # Run web app in dev mode
npx nx lint indicators          # Lint Go indicators service
npx nx docker-build security    # Build Docker image
```

### Affected Commands

Run tasks only on projects affected by current changes:

```bash
# Show affected projects
npx nx affected:apps            # Only affected apps
npx nx affected:libs            # Only affected libraries
npx nx show projects --affected # All affected projects

# Run targets on affected projects
npx nx affected --target=build    # Build affected
npx nx affected --target=test     # Test affected
npx nx affected --target=lint     # Lint affected

# Run multiple targets
npx nx affected --target=lint,test,build --parallel=3

# Compare against different base
npx nx affected --target=test --base=main --head=HEAD
npx nx affected --target=test --base=HEAD~1
```

### Parallel Execution

```bash
# Run with specific parallelism
npx nx affected --target=test --parallel=3

# Max parallelism (use all CPU cores)
npx nx affected --target=test --parallel=max

# Sequential execution
npx nx affected --target=test --parallel=false
```

### Run All Projects

```bash
# Run target on ALL projects (ignoring affected)
npx nx run-many --target=build --all
npx nx run-many --target=test --all --parallel=3

# Run on specific projects
npx nx run-many --target=test --projects=security,assets,web
```

### Dependency Graph

```bash
# Interactive dependency graph (opens browser)
npx nx graph

# Affected dependency graph
npx nx affected:graph

# Export graph as JSON
npx nx graph --file=graph.json

# Show dependencies of specific project
npx nx graph --focus=security

# Show projects affected by specific project
npx nx graph --focus=kotlin-common --affected
```

### Cache Management

```bash
# Clear local cache
npx nx reset

# Show cache statistics
npx nx show projects --affected --with-target=build

# Run without cache
npx nx test security --skip-nx-cache
```

---

## CI/CD Integration

### GitHub Actions Example

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
    branches: ['**']

env:
  NX_CLOUD_ACCESS_TOKEN: ${{ secrets.NX_CLOUD_ACCESS_TOKEN }}

jobs:
  main:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Required for affected detection

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - name: Install dependencies
        run: npm ci

      - name: Lint affected projects
        run: npx nx affected --target=lint --base=origin/main --parallel=3

      - name: Test affected projects
        run: npx nx affected --target=test --base=origin/main --parallel=3 --configuration=ci

      - name: Build affected projects
        run: npx nx affected --target=build --base=origin/main --parallel=3 --configuration=production

      - name: Upload coverage
        uses: codecov/codecov-action@v4
        with:
          directory: ./coverage
```

### Key CI/CD Patterns

**Set affected base**: Always use `--base=origin/main` for PR builds

**Fetch full history**: Use `fetch-depth: 0` to ensure accurate affected detection

**Parallel execution**: Use `--parallel=3` or higher to speed up builds

**Configuration**: Use `--configuration=ci` or `--configuration=production` for environment-specific settings

**Cache sharing**: Set `NX_CLOUD_ACCESS_TOKEN` to enable remote caching

---

## Remote Caching

### Nx Cloud Setup

1. **Sign up for Nx Cloud**: Visit [nx.app](https://nx.app/) and create a free account

2. **Connect workspace**:
   ```bash
   npx nx connect-to-nx-cloud
   ```

3. **Get access token**: Copy token from Nx Cloud dashboard

4. **Add to environment**:
   ```bash
   # Local development (.env)
   export NX_CLOUD_ACCESS_TOKEN=your-token-here

   # CI/CD (GitHub Secrets)
   # Add NX_CLOUD_ACCESS_TOKEN to repository secrets
   ```

5. **Verify caching**:
   ```bash
   # First run (no cache)
   npx nx build security
   # Output: Successfully ran target build for project security (15s)

   # Second run (cached)
   npx nx build security
   # Output: Nx read the output from the cache instead of running the command (0.2s)
   ```

### Cache Benefits

- **Local cache**: Instant rebuilds when nothing changed
- **Remote cache**: Share cache across team and CI
- **CI speedup**: Reuse builds from other branches/PRs
- **Cost reduction**: Fewer GitHub Actions minutes consumed

### Cache Hit Strategies

Maximize cache hits by:
1. Using consistent Node.js and language versions
2. Avoiding unnecessary file changes (timestamps, logs)
3. Properly defining `inputs` in `project.json`
4. Excluding test files from production builds

---

## Dependency Graph

### Visualization

```bash
# Interactive graph (opens browser)
npx nx graph
```

Features:
- **Zoom/pan**: Navigate large graphs
- **Focus**: Click project to highlight dependencies
- **Filter**: Show only apps, libs, or specific tags
- **Export**: Save as JSON or image

### Understanding Dependencies

**Implicit dependencies**: Defined in `project.json`
```json
{
  "implicitDependencies": ["kotlin-common"]
}
```

**Inferred dependencies**: Automatically detected from:
- Import statements (TypeScript)
- Gradle dependencies (Kotlin)
- Go module imports (Go)

**Build order**: Nx uses dependency graph to determine build order
```
kotlin-common (build first)
  ↓
security (depends on kotlin-common)
  ↓
security:docker-build (depends on security:build)
```

### Circular Dependencies

Nx prevents circular dependencies. If detected:
```bash
# Error
NX   Cannot execute task security:build because it has a circular dependency on itself.
```

**Resolution**: Refactor to extract shared code into a separate library.

---

## Troubleshooting

### Common Issues

#### 1. Affected detection missing projects

**Symptom**: `nx affected` doesn't detect changed projects

**Cause**: Missing git history or incorrect base

**Solution**:
```bash
# Ensure full git history
git fetch --all

# Verify base branch
npx nx affected:apps --base=origin/main --head=HEAD

# Check git log
git log origin/main..HEAD
```

#### 2. Cache not working

**Symptom**: Builds always run, never cached

**Cause**: Cache invalidation due to inputs

**Solution**:
```bash
# Clear cache and try again
npx nx reset
npx nx build security

# Check cache inputs
npx nx show project security --web
# Review "inputs" and "namedInputs" sections
```

#### 3. Build failures in CI but not local

**Symptom**: Tests pass locally but fail in CI

**Cause**: Missing dependencies or incorrect configuration

**Solution**:
```bash
# Run with CI configuration locally
npx nx test security --configuration=ci

# Check for missing dependencies
npm ci
npx nx build --all
```

#### 4. Nx Cloud connection issues

**Symptom**: `Could not connect to Nx Cloud`

**Cause**: Invalid token or network issues

**Solution**:
```bash
# Verify token
echo $NX_CLOUD_ACCESS_TOKEN

# Test connection
npx nx connect-to-nx-cloud --yes

# Disable Nx Cloud temporarily
export NX_CLOUD_ACCESS_TOKEN=""
```

### Debug Mode

```bash
# Verbose output
npx nx build security --verbose

# Print configuration
npx nx show project security

# Print resolved configuration
NX_VERBOSE_LOGGING=true npx nx build security
```

---

## Best Practices

### 1. Commit Message Conventions

Use conventional commits for semantic versioning:

```bash
# Examples
git commit -m "feat(security): add 2FA support"
git commit -m "fix(web): resolve routing bug"
git commit -m "chore(deps): update Nx to v18"
```

### 2. Project Organization

**Clear boundaries**: Keep apps, services, and libs separate

**Shared code**: Extract common code into `libs/`

**Tags**: Use consistent tagging for architectural constraints

### 3. Affected Detection

**Always compare to main**: Use `--base=origin/main` in CI

**Test affected**: `npx nx affected --target=test` before pushing

**Visual verification**: Use `npx nx affected:graph` to verify

### 4. Caching

**Define inputs carefully**: Only include files that affect output

**Exclude test files**: Use `production` named input for builds

**Cache expensive operations**: Build and test should always be cached

### 5. CI/CD

**Parallel execution**: Use `--parallel=3` or higher

**Fail fast**: Use `--maxFailures=1` to stop on first failure

**Remote caching**: Always set `NX_CLOUD_ACCESS_TOKEN`

**Full builds**: Run full builds weekly to catch missed dependencies

### 6. Developer Workflow

**Check affected before pushing**:
```bash
npx nx affected:graph
npx nx affected --target=lint,test,build --parallel=3
```

**Use serve for development**:
```bash
npx nx serve web  # Hot reload, fast refresh
```

**Clear cache when debugging**:
```bash
npx nx reset
```

---

## Additional Resources

### Official Documentation
- [Nx Documentation](https://nx.dev/)
- [Nx Affected Commands](https://nx.dev/concepts/affected)
- [Nx Cloud](https://nx.dev/ci/intro/ci-with-nx)
- [Nx Gradle Plugin](https://nx.dev/nx-api/gradle)
- [Nx Go Plugin](https://github.com/nx-go/nx-go)

### Internal Documentation
- [ADR-0003: Nx Monorepo Tooling](../92-adr/0003-nx-monorepo-tooling.md)
- [CI/CD Pipeline Documentation](./ci-cd-pipeline.md)
- [Release Management](./release-management.md)

### Community
- [Nx Discord](https://discord.gg/nx)
- [Nx GitHub](https://github.com/nrwl/nx)
- [Nx Blog](https://blog.nrwl.io/)

---

## Maintenance

### Nx Updates

```bash
# Check for updates
npx nx migrate latest

# Apply migrations
npx nx migrate --run-migrations

# Update dependencies
npm install
```

### Performance Monitoring

Track CI/CD metrics:
- Build time per PR
- Cache hit rate
- GitHub Actions minutes consumed
- Nx Cloud usage (hours/month)

**Review quarterly** to ensure performance targets are met.

---

**Last Updated**: 2026-01-30
**Maintainer**: Platform Engineering Team
**Next Review**: 2026-04-30
