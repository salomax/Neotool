---
title: Linting Standards
type: standard
category: coding
status: current
version: 1.0.0
tags: [linting, code-quality, validation, standards]
ai_optimized: true
search_keywords: [linting, lint, code-quality, validation, ktlint, eslint, formatting]
related:
  - 05-standards/coding-standards/kotlin-standards.md
  - 06-workflows/feature-development.md
  - 11-validation/code-review-checklist.md
---

# Linting Standards

> **Purpose**: Mandatory linting rules to ensure code quality and prevent lint errors from accumulating.

## Core Rule: Lint Check Before Every Change

### Rule: Mandatory Lint Verification

**Rule**: **EVERY code change MUST be verified for lint errors before completion.**

**Rationale**: 
- Prevents accumulation of lint errors that become difficult to fix later
- Ensures consistent code style across the codebase
- Catches potential issues early in development
- Maintains code quality standards

**Enforcement**: 
- Lint checks are mandatory before committing code
- Lint errors must be resolved before code review
- CI/CD pipelines will fail on lint errors
- No exceptions for "quick fixes" or "small changes"

## Backend (Kotlin) Linting

### Tool: ktlint

**Configuration**: Configured in `service/kotlin/build.gradle.kts`

**Commands**:
```bash
# Check for lint errors (does not modify files)
./gradlew ktlintCheck

# Auto-fix lint errors (modifies files)
./gradlew ktlintFormat

# Run lint check for all services
./gradlew ktlintCheck
```

### Rule: Run ktlintCheck Before Committing

**Rule**: Always run `./gradlew ktlintCheck` before committing Kotlin code changes.

**Steps**:
1. Make your code changes
2. Run `./gradlew ktlintCheck` in the `service/kotlin` directory
3. If errors are found:
   - Run `./gradlew ktlintFormat` to auto-fix formatting issues
   - Manually fix any remaining errors that cannot be auto-fixed
   - Re-run `./gradlew ktlintCheck` to verify all errors are resolved
4. Only commit after `ktlintCheck` passes with zero errors

**Example Workflow**:
```bash
cd service/kotlin
# Make changes to Kotlin files
./gradlew ktlintCheck
# If errors found:
./gradlew ktlintFormat
./gradlew ktlintCheck  # Verify fixes
# Now safe to commit
```

### Integration with Build

**Current Setup**: 
- `ktlintCheck` is automatically run as part of the `check` task
- `ignoreFailures` is set to `false` (build fails on lint errors)
- All subprojects have ktlint configured

**Implications**:
- Running `./gradlew check` will fail if lint errors exist
- CI/CD pipelines will catch lint errors automatically
- Local development should catch errors before pushing

## Frontend (TypeScript/Next.js) Linting

### Tool: ESLint

**Configuration**: Configured in `web/eslint.config.mjs`

**Commands**:
```bash
# Check for lint errors (does not modify files)
cd web
pnpm run lint

# Auto-fix lint errors (modifies files)
pnpm run lint:fix

# Type check (also important for code quality)
pnpm run typecheck
```

### Rule: Run lint Before Committing

**Rule**: Always run `pnpm run lint` before committing TypeScript/React code changes.

**Steps**:
1. Make your code changes
2. Run `pnpm run lint` in the `web` directory
3. If errors are found:
   - Run `pnpm run lint:fix` to auto-fix fixable issues
   - Manually fix any remaining errors that cannot be auto-fixed
   - Re-run `pnpm run lint` to verify all errors are resolved
4. Run `pnpm run typecheck` to ensure no TypeScript errors
5. Only commit after both `lint` and `typecheck` pass with zero errors

**Example Workflow**:
```bash
cd web
# Make changes to TypeScript/React files
pnpm run lint
# If errors found:
pnpm run lint:fix
pnpm run lint  # Verify fixes
pnpm run typecheck  # Also check types
# Now safe to commit
```

### Integration with CI

**Current Setup**:
- `lint` command uses `--max-warnings=0` flag (fails on warnings)
- `ci:unit` script runs `typecheck`, `lint`, and `test` in sequence
- CI pipelines run lint checks automatically

**Implications**:
- CI will fail if lint errors or warnings exist
- Local development should catch errors before pushing

## Unified Validation Command

### Using the CLI Validation Script

**Command**: `./neotool validate` (or `./scripts/cli/cli validate`)

**Purpose**: Runs all validations including lint checks for both frontend and backend.

**Usage**:
```bash
# Run all validations (lint, typecheck, tests, coverage)
./neotool validate

# Run only web validations
./neotool validate --web

# Run only service validations
./neotool validate --service

# Run validations for specific service
./neotool validate --service security
```

**When to Use**:
- Before creating a PR
- After completing a feature
- As part of pre-commit workflow
- When you want comprehensive validation

## Pre-Commit Workflow

### Mandatory Steps Before Every Commit

1. **Make your changes**
2. **Run lint checks**:
   - Backend: `cd service/kotlin && ./gradlew ktlintCheck`
   - Frontend: `cd web && pnpm run lint && pnpm run typecheck`
3. **Fix any lint errors**:
   - Backend: `./gradlew ktlintFormat` then re-check
   - Frontend: `pnpm run lint:fix` then re-check
4. **Verify fixes**: Re-run lint checks to ensure zero errors
5. **Commit**: Only commit after lint checks pass

