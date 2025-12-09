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
2. **CRITICAL: If you added or modified GraphQL operations OR created hooks that use GraphQL**: 
   - **MUST run `pnpm run codegen` FIRST** to generate TypeScript types and hooks
   - **DO NOT create hooks importing from `.generated.ts` files before running codegen**
   - This will cause TypeScript errors that cannot be resolved until codegen runs
3. Run `pnpm run lint` in the `web` directory
4. If errors are found:
   - Run `pnpm run lint:fix` to auto-fix fixable issues
   - Manually fix any remaining errors that cannot be auto-fixed
   - Re-run `pnpm run lint` to verify all errors are resolved
5. Run `pnpm run typecheck` to ensure no TypeScript errors
6. Only commit after both `lint` and `typecheck` pass with zero errors

**Example Workflow**:
```bash
cd web
# Make changes to TypeScript/React files

# ⚠️ CRITICAL: If GraphQL operations were added/modified OR hooks using GraphQL were created:
# MUST run codegen FIRST before creating hooks or running typecheck
pnpm run codegen  # Generate types and hooks

# Now create or update hooks that import from .generated.ts files

# Run validation
pnpm run lint
# If errors found:
pnpm run lint:fix
pnpm run lint  # Verify fixes
pnpm run typecheck  # Also check types (should pass if codegen succeeded)
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
2. **CRITICAL: If GraphQL operations were added/modified OR hooks using GraphQL were created**:
   - **MUST run `cd web && pnpm run codegen` FIRST**
   - This generates the `.generated.ts` files that hooks depend on
   - **DO NOT skip this step** - it will cause TypeScript errors
3. **Run lint checks**:
   - Backend: `cd service/kotlin && ./gradlew ktlintCheck`
   - Frontend: `cd web && pnpm run lint && pnpm run typecheck`
4. **Fix any lint errors**:
   - Backend: `./gradlew ktlintFormat` then re-check
   - Frontend: `pnpm run lint:fix` then re-check
5. **Verify fixes**: Re-run lint checks to ensure zero errors
6. **Commit**: Only commit after lint checks pass

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
- Missing generated GraphQL files
- Duplicate type exports

**Solution**: Run `pnpm run lint:fix` to auto-fix most issues. Fix TypeScript errors manually.

**Missing Generated GraphQL Files**:

When importing from `.generated.ts` files, ensure the files exist by running codegen first.

**⚠️ CRITICAL WARNING**: **DO NOT create hooks that import from `.generated.ts` files before running codegen**. This will cause TypeScript errors that cannot be resolved until codegen successfully generates the files.

**Error Examples**:
```typescript
// ❌ Error: Cannot find module '@/lib/graphql/operations/authorization-management/queries.generated'
import { useGetRolesQuery } from '@/lib/graphql/operations/authorization-management/queries.generated';

// ❌ Error: Cannot find module '@/lib/graphql/operations/authorization-management/mutations.generated'
import { useCreateRoleMutation } from '@/lib/graphql/operations/authorization-management/mutations.generated';

// ❌ Error: Module '"@/lib/graphql/types/__generated__/graphql"' has no exported member 'CreateRoleInput'
import { CreateRoleInput, UpdateRoleInput } from '@/lib/graphql/types/__generated__/graphql';
```

**Solution**:
1. **First**: Ensure GraphQL operations are defined in `.ts` files (not `.generated.ts` files)
2. **Second**: Verify the GraphQL schema includes the operations you're using (check supergraph or schema files)
3. **Third**: Run `pnpm run codegen` to generate the missing files
4. **Fourth**: If codegen fails with schema errors, fix the GraphQL schema first, then regenerate the supergraph if needed
5. **Finally**: Create or update your hooks to import from the generated files

**Rule**: Always run `pnpm run codegen` before `pnpm run typecheck` when:
- Adding new GraphQL operations (queries, mutations, subscriptions)
- Modifying existing GraphQL operations
- Creating hooks that import from `.generated.ts` files
- The supergraph schema has been updated

**Creating Hooks with GraphQL Operations - Step-by-Step Checklist**:

When creating hooks that use GraphQL operations, follow this order:

1. ✅ **Define GraphQL operations** in `.ts` files (e.g., `queries.ts`, `mutations.ts`)
   - Use `gql` template literals
   - Include necessary fragments
   - **DO NOT** create `.generated.ts` files manually

2. ✅ **Verify GraphQL schema** includes your operations
   - Check that the supergraph schema (`contracts/graphql/supergraph/supergraph.local.graphql`) includes your types and operations
   - If not, update the schema first and regenerate the supergraph

3. ✅ **Run codegen** to generate types and hooks:
   ```bash
   cd web
   pnpm run codegen
   ```
   - This generates `.generated.ts` files next to your operation files
   - This generates input/output types in the base types file

4. ✅ **Create the hook file** importing from `.generated.ts` files:
   ```typescript
   import { useGetRolesQuery } from '@/lib/graphql/operations/authorization-management/queries.generated';
   import { CreateRoleInput } from '@/lib/graphql/types/__generated__/graphql';
   ```

5. ✅ **Run typecheck** to verify types:
   ```bash
   pnpm run typecheck
   ```
   - Should pass with zero errors if codegen succeeded

6. ✅ **Run lint** to verify code quality:
   ```bash
   pnpm run lint
   ```
   - Fix any linting issues

**Common Mistake**: Creating hooks before running codegen will result in TypeScript errors that cannot be resolved until codegen runs successfully. Always run codegen first!

### Shared GraphQL Fragments & Codegen Post-processing

- **Source of truth** for shared fragments is `web/src/lib/graphql/fragments/common.graphql`. Never edit `common.generated.ts` manually—the file is fully overwritten every time codegen runs.
- `pnpm run codegen` runs `graphql-codegen` **and** `node scripts/fix-generated-types.mjs`. The post-processing script:
  - Rewrites `common.generated.ts` so it only exports fragment types and valid `gql` documents (including nested spreads like `...UserFields`).
  - Normalizes generated operations to import hooks/types from `@apollo/client/react`, matching Apollo Client v4’s package layout.
- **Adding a new shared fragment**:
  1. Append the fragment definition to `common.graphql`.
  2. Run `pnpm run codegen` (which also runs the fixer).
  3. Import the fragment or `FragmentDoc` from `@/lib/graphql/fragments/common.generated`.
- **Rule**: Treat every file under `src/lib/graphql/fragments/*.generated.ts` and `src/lib/graphql/operations/**/*.generated.ts` as read-only outputs. If something looks wrong in a generated file, update the `.graphql` source or schema and re-run codegen instead of hand-editing the artefact.

**Duplicate Type Exports**:

When multiple files export the same type name, use explicit exports in index files to avoid conflicts.

**Error Example**:
```typescript
// ❌ Error: Module './useRoleManagement' has already exported a member named 'Permission'
export * from './useRoleManagement';
export * from './usePermissionManagement'; // Also exports Permission
```

**Solution - Use Explicit Exports**:
```typescript
// ✅ Correct - explicit exports avoid conflicts
export { useRoleManagement } from './useRoleManagement';
export { usePermissionManagement } from './usePermissionManagement';

export type {
  Role,
  UseRoleManagementOptions,
  UseRoleManagementReturn,
} from './useRoleManagement';

// Permission type - exported from primary source only
export type {
  Permission,
  UsePermissionManagementOptions,
  UsePermissionManagementReturn,
} from './usePermissionManagement';
```

**Alternative Solution - Remove Duplicate Type**:
If a type is duplicated across files, remove it from one file and import it from the primary source:

```typescript
// In useRoleManagement.ts
// ❌ Don't export duplicate type
// export type Permission = { ... }