### Quick Validation

For quick validation of both frontend and backend:
```bash
# From project root
./neotool validate
```

## IDE Integration

### Recommended Setup

**VS Code / Cursor**:
- Install ESLint extension for TypeScript/React
- Install Kotlin extension with ktlint support
- Enable "Format on Save" with linting rules
- Configure to show lint errors inline

**IntelliJ IDEA / Android Studio**:
- Enable ktlint plugin
- Configure to run ktlint on save
- Enable ESLint integration for frontend files

**Benefits**:
- Catch lint errors as you type
- Auto-fix on save (when safe)
- Visual indicators of lint issues

## CI/CD Integration

### Automatic Lint Checks

**Backend**: 
- `ktlintCheck` runs as part of `check` task
- CI runs `./gradlew check` which includes lint verification
- Build fails if lint errors exist

**Frontend**:
- CI runs `pnpm run ci:unit` which includes `lint`
- Build fails if lint errors or warnings exist

**No Exceptions**: CI will reject any code with lint errors, regardless of urgency or size of change.

## Common Lint Issues and Solutions

### Backend (ktlint)

**Common Issues**:
- Trailing whitespace
- Missing blank lines
- Incorrect indentation
- Import ordering
- Line length exceeding 120 characters
- Comments in value argument lists (must be on separate lines)
- Multiline expression wrapping (must start on new line)
- Argument list wrapping (arguments must be on separate lines when list is multiline)
- Missing trailing commas in multiline argument lists

**Solution**: Run `./gradlew ktlintFormat` to auto-fix most issues.

**Note**: Some issues cannot be auto-fixed and must be manually corrected:
- Comments in value argument lists must be placed on separate lines above the argument
- Multiline expressions must start on a new line
- Long argument lists must have each argument on a separate line
- Trailing commas are required before closing parentheses in multiline argument lists

**Example - Comments in Argument Lists**:
```kotlin
// ❌ Incorrect - comment on same line as argument
classes(
    "io.micronaut.core.io.service.SoftServiceLoader",
    "*\$*", // Generated inner classes
    "*Generated*",
)

// ✅ Correct - comment on separate line above argument
classes(
    "io.micronaut.core.io.service.SoftServiceLoader",
    // Generated inner classes
    "*\$*",
    "*Generated*",
)
```

**Example - Multiline Expression Wrapping**:
```kotlin
// ❌ Incorrect - multiline expression starts on same line
val fetcher = GraphQLPayloadDataFetcher.createPayloadDataFetcher("testOperation") { payload }

// ✅ Correct - multiline expression starts on new line
val fetcher =
    GraphQLPayloadDataFetcher.createPayloadDataFetcher("testOperation") { payload }
```

**Example - Argument List Wrapping**:
```kotlin
// ❌ Incorrect - arguments on same line when list is multiline
val fetcher = GraphQLPayloadDataFetcher.createUpdateMutationDataFetcher("updateTest") { updateId, updateInput ->
    SuccessPayload("data")
}

// ✅ Correct - arguments on separate lines
val fetcher =
    GraphQLPayloadDataFetcher.createUpdateMutationDataFetcher("updateTest") {
            updateId,
            updateInput,
        ->
        SuccessPayload("data")
    }
```

### Frontend (ESLint)

**Common Issues**:
- Unused variables/imports
- Missing dependencies in hooks
- TypeScript type errors
- React hooks violations
- Import ordering

**Solution**: Run `pnpm run lint:fix` to auto-fix most issues. Fix TypeScript errors manually.

## Exceptions and Exclusions

### Generated Code

**Rule**: Generated code is excluded from lint checks.

**Excluded Paths**:
- Backend: `**/build/**`, `**/generated/**`
- Frontend: `src/lib/graphql/types/__generated__/**`, `**/*.generated.ts`

**Rationale**: Generated code should not be manually edited or linted.

### Legacy Code

**Rule**: Legacy code should be gradually fixed, but new changes must pass lint.

**Approach**:
- New code must pass all lint checks
- When modifying legacy code, fix lint errors in the modified sections
- Gradually improve legacy code over time

## Enforcement

### Developer Responsibility

**Rule**: Every developer is responsible for ensuring their changes pass lint checks.

**Accountability**:
- Code reviews will reject code with lint errors
- PRs with lint errors will not be merged
- CI failures due to lint errors must be fixed before merge

### Code Review

**Rule**: Code reviewers must verify lint checks pass before approving.

**Checklist Item**: 
- [ ] Lint checks pass (verified by CI or local run)
- [ ] No new lint errors introduced
- [ ] Auto-fixable issues have been resolved

## Best Practices

1. **Run lint checks frequently**: Don't wait until the end of development
2. **Fix issues immediately**: Easier to fix when context is fresh
3. **Use auto-fix when safe**: `ktlintFormat` and `lint:fix` handle most issues
4. **Configure IDE**: Catch issues as you type
5. **Pre-commit hooks**: Consider setting up git hooks to run lint checks automatically
6. **Validate before PR**: Always run full validation before creating a PR

## Related Documentation

- [Kotlin Coding Standards](./kotlin-standards.md)
- [Feature Development Workflow](../../06-workflows/feature-development.md)
- [Code Review Checklist](../../11-validation/code-review-checklist.md)
- [Validation Scripts](../../11-validation/validation-scripts.md)