// ✅ Import from primary source if needed
import type { Permission } from './usePermissionManagement';
```

**Rule**: 
- Use explicit named exports in index files when re-exporting from multiple modules
- Avoid duplicate type definitions - define types in one place and import where needed
- When types are identical across files, export from the primary/canonical source only

**React Compiler: preserve-manual-memoization**:

When using `useCallback` or `useMemo` with object properties, React Compiler prefers the entire object in the dependency array rather than individual properties. This allows React Compiler to better optimize and infer dependencies.

**Example - Incorrect (specific properties)**:
```typescript
const loadNextPage = useCallback(() => {
  if (pageInfo?.hasNextPage && pageInfo?.endCursor) {
    setAfter(pageInfo.endCursor);
  }
}, [pageInfo?.hasNextPage, pageInfo?.endCursor]); // ❌ React Compiler error
```

**Example - Correct (entire object)**:
```typescript
const loadNextPage = useCallback(() => {
  if (pageInfo?.hasNextPage && pageInfo?.endCursor) {
    setAfter(pageInfo.endCursor);
  }
}, [pageInfo]); // ✅ React Compiler can optimize
```

**Rationale**: 
- React Compiler infers dependencies automatically and prefers object-level dependencies
- Using the entire object allows React Compiler to better track changes and optimize re-renders
- The error message indicates: "Inferred less specific property than source" - meaning React Compiler wants the entire object

**When to Apply**:
- When using `useCallback` or `useMemo` with properties from an object
- When React Compiler reports `react-hooks/preserve-manual-memoization` errors
- When the dependency array lists multiple properties from the same object

**React Hooks: Potentially Undefined Nested Properties in Dependency Arrays**:

When using `useMemo` or `useCallback` with nested properties that may be undefined, use the parent object in the dependency array instead of the nested property with optional chaining. This satisfies both TypeScript's strict null checks and React Compiler's dependency inference.

**Example - Incorrect (optional chaining in dependency array)**:
```typescript
const roleWithPermissions = useMemo(() => {
  if (!permissionsData?.roles?.edges || !roleId) return null;
  return permissionsData.roles.edges.map(e => e.node).find((r) => r.id === roleId) || null;
}, [permissionsData?.roles?.edges, roleId]); // ❌ TypeScript error + React Compiler error
```

**Example - Correct (parent object in dependency array)**:
```typescript
const roleWithPermissions = useMemo(() => {
  if (!permissionsData?.roles?.edges || !roleId) return null;
  return permissionsData.roles.edges.map(e => e.node).find((r) => r.id === roleId) || null;
}, [permissionsData, roleId]); // ✅ TypeScript happy + React Compiler happy
```

**Rationale**:
- **TypeScript**: Accessing potentially undefined nested properties in dependency arrays causes TypeScript errors (`TS18048: 'X' is possibly 'undefined'`)
- **React Compiler**: React Compiler infers dependencies from actual code usage, not from optional chaining in dependency arrays. It sees `permissionsData.roles.edges` being accessed and expects `permissionsData` (the parent object) in the dependency array
- **Safety**: The null/undefined checks inside the memoized function ensure safe access to nested properties
- **Correctness**: When the parent object changes (including when it goes from `undefined` to defined), the memoized value will correctly recalculate

**When to Apply**:
- When using `useMemo` or `useCallback` with nested properties that may be undefined
- When TypeScript reports `TS18048` errors about possibly undefined values in dependency arrays
- When React Compiler reports `react-hooks/preserve-manual-memoization` errors with "Inferred different dependency than source"
- When accessing nested properties from GraphQL query results or other potentially undefined data structures

**Pattern**:
1. Use the parent object (e.g., `permissionsData`, `usersGroupsData`) in the dependency array
2. Handle null/undefined checks inside the memoized function using optional chaining (`?.`)
3. Access nested properties safely after the null check

**Additional Example**:
```typescript
// ❌ Incorrect
const assignedUsers = useMemo(() => {
  if (!usersGroupsData?.users?.edges) return [];
  return usersGroupsData.users.edges.map(e => e.node)
    .filter((user) => user.roles.some((r) => r.id === role.id))
    .map((user) => ({ id: user.id, email: user.email }));
}, [usersGroupsData?.users?.edges, role, pendingUsers]); // ❌ Errors

// ✅ Correct
const assignedUsers = useMemo(() => {
  if (!usersGroupsData?.users?.edges) return [];
  return usersGroupsData.users.edges.map(e => e.node)
    .filter((user) => user.roles.some((r) => r.id === role.id))
    .map((user) => ({ id: user.id, email: user.email }));
}, [usersGroupsData, role, pendingUsers]); // ✅ No errors
```

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
